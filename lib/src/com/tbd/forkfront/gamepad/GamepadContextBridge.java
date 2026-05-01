package com.tbd.forkfront.gamepad;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

/**
 * Glue between NH_State and UiContextArbiter.
 * Also handles gamepad tracker resets when entering modal contexts.
 */
public final class GamepadContextBridge {
    private UiContextArbiter mArbiter;

    @MainThread
    public void setArbiter(UiContextArbiter arbiter) {
        mArbiter = arbiter;
    }

    @MainThread
    public void pushContext(UiContext ctx) {
        if (mArbiter != null) {
            mArbiter.push(ctx);
        }

        // Reset gamepad state when entering any modal/non-gameplay context
        if (ctx != UiContext.GAMEPLAY && ctx != UiContext.DIRECTION_PROMPT) {
            GamepadDispatcher gd = GamepadDispatcher.getInstance();
            if (gd != null) {
                gd.resetTracker();
            }
        }
    }

    @MainThread
    public void popContext(UiContext ctx) {
        if (mArbiter != null) {
            mArbiter.remove(ctx);
        }
    }

    @Nullable
    @MainThread
    public UiContext current() {
        return mArbiter != null ? mArbiter.current() : null;
    }
}
