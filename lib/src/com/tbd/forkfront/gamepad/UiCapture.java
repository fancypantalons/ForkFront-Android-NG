package com.tbd.forkfront.gamepad;

import android.view.KeyEvent;
import android.view.MotionEvent;

public interface UiCapture {
    /** @return true if consumed; false to allow dispatcher fallback. */
    boolean handleGamepadKey(KeyEvent ev);
    boolean handleGamepadMotion(MotionEvent ev);
}
