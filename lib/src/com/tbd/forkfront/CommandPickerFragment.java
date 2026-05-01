package com.tbd.forkfront;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

/**
 * Full-screen command picker for gamepad navigation.
 * No search, no soft keyboard — just D-pad + A/B selection.
 */
public class CommandPickerFragment extends Fragment {

    public interface OnCommandPickedListener {
        void onCommandPicked(CmdRegistry.CmdInfo cmd);
        void onDismissed();
    }

    private ListView mListView;
    private CommandPickerAdapter mAdapter;
    private OnCommandPickedListener mListener;

    public void setOnCommandPickedListener(OnCommandPickedListener l) {
        mListener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.command_picker_fragment,
            container, false);

        mListView = root.findViewById(R.id.command_picker_list);
        List<CmdRegistry.CmdInfo> commands =
            CmdRegistry.getPaletteSorted();
        mAdapter = new CommandPickerAdapter(requireContext(), commands);
        mListView.setAdapter(mAdapter);
        mListView.setItemsCanFocus(false);

        // Intercept D-pad and gamepad buttons before the ListView's
        // native handler processes them. This gives us full control
        // over navigation while the ListView remains focusable so
        // the selector drawable renders correctly.
        mListView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN)
                    return false;
                switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_BUTTON_B:
                    return handleGamepadKey(event);
                }
                return false;
            }
        });

        mListView.setOnItemClickListener(
            new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                     int position, long id) {
                Object item = mAdapter.getItem(position);
                if (item instanceof CmdRegistry.CmdInfo) {
                    if (mListener != null) {
                        mListener.onCommandPicked(
                            (CmdRegistry.CmdInfo) item);
                    }
                }
            }
        });

        View btnClose = root.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view,
                               @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mListView.post(new Runnable() {
            @Override
            public void run() {
                if (mListView == null) return;
                int initialPos = -1;
                for (int i = 0; i < mAdapter.getCount(); i++) {
                    if (mAdapter.isEnabled(i)) {
                        initialPos = i;
                        break;
                    }
                }
                if (initialPos >= 0) {
                    mAdapter.setSelectedPosition(initialPos);
                    mListView.setSelection(initialPos);
                    mAdapter.applyHighlightToListView(mListView);
                }
                mListView.requestFocus();
            }
        });
    }

    private boolean handleGamepadKey(KeyEvent ev) {
        if (mListView == null) return false;

        int pos = mAdapter.getSelectedPosition();
        boolean wasInvalid = (pos < 0 || pos >= mAdapter.getCount());
        if (wasInvalid) {
            pos = (ev.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP)
                ? mAdapter.getCount() - 1 : 0;
        }

        switch (ev.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_UP:
            if (!wasInvalid) pos--;
            while (pos >= 0 && pos < mAdapter.getCount()
                   && !mAdapter.isEnabled(pos)) pos--;
            if (pos >= 0 && pos < mAdapter.getCount()) {
                mAdapter.setSelectedPosition(pos);
                mListView.setSelection(pos);
                mAdapter.applyHighlightToListView(mListView);
            }
            return true;

        case KeyEvent.KEYCODE_DPAD_DOWN:
            if (!wasInvalid) pos++;
            while (pos >= 0 && pos < mAdapter.getCount()
                   && !mAdapter.isEnabled(pos)) pos++;
            if (pos >= 0 && pos < mAdapter.getCount()) {
                mAdapter.setSelectedPosition(pos);
                mListView.setSelection(pos);
                mAdapter.applyHighlightToListView(mListView);
            }
            return true;

        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_BUTTON_A:
            int selectedPos = mAdapter.getSelectedPosition();
            if (selectedPos >= 0 && selectedPos < mAdapter.getCount()) {
                Object item = mAdapter.getItem(selectedPos);
                if (item instanceof CmdRegistry.CmdInfo) {
                    if (mListener != null) {
                        mListener.onCommandPicked(
                            (CmdRegistry.CmdInfo) item);
                    }
                }
            }
            return true;

        case KeyEvent.KEYCODE_BUTTON_B:
            dismiss();
            return true;
        }
        return false;
    }

    public void dismiss() {
        if (mListener != null) mListener.onDismissed();
        if (isAdded()) {
            requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .remove(this)
                .commit();
        }
    }
}
