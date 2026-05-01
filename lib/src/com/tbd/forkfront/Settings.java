package com.tbd.forkfront;

import android.os.Bundle;
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        GamepadDispatcher gd = GamepadDispatcher.getInstance();
        if (gd != null && gd.isGamepadEvent(event)) {
            if (gd.handleKeyEvent(event, UiContext.SETTINGS_OPEN)) return true;
        }
        return super.dispatchKeyEvent(event);
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
