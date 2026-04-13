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

public class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.ViewHolder> {

    public interface OnCommandSelectedListener {
        void onCommandSelected(CmdRegistry.CmdInfo cmd);
    }

    private List<CmdRegistry.CmdInfo> mAllCommands;
    private List<CmdRegistry.CmdInfo> mFilteredCommands;
    private OnCommandSelectedListener mListener;

    public CommandAdapter(List<CmdRegistry.CmdInfo> commands, OnCommandSelectedListener listener) {
        mAllCommands = new ArrayList<>(commands);
        mFilteredCommands = new ArrayList<>(commands);
        mListener = listener;
    }

    public void filter(String query) {
        mFilteredCommands.clear();
        if (query == null || query.isEmpty()) {
            mFilteredCommands.addAll(mAllCommands);
        } else {
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (CmdRegistry.CmdInfo cmd : mAllCommands) {
                if (cmd.getDisplayName().toLowerCase(Locale.getDefault()).contains(lowerQuery) ||
                    cmd.getCommand().toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                    mFilteredCommands.add(cmd);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.command_palette_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CmdRegistry.CmdInfo cmd = mFilteredCommands.get(position);
        holder.name.setText(cmd.getDisplayName() + " (" + cmd.getCommand() + ")");
        holder.desc.setText(cmd.getDescription());
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onCommandSelected(cmd);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mFilteredCommands.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView desc;

        ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.cmd_name);
            desc = view.findViewById(R.id.cmd_desc);
        }
    }
}
