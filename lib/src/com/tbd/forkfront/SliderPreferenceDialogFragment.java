package com.tbd.forkfront;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;

public class SliderPreferenceDialogFragment extends PreferenceDialogFragmentCompat implements SeekBar.OnSeekBarChangeListener {

    private SeekBar mSeekBar;
    private TextView mValueText;
    private int mValue;

    public static SliderPreferenceDialogFragment newInstance(String key) {
        SliderPreferenceDialogFragment fragment = new SliderPreferenceDialogFragment();
        Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected View onCreateDialogView(android.content.Context context) {
        SliderPreference pref = (SliderPreference) getPreference();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = context.getResources().getDimensionPixelSize(R.dimen.padding_medium);
        layout.setPadding(padding, padding, padding, padding);

        if (pref.getDialogMessage() != null) {
            TextView splashText = new TextView(context);
            splashText.setText(pref.getDialogMessage());
            layout.addView(splashText);
        }

        mValueText = new TextView(context);
        mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
        mValueText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.text_size_xlarge));
        layout.addView(mValueText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mValue = pref.getValue();

        mSeekBar = new SeekBar(context);
        mSeekBar.setMax(pref.getMax() - pref.getMin());
        mSeekBar.setProgress(mValue - pref.getMin());
        mSeekBar.setOnSeekBarChangeListener(this);
        layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        updateValueText();

        return layout;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            SliderPreference pref = (SliderPreference) getPreference();
            if (pref.callChangeListener(mValue)) {
                pref.setValue(mValue);
            }
        }
    }

    private void updateValueText() {
        SliderPreference pref = (SliderPreference) getPreference();
        String t = String.valueOf(mValue);
        mValueText.setText(pref.getSuffix() == null ? t : t.concat(pref.getSuffix()));
    }

    @Override
    public void onProgressChanged(SeekBar seek, int progress, boolean fromTouch) {
        SliderPreference pref = (SliderPreference) getPreference();
        mValue = progress + pref.getMin();
        updateValueText();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seek) {}

    @Override
    public void onStopTrackingTouch(SeekBar seek) {}
}
