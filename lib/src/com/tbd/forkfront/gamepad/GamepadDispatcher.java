package com.tbd.forkfront.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.preference.PreferenceManager;

import com.tbd.forkfront.DeviceProfile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Central gamepad event router.
 *
 * Owns the KeyBindingMap, ChordTracker, AxisNormalizer, and UiCapture stack.
 * A single process-wide instance is accessible via getInstance().
 */
public class GamepadDispatcher {
    private static final String TAG = "GamepadDispatcher";

    /** Synthetic events injected by the dispatcher carry this source flag to prevent re-entry. */
    private static final int SOURCE_SYNTHETIC = InputDevice.SOURCE_UNKNOWN | 0x80000000;

    private static volatile GamepadDispatcher sInstance;

    private final Context mAppContext;
    private final NH_StateRef mStateRef;
    private final UiContextArbiter mArbiter;
    private final UiActionExecutor mActionExecutor;

    private GamepadOptions mOptions;
    private KeyBindingMap mBindingMap;
    private ChordTracker mChordTracker;
    private AxisNormalizer mAxisNormalizer;

    private final Deque<UiCapture> mCaptureStack = new ArrayDeque<>();

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
        (prefs, key) -> {
            if (key != null && key.startsWith("gamepad_")) {
                reloadFromPreferences();
            }
        };

    /**
     * Abstraction over NH_State to avoid a hard dependency from the gamepad package.
     */
    public interface NH_StateRef {
        boolean sendKeyCmd(int nhKey);
        boolean sendDirKeyCmd(int nhKey);
        void sendStringCmd(String str);
        boolean expectsDirection();
        boolean isMouseLocked();
    }

    public GamepadDispatcher(Context appContext,
                             NH_StateRef stateRef,
                             UiContextArbiter arbiter,
                             UiActionExecutor executor) {
        mAppContext = appContext.getApplicationContext();
        mStateRef = stateRef;
        mArbiter = arbiter;
        mActionExecutor = executor;
        sInstance = this;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        prefs.registerOnSharedPreferenceChangeListener(mPrefListener);

        reloadFromPreferences();
    }

    public static GamepadDispatcher getInstance() {
        return sInstance;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    public void destroy() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        prefs.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        if (sInstance == this) sInstance = null;
    }

    public void reloadFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        mOptions = GamepadOptions.fromPrefs(prefs);

        List<KeyBinding> bindings = KeyBindingStore.load(prefs);
        if (bindings == null) {
            String deviceKey = DeviceProfile.detect(mAppContext);
            bindings = KeyBindingDefaultsLoader.loadDefaults(mAppContext, deviceKey);
            KeyBindingStore.save(prefs, bindings, deviceKey != null ? deviceKey : "generic");
        } else {
            String deviceKey = DeviceProfile.detect(mAppContext);
            bindings = KeyBindingDefaultsLoader.mergeNewDefaults(mAppContext, deviceKey, bindings, prefs);
        }

        mBindingMap = new KeyBindingMap(bindings);
        // Preserve the held-set across a reload (ChordTracker.reset not called here)
        if (mChordTracker == null) {
            mChordTracker = new ChordTracker(mBindingMap, this::onBindingFired);
        } else {
            mChordTracker = new ChordTracker(mBindingMap, this::onBindingFired);
        }
        mAxisNormalizer = new AxisNormalizer(mOptions);
    }

    public void setEnabled(boolean enabled) {
        mOptions = new GamepadOptions(enabled, mOptions.leftStickMovement,
            mOptions.synthDiagonals, mOptions.diagonalWindowMs);
    }

    public void resetTracker() {
        if (mChordTracker != null) mChordTracker.reset();
        if (mAxisNormalizer != null) mAxisNormalizer.reset();
    }

    // ─── UiCapture stack ──────────────────────────────────────────────────────

    public void enterUiCapture(UiCapture capture) {
        mCaptureStack.push(capture);
    }

    public void exitUiCapture(UiCapture capture) {
        mCaptureStack.remove(capture);
    }

    public UiContextArbiter getArbiter() { return mArbiter; }
    public void pushContext(UiContext ctx) { mArbiter.push(ctx); }
    public void popContext(UiContext ctx) { mArbiter.pop(ctx); }

    // ─── Event source detection ───────────────────────────────────────────────

    public boolean isGamepadEvent(KeyEvent ev) {
        // Reject synthetic re-injected events
        if ((ev.getSource() & SOURCE_SYNTHETIC) == SOURCE_SYNTHETIC) return false;
        // BACK is always system-handled and never appears in the binding map
        if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) return false;
        int src = ev.getSource();
        return (src & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK |
                       InputDevice.SOURCE_DPAD)) != 0;
    }

    public boolean isGamepadEvent(MotionEvent ev) {
        if ((ev.getSource() & SOURCE_SYNTHETIC) == SOURCE_SYNTHETIC) return false;
        int src = ev.getSource();
        return (src & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) != 0;
    }

    // ─── Main dispatch ────────────────────────────────────────────────────────

    public boolean handleKeyEvent(KeyEvent ev, UiContext ctx) {
        if (!mOptions.enabled) return false;
        if (ctx == UiContext.OTHER) return false;
        if (ctx == UiContext.BINDING_CAPTURE) return false; // dialog reads raw events

        int action = ev.getAction();
        int keycode = ev.getKeyCode();

        // UI_ACTION bindings always fire regardless of context (except OTHER/BINDING_CAPTURE)
        if (action == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
            KeyBinding uiBinding = findSimpleUiActionBinding(keycode);
            if (uiBinding != null) {
                onBindingFired(uiBinding, false);
                return true;
            }
        }

        // Non-GAMEPLAY contexts → active UiCapture, then baseline fallback
        if (ctx != UiContext.GAMEPLAY && ctx != UiContext.DIRECTION_PROMPT) {
            UiCapture cap = mCaptureStack.isEmpty() ? null : mCaptureStack.peek();
            boolean handled = (cap != null) && cap.handleGamepadKey(ev);
            if (!handled) {
                handled = baselineFallback(ev);
            }
            return true; // always consume; don't leak gamepad events into NH_State
        }

        // DIRECTION_PROMPT
        if (ctx == UiContext.DIRECTION_PROMPT) {
            if (action == KeyEvent.ACTION_DOWN) {
                char nhDir = directionFromKeyCode(keycode);
                if (nhDir != 0) {
                    mStateRef.sendDirKeyCmd(nhDir);
                    return true;
                }
                if (keycode == KeyEvent.KEYCODE_ESCAPE ||
                    keycode == KeyEvent.KEYCODE_BUTTON_B) {
                    mStateRef.sendDirKeyCmd('\033');
                    return true;
                }
            }
            return true; // swallow all other events in direction prompt
        }

        // GAMEPLAY → ChordTracker
        if (action == KeyEvent.ACTION_DOWN) {
            ChordTracker.Result result = mChordTracker.onKeyDown(keycode, ev.getRepeatCount());
            return result == ChordTracker.Result.HANDLED;
        } else if (action == KeyEvent.ACTION_UP) {
            ChordTracker.Result result = mChordTracker.onKeyUp(keycode);
            return result == ChordTracker.Result.HANDLED;
        }
        return false;
    }

    public boolean handleGenericMotion(MotionEvent ev, UiContext ctx) {
        if (!mOptions.enabled || mAxisNormalizer == null) return false;

        final UiContext capturedCtx = ctx;
        mAxisNormalizer.handleGenericMotion(ev, new AxisNormalizer.EventSink() {
            @Override
            public void onSynthPress(int buttonCode) {
                handleSynthPress(buttonCode, capturedCtx);
            }
            @Override
            public void onSynthRelease(int buttonCode) {
                handleSynthRelease(buttonCode, capturedCtx);
            }
            @Override
            public void onRightStickMoved(float x, float y) {
                // Deliver to active UiCapture if present (e.g. cursor mode panning)
                if (!mCaptureStack.isEmpty()) {
                    // Right-stick motion delivered as a MotionEvent to the capture.
                    // The capture can inspect ev directly since we pass the original.
                    mCaptureStack.peek().handleGamepadMotion(ev);
                }
            }
        });
        return true;
    }

    // ─── Synthetic press/release from AxisNormalizer ──────────────────────────

    private void handleSynthPress(int buttonCode, UiContext ctx) {
        if (ctx != UiContext.GAMEPLAY) return; // axis input only drives GAMEPLAY actions for now

        if (AxisNormalizer.isLStickPseudo(buttonCode) && mOptions.leftStickMovement) {
            // Check for explicit binding first
            KeyBinding explicit = mBindingMap.find(Chord.single(buttonCode));
            if (explicit != null) {
                onBindingFired(explicit, false);
            } else {
                char nhDir = AxisNormalizer.pseudoToNhDir(buttonCode);
                if (nhDir != 0) mStateRef.sendDirKeyCmd(nhDir);
            }
            return;
        }

        // HAT axes → treat as equivalent D-pad keycodes
        if (AxisNormalizer.isHatPseudo(buttonCode)) {
            int dpadCode = AxisNormalizer.hatPseudoToDpad(buttonCode);
            if (dpadCode != -1) {
                mChordTracker.onKeyDown(dpadCode, 0);
            }
            return;
        }

        // Triggers and any other pseudo-codes → through ChordTracker
        mChordTracker.onKeyDown(buttonCode, 0);
    }

    private void handleSynthRelease(int buttonCode, UiContext ctx) {
        if (ctx != UiContext.GAMEPLAY) return;

        if (AxisNormalizer.isLStickPseudo(buttonCode) && mOptions.leftStickMovement) return;

        if (AxisNormalizer.isHatPseudo(buttonCode)) {
            int dpadCode = AxisNormalizer.hatPseudoToDpad(buttonCode);
            if (dpadCode != -1) mChordTracker.onKeyUp(dpadCode);
            return;
        }
        mChordTracker.onKeyUp(buttonCode);
    }

    // ─── Binding dispatch ─────────────────────────────────────────────────────

    private void onBindingFired(KeyBinding binding, boolean isRepeat) {
        BindingTarget target = binding.target;
        if (isRepeat && target.kind() == BindingTarget.Kind.NH_STRING) return;

        switch (target.kind()) {
            case NH_KEY: {
                char ch = ((BindingTarget.NhKey) target).ch;
                if (mStateRef.expectsDirection() && isDirectionChar(ch)) {
                    mStateRef.sendDirKeyCmd(ch);
                } else {
                    mStateRef.sendKeyCmd(ch);
                }
                break;
            }
            case NH_STRING:
                mStateRef.sendStringCmd(((BindingTarget.NhString) target).line);
                break;
            case UI_ACTION:
                if (mActionExecutor != null) {
                    mActionExecutor.execute(((BindingTarget.UiAction) target).id);
                }
                break;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Find a simple (single-button, no modifiers) UI_ACTION binding for a keycode.
     * These fire in any non-OTHER/BINDING_CAPTURE context.
     */
    private KeyBinding findSimpleUiActionBinding(int keycode) {
        KeyBinding kb = mBindingMap.find(Chord.single(keycode));
        if (kb != null && kb.target.kind() == BindingTarget.Kind.UI_ACTION) return kb;
        return null;
    }

    /**
     * Baseline fallback for non-GAMEPLAY contexts without a UiCapture.
     * Synthesizes standard key events so Android's focus traversal still works.
     * For now this is a no-op stub; real synthesis would require dispatching
     * new KeyEvents through the Activity view tree.
     */
    private boolean baselineFallback(KeyEvent ev) {
        // TODO: synthesize DPAD_CENTER / BACK / PAGE_UP / PAGE_DOWN for focus-traversable UIs
        return false;
    }

    private static char directionFromKeyCode(int keycode) {
        switch (keycode) {
            case KeyEvent.KEYCODE_DPAD_UP:    return 'k';
            case KeyEvent.KEYCODE_DPAD_DOWN:  return 'j';
            case KeyEvent.KEYCODE_DPAD_LEFT:  return 'h';
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 'l';
            default: return 0;
        }
    }

    private static boolean isDirectionChar(char ch) {
        return ch == 'h' || ch == 'j' || ch == 'k' || ch == 'l' ||
               ch == 'y' || ch == 'u' || ch == 'b' || ch == 'n';
    }
}
