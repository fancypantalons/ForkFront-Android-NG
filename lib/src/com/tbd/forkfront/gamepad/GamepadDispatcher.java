package com.tbd.forkfront.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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

    private static final int REPEAT_INITIAL_MS  = 300;
    private static final int REPEAT_INTERVAL_MS = 100;

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

    private final Handler mRepeatHandler = new Handler(Looper.getMainLooper());
    private Runnable mRepeatRunnable;

    // ─── D-pad diagonal synthesis state ──────────────────────────────────────
    private boolean mDiagonalActive = false;
    /** The second D-pad key that completed the diagonal (not added to ChordTracker). */
    private int mDiagonalSecondKey = -1;
    private char mDiagonalChar = 0;
    /** Timestamp of the most recent D-pad key-down (used to enforce diagonalWindowMs). */
    private long mLastDpadDownMs = 0;

    // ─── Baseline fallback dispatcher ─────────────────────────────────────────
    private volatile SyntheticDispatcher mSyntheticDispatcher;

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

    /**
     * Injected by the active Activity so the baseline fallback can synthesize key events
     * into the view tree for focus navigation in non-GAMEPLAY contexts.
     *
     * Call setSyntheticDispatcher() from onResume / onPause; whichever activity is currently
     * in the foreground owns it.
     */
    public interface SyntheticDispatcher {
        /** Dispatch a synthetic DOWN+UP key pair to the activity's view tree. */
        void dispatchKey(int keyCode);
        /** Trigger back navigation (e.g. onBackPressed()). */
        void dispatchBack();
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
        cancelRepeat();
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
        cancelRepeat();
        if (mChordTracker != null) mChordTracker.reset();
        if (mAxisNormalizer != null) mAxisNormalizer.reset();
        mDiagonalActive = false;
        mDiagonalSecondKey = -1;
        mDiagonalChar = 0;
        mLastDpadDownMs = 0;
    }

    public void setSyntheticDispatcher(SyntheticDispatcher dispatcher) {
        mSyntheticDispatcher = dispatcher;
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

        // Non-GAMEPLAY contexts → active UiCapture, then baseline fallback.
        // Returns the consumed result; D-pad returns false so the calling activity can
        // let Android's focus system handle traversal via super.dispatchKeyEvent.
        if (ctx != UiContext.GAMEPLAY && ctx != UiContext.DIRECTION_PROMPT) {
            UiCapture cap = mCaptureStack.isEmpty() ? null : mCaptureStack.peek();
            boolean handled = (cap != null) && cap.handleGamepadKey(ev);
            if (!handled) {
                handled = baselineFallback(ev);
            }
            return handled;
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

        // GAMEPLAY → ChordTracker (with D-pad diagonal synthesis)
        if (action == KeyEvent.ACTION_DOWN) {
            if (isDpadKeycode(keycode)) {
                return handleDpadDown(keycode, ev.getRepeatCount() > 0);
            }
            ChordTracker.Result result = mChordTracker.onKeyDown(keycode, ev.getRepeatCount());
            return result == ChordTracker.Result.HANDLED;
        } else if (action == KeyEvent.ACTION_UP) {
            if (isDpadKeycode(keycode)) {
                return handleDpadUp(keycode);
            }
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
                if (!mCaptureStack.isEmpty()) {
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
            KeyBinding explicit = mBindingMap.find(Chord.single(buttonCode));
            if (explicit != null) {
                onBindingFired(explicit, false);
                startRepeat(() -> onBindingFired(explicit, true));
            } else {
                char nhDir = AxisNormalizer.pseudoToNhDir(buttonCode);
                if (nhDir != 0) {
                    mStateRef.sendDirKeyCmd(nhDir);
                    final char dir = nhDir;
                    startRepeat(() -> {
                        if (mArbiter.current() == UiContext.GAMEPLAY) mStateRef.sendDirKeyCmd(dir);
                    });
                }
            }
            return;
        }

        // HAT axes → route through D-pad diagonal detection
        if (AxisNormalizer.isHatPseudo(buttonCode)) {
            int dpadCode = AxisNormalizer.hatPseudoToDpad(buttonCode);
            if (dpadCode != -1) {
                handleDpadDown(dpadCode, false);
                if (!mDiagonalActive) {
                    // Non-diagonal: start HAT-based key repeat via our timer
                    startRepeat(() -> {
                        if (mArbiter.current() == UiContext.GAMEPLAY) mChordTracker.onKeyDown(dpadCode, 1);
                    });
                }
                // If mDiagonalActive, handleDpadDown already started the diagonal repeat
            }
            return;
        }

        // Triggers and any other pseudo-codes → through ChordTracker
        mChordTracker.onKeyDown(buttonCode, 0);
    }

    private void handleSynthRelease(int buttonCode, UiContext ctx) {
        if (ctx != UiContext.GAMEPLAY) return;

        if (AxisNormalizer.isLStickPseudo(buttonCode) && mOptions.leftStickMovement) {
            cancelRepeat();
            return;
        }

        if (AxisNormalizer.isHatPseudo(buttonCode)) {
            cancelRepeat();
            int dpadCode = AxisNormalizer.hatPseudoToDpad(buttonCode);
            if (dpadCode != -1) handleDpadUp(dpadCode);
            return;
        }
        mChordTracker.onKeyUp(buttonCode);
    }

    // ─── D-pad diagonal synthesis ─────────────────────────────────────────────

    /**
     * Handles a D-pad key-down event, synthesizing a diagonal char when two adjacent
     * cardinals are pressed within diagonalWindowMs and both are bound to movement.
     *
     * Returns true if the event was handled (including diagonal activation).
     */
    private boolean handleDpadDown(int dpadKeycode, boolean isRepeat) {
        if (isRepeat) {
            // While diagonal is active our own timer drives repeats; swallow Android repeats.
            if (mDiagonalActive) return true;
            return mChordTracker.onKeyDown(dpadKeycode, 1) == ChordTracker.Result.HANDLED;
        }

        if (mOptions.synthDiagonals && !mDiagonalActive) {
            int heldDpad = getHeldDpadKeycode();
            if (heldDpad != -1 && heldDpad != dpadKeycode) {
                long now = SystemClock.uptimeMillis();
                if (now - mLastDpadDownMs <= mOptions.diagonalWindowMs) {
                    char diag = getDpadDiagonal(heldDpad, dpadKeycode);
                    if (diag != 0 && isDpadMovementBinding(heldDpad) && isDpadMovementBinding(dpadKeycode)) {
                        mDiagonalActive = true;
                        mDiagonalSecondKey = dpadKeycode;
                        mDiagonalChar = diag;
                        cancelRepeat();
                        mStateRef.sendDirKeyCmd(diag);
                        final char diagChar = diag;
                        startRepeat(() -> {
                            if (mArbiter.current() == UiContext.GAMEPLAY) mStateRef.sendDirKeyCmd(diagChar);
                        });
                        return true; // second key not passed to ChordTracker
                    }
                }
            }
            mLastDpadDownMs = SystemClock.uptimeMillis();
        }

        return mChordTracker.onKeyDown(dpadKeycode, 0) == ChordTracker.Result.HANDLED;
    }

    /**
     * Handles a D-pad key-up event, deactivating diagonal mode if active.
     */
    private boolean handleDpadUp(int dpadKeycode) {
        if (mDiagonalActive) {
            if (dpadKeycode == mDiagonalSecondKey) {
                // Second key released: clean up diagonal, don't pass to ChordTracker
                mDiagonalActive = false;
                mDiagonalSecondKey = -1;
                mDiagonalChar = 0;
                cancelRepeat();
                return true;
            }
            // First key released: end diagonal, fall through to ChordTracker for UP
            mDiagonalActive = false;
            mDiagonalSecondKey = -1;
            mDiagonalChar = 0;
            cancelRepeat();
        }
        mLastDpadDownMs = 0;
        return mChordTracker.onKeyUp(dpadKeycode) == ChordTracker.Result.HANDLED;
    }

    private int getHeldDpadKeycode() {
        for (ButtonId b : mChordTracker.getHeld()) {
            if (isDpadKeycode(b.code)) return b.code;
        }
        return -1;
    }

    private static boolean isDpadKeycode(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT  || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
               code == KeyEvent.KEYCODE_DPAD_UP    || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private static char getDpadDiagonal(int code1, int code2) {
        boolean up    = code1 == KeyEvent.KEYCODE_DPAD_UP    || code2 == KeyEvent.KEYCODE_DPAD_UP;
        boolean down  = code1 == KeyEvent.KEYCODE_DPAD_DOWN  || code2 == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left  = code1 == KeyEvent.KEYCODE_DPAD_LEFT  || code2 == KeyEvent.KEYCODE_DPAD_LEFT;
        boolean right = code1 == KeyEvent.KEYCODE_DPAD_RIGHT || code2 == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (up   && left)  return 'y';
        if (up   && right) return 'u';
        if (down && left)  return 'b';
        if (down && right) return 'n';
        return 0;
    }

    /** Returns true if the D-pad key is bound to its expected NetHack movement char. */
    private boolean isDpadMovementBinding(int dpadCode) {
        char expected = directionFromKeyCode(dpadCode);
        if (expected == 0) return false;
        KeyBinding kb = mBindingMap.find(Chord.single(dpadCode));
        if (kb == null || kb.target.kind() != BindingTarget.Kind.NH_KEY) return false;
        return ((BindingTarget.NhKey) kb.target).ch == expected;
    }

    // ─── Baseline fallback for non-GAMEPLAY contexts ──────────────────────────

    /**
     * Maps A→DPAD_CENTER, B→BACK, L1→PAGE_UP, R1→PAGE_DOWN for contexts without a
     * registered UiCapture (e.g. drawer open, settings navigation).
     *
     * D-pad events return false so the caller can let Android focus traversal handle them
     * via super.dispatchKeyEvent. The caller is responsible for ensuring such events do
     * not reach the game engine (see ForkFront.dispatchKeyEvent).
     */
    private boolean baselineFallback(KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;

        int keycode = ev.getKeyCode();

        // D-pad: pass through — returning false lets Android focus-search handle it.
        if (isDpadKeycode(keycode)) return false;

        SyntheticDispatcher sd = mSyntheticDispatcher;
        if (sd == null) return false;

        switch (keycode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                sd.dispatchKey(KeyEvent.KEYCODE_DPAD_CENTER);
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                sd.dispatchBack();
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                sd.dispatchKey(KeyEvent.KEYCODE_PAGE_UP);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                sd.dispatchKey(KeyEvent.KEYCODE_PAGE_DOWN);
                return true;
            default:
                return false;
        }
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

    // ─── Axis repeat timer ────────────────────────────────────────────────────

    private void startRepeat(Runnable action) {
        cancelRepeat();
        mRepeatRunnable = new Runnable() {
            @Override public void run() {
                action.run();
                mRepeatHandler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };
        mRepeatHandler.postDelayed(mRepeatRunnable, REPEAT_INITIAL_MS);
    }

    private void cancelRepeat() {
        if (mRepeatRunnable != null) {
            mRepeatHandler.removeCallbacks(mRepeatRunnable);
            mRepeatRunnable = null;
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
