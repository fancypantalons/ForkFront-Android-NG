package com.tbd.forkfront;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommandAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_COMMAND = 1;

    public interface OnCommandSelectedListener {
        void onCommandSelected(CmdRegistry.CmdInfo cmd);
    }

    // Wrapper class for list items (either header or command)
    private static class ListItem {
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

    private List<CmdRegistry.CmdInfo> mAllCommands;
    private List<ListItem> mDisplayItems;
    private OnCommandSelectedListener mListener;

    public CommandAdapter(List<CmdRegistry.CmdInfo> commands, OnCommandSelectedListener listener) {
        mAllCommands = new ArrayList<>(commands);
        mListener = listener;
        buildDisplayList("");
    }

    private void buildDisplayList(String query) {
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

        // Group by category and add headers
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

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.command_palette_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.command_palette_item, parent, false);
            return new CommandViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = mDisplayItems.get(position);
        if (item.isHeader) {
            ((HeaderViewHolder) holder).title.setText(item.headerText);
        } else {
            CommandViewHolder cmdHolder = (CommandViewHolder) holder;
            cmdHolder.name.setText(item.command.getDisplayName() + " (" + item.command.getCommand() + ")");
            cmdHolder.desc.setText(item.command.getDescription());
            cmdHolder.itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onCommandSelected(item.command);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mDisplayItems.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView title;

        HeaderViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.header_title);
        }
    }

    static class CommandViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView desc;

        CommandViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.cmd_name);
            desc = view.findViewById(R.id.cmd_desc);
        }
    }
}
