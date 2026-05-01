package com.tbd.forkfront.gamepad;

import android.app.Dialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.button.MaterialButton;
import com.tbd.forkfront.R;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Modal dialog that captures a button press or chord from the controller.
 *
 * The user holds modifier buttons (L1, R1), then presses the action button,
 * then releases all buttons. When all are released the captured chord is shown
 * and the Confirm button is enabled.
 *
 * Result is delivered via FragmentResultListener with key REQUEST_KEY.
 * Bundle extras: "primary" (int), "modifiers" (int[]).
 */
public class BindingCaptureDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "binding_capture_result";
    private static final String ARG_LABEL = "label";

    /**
     * Interface for checking chord conflicts against the existing binding list.
     * Returns the display name of the command already bound to the given chord,
     * or null if no conflict exists.
     */
    public interface ConflictChecker {
        @Nullable String getConflictCommand(@NonNull Chord chord);
    }

    private TextView mPreviewText;
    private View mWarningRow;
    private TextView mWarningText;
    private MaterialButton mConfirmBtn;
    private Dialog mDialog;
    private ConflictChecker mConflictChecker;

    /** Currently held button codes (KeyEvent keycodes). */
    private final TreeSet<Integer> mHeld = new TreeSet<>();
    /** Order buttons were pressed — last = primary. */
    private final List<Integer> mPressOrder = new ArrayList<>();
    /** Finalized chord after all buttons released. */
    private Chord mFinalChord;

    public static BindingCaptureDialogFragment newInstance(@Nullable String commandLabel) {
        return newInstance(commandLabel, null);
    }

    public static BindingCaptureDialogFragment newInstance(@Nullable String commandLabel,
                                                            @Nullable ConflictChecker checker) {
        BindingCaptureDialogFragment f = new BindingCaptureDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LABEL, commandLabel);
        f.setArguments(args);
        f.mConflictChecker = checker;
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String label = getArguments() != null ? getArguments().getString(ARG_LABEL) : null;

        View content = getLayoutInflater()
            .inflate(R.layout.dialog_binding_capture, null, false);

        mPreviewText = content.findViewById(R.id.capture_preview);
        mWarningRow = content.findViewById(R.id.capture_warning_row);
        mWarningText = content.findViewById(R.id.capture_warning_text);
        mConfirmBtn = content.findViewById(R.id.capture_confirm_btn);

        content.findViewById(R.id.capture_cancel_btn).setOnClickListener(v -> dismiss());
        mConfirmBtn.setOnClickListener(v -> deliverResult());
        mConfirmBtn.setEnabled(false);

        mDialog = new Dialog(requireContext(), R.style.NH_Dialog);
        mDialog.setTitle(label != null ? "Bind: " + label : "Capture Binding");
        mDialog.setContentView(content);

        // Intercept all key events so we can capture gamepad buttons
        mDialog.setOnKeyListener((d, keyCode, event) -> {
            if (!isGamepadButton(keyCode)) return false;
            if (event.getRepeatCount() > 0) return true;

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                onButtonDown(keyCode);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                onButtonUp(keyCode);
            }
            return true;
        });

        return mDialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        GamepadDispatcher gd = GamepadDispatcher.getInstance();
        if (gd != null) gd.pushContext(UiContext.BINDING_CAPTURE);
    }

    @Override
    public void onPause() {
        super.onPause();
        GamepadDispatcher gd = GamepadDispatcher.getInstance();
        if (gd != null) gd.popContext(UiContext.BINDING_CAPTURE);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Make the dialog wide enough to look good
            int width = (int)(400 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void onButtonDown(int keyCode) {
        if (!mHeld.contains(keyCode)) {
            mHeld.add(keyCode);
            mPressOrder.add(keyCode);
            mFinalChord = null;
            if (mConfirmBtn != null) mConfirmBtn.setEnabled(false);
            clearConflictWarning();
        }
        updatePreview();
    }

    private void onButtonUp(int keyCode) {
        mHeld.remove(keyCode);
        if (mHeld.isEmpty() && !mPressOrder.isEmpty()) {
            mFinalChord = buildChord();
            if (mConfirmBtn != null) {
                boolean hasChord = mFinalChord != null;
                mConfirmBtn.setEnabled(hasChord);
                if (hasChord) {
                    checkAndShowConflict(mFinalChord);
                }
            }
        }
        updatePreview();
    }

    private void clearCapture() {
        mHeld.clear();
        mPressOrder.clear();
        mFinalChord = null;
        if (mConfirmBtn != null) mConfirmBtn.setEnabled(false);
        clearConflictWarning();
        updatePreview();
    }

    private void updatePreview() {
        if (mPreviewText == null) return;

        if (mPressOrder.isEmpty()) {
            mPreviewText.setText("Waiting for input...");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mPressOrder.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(ButtonNames.of(mPressOrder.get(i)));
        }
        if (!mHeld.isEmpty()) {
            sb.append("  (release to confirm)");
        }
        mPreviewText.setText(sb.toString());
    }

    private void checkAndShowConflict(Chord chord) {
        if (mConflictChecker == null || mWarningRow == null || mWarningText == null) return;
        String conflictName = mConflictChecker.getConflictCommand(chord);
        if (conflictName != null) {
            mWarningRow.setVisibility(View.VISIBLE);
            mWarningText.setText("Already bound to: " + conflictName);
            if (mConfirmBtn != null) {
                mConfirmBtn.setText("Replace");
                mConfirmBtn.setTextColor(com.google.android.material.color.MaterialColors
                    .getColor(requireContext(), com.google.android.material.R.attr.colorError, 0));
            }
        } else {
            clearConflictWarning();
        }
    }

    private void clearConflictWarning() {
        if (mWarningRow != null) mWarningRow.setVisibility(View.GONE);
        if (mConfirmBtn != null) {
            mConfirmBtn.setText("Confirm");
            mConfirmBtn.setTextColor(com.google.android.material.color.MaterialColors
                .getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, 0));
        }
    }

    private Chord buildChord() {
        if (mPressOrder.isEmpty()) return null;
        int primaryCode = mPressOrder.get(mPressOrder.size() - 1);
        ButtonId primary = new ButtonId(primaryCode);
        TreeSet<ButtonId> all = new TreeSet<>();
        for (int code : mPressOrder) all.add(new ButtonId(code));
        try {
            return new Chord(all, primary);
        } catch (Exception e) {
            return null;
        }
    }

    private void deliverResult() {
        if (mFinalChord == null) return;

        int[] modCodes = new int[mFinalChord.modifiers().size()];
        int i = 0;
        for (ButtonId mod : mFinalChord.modifiers()) {
            modCodes[i++] = mod.code;
        }

        Bundle result = new Bundle();
        result.putInt("primary", mFinalChord.primary.code);
        result.putIntArray("modifiers", modCodes);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
    }

    private static boolean isGamepadButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }
}
