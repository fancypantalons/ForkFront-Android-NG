package com.tbd.forkfront;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;

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

        MaterialCardView card = new MaterialCardView(context);
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setUseCompatPadding(false);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = context.getResources().getDimensionPixelSize(R.dimen.padding_medium);
        layout.setPadding(padding, padding, padding, padding);

        if (pref.getDialogMessage() != null) {
            TextView splashText = new TextView(context);
            splashText.setText(pref.getDialogMessage());
            splashText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            layout.addView(splashText);

            MaterialDivider divider = new MaterialDivider(context);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dividerParams.setMargins(0, padding / 2, 0, padding);
            layout.addView(divider, dividerParams);
        }

        mValueText = new TextView(context);
        mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
        mValueText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.text_size_xlarge));
        mValueText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium);
        layout.addView(mValueText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mValue = pref.getValue();

        mSeekBar = new SeekBar(context);
        mSeekBar.setMax(pref.getMax() - pref.getMin());
        mSeekBar.setProgress(mValue - pref.getMin());
        mSeekBar.setOnSeekBarChangeListener(this);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        seekParams.setMargins(0, padding, 0, 0);
        layout.addView(mSeekBar, seekParams);

        updateValueText();

        card.addView(layout);
        return card;
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
