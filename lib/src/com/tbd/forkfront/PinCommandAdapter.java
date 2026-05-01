package com.tbd.forkfront;
import com.tbd.forkfront.context.CmdRegistry;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PinCommandAdapter extends BaseCommandAdapter {

    private Set<String> mCheckedCommands;

    public PinCommandAdapter(List<CmdRegistry.CmdInfo> commands, Set<String> checkedCommands) {
        mAllCommands = new ArrayList<>(commands);
        mCheckedCommands = checkedCommands;
        buildDisplayList("");
    }

    public Set<String> getCheckedCommands() {
        return new HashSet<>(mCheckedCommands);
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
            cmdHolder.name.setText(item.command.getDisplayLabel());
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
