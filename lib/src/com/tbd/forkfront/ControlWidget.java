package com.tbd.forkfront;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    private android.widget.TextView mEditPlaceholder;
    private boolean mEditMode = false;
    
    private float mLastTouchX;
    private float mLastTouchY;
    private float mStartX;
    private float mStartY;
    private int mTouchSlop;
    private boolean mIsDragging = false;
    private boolean mIsResizing = false;
    private boolean mHasMovedPastSlop = false;
    
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
        public int opacity = 191; // Default to 75% opacity (191/255)
        public int fontSize = 15; // Default font size (matching StatusWidget/MessageWidget default)
        public int rows = 3; // For command_palette widget
        public int columns = 3; // For command_palette widget
        public String category = null; // For command_palette widget (null = all categories)
        public boolean contextualOnly = false; // For command_palette widget
    }

    private WidgetData mData = new WidgetData();

    public void setFontSize(int size) {
        mData.fontSize = size;
        if (!"status".equals(mData.type) && !"message".equals(mData.type)) {
            applyFontSizeToView(mContentView, size);
        }
    }

    private void applyFontSizeToView(View view, int size) {
        if (view instanceof android.widget.TextView) {
            ((android.widget.TextView) view).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyFontSizeToView(vg.getChildAt(i), size);
            }
        }
    }

    public ControlWidget(Context context) {
        this(context, null, null);
    }

    public ControlWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ControlWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, null, null);
    }

    public ControlWidget(@NonNull Context context, View contentView, String type) {
        super(context);
        init(context, contentView, type);
    }

    private void init(@NonNull Context context, View contentView, String type) {
        mContentView = contentView;
        mData.type = type;
        
        // Add content view
        if (mContentView != null) {
            addView(mContentView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        
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

        // Add placeholder for edit mode
        mEditPlaceholder = new android.widget.TextView(context);
        mEditPlaceholder.setTextColor(ThemeUtils.resolveColor(context,
                R.attr.colorOnSurfaceVariant, R.color.md_theme_onSurfaceVariant));
        mEditPlaceholder.setGravity(Gravity.CENTER);
        mEditPlaceholder.setTextSize(14);
        mEditPlaceholder.setVisibility(GONE);
        addView(mEditPlaceholder, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                if (mHasMovedPastSlop) return;
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
        mEditPlaceholder.setVisibility(enabled && mEditPlaceholder.getText().length() > 0 ? VISIBLE : GONE);
    }

    public void setPlaceholderText(String text) {
        mEditPlaceholder.setText(text);
        if (mEditMode && text != null && text.length() > 0) {
            mEditPlaceholder.setVisibility(VISIBLE);
        } else {
            mEditPlaceholder.setVisibility(GONE);
        }
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
                        mStartX = x;
                        mStartY = y;
                        mHasMovedPastSlop = false;
                        mGestureDetector.setIsLongpressEnabled(true);
                        
                        // Check if touch is on resize handle
                        int[] location = new int[2];
                        mResizeHandle.getLocationOnScreen(location);
                        if (x >= location[0] && x <= location[0] + mResizeHandle.getWidth() &&
                            y >= location[1] && y <= location[1] + mResizeHandle.getHeight()) {
                            mIsResizing = true;
                            mGestureDetector.setIsLongpressEnabled(false);
                        } else {
                            mIsDragging = true;
                        }
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - mLastTouchX;
                        float dy = y - mLastTouchY;
                        
                        if (Math.abs(x - mStartX) > mTouchSlop || Math.abs(y - mStartY) > mTouchSlop) {
                            mHasMovedPastSlop = true;
                            mGestureDetector.setIsLongpressEnabled(false);
                        }
                        
                        if (mIsResizing) {
                            LayoutParams params = (LayoutParams) getLayoutParams();
                            params.width = (int) Math.max(100, getWidth() + dx);
                            params.height = (int) Math.max(100, getHeight() + dy);
                            setLayoutParams(params);
                        } else if (mIsDragging) {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
                            params.leftMargin += (int) dx;
                            params.topMargin += (int) dy;
                            setLayoutParams(params);
                            // Reset translations just in case
                            setTranslationX(0);
                            setTranslationY(0);
                        }
                        
                        mLastTouchX = x;
                        mLastTouchY = y;
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mIsDragging = false;
                        mIsResizing = false;
                        mHasMovedPastSlop = false;
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
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params != null) {
            mData.x = params.leftMargin;
            mData.y = params.topMargin;
            mData.w = params.width;
            mData.h = params.height;
        } else {
            mData.x = getX();
            mData.y = getY();
            if (mData.w <= 0) mData.w = 200;
            if (mData.h <= 0) mData.h = 200;
        }
        return mData;
    }

    public void setWidgetData(WidgetData data) {
        this.mData = data;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params == null) params = new FrameLayout.LayoutParams(data.w, data.h);
        params.width = data.w;
        params.height = data.h;
        params.leftMargin = (int) data.x;
        params.topMargin = (int) data.y;
        setLayoutParams(params);
        setTranslationX(0);
        setTranslationY(0);
        applyOpacity();
    }
    public void applyOpacity() {
        float alpha = mData.opacity / 255.0f;

        if ("status".equals(mData.type) || "message".equals(mData.type)) {
            // For text widgets, set background opacity but keep text opaque
            if (mContentView != null) {
                int surfaceColor = ThemeUtils.resolveColor(getContext(),
                        R.attr.colorSurface, R.color.md_theme_surface) & 0x00FFFFFF;
                int backgroundColor = (mData.opacity << 24) | surfaceColor;
                mContentView.setBackgroundColor(backgroundColor);
            }
        } else {
            // For button-style widgets, set view alpha
            if (mContentView != null) {
                mContentView.setAlpha(alpha);
            }
        }
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
