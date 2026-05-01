package com.tbd.forkfront.commands;
import com.tbd.forkfront.R;
import com.tbd.forkfront.context.CmdRegistry;

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

    private static class ListItem {
        final boolean isHeader;
        final CmdRegistry.Category category;
        final CmdRegistry.CmdInfo command;

        ListItem(CmdRegistry.Category category) {
            this.isHeader = true;
            this.category = category;
            this.command = null;
        }

        ListItem(CmdRegistry.CmdInfo command) {
            this.isHeader = false;
            this.category = null;
            this.command = command;
        }
    }

    static class HeaderViewHolder {
        TextView title;

        HeaderViewHolder(View view) {
            title = view.findViewById(R.id.header_title);
        }
    }

    static class CommandViewHolder {
        TextView name;
        TextView desc;

        CommandViewHolder(View view) {
            name = view.findViewById(R.id.cmd_name);
            desc = view.findViewById(R.id.cmd_desc);
        }
    }

    private final Context mContext;
    private final List<ListItem> mItems;
    private int mSelectedPosition = -1;

    public CommandPickerAdapter(Context context,
                                  List<CmdRegistry.CmdInfo> commands) {
        mContext = context;
        mItems = new ArrayList<>();

        CmdRegistry.Category lastCategory = null;
        for (CmdRegistry.CmdInfo cmd : commands) {
            if (!CmdRegistry.isPaletteVisible(cmd))
                continue;
            if (cmd.getCategory() != lastCategory) {
                mItems.add(new ListItem(cmd.getCategory()));
                lastCategory = cmd.getCategory();
            }
            mItems.add(new ListItem(cmd));
        }
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        ListItem item = mItems.get(position);
        return item.isHeader ? item.category : item.command;
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
        return mItems.get(position).isHeader ? TYPE_HEADER : TYPE_COMMAND;
    }

    @Override
    public boolean isEnabled(int position) {
        return !mItems.get(position).isHeader;
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
                convertView.setTag(new HeaderViewHolder(convertView));
            }
            HeaderViewHolder holder = (HeaderViewHolder) convertView.getTag();
            holder.title.setText(mItems.get(position).category.getDisplayName());
        } else {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.command_palette_item, parent,
                        false);
                convertView.setTag(new CommandViewHolder(convertView));
            }
            CommandViewHolder holder = (CommandViewHolder) convertView.getTag();
            CmdRegistry.CmdInfo cmd = mItems.get(position).command;
            holder.name.setText(cmd.getDisplayLabel());
            holder.desc.setText(cmd.getDescription());
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
