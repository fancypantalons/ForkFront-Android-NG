package com.tbd.forkfront;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

public class CommandPaletteFragment extends DialogFragment {

    public interface OnCommandListener {
        void onCommandExecute(CmdRegistry.CmdInfo cmd);
    }

    private OnCommandListener mListener;
    private CommandAdapter mAdapter;

    public static CommandPaletteFragment newInstance() {
        return new CommandPaletteFragment();
    }

    public void setOnCommandListener(OnCommandListener listener) {
        mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.command_palette, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView list = view.findViewById(R.id.command_list);
        mAdapter = new CommandAdapter(CmdRegistry.getPaletteSorted(), cmd -> {
            if (mListener != null) {
                mListener.onCommandExecute(cmd);
            }
            dismiss();
        });
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

        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSearchKeyboard(searchView);
        });

        View btnClose = view.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            
            // Constrain in landscape
            if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                width = (int)(500 * getResources().getDisplayMetrics().density);
                height = (int)(400 * getResources().getDisplayMetrics().density);
            }
            
            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void showSearchKeyboard(SearchView searchView) {
        View edit = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (edit != null) {
            edit.requestFocus();
            Util.showKeyboard(requireContext(), edit);
        }
    }
}
