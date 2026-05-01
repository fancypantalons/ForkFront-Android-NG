package com.tbd.forkfront.settings;
import com.tbd.forkfront.R;

import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.text.Html;
import android.text.Layout.Alignment;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.AlignmentSpan;
import android.util.AttributeSet;
import android.widget.TextView;

public class CreditsPreference extends Preference {
    public CreditsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
        setLayoutResource(R.layout.textwindow);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView text = (TextView) holder.findViewById(R.id.text_view);
        if (text != null) {
            text.setText(Html.fromHtml(getContext().getString(R.string.credits)), TextView.BufferType.SPANNABLE);
            text.setMovementMethod(LinkMovementMethod.getInstance());
            Spannable span = (Spannable) text.getText();
            span.setSpan(new AlignmentSpan.Standard(Alignment.ALIGN_CENTER), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}