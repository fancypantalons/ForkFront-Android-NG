package com.tbd.forkfront.window.menu;

import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;
import com.tbd.forkfront.input.KeyEventResult;

public class MenuScrollNavigator {
	private final View mRoot;
	private final Runnable mCloseAction;

	public MenuScrollNavigator(View root, Runnable closeAction) {
		mRoot = root;
		mCloseAction = closeAction;
	}

	public KeyEventResult navigate(int keyCode) {
		ScrollView sv = mRoot.findViewById(com.tbd.forkfront.R.id.scrollview);
		if (sv == null) {
			return KeyEventResult.IGNORED;
		}

		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:
				sv.smoothScrollBy(0, -sv.getHeight() / 4);
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				sv.smoothScrollBy(0, sv.getHeight() / 4);
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				sv.pageScroll(View.FOCUS_UP);
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				sv.pageScroll(View.FOCUS_DOWN);
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (mCloseAction != null) {
					mCloseAction.run();
				}
				break;
			case KeyEvent.KEYCODE_MOVE_HOME:
				sv.fullScroll(View.FOCUS_UP);
				break;
			case KeyEvent.KEYCODE_MOVE_END:
				sv.fullScroll(View.FOCUS_DOWN);
				break;
			default:
				return KeyEventResult.IGNORED;
		}
		return KeyEventResult.HANDLED;
	}
}
