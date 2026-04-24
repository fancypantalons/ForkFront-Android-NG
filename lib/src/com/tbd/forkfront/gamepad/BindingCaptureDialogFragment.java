package com.tbd.forkfront.gamepad;

import android.app.Dialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
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

    private TextView mPreviewText;
    private AlertDialog mDialog;

    /** Currently held button codes (KeyEvent keycodes). */
    private final TreeSet<Integer> mHeld = new TreeSet<>();
    /** Order buttons were pressed — last = primary. */
    private final List<Integer> mPressOrder = new ArrayList<>();
    /** Finalized chord after all buttons released. */
    private Chord mFinalChord;

    public static BindingCaptureDialogFragment newInstance(@Nullable String commandLabel) {
        BindingCaptureDialogFragment f = new BindingCaptureDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LABEL, commandLabel);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String label = getArguments() != null ? getArguments().getString(ARG_LABEL) : null;

        View content = getLayoutInflater()
            .inflate(R.layout.dialog_binding_capture, null, false);

        mPreviewText = content.findViewById(R.id.capture_preview);
        content.findViewById(R.id.capture_clear_btn).setOnClickListener(v -> clearCapture());

        String title = label != null ? "Bind: " + label : "Capture Binding";

        mDialog = new AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(content)
            .setPositiveButton("Confirm", (d, which) -> deliverResult())
            .setNegativeButton("Cancel", null)
            .create();

        // Disable confirm until a chord is captured
        mDialog.setOnShowListener(d -> {
            mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        });

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
        }
    }

    private void onButtonDown(int keyCode) {
        if (!mHeld.contains(keyCode)) {
            mHeld.add(keyCode);
            mPressOrder.add(keyCode);
            mFinalChord = null;
            if (mDialog != null) mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        }
        updatePreview();
    }

    private void onButtonUp(int keyCode) {
        mHeld.remove(keyCode);
        if (mHeld.isEmpty() && !mPressOrder.isEmpty()) {
            mFinalChord = buildChord();
            if (mDialog != null) mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        }
        updatePreview();
    }

    private void clearCapture() {
        mHeld.clear();
        mPressOrder.clear();
        mFinalChord = null;
        if (mDialog != null) mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        if (mPreviewText != null) mPreviewText.setText("Waiting for input…");
    }

    private void updatePreview() {
        if (mPreviewText == null) return;
        if (mPressOrder.isEmpty()) {
            mPreviewText.setText("Waiting for input…");
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
