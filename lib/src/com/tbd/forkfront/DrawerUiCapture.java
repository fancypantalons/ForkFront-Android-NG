package com.tbd.forkfront;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.tbd.forkfront.gamepad.UiCapture;

public class DrawerUiCapture implements UiCapture {
    private final DrawerLayout mDrawer;
    private final NavigationView mNav;

    public DrawerUiCapture(DrawerLayout drawer, NavigationView nav) {
        mDrawer = drawer;
        mNav = nav;
    }

    @Override
    public boolean handleGamepadKey(KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return true; // swallow UPs
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
                mDrawer.closeDrawer(GravityCompat.END);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: {
                View focused = mNav.findFocus();
                if (focused != null) focused.performClick();
                return true;
            }
        }
        return false; // fall through to dispatcher baseline for D-pad traversal
    }

    @Override
    public boolean handleGamepadMotion(MotionEvent ev) { return false; }
}
