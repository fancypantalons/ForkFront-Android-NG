package com.tbd.forkfront.settings;
import com.tbd.forkfront.R;
import com.tbd.forkfront.commands.PinCommandsFragment;
import com.tbd.forkfront.context.CmdRegistry;

import android.app.Dialog;
import android.os.Bundle;
import java.util.Locale;
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
        void onFontSizeChanged(int fontSize);
        void onRowsChanged(int rows);
        void onColumnsChanged(int columns);
        void onCategoryChanged(String category);
        void onContextualOnlyChanged(boolean contextualOnly);
        void onPinnedCommandsChanged(java.util.Set<String> pinnedCommands);
        void onMoveToOtherScreen();
        void onDelete();
    }

    private OnPropertiesListener mListener;
    private String mCurrentLabel;
    private boolean mIsButton;
    private boolean mIsCommandPalette;
    private boolean mIsHorizontal;
    private boolean mShowFontSize;
    private boolean mShowMoveButton;
    private int mOpacity;
    private int mFontSize;
    private int mRows;
    private int mColumns;
    private String mCategory;
    private boolean mContextualOnly;
    private java.util.Set<String> mPinnedCommands;

    public static WidgetPropertiesFragment newInstance(String currentLabel, boolean isButton, boolean isCommandPalette, boolean isHorizontal, int opacity, boolean showFontSize, int fontSize, int rows, int columns, String category, boolean contextualOnly, java.util.Set<String> pinnedCommands, boolean showMoveButton) {
        WidgetPropertiesFragment f = new WidgetPropertiesFragment();
        Bundle args = new Bundle();
        args.putString("label", currentLabel);
        args.putBoolean("isButton", isButton);
        args.putBoolean("isCommandPalette", isCommandPalette);
        args.putBoolean("isHorizontal", isHorizontal);
        args.putInt("opacity", opacity);
        args.putBoolean("showFontSize", showFontSize);
        args.putInt("fontSize", fontSize);
        args.putInt("rows", rows);
        args.putInt("columns", columns);
        args.putString("category", category);
        args.putBoolean("contextualOnly", contextualOnly);
        java.util.ArrayList<String> pinnedList = new java.util.ArrayList<>(pinnedCommands);
        args.putStringArrayList("pinnedCommands", pinnedList);
        args.putBoolean("showMoveButton", showMoveButton);
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
            mIsCommandPalette = getArguments().getBoolean("isCommandPalette");
            mIsHorizontal = getArguments().getBoolean("isHorizontal");
            mOpacity = getArguments().getInt("opacity", 191);
            mShowFontSize = getArguments().getBoolean("showFontSize", false);
            mFontSize = getArguments().getInt("fontSize", 15);
            mRows = getArguments().getInt("rows", 3);
            mColumns = getArguments().getInt("columns", 3);
            mCategory = getArguments().getString("category");
            mContextualOnly = getArguments().getBoolean("contextualOnly", false);
            mPinnedCommands = new java.util.HashSet<>();
            java.util.ArrayList<String> pinnedList = getArguments().getStringArrayList("pinnedCommands");
            if (pinnedList != null) {
                mPinnedCommands.addAll(pinnedList);
            }
            mShowMoveButton = getArguments().getBoolean("showMoveButton", false);
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
        View rowsLayout = view.findViewById(R.id.rows_layout);
        View columnsLayout = view.findViewById(R.id.columns_layout);

        MaterialSwitch switchOrientation = view.findViewById(R.id.switch_orientation);
        if (mIsCommandPalette) {
            orientationLayout.setVisibility(View.VISIBLE);
            switchOrientation.setChecked(mIsHorizontal);
            switchOrientation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mListener != null) {
                    mListener.onOrientationChanged(isChecked);
                }
                // For command palette, toggle which slider is shown
                if (mIsCommandPalette) {
                    if (isChecked) {
                        // Horizontal - show rows
                        rowsLayout.setVisibility(View.VISIBLE);
                        columnsLayout.setVisibility(View.GONE);
                    } else {
                        // Vertical - show columns
                        rowsLayout.setVisibility(View.GONE);
                        columnsLayout.setVisibility(View.VISIBLE);
                    }
                }
            });
        } else {
            orientationLayout.setVisibility(View.GONE);
        }

        // Opacity slider
        com.google.android.material.slider.Slider opacitySlider = view.findViewById(R.id.opacity_slider);
        android.widget.TextView opacityValue = view.findViewById(R.id.opacity_value);
        opacitySlider.setValue(mOpacity);
        opacityValue.setText(String.format(Locale.ROOT, "%d%%", (int)((mOpacity / 255.0f) * 100)));
        opacitySlider.addOnChangeListener((slider, value, fromUser) -> {
            int opacity = (int) value;
            opacityValue.setText(String.format(Locale.ROOT, "%d%%", (int)((opacity / 255.0f) * 100)));
            if (mListener != null) {
                mListener.onOpacityChanged(opacity);
            }
        });

        // Font size slider
        View fontSizeLayout = view.findViewById(R.id.font_size_layout);
        com.google.android.material.slider.Slider fontSizeSlider = view.findViewById(R.id.font_size_slider);
        android.widget.TextView fontSizeValueText = view.findViewById(R.id.font_size_value);
        
        if (mShowFontSize) {
            fontSizeLayout.setVisibility(View.VISIBLE);
            fontSizeSlider.setValue(mFontSize);
            fontSizeValueText.setText(String.format(Locale.ROOT, "%dsp", mFontSize));
            fontSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
                int size = (int) value;
                fontSizeValueText.setText(String.format(Locale.ROOT, "%dsp", size));
                if (mListener != null) {
                    mListener.onFontSizeChanged(size);
                }
            });
        } else {
            fontSizeLayout.setVisibility(View.GONE);
        }

        // Command palette specific settings
        View categoryLayout = view.findViewById(R.id.category_layout);

        if (mIsCommandPalette) {
            // Show rows slider for horizontal orientation, columns slider for vertical
            if (mIsHorizontal) {
                // Horizontal scrolling - specify number of rows (columns grow)
                rowsLayout.setVisibility(View.VISIBLE);
                columnsLayout.setVisibility(View.GONE);

                com.google.android.material.slider.Slider rowsSlider = view.findViewById(R.id.rows_slider);
                android.widget.TextView rowsValue = view.findViewById(R.id.rows_value);
                rowsSlider.setValue(mRows);
                rowsValue.setText(String.valueOf(mRows));
                rowsSlider.addOnChangeListener((slider, value, fromUser) -> {
                    int rows = (int) value;
                    rowsValue.setText(String.valueOf(rows));
                    if (mListener != null) {
                        mListener.onRowsChanged(rows);
                    }
                });
            } else {
                // Vertical scrolling - specify number of columns (rows grow)
                rowsLayout.setVisibility(View.GONE);
                columnsLayout.setVisibility(View.VISIBLE);

                com.google.android.material.slider.Slider columnsSlider = view.findViewById(R.id.columns_slider);
                android.widget.TextView columnsValue = view.findViewById(R.id.columns_value);
                columnsSlider.setValue(mColumns);
                columnsValue.setText(String.valueOf(mColumns));
                columnsSlider.addOnChangeListener((slider, value, fromUser) -> {
                    int columns = (int) value;
                    columnsValue.setText(String.valueOf(columns));
                    if (mListener != null) {
                        mListener.onColumnsChanged(columns);
                    }
                });
            }

            // Category dropdown
            categoryLayout.setVisibility(View.VISIBLE);
            android.widget.AutoCompleteTextView categoryDropdown = view.findViewById(R.id.category_dropdown);

            // Build category list (All + all categories)
            String[] categories = new String[CmdRegistry.Category.values().length + 1];
            categories[0] = "All Commands";
            for (int i = 0; i < CmdRegistry.Category.values().length; i++) {
                categories[i + 1] = CmdRegistry.Category.values()[i].getDisplayName();
            }

            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    categories
            );
            categoryDropdown.setAdapter(adapter);

            // Set current value
            if (mCategory == null || mCategory.isEmpty()) {
                categoryDropdown.setText("All Commands", false);
            } else {
                try {
                    CmdRegistry.Category cat = CmdRegistry.Category.valueOf(mCategory);
                    categoryDropdown.setText(cat.getDisplayName(), false);
                } catch (IllegalArgumentException e) {
                    categoryDropdown.setText("All Commands", false);
                }
            }

            categoryDropdown.setOnItemClickListener((parent, v, position, id) -> {
                if (mListener != null) {
                    if (position == 0) {
                        // "All Commands" selected
                        mListener.onCategoryChanged(null);
                    } else {
                        // Specific category selected
                        CmdRegistry.Category cat = CmdRegistry.Category.values()[position - 1];
                        mListener.onCategoryChanged(cat.name());
                    }
                }
            });

            // Context-relevant only switch
            View contextualOnlyLayout = view.findViewById(R.id.contextual_only_layout);
            contextualOnlyLayout.setVisibility(View.VISIBLE);
            MaterialSwitch switchContextualOnly = view.findViewById(R.id.switch_contextual_only);
            switchContextualOnly.setChecked(mContextualOnly);
            switchContextualOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mListener != null) {
                    mListener.onContextualOnlyChanged(isChecked);
                }
            });

            // Pin Commands button
            View pinCommandsLayout = view.findViewById(R.id.pin_commands_layout);
            pinCommandsLayout.setVisibility(View.VISIBLE);
            com.google.android.material.button.MaterialButton btnPinCommands = view.findViewById(R.id.btn_pin_commands);
            btnPinCommands.setOnClickListener(v -> {
                PinCommandsFragment pinFragment = PinCommandsFragment.newInstance(mPinnedCommands);
                pinFragment.setOnPinsChangedListener(new PinCommandsFragment.OnPinsChangedListener() {
                    @Override
                    public void onPinsChanged(java.util.Set<String> pinnedCommands) {
                        mPinnedCommands = pinnedCommands;
                        if (mListener != null) {
                            mListener.onPinnedCommandsChanged(pinnedCommands);
                        }
                    }
                });
                pinFragment.show(getParentFragmentManager(), "pin_commands");
            });
        } else {
            rowsLayout.setVisibility(View.GONE);
            columnsLayout.setVisibility(View.GONE);
            categoryLayout.setVisibility(View.GONE);
            View contextualOnlyLayout = view.findViewById(R.id.contextual_only_layout);
            contextualOnlyLayout.setVisibility(View.GONE);
            View pinCommandsLayout = view.findViewById(R.id.pin_commands_layout);
            pinCommandsLayout.setVisibility(View.GONE);
        }

        View moveBtn = view.findViewById(R.id.btn_move_to_other_screen);
        if (mShowMoveButton) {
            moveBtn.setVisibility(View.VISIBLE);
            moveBtn.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onMoveToOtherScreen();
                }
                dismiss();
            });
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
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                width = (int)(400 * getResources().getDisplayMetrics().density);
            }
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
