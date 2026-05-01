package com.tbd.forkfront;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;

public class PinCommandsFragment extends DialogFragment {

    public interface OnPinsChangedListener {
        void onPinsChanged(Set<String> pinnedCommands);
    }

    private OnPinsChangedListener mListener;
    private Set<String> mPinnedCommands;
    private PinCommandAdapter mAdapter;

    public static PinCommandsFragment newInstance(Set<String> pinnedCommands) {
        PinCommandsFragment f = new PinCommandsFragment();
        Bundle args = new Bundle();
        ArrayList<String> list = new ArrayList<>(pinnedCommands);
        args.putStringArrayList("pinned", list);
        f.setArguments(args);
        return f;
    }

    public void setOnPinsChangedListener(OnPinsChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPinnedCommands = new HashSet<>();
        if (getArguments() != null) {
            ArrayList<String> list = getArguments().getStringArrayList("pinned");
            if (list != null) {
                mPinnedCommands.addAll(list);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pin_commands_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView list = view.findViewById(R.id.command_list);
        mAdapter = new PinCommandAdapter(CmdRegistry.getPaletteSorted(), mPinnedCommands);
        list.setAdapter(mAdapter);

        SearchView searchView = view.findViewById(R.id.command_search);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mAdapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAdapter.filter(newText);
                return true;
            }
        });

        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btn_done).setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onPinsChanged(mAdapter.getCheckedCommands());
            }
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;

            if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                width = (int)(500 * getResources().getDisplayMetrics().density);
                height = (int)(400 * getResources().getDisplayMetrics().density);
            }

            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
