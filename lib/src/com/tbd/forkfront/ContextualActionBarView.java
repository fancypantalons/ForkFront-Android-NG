package com.tbd.forkfront;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import java.util.List;

/**
 * A dynamic container that shows buttons based on current game context.
 */
public class ContextualActionBarView extends LinearLayout {

    public interface OnActionSelectedListener {
        void onAction(CmdRegistry.CmdInfo cmd);
    }

    private OnActionSelectedListener mListener;

    public ContextualActionBarView(Context context) {
        super(context);
        init();
    }

    public ContextualActionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setPadding(8, 8, 8, 8);
    }

    public void setOrientation(boolean horizontal) {
        setOrientation(horizontal ? HORIZONTAL : VERTICAL);
    }

    public void updateActions(List<CmdRegistry.CmdInfo> actions) {
        removeAllViews();
        if (actions == null || actions.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);
        for (final CmdRegistry.CmdInfo cmd : actions) {
            MaterialButton btn = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonStyle);
            btn.setText(cmd.getDisplayName());
            btn.setPadding(16, 8, 16, 8);
            
            LayoutParams params = new LayoutParams(
                getOrientation() == HORIZONTAL ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT,
                getOrientation() == HORIZONTAL ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT
            );
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onAction(cmd);
                }
            });

            addView(btn);
        }
    }

    public void setOnActionSelectedListener(OnActionSelectedListener listener) {
        mListener = listener;
    }
}
