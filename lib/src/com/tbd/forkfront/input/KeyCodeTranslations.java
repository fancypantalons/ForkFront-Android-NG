package com.tbd.forkfront.input;

import android.view.KeyEvent;

/** Utility class for translating Android key codes to NetHack-specific concepts. */
public class KeyCodeTranslations {
  public static final int DIR_LEFT = 0;
  public static final int DIR_DOWN = 1;
  public static final int DIR_UP = 2;
  public static final int DIR_RIGHT = 3;
  public static final int DIR_UL = 4;
  public static final int DIR_UR = 5;
  public static final int DIR_DL = 6;
  public static final int DIR_DR = 7;

  public static int keyCodeToDir(int keyCode) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_NUMPAD_4:
        return DIR_LEFT;
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_NUMPAD_2:
        return DIR_DOWN;
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_NUMPAD_8:
        return DIR_UP;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_NUMPAD_6:
        return DIR_RIGHT;
      case KeyEvent.KEYCODE_NUMPAD_7:
        return DIR_UL;
      case KeyEvent.KEYCODE_NUMPAD_9:
        return DIR_UR;
      case KeyEvent.KEYCODE_NUMPAD_1:
        return DIR_DL;
      case KeyEvent.KEYCODE_NUMPAD_3:
        return DIR_DR;
      default:
        return -1;
    }
  }
}
