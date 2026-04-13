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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class WidgetPropertiesFragment extends DialogFragment {

    public interface OnPropertiesListener {
        void onLabelChanged(String newLabel);
        void onDelete();
    }

    private OnPropertiesListener mListener;
    private String mCurrentLabel;
    private boolean mIsButton;

    public static WidgetPropertiesFragment newInstance(String currentLabel, boolean isButton) {
        WidgetPropertiesFragment f = new WidgetPropertiesFragment();
        Bundle args = new Bundle();
        args.putString("label", currentLabel);
        args.putBoolean("isButton", isButton);
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
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Standard DialogFragment uses onCreateView or onCreateDialog. 
        // We'll use onCreateView but wrap it in a Material shape via the style.
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
            // Constrain width in landscape
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                width = (int)(400 * getResources().getDisplayMetrics().density);
            }
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
