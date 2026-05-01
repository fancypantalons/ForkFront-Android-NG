package com.tbd.forkfront.window.menu;

import android.view.KeyEvent;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import java.util.Set;

public class MenuGamepadController implements MenuInputController {
	public interface ActionDispatcher {
		void dispatchCancel();
		void dispatchSelectAll();
		void dispatchToggle(int pos);
		void dispatchSelectOne(MenuItem item, int count);
		void dispatchShowAmount(MenuItem item);
		void dispatchSelectChecked();
		KeyEventResult navigate(int keyCode);
		MenuModel getModel();
		boolean isListViewAvailable();
		boolean isListViewFocused();
		int getSelectedItemPosition();
		int getFirstVisiblePosition();
		MenuItem getSelectedItem();
	}

	private final ActionDispatcher mDispatcher;

	public MenuGamepadController(ActionDispatcher dispatcher) {
		mDispatcher = dispatcher;
	}

	@Override
	public KeyEventResult onKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
		return KeyEventResult.IGNORED;
	}

	@Override
	public boolean onGamepadKey(KeyEvent ev) {
		if (ev.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}

		MenuModel model = mDispatcher.getModel();

		switch (ev.getKeyCode()) {
			case KeyEvent.KEYCODE_BUTTON_A:
				if (model.getType() == MenuModel.Type.Text) {
					mDispatcher.dispatchCancel();
					return true;
				}
				if (!mDispatcher.isListViewAvailable() || !mDispatcher.isListViewFocused()) {
					return false;
				}
				return handleA();

			case KeyEvent.KEYCODE_BUTTON_B:
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				if (model.getType() == MenuModel.Type.Text) {
					mDispatcher.dispatchCancel();
					return true;
				}
				model.mKeyboardCount = -1;
				mDispatcher.dispatchCancel();
				return true;

			case KeyEvent.KEYCODE_BUTTON_X:
				if (model.getType() == MenuModel.Type.Menu && model.mHow != MenuSelectMode.PickNone) {
					int pos = mDispatcher.getSelectedItemPosition();
					if (pos == -1) {
						pos = mDispatcher.getFirstVisiblePosition();
					}
					if (pos != -1 && pos < model.mItems.size()) {
						MenuItem item = model.mItems.get(pos);
						if (item.isSelectable() && item.getMaxCount() > 1) {
							mDispatcher.dispatchShowAmount(item);
							return true;
						}
					}
					if (model.mHow == MenuSelectMode.PickMany) {
						mDispatcher.dispatchSelectAll();
					} else {
						model.mKeyboardCount = -1;
					}
					return true;
				}
				break;

			case KeyEvent.KEYCODE_BUTTON_L1:
				if (model.getType() == MenuModel.Type.Menu && model.mHow == MenuSelectMode.PickMany) {
					mDispatcher.dispatchSelectChecked();
					return true;
				}
				return mDispatcher.navigate(model.getType() == MenuModel.Type.Text ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_PAGE_UP) == KeyEventResult.HANDLED;

			case KeyEvent.KEYCODE_BUTTON_R1:
				return mDispatcher.navigate(model.getType() == MenuModel.Type.Text ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_PAGE_DOWN) == KeyEventResult.HANDLED;

			case KeyEvent.KEYCODE_BUTTON_L2:
				if (model.getType() == MenuModel.Type.Text) {
					return mDispatcher.navigate(KeyEvent.KEYCODE_MOVE_HOME) == KeyEventResult.HANDLED;
				}
				return mDispatcher.navigate(KeyEvent.KEYCODE_MOVE_HOME) == KeyEventResult.HANDLED;

			case KeyEvent.KEYCODE_BUTTON_R2:
				if (model.getType() == MenuModel.Type.Text) {
					return mDispatcher.navigate(KeyEvent.KEYCODE_MOVE_END) == KeyEventResult.HANDLED;
				}
				return mDispatcher.navigate(KeyEvent.KEYCODE_MOVE_END) == KeyEventResult.HANDLED;
		}
		return false;
	}

	private boolean handleA() {
		MenuModel model = mDispatcher.getModel();
		int pos = mDispatcher.getSelectedItemPosition();
		if (pos == -1) {
			pos = mDispatcher.getFirstVisiblePosition();
		}
		if (pos != -1) {
			MenuItem item = mDispatcher.getSelectedItem();
			if (model.mHow == MenuSelectMode.PickOne) {
				mDispatcher.dispatchSelectOne(item, model.mKeyboardCount);
			} else {
				mDispatcher.dispatchToggle(pos);
			}
			return true;
		}
		return false;
	}
}
