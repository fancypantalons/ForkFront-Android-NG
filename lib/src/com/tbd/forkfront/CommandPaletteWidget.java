package com.tbd.forkfront;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A widget that displays a scrollable grid of command buttons.
 * Can be configured with rows, columns, category filter, and orientation.
 */
public class CommandPaletteWidget extends ControlWidget {

    private GridLayout mGridLayout;
    private View mScrollContainer;
    private NH_State mNHState;
    private int mRows;
    private int mColumns;
    private CmdRegistry.Category mCategory;
    private boolean mHorizontal;

    public CommandPaletteWidget(Context context, NH_State nhState, int rows, int columns,
                                CmdRegistry.Category category, boolean horizontal) {
        super(context, createScrollContainer(context, horizontal), "command_palette");
        mNHState = nhState;
        mRows = rows;
        mColumns = columns;
        mCategory = category;
        mHorizontal = horizontal;

        // Extract the grid layout from the scroll container
        if (horizontal) {
            HorizontalScrollView scrollView = (HorizontalScrollView) getContentView();
            mGridLayout = (GridLayout) scrollView.getChildAt(0);
        } else {
            ScrollView scrollView = (ScrollView) getContentView();
            mGridLayout = (GridLayout) scrollView.getChildAt(0);
        }

        populateCommands();
    }

    private static View createScrollContainer(Context context, boolean horizontal) {
        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setUseDefaultMargins(false); // No default margins for tighter layout
        gridLayout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        if (horizontal) {
            // Horizontal scrolling - grid grows in columns, fills down rows
            // Use VERTICAL orientation to fill top-to-bottom
            gridLayout.setOrientation(GridLayout.VERTICAL);
            HorizontalScrollView scrollView = new HorizontalScrollView(context);
            scrollView.setFillViewport(false);
            scrollView.addView(gridLayout, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return scrollView;
        } else {
            // Vertical scrolling - grid grows in rows, fills across columns
            // Use HORIZONTAL orientation to fill left-to-right
            gridLayout.setOrientation(GridLayout.HORIZONTAL);
            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(false);
            scrollView.addView(gridLayout, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return scrollView;
        }
    }

    private void populateCommands() {
        mGridLayout.removeAllViews();

        // Get commands, optionally filtered by category
        List<CmdRegistry.CmdInfo> commands;
        if (mCategory != null) {
            commands = new ArrayList<>(CmdRegistry.getByCategory(mCategory));
        } else {
            commands = new ArrayList<>(CmdRegistry.getAllSorted());
        }

        // Sort alphabetically by display name
        Collections.sort(commands, (a, b) ->
            a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));

        if (mHorizontal) {
            // Horizontal scrolling: fixed rows, unlimited columns
            // Fills down each column alphabetically
            // Use VERTICAL orientation to fill top-to-bottom
            mGridLayout.setOrientation(GridLayout.VERTICAL);
            mGridLayout.setRowCount(mRows);
            mGridLayout.setColumnCount(GridLayout.UNDEFINED);

            for (int i = 0; i < commands.size(); i++) {
                // Fill down: row cycles through 0 to mRows-1, then next column
                int row = i % mRows;
                int col = i / mRows;

                MaterialButton btn = createCommandButton(commands.get(i));
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                // Equal height distribution across rows
                params.rowSpec = GridLayout.spec(row, 1f);
                params.columnSpec = GridLayout.spec(col);
                params.width = GridLayout.LayoutParams.WRAP_CONTENT;
                params.height = 0;
                params.setGravity(android.view.Gravity.FILL_VERTICAL | android.view.Gravity.CENTER_HORIZONTAL);
                mGridLayout.addView(btn, params);
            }
        } else {
            // Vertical scrolling: fixed columns, unlimited rows
            // Fills across each row alphabetically
            // Use HORIZONTAL orientation to fill left-to-right
            mGridLayout.setOrientation(GridLayout.HORIZONTAL);
            mGridLayout.setColumnCount(mColumns);
            mGridLayout.setRowCount(GridLayout.UNDEFINED);

            for (int i = 0; i < commands.size(); i++) {
                // Fill across: column cycles through 0 to mColumns-1, then next row
                int col = i % mColumns;
                int row = i / mColumns;

                MaterialButton btn = createCommandButton(commands.get(i));
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.rowSpec = GridLayout.spec(row);
                // Equal width distribution across columns
                params.columnSpec = GridLayout.spec(col, 1f);
                params.width = 0;
                params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                params.setGravity(android.view.Gravity.FILL_HORIZONTAL | android.view.Gravity.CENTER_VERTICAL);
                mGridLayout.addView(btn, params);
            }
        }
    }

    private MaterialButton createCommandButton(CmdRegistry.CmdInfo cmd) {
        MaterialButton btn = new MaterialButton(getContext());
        btn.setText(cmd.getDisplayName());
        btn.setAllCaps(false);

        // Compact styling
        float density = getContext().getResources().getDisplayMetrics().density;
        int padding = (int)(2 * density); // Minimal padding
        btn.setPadding(padding, padding, padding, padding);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12); // Smaller text
        btn.setInsetTop(0);
        btn.setInsetBottom(0);

        btn.setOnClickListener(v -> {
            if (mNHState != null && !mNHState.isEditMode()) {
                if (cmd.getCommand().startsWith("#")) {
                    mNHState.sendStringCmd(cmd.getCommand() + "\n");
                } else {
                    mNHState.sendKeyCmd(cmd.getCommand().charAt(0));
                }
            }
        });

        return btn;
    }

    public void setConfiguration(int rows, int columns, CmdRegistry.Category category, boolean horizontal) {
        boolean orientationChanged = (mHorizontal != horizontal);

        mRows = rows;
        mColumns = columns;
        mCategory = category;
        mHorizontal = horizontal;

        if (orientationChanged) {
            // Orientation changed - need to recreate scroll container
            recreateScrollContainer();
        } else {
            // Just repopulate with new settings
            populateCommands();
        }

        // Force layout update
        mGridLayout.requestLayout();
        if (getContentView() != null) {
            getContentView().requestLayout();
        }
        requestLayout();
    }

    private void recreateScrollContainer() {
        // Clear the old GridLayout first
        if (mGridLayout != null) {
            mGridLayout.removeAllViews();
        }

        // Remove the old scroll container (always at index 0 per ControlWidget constructor)
        if (getChildCount() > 0) {
            View firstChild = getChildAt(0);
            if (firstChild instanceof ScrollView || firstChild instanceof HorizontalScrollView) {
                removeViewAt(0);
            }
        }

        // Create new scroll container with current orientation
        View newScrollContainer = createScrollContainer(getContext(), mHorizontal);

        // Extract the new GridLayout
        if (mHorizontal) {
            HorizontalScrollView scrollView = (HorizontalScrollView) newScrollContainer;
            mGridLayout = (GridLayout) scrollView.getChildAt(0);
        } else {
            ScrollView scrollView = (ScrollView) newScrollContainer;
            mGridLayout = (GridLayout) scrollView.getChildAt(0);
        }

        // Add new scroll container as the first child (before border, handle, etc.)
        addView(newScrollContainer, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Populate with commands
        populateCommands();
    }

    public int getRows() {
        return mRows;
    }

    public int getColumns() {
        return mColumns;
    }

    public CmdRegistry.Category getCategory() {
        return mCategory;
    }

    public boolean isHorizontal() {
        return mHorizontal;
    }
}
