package com.tbd.forkfront.ui;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Determines the current layout configuration key based on device characteristics. Used to select
 * the appropriate widget layout (stock or user-customized).
 */
public class LayoutConfiguration {

  /**
   * Get the layout key for the current device configuration.
   *
   * @param context Android context
   * @return Layout key string (e.g., "portrait", "landscape", "tablet_portrait")
   */
  public static String getCurrentLayoutKey(Context context) {
    return getLayoutKey(context, "primary");
  }

  /**
   * Get the layout key for the specified screen and current device configuration.
   *
   * @param context Android context
   * @param screen Screen identifier ("primary", "secondary")
   * @return Layout key string
   */
  public static String getLayoutKey(Context context, String screen) {
    Configuration config = context.getResources().getConfiguration();
    int orientation = config.orientation;
    int screenLayout = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;

    // Determine if device is a tablet (large or xlarge screen)
    boolean isTablet = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

    if (isTablet) {
      return isLandscape ? "tablet_landscape" : "tablet_portrait";
    } else {
      return isLandscape ? "landscape" : "portrait";
    }
  }

  /**
   * Check if a given screen/orientation/configuration has a stock layout defined.
   *
   * @param context Android context
   * @param screenId Screen identifier
   * @param layoutKey Layout configuration key
   * @return true if stock layout exists in assets
   */
  public static boolean hasStockLayout(Context context, String screenId, String layoutKey) {
    return hasStockLayout(context, screenId, null, layoutKey);
  }

  /** Check if a given screen/device/orientation has a stock layout defined. */
  public static boolean hasStockLayout(
      Context context, String screenId, String deviceKey, String layoutKey) {
    try {
      // Check device-specific first: {screen}/{device}_{orientation}.json
      if (deviceKey != null) {
        String deviceSpecific = screenId + "/" + deviceKey + "_" + layoutKey + ".json";
        if (assetExists(context, "default_layouts/" + deviceSpecific)) {
          return true;
        }
      }

      // Check generic screen: {screen}/{orientation}.json
      String screenGeneric = screenId + "/" + layoutKey + ".json";
      if (assetExists(context, "default_layouts/" + screenGeneric)) {
        return true;
      }

      // Legacy fallback: {orientation}.json (only for primary)
      if ("primary".equals(screenId)) {
        if (assetExists(context, "default_layouts/" + layoutKey + ".json")) {
          return true;
        }
      }
    } catch (Exception e) {
      android.util.Log.e("LayoutConfiguration", "Error checking for stock layout", e);
    }
    return false;
  }

  private static boolean assetExists(Context context, String path) {
    try {
      int lastSlash = path.lastIndexOf('/');
      String dir = lastSlash == -1 ? "" : path.substring(0, lastSlash);
      String file = lastSlash == -1 ? path : path.substring(lastSlash + 1);
      String[] files = context.getAssets().list(dir);
      if (files != null) {
        for (String f : files) {
          if (f.equals(file)) {
            return true;
          }
        }
      }
    } catch (Exception e) {
      // Ignore
    }
    return false;
  }
}
