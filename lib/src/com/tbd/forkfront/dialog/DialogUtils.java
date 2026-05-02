package com.tbd.forkfront.dialog;

import android.app.Dialog;
import android.view.ViewGroup;
import android.view.Window;
import androidx.fragment.app.DialogFragment;

public class DialogUtils {

  public static void setDialogSize(DialogFragment fragment, int landWidthDp, int landHeightDp) {
    Dialog dialog = fragment.getDialog();
    if (dialog == null) return;
    Window window = dialog.getWindow();
    if (window == null) return;

    int width = ViewGroup.LayoutParams.MATCH_PARENT;
    int height = ViewGroup.LayoutParams.MATCH_PARENT;

    if (fragment.getResources().getConfiguration().orientation
        == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
      float density = fragment.getResources().getDisplayMetrics().density;
      width = (int) (landWidthDp * density);
      height = (int) (landHeightDp * density);
    }

    window.setLayout(width, height);
    window.setBackgroundDrawableResource(android.R.color.transparent);
  }
}
