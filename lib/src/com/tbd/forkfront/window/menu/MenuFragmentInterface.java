package com.tbd.forkfront.window.menu;

import android.view.KeyEvent;
import android.view.MotionEvent;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import java.util.Set;

public interface MenuFragmentInterface {
	void refresh();
	KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount);
	boolean handleGamepadKey(KeyEvent ev);
	boolean handleGamepadMotion(MotionEvent ev);
}
