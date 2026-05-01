package com.tbd.forkfront;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PinCommandAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_COMMAND = 1;

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
    private Set<String> mCheckedCommands;

    public PinCommandAdapter(List<CmdRegistry.CmdInfo> commands, Set<String> checkedCommands) {
        mAllCommands = new ArrayList<>(commands);
        mCheckedCommands = checkedCommands;
        buildDisplayList("");
    }

    public Set<String> getCheckedCommands() {
        return mCheckedCommands;
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
                    .inflate(R.layout.pin_command_item, parent, false);
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
            cmdHolder.checkbox.setOnCheckedChangeListener(null);
            cmdHolder.checkbox.setChecked(mCheckedCommands.contains(item.command.getCommand()));
            cmdHolder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    mCheckedCommands.add(item.command.getCommand());
                } else {
                    mCheckedCommands.remove(item.command.getCommand());
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
        CheckBox checkbox;
        TextView name;

        CommandViewHolder(View view) {
            super(view);
            checkbox = view.findViewById(R.id.cmd_checkbox);
            name = view.findViewById(R.id.cmd_name);
        }
    }
}
