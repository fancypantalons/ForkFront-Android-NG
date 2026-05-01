package com.tbd.forkfront;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import com.tbd.forkfront.gamepad.GamepadDispatcher;
import com.tbd.forkfront.gamepad.UiContext;

public class Settings extends AppCompatActivity
    implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
               PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private SettingsFragment fragment;
    private SettingsUiCapture mSettingsUiCapture;

    private final GamepadDispatcher.SyntheticDispatcher mSyntheticDispatcher =
        new GamepadDispatcher.SyntheticDispatcher() {
            @Override
            public void dispatchKey(int keyCode) {
                android.view.View decor = getWindow().getDecorView();
                long now = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                    GamepadDispatcher.SOURCE_SYNTHETIC);
                KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                    GamepadDispatcher.SOURCE_SYNTHETIC);
                decor.dispatchKeyEvent(down);
                decor.dispatchKeyEvent(up);
            }
            @Override
            public void dispatchBack() {
                onBackPressed();
            }
        };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        GamepadDispatcher gd = GamepadDispatcher.getInstance();
        if (gd != null && gd.isGamepadEvent(event)) {
            if (gd.handleKeyEvent(event, UiContext.SETTINGS_OPEN)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        GamepadDispatcher gd = GamepadDispatcher.getInstance();
        if (gd != null) {
            gd.setSyntheticDispatcher(mSyntheticDispatcher);
            if (mSettingsUiCapture == null) {
                mSettingsUiCapture = new SettingsUiCapture(this);
            }
            gd.pushContext(UiContext.SETTINGS_OPEN);
            gd.enterUiCapture(mSettingsUiCapture);
        }
    }

    @Override
    protected void onPause() {
        GamepadDispatcher gd = GamepadDispatcher.getInstance();
        if (gd != null) {
            if (mSettingsUiCapture != null) gd.exitUiCapture(mSettingsUiCapture);
            gd.popContext(UiContext.SETTINGS_OPEN);
            gd.setSyntheticDispatcher(null);
        }
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            fragment = new SettingsFragment();
            getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
        }
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        SettingsFragment newFragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        newFragment.setArguments(args);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(android.R.id.content, newFragment)
            .addToBackStack(pref.getKey())
            .commit();

        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        String fragmentClass = pref.getFragment();
        if (fragmentClass == null) return false;

        Bundle args = pref.getExtras();
        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
            .instantiate(getClassLoader(), fragmentClass);
        fragment.setArguments(args);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit();

        return true;
    }
}
