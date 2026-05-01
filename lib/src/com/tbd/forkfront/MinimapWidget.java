package com.tbd.forkfront;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * A widget that displays a minimap overview of the entire dungeon.
 * Shows player position, pets, and the current viewport outline.
 */
public class MinimapWidget extends ControlWidget implements NHW_Map.MapUpdateListener
{
	private NHW_Map mMapWindow;
	private MinimapView mMinimapView;
	private Tileset mTileset;

	// Semantic gameplay colors -- do not theme
	private static final int COLOR_FLOOR = 0xFF888888;       // Medium gray
	private static final int COLOR_WALL = 0xFF555555;        // Darker gray
	private static final int COLOR_PLAYER = 0xFFFFFF00;      // Bright yellow
	private static final int COLOR_PET = 0xFF00FF00;         // Bright green
	private static final int COLOR_MONSTER = 0xFFFF6666;     // Light red

	// ____________________________________________________________________________________
	public MinimapWidget(Context context, NHW_Map mapWindow, Tileset tileset)
	{
		super(context, new MinimapView(context), "minimap");
		mMapWindow = mapWindow;
		mTileset = tileset;
		mMinimapView = (MinimapView) getContentView();
		mMinimapView.setMapWindow(mapWindow);
		mMinimapView.setTileset(tileset);
		mMinimapView.setParentWidget(this);

		// Register as listener
		mapWindow.addMapListener(this);

		// Get initial viewport state
		mMinimapView.setViewportInfo(
			mapWindow.getViewOffset(),
			mapWindow.getScale(),
			mapWindow.getCanvasRect()
		);
	}

	// ____________________________________________________________________________________
	@Override
	public void onMapUpdated()
	{
		mMinimapView.invalidate(); // Request redraw
	}

	// ____________________________________________________________________________________
	@Override
	public void onViewportChanged(PointF viewOffset, float scale, RectF canvasRect)
	{
		if (DEBUG.isOn()) {
			android.util.Log.d("MinimapWidget", "Viewport changed: offset=(" + viewOffset.x + "," + viewOffset.y +
				") scale=" + scale + " canvas=" + canvasRect);
		}
		mMinimapView.setViewportInfo(viewOffset, scale, canvasRect);
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();
		if (mMapWindow != null) {
			mMapWindow.removeMapListener(this);
		}
	}

	// ____________________________________________________________________________________
	// MinimapView - The custom view that renders the minimap
	// ____________________________________________________________________________________
	private static class MinimapView extends View
	{
		private Paint mPaint = new Paint();
		private Paint mBorderPaint = new Paint();
		private RectF mViewportRect = new RectF();
		private float mMiniTileW;
		private float mMiniTileH;
		private NHW_Map mMapWindow;
		private Tileset mTileset;
		private ControlWidget mParentWidget;
		private PointF mViewOffset = new PointF();
		private float mScale = 1.0f;
		private RectF mCanvasRect = new RectF();

		// Themed colors (updated from theme)
		private int COLOR_UNEXPLORED = 0xFF1A1A1A;  // Very dark gray instead of black
		private int COLOR_VIEWPORT = 0xFFFFFFFF;    // Solid white (more visible)
		private int COLOR_BORDER = 0xFF666666;      // Gray border

		// ____________________________________________________________________________________
		public MinimapView(Context context)
		{
			super(context);
			mPaint.setAntiAlias(true);

			// Border paint
			mBorderPaint.setStyle(Paint.Style.STROKE);
			mBorderPaint.setStrokeWidth(2);
			mBorderPaint.setAntiAlias(false);

			updateColors();
		}

		private void updateColors() {
			// Chrome-tracking colors: viewport outline, border, unexplored cells.
			COLOR_UNEXPLORED = ThemeUtils.resolveColor(getContext(),
				R.attr.colorGameBackground, R.color.nh_game_background);
			COLOR_VIEWPORT = ThemeUtils.resolveColor(getContext(),
				R.attr.colorOnSurface, R.color.md_theme_onSurface);
			COLOR_BORDER = ThemeUtils.resolveColor(getContext(),
				R.attr.colorOutline, R.color.md_theme_outline);
			mBorderPaint.setColor(COLOR_BORDER);
			// COLOR_FLOOR / COLOR_WALL / COLOR_PLAYER / COLOR_PET / COLOR_MONSTER
			// are intentional gameplay semantics -- do not theme.
		}

		// ____________________________________________________________________________________
		public void setParentWidget(ControlWidget widget)
		{
			mParentWidget = widget;
		}

		// ____________________________________________________________________________________
		public void setMapWindow(NHW_Map mapWindow)
		{
			mMapWindow = mapWindow;
		}

		// ____________________________________________________________________________________
		public void setTileset(Tileset tileset)
		{
			mTileset = tileset;
		}

		// ____________________________________________________________________________________
		public void setViewportInfo(PointF viewOffset, float scale, RectF canvasRect)
		{
			mViewOffset.set(viewOffset.x, viewOffset.y);
			mScale = scale;
			mCanvasRect.set(canvasRect);
			if (DEBUG.isOn()) {
				android.util.Log.d("MinimapView", "Viewport info set: offset=(" + viewOffset.x + "," + viewOffset.y +
					") scale=" + scale + " canvas=" + canvasRect + " isEmpty=" + canvasRect.isEmpty());
			}
			invalidate();
		}

		// ____________________________________________________________________________________
		@Override
		protected void onDraw(Canvas canvas)
		{
			super.onDraw(canvas);

			if (mMapWindow == null || mTileset == null) {
				return;
			}

			// Calculate mini tile size
			int width = getWidth();
			int height = getHeight();
			if (width == 0 || height == 0) {
				return;
			}

			mMiniTileW = width / (float) NHW_Map.TileCols;
			mMiniTileH = height / (float) NHW_Map.TileRows;

			// Draw all tiles
			drawTiles(canvas);

			// Draw player (before viewport so it's behind the outline)
			drawPlayer(canvas);

			// Draw viewport outline (on top of everything for visibility)
			drawViewport(canvas);

			// Draw border around entire minimap
			drawBorder(canvas);
		}

		// ____________________________________________________________________________________
		private void drawTiles(Canvas canvas)
		{
			for (int y = 0; y < NHW_Map.TileRows; y++) {
				for (int x = 0; x < NHW_Map.TileCols; x++) {
					int glyph = mMapWindow.getTileGlyph(x, y);
					short overlay = mMapWindow.getTileOverlay(x, y);

					float left = x * mMiniTileW;
					float top = y * mMiniTileH;
					float right = (x + 1) * mMiniTileW;
					float bottom = (y + 1) * mMiniTileH;

					// Draw base tile
					if (glyph >= 0) {
						// Tile is explored - bright gray
						mPaint.setColor(COLOR_FLOOR);
						canvas.drawRect(left, top, right, bottom, mPaint);

						// Draw pets as green dots
						if ((overlay & mTileset.OVERLAY_PET) != 0) {
							mPaint.setColor(COLOR_PET);
							float cx = (x + 0.5f) * mMiniTileW;
							float cy = (y + 0.5f) * mMiniTileH;
							float radius = Math.min(mMiniTileW, mMiniTileH) * 0.4f;
							canvas.drawCircle(cx, cy, radius, mPaint);
						}
					} else {
						// Tile is unexplored - dark gray
						mPaint.setColor(COLOR_UNEXPLORED);
						canvas.drawRect(left, top, right, bottom, mPaint);
					}
				}
			}
		}

		// ____________________________________________________________________________________
		private void drawPlayer(Canvas canvas)
		{
			Point playerPos = mMapWindow.getPlayerPos();
			mPaint.setColor(COLOR_PLAYER);

			float cx = (playerPos.x + 0.5f) * mMiniTileW;
			float cy = (playerPos.y + 0.5f) * mMiniTileH;
			float radius = Math.min(mMiniTileW, mMiniTileH) * 0.7f;

			canvas.drawCircle(cx, cy, radius, mPaint);
		}

		// ____________________________________________________________________________________
		private void drawViewport(Canvas canvas)
		{
			if (mCanvasRect.isEmpty() || mMapWindow == null) {
				return;
			}

			// Get the actual scaled tile dimensions from the map
			float scaledTileW = mMapWindow.getScaledTileWidth();
			float scaledTileH = mMapWindow.getScaledTileHeight();

			if (scaledTileW == 0 || scaledTileH == 0) {
				return;
			}

			// Calculate which tiles are visible on screen using the view offset
			// mViewOffset is the pixel position of tile (0,0) on the canvas
			// If offset.x is negative, we've scrolled right (tiles on the right are visible)
			// If offset.x is positive, we've scrolled left (tiles on the left are visible)

			// The leftmost visible tile
			float visibleLeft = -mViewOffset.x / scaledTileW;
			// The topmost visible tile
			float visibleTop = -mViewOffset.y / scaledTileH;
			// The rightmost visible tile
			float visibleRight = visibleLeft + (mCanvasRect.width() / scaledTileW);
			// The bottommost visible tile
			float visibleBottom = visibleTop + (mCanvasRect.height() / scaledTileH);

			// Clamp to map bounds
			float viewportLeft = Math.max(0, Math.min(NHW_Map.TileCols, visibleLeft));
			float viewportTop = Math.max(0, Math.min(NHW_Map.TileRows, visibleTop));
			float viewportRight = Math.max(0, Math.min(NHW_Map.TileCols, visibleRight));
			float viewportBottom = Math.max(0, Math.min(NHW_Map.TileRows, visibleBottom));

			drawViewportRect(canvas, viewportLeft, viewportTop, viewportRight, viewportBottom);
		}

		// ____________________________________________________________________________________
		private void drawViewportRect(Canvas canvas, float viewportLeft, float viewportTop, float viewportRight, float viewportBottom)
		{
			// Convert to minimap coordinates
			float left = viewportLeft * mMiniTileW;
			float top = viewportTop * mMiniTileH;
			float right = viewportRight * mMiniTileW;
			float bottom = viewportBottom * mMiniTileH;

			// Draw viewport rectangle with bright color and thicker stroke
			mPaint.setColor(COLOR_VIEWPORT);
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setStrokeWidth(3); // Thicker for better visibility
			mPaint.setAntiAlias(false); // Crisp lines
			canvas.drawRect(left, top, right, bottom, mPaint);
			mPaint.setStyle(Paint.Style.FILL);
			mPaint.setAntiAlias(true);
		}

		// ____________________________________________________________________________________
		private void drawBorder(Canvas canvas)
		{
			// Draw border around entire minimap
			canvas.drawRect(0, 0, getWidth(), getHeight(), mBorderPaint);
		}

		// ____________________________________________________________________________________
		@Override
		public boolean performClick()
		{
			return super.performClick();
		}

		// ____________________________________________________________________________________
		@Override
		public boolean onTouchEvent(MotionEvent event)
		{
			if (event.getAction() == MotionEvent.ACTION_UP) {
				performClick();
			}
			// TODO: Allow tapping minimap to center main map on that location
			// For now, just consume the event in edit mode
			return super.onTouchEvent(event);
		}
	}
}
