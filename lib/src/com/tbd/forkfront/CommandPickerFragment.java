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
    private Runnable mInitRunnable;

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
                case KeyEvent.KEYCODE_BACK:
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
                dispatchCommandPicked(position);
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
        if (savedInstanceState != null) {
            int restoredPos = savedInstanceState.getInt("selected_position", -1);
            if (restoredPos >= 0 && restoredPos < mAdapter.getCount()) {
                mAdapter.setSelectedPosition(restoredPos);
            }
        }
        mInitRunnable = new Runnable() {
            @Override
            public void run() {
                if (mListView == null) return;
                int initialPos = mAdapter.getSelectedPosition();
                if (initialPos < 0 || initialPos >= mAdapter.getCount()) {
                    for (int i = 0; i < mAdapter.getCount(); i++) {
                        if (mAdapter.isEnabled(i)) {
                            initialPos = i;
                            break;
                        }
                    }
                }
                if (initialPos >= 0) {
                    mAdapter.setSelectedPosition(initialPos);
                    mListView.setSelection(initialPos);
                    mAdapter.applyHighlightToListView(mListView);
                }
                mListView.requestFocus();
            }
        };
        mListView.post(mInitRunnable);
    }

    @Override
    public void onDestroyView() {
        if (mListView != null) {
            if (mInitRunnable != null) {
                mListView.removeCallbacks(mInitRunnable);
            }
            mListView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter != null) {
            outState.putInt("selected_position", mAdapter.getSelectedPosition());
        }
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
            moveSelection(-1);
            return true;

        case KeyEvent.KEYCODE_DPAD_DOWN:
            moveSelection(1);
            return true;

        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_BUTTON_A:
            dispatchCommandPicked(mAdapter.getSelectedPosition());
            return true;

        case KeyEvent.KEYCODE_BUTTON_B:
        case KeyEvent.KEYCODE_BACK:
            dismiss();
            return true;
        }
        return false;
    }

    private void moveSelection(int delta) {
        int pos = mAdapter.getSelectedPosition();
        if (pos < 0 || pos >= mAdapter.getCount()) {
            pos = (delta < 0) ? mAdapter.getCount() - 1 : 0;
        } else {
            pos += delta;
        }
        while (pos >= 0 && pos < mAdapter.getCount()
               && !mAdapter.isEnabled(pos)) {
            pos += delta;
        }
        if (pos >= 0 && pos < mAdapter.getCount()) {
            mAdapter.setSelectedPosition(pos);
            mListView.setSelection(pos);
            mAdapter.applyHighlightToListView(mListView);
        }
    }

    private void dispatchCommandPicked(int position) {
        if (position < 0 || position >= mAdapter.getCount()) return;
        Object item = mAdapter.getItem(position);
        if (item instanceof CmdRegistry.CmdInfo) {
            if (mListener != null) {
                mListener.onCommandPicked((CmdRegistry.CmdInfo) item);
            }
        }
    }

    public void dismiss() {
        if (isRemoving() || !isAdded()) return;
        if (mListener != null) mListener.onDismissed();
        requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .remove(this)
            .commitAllowingStateLoss();
    }
}
