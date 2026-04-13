package com.tbd.forkfront;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
    
    public interface OnWidgetChangeListener {
        void onWidgetChanged(ControlWidget widget);
    }
    
    private OnWidgetChangeListener mChangeListener;

    public static class WidgetData {
        public String type;
        public String label;
        public String command;
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
                (int)(24 * context.getResources().getDisplayMetrics().density),
                (int)(24 * context.getResources().getDisplayMetrics().density)
        );
        handleParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        mResizeHandle.setLayoutParams(handleParams);
        mResizeHandle.setVisibility(GONE);
        addView(mResizeHandle);
        
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
            float x = ev.getX();
            float y = ev.getY();
            if (x >= mResizeHandle.getLeft() && x <= mResizeHandle.getRight() &&
                y >= mResizeHandle.getTop() && y <= mResizeHandle.getBottom()) {
                return false;
            }
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private void setupTouchListeners() {
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mEditMode) return false;

                float x = event.getRawX();
                float y = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastTouchX = x;
                        mLastTouchY = y;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - mLastTouchX;
                        float dy = y - mLastTouchY;
                        
                        setX(getX() + dx);
                        setY(getY() + dy);
                        
                        mLastTouchX = x;
                        mLastTouchY = y;
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        if (mChangeListener != null) {
                            mChangeListener.onWidgetChanged(ControlWidget.this);
                        }
                        return true;
                }
                return false;
            }
        });

        mResizeHandle.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getRawX();
                float y = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mLastTouchX = x;
                        mLastTouchY = y;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - mLastTouchX;
                        float dy = y - mLastTouchY;
                        
                        LayoutParams params = (LayoutParams) getLayoutParams();
                        params.width = (int) Math.max(100, getWidth() + dx);
                        params.height = (int) Math.max(100, getHeight() + dy);
                        setLayoutParams(params);
                        
                        mLastTouchX = x;
                        mLastTouchY = y;
                        return true;
                        
                    case MotionEvent.ACTION_UP:
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
    
    public WidgetData getWidgetData() {
        mData.x = getX();
        mData.y = getY();
        
        android.view.ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) {
            mData.w = params.width;
            mData.h = params.height;
        } else {
            // Provide safe defaults if not yet attached to a layout
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
}
