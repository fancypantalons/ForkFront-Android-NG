package com.tbd.forkfront;

import android.os.Handler;
import android.os.Looper;

/**
 * Reusable touch/key repeat timer.
 *
 * Mirrors the repeat logic from GamepadDispatcher so that touch-based
 * widgets and the gamepad behave identically:
 *   - 300 ms initial delay
 *   - 100 ms repeat interval
 */
public class TouchRepeatHelper {
    private static final int REPEAT_INITIAL_MS  = 300;
    private static final int REPEAT_INTERVAL_MS = 100;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mRepeatRunnable;

    /**
     * Start repeating the given action. Cancels any active repeat first.
     */
    public void startRepeat(Runnable action) {
        cancelRepeat();
        mRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                action.run();
                mHandler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };
        mHandler.postDelayed(mRepeatRunnable, REPEAT_INITIAL_MS);
    }

    /**
     * Cancel any active repeat timer.
     */
    public void cancelRepeat() {
        if (mRepeatRunnable != null) {
            mHandler.removeCallbacks(mRepeatRunnable);
            mRepeatRunnable = null;
        }
    }

    /**
     * Cancel repeat and clear state. Call from lifecycle destroy / detach.
     */
    public void destroy() {
        cancelRepeat();
    }
}
