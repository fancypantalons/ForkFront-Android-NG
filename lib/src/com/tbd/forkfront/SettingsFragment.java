package com.tbd.forkfront;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.hardware.input.InputManager;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.tbd.forkfront.gamepad.GamepadDeviceWatcher;
import com.tbd.forkfront.gamepad.KeyBindingDefaultsLoader;
import com.tbd.forkfront.gamepad.KeyBindingStore;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, InputManager.InputDeviceListener {

    private ActivityResultLauncher<String> mImagePickerLauncher;
    private InputManager mInputManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register ActivityResultLauncher for image picking
        mImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    if (Tileset.createCustomTilesetLocalCopy(getContext(), uri)) {
                        SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
                        editor.putBoolean("customTiles", true);
                        editor.apply();
                        Toast.makeText(getContext(), "Custom tileset image updated", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        EditTextPreference wPref = findPreference("customTileW");
        if (wPref != null) {
            wPref.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        }

        EditTextPreference hPref = findPreference("customTileH");
        if (hPref != null) {
            hPref.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        }

        Preference pickCustomTileset = findPreference("pick_custom_tileset");
        if (pickCustomTileset != null) {
            pickCustomTileset.setOnPreferenceClickListener(preference -> {
                mImagePickerLauncher.launch("image/*");
                return true;
            });
        }

        updateTilesetVisibility();
        updateGamepadVisibility();

        Preference gamepadReset = findPreference("gamepad_reset_defaults");
        if (gamepadReset != null) {
            gamepadReset.setOnPreferenceClickListener(pref -> {
                SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
                if (prefs != null) {
                    KeyBindingStore.clear(prefs);
                    // Also clear the "defaults applied" tracker so all defaults re-apply cleanly
                    prefs.edit().remove("gamepad_defaults_applied_v1").apply();
                    Toast.makeText(getContext(), "Gamepad bindings reset to defaults", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        if(!getContext().getResources().getBoolean(R.bool.hearseAvailable)) {
            PreferenceCategory advancedParent = findPreference("advanced");
            Preference hearsePref = findPreference("hearse");
            if(advancedParent != null && hearsePref != null) {
                advancedParent.removePreference(hearsePref);
            }
        }

        Preference root = getPreferenceScreen();
        if (root != null) {
            setIconSpaceReserved(root, false);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView list = getListView();
        list.post(() -> list.requestFocus());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            mInputManager = (InputManager) getContext().getSystemService(android.content.Context.INPUT_SERVICE);
            if (mInputManager != null) {
                mInputManager.registerInputDeviceListener(this, null);
            }
            updateGamepadVisibility();
        }
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
        if (mInputManager != null) {
            mInputManager.unregisterInputDeviceListener(this);
            mInputManager = null;
        }
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
        } else if ("tileset".equals(key)) {
            handleTilesetChanged(sharedPreferences);
        } else if ("theme_mode".equals(key)) {
            String themeMode = sharedPreferences.getString("theme_mode", "-1");
            AppCompatDelegate.setDefaultNightMode(Integer.parseInt(themeMode));
        }
    }

    private void handleTilesetChanged(SharedPreferences sharedPreferences) {
        String value = sharedPreferences.getString("tileset", "default_32");
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if ("CUSTOM".equals(value)) {
            editor.putBoolean("customTiles", true);
        } else {
            editor.putBoolean("customTiles", false);
            // Parse default dimensions from value (e.g., default_32 or geoduck_24x40)
            int tileW = 32;
            int tileH = 32;
            if (!"TTY".equals(value)) {
                int xIndex = value.lastIndexOf('x');
                int underIndex = value.lastIndexOf('_');
                try {
                    if (xIndex > 0 && underIndex > 0) {
                        tileW = Integer.parseInt(value.substring(underIndex + 1, xIndex));
                        tileH = Integer.parseInt(value.substring(xIndex + 1));
                    } else if (underIndex > 0) {
                        tileW = Integer.parseInt(value.substring(underIndex + 1));
                        tileH = tileW;
                    }
                } catch (NumberFormatException e) {
                    // Use defaults
                }
            }
            editor.putInt("tileW", tileW);
            editor.putInt("tileH", tileH);
        }
        editor.apply();
        updateTilesetVisibility();
    }

    private void updateTilesetVisibility() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String tileset = prefs.getString("tileset", "default_32");
        boolean isCustom = "CUSTOM".equals(tileset);

        Preference pickPref = findPreference("pick_custom_tileset");
        Preference wPref = findPreference("customTileW");
        Preference hPref = findPreference("customTileH");

        if (pickPref != null) pickPref.setVisible(isCustom);
        if (wPref != null) wPref.setVisible(isCustom);
        if (hPref != null) hPref.setVisible(isCustom);
    }

    private void setIconSpaceReserved(Preference preference, boolean reserved) {
        preference.setIconSpaceReserved(reserved);
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                setIconSpaceReserved(group.getPreference(i), reserved);
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

    @Override
    public void onInputDeviceAdded(int deviceId) {
        updateGamepadVisibility();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        updateGamepadVisibility();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {}

    private void updateGamepadVisibility() {
        if (getContext() == null) return;
        Preference gamepadScreen = findPreference("gamepad_screen");
        if (gamepadScreen != null) {
            gamepadScreen.setVisible(GamepadDeviceWatcher.isGamepadConnected(getContext()));
        }
    }
}
