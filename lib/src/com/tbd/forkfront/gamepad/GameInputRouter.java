package com.tbd.forkfront.gamepad;

import android.view.KeyEvent;
import android.view.MotionEvent;

import com.tbd.forkfront.*;

import java.util.Set;
import java.util.function.Supplier;

import androidx.annotation.MainThread;

/**
 * Routes keyboard and gamepad input to the appropriate UI components.
 */
public final class GameInputRouter {
    private final WindowRegistry mWindows;
    private final Supplier<NHW_Message> mMessage;
    private final Supplier<NHW_Map> mMap;
    private final NH_GetLine mGetLine;
    private final NH_CharacterPicker mCharacterPicker;
    private final NH_Question mQuestion;
    private final EngineCommands mCommands;
    private final GamepadContextBridge mCtxBridge;

    public GameInputRouter(
            WindowRegistry windows,
            Supplier<NHW_Message> message,
            Supplier<NHW_Map> map,
            NH_GetLine getLine,
            NH_CharacterPicker picker,
            NH_Question question,
            EngineCommands commands,
            GamepadContextBridge ctxBridge) {
        mWindows = windows;
        mMessage = message;
        mMap = map;
        mGetLine = getLine;
        mCharacterPicker = picker;
        mQuestion = question;
        mCommands = commands;
        mCtxBridge = ctxBridge;
    }

    // Single UiCapture that routes to whichever in-game window is currently active.
    private final UiCapture mGameUiCapture = new UiCapture() {
        @Override
        public boolean handleGamepadKey(KeyEvent ev) {
            if (mCtxBridge.current() == UiContext.CHARACTER_PICKER) {
                return mCharacterPicker.handleGamepadKey(ev.getKeyCode(), ev);
            }
            if(mGetLine != null && mGetLine.isFocused()) {
                if(mGetLine.handleGamepadKey(ev)) return true;
                if(ev.getAction() == KeyEvent.ACTION_DOWN) {
                    KeyEventResult res = mGetLine.handleKeyDown('\0', 0, ev.getKeyCode(), null, ev.getRepeatCount());
                    if (res == KeyEventResult.RETURN_TO_SYSTEM) return false;
                    return res == KeyEventResult.HANDLED;
                }
                return false;
            }
            if(mQuestion != null && mQuestion.isShowing()) {
                if(mQuestion.handleGamepadKey(ev)) return true;
                if(ev.getAction() == KeyEvent.ACTION_DOWN) {
                    KeyEventResult res = mQuestion.handleKeyDown('\0', 0, ev.getKeyCode(), null, ev.getRepeatCount());
                    if (res == KeyEventResult.RETURN_TO_SYSTEM) return false;
                    return res == KeyEventResult.HANDLED;
                }
                return false;
            }

            NH_Window top = mWindows.topVisibleNonSystem();
            if(top != null) {
                if(top.handleGamepadKey(ev)) return true;
                if(ev.getAction() == KeyEvent.ACTION_DOWN)
                    return top.handleKeyDown('\0', 0, ev.getKeyCode(), null, ev.getRepeatCount()) == KeyEventResult.HANDLED;
            }

            NHW_Map map = mMap.get();
            if(mCommands.isMouseLocked() && map != null) {
                if(map.handleGamepadKey(ev)) return true;
            }
            
            NHW_Message message = mMessage.get();
            if(message != null && message.isMoreVisible()) {
                if(message.handleGamepadKey(ev)) return true;
                if(ev.getAction() == KeyEvent.ACTION_DOWN)
                    return message.handleKeyDown(' ', 0, KeyEvent.KEYCODE_SPACE, null, 0) == KeyEventResult.HANDLED;
            }

            return false;
        }

        @Override
        public boolean handleGamepadMotion(MotionEvent ev) {
            if(mGetLine != null && mGetLine.isFocused()) return mGetLine.handleGamepadMotion(ev);
            if(mQuestion != null && mQuestion.isShowing()) return mQuestion.handleGamepadMotion(ev);

            NH_Window top = mWindows.topVisibleNonSystem();
            if(top instanceof NHW_Menu) return ((NHW_Menu)top).handleGamepadMotion(ev);
            if(top instanceof NHW_Text) return ((NHW_Text)top).handleGamepadMotion(ev);

            NHW_Map map = mMap.get();
            if(mCommands.isMouseLocked() && map != null) return map.handleGamepadMotion(ev);
            
            NHW_Message message = mMessage.get();
            if(message != null && message.isMoreVisible()) return message.handleGamepadMotion(ev);

            return false;
        }
    };

    public UiCapture asUiCapture() {
        return mGameUiCapture;
    }

    @MainThread
    public boolean handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
        if (mCtxBridge.current() == UiContext.CHARACTER_PICKER) {
            if (mCharacterPicker.handleKeyDown(keyCode, null)) return true;
        }

        if(repeatCount > 0) switch(keyCode) {
            case KeyEvent.KEYCODE_ESCAPE:
                // Ignore repeat on these actions
                return true;
        }

        KeyEventResult ret = mGetLine.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);

        if(ret == KeyEventResult.IGNORED)
            ret = mQuestion.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);

        for(int i = mWindows.size() - 1; ret == KeyEventResult.IGNORED && i >= 0; i--)
        {
            NH_Window w = mWindows.getAt(i);
            ret = w.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
        }

        if(ret == KeyEventResult.HANDLED)
            return true;
        if(ret == KeyEventResult.RETURN_TO_SYSTEM)
            return false;

        if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)
        {
            if(mCommands.expectsDirection())
                return mCommands.sendKeyCmd('\033');
        }
        if(DEBUG.runTrace() && keyCode == KeyEvent.KEYCODE_BACK)
            android.os.Debug.stopMethodTracing();
        return mCommands.sendKeyCmd(nhKey);
    }
}
