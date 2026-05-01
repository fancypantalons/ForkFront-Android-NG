package com.tbd.forkfront.window.menu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
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

public class MenuTextFragment extends Fragment {
	private NHW_Menu mController;
	private View mRoot;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		int wid = getArguments().getInt("wid");
		NetHackViewModel viewModel = new ViewModelProvider(requireActivity()).get(NetHackViewModel.class);
		mController = (NHW_Menu) viewModel.getState().getWindows().get(wid);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		mRoot = inflater.inflate(R.layout.dialog_text, container, false);
		
		View okBtn = mRoot.findViewById(R.id.btn_ok);
		View closeBtn = mRoot.findViewById(R.id.btn_close);

		if (okBtn != null) {
			okBtn.setOnClickListener(v -> mController.close());
		}
		if (closeBtn != null) {
			closeBtn.setOnClickListener(v -> mController.close());
		}

		refresh();
		mRoot.requestFocus();

		return mRoot;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mRoot = null;
	}

	public void refresh() {
		if (mRoot != null) {
			NH_TextView tv = mRoot.findViewById(R.id.text_view);
			if (tv != null && mController.mBuilder != null) {
				tv.setText(mController.mBuilder);
			}
		}
	}

	private KeyEventResult navigateScroll(int keyCode) {
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
				mController.close();
				break;
			case KeyEvent.KEYCODE_MOVE_HOME:
				sv.fullScroll(View.FOCUS_UP);
				break;
			case KeyEvent.KEYCODE_MOVE_END:
				sv.fullScroll(View.FOCUS_DOWN);
				break;
			case KeyEvent.KEYCODE_PAGE_UP:
				sv.pageScroll(View.FOCUS_UP);
				break;
			case KeyEvent.KEYCODE_PAGE_DOWN:
				sv.pageScroll(View.FOCUS_DOWN);
				break;
			default:
				return KeyEventResult.IGNORED;
		}
		return KeyEventResult.HANDLED;
	}

	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
		if (ch == '<') {
			keyCode = KeyEvent.KEYCODE_PAGE_UP;
		} else if (ch == '>') {
			keyCode = KeyEvent.KEYCODE_PAGE_DOWN;
		}

		if (keyCode < 0) {
			return KeyEventResult.IGNORED;
		}

		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_CENTER:
				return navigateScroll(keyCode);

			case KeyEvent.KEYCODE_ESCAPE:
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_BACK:
				mController.close();
				break;

			case KeyEvent.KEYCODE_SPACE:
			case KeyEvent.KEYCODE_PAGE_DOWN:
			case KeyEvent.KEYCODE_PAGE_UP:
				return navigateScroll(keyCode);

			default:
				return KeyEventResult.RETURN_TO_SYSTEM;
		}
		return KeyEventResult.HANDLED;
	}

	public boolean handleGamepadKey(KeyEvent ev) {
		if (ev.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}

		switch (ev.getKeyCode()) {
			case KeyEvent.KEYCODE_BUTTON_A:
			case KeyEvent.KEYCODE_BUTTON_B:
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				mController.close();
				return true;

			case KeyEvent.KEYCODE_BUTTON_L1:
				return navigateScroll(KeyEvent.KEYCODE_DPAD_LEFT) == KeyEventResult.HANDLED;

			case KeyEvent.KEYCODE_BUTTON_R1:
				return navigateScroll(KeyEvent.KEYCODE_DPAD_RIGHT) == KeyEventResult.HANDLED;

			case KeyEvent.KEYCODE_BUTTON_L2:
				return navigateScroll(KeyEvent.KEYCODE_MOVE_HOME) == KeyEventResult.HANDLED;

			case KeyEvent.KEYCODE_BUTTON_R2:
				return navigateScroll(KeyEvent.KEYCODE_MOVE_END) == KeyEventResult.HANDLED;
		}
		return false;
	}

	public boolean handleGamepadMotion(MotionEvent ev) {
		return false;
	}
}