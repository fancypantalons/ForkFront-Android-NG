package com.tbd.forkfront;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the gamepad command picker. Groups commands by category
 * with non-selectable section headers.
 */
public class CommandPickerAdapter extends BaseAdapter {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_COMMAND = 1;

    private final Context mContext;
    private final List<Object> mItems;
    private final List<Boolean> mEnabled;
    private int mSelectedPosition = -1;

    public CommandPickerAdapter(Context context,
                                  List<CmdRegistry.CmdInfo> commands) {
        mContext = context;
        mItems = new ArrayList<>();
        mEnabled = new ArrayList<>();

        CmdRegistry.Category lastCategory = null;
        for (CmdRegistry.CmdInfo cmd : commands) {
            if (!CmdRegistry.isPaletteVisible(cmd))
                continue;
            if (cmd.getCategory() != lastCategory) {
                mItems.add(cmd.getCategory());
                mEnabled.add(false);
                lastCategory = cmd.getCategory();
            }
            mItems.add(cmd);
            mEnabled.add(true);
        }
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position) instanceof CmdRegistry.Category
            ? TYPE_HEADER : TYPE_COMMAND;
    }

    @Override
    public boolean isEnabled(int position) {
        return mEnabled.get(position);
    }

    public void setSelectedPosition(int position) {
        mSelectedPosition = position;
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    /**
     * Apply highlight directly to visible children of the given ListView.
     * More deterministic than invalidateViews() when touch mode may defer rebinding.
     */
    public void applyHighlightToListView(ListView listView) {
        if (listView == null) return;
        int first = listView.getFirstVisiblePosition();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int pos = first + i;
            if (pos == mSelectedPosition) {
                child.setBackgroundResource(R.drawable.command_picker_highlight);
            } else {
                child.setBackgroundResource(0);
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int type = getItemViewType(position);
        if (type == TYPE_HEADER) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.command_palette_header, parent,
                        false);
            }
            CmdRegistry.Category cat =
                (CmdRegistry.Category) mItems.get(position);
            ((TextView) convertView.findViewById(R.id.header_title))
                .setText(cat.getDisplayName());
        } else {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.command_palette_item, parent,
                        false);
            }
            CmdRegistry.CmdInfo cmd =
                (CmdRegistry.CmdInfo) mItems.get(position);
            ((TextView) convertView.findViewById(R.id.cmd_name))
                .setText(cmd.getDisplayName() + " ("
                    + cmd.getCommand() + ")");
            ((TextView) convertView.findViewById(R.id.cmd_desc))
                .setText(cmd.getDescription());
        }
        if (position == mSelectedPosition) {
            convertView.setBackgroundResource(
                R.drawable.command_picker_highlight);
        } else {
            convertView.setBackgroundResource(0);
        }
        return convertView;
    }
}
