package com.tbd.forkfront;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

/**
 * A dynamic, scrollable container that shows buttons based on current game context.
 */
public class ContextualActionBarView extends FrameLayout {

    public interface OnActionSelectedListener {
        void onAction(CmdRegistry.CmdInfo cmd);
    }

    private OnActionSelectedListener mListener;
    private LinearLayout mButtonContainer;
    private ScrollView mVerticalScroll;
    private HorizontalScrollView mHorizontalScroll;
    private boolean mIsHorizontal = true;

    public ContextualActionBarView(Context context) {
        super(context);
        init(context);
    }

    public ContextualActionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mVerticalScroll = new ScrollView(context);
        mHorizontalScroll = new HorizontalScrollView(context);
        
        mButtonContainer = new LinearLayout(context);
        mButtonContainer.setPadding(8, 8, 8, 8);
        
        updateOrientation();
    }

    public void setOrientation(boolean horizontal) {
        if (mIsHorizontal != horizontal) {
            mIsHorizontal = horizontal;
            updateOrientation();
        }
    }

    private void updateOrientation() {
        removeAllViews();
        mVerticalScroll.removeAllViews();
        mHorizontalScroll.removeAllViews();
        
        mButtonContainer.setOrientation(mIsHorizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        
        if (mIsHorizontal) {
            mHorizontalScroll.addView(mButtonContainer, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            addView(mHorizontalScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            mVerticalScroll.addView(mButtonContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            addView(mVerticalScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    public void updateActions(List<CmdRegistry.CmdInfo> actions) {
        mButtonContainer.removeAllViews();
        if (actions == null || actions.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);
        for (final CmdRegistry.CmdInfo cmd : actions) {
            MaterialButton btn = ThemeUtils.createButtonText(getContext());
            btn.setText(cmd.getDisplayName());
            btn.setPadding(16, 8, 16, 8);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                mIsHorizontal ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                mIsHorizontal ? LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onAction(cmd);
                }
            });

            mButtonContainer.addView(btn);
        }
    }

    public void setOnActionSelectedListener(OnActionSelectedListener listener) {
        mListener = listener;
    }
}
