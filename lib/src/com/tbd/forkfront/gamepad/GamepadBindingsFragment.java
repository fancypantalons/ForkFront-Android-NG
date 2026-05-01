package com.tbd.forkfront.gamepad;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.tbd.forkfront.CmdRegistry;
import com.tbd.forkfront.DeviceProfile;
import com.tbd.forkfront.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Fragment for viewing and editing gamepad key bindings.
 * Hosted by the Settings activity via the "Configure bindings..." preference.
 */
public class GamepadBindingsFragment extends Fragment {

    // RecyclerView item types
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BINDING = 1;

    // Live binding list (mutable; saved to prefs on each change)
    private List<KeyBinding> mBindings;
    // Default bindings for reset support
    private List<KeyBinding> mDefaults;
    // Flat list for the RecyclerView (HeaderItem or BindingItem)
    private List<Object> mListItems;
    // Full unfiltered list for search reset
    private List<Object> mAllListItems;

    // Editing state
    private int mEditingIndex = -1;               // index into mBindings being edited; -1 = new
    private CmdRegistry.CmdInfo mPendingCmd;       // command selected for new binding

    private BindingsAdapter mAdapter;
    private SearchView mSearchView;
    private View mEmptyView;

    // --- Lifecycle --------------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gamepad_bindings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() != null) getActivity().setTitle("Configure Bindings");

        mSearchView = view.findViewById(R.id.bindings_search);
        mEmptyView = view.findViewById(R.id.bindings_empty);

        mSearchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showSearchKeyboard();
        });

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mAdapter.filter(query);
                updateEmptyState();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAdapter.filter(newText);
                updateEmptyState();
                return true;
            }
        });

        RecyclerView recycler = view.findViewById(R.id.bindings_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new BindingsAdapter();
        recycler.setAdapter(mAdapter);

        view.findViewById(R.id.btn_add_binding).setOnClickListener(v -> onAddBindingClicked());

        // Register for chord capture results
        getParentFragmentManager().setFragmentResultListener(
            BindingCaptureDialogFragment.REQUEST_KEY,
            getViewLifecycleOwner(),
            (key, bundle) -> {
                int primary = bundle.getInt("primary");
                int[] mods = bundle.getIntArray("modifiers");
                onChordCaptured(primary, mods != null ? mods : new int[0]);
            }
        );

        loadBindings();
        buildListItems();
        mAllListItems = new ArrayList<>(mListItems);
        mAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // --- Data loading / saving --------------------------------------------------

    private void loadBindings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mBindings = KeyBindingStore.load(prefs);
        if (mBindings == null) {
            String deviceKey = DeviceProfile.detect(requireContext());
            mBindings = KeyBindingDefaultsLoader.loadDefaults(requireContext(), deviceKey);
            KeyBindingStore.save(prefs, mBindings, deviceKey != null ? deviceKey : "generic");
        }
        String deviceKey = DeviceProfile.detect(requireContext());
        mDefaults = KeyBindingDefaultsLoader.loadDefaults(requireContext(), deviceKey);
    }

    private void saveAndNotify() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        KeyBindingStore.save(prefs, mBindings, DeviceProfile.detect(requireContext()));
        GamepadDispatcher gd = GamepadDispatcher.getInstance();
        if (gd != null) gd.reloadFromPreferences();
    }

    // --- List building ----------------------------------------------------------

    private void buildListItems() {
        mListItems = new ArrayList<>();

        // Group binding indices by category
        Map<CmdRegistry.Category, List<Integer>> byCat = new LinkedHashMap<>();
        for (CmdRegistry.Category cat : CmdRegistry.Category.values()) {
            byCat.put(cat, new ArrayList<>());
        }
        List<Integer> otherIndices = new ArrayList<>();

        for (int i = 0; i < mBindings.size(); i++) {
            CmdRegistry.Category cat = getCategory(mBindings.get(i));
            if (cat != null) {
                byCat.get(cat).add(i);
            } else {
                otherIndices.add(i);
            }
        }

        for (CmdRegistry.Category cat : CmdRegistry.Category.values()) {
            List<Integer> indices = byCat.get(cat);
            if (!indices.isEmpty()) {
                mListItems.add(new HeaderItem(cat.getDisplayName()));
                for (int idx : indices) {
                    mListItems.add(new BindingItem(idx, mBindings.get(idx),
                        getDefaultBinding(mBindings.get(idx))));
                }
            }
        }

        if (!otherIndices.isEmpty()) {
            mListItems.add(new HeaderItem("Other"));
            for (int idx : otherIndices) {
                mListItems.add(new BindingItem(idx, mBindings.get(idx),
                    getDefaultBinding(mBindings.get(idx))));
            }
        }
    }

    private void rebuildAndRefresh() {
        buildListItems();
        mAllListItems = new ArrayList<>(mListItems);
        String query = mSearchView != null ? mSearchView.getQuery().toString() : "";
        mAdapter.filter(query);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (mEmptyView == null) return;
        boolean isEmpty = mListItems == null || mListItems.isEmpty();
        mEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    // --- Item action handlers ---------------------------------------------------

    private void onEditClicked(int bindingsIndex) {
        KeyBinding kb = mBindings.get(bindingsIndex);
        mEditingIndex = bindingsIndex;
        mPendingCmd = null;
        String label = getDisplayName(kb);
        BindingCaptureDialogFragment dialog =
            BindingCaptureDialogFragment.newInstance(label, buildConflictChecker());
        dialog.show(getParentFragmentManager(), "capture");
    }

    private void onResetClicked(int bindingsIndex) {
        KeyBinding kb = mBindings.get(bindingsIndex);
        KeyBinding def = getDefaultBinding(kb);
        if (def == null) return;
        mBindings.set(bindingsIndex, new KeyBinding(def.chord, kb.target, kb.label, kb.locked, kb.sourceCmdKey));
        rebuildAndRefresh();
        saveAndNotify();
        Toast.makeText(requireContext(), "Reset to default chord", Toast.LENGTH_SHORT).show();
    }

    private void onClearClicked(int bindingsIndex) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Remove Binding")
            .setMessage("Remove this binding?")
            .setPositiveButton("Remove", (d, w) -> {
                mBindings.remove(bindingsIndex);
                rebuildAndRefresh();
                saveAndNotify();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void onAddBindingClicked() {
        com.tbd.forkfront.CommandPickerDialogFragment dialog =
            com.tbd.forkfront.CommandPickerDialogFragment.newInstance();
        dialog.setOnCommandSelectedListener(cmd -> {
            mPendingCmd = cmd;
            mEditingIndex = -1;
            BindingCaptureDialogFragment capture =
                BindingCaptureDialogFragment.newInstance(
                    cmd.getDisplayName(), buildConflictChecker());
            capture.show(getParentFragmentManager(), "capture");
        });
        dialog.show(getParentFragmentManager(), "command_picker");
    }

    private BindingCaptureDialogFragment.ConflictChecker buildConflictChecker() {
        return chord -> {
            int idx = findChordConflict(chord, mEditingIndex);
            if (idx < 0) return null;
            return getDisplayName(mBindings.get(idx));
        };
    }

    private void onChordCaptured(int primaryCode, int[] modCodes) {
        SortedSet<ButtonId> all = new TreeSet<>();
        ButtonId primary = new ButtonId(primaryCode);
        all.add(primary);
        for (int code : modCodes) all.add(new ButtonId(code));
        Chord chord;
        try {
            chord = new Chord(all, primary);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Invalid chord", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for chord conflict
        int conflictIdx = findChordConflict(chord, mEditingIndex);
        if (conflictIdx >= 0) {
            KeyBinding existing = mBindings.get(conflictIdx);
            String existingName = getDisplayName(existing);
            new AlertDialog.Builder(requireContext())
                .setTitle("Chord Conflict")
                .setMessage("\"" + chord.displayName() + "\" is already bound to \""
                    + existingName + "\". Replace it?")
                .setPositiveButton("Replace", (d, w) -> applyChord(chord, conflictIdx))
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            applyChord(chord, -1);
        }
    }

    private void applyChord(Chord chord, int conflictIdx) {
        // Remove conflicting binding first (if any)
        if (conflictIdx >= 0 && conflictIdx != mEditingIndex) {
            mBindings.remove(conflictIdx);
            // Adjust editing index if needed
            if (mEditingIndex > conflictIdx) mEditingIndex--;
        }

        if (mEditingIndex >= 0 && mEditingIndex < mBindings.size()) {
            // Edit existing
            KeyBinding old = mBindings.get(mEditingIndex);
            mBindings.set(mEditingIndex,
                new KeyBinding(chord, old.target, old.label, old.locked, old.sourceCmdKey));
        } else if (mPendingCmd != null) {
            // New binding from command picker
            BindingTarget target = BindingTarget.fromCmdKey(mPendingCmd.getCommand());
            if (target != null) {
                mBindings.add(new KeyBinding(chord, target, null, false, mPendingCmd.getCommand()));
            }
        }

        mEditingIndex = -1;
        mPendingCmd = null;
        rebuildAndRefresh();
        saveAndNotify();
    }

    // --- Helpers ----------------------------------------------------------------

    private CmdRegistry.Category getCategory(KeyBinding kb) {
        if (kb.sourceCmdKey == null) return null;
        CmdRegistry.CmdInfo info = CmdRegistry.get(kb.sourceCmdKey);
        return info != null ? info.getCategory() : null;
    }

    private KeyBinding getDefaultBinding(KeyBinding kb) {
        if (kb.sourceCmdKey == null || mDefaults == null) return null;
        for (KeyBinding def : mDefaults) {
            if (kb.sourceCmdKey.equals(def.sourceCmdKey)) return def;
        }
        return null;
    }

    private boolean isModified(KeyBinding kb) {
        KeyBinding def = getDefaultBinding(kb);
        if (def == null) return false;
        return !kb.chord.equals(def.chord);
    }

    private String getDisplayName(KeyBinding kb) {
        if (kb.label != null) return kb.label;
        if (kb.sourceCmdKey != null) {
            CmdRegistry.CmdInfo info = CmdRegistry.get(kb.sourceCmdKey);
            if (info != null) return info.getDisplayName();
            if (kb.target instanceof BindingTarget.UiAction) {
                return formatUiActionName(((BindingTarget.UiAction) kb.target).id);
            }
            if ("ESC".equals(kb.sourceCmdKey)) return "Cancel / ESC";
            return kb.sourceCmdKey;
        }
        if (kb.target instanceof BindingTarget.NhKey) {
            char ch = ((BindingTarget.NhKey) kb.target).ch;
            if (ch == '\033') return "Cancel / ESC";
        }
        return "(no command)";
    }

    private String formatUiActionName(UiActionId id) {
        switch (id) {
            case OPEN_DRAWER:          return "Open Drawer";
            case OPEN_COMMAND_PALETTE: return "Command Palette";
            case OPEN_SETTINGS:        return "Open Settings";
            case TOGGLE_KEYBOARD:      return "Toggle Keyboard";
            case ZOOM_IN:              return "Zoom In";
            case ZOOM_OUT:             return "Zoom Out";
            case TOGGLE_MAP_LOCK:      return "Toggle Map Lock";
            case RECENTER_MAP:         return "Recenter Map";
            case RESEND_LAST_CMD:      return "Resend Last Command";
            default:                   return id.name();
        }
    }

    /** Returns the index of a binding whose chord equals {@code chord}, ignoring {@code skipIdx}. */
    private int findChordConflict(Chord chord, int skipIdx) {
        for (int i = 0; i < mBindings.size(); i++) {
            if (i == skipIdx) continue;
            if (chord.equals(mBindings.get(i).chord)) return i;
        }
        return -1;
    }

    private void showSearchKeyboard() {
        android.util.Log.d("NH_IME", "showSearchKeyboard called");
        View edit = mSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        android.util.Log.d("NH_IME", "edit view=" + edit + " hasFocus=" + (edit != null ? edit.hasFocus() : "null"));
        if (edit != null) {
            boolean focused = edit.requestFocus();
            android.util.Log.d("NH_IME", "requestFocus returned: " + focused);
            com.tbd.forkfront.Util.showKeyboard(requireContext(), edit);
        }
    }

    // --- Data classes -----------------------------------------------------------

    private static class HeaderItem {
        final String title;
        HeaderItem(String t) { title = t; }
    }

    private static class BindingItem {
        final int bindingsIndex;
        final KeyBinding binding;
        @Nullable final KeyBinding defaultBinding;

        BindingItem(int idx, KeyBinding kb, @Nullable KeyBinding def) {
            bindingsIndex = idx;
            binding = kb;
            defaultBinding = def;
        }
    }

    // --- RecyclerView Adapter ---------------------------------------------------

    private class BindingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return (mListItems.get(position) instanceof HeaderItem) ? TYPE_HEADER : TYPE_BINDING;
        }

        @Override
        public int getItemCount() {
            return mListItems != null ? mListItems.size() : 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View v = inf.inflate(R.layout.item_gamepad_binding_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = inf.inflate(R.layout.item_gamepad_binding, parent, false);
                return new BindingViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                HeaderItem item = (HeaderItem) mListItems.get(position);
                ((HeaderViewHolder) holder).title.setText(item.title);
            } else {
                BindingItem item = (BindingItem) mListItems.get(position);
                ((BindingViewHolder) holder).bind(item);
            }
        }

        public void filter(String query) {
            if (query == null || query.trim().isEmpty()) {
                mListItems = new ArrayList<>(mAllListItems);
                notifyDataSetChanged();
                return;
            }
            String lower = query.toLowerCase(Locale.getDefault());
            List<Object> filtered = new ArrayList<>();
            String lastHeader = null;
            for (Object obj : mAllListItems) {
                if (obj instanceof HeaderItem) {
                    lastHeader = ((HeaderItem) obj).title;
                } else if (obj instanceof BindingItem) {
                    BindingItem bi = (BindingItem) obj;
                    String displayName = getDisplayName(bi.binding).toLowerCase(Locale.getDefault());
                    String cmdKey = bi.binding.sourceCmdKey != null
                        ? bi.binding.sourceCmdKey.toLowerCase(Locale.getDefault()) : "";
                    String chordName = bi.binding.chord.displayName().toLowerCase(Locale.getDefault());
                    if (displayName.contains(lower) || cmdKey.contains(lower) || chordName.contains(lower)) {
                        // Add header if not already added for this section
                        if (lastHeader != null && (filtered.isEmpty() ||
                            !(filtered.get(filtered.size() - 1) instanceof HeaderItem) ||
                            !((HeaderItem) filtered.get(filtered.size() - 1)).title.equals(lastHeader))) {
                            filtered.add(new HeaderItem(lastHeader));
                        }
                        filtered.add(bi);
                    }
                }
            }
            mListItems = filtered;
            notifyDataSetChanged();
        }
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderViewHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.header_title);
        }
    }

    private class BindingViewHolder extends RecyclerView.ViewHolder {
        final TextView cmdName;
        final View lockedBadge;
        final ChipGroup chipGroup;
        final View editBtn;
        final View resetBtn;
        final View clearBtn;
        final View actionsRow;

        BindingViewHolder(@NonNull View v) {
            super(v);
            cmdName = v.findViewById(R.id.binding_cmd_name);
            lockedBadge = v.findViewById(R.id.binding_locked_badge);
            chipGroup = v.findViewById(R.id.binding_chips);
            editBtn = v.findViewById(R.id.binding_edit_btn);
            resetBtn = v.findViewById(R.id.binding_reset_btn);
            clearBtn = v.findViewById(R.id.binding_clear_btn);
            actionsRow = v.findViewById(R.id.binding_actions);
        }

        void bind(BindingItem item) {
            KeyBinding kb = item.binding;
            int idx = item.bindingsIndex;

            cmdName.setText(getDisplayName(kb));

            // Modified indicator: italicize if differs from default
            if (isModified(kb)) {
                cmdName.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                // Use italic to indicate modified; Material body large doesn't have italic by default
                // so we set it explicitly
                cmdName.setTypeface(null, android.graphics.Typeface.ITALIC);
            } else {
                cmdName.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            // Render chord as chips
            chipGroup.removeAllViews();
            if (kb.chord != null) {
                SortedSet<ButtonId> modifiers = kb.chord.modifiers();
                for (ButtonId mod : modifiers) {
                    Chip chip = createChip(chipGroup, mod.displayName(), false);
                    chipGroup.addView(chip);
                }
                Chip primaryChip = createChip(chipGroup, kb.chord.primary.displayName(), true);
                chipGroup.addView(primaryChip);
            }

            if (kb.locked) {
                lockedBadge.setVisibility(View.VISIBLE);
                actionsRow.setVisibility(View.GONE);
                itemView.setAlpha(0.6f);
            } else {
                lockedBadge.setVisibility(View.GONE);
                actionsRow.setVisibility(View.VISIBLE);
                itemView.setAlpha(1.0f);
                editBtn.setOnClickListener(vv -> onEditClicked(idx));
                resetBtn.setVisibility(item.defaultBinding != null ? View.VISIBLE : View.GONE);
                if (item.defaultBinding != null) {
                    resetBtn.setOnClickListener(vv -> onResetClicked(idx));
                }
                clearBtn.setOnClickListener(vv -> onClearClicked(idx));
            }
        }

        private Chip createChip(ViewGroup parent, String label, boolean isPrimary) {
            Chip chip = new Chip(parent.getContext());
            chip.setText(label);
            chip.setClickable(false);
            chip.setFocusable(false);
            chip.setCheckable(false);
            if (isPrimary) {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceContainerHighest)));
                chip.setTextColor(com.google.android.material.color.MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface));
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOutline)));
                chip.setChipStrokeWidth(1.5f);
            } else {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceContainer)));
                chip.setTextColor(com.google.android.material.color.MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant));
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOutlineVariant)));
                chip.setChipStrokeWidth(1f);
            }
            return chip;
        }
    }
}
