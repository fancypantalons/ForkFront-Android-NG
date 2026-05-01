package com.tbd.forkfront;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class CommandAdapter extends BaseCommandAdapter {

    public interface OnCommandSelectedListener {
        void onCommandSelected(CmdRegistry.CmdInfo cmd);
    }

    private OnCommandSelectedListener mListener;

    public CommandAdapter(List<CmdRegistry.CmdInfo> commands, OnCommandSelectedListener listener) {
        mAllCommands = new ArrayList<>(commands);
        mListener = listener;
        buildDisplayList("");
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
            cmdHolder.name.setText(item.command.getDisplayLabel());
            cmdHolder.desc.setText(item.command.getDescription());
            cmdHolder.itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onCommandSelected(item.command);
                }
            });
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
