package com.tbd.forkfront.gamepad;

import android.view.KeyEvent;

public final class ButtonNames {
  private ButtonNames() {}

  public static String of(int code) {
    switch (code) {
      case KeyEvent.KEYCODE_BUTTON_A:
        return "A";
      case KeyEvent.KEYCODE_BUTTON_B:
        return "B";
      case KeyEvent.KEYCODE_BUTTON_X:
        return "X";
      case KeyEvent.KEYCODE_BUTTON_Y:
        return "Y";
      case KeyEvent.KEYCODE_BUTTON_L1:
        return "L1";
      case KeyEvent.KEYCODE_BUTTON_R1:
        return "R1";
      case KeyEvent.KEYCODE_BUTTON_L2:
        return "L2";
      case KeyEvent.KEYCODE_BUTTON_R2:
        return "R2";
      case KeyEvent.KEYCODE_BUTTON_THUMBL:
        return "L3";
      case KeyEvent.KEYCODE_BUTTON_THUMBR:
        return "R3";
      case KeyEvent.KEYCODE_BUTTON_START:
        return "Start";
      case KeyEvent.KEYCODE_BUTTON_SELECT:
        return "Select";
      case KeyEvent.KEYCODE_DPAD_UP:
        return "D-Up";
      case KeyEvent.KEYCODE_DPAD_DOWN:
        return "D-Down";
      case KeyEvent.KEYCODE_DPAD_LEFT:
        return "D-Left";
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        return "D-Right";
      case KeyEvent.KEYCODE_DPAD_CENTER:
        return "D-Center";
      case ButtonId.AXIS_LTRIGGER_PSEUDO:
        return "L2 (axis)";
      case ButtonId.AXIS_RTRIGGER_PSEUDO:
        return "R2 (axis)";
      case ButtonId.AXIS_HAT_LEFT_PSEUDO:
        return "HAT-Left";
      case ButtonId.AXIS_HAT_RIGHT_PSEUDO:
        return "HAT-Right";
      case ButtonId.AXIS_HAT_UP_PSEUDO:
        return "HAT-Up";
      case ButtonId.AXIS_HAT_DOWN_PSEUDO:
        return "HAT-Down";
      case ButtonId.LSTICK_UP_PSEUDO:
        return "LS-N";
      case ButtonId.LSTICK_DOWN_PSEUDO:
        return "LS-S";
      case ButtonId.LSTICK_LEFT_PSEUDO:
        return "LS-W";
      case ButtonId.LSTICK_RIGHT_PSEUDO:
        return "LS-E";
      case ButtonId.LSTICK_UL_PSEUDO:
        return "LS-NW";
      case ButtonId.LSTICK_UR_PSEUDO:
        return "LS-NE";
      case ButtonId.LSTICK_DL_PSEUDO:
        return "LS-SW";
      case ButtonId.LSTICK_DR_PSEUDO:
        return "LS-SE";
      case ButtonId.RSTICK_UP_PSEUDO:
        return "RS-N";
      case ButtonId.RSTICK_DOWN_PSEUDO:
        return "RS-S";
      case ButtonId.RSTICK_LEFT_PSEUDO:
        return "RS-W";
      case ButtonId.RSTICK_RIGHT_PSEUDO:
        return "RS-E";
      default:
        return "Button(" + code + ")";
    }
  }
}
