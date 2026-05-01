package com.tbd.forkfront.window.menu;

import android.view.KeyEvent;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import java.util.Set;

public interface MenuInputController {
	KeyEventResult onKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount);
	boolean onGamepadKey(KeyEvent ev);
}
