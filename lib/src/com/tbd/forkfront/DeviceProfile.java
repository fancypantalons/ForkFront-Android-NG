package com.tbd.forkfront;

import android.content.Context;
import android.os.Build;

/**
 * Heuristics for detecting specific hardware devices (like Ayn Thor).
 */
public final class DeviceProfile {
    public static String detect(Context ctx) {
        if (isAynThor(ctx)) return "thor";
        return null;
    }

    private static boolean isAynThor(Context ctx) {
        // Primary heuristic: manufacturer / model strings.
        if ("AYN".equalsIgnoreCase(Build.MANUFACTURER)
                && Build.MODEL != null
                && Build.MODEL.toLowerCase().contains("thor")) {
            return true;
        }
        // Fallback heuristic: a presentation display matching the Thor's
        // ~3.92" 31:27 aspect ratio.
        return matchesThorSecondaryDisplay(ctx);
    }

    private static boolean matchesThorSecondaryDisplay(Context ctx) {
        android.hardware.display.DisplayManager dm = (android.hardware.display.DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return false;
        
        android.view.Display[] displays = dm.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (android.view.Display d : displays) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            d.getRealMetrics(metrics);
            
            // Thor secondary: width ~3.1", height ~2.7" (diagonal ~4.1" but active area ~3.92")
            float widthInches = metrics.widthPixels / metrics.xdpi;
            float heightInches = metrics.heightPixels / metrics.ydpi;
            
            if (widthInches > 2.9f && widthInches < 3.3f && 
                heightInches > 2.5f && heightInches < 2.9f) {
                return true;
            }
        }
        return false;
    }
}
