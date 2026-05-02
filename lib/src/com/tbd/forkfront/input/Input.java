package com.tbd.forkfront.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.ui.Util;
import java.util.EnumSet;

public class Input {
  public enum Modifier {
    Shift
  }

  public static EnumSet<Modifier> modifiersFromKeyEvent(KeyEvent event) {
    EnumSet<Modifier> mod = EnumSet.noneOf(Modifier.class);

    // The ALT key is used for special characters (ike '{', '\', and '>') in the emulator
    // so I don't dare use it as a modifier for meta keys
    // if(event.isAltPressed())
    //	mod.add(Modifiers.Meta);

    // Some users have reported that they can't use the hardware shift key. I suspect
    // that the "isShiftPressed" function is broken on those devices. Checking for
    // META_SHIFT might work, otherwise I'll have to manually track the shift keys

    // I don't trust META_SHIFT_ON since its value is not equal to (META_SHIFT_LEFT_ON |
    // META_SHIFT_RIGHT_ON)
    int metaShiftMask =
        KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON | KeyEvent.META_SHIFT_ON;
    if (event.isShiftPressed() || (event.getMetaState() & metaShiftMask) != 0)
      mod.add(Modifier.Shift);

    return mod;
  }

  public static int nhKeyFromKeyCode(
      int keyCode, char ch, EnumSet<Modifier> mod, boolean numPadOn) {
    int nhKey;
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        nhKey = 0x80;
        break;
      case KeyEvent.KEYCODE_SPACE:
        nhKey = ' ';
        break;
      case KeyEvent.KEYCODE_ESCAPE:
        nhKey = '\033';
        break;
      case KeyEvent.KEYCODE_DEL:
        nhKey = 0x7f;
        break;
      case KeyEvent.KEYCODE_DPAD_LEFT:
        nhKey = numPadOn ? '4' : 'h';
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        nhKey = numPadOn ? '6' : 'l';
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
        nhKey = numPadOn ? '8' : 'k';
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        nhKey = numPadOn ? '2' : 'j';
        break;
      case KeyEvent.KEYCODE_DPAD_CENTER:
        nhKey = '.';
        break;
      default:
        nhKey = ch;
        if (nhKey != 0) {
          if (mod.contains(Modifier.Shift)) nhKey = Character.toUpperCase(nhKey);
        }
        return nhKey;
    }

    if (nhKey != 0 && nhKey != 0x80 && mod.contains(Modifier.Shift))
      nhKey = Character.toUpperCase(nhKey);

    return nhKey;
  }

  public static int keyCodeToAction(int keyCode, Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    if (prefs == null) return keyCode;

    int action;
    switch (keyCode) {
      case KeyEvent.KEYCODE_VOLUME_UP:
        action = Util.parseInt(prefs.getString("volup", ""), KeyAction.SystemDefault);
        if (action == KeyAction.SystemDefault) action = keyCode;
        break;
      case KeyEvent.KEYCODE_VOLUME_DOWN:
        action = Util.parseInt(prefs.getString("voldown", ""), KeyAction.SystemDefault);
        if (action == KeyAction.SystemDefault) action = keyCode;
        break;
      case /*KeyEvent.KEYCODE_ZOOM_IN*/ 168:
        action = KeyAction.ZoomIn;
        break;
      case /*KeyEvent.KEYCODE_ZOOM_OUT*/ 169:
        action = KeyAction.ZoomOut;
        break;
      default:
        action = keyCode;
    }

    return action;
  }
}
