package com.tbd.forkfront.window.menu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.tbd.forkfront.R;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.window.text.NH_TextView;
import java.util.Set;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class MenuTextFragment extends Fragment implements MenuKeyboardController.ActionDispatcher, MenuGamepadController.ActionDispatcher, MenuFragmentInterface {
	private NHW_Menu mController;
	private View mRoot;
	private MenuScrollNavigator mScrollNav;
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
		mRoot = inflater.inflate(R.layout.dialog_text, container, false);
		
		mScrollNav = new MenuScrollNavigator(mRoot, () -> mController.close());
		
		refresh();
		mRoot.requestFocus();

		return mRoot;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mRoot = null;
		mScrollNav = null;
	}

	public void refresh() {
		if (mRoot != null) {
			NH_TextView tv = mRoot.findViewById(R.id.text_view);
			if (tv != null && mController.getModel().mBuilder != null) {
				tv.setText(mController.getModel().mBuilder);
			}
		}
	}

	@Override
	public void dispatchOk() {
		mController.close();
	}

	@Override
	public void dispatchCancel() {
		mController.close();
	}

	@Override
	public void dispatchSelectAll() {}

	@Override
	public void dispatchClearAll() {}

	@Override
	public void dispatchToggle(int pos) {}

	@Override
	public void dispatchSelectOne(MenuItem item, int count) {}

	@Override
	public void dispatchShowAmount(MenuItem item) {}

	@Override
	public void dispatchSelectChecked() {}

	@Override
	public KeyEventResult navigate(int keyCode) {
		return mScrollNav.navigate(keyCode);
	}

	@Override
	public int getAccelerator(char ch) {
		return -1;
	}

	@Override
	public MenuModel getModel() {
		return mController.getModel();
	}

	@Override
	public boolean isListViewAvailable() {
		return false;
	}

	@Override
	public boolean isListViewFocused() {
		return false;
	}

	@Override
	public int getSelectedItemPosition() {
		return -1;
	}

	@Override
	public int getFirstVisiblePosition() {
		return -1;
	}

	@Override
	public MenuItem getSelectedItem() {
		return null;
	}

	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
		return mKeyboardCtl.onKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
	}

	public boolean handleGamepadKey(KeyEvent ev) {
		return mGamepadCtl.onGamepadKey(ev);
	}

	public boolean handleGamepadMotion(MotionEvent ev) {
		return false;
	}
}
