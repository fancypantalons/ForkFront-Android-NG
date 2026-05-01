package com.tbd.forkfront.window.menu;

import android.view.KeyEvent;
import android.widget.ListView;
import com.tbd.forkfront.input.KeyEventResult;

public class MenuListNavigator {
	private final ListView mListView;
	private final MenuModel mModel;
	private final MenuSelectionService mSelectionService;
	private final ToggleAtPosition mToggleAction;

	public interface ToggleAtPosition {
		void toggle(int pos);
	}

	public MenuListNavigator(ListView listView, MenuModel model, MenuSelectionService selectionService, ToggleAtPosition toggleAction) {
		mListView = listView;
		mModel = model;
		mSelectionService = selectionService;
		mToggleAction = toggleAction;
	}

	public KeyEventResult navigate(int keyCode) {
		if (mListView == null) {
			return KeyEventResult.IGNORED;
		}

		if (!mListView.hasFocus()) {
			mListView.requestFocus();
		}

		int pos = mListView.getSelectedItemPosition();
		boolean wasInvalid = (pos == ListView.INVALID_POSITION);
		if (wasInvalid) {
			pos = (keyCode == KeyEvent.KEYCODE_DPAD_UP) ? mListView.getCount() - 1 : mListView.getFirstVisiblePosition();
		}

		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:
				if (!wasInvalid) {
					pos--;
				}
				while (pos >= 0 && !mListView.getAdapter().isEnabled(pos)) {
					pos--;
				}
				if (pos >= 0) {
					mListView.setSelection(pos);
				}
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				if (!wasInvalid) {
					pos++;
				}
				while (pos < mListView.getCount() && !mListView.getAdapter().isEnabled(pos)) {
					pos++;
				}
				if (pos < mListView.getCount()) {
					mListView.setSelection(pos);
				}
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (pos != ListView.INVALID_POSITION) {
					MenuItem item = (MenuItem) mListView.getItemAtPosition(pos);
					if (mModel.mHow == MenuSelectMode.PickOne) {
						mSelectionService.sendSelectOne(item, mModel.mKeyboardCount);
					} else {
						if (mToggleAction != null) {
							mToggleAction.toggle(pos);
						}
					}
				}
				break;
			case KeyEvent.KEYCODE_PAGE_UP:
				mListView.smoothScrollBy(-mListView.getHeight() * 3 / 4, 100);
				break;
			case KeyEvent.KEYCODE_PAGE_DOWN:
				mListView.smoothScrollBy(mListView.getHeight() * 3 / 4, 100);
				break;
			default:
				return KeyEventResult.IGNORED;
		}
		return KeyEventResult.HANDLED;
	}
}
