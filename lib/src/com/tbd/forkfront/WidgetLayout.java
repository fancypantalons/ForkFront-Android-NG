package com.tbd.forkfront;
import com.tbd.forkfront.context.CmdRegistry;
import com.tbd.forkfront.widgets.CommandPaletteWidget;
import com.tbd.forkfront.widgets.MinimapWidget;
import com.tbd.forkfront.widgets.MessageWidget;
import com.tbd.forkfront.widgets.StatusWidget;
import com.tbd.forkfront.widgets.ControlWidget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.tbd.forkfront.widgets.WidgetLayoutController;

import java.util.ArrayList;
import java.util.List;

/**
 * A layout container that manages draggable and resizable ControlWidgets.
 */
public class WidgetLayout extends FrameLayout {

    private static final String COMMITTED_PREFIX = "layouts/";
    private static final String DRAFT_PREFIX = "draft_layouts/";

    private boolean mEditMode = false;
    private final List<ControlWidget> mWidgets = new ArrayList<>();
    private WidgetLayoutController mController;
    private EngineCommands mCommands;
    private MapInputCoordinator mMapInput;
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

    public void setDependencies(WidgetLayoutController controller, EngineCommands commands, MapInputCoordinator mapInput) {
        mController = controller;
        mCommands = commands;
        mMapInput = mapInput;
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
        if (mViewArea != null && mMapInput != null) {
            int l = mViewArea.getLeft();
            int t = mViewArea.getTop();
            int r = mViewArea.getRight();
            int b = mViewArea.getBottom();
            
            if (mViewRect.left != l || mViewRect.top != t || mViewRect.right != r || mViewRect.bottom != b) {
                mViewRect.set(l, t, r, b);
                mMapInput.viewAreaChanged(mViewRect);
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
                    if (mEditMode) {
                        saveDraftLayout();
                    }
                }
            });

            widget.setOnWidgetLongClickListener(new ControlWidget.OnWidgetLongClickListener() {
                @Override
                public void onWidgetLongClick(ControlWidget w) {
                    if (mController != null) {
                        mController.showWidgetProperties(w);
                    }
                }
            });
        }
    }

    public void removeWidget(ControlWidget widget) {
        widget.setOnWidgetChangeListener(null);
        widget.setOnWidgetLongClickListener(null);
        mWidgets.remove(widget);
        removeView(widget);
        if (mEditMode) {
            saveDraftLayout();
        }
    }

    private String getLayoutKey() {
        return LayoutConfiguration.getLayoutKey(getContext(), mScreenId);
    }

    private String buildPrefix(String basePrefix, String layoutKey) {
        return basePrefix + mScreenId + "/" + layoutKey + "/";
    }

    private android.content.SharedPreferences getPrefs() {
        return getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
    }

    public void saveLayout() {
        saveLayoutToPrefs(COMMITTED_PREFIX);
    }

    public void saveDraftLayout() {
        saveLayoutToPrefs(DRAFT_PREFIX);
    }

    private void saveLayoutToPrefs(String basePrefix) {
        String layoutKey = getLayoutKey();
        android.content.SharedPreferences prefs = getPrefs();
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        String prefix = buildPrefix(basePrefix, layoutKey);
        
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
        String layoutKey = getLayoutKey();
        android.content.SharedPreferences prefs = getPrefs();

        // In edit mode, prefer draft if it exists
        if (mEditMode) {
            String draftKey = buildPrefix(DRAFT_PREFIX, layoutKey) + "widget_count";
            if (prefs.contains(draftKey)) {
                loadUserLayout(layoutKey, prefs, DRAFT_PREFIX);
                return;
            }
        }

        String userLayoutKey = buildPrefix(COMMITTED_PREFIX, layoutKey) + "widget_count";

        if (prefs.contains(userLayoutKey)) {
            loadUserLayout(layoutKey, prefs, COMMITTED_PREFIX);
        } else {
            loadStockLayout(layoutKey);
        }
    }

    public void loadCommittedLayout() {
        String layoutKey = getLayoutKey();
        android.content.SharedPreferences prefs = getPrefs();

        String userLayoutKey = buildPrefix(COMMITTED_PREFIX, layoutKey) + "widget_count";

        if (prefs.contains(userLayoutKey)) {
            loadUserLayout(layoutKey, prefs, COMMITTED_PREFIX);
        } else {
            loadStockLayout(layoutKey);
        }
    }

    public void enterEditMode() {
        String layoutKey = getLayoutKey();
        android.content.SharedPreferences prefs = getPrefs();

        String draftCountKey = buildPrefix(DRAFT_PREFIX, layoutKey) + "widget_count";
        if (prefs.contains(draftCountKey)) {
            // Draft already exists from a previous edit session; preserve it
            return;
        }

        // No draft yet: snapshot committed layout into draft
        String committedPrefix = buildPrefix(COMMITTED_PREFIX, layoutKey);
        String draftPrefix = buildPrefix(DRAFT_PREFIX, layoutKey);
        int count = prefs.getInt(committedPrefix + "widget_count", 0);

        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(draftPrefix + "widget_count", count);
        for (int i = 0; i < count; i++) {
            copyPref(editor, prefs, committedPrefix, draftPrefix, i);
        }
        editor.apply();
    }

    public void commitDraftToLayout() {
        String layoutKey = getLayoutKey();
        android.content.SharedPreferences prefs = getPrefs();

        String draftPrefix = buildPrefix(DRAFT_PREFIX, layoutKey);
        String committedPrefix = buildPrefix(COMMITTED_PREFIX, layoutKey);
        int count = prefs.getInt(draftPrefix + "widget_count", 0);

        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(committedPrefix + "widget_count", count);
        for (int i = 0; i < count; i++) {
            copyPref(editor, prefs, draftPrefix, committedPrefix, i);
        }
        // Clear draft
        clearDraftInternal(editor, prefs, draftPrefix, count);
        editor.apply();
    }

    public void clearDraft() {
        String layoutKey = getLayoutKey();
        android.content.SharedPreferences prefs = getPrefs();

        String draftPrefix = buildPrefix(DRAFT_PREFIX, layoutKey);
        int count = prefs.getInt(draftPrefix + "widget_count", 0);

        android.content.SharedPreferences.Editor editor = prefs.edit();
        clearDraftInternal(editor, prefs, draftPrefix, count);
        editor.apply();
    }

    private void copyPref(android.content.SharedPreferences.Editor editor,
                          android.content.SharedPreferences prefs,
                          String fromPrefix, String toPrefix, int index) {
        String suffix = "widget_" + index + "_";
        editor.putString(toPrefix + suffix + "type",
                prefs.getString(fromPrefix + suffix + "type", ""));
        editor.putString(toPrefix + suffix + "label",
                prefs.getString(fromPrefix + suffix + "label", ""));
        editor.putString(toPrefix + suffix + "command",
                prefs.getString(fromPrefix + suffix + "command", ""));
        editor.putBoolean(toPrefix + suffix + "horizontal",
                prefs.getBoolean(fromPrefix + suffix + "horizontal", true));
        editor.putFloat(toPrefix + suffix + "x",
                prefs.getFloat(fromPrefix + suffix + "x", 0));
        editor.putFloat(toPrefix + suffix + "y",
                prefs.getFloat(fromPrefix + suffix + "y", 0));
        editor.putInt(toPrefix + suffix + "w",
                prefs.getInt(fromPrefix + suffix + "w", 200));
        editor.putInt(toPrefix + suffix + "h",
                prefs.getInt(fromPrefix + suffix + "h", 200));
        editor.putInt(toPrefix + suffix + "opacity",
                prefs.getInt(fromPrefix + suffix + "opacity", 191));
        editor.putInt(toPrefix + suffix + "font_size",
                prefs.getInt(fromPrefix + suffix + "font_size", 15));
        editor.putInt(toPrefix + suffix + "rows",
                prefs.getInt(fromPrefix + suffix + "rows", 3));
        editor.putInt(toPrefix + suffix + "columns",
                prefs.getInt(fromPrefix + suffix + "columns", 3));
        editor.putString(toPrefix + suffix + "category",
                prefs.getString(fromPrefix + suffix + "category", null));
        editor.putBoolean(toPrefix + suffix + "contextual_only",
                prefs.getBoolean(fromPrefix + suffix + "contextual_only", false));
        java.util.Set<String> pinned = prefs.getStringSet(
                fromPrefix + suffix + "pinned_commands", null);
        editor.putStringSet(toPrefix + suffix + "pinned_commands", pinned);
    }

    private void clearDraftInternal(android.content.SharedPreferences.Editor editor,
                                    android.content.SharedPreferences prefs,
                                    String draftPrefix, int count) {
        for (int i = 0; i < count; i++) {
            String suffix = "widget_" + i + "_";
            editor.remove(draftPrefix + suffix + "type");
            editor.remove(draftPrefix + suffix + "label");
            editor.remove(draftPrefix + suffix + "command");
            editor.remove(draftPrefix + suffix + "horizontal");
            editor.remove(draftPrefix + suffix + "x");
            editor.remove(draftPrefix + suffix + "y");
            editor.remove(draftPrefix + suffix + "w");
            editor.remove(draftPrefix + suffix + "h");
            editor.remove(draftPrefix + suffix + "opacity");
            editor.remove(draftPrefix + suffix + "font_size");
            editor.remove(draftPrefix + suffix + "rows");
            editor.remove(draftPrefix + suffix + "columns");
            editor.remove(draftPrefix + suffix + "category");
            editor.remove(draftPrefix + suffix + "contextual_only");
            editor.remove(draftPrefix + suffix + "pinned_commands");
        }
        editor.remove(draftPrefix + "widget_count");
    }

    public ControlWidget createWidget(ControlWidget.WidgetData data) {
        if ("dpad".equals(data.type)) {
            DirectionalPadView dpadView = new DirectionalPadView(getContext());
            if (mCommands != null) {
                dpadView.setOnDirectionListener(cmd -> mCommands.sendDirKeyCmd(cmd));
            }
            return new ControlWidget(getContext(), dpadView, "dpad");
        } else if ("button".equals(data.type)) {
            MaterialButton btn = ThemeUtils.createButtonText(getContext());
            btn.setText(data.label);
            if (mCommands != null && data.command != null && data.command.length() > 0) {
                final TouchRepeatHelper repeatHelper = new TouchRepeatHelper();
                final Runnable fireCommand = new Runnable() {
                    @Override
                    public void run() {
                        if (isEditMode()) return;
                        if (data.command.startsWith("#")) {
                            mCommands.sendStringCmd(data.command + "\n");
                        } else {
                            mCommands.sendKeyCmd(data.command.charAt(0));
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
                if (mController != null && !isEditMode() && getContext() instanceof AppCompatActivity) {
                    mController.showCommandPaletteForLayout((AppCompatActivity) getContext(), this);
                }
            });
            ControlWidget w = new ControlWidget(getContext(), btn, "palette");
            w.getWidgetData().label = data.label;
            return w;
        } else if ("status".equals(data.type)) {
            if (mController != null && mController.getStatusWindow() != null) {
                ControlWidget w = new StatusWidget(getContext(), mController.getStatusWindow());
                w.setPlaceholderText("Status Window");
                return w;
            }
        } else if ("message".equals(data.type)) {
            if (mController != null && mController.getMessageWindow() != null) {
                ControlWidget w = new MessageWidget(getContext(), mController.getMessageWindow());
                w.setPlaceholderText("Message Window");
                return w;
            }
        } else if ("minimap".equals(data.type)) {
            if (mController != null && mController.getMapWindow() != null && mController.getTileset() != null) {
                ControlWidget w = new MinimapWidget(getContext(), mController.getMapWindow(), mController.getTileset());
                w.setPlaceholderText("Minimap");
                return w;
            }
        } else if ("command_palette".equals(data.type)) {
            if (mController != null) {
                CmdRegistry.Category category = null;
                if (data.category != null && !data.category.isEmpty()) {
                    try {
                        category = CmdRegistry.Category.valueOf(data.category);
                    } catch (IllegalArgumentException e) {
                        // Invalid category, use null (all categories)
                    }
                }
                ControlWidget w = new CommandPaletteWidget(getContext(), mController.getContextActions(),
                        mCommands, data.rows, data.columns, category, data.horizontal,
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
    private void loadUserLayout(String layoutKey, android.content.SharedPreferences prefs,
                                String basePrefix) {
        // Clear existing widgets
        for (ControlWidget w : mWidgets) {
            removeView(w);
        }
        mWidgets.clear();
        
        String prefix = buildPrefix(basePrefix, layoutKey);
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
        
        String deviceKey = mController != null ? mController.getDeviceKey() : null;

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
        // Save current layout if in edit mode (preserve unsaved changes to draft)
        if (mEditMode) {
            saveDraftLayout();
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
        String layoutKey = getLayoutKey();
        android.content.SharedPreferences prefs = getPrefs();
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        String prefix = buildPrefix(COMMITTED_PREFIX, layoutKey);
        
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

        // Also clear any draft for this layout
        String draftPrefix = buildPrefix(DRAFT_PREFIX, layoutKey);
        int draftCount = prefs.getInt(draftPrefix + "widget_count", 0);
        clearDraftInternal(editor, prefs, draftPrefix, draftCount);

        editor.apply();
        
        // Reload stock layout
        loadCommittedLayout();
    }
}
