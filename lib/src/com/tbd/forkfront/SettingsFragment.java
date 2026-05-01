package com.tbd.forkfront;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private ActivityResultLauncher<String> mImagePickerLauncher;
    private TilesetPreference mTilesetPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register ActivityResultLauncher for image picking
        mImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && mTilesetPreference != null) {
                    mTilesetPreference.handleImageResult(uri);
                }
            }
        );
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        mTilesetPreference = findPreference("tilesetPreference");
        if (mTilesetPreference != null) {
            mTilesetPreference.setImagePickerLauncher(new TilesetPreference.ImagePickerLauncher() {
                @Override
                public void launchImagePicker() {
                    mImagePickerLauncher.launch("image/*");
                }
            });
        }

        if(!getContext().getResources().getBoolean(R.bool.hearseAvailable)) {
            PreferenceCategory hearseParent = findPreference("advanced");
            Preference hearsePref = findPreference("hearse");
            if(hearseParent != null && hearsePref != null) {
                hearseParent.removePreference(hearsePref);
            }
        }

        PreferenceCategory settingsCategory = findPreference("settings");
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            Preference fullscreenPref = findPreference("immersive");
            if(settingsCategory != null && fullscreenPref != null) {
                settingsCategory.removePreference(fullscreenPref);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null) {
            for(int i = 0; i < 10; i++) {
                char idx = (char)('0' + i);
                Preference screen = findPreference("panel" + idx);
                if(screen == null) break;
                String name = sharedPreferences.getString("pName" + idx, "");
                screen.setTitle(name);
            }
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.startsWith("pName")) {
            char idx = key.charAt(key.length() - 1);
            Preference screen = findPreference("panel" + idx);
            if (screen != null) {
                String name = sharedPreferences.getString("pName" + idx, "");
                screen.setTitle(name);
            }
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof SliderPreference) {
            @SuppressWarnings("deprecation")
            androidx.fragment.app.DialogFragment f = SliderPreferenceDialogFragment.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}
