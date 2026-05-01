package com.tbd.forkfront;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;

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
        return AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
    }

    /**
     * Get the Material 3 linear out slow in easing
     */
    public static TimeInterpolator getLinearOutSlowInEasing(Context context) {
        return AnimationUtils.loadInterpolator(context, android.R.interpolator.linear_out_slow_in);
    }

    /**
     * Get the Material 3 fast out linear in easing
     */
    public static TimeInterpolator getFastOutLinearInEasing(Context context) {
        return AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_linear_in);
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
                .setInterpolator(AnimationUtils.loadInterpolator(view.getContext(), android.R.interpolator.fast_out_slow_in))
                .start();
    }

    /**
     * Fade out a view with Material 3 standard easing and short duration.
     */
    public static void fadeOut(@NonNull final View view) {
        view.animate()
                .alpha(0f)
                .setDuration(DURATION_SHORT_4)
                .setInterpolator(AnimationUtils.loadInterpolator(view.getContext(), android.R.interpolator.fast_out_slow_in))
                .withEndAction(() -> view.setVisibility(View.GONE))
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
                .setInterpolator(AnimationUtils.loadInterpolator(view.getContext(), android.R.interpolator.fast_out_slow_in))
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
                .setInterpolator(AnimationUtils.loadInterpolator(view.getContext(), android.R.interpolator.fast_out_slow_in))
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }
}
