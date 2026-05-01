package com.tbd.forkfront;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference;

public class Settings extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    private SettingsFragment fragment;

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
        if (currentFragment instanceof PreferenceFragmentCompat) {
            Preference pref = ((PreferenceFragmentCompat) currentFragment).findPreference("tilesetPreference");
            if (pref instanceof TilesetPreference) {
                ((TilesetPreference) pref).onActivityResult(requestCode, resultCode, data);
            }
        }
    }
}
