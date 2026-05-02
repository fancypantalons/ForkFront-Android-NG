package com.tbd.forkfront.ui;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.R;
import com.tbd.forkfront.settings.WidgetLayout;

/** A Presentation that hosts a WidgetLayout on a secondary display. */
public class SecondaryScreenPresentation extends Presentation {

  private WidgetLayout mWidgetLayout;
  private final Context mOuterContext;

  public SecondaryScreenPresentation(Context outerContext, Display display) {
    super(outerContext, display);
    mOuterContext = outerContext;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    applyTheme();
    super.onCreate(savedInstanceState);

    // FLAG_NOT_FOCUSABLE ensures that touches on the secondary screen don't
    // steal focus from the primary Activity (important for IME and window focus).
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

    setContentView(R.layout.secondary_window);
    mWidgetLayout = findViewById(R.id.secondary_widget_layout);
    mWidgetLayout.setScreenId("secondary");

    // Wire up edit buttons if NH_State is available
    // Note: ForkFront will call attachSecondaryWidgetLayout which triggers more setup
  }

  private void applyTheme() {
    // Inherit night mode from the outer activity context; no manual override needed.
    getContext().setTheme(R.style.Theme_ForkFront);
  }

  public void wireButtons(NH_State state) {
    if (state != null && mWidgetLayout != null) {
      state.getWidgets().wireButtons(mWidgetLayout, mWidgetLayout.getRootView());
    }
  }

  public WidgetLayout getWidgetLayout() {
    return mWidgetLayout;
  }

  /**
   * Re-applies the current theme and re-inflates the layout. Call this when the theme may have
   * changed (e.g. after returning from Settings). Returns the new WidgetLayout for re-attachment.
   */
  public WidgetLayout refreshTheme() {
    applyTheme();
    setContentView(R.layout.secondary_window);
    mWidgetLayout = findViewById(R.id.secondary_widget_layout);
    mWidgetLayout.setScreenId("secondary");
    return mWidgetLayout;
  }
}
