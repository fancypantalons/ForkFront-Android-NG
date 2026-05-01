package com.tbd.forkfront;

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
	private static final double ZOOM_BASE = 1.005;
	static final float PIE_SLICE = (float)Math.sqrt(2)-1;// tan(pi/8)
	private static final float SELF_RADIUS_FACTOR = 25;
	private static final float MIN_TILE_SIZE_FACTOR = 5;
	private static final float MAX_TILE_SIZE_FACTOR = 100;

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
		return mNHState.isNumPadOn() ? NUM_DIRS[dir] : VI_DIRS[dir];
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
	Tile[][] mTiles;
	float mScale;
	float mDisplayDensity;
	private float mMinTileH;
	private float mMaxTileH;
	float mSelfRadius;
	float mSelfRadiusSquared;
	float mScaleCount;
	private float mMinScaleCount;
	private float mMaxScaleCount;
	private float mZoomStep;
	private float mLockTopMargin;
	int mStickyZoom;
	boolean mIsStickyZoom;
	final Tileset mTileset;
	PointF mViewOffset;
	RectF mCanvasRect;
	Point mPlayerPos;
	Point mCursorPos;
	private boolean mIsRogue;
	private NHW_Status mStatus;
	int mHealthColor;
	private boolean mIsVisible;
	boolean mIsBlocking;
	private int mWid;
	NH_State mNHState;
	final ByteDecoder mDecoder;
	int mBorderColor;
	private int mScreenSizeClass;
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
	public NHW_Map(AppCompatActivity context, Tileset tileset, NHW_Status status, NH_State nhState, ByteDecoder decoder)
	{
		mNHState = nhState;
		mDecoder = decoder;
		mTileset = tileset;
		mTiles = new Tile[TileRows][TileCols];
		for(Tile[] row : mTiles)
		{
			for(int i = 0; i < row.length; i++)
				row[i] = new Tile();
		}
		mScale = 1.f;
		mViewOffset = new PointF();
		mCanvasRect = new RectF();
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
	private float getMinTileSizeDp()
	{
		// For xxxhdpi (4K phones), allow smaller tiles
		if (mDisplayDensity >= 4.0f)
			return 8.f;
		if (mDisplayDensity >= 3.0f)
			return 6.f;
		return 5.f;
	}

	// ____________________________________________________________________________________
	private float getMaxTileSizeDp()
	{
		// For tablets and foldables, allow larger tiles
		if (mScreenSizeClass == Configuration.SCREENLAYOUT_SIZE_XLARGE)
			return 150.f;
		if (mScreenSizeClass == Configuration.SCREENLAYOUT_SIZE_LARGE)
			return 120.f;
		return 100.f;
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
		mMinTileH = getMinTileSizeDp() * mDisplayDensity;
		mMaxTileH = getMaxTileSizeDp() * mDisplayDensity;
		mSelfRadius = SELF_RADIUS_FACTOR * mDisplayDensity;
		mSelfRadiusSquared = mSelfRadius * mSelfRadius;
		mLockTopMargin = mStatus.getHeight();
		if(mUI != null)
		{
			mUI.hideInternal();
			ViewGroup parent = (ViewGroup)mUI.getParent();
			if(parent != null)
				parent.removeView(mUI);
		}
		mUI = new MapView(this);
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
			mMinTileH = getMinTileSizeDp() * mDisplayDensity;
			mMaxTileH = getMaxTileSizeDp() * mDisplayDensity;
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
			mNHState.sendPosCmd(mCursorPos.x, mCursorPos.y);
			return true;
		case KeyEvent.KEYCODE_BUTTON_B:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			mNHState.sendDirKeyCmd('\033');
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
				new PointF(mViewOffset.x, mViewOffset.y),
				mScale,
				new RectF(mCanvasRect)
			);
		}
	}

	public PointF getViewOffset()
	{
		return new PointF(mViewOffset.x, mViewOffset.y);
	}

	public float getScale()
	{
		return mScale;
	}

	public RectF getCanvasRect()
	{
		return new RectF(mCanvasRect);
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
		mPlayerPos.x = playerX;
		mPlayerPos.y = playerY;

		centerView(tileX, tileY);
	}

	// ____________________________________________________________________________________
	public void centerView(final int tileX, final int tileY)
	{
		float tileW = mUI.mRenderer.getScaledTileWidth();
		float tileH = mUI.mRenderer.getScaledTileHeight();

		float ofsX, ofsY;
		if(shouldLockView(tileW, tileH))
		{
			ofsX = mCanvasRect.left + (mCanvasRect.width() - tileW * TileCols) * .5f;

			float hDiff = mCanvasRect.height() - tileH * TileRows;
			float margin = Math.min(mLockTopMargin, hDiff);
			ofsY = mCanvasRect.top + (hDiff + margin) * .5f;
		}
		else
		{
			ofsX = mCanvasRect.left + (mCanvasRect.width() - tileW) * .5f - tileW * tileX;
			ofsY = mCanvasRect.top + (mCanvasRect.height() - tileH) * .5f - tileH * tileY;
		}

		if (mViewOffset.x != ofsX || mViewOffset.y != ofsY)
		{
			mViewOffset.set(ofsX, ofsY);
			mUI.requestRedraw();
			notifyViewportChanged();
		}
	}

	// ____________________________________________________________________________________
	private boolean shouldLockView()
	{
		return shouldLockView(mUI.mRenderer.getScaledTileWidth(), mUI.mRenderer.getScaledTileHeight());
	}

	// ____________________________________________________________________________________
	private boolean shouldLockView(float tileW, float tileH)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		if(!prefs.getBoolean("lockView", true))
			return false;

		return tileW * TileCols	<= mCanvasRect.width() && tileH * TileRows <= mCanvasRect.height();
	}

	// ____________________________________________________________________________________
	public void onCursorPosClicked()
	{
		if(mIsBlocking)
		{
			setBlocking(false);
			return;
		}

		Log.print(String.format(Locale.ROOT, "cursor pos clicked: %dx%d", mCursorPos.x, mCursorPos.y));		mNHState.sendPosCmd(mCursorPos.x, mCursorPos.y);
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
		if(amount == 0)
			return;
		zoomForced(amount);
	}

	// ____________________________________________________________________________________
	public void zoomForced(float amount)
	{
		if(mUI == null)
			return;

		float ofsX = (mViewOffset.x - mCanvasRect.left - mCanvasRect.width() * 0.5f) / mUI.getViewWidth();
		float ofsY = (mViewOffset.y - mCanvasRect.top - mCanvasRect.height() * 0.5f) / mUI.getViewHeight();

		mScaleCount = Math.min(Math.max(mScaleCount + amount, mMinScaleCount), mMaxScaleCount);

		mScale = (float) Math.pow(ZOOM_BASE, mScaleCount);

		if(canPan())
		{
			ofsX = mCanvasRect.left + ofsX * mUI.getViewWidth() + mCanvasRect.width() * 0.5f;
			ofsY = mCanvasRect.top + ofsY * mUI.getViewHeight() + mCanvasRect.height() * 0.5f;

			mViewOffset.set(ofsX, ofsY);
		}
		else
		{
			centerView(0, 0);
		}
		mUI.requestRedraw();
		notifyViewportChanged();
	}

	// ____________________________________________________________________________________
	private void resetZoom()
	{
		zoom(-mScaleCount);
		centerView(mCursorPos.x, mCursorPos.y);
	}

	// ____________________________________________________________________________________
	public void updateZoomLimits()
	{
		float minScale = mMinTileH / mUI.mRenderer.getBaseTileHeight();
		float maxScale = mMaxTileH / mUI.mRenderer.getBaseTileHeight();

		float amount;
		if(mMaxScaleCount - mMinScaleCount < 1)
			amount = 0.5f;
		else
			amount = (mScaleCount - mMinScaleCount) / (mMaxScaleCount - mMinScaleCount);

		mMinScaleCount = (float)(Math.log(minScale) / Math.log(ZOOM_BASE));
		mMaxScaleCount = (float)(Math.log(maxScale) / Math.log(ZOOM_BASE));

		mZoomStep = (mMaxScaleCount - mMinScaleCount) / 20;
		mScaleCount = mMinScaleCount + amount * (mMaxScaleCount - mMinScaleCount);

		zoomForced(0);
	}

	// ____________________________________________________________________________________
	void updateViewBounds()
	{
		// Adjust lock top margin to account for system insets (status bar + top notch/cutout)
		mLockTopMargin = mStatus.getHeight() + mSystemInsetsTop;

		// Recalculate zoom limits with new bounds
		updateZoomLimits();

		// Re-center view if needed
		centerView(mCursorPos.x, mCursorPos.y);
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
		if(canPan())
		{
			mViewOffset.offset(dx, dy);
			mUI.requestRedraw();
			notifyViewportChanged();
		}
	}

	// ____________________________________________________________________________________
	private boolean canPan()
	{
		return travelAfterPan() || !shouldLockView();
	}

	// ____________________________________________________________________________________
	enum Travel
	{
		Never,
		AfterPan,
		Always
	}

	// ____________________________________________________________________________________
	private boolean travelAfterPan()
	{
		return getTravelOption() == Travel.AfterPan;
	}

	// ____________________________________________________________________________________
	Travel getTravelOption()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		// Convert old option
		if(prefs.contains("travelAfterPan"))
		{
			boolean oldValue = prefs.getBoolean("travelAfterPan", true);
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove("travelAfterPan");
			editor.putString("travelOnClick", oldValue ? "1" : "0");
			editor.apply();
		}
		int setting = Util.parseInt(prefs.getString("travelOnClick", "1"), 1);
		if(setting == 0)
			return Travel.Never;
		if(setting == 1)
			return Travel.AfterPan;
		return Travel.Always;
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
				mNHState.sendKeyCmd(' ');
			mUI.setBlockingInternal(bBlocking);
		}
		mIsBlocking = bBlocking;
	}

	// ____________________________________________________________________________________
	private void hideControls()
	{
		mStatus.hide();
		mNHState.hideControls();
	}

	// ____________________________________________________________________________________
	private void showControls()
	{
		mStatus.show(false);
		mNHState.showControls();
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
			float scale = mScaleCount;
			zoom(keyCode == KeyAction.ZoomIn ? mZoomStep : -mZoomStep);
			if(Math.abs(mScaleCount - scale) < 0.1 && repeatCount == 0)
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
		mUI.viewAreaChanged(viewRect);
	}

	// ____________________________________________________________________________________
	public void saveZoomLevel() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		prefs.edit().putFloat("zoomLevel", mScaleCount).apply();
	}
	
	// ____________________________________________________________________________________
	public void loadZoomLevel() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		float zoomLevel = 0;
		try
		{
			zoomLevel = prefs.getFloat("zoomLevel", 0.f);
		}
		catch(Exception e)
		{
		}
		zoom(zoomLevel - mScaleCount);
	}

}
