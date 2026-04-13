package com.tbd.forkfront;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

public class WidgetPropertiesFragment extends DialogFragment {

    public interface OnPropertiesListener {
        void onLabelChanged(String newLabel);
        void onOrientationChanged(boolean horizontal);
        void onOpacityChanged(int opacity);
        void onDelete();
    }

    private OnPropertiesListener mListener;
    private String mCurrentLabel;
    private boolean mIsButton;
    private boolean mIsContextual;
    private boolean mIsHorizontal;
    private int mOpacity;

    public static WidgetPropertiesFragment newInstance(String currentLabel, boolean isButton, boolean isContextual, boolean isHorizontal, int opacity) {
        WidgetPropertiesFragment f = new WidgetPropertiesFragment();
        Bundle args = new Bundle();
        args.putString("label", currentLabel);
        args.putBoolean("isButton", isButton);
        args.putBoolean("isContextual", isContextual);
        args.putBoolean("isHorizontal", isHorizontal);
        args.putInt("opacity", opacity);
        f.setArguments(args);
        return f;
    }

    public void setOnPropertiesListener(OnPropertiesListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mCurrentLabel = getArguments().getString("label");
            mIsButton = getArguments().getBoolean("isButton");
            mIsContextual = getArguments().getBoolean("isContextual");
            mIsHorizontal = getArguments().getBoolean("isHorizontal");
            mOpacity = getArguments().getInt("opacity", 191);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.widget_properties, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputEditText editLabel = view.findViewById(R.id.edit_label);
        View labelLayout = view.findViewById(R.id.label_layout);
        
        if (mIsButton) {
            editLabel.setText(mCurrentLabel);
            editLabel.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (mListener != null) {
                        mListener.onLabelChanged(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        } else {
            labelLayout.setVisibility(View.GONE);
        }

        View orientationLayout = view.findViewById(R.id.orientation_layout);
        MaterialSwitch switchOrientation = view.findViewById(R.id.switch_orientation);
        if (mIsContextual) {
            orientationLayout.setVisibility(View.VISIBLE);
            switchOrientation.setChecked(mIsHorizontal);
            switchOrientation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mListener != null) {
                    mListener.onOrientationChanged(isChecked);
                }
            });
        } else {
            orientationLayout.setVisibility(View.GONE);
        }

        // Opacity slider
        com.google.android.material.slider.Slider opacitySlider = view.findViewById(R.id.opacity_slider);
        android.widget.TextView opacityValue = view.findViewById(R.id.opacity_value);
        opacitySlider.setValue(mOpacity);
        opacityValue.setText(String.format("%d%%", (int)((mOpacity / 255.0f) * 100)));
        opacitySlider.addOnChangeListener((slider, value, fromUser) -> {
            int opacity = (int) value;
            opacityValue.setText(String.format("%d%%", (int)((opacity / 255.0f) * 100)));
            if (mListener != null) {
                mListener.onOpacityChanged(opacity);
            }
        });

        view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onDelete();
            }
            dismiss();
        });

        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                width = (int)(400 * getResources().getDisplayMetrics().density);
            }
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
