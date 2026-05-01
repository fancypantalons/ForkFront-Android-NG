package com.tbd.forkfront.ui;

import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.WindowManager;

import com.tbd.forkfront.ForkFront;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.settings.WidgetLayout;

public class SecondaryDisplayController implements DisplayManager.DisplayListener {

    private final ForkFront mActivity;
    private final DisplayManager mDisplayManager;
    private SecondaryScreenPresentation mPresentation;

    public SecondaryDisplayController(ForkFront activity) {
        mActivity = activity;
        mDisplayManager = (DisplayManager) activity.getSystemService(android.content.Context.DISPLAY_SERVICE);
    }

    public void onResume() {
        mDisplayManager.registerDisplayListener(this, null);
        boolean hadPresentation = mPresentation != null;
        updateSecondaryDisplay();
        if (hadPresentation && mPresentation != null) {
            WidgetLayout refreshedLayout = mPresentation.refreshTheme();
            NH_State state = mActivity.getState();
            if (state != null) {
                state.getWidgets().attachSecondaryWidgetLayout(refreshedLayout);
                mPresentation.wireButtons(state);
            }
        }
    }

    public void onPause() {
        mDisplayManager.unregisterDisplayListener(this);
    }

    public void onDestroy() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    private void updateSecondaryDisplay() {
        Display[] displays = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if (displays.length > 0) {
            Display display = displays[0];
            if (mPresentation == null || mPresentation.getDisplay() != display) {
                if (mPresentation != null) {
                    mPresentation.dismiss();
                }
                mPresentation = new SecondaryScreenPresentation(mActivity, display);
                try {
                    mPresentation.show();
                    NH_State state = mActivity.getState();
                    if (state != null) {
                        state.getWidgets().attachSecondaryWidgetLayout(mPresentation.getWidgetLayout());
                        mPresentation.wireButtons(state);
                    }
                } catch (WindowManager.InvalidDisplayException e) {
                    mPresentation = null;
                }
            }
        } else {
            if (mPresentation != null) {
                mPresentation.dismiss();
                mPresentation = null;
                NH_State state = mActivity.getState();
                if (state != null) {
                    state.getWidgets().detachSecondaryWidgetLayout();
                }
            }
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        updateSecondaryDisplay();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        updateSecondaryDisplay();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        updateSecondaryDisplay();
    }
}
