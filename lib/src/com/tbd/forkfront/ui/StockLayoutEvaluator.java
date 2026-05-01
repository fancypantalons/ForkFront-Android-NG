package com.tbd.forkfront.ui;
import com.tbd.forkfront.widgets.ControlWidget;
import com.tbd.forkfront.R;

import android.content.Context;
import android.graphics.Rect;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates stock widget layouts from JSON into absolute screen positions.
 * Converts anchor-based positioning (LEFT/RIGHT/CENTER + margins in DP)
 * to pixel coordinates based on actual window metrics.
 */
public class StockLayoutEvaluator {

    private final Context mContext;
    private final Rect mWindowBounds;
    private final int mWindowBottom;
    private final float mDensity;
    private final int mPeekHeight;

    public StockLayoutEvaluator(Context context) {
        mContext = context;
        mWindowBounds = WindowMetricsHelper.getLayoutBounds(context);
        mWindowBottom = WindowMetricsHelper.getWindowBounds(context).bottom;
        mDensity = context.getResources().getDisplayMetrics().density;
        mPeekHeight = context.getResources().getDimensionPixelSize(R.dimen.command_palette_peek_height);
    }
    
    /**
     * Load and evaluate a stock layout JSON file.
     * 
     * @param screenId Screen identifier ("primary", "secondary")
     * @param deviceKey Device key (e.g. "thor") or null
     * @param layoutKey Layout configuration key (e.g., "portrait")
     * @return List of WidgetData with absolute pixel positions, or null on error
     */
    public List<ControlWidget.WidgetData> evaluateStockLayout(String screenId, String deviceKey, String layoutKey) {
        try {
            // Load JSON from assets with priority resolution
            InputStream is = openAsset(screenId, deviceKey, layoutKey);
            if (is == null) {
                return null;
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            is.close();
            String json = baos.toString("UTF-8");
            
            JSONObject root = new JSONObject(json);
            JSONArray widgets = root.getJSONArray("widgets");
            
            List<ControlWidget.WidgetData> result = new ArrayList<>();

            Rect evalBounds = new Rect(mWindowBounds);
            if ("primary".equals(screenId)) {
                evalBounds.bottom = mWindowBottom - mPeekHeight;
            }

            for (int i = 0; i < widgets.length(); i++) {
                JSONObject widgetDef = widgets.getJSONObject(i);
                ControlWidget.WidgetData data = evaluateWidget(widgetDef, evalBounds);
                if (data != null) {
                    result.add(data);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            android.util.Log.e("StockLayoutEvaluator", "Failed to load stock layout: " + layoutKey, e);
            return null;
        }
    }

    private InputStream openAsset(String screenId, String deviceKey, String layoutKey) {
        // 1. {screen}/{device}_{orientation}.json
        if (deviceKey != null) {
            try {
                return mContext.getAssets().open("default_layouts/" + screenId + "/" + deviceKey + "_" + layoutKey + ".json");
            } catch (Exception e) { /* ignore */ }
        }

        // 2. {screen}/{orientation}.json
        try {
            return mContext.getAssets().open("default_layouts/" + screenId + "/" + layoutKey + ".json");
        } catch (Exception e) { /* ignore */ }

        // 3. {orientation}.json (legacy fallback, primary only)
        if ("primary".equals(screenId)) {
            try {
                return mContext.getAssets().open("default_layouts/" + layoutKey + ".json");
            } catch (Exception e) { /* ignore */ }
        }

        return null;
    }
    
    /**
     * Evaluate a single widget definition to absolute positions.
     */
    private ControlWidget.WidgetData evaluateWidget(JSONObject def, Rect bounds) throws Exception {
        ControlWidget.WidgetData data = new ControlWidget.WidgetData();

        // Basic properties
        data.type = def.getString("type");
        data.label = def.optString("label", "");
        data.command = def.optString("command", "");

        // Get size and anchor specs
        JSONObject sizeObj = def.getJSONObject("size");
        JSONObject anchorObj = def.getJSONObject("anchor");
        String hAnchor = anchorObj.getString("horizontal");
        String vAnchor = anchorObj.getString("vertical");

        int marginLeft = dpToPx(anchorObj.optString("marginLeft", "0dp"));
        int marginTop = dpToPx(anchorObj.optString("marginTop", "0dp"));
        int marginRight = dpToPx(anchorObj.optString("marginRight", "0dp"));
        int marginBottom = dpToPx(anchorObj.optString("marginBottom", "0dp"));

        // Evaluate size with margin awareness
        String widthSpec = sizeObj.getString("width");
        String heightSpec = sizeObj.getString("height");
        boolean isWidthMatchParent = "MATCH_PARENT".equals(widthSpec);
        boolean isHeightMatchParent = "MATCH_PARENT".equals(heightSpec);

        data.w = evaluateSize(widthSpec, true, marginLeft, marginRight, bounds);
        data.h = evaluateSize(heightSpec, false, marginTop, marginBottom, bounds);

        // Calculate x position
        // When MATCH_PARENT is used, margins are insets (defining available space),
        // so positioning is always at the start margin regardless of anchor.
        // For fixed sizes, margins are positioning offsets applied per anchor.
        switch (hAnchor) {
            case "LEFT":
                data.x = bounds.left + marginLeft;
                break;
            case "RIGHT":
                data.x = bounds.right - data.w - marginRight;
                break;
            case "CENTER":
                if (isWidthMatchParent) {
                    // With MATCH_PARENT, widget fills available space between margins
                    data.x = bounds.left + marginLeft;
                } else {
                    // With fixed size, margins are offsets from center
                    data.x = bounds.centerX() - data.w / 2 + marginLeft - marginRight;
                }
                break;
            default:
                android.util.Log.w("StockLayoutEvaluator", "Unknown horizontal anchor: " + hAnchor);
                data.x = 0;
        }

        // Calculate y position
        switch (vAnchor) {
            case "TOP":
                data.y = bounds.top + marginTop;
                break;
            case "BOTTOM":
                data.y = bounds.bottom - data.h - marginBottom;
                break;
            case "CENTER":
                if (isHeightMatchParent) {
                    // With MATCH_PARENT, widget fills available space between margins
                    data.y = bounds.top + marginTop;
                } else {
                    // With fixed size, margins are offsets from center
                    data.y = bounds.centerY() - data.h / 2 + marginTop - marginBottom;
                }
                break;
            default:
                android.util.Log.w("StockLayoutEvaluator", "Unknown vertical anchor: " + vAnchor);
                data.y = 0;
        }
        
        // Copy properties if present
        if (def.has("properties")) {
            JSONObject props = def.getJSONObject("properties");
            data.opacity = props.optInt("opacity", 191);
            data.fontSize = props.optInt("fontSize", 15);
            data.horizontal = props.optBoolean("horizontal", true);
            data.rows = props.optInt("rows", 3);
            data.columns = props.optInt("columns", 3);
            data.category = props.optString("category", null);
            if (data.category != null && data.category.isEmpty()) {
                data.category = null;
            }
            data.contextualOnly = props.optBoolean("contextualOnly", false);
            if (props.has("pinnedCommands")) {
                org.json.JSONArray arr = props.getJSONArray("pinnedCommands");
                data.pinnedCommands = new java.util.HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    data.pinnedCommands.add(arr.getString(i));
                }
            }
        }
        
        return data;
    }
    
    /**
     * Evaluate a size specification to pixels.
     *
     * @param spec Size specification: "MATCH_PARENT", "XXdp", or "XX%" (percentage)
     * @param isWidth true for width, false for height
     * @param marginStart Left margin (for width) or top margin (for height) in pixels
     * @param marginEnd Right margin (for width) or bottom margin (for height) in pixels
     * @return Size in pixels
     */
    private int evaluateSize(String spec, boolean isWidth, int marginStart, int marginEnd, Rect bounds) {
        if ("MATCH_PARENT".equals(spec)) {
            // MATCH_PARENT with margins: margins define insets, reducing available space
            int fullSize = isWidth ? bounds.width() : bounds.height();
            return fullSize - marginStart - marginEnd;
        } else if (spec.endsWith("dp")) {
            return dpToPx(spec);
        } else if (spec.endsWith("%")) {
            // Percentage of window dimension
            int percent = Integer.parseInt(spec.substring(0, spec.length() - 1));
            int windowSize = isWidth ? bounds.width() : bounds.height();
            return (windowSize * percent) / 100;
        }

        android.util.Log.w("StockLayoutEvaluator", "Unknown size spec: " + spec);
        return dpToPx("200dp"); // fallback
    }
    
    /**
     * Convert DP string to pixels.
     */
    private int dpToPx(String dpStr) {
        String numStr = dpStr.replace("dp", "").trim();
        int dp = Integer.parseInt(numStr);
        return Math.round(dp * mDensity);
    }
}
