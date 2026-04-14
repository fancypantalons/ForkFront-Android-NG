package com.tbd.forkfront;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Determines the current layout configuration key based on device characteristics.
 * Used to select the appropriate widget layout (stock or user-customized).
 */
public class LayoutConfiguration {
    
    /**
     * Get the layout key for the current device configuration.
     * 
     * @param context Android context
     * @return Layout key string (e.g., "portrait", "landscape", "tablet_portrait")
     */
    public static String getCurrentLayoutKey(Context context) {
        Configuration config = context.getResources().getConfiguration();
        int orientation = config.orientation;
        int screenLayout = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        
        // Determine if device is a tablet (large or xlarge screen)
        boolean isTablet = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        
        // For now, just distinguish phone vs tablet, portrait vs landscape
        // Future: could add foldable detection, multi-window, etc.
        if (isTablet) {
            return isLandscape ? "tablet_landscape" : "tablet_portrait";
        } else {
            return isLandscape ? "landscape" : "portrait";
        }
    }
    
    /**
     * Check if a given orientation/configuration has a stock layout defined.
     * 
     * @param context Android context
     * @param layoutKey Layout configuration key
     * @return true if stock layout exists in assets
     */
    public static boolean hasStockLayout(Context context, String layoutKey) {
        try {
            String[] files = context.getAssets().list("default_layouts");
            if (files != null) {
                for (String file : files) {
                    if (file.equals(layoutKey + ".json")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("LayoutConfiguration", "Error checking for stock layout", e);
        }
        return false;
    }
}
