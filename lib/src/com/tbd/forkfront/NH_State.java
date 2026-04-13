package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
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
import com.google.android.material.button.MaterialButton;

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
	private WidgetLayout mWidgetLayout;
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
	private int mPlayerObjectFlags;
	private int mNearbyMonstersMask;
	private ControlWidget mTemporaryDPad;

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
				mStatus.show(false); // Status is always visible with field-based updates
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
			if (mWidgetLayout == null) {
				mWidgetLayout = (WidgetLayout)activity.findViewById(R.id.widgetLayout1);
				if (mWidgetLayout != null) {
					// Manually trigger if it didn't run
					mWidgetLayout.onFinishInflate();
				}
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
		if (mWidgetLayout != null) {
			mWidgetLayout.setNHState(this);
			mWidgetLayout.loadLayout();

			View emergencySettings = activity.findViewById(R.id.emergency_settings);
			if (emergencySettings != null) {
				emergencySettings.setOnClickListener(v -> startPreferences());
			}

			View btnAdd = activity.findViewById(R.id.btn_add_widget);
			if (btnAdd != null) {
				btnAdd.setVisibility(isEditMode() ? View.VISIBLE : View.GONE);
				btnAdd.setOnClickListener(v -> {
					if (mActivity != null) {
						showAddWidgetDialog(mActivity);
					}
				});
			}

			View btnSave = activity.findViewById(R.id.btn_save_layout);
			View btnSettings = activity.findViewById(R.id.emergency_settings);
			if (btnSave != null) {
				btnSave.setVisibility(isEditMode() ? View.VISIBLE : View.GONE);
				btnSave.setOnClickListener(v -> {
					// Save layout and exit edit mode
					if (mWidgetLayout != null) {
						mWidgetLayout.saveLayout();
					}
					setEditMode(false);
				});
			}
			if (btnSettings != null) {
				btnSettings.setVisibility(isEditMode() ? View.GONE : View.VISIBLE);
			}

			// Initialize default widgets if it's the first run of the new system
			SharedPreferences ffPrefs = activity.getSharedPreferences("forkfront_ui", Context.MODE_PRIVATE);
			if (!ffPrefs.getBoolean("initialized_v2", false)) {
				float density = activity.getResources().getDisplayMetrics().density;
				int dpadSize = (int)(180 * density);
				int btnW = (int)(100 * density);
				int btnH = (int)(60 * density);

				// Add Default Status Widget at top
				ControlWidget.WidgetData statusData = new ControlWidget.WidgetData();
				statusData.type = "status";
				statusData.x = 0;
				statusData.y = 0; // Top of screen
				statusData.w = (int)(activity.getWindow().getDecorView().getWidth());
				statusData.h = (int)(60 * density);

				StatusWidget statusWidget = new StatusWidget(activity, mStatus);
				statusWidget.setWidgetData(statusData);
				mWidgetLayout.addWidget(statusWidget);

				// Add Default D-Pad
				ControlWidget.WidgetData dpadData = new ControlWidget.WidgetData();
				dpadData.type = "dpad";
				dpadData.x = 20 * density;
				dpadData.y = 150 * density; // Higher up to ensure visibility in landscape
				dpadData.w = dpadSize;
				dpadData.h = dpadSize;

				ControlWidget dpadWidget = new ControlWidget(activity, new DirectionalPadView(activity), "dpad");
				dpadWidget.setWidgetData(dpadData);
				mWidgetLayout.addWidget(dpadWidget);

				// Add Default Search Button
				ControlWidget.WidgetData searchData = new ControlWidget.WidgetData();
				searchData.type = "button";
				searchData.label = "Search";
				searchData.command = "s";
				searchData.x = 250 * density;
				searchData.y = 150 * density;
				searchData.w = btnW;
				searchData.h = btnH;

				MaterialButton searchBtn = new MaterialButton(activity);
				searchBtn.setText("Search");
				searchBtn.setOnClickListener(v -> sendKeyCmd('s'));
				ControlWidget searchWidget = new ControlWidget(activity, searchBtn, "button");
				searchWidget.setWidgetData(searchData);
				mWidgetLayout.addWidget(searchWidget);

				ffPrefs.edit().putBoolean("initialized_v2", true).apply();
				mWidgetLayout.saveLayout();
			}
		}
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

		/*if (mWidgetLayout != null) {
			mWidgetLayout.setOrientation(newConfig.orientation);
		}*/

		// Forward configuration changes to map for adaptive tile scaling
		if(mMap != null)
			mMap.onConfigurationChanged(newConfig);
	}

	// ____________________________________________________________________________________
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void preferencesUpdated()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mApp);

		/*if (mWidgetLayout != null) {
			mWidgetLayout.preferencesUpdated(prefs);
		}*/
		setEditMode(prefs.getBoolean("edit_mode", false));
		mMap.preferencesUpdated(prefs);
		mStatus.preferencesUpdated(prefs);
		mMessage.preferencesUpdated(prefs);
		for(NH_Window w : mWindows)
		{
			if(w != mMap && w != mStatus && w != mMessage)
				w.preferencesUpdated(prefs);
		}

		/*if (mWidgetLayout != null) {
			mWidgetLayout.show();
		}*/

		mTileset.updateTileset(prefs, mApp.getResources());
		mMap.updateZoomLimits();
		updateSystemUiVisibilityFlags(prefs);
	}

	// ____________________________________________________________________________________
	public void onCreateContextMenu(ContextMenu menu, View v)
	{
		/*if (mWidgetLayout != null) {
			mWidgetLayout.onCreateContextMenu(menu, v);
		}*/
	}

	// ____________________________________________________________________________________
	public void onContextMenuClosed() {
		/*if (mWidgetLayout != null) {
			mWidgetLayout.onContextMenuClosed();
		}*/
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
		/*if (mWidgetLayout != null) {
			mWidgetLayout.onContextItemSelected(item);
		}*/
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

	public void sendStringCmd(String str)
	{
		mIO.sendLineCmd(str);
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

	public void showAddWidgetDialog(AppCompatActivity activity) {
		String[] options = {"Directional Pad", "Custom Action Button", "Command List", "Status Window"};
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
			.setTitle("Add Widget")
			.setItems(options, (dialog, which) -> {
				if (which == 0) {
					addDPadWidget(activity);
				} else if (which == 1) {
					showCommandPalette(activity);
				} else if (which == 2) {
					addPaletteWidget(activity);
				} else {
					addStatusWidget(activity);
				}
			})
			.show();
	}

	private void addPaletteWidget(AppCompatActivity activity) {
		float density = activity.getResources().getDisplayMetrics().density;
		
		ControlWidget.WidgetData data = new ControlWidget.WidgetData();
		data.type = "palette";
		data.label = "Commands";
		data.x = 100;
		data.y = 100;
		data.w = (int)(100 * density);
		data.h = (int)(60 * density);
		
		MaterialButton btn = new MaterialButton(activity);
		btn.setText("Commands");
		btn.setIconResource(android.R.drawable.ic_menu_search);
		btn.setOnClickListener(v -> {
			if (!isEditMode()) {
				showCommandPalette(activity);
			}
		});
		
		ControlWidget widget = new ControlWidget(activity, btn, "palette");
		widget.setWidgetData(data);
		mWidgetLayout.addWidget(widget);
		mWidgetLayout.saveLayout();
	}

	private void addDPadWidget(AppCompatActivity activity) {
		float density = activity.getResources().getDisplayMetrics().density;
		int dpadSize = (int)(180 * density);

		ControlWidget.WidgetData dpadData = new ControlWidget.WidgetData();
		dpadData.type = "dpad";
		dpadData.x = 100;
		dpadData.y = 100;
		dpadData.w = dpadSize;
		dpadData.h = dpadSize;

		ControlWidget dpadWidget = new ControlWidget(activity, new DirectionalPadView(activity), "dpad");
		dpadWidget.setWidgetData(dpadData);
		mWidgetLayout.addWidget(dpadWidget);
		mWidgetLayout.saveLayout();
	}

	private void addStatusWidget(AppCompatActivity activity) {
		float density = activity.getResources().getDisplayMetrics().density;

		ControlWidget.WidgetData statusData = new ControlWidget.WidgetData();
		statusData.type = "status";
		statusData.x = 100;
		statusData.y = 100;
		statusData.w = (int)(400 * density); // Wide enough for status text
		statusData.h = (int)(60 * density);  // Two lines of text

		StatusWidget statusWidget = new StatusWidget(activity, mStatus);
		statusWidget.setWidgetData(statusData);
		mWidgetLayout.addWidget(statusWidget);
		mWidgetLayout.saveLayout();
	}

	public void showCommandPalette(AppCompatActivity activity) {
	    CommandPaletteFragment palette = CommandPaletteFragment.newInstance();
	    palette.setOnCommandListener(cmd -> {
	        if (isEditMode()) {
	            // Add a new button widget for this command
	            ControlWidget.WidgetData data = new ControlWidget.WidgetData();
	            data.type = "button";
	            data.label = cmd.getDisplayName();
	            data.command = cmd.getCommand();
	            data.x = 100; // Default position
	            data.y = 100;
	            data.w = (int)(100 * activity.getResources().getDisplayMetrics().density);
	            data.h = (int)(60 * activity.getResources().getDisplayMetrics().density);

	            MaterialButton btn = new MaterialButton(activity);
	            btn.setText(data.label);
	            btn.setOnClickListener(v -> {
	                if (cmd.getCommand().startsWith("#")) {
	                    sendStringCmd(cmd.getCommand() + "\n");
	                } else {
	                    sendKeyCmd(cmd.getCommand().charAt(0));
	                }
	            });

	            ControlWidget widget = new ControlWidget(activity, btn, "button");
	            widget.setWidgetData(data);
	            mWidgetLayout.addWidget(widget);
	            mWidgetLayout.saveLayout();
	        } else {
	            if (cmd.getCommand().startsWith("#")) {
	                sendStringCmd(cmd.getCommand() + "\n");
	            } else {
	                sendKeyCmd(cmd.getCommand().charAt(0));
	            }
	        }
	    });
	    palette.show(activity.getSupportFragmentManager(), "command_palette");
	}

	public void showWidgetProperties(AppCompatActivity activity, ControlWidget widget) {
		ControlWidget.WidgetData data = widget.getWidgetData();
		boolean isButton = "button".equals(data.type);
		boolean isContextual = "contextual".equals(data.type);
		
		WidgetPropertiesFragment fragment = WidgetPropertiesFragment.newInstance(data.label, isButton, isContextual, data.horizontal);
		fragment.setOnPropertiesListener(new WidgetPropertiesFragment.OnPropertiesListener() {
			@Override
			public void onLabelChanged(String newLabel) {
				data.label = newLabel;
				if (isButton && widget.getContentView() instanceof MaterialButton) {
					((MaterialButton) widget.getContentView()).setText(newLabel);
				}
				mWidgetLayout.saveLayout();
			}

			@Override
			public void onOrientationChanged(boolean horizontal) {
				data.horizontal = horizontal;
				if (isContextual && widget.getContentView() instanceof ContextualActionBarView) {
					((ContextualActionBarView) widget.getContentView()).setOrientation(horizontal);
				}
				mWidgetLayout.saveLayout();
			}

			@Override
			public void onDelete() {
				mWidgetLayout.removeWidget(widget);
			}
		});
		fragment.show(activity.getSupportFragmentManager(), "widget_properties");
	}

	public void updateContextualActions() {
		if (mWidgetLayout == null || mMap == null) return;

		android.graphics.Point pos = mMap.getPlayerPos();
		if (pos == null) return;

		java.util.List<String> actionKeys = new java.util.ArrayList<>();
		
		// ALWAYS add search first
		actionKeys.add("s");
		
		// NetHack 3.6 definitive glyph offsets
		final int GLYPH_MON_OFF = 0;
		final int GLYPH_PET_OFF = 381;
		final int GLYPH_INVIS_OFF = 762;
		final int GLYPH_DETECT_OFF = 763;
		final int GLYPH_BODY_OFF = 1144;
		final int GLYPH_RIDDEN_OFF = 1525;
		final int GLYPH_OBJ_OFF = 1906;
		final int GLYPH_CMAP_OFF = 2359;
		
		int centerGlyph = mMap.getTileGlyph(pos.x, pos.y);
		int bkGlyph = mMap.getTileBkGlyph(pos.x, pos.y);
		char centerChar = mMap.getTileChar(pos.x, pos.y);

		// 1. Check surrounding tiles (Secondary)
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) continue;
				int nx = pos.x + dx;
				int ny = pos.y + dy;
				char tile = mMap.getTileChar(nx, ny);
				int glyph = mMap.getTileGlyph(nx, ny);
				
				// Doors
				if (tile == '+' ) { // Closed door
					if (!actionKeys.contains("o")) actionKeys.add("o");
					if (!actionKeys.contains(String.valueOf((char)4))) actionKeys.add(String.valueOf((char)4)); // Kick
				}
				if (tile == '-' || tile == '|') { // Open door
					if (glyph >= GLYPH_CMAP_OFF) {
						if (!actionKeys.contains("c")) actionKeys.add("c");
					}
				}
			}
		}

		// Monsters (Secondary)
		if (mNearbyMonstersMask != 0) {
			if (!actionKeys.contains("#chat")) actionKeys.add("#chat");
		}

		// 2. Check player's position (PRIMARY - append last)
		if ((mPlayerObjectFlags & 8) != 0) if (!actionKeys.contains("<")) actionKeys.add("<");
		if ((mPlayerObjectFlags & 16) != 0) if (!actionKeys.contains(">")) actionKeys.add(">");
		if ((mPlayerObjectFlags & 32) != 0) { 
			if (!actionKeys.contains("#pray")) actionKeys.add("#pray"); 
			if (!actionKeys.contains("#offer")) actionKeys.add("#offer"); 
		}
		if ((mPlayerObjectFlags & 64) != 0) { 
			if (!actionKeys.contains("q")) actionKeys.add("q"); 
			if (!actionKeys.contains("D")) actionKeys.add("D"); 
		}
		if ((mPlayerObjectFlags & 128) != 0) if (!actionKeys.contains("#sit")) actionKeys.add("#sit");

		if ((mPlayerObjectFlags & 1) != 0) {
			if (!actionKeys.contains(",")) actionKeys.add(","); // Pick up
			if ((mPlayerObjectFlags & 2) != 0) {
				if (!actionKeys.contains("#loot")) actionKeys.add("#loot");
			}
			if ((mPlayerObjectFlags & 4) != 0) {
				if (!actionKeys.contains("e")) actionKeys.add("e"); // Eat
			}
		}

		// Always show search (already added at start)

		List<CmdRegistry.CmdInfo> actions = new ArrayList<>();
		for (String key : actionKeys) {
			CmdRegistry.CmdInfo info = CmdRegistry.get(key);
			if (info != null) {
				actions.add(info);
			}
		}
		
		for (int i = 0; i < mWidgetLayout.getChildCount(); i++) {
			View child = mWidgetLayout.getChildAt(i);
			if (child instanceof ControlWidget) {
				ControlWidget w = (ControlWidget) child;
				if ("contextual".equals(w.getWidgetData().type) && w.getContentView() instanceof ContextualActionBarView) {
					((ContextualActionBarView) w.getContentView()).updateActions(actions);
				}
			}
		}
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
	public void setEditMode(boolean enabled)
	{
		if (mWidgetLayout != null) {
			mWidgetLayout.setEditMode(enabled);
		}
		if (mActivity != null) {
			View btnAdd = mActivity.findViewById(R.id.btn_add_widget);
			if (btnAdd != null) {
				btnAdd.setVisibility(enabled ? View.VISIBLE : View.GONE);
			}
			View btnSave = mActivity.findViewById(R.id.btn_save_layout);
			if (btnSave != null) {
				btnSave.setVisibility(enabled ? View.VISIBLE : View.GONE);
			}
			View btnSettings = mActivity.findViewById(R.id.emergency_settings);
			if (btnSettings != null) {
				btnSettings.setVisibility(enabled ? View.GONE : View.VISIBLE);
			}
		}
		// Clear the edit_mode preference when disabling edit mode
		if (!enabled) {
			SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mApp).edit();
			editor.putBoolean("edit_mode", false);
			editor.apply();
		}
	}

	public boolean isEditMode()
	{
		return mWidgetLayout != null && mWidgetLayout.isEditMode();
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
					/*if (mWidgetLayout != null) {
						mWidgetLayout.hide();
					}*/
				}
				else
				{
					/*if (mWidgetLayout != null) {
						mWidgetLayout.show();
					}*/
				}
			}
			else
			{
				if (mKeyboard != null) {
					mKeyboard.show();
				}
				/*if (mWidgetLayout != null) {
					mWidgetLayout.hide();
				}*/
			}
		}
		else
		{
			/*if (mWidgetLayout != null) {
				mWidgetLayout.hide();
			}*/
			if (mKeyboard != null) {
				mKeyboard.hide();
			}
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
	public NHW_Status getStatusWindow()
	{
		return mStatus;
	}

	// ____________________________________________________________________________________
	public void startPreferences()
	{
		// Starting activities requires Activity context
		if (mActivity == null) return;

		// Use the modern Activity Result API via ForkFront's launchSettings method
		if (mActivity instanceof ForkFront) {
			((ForkFront) mActivity).launchSettings();
		}
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
		public void printTile(int wid, int x, int y, int tile, int bkglyph, int ch, int col, int special) {
			if (mMap != null) {
				mMap.printTile(x, y, tile, bkglyph, ch, col, special);
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
		public void cliparound(int x, int y, int playerX, int playerY, int objectFlags, int nearbyMonsters)
		{
			mPlayerObjectFlags = objectFlags;
			mNearbyMonstersMask = nearbyMonsters;
			if(mMap != null)
				mMap.cliparound(x, y, playerX, playerY);
			updateContextualActions();
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
			if (mViewModel != null) {
				mViewModel.runOnActivity(() -> {
					if (mActivity != null && mWidgetLayout != null) {
						// Check if we already have a D-pad on screen
						ControlWidget existingDPad = null;
						for (int i = 0; i < mWidgetLayout.getChildCount(); i++) {
							View child = mWidgetLayout.getChildAt(i);
							if (child instanceof ControlWidget && "dpad".equals(((ControlWidget) child).getWidgetData().type)) {
								existingDPad = (ControlWidget) child;
								break;
							}
						}

						if (existingDPad != null) {
							// Pulse existing D-pad to draw attention
							existingDPad.pulseAttention();
						} else {
							// Spawn temporary D-pad centered on screen
							float density = mActivity.getResources().getDisplayMetrics().density;
							int dpadSize = (int)(200 * density);

							DirectionalPadView dpadView = new DirectionalPadView(mActivity);
							dpadView.setOnDirectionListener(cmd -> sendDirKeyCmd(cmd));

							mTemporaryDPad = new ControlWidget(mActivity, dpadView, "dpad");

							ControlWidget.WidgetData dpadData = new ControlWidget.WidgetData();
							dpadData.type = "dpad";
							dpadData.x = (mActivity.getWindow().getDecorView().getWidth() - dpadSize) / 2f;
							dpadData.y = (mActivity.getWindow().getDecorView().getHeight() - dpadSize) / 2f;
							dpadData.w = dpadSize;
							dpadData.h = dpadSize;
							mTemporaryDPad.setWidgetData(dpadData);

							mWidgetLayout.addWidget(mTemporaryDPad);
						}
					}
				});
			}
		}

		// ____________________________________________________________________________________
		@Override
		public void hideDPad()
		{
			// Remove temporary D-pad if one was spawned
			if (mTemporaryDPad != null && mWidgetLayout != null) {
				if (mViewModel != null) {
					mViewModel.runOnActivity(() -> {
						if (mWidgetLayout != null) {
							mWidgetLayout.removeWidget(mTemporaryDPad);
							mTemporaryDPad = null;
						}
					});
				}
			}
			mIsDPadActive = false;
			updateVisibleState();
		}

		// ____________________________________________________________________________________
		@Override
		public void setNumPadOption(boolean numPadOn) {
			mNumPad = numPadOn;
			// Old DPad overlay removed - no longer need to update
		}

		// ____________________________________________________________________________________
		// Field-based status methods
		// ____________________________________________________________________________________
		@Override
		public void statusInit()
		{
			if (mStatus != null) {
				mStatus.statusInit();
			}
		}

		@Override
		public void statusEnableField(int fieldIdx, String name, String fmt, boolean enable)
		{
			if (mStatus != null) {
				mStatus.statusEnableField(fieldIdx, name, fmt, enable);
			}
		}

		@Override
		public void statusUpdate(int fieldIdx, String value, long conditionMask, int chg, int percent, int color, long[] colormasks)
		{
			if (mStatus != null) {
				mStatus.statusUpdate(fieldIdx, value, conditionMask, chg, percent, color, colormasks);
			}
		}

		@Override
		public void statusFinish()
		{
			if (mStatus != null) {
				mStatus.statusFinish();
			}
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
