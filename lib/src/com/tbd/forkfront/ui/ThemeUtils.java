package com.tbd.forkfront.ui;

import android.content.Context;
import android.util.TypedValue;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.tbd.forkfront.R;

public final class ThemeUtils {
  private ThemeUtils() {}

  public static MaterialButton createButtonText(Context context) {
    MaterialButton btn =
        new MaterialButton(
            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    float density = context.getResources().getDisplayMetrics().density;
    btn.setCornerRadius((int) (8 * density));
    btn.setInsetTop(0);
    btn.setInsetBottom(0);
    btn.setMaxLines(1);
    btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
    btn.setHorizontallyScrolling(true);
    btn.setForeground(ContextCompat.getDrawable(context, R.drawable.gamepad_focus_selector));

    // Add solid surface background so translucency setting is visible over the map
    int surfaceColor = resolveColor(context, R.attr.colorSurface, R.color.md_theme_surface);
    btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surfaceColor));

    // Use high-contrast on-surface color for text and outline
    int onSurfaceColor = resolveColor(context, R.attr.colorOnSurface, R.color.md_theme_onSurface);
    btn.setTextColor(onSurfaceColor);
    btn.setStrokeColor(android.content.res.ColorStateList.valueOf(onSurfaceColor));

    return btn;
  }

  /**
   * Resolve a color theme attribute for the given context, falling back to a color resource if the
   * attribute can't be resolved. The fallback resource is read via ContextCompat, so it will
   * respect the configuration's night-mode (values-night/colors.xml wins in dark mode).
   */
  @ColorInt
  public static int resolveColor(
      Context context, @AttrRes int themeAttr, @ColorRes int colorResFallback) {
    TypedValue tv = new TypedValue();
    if (context.getTheme().resolveAttribute(themeAttr, tv, true)) {
      return tv.data;
    }
    return ContextCompat.getColor(context, colorResFallback);
  }
}
