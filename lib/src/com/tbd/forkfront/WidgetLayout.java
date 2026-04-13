package com.tbd.forkfront;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * A layout container that manages draggable and resizable ControlWidgets.
 */
public class WidgetLayout extends FrameLayout {

    private boolean mEditMode = false;
    private final List<ControlWidget> mWidgets = new ArrayList<>();
    private NH_State mNHState;
    private View mViewArea;
    private final Rect mViewRect = new Rect();

    public WidgetLayout(Context context) {
        super(context);
    }

    public WidgetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WidgetLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mViewArea = findViewById(R.id.viewArea);
    }

    public void setNHState(NH_State state) {
        mNHState = state;
    }

    public void setEditMode(boolean enabled) {
        mEditMode = enabled;
        for (ControlWidget widget : mWidgets) {
            widget.setEditMode(enabled);
        }
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        // Notify map about view area changes
        if (mViewArea != null && mNHState != null) {
            int l = mViewArea.getLeft();
            int t = mViewArea.getTop();
            int r = mViewArea.getRight();
            int b = mViewArea.getBottom();
            
            if (mViewRect.left != l || mViewRect.top != t || mViewRect.right != r || mViewRect.bottom != b) {
                mViewRect.set(l, t, r, b);
                mNHState.viewAreaChanged(mViewRect);
            }
        }
    }

    public void addWidget(ControlWidget widget) {
        if (!mWidgets.contains(widget)) {
            mWidgets.add(widget);
            addView(widget);
            widget.setEditMode(mEditMode);
            
            widget.setOnWidgetChangeListener(new ControlWidget.OnWidgetChangeListener() {
                @Override
                public void onWidgetChanged(ControlWidget w) {
                    saveLayout();
                }
            });

            widget.setOnWidgetLongClickListener(new ControlWidget.OnWidgetLongClickListener() {
                @Override
                public void onWidgetLongClick(ControlWidget w) {
                    if (mNHState != null && getContext() instanceof AppCompatActivity) {
                        mNHState.showWidgetProperties((AppCompatActivity) getContext(), w);
                    }
                }
            });
        }
    }

    public void removeWidget(ControlWidget widget) {
        mWidgets.remove(widget);
        removeView(widget);
        saveLayout();
    }

    public void saveLayout() {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        editor.putInt("widget_count", mWidgets.size());
        for (int i = 0; i < mWidgets.size(); i++) {
            ControlWidget widget = mWidgets.get(i);
            ControlWidget.WidgetData data = widget.getWidgetData();
            editor.putString("widget_" + i + "_type", data.type);
            editor.putString("widget_" + i + "_label", data.label);
            editor.putString("widget_" + i + "_command", data.command);
            editor.putBoolean("widget_" + i + "_horizontal", data.horizontal);
            editor.putFloat("widget_" + i + "_x", data.x);
            editor.putFloat("widget_" + i + "_y", data.y);
            editor.putInt("widget_" + i + "_w", data.w);
            editor.putInt("widget_" + i + "_h", data.h);
        }
        editor.apply();
    }

    public void loadLayout() {
        // Clear existing dynamic widgets but keep XML children
        for (ControlWidget w : mWidgets) {
            removeView(w);
        }
        mWidgets.clear();

        android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
        int count = prefs.getInt("widget_count", 0);
        
        for (int i = 0; i < count; i++) {
            ControlWidget.WidgetData data = new ControlWidget.WidgetData();
            data.type = prefs.getString("widget_" + i + "_type", "");
            data.label = prefs.getString("widget_" + i + "_label", "");
            data.command = prefs.getString("widget_" + i + "_command", "");
            data.horizontal = prefs.getBoolean("widget_" + i + "_horizontal", true);
            data.x = prefs.getFloat("widget_" + i + "_x", 0);
            data.y = prefs.getFloat("widget_" + i + "_y", 0);
            data.w = prefs.getInt("widget_" + i + "_w", 200);
            data.h = prefs.getInt("widget_" + i + "_h", 200);
            
            ControlWidget widget = createWidget(data);
            if (widget != null) {
                addWidget(widget);
                widget.setWidgetData(data);
            }
        }
    }

    private ControlWidget createWidget(ControlWidget.WidgetData data) {
        if ("dpad".equals(data.type)) {
            DirectionalPadView dpadView = new DirectionalPadView(getContext());
            if (mNHState != null) {
                dpadView.setOnDirectionListener(cmd -> mNHState.sendDirKeyCmd(cmd));
            }
            return new ControlWidget(getContext(), dpadView, "dpad");
        } else if ("button".equals(data.type)) {
            MaterialButton btn = new MaterialButton(getContext());
            btn.setText(data.label);
            if (mNHState != null && data.command != null && data.command.length() > 0) {
                btn.setOnClickListener(v -> {
                    if (mNHState.isEditMode()) return;
                    if (data.command.startsWith("#")) {
                        mNHState.sendStringCmd(data.command + "\n");
                    } else {
                        mNHState.sendKeyCmd(data.command.charAt(0));
                    }
                });
            }
            ControlWidget w = new ControlWidget(getContext(), btn, "button");
            w.getWidgetData().label = data.label;
            w.getWidgetData().command = data.command;
            return w;
        } else if ("palette".equals(data.type)) {
            MaterialButton btn = new MaterialButton(getContext());
            btn.setText(data.label);
            btn.setIconResource(android.R.drawable.ic_menu_search);
            btn.setOnClickListener(v -> {
                if (mNHState != null && !mNHState.isEditMode() && getContext() instanceof AppCompatActivity) {
                    mNHState.showCommandPalette((AppCompatActivity) getContext());
                }
            });
            ControlWidget w = new ControlWidget(getContext(), btn, "palette");
            w.getWidgetData().label = data.label;
            return w;
        } else if ("contextual".equals(data.type)) {
            ContextualActionBarView contextualView = new ContextualActionBarView(getContext());
            contextualView.setOrientation(data.horizontal);
            if (mNHState != null) {
                contextualView.setOnActionSelectedListener(cmd -> {
                    if (!mNHState.isEditMode()) {
                        if (cmd.getCommand().startsWith("#")) {
                            mNHState.sendStringCmd(cmd.getCommand() + "\n");
                        } else {
                            mNHState.sendKeyCmd(cmd.getCommand().charAt(0));
                        }
                    }
                });
                // Initial update (will be GONE if no actions)
                contextualView.updateActions(null);
            }
            ControlWidget w = new ControlWidget(getContext(), contextualView, "contextual");
            w.getWidgetData().horizontal = data.horizontal;
            return w;
        }
        return null;
    }
}
