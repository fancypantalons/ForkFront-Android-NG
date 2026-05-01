package com.tbd.forkfront.window;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.R;

import android.content.SharedPreferences;

/**
 * Abstract base class for NH_Window implementations.
 * Manages common state like visibility, blocking mode, and context.
 */
public abstract class AbstractNhWindow implements NH_Window {
    protected final int mWid;
    protected EngineCommandSender mIO;
    protected AppCompatActivity mContext;
    protected NH_State mState;
    protected boolean mIsBlocking;
    protected boolean mIsVisible;

    public AbstractNhWindow(int wid, AppCompatActivity context, EngineCommandSender io) {
        mWid = wid;
        mContext = context;
        mIO = io;
        mState = new androidx.lifecycle.ViewModelProvider(context).get(NetHackViewModel.class).getState();
    }

    @Override
    public int id() {
        return mWid;
    }

    @Override
    public void setContext(AppCompatActivity context) {
        mContext = context;
    }

    @Override
    public boolean isVisible() {
        return mIsVisible;
    }

    @Override
    public void preferencesUpdated(SharedPreferences prefs) {
        // Default no-op
    }

    protected void addFragment(Fragment fragment) {
        if (mContext != null && !fragment.isAdded()) {
            mContext.getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.window_fragment_host, fragment, "nhw_" + mWid)
                .commitAllowingStateLoss();
        }
    }

    protected void removeFragment() {
        if (mContext != null) {
            Fragment fragment = mContext.getSupportFragmentManager().findFragmentByTag("nhw_" + mWid);
            if (fragment != null) {
                mContext.getSupportFragmentManager()
                    .beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss();
            }
        }
    }

    public void close() {
        mIsVisible = false;
        if (mIsBlocking) {
            mIO.sendKeyCmd(' ');
        }
    }
}
