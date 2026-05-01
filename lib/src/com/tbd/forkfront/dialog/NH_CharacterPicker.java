package com.tbd.forkfront.dialog;
import com.tbd.forkfront.R;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.commands.CharacterAdapter;
import com.tbd.forkfront.engine.NetHackIO;
import com.tbd.forkfront.engine.UpdateAssets;
import com.tbd.forkfront.ui.Util;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.tbd.forkfront.gamepad.UiContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NH_CharacterPicker {
    private final NetHackIO mIO;
    private final NH_State mState;
    private AppCompatActivity mActivity;
    private ViewGroup mRoot;
    private RecyclerView mList;
    private View mNewCharacterPanel;
    private EditText mNameInput;
    private CheckBox mWizardMode;
    private TextView mErrorText;
    private List<String> mSaves;
    private int mMaxChars;
    private File mSaveDir;

    public NH_CharacterPicker(NetHackIO io, NH_State state) {
        mIO = io;
        mState = state;
    }

    public void setContext(AppCompatActivity activity) {
        mActivity = activity;
        if (activity != null) {
            // NetHack's HACKDIR lives in external app storage (see UpdateAssets);
            // getFilesDir() points at internal storage and silently misses every save.
            File dataDir = activity.getExternalFilesDir(null);
            if (dataDir == null) dataDir = activity.getFilesDir();
            mSaveDir = new File(dataDir, "save");
            if (mRoot != null) {
                // Re-show the picker if it was visible on the old activity
                show(mMaxChars, mSaves.toArray(new String[0]));
            }
        }
    }

    public void show(final int nMaxChars, final String[] saves) {
        mMaxChars = nMaxChars;
        mSaves = new ArrayList<>(Arrays.asList(saves));
        
        mState.getScope().runOnActivity(() -> {
            AppCompatActivity activity = mActivity;
            if (activity == null || activity.isFinishing()) return;

            if (mRoot != null && mRoot.getParent() != null) {
                ((ViewGroup) mRoot.getParent()).removeView(mRoot);
            }

            mRoot = (ViewGroup) Util.inflate(activity, R.layout.dialog_character_picker, R.id.dlg_frame);
            
            mState.getGamepadContext().pushContext(UiContext.CHARACTER_PICKER);
            
            mList = mRoot.findViewById(R.id.character_list);
            mNewCharacterPanel = mRoot.findViewById(R.id.new_character_panel);
            mNameInput = mRoot.findViewById(R.id.name_input);
            mWizardMode = mRoot.findViewById(R.id.wizard_mode);
            mErrorText = mRoot.findViewById(R.id.error_text);
            
            mList.setLayoutManager(new LinearLayoutManager(activity));
            mList.addItemDecoration(new androidx.recyclerview.widget.DividerItemDecoration(activity, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL));
            mList.setAdapter(new CharacterAdapter(mSaves, mState.getPrefs().getLastUsername(), mSaveDir, new CharacterAdapter.OnCharacterSelectedListener() {
                @Override
                public void onCharacterSelected(String plname) {
                    submit(plname, false);
                }

                @Override
                public void onNewCharacterRequested() {
                    showNewCharacterPanel();
                }
            }));
            
            Button btnCancelNew = mRoot.findViewById(R.id.btn_cancel_new);
            btnCancelNew.setOnClickListener(v -> hideNewCharacterPanel());
            
            Button btnBegin = mRoot.findViewById(R.id.btn_begin);
            btnBegin.setOnClickListener(v -> {
                String name = mNameInput.getText().toString().trim();
                if (validate(name)) {
                    submit(name, mWizardMode.isChecked());
                }
            });
            
            if (mSaves.isEmpty()) {
                showNewCharacterPanel();
            } else {
                focusFirstRowWhenAvailable();
            }
        });
    }

    // The RecyclerView has no children at the moment show() runs — items are bound during
    // the next layout pass. Calling requestFocus before then either no-ops or focuses the
    // RecyclerView itself, neither of which gives the D-pad a row to navigate from.
    private void focusFirstRowWhenAvailable() {
        if (mList.getChildCount() > 0) {
            View first = mList.getChildAt(0);
            if (first != null && first.requestFocus()) return;
        }
        mList.addOnChildAttachStateChangeListener(
            new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(View v) {
                    if (mList.getChildAdapterPosition(v) == 0) {
                        mList.removeOnChildAttachStateChangeListener(this);
                        v.post(v::requestFocus);
                    }
                }
                @Override public void onChildViewDetachedFromWindow(View v) {}
            });
    }

    private void showNewCharacterPanel() {
        mList.setVisibility(View.GONE);
        mNewCharacterPanel.setVisibility(View.VISIBLE);
        mNameInput.requestFocus();
        // Only auto-show the IME in touch mode. With a gamepad, the IME would intercept
        // D-pad keys for its own navigation; users can tap the field (or hit BUTTON_A)
        // to bring it up explicitly.
        if (mNameInput.isInTouchMode()) {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(mNameInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideNewCharacterPanel() {
        if (mSaves.isEmpty()) {
            cancel();
            return;
        }
        mNewCharacterPanel.setVisibility(View.GONE);
        mList.setVisibility(View.VISIBLE);
        mList.requestFocus();
        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mNameInput.getWindowToken(), 0);
        }
    }

    private boolean validate(String name) {
        if (name.isEmpty()) {
            mErrorText.setText("Name cannot be empty");
            mErrorText.setVisibility(View.VISIBLE);
            return false;
        }
        if (name.length() > mMaxChars - 1) {
            mErrorText.setText("Name too long");
            mErrorText.setVisibility(View.VISIBLE);
            return false;
        }
        for (String s : mSaves) {
            if (s.equalsIgnoreCase(name)) {
                mErrorText.setText("Character already exists");
                mErrorText.setVisibility(View.VISIBLE);
                return false;
            }
        }
        mErrorText.setVisibility(View.GONE);
        return true;
    }

    private void submit(String name, boolean wizard) {
        mIO.sendLineCmd(name + (wizard ? "1" : "0"));
        dismiss();
    }

    private void cancel() {
        mIO.sendLineCmd("\033 ");
        dismiss();
    }

    public void dismiss() {
        if (mRoot != null) {
            mState.getGamepadContext().popContext(UiContext.CHARACTER_PICKER);
            ((ViewGroup) mRoot.getParent()).removeView(mRoot);
            mRoot = null;
        }
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        android.util.Log.d("NH_Picker", "handleKeyDown: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mNewCharacterPanel.getVisibility() == View.VISIBLE && !mSaves.isEmpty()) {
                hideNewCharacterPanel();
            } else {
                cancel();
            }
            return true;
        }
        return false;
    }

    public boolean handleGamepadKey(int keyCode, KeyEvent event) {
        android.util.Log.d("NH_Picker", "handleGamepadKey: " + keyCode + " action: " + event.getAction());
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_ENTER:
                    // If the user is on the name field, open the IME so they can type.
                    // Otherwise let the system synthesize a click on the focused view.
                    if (mNewCharacterPanel != null
                            && mNewCharacterPanel.getVisibility() == View.VISIBLE
                            && mNameInput != null
                            && mNameInput.isFocused()) {
                        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(mNameInput, InputMethodManager.SHOW_IMPLICIT);
                        }
                        return true;
                    }
                    android.util.Log.d("NH_Picker", "Navigation/Selection key - letting system handle");
                    return false;
                case KeyEvent.KEYCODE_BUTTON_B:
                    if (mNewCharacterPanel.getVisibility() == View.VISIBLE && !mSaves.isEmpty()) {
                        hideNewCharacterPanel();
                    } else {
                        cancel();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    if (mList.getVisibility() == View.VISIBLE) {
                        mList.smoothScrollBy(0, -mList.getHeight());
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (mList.getVisibility() == View.VISIBLE) {
                        mList.smoothScrollBy(0, mList.getHeight());
                        return true;
                    }
                    break;
            }
        }
        return false;
    }
}
