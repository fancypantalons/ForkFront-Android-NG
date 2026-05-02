package com.tbd.forkfront.input;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import com.google.android.material.button.MaterialButton;
import com.tbd.forkfront.ui.ThemeUtils;
import java.util.ArrayList;
import java.util.List;

/** A modern, Material 3 based 8-way directional pad for NetHack. */
public class DirectionalPadView extends GridLayout {

  public interface OnDirectionListener {
    void onDirection(char cmd);
  }

  private OnDirectionListener mListener;
  private final List<TouchRepeatHelper> mRepeatHelpers = new ArrayList<>();

  public DirectionalPadView(Context context) {
    super(context);
    init(context);
  }

  public DirectionalPadView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  private void init(Context context) {
    setColumnCount(3);
    setRowCount(3);

    addDirectionButton(context, "y", "↖", 0, 0);
    addDirectionButton(context, "k", "↑", 0, 1);
    addDirectionButton(context, "u", "↗", 0, 2);

    addDirectionButton(context, "h", "←", 1, 0);
    addDirectionButton(context, ".", "•", 1, 1);
    addDirectionButton(context, "l", "→", 1, 2);

    addDirectionButton(context, "b", "↙", 2, 0);
    addDirectionButton(context, "j", "↓", 2, 1);
    addDirectionButton(context, "n", "↘", 2, 2);
  }

  private void addDirectionButton(
      Context context, final String cmd, String label, int row, int col) {
    MaterialButton button = ThemeUtils.createButtonText(context);
    button.setText(label);
    button.setPadding(0, 0, 0, 0);
    button.setInsetTop(0);
    button.setInsetBottom(0);
    button.setMinimumWidth(0);
    button.setMinimumHeight(0);

    // Use the dPadBtn style properties manually since we're in Java
    button.setTextSize(18f);

    GridLayout.LayoutParams params =
        new GridLayout.LayoutParams(GridLayout.spec(row, 1f), GridLayout.spec(col, 1f));
    params.width = 0;
    params.height = 0;
    button.setLayoutParams(params);

    final char direction = cmd.charAt(0);
    final TouchRepeatHelper repeatHelper = new TouchRepeatHelper();
    mRepeatHelpers.add(repeatHelper);
    button.setOnTouchListener(
        new OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
              case MotionEvent.ACTION_DOWN:
                v.setPressed(true);
                if (mListener != null) {
                  mListener.onDirection(direction);
                }
                repeatHelper.startRepeat(
                    () -> {
                      if (mListener != null) {
                        mListener.onDirection(direction);
                      }
                    });
                return true;
              case MotionEvent.ACTION_UP:
              case MotionEvent.ACTION_CANCEL:
                v.setPressed(false);
                repeatHelper.cancelRepeat();
                v.performClick();
                return true;
            }
            return false;
          }
        });

    addView(button);
  }

  public void setOnDirectionListener(OnDirectionListener listener) {
    mListener = listener;
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    for (TouchRepeatHelper helper : mRepeatHelpers) {
      helper.destroy();
    }
    mRepeatHelpers.clear();
  }
}
