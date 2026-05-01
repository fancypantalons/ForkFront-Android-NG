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
	private final MenuModel mModel;
	private final MenuSelectionService mSelectionService;
	private Fragment mFragment;

	// ____________________________________________________________________________________
	public NHW_Menu(int wid, AppCompatActivity context, EngineCommandSender io, Tileset tileset)
	{
		super(wid, context, io);
		mModel = new MenuModel(tileset);
		mSelectionService = new MenuSelectionService(io);
	}

	public MenuModel getModel() {
		return mModel;
	}

	public MenuSelectionService getSelectionService() {
		return mSelectionService;
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return mModel.mTitle;
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsVisible = true;
		if(mModel.mBuilder != null && mModel.mType == MenuModel.Type.None)
		{
			mModel.mType = MenuModel.Type.Text;
		}
		mModel.mKeyboardCount = -1;
		mIsBlocking = bBlocking;

		if(mFragment == null || !mFragment.isAdded())
		{
			if (mModel.mType == MenuModel.Type.Text)
				mFragment = new MenuTextFragment();
			else
				mFragment = new MenuFragment();

			Bundle args = new Bundle();
			args.putInt("wid", mWid);
			mFragment.setArguments(args);
			addFragment(mFragment);
		}
		mState.getGamepadContext().pushContext(mModel.mType == MenuModel.Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
	}

	// ____________________________________________________________________________________
	private void hide()
	{
		if (mIsVisible) {
			mState.getGamepadContext().popContext(mModel.mType == MenuModel.Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
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
		return mFragment != null && mFragment.isAdded() && ((MenuFragmentInterface)mFragment).handleGamepadKey(ev);
	}

	@Override
	public boolean handleGamepadMotion(MotionEvent ev)
	{
		return mFragment != null && mFragment.isAdded() && ((MenuFragmentInterface)mFragment).handleGamepadMotion(ev);
	}

	// ____________________________________________________________________________________
	public void close()
	{
		hide();
		super.close();
		mIsBlocking = false;
		mModel.mItems = null;
		mModel.mBuilder = null;
		mModel.mType = MenuModel.Type.None;
		mModel.mKeyboardCount = -1;
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
		if(mModel.mBuilder == null)
		{
			mModel.mBuilder = new SpannableStringBuilder(str);
			mModel.mItems = null;
		}
		else
		{
			mModel.mBuilder.append('\n');
			mModel.mBuilder.append(TextAttr.style(str, attr, mContext));
		}
		if(mFragment != null && mFragment.isAdded())
			((MenuFragmentInterface)mFragment).refresh();
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
		mModel.mItems = new ArrayList<MenuItem>(100);
		mModel.mBuilder = null;
	}

	// ____________________________________________________________________________________
	public void addMenu(int tile, long ident, int accelerator, int groupacc, int attr, String str, int preselected, int color)
	{
		if(str.length() == 0 && tile < 0)
			return;
		// start_menu is not always called
		if(mModel.mItems == null)
			startMenu();
		mModel.mItems.add(new MenuItem(tile, ident, accelerator, groupacc, attr, str, preselected, color, mContext));
	}

	// ____________________________________________________________________________________
	public void endMenu(String prompt)
	{
		mModel.mTitle = prompt;
	}

	// ____________________________________________________________________________________
	@Override
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
	{
		if(mFragment != null && mFragment.isAdded()) {
			android.util.Log.d("NHW_Menu", "Delegating handleKeyDown to fragment");
			return ((MenuFragmentInterface)mFragment).handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
		}
		android.util.Log.d("NHW_Menu", "Fragment not ready for handleKeyDown");
		return KeyEventResult.IGNORED;
	}

	// ____________________________________________________________________________________
	public void selectMenu(MenuSelectMode how)
	{
		mModel.mType = MenuModel.Type.Menu;
		mModel.mKeyboardCount = -1;
		mModel.mHow = how;
		MenuAcceleratorAssigner.assign(mModel.mItems);
		show(false);
	}

	// ____________________________________________________________________________________
	@Override
	public void preferencesUpdated(SharedPreferences prefs) {
		if(mFragment != null && mFragment.isAdded())
			((MenuFragmentInterface)mFragment).refresh();
	}
}
