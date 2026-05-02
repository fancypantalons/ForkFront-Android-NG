package com.tbd.forkfront.dialog;

import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.engine.NetHackViewModel;

/**
 * Base class for modal UI components (dialogs/questions). Manages the root view and provides common
 * helpers for show/dismiss.
 */
public abstract class BaseModalUi {
  protected final AppCompatActivity mContext;
  protected final NH_State mState;
  protected final EngineCommandSender mIO;
  protected View mRoot;
  protected boolean mIsDisabled;

  public BaseModalUi(AppCompatActivity context, EngineCommandSender io) {
    mContext = context;
    mIO = io;
    mState =
        new androidx.lifecycle.ViewModelProvider(context).get(NetHackViewModel.class).getState();
  }

  public void dismiss() {
    if (mRoot != null) {
      ViewGroup parent = (ViewGroup) mRoot.getParent();
      if (parent != null) {
        parent.removeView(mRoot);
      }
      mRoot = null;
    }
  }

  public boolean isShowing() {
    return mRoot != null && mRoot.getVisibility() == View.VISIBLE;
  }
}
