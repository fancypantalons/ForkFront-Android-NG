package com.tbd.forkfront.window.menu;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.R;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.window.text.TextAttr;
import com.tbd.forkfront.window.map.Tileset;
import com.tbd.forkfront.window.AbstractNhWindow;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.input.Input;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.tbd.forkfront.gamepad.UiContext;
import androidx.annotation.MainThread;

@MainThread
public class NHW_Menu extends AbstractNhWindow
{
	public enum Type { None, Menu, Text }
	
	public ArrayList<MenuItem> mItems;
	public String mTitle;
	public SpannableStringBuilder mBuilder;
	public Type mType = Type.None;
	public MenuSelectMode mHow;
	public int mKeyboardCount = -1;
	public final Tileset mTileset;
	private Fragment mFragment;

	// ____________________________________________________________________________________
	public NHW_Menu(int wid, AppCompatActivity context, EngineCommandSender io, Tileset tileset)
	{
		super(wid, context, io);
		mTileset = tileset;
	}

	public void sendSelectChecked(ArrayList<MenuItem> items) { mIO.sendSelectCmd(items); }
	public void sendCancelSelect() { mIO.sendCancelSelectCmd(); }
	public void sendSelectOne(MenuItem item, int count) { mIO.sendSelectCmd(item.getId(), count); }
	public void sendSelectNone() { mIO.sendSelectNoneCmd(); }
	public void sendContinue() { mIO.sendKeyCmd(' '); }
	
	private static void assignAccelerators(List<MenuItem> items) {
		for (MenuItem i : items) {
			if (i.hasAcc()) return;
		}
		char acc = 'a';
		for (MenuItem i : items) {
			if (!i.isHeader() && i.isSelectable() && acc != 0) {
				i.setAcc(acc);
				acc++;
				if (acc == 'z' + 1) acc = 'A';
				else if (acc == 'Z' + 1) acc = 0;
			}
		}
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return mTitle;
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsVisible = true;
		if(mBuilder != null && mType == Type.None)
		{
			mType = Type.Text;
		}
		mKeyboardCount = -1;
		mIsBlocking = bBlocking;

		if(mFragment == null || !mFragment.isAdded())
		{
			if (mType == Type.Text)
				mFragment = new MenuTextFragment();
			else
				mFragment = new MenuFragment();

			Bundle args = new Bundle();
			args.putInt("wid", mWid);
			mFragment.setArguments(args);
			addFragment(mFragment);
		}
		mState.getGamepadContext().pushContext(mType == Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
	}

	// ____________________________________________________________________________________
	private void hide()
	{
		if (mIsVisible) {
			mState.getGamepadContext().popContext(mType == Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
		}
		mIsVisible = false;
		removeFragment();
	}

	// ____________________________________________________________________________________
	@Override
	public void destroy()
	{
		mIsVisible = false;
		close();
	}

	@Override
	public boolean handleGamepadKey(KeyEvent ev)
	{
		if (mFragment != null && mFragment.isAdded()) {
			if (mFragment instanceof MenuFragment) return ((MenuFragment)mFragment).handleGamepadKey(ev);
			if (mFragment instanceof MenuTextFragment) return ((MenuTextFragment)mFragment).handleGamepadKey(ev);
		}
		return false;
	}

	@Override
	public boolean handleGamepadMotion(MotionEvent ev)
	{
		if (mFragment != null && mFragment.isAdded()) {
			if (mFragment instanceof MenuFragment) return ((MenuFragment)mFragment).handleGamepadMotion(ev);
			if (mFragment instanceof MenuTextFragment) return ((MenuTextFragment)mFragment).handleGamepadMotion(ev);
		}
		return false;
	}

	// ____________________________________________________________________________________
	public void close()
	{
		hide();
		super.close();
		mIsBlocking = false;
		mItems = null;
		mBuilder = null;
		mType = Type.None;
		mKeyboardCount = -1;
	}

	// ____________________________________________________________________________________
	protected void removeFragment()
	{
		super.removeFragment();
		mFragment = null;
	}

	// ____________________________________________________________________________________
	@Override
	public void clear()
	{
		// Menus are ephemeral and do not support clear.
		// This is a safe no-op to satisfy the NH_Window contract.
	}

	// ____________________________________________________________________________________
	@Override
	public void printString(final int attr, final String str, int append, int color)
	{
		if(mBuilder == null)
		{
			mBuilder = new SpannableStringBuilder(str);
			mItems = null;
		}
		else
		{
			mBuilder.append('\n');
			mBuilder.append(TextAttr.style(str, attr, mContext));
		}
		if(mFragment != null && mFragment.isAdded()) {
			if (mFragment instanceof MenuFragment) ((MenuFragment)mFragment).refresh();
			if (mFragment instanceof MenuTextFragment) ((MenuTextFragment)mFragment).refresh();
		}
	}

	// ____________________________________________________________________________________
	@Override
	public void setCursorPos(int x, int y) {
	}

	@Override
	public boolean isVisible() {
		return mIsVisible;
	}

	// ____________________________________________________________________________________
	public void startMenu()
	{
		mItems = new ArrayList<MenuItem>(100);
		mBuilder = null;
	}

	// ____________________________________________________________________________________
	public void addMenu(int tile, long ident, int accelerator, int groupacc, int attr, String str, int preselected, int color)
	{
		if(str.length() == 0 && tile < 0)
			return;
		// start_menu is not always called
		if(mItems == null)
			startMenu();
		mItems.add(new MenuItem(tile, ident, accelerator, groupacc, attr, str, preselected, color, mContext));
	}

	// ____________________________________________________________________________________
	public void endMenu(String prompt)
	{
		mTitle = prompt;
	}

	// ____________________________________________________________________________________
	@Override
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
	{
		if(mFragment != null && mFragment.isAdded()) {
			android.util.Log.d("NHW_Menu", "Delegating handleKeyDown to fragment");
			if (mFragment instanceof MenuFragment) return ((MenuFragment)mFragment).handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
			if (mFragment instanceof MenuTextFragment) return ((MenuTextFragment)mFragment).handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
		}
		android.util.Log.d("NHW_Menu", "Fragment not ready for handleKeyDown");
		return KeyEventResult.IGNORED;
	}

	// ____________________________________________________________________________________
	public void selectMenu(MenuSelectMode how)
	{
		mType = Type.Menu;
		mKeyboardCount = -1;
		mHow = how;
		assignAccelerators(mItems);
		show(false);
	}

	// ____________________________________________________________________________________
	@Override
	public void preferencesUpdated(SharedPreferences prefs) {
		if(mFragment != null && mFragment.isAdded()) {
			if (mFragment instanceof MenuFragment) ((MenuFragment)mFragment).refresh();
			if (mFragment instanceof MenuTextFragment) ((MenuTextFragment)mFragment).refresh();
		}
	}
}