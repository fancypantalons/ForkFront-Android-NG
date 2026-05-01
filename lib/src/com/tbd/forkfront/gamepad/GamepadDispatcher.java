package com.tbd.forkfront.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.preference.PreferenceManager;

import com.tbd.forkfront.DeviceProfile;
import com.tbd.forkfront.TouchRepeatHelper;

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
    public static final int SOURCE_SYNTHETIC = InputDevice.SOURCE_UNKNOWN | 0x80000000;

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

    private final TouchRepeatHelper mRepeatHelper = new TouchRepeatHelper();
    private final Handler mRepeatHandler = new Handler(Looper.getMainLooper());

    // ─── D-pad diagonal synthesis state ──────────────────────────────────────
    private boolean mDiagonalActive = false;
    /** The second D-pad key that completed the diagonal (not added to ChordTracker). */
    private int mDiagonalSecondKey = -1;
    private char mDiagonalChar = 0;
    /** Buffered d-pad key waiting to see if a diagonal partner arrives within diagonalWindowMs. */
    private int mBufferedDpadKey = -1;
    private Runnable mDpadBufferRunnable;

    // ─── Left-stick settle debounce state ────────────────────────────────────
    // When the stick first leaves the deadzone it sweeps through intermediate octants
    // before settling. We hold the first press for STICK_SETTLE_MS; if the octant
    // changes during that window we just update the pending target. Only after the
    // timer fires do we commit to a direction and start repeating.
    // After first commitment, octant transitions are immediate (no debounce).
    private static final int STICK_SETTLE_MS = 50;
    private int mStickActivePseudo = -1;
    private boolean mStickSettling = false;
    private int mStickPendingPseudo = -1;
    private Runnable mStickSettleRunnable;
    // Transient flag: set when active stick releases, cleared after the current
    // synchronous event batch completes (via post). Lets an immediately following
    // Press skip the settle debounce (octant transition, not fresh activation).
    private boolean mStickWasActive = false;

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
        mRepeatHelper.destroy();
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
        mRepeatHelper.cancelRepeat();
        cancelDpadBuffer();
        cancelStickSettle();
        mStickActivePseudo = -1;
        mStickWasActive = false;
        if (mChordTracker != null) mChordTracker.reset();
        if (mAxisNormalizer != null) mAxisNormalizer.reset();
        mDiagonalActive = false;
        mDiagonalSecondKey = -1;
        mDiagonalChar = 0;
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
    public void popContext(UiContext ctx) { mArbiter.remove(ctx); }

    // ─── Event source detection ───────────────────────────────────────────────

    private static boolean isGamepadSource(int source) {
        return (source & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK |
                          InputDevice.SOURCE_DPAD)) != 0;
    }

    public boolean isGamepadEvent(KeyEvent ev) {
        // Reject synthetic re-injected events
        if ((ev.getSource() & SOURCE_SYNTHETIC) == SOURCE_SYNTHETIC) return false;
        // BACK is always system-handled and never appears in the binding map
        if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) return false;
        return isGamepadSource(ev.getSource());
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
        // In non-gameplay contexts (drawer, settings, dialogs), don't consume motion
        // events. Letting them fall through to ViewRootImpl's SyntheticInputStage gives
        // us free HAT→DPAD synthesis, which drives normal focus navigation. This is
        // why Settings (which doesn't override onGenericMotionEvent) "just works."
        if (ctx != UiContext.GAMEPLAY) return false;

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
        if (ctx != UiContext.GAMEPLAY) return; // motion events fall through in non-gameplay

        if (AxisNormalizer.isLStickPseudo(buttonCode) && mOptions.leftStickMovement) {
            if (mStickActivePseudo != -1 || mStickWasActive) {
                // Active or just-released: octant transition — skip debounce.
                mStickWasActive = false;
                cancelStickSettle();
                fireStickDirection(buttonCode);
            } else if (mStickSettling) {
                // Already in settle window: update target and restart timer.
                cancelStickSettle();
                startStickSettle(buttonCode);
            } else {
                // Fresh activation from neutral: start settle window.
                startStickSettle(buttonCode);
            }
            return;
        }

        // HAT axes → route through D-pad diagonal detection
        if (AxisNormalizer.isHatPseudo(buttonCode)) {
            int dpadCode = AxisNormalizer.hatPseudoToDpad(buttonCode);
            if (dpadCode != -1) {
                handleDpadDown(dpadCode, false);
                // Start repeat only for immediate cardinal dispatch (not buffering, not diagonal).
                // Diagonal repeat is started inside handleDpadDown.
                // Buffer-flush repeat is started inside flushDpadBuffer.
                if (!mDiagonalActive && mBufferedDpadKey == -1) {
                    mRepeatHelper.startRepeat(() -> {
                        if (mArbiter.current() == UiContext.GAMEPLAY) mChordTracker.onKeyDown(dpadCode, 1);
                    });
                }
            }
            return;
        }

        // Triggers and any other pseudo-codes → through ChordTracker
        mChordTracker.onKeyDown(buttonCode, 0);
    }

    private void handleSynthRelease(int buttonCode, UiContext ctx) {
        if (ctx != UiContext.GAMEPLAY) return;

        if (AxisNormalizer.isLStickPseudo(buttonCode) && mOptions.leftStickMovement) {
            if (mStickSettling) {
                // Released before settle timer fired: cancel cleanly, no movement.
                cancelStickSettle();
            } else if (mStickActivePseudo != -1) {
                // Active direction released: stop repeat.
                mRepeatHelper.cancelRepeat();
                mStickActivePseudo = -1;
                // Set transient flag so an immediately following Press (octant transition
                // within the same synchronous event batch) skips the settle window.
                mStickWasActive = true;
                mRepeatHandler.post(() -> mStickWasActive = false);
            }
            return;
        }

        if (AxisNormalizer.isHatPseudo(buttonCode)) {
            mRepeatHelper.cancelRepeat();
            int dpadCode = AxisNormalizer.hatPseudoToDpad(buttonCode);
            if (dpadCode != -1) handleDpadUp(dpadCode);
            return;
        }
        mChordTracker.onKeyUp(buttonCode);
    }

    // ─── D-pad diagonal synthesis ─────────────────────────────────────────────

    /**
     * Handles a D-pad key-down event.
     *
     * When synthDiagonals is enabled, the first press is held in a short buffer for
     * diagonalWindowMs. If a second adjacent movement key arrives within the window the
     * buffer is cancelled and only the diagonal fires. Otherwise the buffer flushes and
     * the cardinal fires normally (with repeat). This prevents the extra cardinal step
     * that the old approach produced before the diagonal could activate.
     */
    private boolean handleDpadDown(int dpadKeycode, boolean isRepeat) {
        if (isRepeat) {
            if (mDiagonalActive) return true;
            if (mBufferedDpadKey != -1) return true; // buffer pending; swallow Android repeat
            return mChordTracker.onKeyDown(dpadKeycode, 1) == ChordTracker.Result.HANDLED;
        }

        if (mOptions.synthDiagonals && !mDiagonalActive) {
            if (mBufferedDpadKey != -1 && mBufferedDpadKey != dpadKeycode) {
                // A key is already buffered — check whether this new key forms a diagonal.
                char diag = getDpadDiagonal(mBufferedDpadKey, dpadKeycode);
                if (diag != 0 && isDpadMovementBinding(mBufferedDpadKey) && isDpadMovementBinding(dpadKeycode)) {
                    cancelDpadBuffer();
                    mDiagonalActive = true;
                    mDiagonalSecondKey = dpadKeycode;
                    mDiagonalChar = diag;
                    mRepeatHelper.cancelRepeat();
                    mStateRef.sendDirKeyCmd(diag);
                    final char diagChar = diag;
                    mRepeatHelper.startRepeat(() -> {
                        if (mArbiter.current() == UiContext.GAMEPLAY) mStateRef.sendDirKeyCmd(diagChar);
                    });
                    return true;
                }
                // Not a diagonal pair — flush buffered key now, fall through for the new key.
                flushDpadBuffer(mBufferedDpadKey);
            }

            // Buffer this key if it's a movement binding and no key is already pending.
            if (mBufferedDpadKey == -1 && isDpadMovementBinding(dpadKeycode)) {
                mBufferedDpadKey = dpadKeycode;
                final int bufferedKey = dpadKeycode;
                mDpadBufferRunnable = () -> {
                    mDpadBufferRunnable = null;
                    flushDpadBuffer(bufferedKey);
                };
                mRepeatHandler.postDelayed(mDpadBufferRunnable, mOptions.diagonalWindowMs);
                return true;
            }
        }

        return mChordTracker.onKeyDown(dpadKeycode, 0) == ChordTracker.Result.HANDLED;
    }

    /**
     * Handles a D-pad key-up event, deactivating diagonal or buffer state as needed.
     */
    private boolean handleDpadUp(int dpadKeycode) {
        // Key released while still buffered (no diagonal formed): fire once with no repeat.
        if (mBufferedDpadKey == dpadKeycode) {
            cancelDpadBuffer();
            mChordTracker.onKeyDown(dpadKeycode, 0);
            mChordTracker.onKeyUp(dpadKeycode);
            return true;
        }

        if (mDiagonalActive) {
            if (dpadKeycode == mDiagonalSecondKey) {
                mDiagonalActive = false;
                mDiagonalSecondKey = -1;
                mDiagonalChar = 0;
                mRepeatHelper.cancelRepeat();
                return true;
            }
            // First key of the diagonal released: deactivate and consume.
            mDiagonalActive = false;
            mDiagonalSecondKey = -1;
            mDiagonalChar = 0;
            mRepeatHelper.cancelRepeat();
            return true;
        }
        return mChordTracker.onKeyUp(dpadKeycode) == ChordTracker.Result.HANDLED;
    }

    private void cancelDpadBuffer() {
        if (mDpadBufferRunnable != null) {
            mRepeatHandler.removeCallbacks(mDpadBufferRunnable);
            mDpadBufferRunnable = null;
        }
        mBufferedDpadKey = -1;
    }

    /** Dispatch the buffered d-pad key and arm the HAT repeat timer. */
    private void flushDpadBuffer(int dpadCode) {
        mDpadBufferRunnable = null;
        mBufferedDpadKey = -1;
        if (mChordTracker.onKeyDown(dpadCode, 0) == ChordTracker.Result.HANDLED) {
            mRepeatHelper.startRepeat(() -> {
                if (mArbiter.current() == UiContext.GAMEPLAY) mChordTracker.onKeyDown(dpadCode, 1);
            });
        }
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
        if (isDpadKeycode(keycode)) {
            return false;
        }

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

    // ─── Left-stick settle + direction fire ──────────────────────────────────

    private void startStickSettle(int pseudo) {
        mStickSettling = true;
        mStickPendingPseudo = pseudo;
        mStickSettleRunnable = () -> {
            mStickSettleRunnable = null;
            mStickSettling = false;
            int pending = mStickPendingPseudo;
            mStickPendingPseudo = -1;
            fireStickDirection(pending);
        };
        mRepeatHandler.postDelayed(mStickSettleRunnable, STICK_SETTLE_MS);
    }

    private void cancelStickSettle() {
        if (mStickSettleRunnable != null) {
            mRepeatHandler.removeCallbacks(mStickSettleRunnable);
            mStickSettleRunnable = null;
        }
        mStickSettling = false;
        mStickPendingPseudo = -1;
    }

    private void fireStickDirection(int pseudo) {
        mStickActivePseudo = pseudo;
        mRepeatHelper.cancelRepeat();
        KeyBinding explicit = mBindingMap.find(Chord.single(pseudo));
        if (explicit != null) {
            onBindingFired(explicit, false);
            mRepeatHelper.startRepeat(() -> onBindingFired(explicit, true));
        } else {
            char nhDir = AxisNormalizer.pseudoToNhDir(pseudo);
            if (nhDir != 0) {
                mStateRef.sendDirKeyCmd(nhDir);
                final char dir = nhDir;
                mRepeatHelper.startRepeat(() -> {
                    if (mArbiter.current() == UiContext.GAMEPLAY) mStateRef.sendDirKeyCmd(dir);
                });
            }
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
