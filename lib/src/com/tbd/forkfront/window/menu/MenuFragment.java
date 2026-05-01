package com.tbd.forkfront.window.menu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.tbd.forkfront.R;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import java.util.Set;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class MenuFragment extends Fragment implements AmountSelector.Listener, MenuKeyboardController.ActionDispatcher, MenuGamepadController.ActionDispatcher, MenuFragmentInterface {
	private NHW_Menu mController;
	private View mRoot;
	private ListView mListView;
	private AmountSelector mAmountSelector;
	private View mSelectAllBtn;
	private MenuListNavigator mListNav;
	private MenuKeyboardController mKeyboardCtl;
	private MenuGamepadController mGamepadCtl;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		int wid = getArguments().getInt("wid");
		NetHackViewModel viewModel = new ViewModelProvider(requireActivity()).get(NetHackViewModel.class);
		mController = (NHW_Menu) viewModel.getState().getWindows().get(wid);
		
		mKeyboardCtl = new MenuKeyboardController(this);
		mGamepadCtl = new MenuGamepadController(this);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		MenuSelectMode how = mController.getModel().mHow;
		if (how == MenuSelectMode.PickMany) {
			mRoot = inflater.inflate(R.layout.dialog_menu3, container, false);
		} else {
			mRoot = inflater.inflate(R.layout.dialog_menu1, container, false);
		}
		
		mListView = mRoot.findViewById(R.id.menu_list);
		mSelectAllBtn = mRoot.findViewById(R.id.btn_all);
		View okBtn = mRoot.findViewById(R.id.btn_ok);
		View cancelBtn = mRoot.findViewById(R.id.btn_cancel);
		View closeBtn = mRoot.findViewById(R.id.btn_close);

		if (mSelectAllBtn instanceof Button) {
			mSelectAllBtn.setOnClickListener(v -> {
				if (((Button)mSelectAllBtn).getText().equals("Select all")) {
					dispatchSelectAll();
				} else {
					dispatchClearAll();
				}
			});
		}

		if (okBtn != null) {
			okBtn.setOnClickListener(v -> dispatchOk());
		}
		if (cancelBtn != null) {
			cancelBtn.setOnClickListener(v -> dispatchCancel());
		}
		if (closeBtn != null) {
			closeBtn.setOnClickListener(v -> dispatchCancel());
		}

		if (mListView != null) {
			mListView.setAdapter(new MenuItemAdapter((AppCompatActivity) requireActivity(), R.layout.menu_item, (ArrayList<MenuItem>) mController.getModel().mItems, mController.getModel().getTileset(), mController.getModel().mHow));
			mListView.setOnItemClickListener((parent, view, position, id) -> toggleItemOrGroupAt(position));
			mListView.setOnItemLongClickListener((parent, view, position, id) -> {
				MenuItem item = (MenuItem) mListView.getItemAtPosition(position);
				if (item.isSelectable() && item.getMaxCount() > 1) {
					dispatchShowAmount(item);
					return true;
				}
				return false;
			});

			mListNav = new MenuListNavigator(mListView, mController.getModel(), mController.getSelectionService(), this::toggleItemOrGroupAt);
		}
		
		mRoot.requestFocus();
		updateSelectAllBtn();

		return mRoot;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mRoot = null;
		mListView = null;
		mSelectAllBtn = null;
		mListNav = null;
	}

	public void refresh() {
		if (mListView != null) {
			((MenuItemAdapter) mListView.getAdapter()).notifyDataSetChanged();
			updateSelectAllBtn();
		}
	}

	private void updateSelectAllBtn() {
		if (!(mSelectAllBtn instanceof Button)) return;
		boolean any = false;
		for (MenuItem item : mController.getModel().mItems) {
			if (item.isSelectable() && item.isSelected()) {
				any = true;
				break;
			}
		}
		((Button)mSelectAllBtn).setText(any ? "Clear all" : "Select all");
	}

	private void toggleItemOrGroupAt(int pos) {
		MenuItem item = mController.getModel().mItems.get(pos);
		if (item.isHeader()) {
			char groupAcc = item.getGroupAcc();
			boolean anySelected = false;
			for (MenuItem i : mController.getModel().mItems) {
				if (i.getGroupAcc() == groupAcc && !i.isHeader() && i.isSelected()) {
					anySelected = true;
					break;
				}
			}
			for (MenuItem i : mController.getModel().mItems) {
				if (i.getGroupAcc() == groupAcc && !i.isHeader()) {
					i.setSelected(!anySelected);
					i.setCount(!anySelected ? i.getMaxCount() : 0);
				}
			}
		} else {
			item.setSelected(!item.isSelected());
			item.setCount(item.isSelected() ? item.getMaxCount() : 0);
		}
		refresh();
	}

	@Override
	public void dispatchOk() {
		dispatchSelectChecked();
	}

	@Override
	public void dispatchCancel() {
		mController.getSelectionService().sendCancelSelect();
		mController.close();
	}

	@Override
	public void dispatchSelectAll() {
		for (MenuItem item : mController.getModel().mItems) {
			if (item.isSelectable()) {
				item.setSelected(true);
				item.setCount(item.getMaxCount());
			}
		}
		refresh();
	}

	@Override
	public void dispatchClearAll() {
		for (MenuItem item : mController.getModel().mItems) {
			if (item.isSelectable()) {
				item.setSelected(false);
				item.setCount(0);
			}
		}
		refresh();
	}

	@Override
	public void dispatchToggle(int pos) {
		toggleItemOrGroupAt(pos);
	}

	@Override
	public void dispatchSelectOne(MenuItem item, int count) {
		mController.getSelectionService().sendSelectOne(item, count);
		mController.close();
	}

	@Override
	public void dispatchShowAmount(MenuItem item) {
		mAmountSelector = new AmountSelector(this, (AppCompatActivity) requireActivity(), mController.getModel().getTileset(), item);
	}

	@Override
	public void dispatchSelectChecked() {
		ArrayList<MenuItem> items = new ArrayList<>();
		for (MenuItem item : mController.getModel().mItems) {
			if (item.isSelected()) {
				items.add(item);
			}
		}
		mController.getSelectionService().sendSelectChecked(items);
		mController.close();
	}

	@Override
	public KeyEventResult navigate(int keyCode) {
		if (mListNav == null) return KeyEventResult.IGNORED;
		return mListNav.navigate(keyCode);
	}

	@Override
	public int getAccelerator(char ch) {
		for (int i = 0; i < mController.getModel().mItems.size(); i++) {
			if (mController.getModel().mItems.get(i).getAcc() == ch) return i;
		}
		return -1;
	}

	@Override
	public MenuModel getModel() {
		return mController.getModel();
	}

	@Override
	public boolean isListViewAvailable() {
		return mListView != null;
	}

	@Override
	public boolean isListViewFocused() {
		return mListView != null && mListView.hasFocus();
	}

	@Override
	public int getSelectedItemPosition() {
		return mListView != null ? mListView.getSelectedItemPosition() : -1;
	}

	@Override
	public int getFirstVisiblePosition() {
		return mListView != null ? mListView.getFirstVisiblePosition() : -1;
	}

	@Override
	public MenuItem getSelectedItem() {
		int pos = getSelectedItemPosition();
		if (pos == -1) pos = getFirstVisiblePosition();
		return pos != -1 ? (MenuItem) mListView.getItemAtPosition(pos) : null;
	}

	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
		if (mAmountSelector != null) return mAmountSelector.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
		return mKeyboardCtl.onKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
	}

	public boolean handleGamepadKey(KeyEvent ev) {
		if (mAmountSelector != null) return mAmountSelector.handleGamepadKey(ev);
		return mGamepadCtl.onGamepadKey(ev);
	}

	public boolean handleGamepadMotion(MotionEvent ev) {
		return false;
	}

	@Override
	public void onDismissCount(MenuItem item, int amount) {
		mAmountSelector = null;
		if (amount != -1) {
			item.setCount(amount);
			item.setSelected(amount != 0);
		}
		refresh();
	}
}
