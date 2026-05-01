package com.tbd.forkfront;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
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
    private String mScreenId = "primary";

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

    public void setScreenId(String id) {
        mScreenId = id;
    }

    public String getScreenId() {
        return mScreenId;
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
                    // Manual save only
                }
            });

            widget.setOnWidgetLongClickListener(new ControlWidget.OnWidgetLongClickListener() {
                @Override
                public void onWidgetLongClick(ControlWidget w) {
                    if (mNHState != null) {
                        mNHState.showWidgetProperties(w);
                    }
                }
            });
        }
    }

    public void removeWidget(ControlWidget widget) {
        mWidgets.remove(widget);
        removeView(widget);
    }

    public void saveLayout() {
        String layoutKey = LayoutConfiguration.getLayoutKey(getContext(), mScreenId);
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        String prefix = "layouts/" + mScreenId + "/" + layoutKey + "/";
        
        editor.putInt(prefix + "widget_count", mWidgets.size());
        for (int i = 0; i < mWidgets.size(); i++) {
            ControlWidget widget = mWidgets.get(i);
            ControlWidget.WidgetData data = widget.getWidgetData();
            editor.putString(prefix + "widget_" + i + "_type", data.type);
            editor.putString(prefix + "widget_" + i + "_label", data.label);
            editor.putString(prefix + "widget_" + i + "_command", data.command);
            editor.putBoolean(prefix + "widget_" + i + "_horizontal", data.horizontal);
            editor.putFloat(prefix + "widget_" + i + "_x", data.x);
            editor.putFloat(prefix + "widget_" + i + "_y", data.y);
            editor.putInt(prefix + "widget_" + i + "_w", data.w);
            editor.putInt(prefix + "widget_" + i + "_h", data.h);
            editor.putInt(prefix + "widget_" + i + "_opacity", data.opacity);
            editor.putInt(prefix + "widget_" + i + "_font_size", data.fontSize);
            editor.putInt(prefix + "widget_" + i + "_rows", data.rows);
            editor.putInt(prefix + "widget_" + i + "_columns", data.columns);
            editor.putString(prefix + "widget_" + i + "_category", data.category);
            editor.putBoolean(prefix + "widget_" + i + "_contextual_only", data.contextualOnly);
            editor.putStringSet(prefix + "widget_" + i + "_pinned_commands", data.pinnedCommands);
        }
        editor.apply();
    }

    public void loadLayout() {
        String layoutKey = LayoutConfiguration.getLayoutKey(getContext(), mScreenId);
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);

        String userLayoutKey = "layouts/" + mScreenId + "/" + layoutKey + "/widget_count";

        if (prefs.contains(userLayoutKey)) {
            loadUserLayout(layoutKey, prefs);
        } else {
            loadStockLayout(layoutKey);
        }
    }
    ControlWidget createWidget(ControlWidget.WidgetData data) {
        if ("dpad".equals(data.type)) {
            DirectionalPadView dpadView = new DirectionalPadView(getContext());
            if (mNHState != null) {
                dpadView.setOnDirectionListener(cmd -> mNHState.sendDirKeyCmd(cmd));
            }
            return new ControlWidget(getContext(), dpadView, "dpad");
        } else if ("button".equals(data.type)) {
            MaterialButton btn = ThemeUtils.createButtonText(getContext());
            btn.setText(data.label);
            if (mNHState != null && data.command != null && data.command.length() > 0) {
                final TouchRepeatHelper repeatHelper = new TouchRepeatHelper();
                final Runnable fireCommand = new Runnable() {
                    @Override
                    public void run() {
                        if (mNHState.isEditMode()) return;
                        if (data.command.startsWith("#")) {
                            mNHState.sendStringCmd(data.command + "\n");
                        } else {
                            mNHState.sendKeyCmd(data.command.charAt(0));
                        }
                    }
                };
                btn.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                v.setPressed(true);
                                fireCommand.run();
                                repeatHelper.startRepeat(fireCommand);
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                v.setPressed(false);
                                repeatHelper.cancelRepeat();
                                v.performClick();
                                return true;
                        }
                        return false;
                    }
                });
                btn.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        // no-op
                    }
                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        repeatHelper.destroy();
                    }
                });
            }
            ControlWidget w = new ControlWidget(getContext(), btn, "button");
            w.getWidgetData().label = data.label;
            w.getWidgetData().command = data.command;
            return w;
        } else if ("palette".equals(data.type)) {
            MaterialButton btn = ThemeUtils.createButtonText(getContext());
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
        } else if ("status".equals(data.type)) {
            if (mNHState != null && mNHState.getStatusWindow() != null) {
                ControlWidget w = new StatusWidget(getContext(), mNHState.getStatusWindow());
                w.setPlaceholderText("Status Window");
                return w;
            }
        } else if ("message".equals(data.type)) {
            if (mNHState != null && mNHState.getMessageWindow() != null) {
                ControlWidget w = new MessageWidget(getContext(), mNHState.getMessageWindow());
                w.setPlaceholderText("Message Window");
                return w;
            }
        } else if ("minimap".equals(data.type)) {
            if (mNHState != null && mNHState.getMapWindow() != null && mNHState.getTileset() != null) {
                ControlWidget w = new MinimapWidget(getContext(), mNHState.getMapWindow(), mNHState.getTileset());
                w.setPlaceholderText("Minimap");
                return w;
            }
        } else if ("command_palette".equals(data.type)) {
            if (mNHState != null) {
                CmdRegistry.Category category = null;
                if (data.category != null && !data.category.isEmpty()) {
                    try {
                        category = CmdRegistry.Category.valueOf(data.category);
                    } catch (IllegalArgumentException e) {
                        // Invalid category, use null (all categories)
                    }
                }
                ControlWidget w = new CommandPaletteWidget(getContext(), mNHState,
                        data.rows, data.columns, category, data.horizontal,
                        data.contextualOnly, data.pinnedCommands);
                w.setPlaceholderText("Command Palette");
                return w;
            }
        }
        return null;
    }

    /**
     * Load user's customized layout from SharedPreferences.
     */
    private void loadUserLayout(String layoutKey, android.content.SharedPreferences prefs) {
        // Clear existing widgets
        for (ControlWidget w : mWidgets) {
            removeView(w);
        }
        mWidgets.clear();
        
        String prefix = "layouts/" + mScreenId + "/" + layoutKey + "/";
        int count = prefs.getInt(prefix + "widget_count", 0);
        
        for (int i = 0; i < count; i++) {
            ControlWidget.WidgetData data = new ControlWidget.WidgetData();
            data.type = prefs.getString(prefix + "widget_" + i + "_type", "");
            data.label = prefs.getString(prefix + "widget_" + i + "_label", "");
            data.command = prefs.getString(prefix + "widget_" + i + "_command", "");
            data.horizontal = prefs.getBoolean(prefix + "widget_" + i + "_horizontal", true);
            data.x = prefs.getFloat(prefix + "widget_" + i + "_x", 0);
            data.y = prefs.getFloat(prefix + "widget_" + i + "_y", 0);
            data.w = prefs.getInt(prefix + "widget_" + i + "_w", 200);
            data.h = prefs.getInt(prefix + "widget_" + i + "_h", 200);
            data.opacity = prefs.getInt(prefix + "widget_" + i + "_opacity", 191);
            data.fontSize = prefs.getInt(prefix + "widget_" + i + "_font_size", 15);
            data.rows = prefs.getInt(prefix + "widget_" + i + "_rows", 3);
            data.columns = prefs.getInt(prefix + "widget_" + i + "_columns", 3);
            data.category = prefs.getString(prefix + "widget_" + i + "_category", null);
            data.contextualOnly = prefs.getBoolean(prefix + "widget_" + i + "_contextual_only", false);
            java.util.Set<String> pinned = prefs.getStringSet(prefix + "widget_" + i + "_pinned_commands", null);
            if (pinned != null) {
                data.pinnedCommands = new java.util.HashSet<>(pinned);
            }
            
            ControlWidget widget = createWidget(data);
            if (widget != null) {
                widget.setWidgetData(data);
                widget.setFontSize(data.fontSize);
                addWidget(widget);
            }
        }
        requestLayout();
    }

    /**
     * Load stock layout from assets and evaluate to absolute positions.
     */
    private void loadStockLayout(String layoutKey) {
        // Clear existing widgets
        for (ControlWidget w : mWidgets) {
            removeView(w);
        }
        mWidgets.clear();
        
        String deviceKey = mNHState != null ? mNHState.getDeviceKey() : null;

        // Check if stock layout exists
        if (!LayoutConfiguration.hasStockLayout(getContext(), mScreenId, deviceKey, layoutKey)) {
            return;
        }
        
        // Evaluate stock layout to absolute positions
        StockLayoutEvaluator evaluator = new StockLayoutEvaluator(getContext());
        List<ControlWidget.WidgetData> stockWidgets = evaluator.evaluateStockLayout(mScreenId, deviceKey, layoutKey);
        
        if (stockWidgets == null) {
            return;
        }
        
        // Create widgets from evaluated positions
        for (ControlWidget.WidgetData data : stockWidgets) {
            ControlWidget widget = createWidget(data);
            if (widget != null) {
                widget.setWidgetData(data);
                widget.setFontSize(data.fontSize);
                addWidget(widget);
            }
        }
        requestLayout();
    }

    /**
     * Reloads the appropriate layout for the new orientation.
     * Called by NH_State when configuration changes, not by Android framework.
     */
    public void reloadForNewOrientation(android.content.res.Configuration newConfig) {
        // Save current layout if in edit mode (preserve unsaved changes)
        if (mEditMode) {
            saveLayout();
        }
        
        // Reload layout for new orientation
        loadLayout();
        
        // Restore edit mode if it was active
        if (mEditMode) {
            setEditMode(true);
        }
    }

    /**
     * Reset the current orientation's layout to stock default.
     * Deletes user customizations for the current orientation only.
     */
    public void resetToDefault() {
        String layoutKey = LayoutConfiguration.getLayoutKey(getContext(), mScreenId);
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        String prefix = "layouts/" + mScreenId + "/" + layoutKey + "/";
        
        // Remove all user customizations for this layout
        int count = prefs.getInt(prefix + "widget_count", 0);
        for (int i = 0; i < count; i++) {
            editor.remove(prefix + "widget_" + i + "_type");
            editor.remove(prefix + "widget_" + i + "_label");
            editor.remove(prefix + "widget_" + i + "_command");
            editor.remove(prefix + "widget_" + i + "_horizontal");
            editor.remove(prefix + "widget_" + i + "_x");
            editor.remove(prefix + "widget_" + i + "_y");
            editor.remove(prefix + "widget_" + i + "_w");
            editor.remove(prefix + "widget_" + i + "_h");
            editor.remove(prefix + "widget_" + i + "_opacity");
            editor.remove(prefix + "widget_" + i + "_font_size");
            editor.remove(prefix + "widget_" + i + "_rows");
            editor.remove(prefix + "widget_" + i + "_columns");
            editor.remove(prefix + "widget_" + i + "_category");
            editor.remove(prefix + "widget_" + i + "_contextual_only");
            editor.remove(prefix + "widget_" + i + "_pinned_commands");
        }
        editor.remove(prefix + "widget_count");
        editor.apply();
        
        // Reload stock layout
        loadLayout();
    }
}
