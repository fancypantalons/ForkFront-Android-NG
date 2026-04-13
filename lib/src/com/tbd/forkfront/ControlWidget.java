package com.tbd.forkfront;

import android.content.Context;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

/**
 * A wrapper view that adds dragging and resizing capabilities to any content view.
 */
public class ControlWidget extends FrameLayout {

    private View mContentView;
    private View mResizeHandle;
    private View mBorderView;
    private boolean mEditMode = false;
    
    private float mLastTouchX;
    private float mLastTouchY;
    private boolean mIsDragging = false;
    private boolean mIsResizing = false;
    
    private GestureDetector mGestureDetector;

    public interface OnWidgetChangeListener {
        void onWidgetChanged(ControlWidget widget);
    }

    public interface OnWidgetLongClickListener {
        void onWidgetLongClick(ControlWidget widget);
    }
    
    private OnWidgetChangeListener mChangeListener;
    private OnWidgetLongClickListener mLongClickListener;

    public static class WidgetData {
        public String type;
        public String label;
        public String command;
        public boolean horizontal = true;
        public float x, y;
        public int w, h;
    }

    private WidgetData mData = new WidgetData();

    public ControlWidget(@NonNull Context context, View contentView, String type) {
        super(context);
        mContentView = contentView;
        mData.type = type;
        
        // Add content view
        addView(mContentView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        // Add border for edit mode
        mBorderView = new View(context);
        mBorderView.setBackgroundResource(R.drawable.widget_border);
        mBorderView.setVisibility(GONE);
        addView(mBorderView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        // Add resize handle
        mResizeHandle = new ImageView(context);
        mResizeHandle.setBackgroundResource(R.drawable.ic_resize_handle);
        LayoutParams handleParams = new LayoutParams(
                (int)(32 * context.getResources().getDisplayMetrics().density),
                (int)(32 * context.getResources().getDisplayMetrics().density)
        );
        handleParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        mResizeHandle.setLayoutParams(handleParams);
        mResizeHandle.setVisibility(GONE);
        mResizeHandle.setPadding(8, 8, 0, 0); // Increase visual size / padding
        addView(mResizeHandle);
        
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                if (mEditMode && mLongClickListener != null) {
                    mLongClickListener.onWidgetLongClick(ControlWidget.this);
                }
            }
        });

        setupTouchListeners();
    }

    public void setEditMode(boolean enabled) {
        mEditMode = enabled;
        mBorderView.setVisibility(enabled ? VISIBLE : GONE);
        mResizeHandle.setVisibility(enabled ? VISIBLE : GONE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mEditMode) {
            // In edit mode, we want to see all touches first
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private void setupTouchListeners() {
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mEditMode) return false;

                // Pass to gesture detector for long press
                mGestureDetector.onTouchEvent(event);

                float x = event.getRawX();
                float y = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastTouchX = x;
                        mLastTouchY = y;
                        
                        // Check if touch is on resize handle
                        int[] location = new int[2];
                        mResizeHandle.getLocationOnScreen(location);
                        if (x >= location[0] && x <= location[0] + mResizeHandle.getWidth() &&
                            y >= location[1] && y <= location[1] + mResizeHandle.getHeight()) {
                            mIsResizing = true;
                        } else {
                            mIsDragging = true;
                        }
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - mLastTouchX;
                        float dy = y - mLastTouchY;
                        
                        if (mIsResizing) {
                            LayoutParams params = (LayoutParams) getLayoutParams();
                            params.width = (int) Math.max(100, getWidth() + dx);
                            params.height = (int) Math.max(100, getHeight() + dy);
                            setLayoutParams(params);
                        } else if (mIsDragging) {
                            setX(getX() + dx);
                            setY(getY() + dy);
                        }
                        
                        mLastTouchX = x;
                        mLastTouchY = y;
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mIsDragging = false;
                        mIsResizing = false;
                        if (mChangeListener != null) {
                            mChangeListener.onWidgetChanged(ControlWidget.this);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public void setOnWidgetChangeListener(OnWidgetChangeListener listener) {
        mChangeListener = listener;
    }

    public void setOnWidgetLongClickListener(OnWidgetLongClickListener listener) {
        mLongClickListener = listener;
    }
    
    public WidgetData getWidgetData() {
        mData.x = getX();
        mData.y = getY();
        
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) {
            mData.w = params.width;
            mData.h = params.height;
        } else {
            if (mData.w <= 0) mData.w = 200;
            if (mData.h <= 0) mData.h = 200;
        }
        return mData;
    }

    public void setWidgetData(WidgetData data) {
        this.mData = data;
        setX(data.x);
        setY(data.y);
        LayoutParams params = (LayoutParams) getLayoutParams();
        if (params == null) params = new LayoutParams(data.w, data.h);
        params.width = data.w;
        params.height = data.h;
        setLayoutParams(params);
    }

    public View getContentView() {
        return mContentView;
    }

    public void pulseAttention() {
        // Briefly show and pulse the border to draw attention
        if (mBorderView != null && !mEditMode) {
            mBorderView.setVisibility(VISIBLE);
            mBorderView.setAlpha(0f);
            mBorderView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        mBorderView.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .setStartDelay(200)
                                .withEndAction(() -> mBorderView.setVisibility(GONE))
                                .start();
                    })
                    .start();
        }
    }
}
