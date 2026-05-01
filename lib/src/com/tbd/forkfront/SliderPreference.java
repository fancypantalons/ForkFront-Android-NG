package com.tbd.forkfront;

import android.content.Context;
import androidx.preference.DialogPreference;
import android.util.AttributeSet;

public class SliderPreference extends DialogPreference {
    private static final String androidns = "http://schemas.android.com/apk/res/android";
    private static final String thisns = "http://schemas.android.com/apk/res-auto";

    private String mDialogMessage, mSuffix;
    private int mDefault, mMin, mMax, mValue;

    public SliderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDialogMessage = attrs.getAttributeValue(androidns, "dialogMessage");
        mSuffix = attrs.getAttributeValue(androidns, "text");
        mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
        mMin = attrs.getAttributeIntValue(thisns, "min", 0);
        mMax = attrs.getAttributeIntValue(androidns, "max", 100);
        mValue = getPersistedInt(mDefault);
    }

    public String getDialogMessage() { return mDialogMessage; }
    public String getSuffix() { return mSuffix; }
    public int getMin() { return mMin; }
    public int getMax() { return mMax; }
    public int getValue() { return mValue; }

    public void setValue(int value) {
        mValue = value;
        persistInt(value);
        callChangeListener(value);
    }
}
