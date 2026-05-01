package com.tbd.forkfront;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

/**
 * A Presentation that hosts a WidgetLayout on a secondary display.
 */
public class SecondaryScreenPresentation extends Presentation {

    private WidgetLayout mWidgetLayout;

    public SecondaryScreenPresentation(Context outerContext, Display display) {
        super(outerContext, display, R.style.Theme_ForkFront);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

    public void wireButtons(NH_State state) {
        if (state != null && mWidgetLayout != null) {
            state.wireWidgetLayoutButtons(mWidgetLayout, mWidgetLayout.getRootView());
        }
    }

    public WidgetLayout getWidgetLayout() {
        return mWidgetLayout;
    }
}
