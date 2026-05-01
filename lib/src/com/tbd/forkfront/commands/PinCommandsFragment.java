package com.tbd.forkfront.commands;
import com.tbd.forkfront.R;
import com.tbd.forkfront.dialog.DialogUtils;
import com.tbd.forkfront.context.CmdRegistry;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
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
        if (pinnedCommands == null) {
            pinnedCommands = Collections.emptySet();
        }
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
        if (savedInstanceState != null) {
            ArrayList<String> checked = savedInstanceState.getStringArrayList("checked");
            if (checked != null) {
                mPinnedCommands.addAll(checked);
            }
        } else if (getArguments() != null) {
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

        View btnClose = view.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        View btnDone = view.findViewById(R.id.btn_done);
        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onPinsChanged(mAdapter.getCheckedCommands());
                }
                dismiss();
            });
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter != null) {
            outState.putStringArrayList("checked",
                new ArrayList<>(mAdapter.getCheckedCommands()));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        DialogUtils.setDialogSize(this, 500, 400);
    }
}
