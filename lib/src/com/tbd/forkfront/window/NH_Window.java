package com.tbd.forkfront.window;

import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;

import java.util.Set;

import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;

import androidx.annotation.MainThread;

public interface NH_Window
{
	@MainThread
	int id();
	@MainThread
	void show(boolean bBlocking);
	@MainThread
	void destroy();
	@MainThread
	void clear();
	@MainThread
	void printString(int attr, String str, int append, int color);
	@MainThread
	KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode,
	        Set<Input.Modifier> modifiers, int repeatCount);
	@MainThread
	void setContext(AppCompatActivity context);
	@MainThread
	String getTitle();
	@MainThread
	void setCursorPos(int x, int y);
	@MainThread
	void preferencesUpdated(SharedPreferences prefs);
	@MainThread
	boolean isVisible();
	@MainThread
	boolean handleGamepadKey(android.view.KeyEvent ev);
	@MainThread
	boolean handleGamepadMotion(android.view.MotionEvent ev);
}
