package com.tbd.forkfront.window.menu;

import android.view.KeyEvent;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import java.util.Set;

public class MenuKeyboardController implements MenuInputController {
	public interface ActionDispatcher {
		void dispatchOk();
		void dispatchCancel();
		void dispatchSelectAll();
		void dispatchClearAll();
		void dispatchToggle(int pos);
		void dispatchSelectOne(MenuItem item, int count);
		KeyEventResult navigate(int keyCode);
		int getAccelerator(char ch);
		MenuModel getModel();
	}

	private final ActionDispatcher mDispatcher;

	public MenuKeyboardController(ActionDispatcher dispatcher) {
		mDispatcher = dispatcher;
	}

	@Override
	public KeyEventResult onKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
		MenuModel model = mDispatcher.getModel();
		
		if (ch == '<') {
			keyCode = KeyEvent.KEYCODE_PAGE_UP;
		} else if (ch == '>') {
			keyCode = KeyEvent.KEYCODE_PAGE_DOWN;
		}

		if (keyCode < 0) {
			return KeyEventResult.IGNORED;
		}

		if (model.mType == MenuModel.Type.Text) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_DOWN:
				case KeyEvent.KEYCODE_DPAD_LEFT:
				case KeyEvent.KEYCODE_DPAD_RIGHT:
				case KeyEvent.KEYCODE_DPAD_CENTER:
					return mDispatcher.navigate(keyCode);

				case KeyEvent.KEYCODE_ESCAPE:
				case KeyEvent.KEYCODE_ENTER:
				case KeyEvent.KEYCODE_BACK:
					mDispatcher.dispatchCancel();
					break;

				case KeyEvent.KEYCODE_SPACE:
				case KeyEvent.KEYCODE_PAGE_DOWN:
				case KeyEvent.KEYCODE_PAGE_UP:
					return mDispatcher.navigate(keyCode);

				default:
					return KeyEventResult.RETURN_TO_SYSTEM;
			}
			return KeyEventResult.HANDLED;
		}

		if (model.mType == MenuModel.Type.Menu) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_DOWN:
				case KeyEvent.KEYCODE_DPAD_LEFT:
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					return KeyEventResult.RETURN_TO_SYSTEM;

				case KeyEvent.KEYCODE_DPAD_CENTER:
					return mDispatcher.navigate(keyCode);

				case KeyEvent.KEYCODE_ESCAPE:
				case KeyEvent.KEYCODE_BACK:
					mDispatcher.dispatchCancel();
					break;

				case KeyEvent.KEYCODE_PAGE_DOWN:
				case KeyEvent.KEYCODE_PAGE_UP:
					return mDispatcher.navigate(keyCode);

				case KeyEvent.KEYCODE_ENTER:
				case KeyEvent.KEYCODE_SPACE:
					return KeyEventResult.RETURN_TO_SYSTEM;

				default:
					if (model.mHow == MenuSelectMode.PickNone) {
						if (mDispatcher.getAccelerator(ch) >= 0) {
							mDispatcher.dispatchOk();
							return KeyEventResult.HANDLED;
						}
						return KeyEventResult.RETURN_TO_SYSTEM;
					} else if (ch >= '0' && ch <= '9') {
						if (model.mKeyboardCount < 0) {
							model.mKeyboardCount = 0;
						}
						model.mKeyboardCount = model.mKeyboardCount * 10 + ch - '0';
						return KeyEventResult.HANDLED;
					} else if (menuSelect(ch)) {
						return KeyEventResult.HANDLED;
					} else if (model.mHow == MenuSelectMode.PickMany) {
						if (ch == '.' || keyCode == KeyEvent.KEYCODE_PERIOD) {
							mDispatcher.dispatchSelectAll();
							return KeyEventResult.HANDLED;
						} else if (ch == '-' || keyCode == KeyEvent.KEYCODE_MINUS) {
							mDispatcher.dispatchClearAll();
							return KeyEventResult.HANDLED;
						}
					}
					return KeyEventResult.RETURN_TO_SYSTEM;
			}
			return KeyEventResult.HANDLED;
		}
		return KeyEventResult.IGNORED;
	}

	private boolean menuSelect(char ch) {
		MenuModel model = mDispatcher.getModel();
		if (model.mHow == MenuSelectMode.PickNone) {
			return false;
		}

		int pos = mDispatcher.getAccelerator(ch);
		if (pos >= 0) {
			MenuItem item = model.mItems.get(pos);
			if (!item.isHeader() && item.isSelectable()) {
				if (model.mHow == MenuSelectMode.PickOne) {
					mDispatcher.dispatchSelectOne(item, model.mKeyboardCount);
				} else {
					mDispatcher.dispatchToggle(pos);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onGamepadKey(KeyEvent ev) {
		return false;
	}
}
