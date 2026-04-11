package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.annotation.TargetApi;
import android.app.Application;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import androidx.preference.PreferenceManager;
import android.view.*;
import com.tbd.forkfront.*;
import com.tbd.forkfront.Hearse.Hearse;

public class NH_State
{
	private enum CmdMode
	{
		Panel,
		Keyboard,
	}

	private Application mApp;  // Application context (never leaks, survives Activity)
	private AppCompatActivity mActivity;  // Activity context (updated via setContext())
	private NetHackViewModel mViewModel;  // Reference to ViewModel for deferred UI operations
	private NetHackIO mIO;
	private ByteDecoder mDecoder;  // Stored for deferred initialization
	private NHW_Message mMessage;
	private NHW_Status mStatus;
	private NHW_Map mMap;
	private NH_GetLine mGetLine;
	private NH_Question mQuestion;
	private ArrayList<NH_Window> mWindows;
	private Tileset mTileset;
	private CmdPanelLayout mCmdPanelLayout;
	private DPadOverlay mDPad;
	private boolean mIsDPadActive;
	private boolean mStickyKeyboard;
	private boolean mHideQuickKeyboard;
	private CmdMode mMode;
	private SoftKeyboard mKeyboard;
	private boolean mControlsVisible;
	private boolean mNumPad;
	private boolean mIsMouseLocked;
	private Hearse mHearse;
	private SoftKeyboard.KEYBOARD mRegularKeyboard;
	private SoundPlayer mSoundPlayer;

	// ____________________________________________________________________________________
	/**
	 * Backward-compatible constructor for Phase 4.3-4.4.
	 * Creates NetHackIO internally with Application context.
	 * @deprecated Use constructor with Application and NetHackIO parameters
	 */
	@Deprecated
	public NH_State(AppCompatActivity activity, ByteDecoder decoder)
	{
		this(activity.getApplication(), decoder, new NetHackIO(activity.getApplication(), null, decoder));
		// Now we need to inject the handler into mIO
		// This is a temporary workaround until Phase 4.5
		try {
			java.lang.reflect.Field handlerField = NetHackIO.class.getDeclaredField("mNhHandler");
			handlerField.setAccessible(true);
			handlerField.set(mIO, NhHandler);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject NhHandler into NetHackIO", e);
		}
		setContext(activity);
	}

	// ____________________________________________________________________________________
	/**
	 * New constructor for ViewModel-based lifecycle management (Phase 4.3+).
	 * Requires external creation of NetHackIO with proper NH_Handler.
	 */
	public NH_State(Application app, ByteDecoder decoder, NetHackIO io)
	{
		mApp = app;
		mIO = io;
		mDecoder = decoder;
		mTileset = new Tileset(app);
		mWindows = new ArrayList<>();
		mGetLine = new NH_GetLine(mIO, this);
		mQuestion = new NH_Question(mIO, this);
		mDPad = new DPadOverlay(this);
		mSoundPlayer = new SoundPlayer();
		mMode = CmdMode.Panel;

		// Components requiring Activity context will be initialized in setContext()
		// when an Activity is available: mMessage, mStatus, mMap, mKeyboard, mCmdPanelLayout
	}

	// ____________________________________________________________________________________
	public void setContext(AppCompatActivity activity)
	{
		mActivity = activity;

		// Initialize Activity-dependent components on first call
		if (activity != null) {
			if (mMessage == null) {
				mMessage = new NHW_Message(activity, mIO);
			}
			if (mStatus == null) {
				mStatus = new NHW_Status(activity, mIO);
			}
			if (mKeyboard == null) {
				mKeyboard = new SoftKeyboard(activity, this);
			} else {
				// Update keyboard with new Activity context when Activity is recreated
				mKeyboard.setContext(activity);
			}
			if (mMap == null) {
				mMap = new NHW_Map(activity, mTileset, mStatus, this, mDecoder);
			}
			if (mCmdPanelLayout == null) {
				mCmdPanelLayout = (CmdPanelLayout)activity.findViewById(R.id.cmdPanelLayout1);
			}
		}

		// Update all child components with new Activity context
		for(NH_Window w : mWindows)
			w.setContext(activity);
		mGetLine.setContext(activity);
		mQuestion.setContext(activity);
		if (mMessage != null) {
			mMessage.setContext(activity);
		}
		if (mStatus != null) {
			mStatus.setContext(activity);
		}
		if (mCmdPanelLayout != null) {
			mCmdPanelLayout.setContext(activity, this);
		}
		mDPad.setContext(activity);
		if (mMap != null) {
			mMap.setContext(activity);
		}
		mTileset.setContext(activity);
	}

	// ____________________________________________________________________________________
	public void startNetHack(String path)
	{
		// Ensure Activity context has been set before starting
		if (mActivity == null || mMap == null) {
			throw new IllegalStateException("setContext() must be called with an Activity before starting NetHack");
		}

		mIO.start(path);

		preferencesUpdated();
		updateVisibleState();

		mMap.loadZoomLevel();

		// I have preferences already, might as well pass them in...
		// Pass Application context to Hearse to avoid Activity leaks
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApp);
		mHearse = new Hearse(mApp, prefs, path);
	}

	// ____________________________________________________________________________________
	private String getLastUsername()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApp);
		return prefs.getString("lastUsername", "");
	}

	// ____________________________________________________________________________________
	public void onConfigurationChanged(Configuration newConfig)
	{
		if(mMode == CmdMode.Keyboard && mKeyboard != null)
		{
			// Since the keyboard refuses to change its layout when the orientation changes
			// we recreate a new keyboard every time
			hideKeyboard();
			showKeyboard();
		}

		if (mCmdPanelLayout != null) {
			mCmdPanelLayout.setOrientation(newConfig.orientation);
		}
		mDPad.setOrientation(newConfig.orientation);

		// Forward configuration changes to map for adaptive tile scaling
		if(mMap != null)
			mMap.onConfigurationChanged(newConfig);
	}

	// ____________________________________________________________________________________
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void preferencesUpdated()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApp);

		if (mCmdPanelLayout != null) {
			mCmdPanelLayout.preferencesUpdated(prefs);
		}
		mDPad.preferencesUpdated(prefs);
		mMap.preferencesUpdated(prefs);
		mStatus.preferencesUpdated(prefs);
		mMessage.preferencesUpdated(prefs);
		for(NH_Window w : mWindows)
		{
			if(w != mMap && w != mStatus && w != mMessage)
				w.preferencesUpdated(prefs);
		}

		if(mMode == CmdMode.Panel && mCmdPanelLayout != null)
			mCmdPanelLayout.show();

		mTileset.updateTileset(prefs, mApp.getResources());
		mMap.updateZoomLimits();
		updateSystemUiVisibilityFlags(prefs);
	}

	// ____________________________________________________________________________________
	public void onCreateContextMenu(ContextMenu menu, View v)
	{
		if (mCmdPanelLayout != null) {
			mCmdPanelLayout.onCreateContextMenu(menu, v);
		}
	}

	// ____________________________________________________________________________________
	public void onContextMenuClosed() {
		if (mCmdPanelLayout != null) {
			mCmdPanelLayout.onContextMenuClosed();
		}
		updateSystemUiVisibilityFlags(PreferenceManager.getDefaultSharedPreferences(mApp));
	}

	// ____________________________________________________________________________________
	private void updateSystemUiVisibilityFlags(SharedPreferences prefs)
	{
		// Window operations require Activity context
		if (mActivity == null) return;

		boolean isFullscreen = prefs.getBoolean("fullscreen", false);
		int fullscreenFlag = isFullscreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0;
		mActivity.getWindow().setFlags(fullscreenFlag, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			boolean isImmersive = prefs.getBoolean("immersive", false);
			int uiVisibilityFlags = isImmersive ?
					(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
					: 0;
			mActivity.getWindow().getDecorView().setSystemUiVisibility(uiVisibilityFlags);
		}
	}

	// ____________________________________________________________________________________
	public void onContextItemSelected(android.view.MenuItem item)
	{
		if (mCmdPanelLayout != null) {
			mCmdPanelLayout.onContextItemSelected(item);
		}
	}

	// ____________________________________________________________________________________
	public boolean handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount, boolean bSoftInput)
	{
		if(keyCode == KeyEvent.KEYCODE_BACK && isKeyboardMode())
		{
			hideKeyboard();
			restoreRegularKeyboard();
			return true;
		}

		if(repeatCount > 0) switch(keyCode) {
			case KeyAction.Keyboard:
				if(mMode == CmdMode.Keyboard)
					mStickyKeyboard = false;
			case KeyAction.Control:
			case KeyAction.Meta:
			case KeyEvent.KEYCODE_ESCAPE:
				// Ignore repeat on these actions
				return true;
		}

		KeyEventResult ret = mGetLine.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);

		if(ret == KeyEventResult.IGNORED)
			ret = mQuestion.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);

		for(int i = mWindows.size() - 1; ret == KeyEventResult.IGNORED && i >= 0; i--)
		{
			NH_Window w = mWindows.get(i);
			ret = w.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);
		}

		if(ret == KeyEventResult.HANDLED)
			return true;
		if(ret == KeyEventResult.RETURN_TO_SYSTEM)
			return false;

		if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)
		{
			if(mMode == CmdMode.Keyboard)
			{
				hideKeyboard();
				return true;
			}

			if(mIsDPadActive)
				return sendKeyCmd('\033');
		}
		else if(keyCode == KeyAction.Keyboard)
		{
			mStickyKeyboard = true;
			toggleKeyboard();
			return true;
		}
		else if(keyCode == KeyAction.Control || keyCode == KeyAction.Meta)
		{
			if(!Util.hasPhysicalKeyboard(mApp))
			{
				saveRegularKeyboard();
				if(mMode != CmdMode.Keyboard)
					mHideQuickKeyboard = true;
				showKeyboard();
				if(keyCode == KeyAction.Control)
					setCtrlKeyboard();
				else
					setMetaKeyboard();
			}
			return true;
		}
		if(DEBUG.runTrace() && keyCode == KeyEvent.KEYCODE_BACK)
			Debug.stopMethodTracing();
		return sendKeyCmd(nhKey);
	}

	// ____________________________________________________________________________________
	public NH_Window getWindow(int wid)
	{
		int i = getWindowI(wid);
		return i >= 0 ? mWindows.get(i) : null;
	}

	// ____________________________________________________________________________________
	public int getWindowI(int wid)
	{
		for(int i = 0; i < mWindows.size(); i++)
			if(mWindows.get(i).id() == wid)
				return i;
		return -1;
	}

	// ____________________________________________________________________________________
	public NH_Window toFront(int wid)
	{
		int i = getWindowI(wid);
		NH_Window w = null;
		if(i >= 0)
		{
			w = mWindows.get(i);
			if(i < mWindows.size() - 1)
			{
				mWindows.remove(i);
				mWindows.add(w);
			}
		}
		return w;
	}

	// ____________________________________________________________________________________
	public boolean handleKeyUp(int keyCode)
	{
		if(mMap != null && mMap.handleKeyUp(keyCode))
			return true;

		if(keyCode == KeyAction.Keyboard)
		{
			if(!mStickyKeyboard && mMode == CmdMode.Keyboard)
				hideKeyboard();
			mStickyKeyboard = false;
			return true;
		}
		else if(keyCode == KeyAction.Control || keyCode == KeyAction.Meta)
		{
			if(mMode == CmdMode.Keyboard)
			{
				if(mHideQuickKeyboard)
					hideKeyboard();
				restoreRegularKeyboard();
			}

			mHideQuickKeyboard = false;
			return true;
		}
		return false;
	}

	// ____________________________________________________________________________________
	public boolean isMouseLocked()
	{
		return mIsMouseLocked;
	}

	// ____________________________________________________________________________________
	public void saveAndQuit()
	{
		mIO.saveAndQuit();
	}

	// ____________________________________________________________________________________
	public void saveState()
	{
		mIO.saveState();
	}

	// ____________________________________________________________________________________
	public Handler getHandler()
	{
		return mIO.getHandler();
	}

	// ____________________________________________________________________________________
	public void waitReady()
	{
		mIO.waitReady();
	}

	// ____________________________________________________________________________________
	public boolean sendKeyCmd(int key)
	{
		if(key <= 0 || key > 0xff)
			return false;
		mIO.sendKeyCmd((char)key);
		return true;
	}

	// ____________________________________________________________________________________
	public boolean sendDirKeyCmd(int key)
	{
		if(key <= 0 || key > 0xff)
			return false;
		if(key == 0x80 || key == '\033')
			mIsMouseLocked = false;
		if(mIsDPadActive)
			mIO.sendKeyCmd((char)key);
		else
			mIO.sendDirKeyCmd((char)key);
		return true;
	}

	// ____________________________________________________________________________________
	public void sendPosCmd(int x, int y)
	{
		mIsMouseLocked = false;
		mIO.sendPosCmd(x, y);
	}

	// ____________________________________________________________________________________
	public void clickCursorPos()
	{
		if (mMap != null) {
			mMap.onCursorPosClicked();
		}
	}

	// ____________________________________________________________________________________
	public boolean expectsDirection()
	{
		return mIsDPadActive;
	}

	// ____________________________________________________________________________________
	public boolean isDPadVisible()
	{
		return mDPad.isVisible();
	}

	// ____________________________________________________________________________________
	public void showControls()
	{
		mControlsVisible = true;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void hideControls()
	{
		mControlsVisible = false;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void showKeyboard()
	{
		mMode = CmdMode.Keyboard;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void hideKeyboard()
	{
		mMode = CmdMode.Panel;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void toggleKeyboard()
	{
		if(mMode == CmdMode.Panel)
			showKeyboard();
		else
			hideKeyboard();
	}

	// ____________________________________________________________________________________
	public void setMetaKeyboard()
	{
		if (mKeyboard != null) {
			mKeyboard.setMetaKeyboard();
		}
	}

	// ____________________________________________________________________________________
	private void saveRegularKeyboard()
	{
		if (mKeyboard != null) {
			mRegularKeyboard = mKeyboard.getKeyboard();
		}
	}

	// ____________________________________________________________________________________
	private void restoreRegularKeyboard()
	{
		if(mRegularKeyboard != null && mKeyboard != null) {
			mKeyboard.setKeyboard(mRegularKeyboard);
		}
		mRegularKeyboard = null;
	}

	// ____________________________________________________________________________________
	public void setCtrlKeyboard()
	{
		if (mKeyboard != null) {
			mKeyboard.setCtrlKeyboard();
		}
	}

	// ____________________________________________________________________________________
	private boolean isKeyboardMode()
	{
		return mMode == CmdMode.Keyboard && mControlsVisible;
	}

	// ____________________________________________________________________________________
	public void updateVisibleState()
	{
		if(mControlsVisible)
		{
			if(mMode == CmdMode.Panel)
			{
				if (mKeyboard != null) {
					mKeyboard.hide();
				}
				if(mIsDPadActive)
				{
					mDPad.showDirectional(true);
					if (mCmdPanelLayout != null) {
						mCmdPanelLayout.hide();
					}
				}
				else
				{
					mDPad.showDirectional(false);
					if (mCmdPanelLayout != null) {
						mCmdPanelLayout.show();
					}
				}
			}
			else
			{
				if (mKeyboard != null) {
					mKeyboard.show();
				}
				if (mCmdPanelLayout != null) {
					mCmdPanelLayout.hide();
				}
				//mDPad.setVisible(false);
				mDPad.forceHide();
			}
		}
		else
		{
			if (mCmdPanelLayout != null) {
				mCmdPanelLayout.hide();
			}
			if (mKeyboard != null) {
				mKeyboard.hide();
			}
			mDPad.forceHide();
		}
	}

	// ____________________________________________________________________________________
	public void viewAreaChanged(Rect viewRect)
	{
		if (mMap != null) {
			mMap.viewAreaChanged(viewRect);
		}
	}

	// ____________________________________________________________________________________
	public boolean isNumPadOn()
	{
		return mNumPad;
	}

	// ____________________________________________________________________________________
	public void startPreferences()
	{
		// Starting activities requires Activity context
		if (mActivity == null) return;

		Intent prefsActivity = new Intent(mApp, Settings.class);
		mActivity.startActivityForResult(prefsActivity, 42);
	}

	// ____________________________________________________________________________________
	/**
	 * Get the NH_Handler for this state.
	 * Used by NetHackIO to receive JNI callbacks from native engine.
	 * Package-private for access by NetHackViewModel.
	 */
	NH_Handler getNhHandler() {
		return NhHandler;
	}

	// ____________________________________________________________________________________
	/**
	 * Set the ViewModel reference for deferred UI operations.
	 * Package-private for access by NetHackViewModel.
	 */
	void setViewModel(NetHackViewModel viewModel) {
		mViewModel = viewModel;
	}

	// ____________________________________________________________________________________
	private NH_Handler NhHandler = new NH_Handler() {
		@Override
		public void setLastUsername(String username) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApp);
			prefs.edit().putString("lastUsername", username).commit();
		}

		// ____________________________________________________________________________________
		@Override
		public void setCursorPos(int wid, int x, int y) {
			NH_Window wnd = getWindow(wid);
			if(wnd != null)
				wnd.setCursorPos(x, y);
		}

		// ____________________________________________________________________________________
		@Override
		public void putString(int wid, int attr, String msg, int append, int color) {
			NH_Window wnd = getWindow(wid);
			if(wnd == null) {
				Log.print("[no wnd] " + msg);
				if (mMessage != null) {
					mMessage.printString(attr, msg, append, color);
				}
			} else
				wnd.printString(attr, msg, append, color);
		}

		// ____________________________________________________________________________________
		@Override
		public void setHealthColor(int color) {
			if(mMap != null)
				mMap.setHealthColor(color);
		}

		// ____________________________________________________________________________________
		@Override
		public void rawPrint(int attr, String msg) {
			if (mMessage != null) {
				mMessage.printString(attr, msg, 0, -1);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void printTile(int wid, int x, int y, int tile, int ch, int col, int special) {
			if (mMap != null) {
				mMap.printTile(x, y, tile, ch, col, special);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void ynFunction(String question, byte[] choices, int def) {
			// Defer to when Activity is available using ViewModel's queue
			if (mViewModel != null) {
				mViewModel.runOnActivity(() -> {
					if (mActivity != null) {
						mQuestion.show(mActivity, question, choices, def);
					}
				});
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void getLine(String title, int nMaxChars, boolean showLog) {
			// Defer to when Activity is available using ViewModel's queue
			if (mViewModel != null) {
				mViewModel.runOnActivity(() -> {
					if (mActivity != null) {
						if(showLog && mMessage != null)
							mGetLine.show(mActivity, mMessage.getLogLine(2) + title, nMaxChars);
						else
							mGetLine.show(mActivity, title, nMaxChars);
					}
				});
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void askName(int nMaxChars, String[] saves) {
			// Defer to when Activity is available using ViewModel's queue
			if (mViewModel != null) {
				mViewModel.runOnActivity(() -> {
					if (mActivity != null) {
						String last = getLastUsername();
						List<String> list = new ArrayList<>();
						for(String s : saves) {
							if(last.equals(s))
								list.add(0, s);
							else
								list.add(s);
						}
						mGetLine.showWhoAreYou(mActivity, nMaxChars, list);
					}
				});
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void loadSound(String filename)
		{
			mSoundPlayer.load(filename);
		}

		@Override
		public void playSound(String filename, int volume)
		{
			mSoundPlayer.play(filename, volume);
		}

		// ____________________________________________________________________________________
		@Override
		public void createWindow(int wid, int type)
		{
			switch(type)
			{
			case 1: // #define NHW_MESSAGE 1
				if (mMessage != null) {
					mMessage.setId(wid);
					mWindows.add(mMessage);
				}
			break;

			case 2: // #define NHW_STATUS 2
				if (mStatus != null) {
					mStatus.setId(wid);
					mWindows.add(mStatus);
				}
			break;

			case 3: // #define NHW_MAP 3
				if (mMap != null) {
					mMap.setId(wid);
					mWindows.add(mMap);
				}
			break;

			case 4: // #define NHW_MENU 4
				// Defer menu creation until Activity is available
				if (mViewModel != null) {
					mViewModel.runOnActivity(() -> {
						if (mActivity != null) {
							mWindows.add(new NHW_Menu(wid, mActivity, mIO, mTileset));
						}
					});
				}
			break;

			case 5: // #define NHW_TEXT 5
				// Defer text window creation until Activity is available
				if (mViewModel != null) {
					mViewModel.runOnActivity(() -> {
						if (mActivity != null) {
							mWindows.add(new NHW_Text(wid, mActivity, mIO));
						}
					});
				}
			break;
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void displayWindow(final int wid, final int bBlocking)
		{
			// Defer window display until Activity is available
			if (mViewModel != null) {
				mViewModel.runOnActivity(() -> {
					NH_Window win = toFront(wid);
					if(win != null)
						win.show(bBlocking != 0);
				});
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void clearWindow(final int wid, final int isRogueLevel)
		{
			NH_Window wnd = getWindow(wid);
			if(wnd != null)
			{
				wnd.clear();
				if(wnd == mMap && mMap != null)
					mMap.setRogueLevel(isRogueLevel != 0);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void destroyWindow(final int wid)
		{
			int i = getWindowI(wid);
			mWindows.get(i).destroy();
			mWindows.remove(i);
		}

		// ____________________________________________________________________________________
		@Override
		public void startMenu(final int wid)
		{
			NHW_Menu menu = (NHW_Menu)getWindow(wid);
			if (menu != null) {
				menu.startMenu();
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void addMenu(int wid, int tile, long id, int acc, int groupAcc, int attr, String text, int bSelected, int color)
		{
			NHW_Menu menu = (NHW_Menu)getWindow(wid);
			if (menu != null) {
				menu.addMenu(tile, id, acc, groupAcc, attr, text, bSelected, color);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void endMenu(int wid, String prompt)
		{
			NHW_Menu menu = (NHW_Menu)getWindow(wid);
			if (menu != null) {
				menu.endMenu(prompt);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void selectMenu(int wid, int how)
		{
			// Defer menu selection until Activity is available
			if (mViewModel != null) {
				mViewModel.runOnActivity(() -> {
					NHW_Menu menu = (NHW_Menu)toFront(wid);
					if (menu != null) {
						menu.selectMenu(MenuSelectMode.fromInt(how));
					}
				});
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void cliparound(int x, int y, int playerX, int playerY)
		{
			if (mMap != null) {
				mMap.cliparound(x, y, playerX, playerY);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void showLog(final int bBlocking)
		{
			if (mMessage != null) {
				mMessage.showLog(bBlocking != 0);
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void editOpts()
		{
		}

		// ____________________________________________________________________________________
		@Override
		public void lockMouse()
		{
			mIsMouseLocked = true;
		}

		// ____________________________________________________________________________________
		@Override
		public void showDPad()
		{
			mIsDPadActive = true;
			updateVisibleState();
		}

		// ____________________________________________________________________________________
		@Override
		public void hideDPad()
		{
			mIsDPadActive = false;
			updateVisibleState();
		}

		// ____________________________________________________________________________________
		@Override
		public void setNumPadOption(boolean numPadOn) {
			mNumPad = numPadOn;
			mDPad.updateNumPadState();
		}

		// ____________________________________________________________________________________
		@Override
		public void redrawStatus()
		{
			if (mStatus != null) {
				mStatus.redraw();
			}
		}
	};
}
