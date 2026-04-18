package com.tbd.forkfront.gamepad;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/**
 * Modal dialog that captures a button press or chord from the controller
 * and records it as a new binding.
 * Full implementation is deferred to a future phase.
 */
public class BindingCaptureDialogFragment extends DialogFragment {

    public static BindingCaptureDialogFragment newInstance() {
        return new BindingCaptureDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
            .setTitle("Capture Binding")
            .setMessage("Press a button or chord on your controller…\n\n(Editor not yet implemented)")
            .setNegativeButton("Cancel", null)
            .create();
    }
}
