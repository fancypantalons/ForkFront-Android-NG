package com.tbd.forkfront;

import android.app.Presentation;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ContextThemeWrapper;
import android.view.Display;
import android.view.WindowManager;

/**
 * A Presentation that hosts a WidgetLayout on a secondary display.
 */
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
        String themeMode = PreferenceManager.getDefaultSharedPreferences(getContext()).getString("theme_mode", "-1");
        int mode = Integer.parseInt(themeMode);
        
        int nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM || mode == -1) {
            // Follow system: sync with the outer context (Activity) which is managed by AppCompatDelegate
            nightMode = mOuterContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        }

        if (nightMode != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            Configuration config = new Configuration(getContext().getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
            getContext().getResources().updateConfiguration(config, getContext().getResources().getDisplayMetrics());
        }
        
        getContext().setTheme(R.style.Theme_ForkFront);
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
