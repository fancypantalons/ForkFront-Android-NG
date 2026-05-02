package com.tbd.forkfront.commands;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.tbd.forkfront.R;
import com.tbd.forkfront.context.CmdRegistry;
import com.tbd.forkfront.dialog.DialogUtils;

public class CommandPickerDialogFragment extends DialogFragment {

  public interface OnCommandSelectedListener {
    void onCommandSelected(CmdRegistry.CmdInfo cmd);
  }

  private OnCommandSelectedListener mListener;
  private CommandAdapter mAdapter;

  public static CommandPickerDialogFragment newInstance() {
    return new CommandPickerDialogFragment();
  }

  public void setOnCommandSelectedListener(OnCommandSelectedListener listener) {
    mListener = listener;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.command_palette, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    View handle = view.findViewById(R.id.command_palette_handle);
    if (handle != null) {
      handle.setVisibility(View.GONE);
    }

    RecyclerView list = view.findViewById(R.id.command_list);
    mAdapter =
        new CommandAdapter(
            CmdRegistry.getPaletteSorted(),
            cmd -> {
              if (mListener != null) {
                mListener.onCommandSelected(cmd);
              }
              dismiss();
            });
    list.setAdapter(mAdapter);

    SearchView searchView = view.findViewById(R.id.command_search);
    searchView.setOnQueryTextListener(
        new SearchView.OnQueryTextListener() {
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
  }

  @Override
  public void onStart() {
    super.onStart();
    DialogUtils.setDialogSize(this, 500, 400);
  }
}
