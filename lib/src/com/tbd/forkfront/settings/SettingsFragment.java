package com.tbd.forkfront.settings;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.tbd.forkfront.R;
import com.tbd.forkfront.gamepad.GamepadDeviceWatcher;
import com.tbd.forkfront.gamepad.KeyBindingStore;
import com.tbd.forkfront.window.map.Tileset;

public class SettingsFragment extends PreferenceFragmentCompat
    implements SharedPreferences.OnSharedPreferenceChangeListener,
        InputManager.InputDeviceListener {

  private static final int MAX_PANELS = 10;

  private ActivityResultLauncher<String> mImagePickerLauncher;
  private InputManager mInputManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Register ActivityResultLauncher for image picking
    mImagePickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
              if (uri != null) {
                if (Tileset.createCustomTilesetLocalCopy(getContext(), uri)) {
                  SharedPreferences.Editor editor =
                      getPreferenceManager().getSharedPreferences().edit();
                  editor.putBoolean("customTiles", true);
                  editor.apply();
                  Toast.makeText(getContext(), "Custom tileset image updated", Toast.LENGTH_SHORT)
                      .show();
                }
              }
            });
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.preferences, rootKey);
    setupTilesetPreferences();
    setupGamepadPreferences();
    setupHearsePreferences();
    setupUiPreferences();
  }

  private void setupTilesetPreferences() {
    EditTextPreference wPref = findPreference("customTileW");
    if (wPref != null) {
      wPref.setOnBindEditTextListener(
          editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
    }

    EditTextPreference hPref = findPreference("customTileH");
    if (hPref != null) {
      hPref.setOnBindEditTextListener(
          editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
    }

    Preference pickCustomTileset = findPreference("pick_custom_tileset");
    if (pickCustomTileset != null) {
      pickCustomTileset.setOnPreferenceClickListener(
          preference -> {
            mImagePickerLauncher.launch("image/*");
            return true;
          });
    }

    updateTilesetVisibility();
  }

  private void setupGamepadPreferences() {
    updateGamepadVisibility();

    Preference gamepadReset = findPreference("gamepad_reset_defaults");
    if (gamepadReset != null) {
      gamepadReset.setOnPreferenceClickListener(
          pref -> {
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            if (prefs != null) {
              KeyBindingStore.clear(prefs);
              // Also clear the "defaults applied" tracker so all defaults re-apply cleanly
              prefs.edit().remove("gamepad_defaults_applied_v1").apply();
              Toast.makeText(getContext(), "Gamepad bindings reset to defaults", Toast.LENGTH_SHORT)
                  .show();
            }
            return true;
          });
    }
  }

  private void setupHearsePreferences() {
    if (!getContext().getResources().getBoolean(R.bool.hearseAvailable)) {
      PreferenceCategory advancedParent = findPreference("advanced");
      Preference hearsePref = findPreference("hearse");
      if (advancedParent != null && hearsePref != null) {
        advancedParent.removePreference(hearsePref);
      }
    }
  }

  private void setupUiPreferences() {
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
      mInputManager =
          (InputManager) getContext().getSystemService(android.content.Context.INPUT_SERVICE);
      if (mInputManager != null) {
        mInputManager.registerInputDeviceListener(this, null);
      }
      updateGamepadVisibility();
    }
    SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
    if (sharedPreferences != null) {
      for (int i = 0; i < MAX_PANELS; i++) {
        char idx = (char) ('0' + i);
        Preference screen = findPreference("panel" + idx);
        if (screen == null) break;
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
    if (key.startsWith("pName")) {
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

  private Point parseTileDimensions(String value) {
    int tileW = 32;
    int tileH = 32;
    if (!"TTY".equals(value) && !"CUSTOM".equals(value)) {
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
    return new Point(tileW, tileH);
  }

  private void handleTilesetChanged(SharedPreferences sharedPreferences) {
    String value = sharedPreferences.getString("tileset", "default_32");
    SharedPreferences.Editor editor = sharedPreferences.edit();

    if ("CUSTOM".equals(value)) {
      editor.putBoolean("customTiles", true);
    } else {
      editor.putBoolean("customTiles", false);
      Point dims = parseTileDimensions(value);
      editor.putInt("tileW", dims.x);
      editor.putInt("tileH", dims.y);
    }
    editor.apply();
    updateTilesetVisibility();
  }

  private void updateTilesetVisibility() {
    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
    String tileset = prefs.getString("tileset", "default_32");
    boolean isCustom = "CUSTOM".equals(tileset);
    boolean isAscii = "TTY".equals(tileset);

    Preference pickPref = findPreference("pick_custom_tileset");
    Preference wPref = findPreference("customTileW");
    Preference hPref = findPreference("customTileH");
    Preference smoothScalingPref = findPreference("fallbackRenderer");

    if (pickPref != null) pickPref.setVisible(isCustom);
    if (wPref != null) wPref.setVisible(isCustom);
    if (hPref != null) hPref.setVisible(isCustom);
    if (smoothScalingPref != null) smoothScalingPref.setVisible(!isAscii);
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
      androidx.fragment.app.DialogFragment f =
          SliderPreferenceDialogFragment.newInstance(preference.getKey());
      f.show(getChildFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
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
