package com.tbd.forkfront;

import android.graphics.Rect;
import androidx.annotation.MainThread;
import java.util.function.Supplier;

/**
 * Coordinates map cursor and view operations.
 */
public final class MapInputCoordinator {
    private final ActivityScope mScope;
    private final Supplier<NHW_Map> mMapProvider;

    public MapInputCoordinator(ActivityScope scope, Supplier<NHW_Map> mapProvider) {
        mScope = scope;
        mMapProvider = mapProvider;
    }

    @MainThread
    public void enterMapCursorMode() {
        mScope.runOnActivity(() -> {
            NHW_Map map = mMapProvider.get();
            if (map != null) map.beginGamepadCursor();
        });
    }

    @MainThread
    public void exitMapCursorMode() {
        mScope.runOnActivity(() -> {
            NHW_Map map = mMapProvider.get();
            if (map != null) map.endGamepadCursor();
        });
    }

    @MainThread
    public void clickCursorPos() {
        NHW_Map map = mMapProvider.get();
        if (map != null) {
            map.onCursorPosClicked();
        }
    }

    @MainThread
    public void zoomIn() {
        NHW_Map map = mMapProvider.get();
        if (map != null) map.zoom(1.0f);
    }

    @MainThread
    public void zoomOut() {
        NHW_Map map = mMapProvider.get();
        if (map != null) map.zoom(-1.0f);
    }

    @MainThread
    public void recenterMap() {
        NHW_Map map = mMapProvider.get();
        if (map != null) {
            android.graphics.Point p = map.getPlayerPos();
            if (p != null) map.centerView(p.x, p.y);
        }
    }

    @MainThread
    public void viewAreaChanged(Rect viewRect) {
        NHW_Map map = mMapProvider.get();
        if (map != null) {
            map.viewAreaChanged(viewRect);
        }
    }

    @MainThread
    public boolean handleKeyUp(int keyCode) {
        NHW_Map map = mMapProvider.get();
        if (map != null && map.handleKeyUp(keyCode)) {
            return true;
        }
        return false;
    }

    @MainThread
    public void showControls() {
        // Controls visibility is now managed by WidgetLayout
    }

    @MainThread
    public void hideControls() {
        // Controls visibility is now managed by WidgetLayout
    }
}
