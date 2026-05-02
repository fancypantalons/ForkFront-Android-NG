package com.tbd.forkfront.settings;

import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.appcompat.app.AppCompatActivity;
import com.tbd.forkfront.gamepad.UiCapture;

public class SettingsUiCapture implements UiCapture {
  private final AppCompatActivity mActivity;

  public SettingsUiCapture(AppCompatActivity activity) {
    mActivity = activity;
  }

  @Override
  public boolean handleGamepadKey(KeyEvent ev) {
    if (ev.getAction() != KeyEvent.ACTION_DOWN) return true; // swallow UPs
    switch (ev.getKeyCode()) {
      case KeyEvent.KEYCODE_BUTTON_B:
      case KeyEvent.KEYCODE_BACK:
        mActivity.onBackPressed();
        return true;
    }
    return false; // fall through to dispatcher baseline for D-pad / A confirm
  }

  @Override
  public boolean handleGamepadMotion(MotionEvent ev) {
    return false;
  }
}
