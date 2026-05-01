package com.tbd.forkfront;

import android.view.View;
import android.view.WindowManager;
import androidx.annotation.MainThread;

/**
 * Manages system UI visibility and immersive mode flags.
 */
public final class SystemUiController {
    private final ActivityScope mScope;

    public SystemUiController(ActivityScope scope) {
        mScope = scope;
    }

    @MainThread
    public void applyImmersiveFlags() {
        // Window operations require Activity context
        if (mScope.getActivity() == null) {
            return;
        }

        mScope.getActivity().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        int uiVisibilityFlags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY 
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION 
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        
        mScope.getActivity().getWindow().getDecorView().setSystemUiVisibility(uiVisibilityFlags);
    }
}
