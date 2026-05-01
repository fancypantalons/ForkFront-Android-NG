package com.tbd.forkfront.window.map;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.R;
import com.tbd.forkfront.Log;
import com.tbd.forkfront.engine.EngineCommands;
import com.tbd.forkfront.window.message.NHW_Status;
import com.tbd.forkfront.window.NH_Window;
import com.tbd.forkfront.engine.ByteDecoder;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.input.KeyAction;
import com.tbd.forkfront.input.Input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.graphics.*;
import androidx.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;

public class NHW_Map implements NH_Window
{
	// ____________________________________________________________________________________
	// Listener interface for map updates
	// ____________________________________________________________________________________
	public interface MapUpdateListener
	{
		void onMapUpdated();
		void onViewportChanged(PointF viewOffset, float scale, RectF canvasRect);
	}
	public static final int TileCols = 80;
	public static final int TileRows = 21;
	static final float PIE_SLICE = (float)Math.sqrt(2)-1;// tan(pi/8)
	private static final float SELF_RADIUS_FACTOR = 25;

	// y k u
	// \ | /
	// h- . -l
	// / | \
	// b j n

	static final int DIR_LEFT = 0;
	static final int DIR_DOWN = 1;
	static final int DIR_UP = 2;
	static final int DIR_RIGHT = 3;
	static final int DIR_UL = 4;
	static final int DIR_UR = 5;
	static final int DIR_DL = 6;
	static final int DIR_DR = 7;

	private static final char[] VI_DIRS = {'h', 'j', 'k', 'l', 'y', 'u', 'b', 'n'};
	private static final char[] NUM_DIRS = {'4', '2', '8', '6', '7', '9', '1', '3'};

	char getDirChar(int dir)
	{
		return mCommands.isNumPadOn() ? NUM_DIRS[dir] : VI_DIRS[dir];
	}

	class Tile
	{
		public int glyph;
		public int bkglyph;
		public short overlay;
		public char[] ch = { 0 };
		public int color;
	}

	enum TouchResult
	{
		SEND_POS,
		SEND_MY_POS,
		SEND_DIR
	}

	AppCompatActivity mContext;
	MapView mUI;
	MapViewport mViewport;
	Tile[][] mTiles;
	float mDisplayDensity;
	float mSelfRadius;
	float mSelfRadiusSquared;
	final Tileset mTileset;
	Point mPlayerPos;
	Point mCursorPos;
	private boolean mIsRogue;
	NHW_Status mStatus;
	int mHealthColor;
	private boolean mIsVisible;
	boolean mIsBlocking;
	private int mWid;
	final EngineCommands mCommands;
	final MapInputCoordinator mMapInput;
	final ByteDecoder mDecoder;
	int mBorderColor;
	int mScreenSizeClass;
	int mGameBackgroundColor;


	volatile boolean mIsGamepadCursorMode;
	private long mLastCursorMoveMs;

	// System insets for edge-to-edge support
	int mSystemInsetsTop;
	int mSystemInsetsBottom;
	int mSystemInsetsLeft;
	int mSystemInsetsRight;

	// Map update listeners
	private final List<MapUpdateListener> mMapListeners = new ArrayList<>();

	// ____________________________________________________________________________________
	public NHW_Map(AppCompatActivity context, Tileset tileset, NHW_Status status, EngineCommands commands, MapInputCoordinator mapInput, ByteDecoder decoder)
	{
		mCommands = commands;
		mMapInput = mapInput;
		mDecoder = decoder;
		mTileset = tileset;
		mTiles = new Tile[TileRows][TileCols];
		for(Tile[] row : mTiles)
		{
			for(int i = 0; i < row.length; i++)
				row[i] = new Tile();
		}
		mPlayerPos = new Point();
		mCursorPos = new Point(-1, -1);
		mStatus = status;
		mBorderColor = 0;
		mSystemInsetsTop = 0;
		mSystemInsetsBottom = 0;
		mSystemInsetsLeft = 0;
		mSystemInsetsRight = 0;
		mGameBackgroundColor = 0xFF000000;
		clear();
		setContext(context);
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return "NHW_Map";
	}
	
	// ____________________________________________________________________________________
	@Override
	public void setContext(AppCompatActivity context)
	{
		if(mContext == context)
			return;
		mContext = context;

		// Detect screen size class for adaptive tile scaling
		Configuration config = context.getResources().getConfiguration();
		mScreenSizeClass = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;

		mDisplayDensity = context.getResources().getDisplayMetrics().density;
		mSelfRadius = SELF_RADIUS_FACTOR * mDisplayDensity;
		mSelfRadiusSquared = mSelfRadius * mSelfRadius;
		if(mUI != null)
		{
			mUI.hideInternal();
			ViewGroup parent = (ViewGroup)mUI.getParent();
			if(parent != null)
				parent.removeView(mUI);
		}
		mUI = new MapView(this);
		mViewport = new MapViewport(this, mUI, mUI.mRenderer);
		mViewport.mMinTileH = mViewport.getMinTileSizeDp() * mDisplayDensity;
		mViewport.mMaxTileH = mViewport.getMaxTileSizeDp() * mDisplayDensity;
		mViewport.mLockTopMargin = mStatus.getHeight();

		if(mIsVisible)
			show(mIsBlocking);
		else
			hide();
		updateZoomLimits();
	}

	// ____________________________________________________________________________________
	public void onConfigurationChanged(Configuration newConfig)
	{
		if(mContext == null)
			return;

		// Update screen size class for adaptive tile scaling
		int newScreenSizeClass = newConfig.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
		float newDisplayDensity = mContext.getResources().getDisplayMetrics().density;

		// Check if screen size class or density has changed
		boolean sizeChanged = (mScreenSizeClass != newScreenSizeClass);
		boolean densityChanged = (Math.abs(mDisplayDensity - newDisplayDensity) > 0.01f);

		if(sizeChanged || densityChanged)
		{
			mScreenSizeClass = newScreenSizeClass;
			mDisplayDensity = newDisplayDensity;

			// Preserve current zoom level preference (mScaleCount)
			// Recalculate absolute tile sizes with new density/size class
			mViewport.mMinTileH = mViewport.getMinTileSizeDp() * mDisplayDensity;
			mViewport.mMaxTileH = mViewport.getMaxTileSizeDp() * mDisplayDensity;
			mSelfRadius = SELF_RADIUS_FACTOR * mDisplayDensity;
			mSelfRadiusSquared = mSelfRadius * mSelfRadius;

			// Recalculate zoom limits and clamp mScaleCount to new bounds if necessary
			updateZoomLimits();
		}
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsVisible = true;
		setBlocking(bBlocking);
		mUI.showInternal();
	}

	// ____________________________________________________________________________________
	private void hide()
	{
		mIsVisible = false;
		mUI.hideInternal();
	}

	// ____________________________________________________________________________________
	@Override
	public void destroy()
	{
		hide();
	}

	// ____________________________________________________________________________________
	public void setId(int wid)
	{
		mWid = wid;
	}
	
	// ____________________________________________________________________________________
	@Override
	public int id()
	{
		return mWid;
	}
	
	// ____________________________________________________________________________________
	@Override
	public void printString(int attr, String str, int append, int color)
	{
	}

	// ____________________________________________________________________________________
	@Override
	public void clear()
	{
		synchronized (mTiles)
		{
			for(Tile[] row : mTiles)
			{
				for(int i = 0; i < row.length; i++)
					row[i].glyph = -1;
			}
		}
	}

	public Point getPlayerPos()
	{
		return mPlayerPos;
	}

	public int getTileGlyph(int x, int y)
	{
		if(x < 0 || x >= TileCols || y < 0 || y >= TileRows)
			return -1;
		synchronized (mTiles)
		{
			return mTiles[y][x].glyph;
		}
	}

	public int getTileBkGlyph(int x, int y)
	{
		if(x < 0 || x >= TileCols || y < 0 || y >= TileRows)
			return -1;
		synchronized (mTiles)
		{
			return mTiles[y][x].bkglyph;
		}
	}

	public char getTileChar(int x, int y)
	{
		if(x < 0 || x >= TileCols || y < 0 || y >= TileRows)
			return ' ';
		synchronized (mTiles)
		{
			return mTiles[y][x].ch[0];
		}
	}

	public short getTileOverlay(int x, int y)
	{
		if(x < 0 || x >= TileCols || y < 0 || y >= TileRows)
			return 0;
		synchronized (mTiles)
		{
			return mTiles[y][x].overlay;
		}
	}

	public void beginGamepadCursor()
	{
		mIsGamepadCursorMode = true;
		if(mCursorPos.x < 0 || mCursorPos.y < 0)
		{
			setCursorPos(mPlayerPos.x, mPlayerPos.y);
		}
		centerView(mCursorPos.x, mCursorPos.y);
		if(mUI != null)
			mUI.invalidate();
	}

	public void endGamepadCursor()
	{
		mIsGamepadCursorMode = false;
		if(mUI != null)
			mUI.invalidate();
	}

	public boolean handleGamepadKey(KeyEvent ev)
	{
		if(!mIsGamepadCursorMode) return false;
		if(ev.getAction() != KeyEvent.ACTION_DOWN) return false;

		long now = System.currentTimeMillis();
		switch(ev.getKeyCode())
		{
		case KeyEvent.KEYCODE_DPAD_UP:
			return moveCursor(0, -1, now);
		case KeyEvent.KEYCODE_DPAD_DOWN:
			return moveCursor(0, 1, now);
		case KeyEvent.KEYCODE_DPAD_LEFT:
			return moveCursor(-1, 0, now);
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			return moveCursor(1, 0, now);
		case KeyEvent.KEYCODE_BUTTON_A:
			mCommands.sendPosCmd(mCursorPos.x, mCursorPos.y);
			return true;
		case KeyEvent.KEYCODE_BUTTON_B:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			mCommands.sendDirKeyCmd('\033');
			return true;
		case KeyEvent.KEYCODE_BUTTON_X:
			setCursorPos(mPlayerPos.x, mPlayerPos.y);
			centerView(mCursorPos.x, mCursorPos.y);
			return true;
		case KeyEvent.KEYCODE_BUTTON_L1:
			return moveCursor(-5, 0, now);
		case KeyEvent.KEYCODE_BUTTON_R1:
			return moveCursor(5, 0, now);
		}
		return false;
	}

	private boolean moveCursor(int dx, int dy, long now)
	{
		if(now - mLastCursorMoveMs < 100) return true;
		setCursorPos(Math.max(0, Math.min(TileCols - 1, mCursorPos.x + dx)),
					 Math.max(0, Math.min(TileRows - 1, mCursorPos.y + dy)));
		centerView(mCursorPos.x, mCursorPos.y);
		mLastCursorMoveMs = now;
		return true;
	}

	@Override
	public boolean handleGamepadMotion(MotionEvent ev)
	{
		return false;
	}

	@Override
	public boolean isVisible()
	{
		return mIsVisible;
	}

	// ____________________________________________________________________________________
	// Listener management
	// ____________________________________________________________________________________

	public void addMapListener(MapUpdateListener listener)
	{
		if(listener != null && !mMapListeners.contains(listener))
		{
			mMapListeners.add(listener);
		}
	}

	public void removeMapListener(MapUpdateListener listener)
	{
		mMapListeners.remove(listener);
	}

	void notifyMapUpdated()
	{
		for(MapUpdateListener listener : mMapListeners)
		{
			listener.onMapUpdated();
		}
	}

	void notifyViewportChanged()
	{
		for(MapUpdateListener listener : mMapListeners)
		{
			listener.onViewportChanged(
				new PointF(mViewport.mViewOffset.x, mViewport.mViewOffset.y),
				mViewport.mScale,
				new RectF(mViewport.mCanvasRect)
			);
		}
	}

	public PointF getViewOffset()
	{
		return mViewport.getViewOffset();
	}

	public float getScale()
	{
		return mViewport.getScale();
	}

	public RectF getCanvasRect()
	{
		return mViewport.getCanvasRect();
	}

	public float getScaledTileWidth()
	{
		return mUI != null ? mUI.mRenderer.getScaledTileWidth() : 0;
	}

	public float getScaledTileHeight()
	{
		return mUI != null ? mUI.mRenderer.getScaledTileHeight() : 0;
	}

	// ____________________________________________________________________________________
	public void cliparound(final int tileX, final int tileY, final int playerX, final int playerY)
	{
		mViewport.cliparound(tileX, tileY, playerX, playerY);
	}

	// ____________________________________________________________________________________
	public void centerView(final int tileX, final int tileY)
	{
		mViewport.centerView(tileX, tileY);
	}

	// ____________________________________________________________________________________
	private boolean shouldLockView()
	{
		return mViewport.shouldLockView();
	}

	// ____________________________________________________________________________________
	private boolean shouldLockView(float tileW, float tileH)
	{
		return mViewport.shouldLockView(tileW, tileH);
	}

	// ____________________________________________________________________________________
	public void onCursorPosClicked()
	{
		if(mIsBlocking)
		{
			setBlocking(false);
			return;
		}

		Log.print(String.format(Locale.ROOT, "cursor pos clicked: %dx%d", mCursorPos.x, mCursorPos.y));
		mCommands.sendPosCmd(mCursorPos.x, mCursorPos.y);
	}

	// ____________________________________________________________________________________
	public int dxFromKey(int c)
	{
		int d;
		int l = Character.toLowerCase(c);
		if(l == getDirChar(DIR_UL) || l == getDirChar(DIR_LEFT) || l == getDirChar(DIR_DL))
			d = -1;
		else if(l == getDirChar(DIR_UR) || l == getDirChar(DIR_RIGHT) || l == getDirChar(DIR_DR))
			d = 1;
		else
			d = 0;
		if(l != c)
			return d * 8;
		return d;
	}

	// ____________________________________________________________________________________
	public int dyFromKey(int c)
	{
		int d;
		int l = Character.toLowerCase(c);
		if(l == getDirChar(DIR_UL) || l == getDirChar(DIR_UP) || l == getDirChar(DIR_UR))
			d = -1;
		else if(l == getDirChar(DIR_DL) || l == getDirChar(DIR_DOWN) || l == getDirChar(DIR_DR))
			d = 1;
		else
			d = 0;
		if(l != c)
			return d * 8;
		return d;
	}

	// ____________________________________________________________________________________
	public void zoom(float amount)
	{
		mViewport.zoom(amount);
	}

	// ____________________________________________________________________________________
	public void zoomForced(float amount)
	{
		mViewport.zoomForced(amount);
	}

	// ____________________________________________________________________________________
	private void resetZoom()
	{
		mViewport.resetZoom();
	}

	// ____________________________________________________________________________________
	public void updateZoomLimits()
	{
		mViewport.updateZoomLimits();
	}

	// ____________________________________________________________________________________
	void updateViewBounds()
	{
		mViewport.updateViewBounds();
	}

	// ____________________________________________________________________________________
	@Override
	public void preferencesUpdated(SharedPreferences prefs) {
		int borderOpacity = prefs.getInt("borderOpacity", 50);
		
		int red = 0xc9;
		int green = 0xc9;
		int blue = 0xf9;
		
		TypedValue typedValue = new TypedValue();
		if (mContext.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true)) {
			red = Color.red(typedValue.data);
			green = Color.green(typedValue.data);
			blue = Color.blue(typedValue.data);
		}

		int borderColor = Color.rgb(red*borderOpacity/256, green*borderOpacity/256, blue*borderOpacity/256);
		if(borderColor != mBorderColor)
		{
			mBorderColor = borderColor;
			mUI.requestRedraw();
		}
		mUI.mRenderer.updateGameBackgroundColor();
	}

	// ____________________________________________________________________________________
	public void pan(float dx, float dy)
	{
		mViewport.pan(dx, dy);
	}

	// ____________________________________________________________________________________
	private boolean canPan()
	{
		return mViewport.canPan();
	}

	// ____________________________________________________________________________________
	private boolean travelAfterPan()
	{
		return mViewport.travelAfterPan();
	}

	// ____________________________________________________________________________________
	MapViewport.Travel getTravelOption()
	{
		return mViewport.getTravelOption();
	}

	// ____________________________________________________________________________________
	public static int clamp(int i, int min, int max)
	{
		return Math.min(Math.max(min, i), max);
	}

	// ____________________________________________________________________________________
	public void setRogueLevel(boolean bIsRogueLevel)
	{
		if(mIsRogue != bIsRogueLevel)
		{
			mIsRogue = bIsRogueLevel;
			updateZoomLimits();
		}
	}

	// ____________________________________________________________________________________
	public boolean isTTY()
	{
		return mIsRogue || !mTileset.hasTiles();
	}

	// ____________________________________________________________________________________
	public void setBlocking(boolean bBlocking)
	{
		if(bBlocking)
		{
			hideControls();
			mUI.setBlockingInternal(bBlocking);
		}
		else
		{
			showControls();
			if(mIsBlocking)
				mCommands.sendKeyCmd(' ');
			mUI.setBlockingInternal(bBlocking);
		}
		mIsBlocking = bBlocking;
	}

	// ____________________________________________________________________________________
	private void hideControls()
	{
		mStatus.hide();
		mMapInput.hideControls();
	}

	// ____________________________________________________________________________________
	private void showControls()
	{
		mStatus.show(false);
		mMapInput.showControls();
	}

	// ____________________________________________________________________________________
	@Override
	public void setCursorPos(int x, int y)
	{
		if(mCursorPos.x != x || mCursorPos.y != y)
		{
			mCursorPos.x = clamp(x, 0, TileCols - 1);
			mCursorPos.y = clamp(y, 0, TileRows - 1);
			mUI.requestRedraw();
		}
	}

	// ____________________________________________________________________________________
	public void printTile(final int x, final int y, final int tile, final int bkglyph, final int ch, final int col, final int special)
	{
		synchronized (mTiles)
		{
			mTiles[y][x].glyph = tile;
			mTiles[y][x].bkglyph = bkglyph;
			mTiles[y][x].ch[0] = mDecoder.decode(ch);
			mTiles[y][x].color = col;
			mTiles[y][x].overlay = (short)special;
		}
		mUI.requestRedraw();
		notifyMapUpdated();
	}

	// ____________________________________________________________________________________
	@Override
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
	{
		if(keyCode == KeyAction.ZoomIn || keyCode == KeyAction.ZoomOut)
		{
			float scale = mViewport.mScaleCount;
			zoom(keyCode == KeyAction.ZoomIn ? mViewport.mZoomStep : -mViewport.mZoomStep);
			if(Math.abs(mViewport.mScaleCount - scale) < 0.1 && repeatCount == 0)
				resetZoom();
			saveZoomLevel();
			return KeyEventResult.HANDLED;
		}

		return mUI.handleKeyDown(nhKey, keyCode) ? KeyEventResult.HANDLED : KeyEventResult.IGNORED;
	}

	// ____________________________________________________________________________________
	public boolean handleKeyUp(int keyCode)
	{
		return mUI.handleKeyUp(keyCode);
	}

	// ____________________________________________________________________________________
	public void setHealthColor(int color)
	{
		if(color != mHealthColor)
		{
			mHealthColor = color;
			mUI.requestRedraw();
		}
	}

	// ____________________________________________________________________________________
	public void viewAreaChanged(Rect viewRect)
	{
		mViewport.viewAreaChanged(viewRect);
	}

	// ____________________________________________________________________________________
	public void saveZoomLevel() {
		mViewport.saveZoomLevel();
	}
	
	// ____________________________________________________________________________________
	public void loadZoomLevel() {
		mViewport.loadZoomLevel();
	}

}
