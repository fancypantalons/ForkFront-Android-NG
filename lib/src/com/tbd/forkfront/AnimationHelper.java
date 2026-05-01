package com.tbd.forkfront;

import android.content.Context;
import android.view.View;
import android.view.animation.Interpolator;
import android.animation.TimeInterpolator;
import androidx.annotation.NonNull;
import com.google.android.material.animation.AnimationUtils;

/**
 * Utility class for Material 3 motion and animations.
 */
public class AnimationHelper {

    /** Standard Material 3 Durations (approximate from M3 guidelines) */
    public static final int DURATION_SHORT_1 = 50;
    public static final int DURATION_SHORT_2 = 100;
    public static final int DURATION_SHORT_3 = 150;
    public static final int DURATION_SHORT_4 = 200;
    public static final int DURATION_MEDIUM_1 = 250;
    public static final int DURATION_MEDIUM_2 = 300;
    public static final int DURATION_MEDIUM_3 = 350;
    public static final int DURATION_MEDIUM_4 = 400;
    public static final int DURATION_LONG_1 = 450;
    public static final int DURATION_LONG_2 = 500;
    public static final int DURATION_LONG_3 = 550;
    public static final int DURATION_LONG_4 = 600;

    /**
     * Get the Material 3 standard easing (FastOutSlowIn equivalent)
     */
    public static TimeInterpolator getStandardEasing(Context context) {
        return AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR;
    }

    /**
     * Get the Material 3 linear out slow in easing
     */
    public static TimeInterpolator getLinearOutSlowInEasing(Context context) {
        return AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR;
    }

    /**
     * Get the Material 3 fast out linear in easing
     */
    public static TimeInterpolator getFastOutLinearInEasing(Context context) {
        return AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR;
    }

    /**
     * Fade in a view with Material 3 standard easing and medium duration.
     */
    public static void fadeIn(@NonNull View view) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(DURATION_MEDIUM_1)
                .setInterpolator(AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR)
                .start();
    }

    /**
     * Fade out a view with Material 3 standard easing and short duration.
     */
    public static void fadeOut(@NonNull final View view) {
        view.animate()
                .alpha(0f)
                .setDuration(DURATION_SHORT_4)
                .setInterpolator(AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    /**
     * Slide a view in from the bottom.
     */
    public static void slideInBottom(@NonNull View view) {
        view.setTranslationY(view.getHeight() > 0 ? view.getHeight() : 500f);
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(DURATION_MEDIUM_2)
                .setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR)
                .start();
    }

    /**
     * Slide a view out to the bottom.
     */
    public static void slideOutBottom(@NonNull final View view) {
        view.animate()
                .translationY(view.getHeight() > 0 ? view.getHeight() : 500f)
                .alpha(0f)
                .setDuration(DURATION_SHORT_4)
                .setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.setVisibility(View.GONE);
                    }
                })
                .start();
    }
}
