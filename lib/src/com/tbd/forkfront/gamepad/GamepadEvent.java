package com.tbd.forkfront.gamepad;

import android.view.KeyEvent;

public final class GamepadEvent {
    public enum Kind {
        BUTTON_DOWN, BUTTON_UP, DPAD, STICK_LEFT, STICK_RIGHT,
        TRIGGER_LEFT, TRIGGER_RIGHT
    }

    public Kind kind;
    public int buttonId;        // KeyEvent.KEYCODE_BUTTON_A etc. for BUTTON_*
                                // pseudo-codes from ButtonId for axis-as-button
    public int dpadDx, dpadDy;  // -1, 0, +1 for DPAD
    public float stickX, stickY; // normalized [-1, 1]
    public float triggerValue;   // 0..1
    public KeyEvent rawKeyEvent; // for fall-through to AlertDialogs / IME
    public long timestampMs;
    public int repeatCount;
}
