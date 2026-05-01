package com.tbd.forkfront;

import android.app.Application;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity/Application/ViewModel binding.
 * Manages the lifecycle relationship between the game state and the Android Activity.
 */
public final class ActivityScope {
    private final Application mApp;
    private volatile AppCompatActivity mActivity; // volatile for safe publication
    private NetHackViewModel mViewModel;

    public ActivityScope(Application app, NetHackViewModel vm) {
        mApp = app;
        mViewModel = vm;
    }

    public Application getApp() {
        return mApp;
    }

    @Nullable
    @MainThread
    public AppCompatActivity getActivity() {
        return mActivity;
    }

    @MainThread
    public void setActivity(@Nullable AppCompatActivity a) {
        mActivity = a;
    }

    @AnyThread
    public void runOnActivity(Runnable r) {
        if (mViewModel != null) {
            mViewModel.runOnActivity(r);
        }
    }

    public void setViewModel(NetHackViewModel vm) {
        mViewModel = vm;
    }
}
