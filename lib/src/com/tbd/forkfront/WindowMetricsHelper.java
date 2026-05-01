package com.tbd.forkfront;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * Helper class for obtaining window dimensions using the WindowMetrics API.
 * Requires API 33+ (Android 13+).
 */
public class WindowMetricsHelper {

    /**
     * Get the current window bounds (includes area behind system UI).
     *
     * @param context Context (should be an Activity context when possible)
     * @return Rect representing the window bounds
     */
    public static Rect getWindowBounds(Context context) {
        WindowManager wm = context.getSystemService(WindowManager.class);
        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        return metrics.getBounds();
    }

    /**
     * Get the current window bounds excluding system UI (status bar, navigation bar).
     * This represents the actual usable area for positioning widgets.
     *
     * @param context Context (should be an Activity context when possible)
     * @return Rect representing the safe bounds
     */
    public static Rect getSafeBounds(Context context) {
        WindowManager wm = context.getSystemService(WindowManager.class);
        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        WindowInsets windowInsets = metrics.getWindowInsets();

        Insets insets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars()
        );

        Rect bounds = metrics.getBounds();
        return new Rect(
            bounds.left + insets.left,
            bounds.top + insets.top,
            bounds.right - insets.right,
            bounds.bottom - insets.bottom
        );
    }

    /**
     * Get the current window width in pixels.
     */
    public static int getWindowWidth(Context context) {
        return getWindowBounds(context).width();
    }

    /**
     * Get the current window height in pixels.
     */
    public static int getWindowHeight(Context context) {
        return getWindowBounds(context).height();
    }

    /**
     * Get the current window bounds excluding display cutout only.
     * This represents the usable area for immersive fullscreen layouts,
     * where system bars are transient overlays rather than reserved space.
     *
     * @param context Context (should be an Activity context when possible)
     * @return Rect representing the layout bounds
     */
    public static Rect getLayoutBounds(Context context) {
        WindowManager wm = context.getSystemService(WindowManager.class);
        WindowMetrics metrics = wm.getCurrentWindowMetrics();
        WindowInsets windowInsets = metrics.getWindowInsets();

        Insets cutout = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.displayCutout()
        );

        Rect bounds = metrics.getBounds();
        return new Rect(
            bounds.left + cutout.left,
            bounds.top + cutout.top,
            bounds.right - cutout.right,
            bounds.bottom - cutout.bottom
        );
    }

    /**
     * Get the safe window width in pixels (excluding system UI).
     */
    public static int getSafeWidth(Context context) {
        return getSafeBounds(context).width();
    }

    /**
     * Get the safe window height in pixels (excluding system UI).
     */
    public static int getSafeHeight(Context context) {
        return getSafeBounds(context).height();
    }
}
