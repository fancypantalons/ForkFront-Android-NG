package com.tbd.forkfront.settings;

import android.content.SharedPreferences;
import androidx.annotation.MainThread;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.gamepad.GamepadDispatcher;
import com.tbd.forkfront.ui.ActivityScope;
import com.tbd.forkfront.ui.DeviceProfile;
import com.tbd.forkfront.ui.SystemUiController;
import com.tbd.forkfront.window.WindowRegistry;
import com.tbd.forkfront.window.map.NHW_Map;
import com.tbd.forkfront.window.map.Tileset;
import com.tbd.forkfront.window.message.NHW_Message;
import com.tbd.forkfront.window.message.NHW_Status;
import java.util.function.Supplier;

/** Coordinates preference changes and distributes them to relevant components. */
public final class PreferencesCoordinator {
  private final ActivityScope mScope;
  private final WindowRegistry mWindows;
  private final Tileset mTileset;
  private final Supplier<NHW_Map> mMapProvider;
  private final Supplier<NHW_Status> mStatusProvider;
  private final Supplier<NHW_Message> mMessageProvider;
  private final SystemUiController mSysUi;
  private final NH_State
      mNHState; // Temporary collaborator until WidgetLayoutController is extracted
  private String mDeviceKey;

  public PreferencesCoordinator(
      ActivityScope scope,
      WindowRegistry windows,
      Tileset tileset,
      Supplier<NHW_Map> map,
      Supplier<NHW_Status> status,
      Supplier<NHW_Message> message,
      SystemUiController sysUi,
      NH_State nhState) {
    mScope = scope;
    mWindows = windows;
    mTileset = tileset;
    mMapProvider = map;
    mStatusProvider = status;
    mMessageProvider = message;
    mSysUi = sysUi;
    mNHState = nhState;
  }

  @MainThread
  public void apply() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mScope.getApp());

    // Notify the gamepad dispatcher so it can rebuild the binding map
    GamepadDispatcher gd = GamepadDispatcher.getInstance();
    if (gd != null) {
      gd.reloadFromPreferences();
    }

    mNHState.getWidgets().setEditMode(prefs.getBoolean("edit_mode", false));

    NHW_Map map = mMapProvider.get();
    NHW_Status status = mStatusProvider.get();
    NHW_Message message = mMessageProvider.get();

    if (map != null) {
      map.preferencesUpdated(prefs);
    }
    if (status != null) {
      status.preferencesUpdated(prefs);
    }
    if (message != null) {
      message.preferencesUpdated(prefs);
    }

    mWindows.forEachExcept(w -> w.preferencesUpdated(prefs), map, status, message);

    mTileset.updateTileset(prefs, mScope.getApp().getResources());

    if (map != null) {
      map.updateZoomLimits();
    }

    mSysUi.applyImmersiveFlags();
  }

  @MainThread
  public String getLastUsername() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mScope.getApp());
    return prefs.getString("lastUsername", "");
  }

  @MainThread
  public void setLastUsername(String username) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mScope.getApp());
    prefs.edit().putString("lastUsername", username).apply();
  }

  @MainThread
  public String getDeviceKey() {
    if (mDeviceKey == null && mScope.getApp() != null) {
      mDeviceKey = DeviceProfile.detect(mScope.getApp());
    }
    return mDeviceKey;
  }
}
