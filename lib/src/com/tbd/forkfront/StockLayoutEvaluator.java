package com.tbd.forkfront;

import android.content.Context;
import android.util.DisplayMetrics;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates stock widget layouts from JSON into absolute screen positions.
 * Converts anchor-based positioning (LEFT/RIGHT/CENTER + margins in DP)
 * to pixel coordinates based on actual screen metrics.
 */
public class StockLayoutEvaluator {
    
    private final Context mContext;
    private final DisplayMetrics mMetrics;
    private final float mDensity;
    
    public StockLayoutEvaluator(Context context) {
        mContext = context;
        mMetrics = context.getResources().getDisplayMetrics();
        mDensity = mMetrics.density;
    }
    
    /**
     * Load and evaluate a stock layout JSON file.
     * 
     * @param layoutKey Layout configuration key (e.g., "portrait")
     * @return List of WidgetData with absolute pixel positions, or null on error
     */
    public List<ControlWidget.WidgetData> evaluateStockLayout(String layoutKey) {
        try {
            // Load JSON from assets
            InputStream is = mContext.getAssets().open("default_layouts/" + layoutKey + ".json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            
            JSONObject root = new JSONObject(json);
            JSONArray widgets = root.getJSONArray("widgets");
            
            List<ControlWidget.WidgetData> result = new ArrayList<>();
            
            for (int i = 0; i < widgets.length(); i++) {
                JSONObject widgetDef = widgets.getJSONObject(i);
                ControlWidget.WidgetData data = evaluateWidget(widgetDef);
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
    
    /**
     * Evaluate a single widget definition to absolute positions.
     */
    private ControlWidget.WidgetData evaluateWidget(JSONObject def) throws Exception {
        ControlWidget.WidgetData data = new ControlWidget.WidgetData();
        
        // Basic properties
        data.type = def.getString("type");
        data.label = def.optString("label", "");
        data.command = def.optString("command", "");
        
        // Evaluate size first (needed for position calculation)
        JSONObject sizeObj = def.getJSONObject("size");
        data.w = evaluateSize(sizeObj.getString("width"), true);
        data.h = evaluateSize(sizeObj.getString("height"), false);
        
        // Evaluate position from anchor
        JSONObject anchorObj = def.getJSONObject("anchor");
        String hAnchor = anchorObj.getString("horizontal");
        String vAnchor = anchorObj.getString("vertical");
        
        int marginLeft = dpToPx(anchorObj.optString("marginLeft", "0dp"));
        int marginTop = dpToPx(anchorObj.optString("marginTop", "0dp"));
        int marginRight = dpToPx(anchorObj.optString("marginRight", "0dp"));
        int marginBottom = dpToPx(anchorObj.optString("marginBottom", "0dp"));
        
        // Calculate x position
        switch (hAnchor) {
            case "LEFT":
                data.x = marginLeft;
                break;
            case "RIGHT":
                data.x = mMetrics.widthPixels - data.w - marginRight;
                break;
            case "CENTER":
                data.x = (mMetrics.widthPixels - data.w) / 2 + marginLeft - marginRight;
                break;
            default:
                android.util.Log.w("StockLayoutEvaluator", "Unknown horizontal anchor: " + hAnchor);
                data.x = 0;
        }
        
        // Calculate y position (account for status bar on TOP anchors)
        int statusBarHeight = getStatusBarHeight();
        
        switch (vAnchor) {
            case "TOP":
                data.y = marginTop + statusBarHeight;
                break;
            case "BOTTOM":
                data.y = mMetrics.heightPixels - data.h - marginBottom;
                break;
            case "CENTER":
                data.y = (mMetrics.heightPixels - data.h) / 2 + marginTop - marginBottom;
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
        }
        
        return data;
    }
    
    /**
     * Evaluate a size specification to pixels.
     * 
     * @param spec Size specification: "MATCH_PARENT", "XXdp", or "XX%" (percentage)
     * @param isWidth true for width, false for height
     * @return Size in pixels
     */
    private int evaluateSize(String spec, boolean isWidth) {
        if ("MATCH_PARENT".equals(spec)) {
            return isWidth ? mMetrics.widthPixels : mMetrics.heightPixels;
        } else if (spec.endsWith("dp")) {
            return dpToPx(spec);
        } else if (spec.endsWith("%")) {
            // Percentage of screen dimension
            int percent = Integer.parseInt(spec.substring(0, spec.length() - 1));
            int screenSize = isWidth ? mMetrics.widthPixels : mMetrics.heightPixels;
            return (screenSize * percent) / 100;
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
    
    /**
     * Get Android status bar height to avoid overlap.
     */
    private int getStatusBarHeight() {
        int resourceId = mContext.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return mContext.getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
}
