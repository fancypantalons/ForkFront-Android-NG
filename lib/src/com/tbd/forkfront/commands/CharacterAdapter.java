package com.tbd.forkfront.commands;
import com.tbd.forkfront.R;
import com.tbd.forkfront.engine.SaveMetadata;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class CharacterAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_CHARACTER = 0;
    private static final int TYPE_NEW_CHARACTER = 1;

    public interface OnCharacterSelectedListener {
        void onCharacterSelected(String plname);
        void onNewCharacterRequested();
    }

    private final List<String> characters;
    private final String lastUsername;
    private final File saveDir;
    private final OnCharacterSelectedListener listener;

    public CharacterAdapter(List<String> characters, String lastUsername, File saveDir, OnCharacterSelectedListener listener) {
        this.characters = characters;
        this.lastUsername = lastUsername;
        this.saveDir = saveDir;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return position < characters.size() ? TYPE_CHARACTER : TYPE_NEW_CHARACTER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_CHARACTER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_character, parent, false);
            return new CharacterViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_new_character, parent, false);
            return new NewCharacterViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CharacterViewHolder) {
            String name = characters.get(position);
            ((CharacterViewHolder) holder).bind(name, name.equals(lastUsername));
        }
    }

    @Override
    public int getItemCount() {
        return characters.size() + 1;
    }

    class CharacterViewHolder extends RecyclerView.ViewHolder {
        TextView identityText;
        TextView raceChip;
        TextView alignmentChip;
        TextView wizardChip;
        View chipContainer;
        View root;

        CharacterViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.character_row_root);
            identityText = itemView.findViewById(R.id.character_identity);
            raceChip = itemView.findViewById(R.id.chip_race);
            alignmentChip = itemView.findViewById(R.id.chip_alignment);
            wizardChip = itemView.findViewById(R.id.chip_wizard);
            chipContainer = itemView.findViewById(R.id.chip_container);
            root.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < characters.size()) {
                    listener.onCharacterSelected(characters.get(pos));
                }
            });
        }

        void bind(String name, boolean isLast) {
            SaveMetadata meta = SaveMetadata.load(saveDir, name);
            if (meta.hasMetadata()) {
                identityText.setText(String.format("%s the %s", name, meta.getRole()));
                raceChip.setText(meta.getRace());
                alignmentChip.setText(meta.getAlignment());
                wizardChip.setVisibility(meta.isWizard() ? View.VISIBLE : View.GONE);
                raceChip.setVisibility(View.VISIBLE);
                alignmentChip.setVisibility(View.VISIBLE);
                chipContainer.setVisibility(View.VISIBLE);
            } else {
                identityText.setText(name);
                chipContainer.setVisibility(View.GONE);
            }
        }
    }

    class NewCharacterViewHolder extends RecyclerView.ViewHolder {
        NewCharacterViewHolder(View itemView) {
            super(itemView);
            itemView.findViewById(R.id.new_character_row_root).setOnClickListener(v -> listener.onNewCharacterRequested());
        }
    }
}
