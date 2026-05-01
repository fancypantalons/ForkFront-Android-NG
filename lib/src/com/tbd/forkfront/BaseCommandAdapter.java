package com.tbd.forkfront;
import com.tbd.forkfront.context.CmdRegistry;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class BaseCommandAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    protected static final int TYPE_HEADER = 0;
    protected static final int TYPE_COMMAND = 1;

    protected static class ListItem {
        final boolean isHeader;
        final String headerText;
        final CmdRegistry.CmdInfo command;

        ListItem(String headerText) {
            this.isHeader = true;
            this.headerText = headerText;
            this.command = null;
        }

        ListItem(CmdRegistry.CmdInfo command) {
            this.isHeader = false;
            this.headerText = null;
            this.command = command;
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView title;

        HeaderViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.header_title);
        }
    }

    protected List<CmdRegistry.CmdInfo> mAllCommands;
    protected List<ListItem> mDisplayItems;

    protected void buildDisplayList(String query) {
        mDisplayItems = new ArrayList<>();

        List<CmdRegistry.CmdInfo> filtered = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            filtered.addAll(mAllCommands);
        } else {
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (CmdRegistry.CmdInfo cmd : mAllCommands) {
                if (cmd.getDisplayName().toLowerCase(Locale.getDefault()).contains(lowerQuery) ||
                    cmd.getCommand().toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                    filtered.add(cmd);
                }
            }
        }

        CmdRegistry.Category lastCategory = null;
        for (CmdRegistry.CmdInfo cmd : filtered) {
            if (cmd.getCategory() != lastCategory) {
                mDisplayItems.add(new ListItem(cmd.getCategory().getDisplayName()));
                lastCategory = cmd.getCategory();
            }
            mDisplayItems.add(new ListItem(cmd));
        }
    }

    public void filter(String query) {
        buildDisplayList(query);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mDisplayItems.get(position).isHeader ? TYPE_HEADER : TYPE_COMMAND;
    }

    @Override
    public int getItemCount() {
        return mDisplayItems.size();
    }
}
