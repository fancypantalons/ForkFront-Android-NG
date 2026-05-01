package com.tbd.forkfront.window.map;

import java.util.Arrays;
import java.util.List;

import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

class MapGestureController
{
	enum ZoomPanMode
	{
		Idle, Pressed, Panning, Zooming,
	}

	private final NHW_Map mMap;
	private final MapView mView;
	private final MapRenderer mRenderer;
	private MapViewport mViewport;
	private final Handler mHandler;
	private Runnable mLongPressRunnable;
	private final PointF mPointer0;
	private final PointF mPointer1;
	private int mPointerId0;
	private int mPointerId1;
	private float mPointerDist;
	private ZoomPanMode mZoomPanMode;
	private boolean mIsdPadCenterDown; // don't know if both of these can exist on the same device
	private boolean mIsTrackBallDown; //
	private boolean mIsPannedSinceDown;
	private boolean mIsViewPanned;
	private List<Character> mPickChars = Arrays.asList('.', ',', ';', ':');
	private List<Character> mCancelKeys = Arrays.asList('\033', (char)0x80);

	// ____________________________________________________________________________________
	MapGestureController(NHW_Map map, MapView view, MapRenderer renderer)
	{
		mMap = map;
		mView = view;
		mRenderer = renderer;
		mHandler = new Handler(Looper.getMainLooper());
		mPointer0 = new PointF();
		mPointer1 = new PointF();
		mPointerId0 = -1;
		mPointerId1 = -1;
		mZoomPanMode = ZoomPanMode.Idle;
	}

	// ____________________________________________________________________________________
	void setViewport(MapViewport viewport)
	{
		mViewport = viewport;
	}

	// ____________________________________________________________________________________
	void resetState()
	{
		mIsdPadCenterDown = false;
		mIsTrackBallDown = false;
		mIsPannedSinceDown = false;
		mIsViewPanned = false;
	}

	// ____________________________________________________________________________________
	boolean onTrackball(MotionEvent event)
	{
		switch(getAction(event))
		{
		case MotionEvent.ACTION_DOWN:
			mIsTrackBallDown = true;
			mIsPannedSinceDown = false;
		break;

		case MotionEvent.ACTION_UP:
			if(mIsTrackBallDown && !mIsPannedSinceDown)
				mMap.onCursorPosClicked();
			mIsTrackBallDown = false;
		break;

		case MotionEvent.ACTION_MOVE:
			// TODO tweak sensitivity
			final float axis0 = 1.f;
			final float axis1 = 0.8f;

			char dir = 0;

			if(event.getX() >= axis0) {
				if(event.getY() >= axis1)      dir = mMap.getDirChar(NHW_Map.DIR_DR);
				else if(event.getY() <= -axis1) dir = mMap.getDirChar(NHW_Map.DIR_UR);
				else                           dir = mMap.getDirChar(NHW_Map.DIR_RIGHT);
			} else if(event.getX() <= -axis0) {
				if(event.getY() >= axis1)      dir = mMap.getDirChar(NHW_Map.DIR_DL);
				else if(event.getY() <= -axis1) dir = mMap.getDirChar(NHW_Map.DIR_UL);
				else                           dir = mMap.getDirChar(NHW_Map.DIR_LEFT);
			} else if(event.getY() >= axis0) {
				if(event.getX() >= axis1)      dir = mMap.getDirChar(NHW_Map.DIR_DR);
				else if(event.getX() <= -axis1) dir = mMap.getDirChar(NHW_Map.DIR_DL);
				else                           dir = mMap.getDirChar(NHW_Map.DIR_DOWN);
			} else if(event.getY() <= -axis0) {
				if(event.getX() >= axis1)      dir = mMap.getDirChar(NHW_Map.DIR_UR);
				else if(event.getX() <= -axis1) dir = mMap.getDirChar(NHW_Map.DIR_UL);
				else                           dir = mMap.getDirChar(NHW_Map.DIR_UP);
			}

			if(dir != 0)
				sendDirKeyCmd(dir);
		break;
		}

		return true;
	}

	// ____________________________________________________________________________________
	boolean onTouch(MotionEvent event)
	{
		boolean bHandled = true;
		int idx;

		switch(getAction(event))
		{
		case MotionEvent.ACTION_DOWN:
			setZoomPanMode(ZoomPanMode.Pressed);
			if(mLongPressRunnable != null)
				mHandler.removeCallbacks(mLongPressRunnable);
			mLongPressRunnable = new Runnable() {
				@Override
				public void run() {
					if(mZoomPanMode == ZoomPanMode.Pressed)
						onTouched(mPointer0.x, mPointer0.y, true);
					setZoomPanMode(ZoomPanMode.Idle);
				}
			};
			mHandler.postDelayed(mLongPressRunnable, ViewConfiguration.getLongPressTimeout());

			idx = getActionIndex(event);
			mPointerId0 = event.getPointerId(idx);
			mPointerId1 = -1;
			mPointer0.set(event.getX(idx), event.getY(idx));
		break;

		case MotionEvent.ACTION_UP:
			if(mZoomPanMode == ZoomPanMode.Pressed)
			{
				if(mLongPressRunnable != null)
					mHandler.removeCallbacks(mLongPressRunnable);
				onTouched(mPointer0.x, mPointer0.y, false);
				mView.performClick();
			}
			setZoomPanMode(ZoomPanMode.Idle);
		break;

		case MotionEvent.ACTION_CANCEL:
			if(mLongPressRunnable != null)
				mHandler.removeCallbacks(mLongPressRunnable);
			setZoomPanMode(ZoomPanMode.Idle);
		break;

		case MotionEvent.ACTION_POINTER_DOWN:
			if(mPointerId1 < 0 && (mZoomPanMode == ZoomPanMode.Pressed || mZoomPanMode == ZoomPanMode.Panning))
			{
				// second pointer down, enter zoom mode
				if(mLongPressRunnable != null)
					mHandler.removeCallbacks(mLongPressRunnable);
				setZoomPanMode(ZoomPanMode.Zooming);
				mIsViewPanned = false;
				mViewport.mIsStickyZoom = false;
				idx = getActionIndex(event);
				mPointerId1 = event.getPointerId(idx);
				mPointer1.set(event.getX(idx), event.getY(idx));
				mPointerDist = getPointerDist(event);
			}
		break;

		case MotionEvent.ACTION_POINTER_UP:

			idx = getActionIndex(event);

			int idx0 = event.findPointerIndex(mPointerId0);
			int idx1 = event.findPointerIndex(mPointerId1);

			if(mZoomPanMode == ZoomPanMode.Zooming)
			{
				// Released one of the first two pointers. Ignore other pointers
				if(idx == idx0)
					idx = idx1;
				else if(idx == idx1)
					idx = idx0;

				if(idx == idx0 || idx == idx1)
				{
					// Reset start position for the first pointer
					setZoomPanMode(ZoomPanMode.Panning);
					mPointer0.set(event.getX(idx), event.getY(idx));
					mPointerId0 = event.getPointerId(idx);
					mPointerId1 = -1;
				}
			}
			else if(mZoomPanMode == ZoomPanMode.Panning && idx == idx0)
			{
				// Released the last pointer of the first two. Ignore other pointers
				if(mLongPressRunnable != null)
					mHandler.removeCallbacks(mLongPressRunnable);
				setZoomPanMode(ZoomPanMode.Idle);
			}

		break;

		case MotionEvent.ACTION_MOVE:
			if(mZoomPanMode == ZoomPanMode.Pressed)
				tryStartPan(event);
			else if(mZoomPanMode == ZoomPanMode.Panning)
				calcPan(event);
			else if(mZoomPanMode == ZoomPanMode.Zooming)
				calcZoom(event);
		break;

		default:
			bHandled = false;
		}

		return bHandled;
	}

	// ____________________________________________________________________________________
	private int getActionIndex(MotionEvent event)
	{
		return (event.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
	}

	// ____________________________________________________________________________________
	private int getAction(MotionEvent event)
	{
		return event.getAction() & MotionEvent.ACTION_MASK;
	}

	// ____________________________________________________________________________________
	private void tryStartPan(MotionEvent event)
	{
		int idx = event.findPointerIndex(mPointerId0);

		float dx = event.getX(idx) - mPointer0.x;
		float dy = event.getY(idx) - mPointer0.y;

		float th = ViewConfiguration.get(mView.getContext()).getScaledTouchSlop();

		if(Math.abs(dx) > th || Math.abs(dy) > th)
		{
			if(mLongPressRunnable != null)
				mHandler.removeCallbacks(mLongPressRunnable);
			if(mZoomPanMode != ZoomPanMode.Zooming)
				mIsViewPanned = true;
			setZoomPanMode(ZoomPanMode.Panning);
		}
	}

	// ____________________________________________________________________________________
	private void setZoomPanMode(ZoomPanMode mode)
	{
		if(mZoomPanMode == ZoomPanMode.Zooming)
			mMap.saveZoomLevel();
		mZoomPanMode = mode;
	}

	// ____________________________________________________________________________________
	private void calcPan(MotionEvent event)
	{
		int idx = event.findPointerIndex(mPointerId0);

		float dx = event.getX(idx) - mPointer0.x;
		float dy = event.getY(idx) - mPointer0.y;

		mMap.pan(dx, dy);

		mPointer0.set(event.getX(idx), event.getY(idx));
	}

	// ____________________________________________________________________________________
	private void calcZoom(MotionEvent event)
	{
		int idx0 = event.findPointerIndex(mPointerId0);
		int idx1 = event.findPointerIndex(mPointerId1);

		// Calc average movement of the two cursors and pan accordingly.
		// Don't pan if the cursors move in opposite direction on respective axis.
		float dx0 = event.getX(idx0) - mPointer0.x;
		float dy0 = event.getY(idx0) - mPointer0.y;
		float dx1 = event.getX(idx1) - mPointer1.x;
		float dy1 = event.getY(idx1) - mPointer1.y;

		if(dx0 > 0 && dx1 < 0 || dx0 < 0 && dx1 > 0)
			dx0 = 0;
		else
			dx0 = (dx0 + dx1) * 0.5f;

		if(dy0 > 0 && dy1 < 0 || dy0 < 0 && dy1 > 0)
			dy0 = 0;
		else
			dy0 = (dy0 + dy1) * 0.5f;

		mMap.pan(dx0, dy0);

		// Calc dist between cursors and zoom accordingly.
		float newDist = getPointerDist(event);
		if(newDist > 5)
		{
			float zoomAmount = (int)(1.5f * (newDist - mPointerDist) / mMap.mDisplayDensity);

			if(zoomAmount != 0)
			{
				if(mViewport.mIsStickyZoom)
				{
					int oldScaleSign = (int)Math.signum(mViewport.mScaleCount);
					int newScaleSign = (int)Math.signum(mViewport.mScaleCount + zoomAmount);
					int zoomSign = (int)Math.signum(zoomAmount);
					int stickySign = (int)Math.signum(mViewport.mStickyZoom);

					boolean crossedZero = oldScaleSign != 0 && newScaleSign != 0
						&& oldScaleSign != newScaleSign;
					boolean sameDirAsSticky = mViewport.mStickyZoom != 0
						&& stickySign == zoomSign;

					if((crossedZero && mViewport.mStickyZoom == 0) || sameDirAsSticky)
					{
						mViewport.mStickyZoom += zoomAmount;
						if(Math.abs(mViewport.mStickyZoom) < 50)
							zoomAmount = -mViewport.mScaleCount;
						else
							mViewport.mStickyZoom = 0;
					}
					else
					{
						mViewport.mStickyZoom = 0;
						mViewport.mIsStickyZoom = true;
					}
				}
				else
				{
					mViewport.mStickyZoom = 0;
					mViewport.mIsStickyZoom = true;
				}

				mMap.zoom(zoomAmount);
			}

			mPointerDist = newDist;

			mPointer0.set(event.getX(idx0), event.getY(idx0));
			mPointer1.set(event.getX(idx1), event.getY(idx1));
		}
	}

	// ____________________________________________________________________________________
	private float getPointerDist(MotionEvent event)
	{
		int idx0 = event.findPointerIndex(mPointerId0);
		int idx1 = event.findPointerIndex(mPointerId1);
		float dx = event.getX(idx0) - event.getX(idx1);
		float dy = event.getY(idx0) - event.getY(idx1);
		return (float)Math.sqrt(dx * dx + dy * dy);
	}

	// ____________________________________________________________________________________
	private void onTouched(float x, float y, boolean bLongClick)
	{
		if(mMap.mIsBlocking)
		{
			if(!bLongClick)
				mMap.setBlocking(false);
			mIsViewPanned = false;
			return;
		}

		int tileX = mRenderer.screenToTileX(x);
		int tileY = mRenderer.screenToTileY(y);

		float tileW = mRenderer.getScaledTileWidth();
		float tileH = mRenderer.getScaledTileHeight();
		float cx = mRenderer.tileToScreenX(mMap.mPlayerPos.x) + tileW * 0.5f;
		float cy = mRenderer.tileToScreenY(mMap.mPlayerPos.y) + tileH * 0.5f;

		float dx = x - cx;
		float dy = y - cy;

		float distFromSelfSquared = dx*dx+dy*dy;

		switch(getTouchResult(tileX, tileY, distFromSelfSquared))
		{
			case SEND_MY_POS:
				tileX = mMap.mPlayerPos.x;
				tileY = mMap.mPlayerPos.y;
			// fall through
			case SEND_POS:
				tileX = NHW_Map.clamp(tileX, 0, NHW_Map.TileCols - 1);
				tileY = NHW_Map.clamp(tileY, 0, NHW_Map.TileRows - 1);
				if(mMap.mCommands.isMouseLocked())
					mMap.setCursorPos(tileX, tileY);
				mMap.mCommands.sendPosCmd(tileX, tileY);
			break;

			case SEND_DIR:
				char dir = getDir(tileX, tileY, dx, dy, distFromSelfSquared);

				if(bLongClick && !mMap.mCommands.expectsDirection())
				{
					mMap.mCommands.sendKeyCmd('g');
					mMap.mCommands.sendKeyCmd(dir);
				}
				else
				{
					mMap.mCommands.sendDirKeyCmd(dir);
				}
			break;
		}
		mIsViewPanned = false;
	}

	// ____________________________________________________________________________________
	private char getDir(int tileX, int tileY, float dx, float dy, float distFromSelfSquared)
	{
		if(mMap.mPlayerPos.equals(tileX, tileY) || distFromSelfSquared < mMap.mSelfRadiusSquared)
			return '.';

		float adx = Math.abs(dx);
		float ady = Math.abs(dy);

		char dir;
		if(ady < NHW_Map.PIE_SLICE * adx)
			dir = dx > 0 ? mMap.getDirChar(NHW_Map.DIR_RIGHT) : mMap.getDirChar(NHW_Map.DIR_LEFT);
		else if(adx < NHW_Map.PIE_SLICE * ady)
			dir = dy < 0 ? mMap.getDirChar(NHW_Map.DIR_UP) : mMap.getDirChar(NHW_Map.DIR_DOWN);
		else if(dx > 0)
			dir = dy < 0 ? mMap.getDirChar(NHW_Map.DIR_UR) : mMap.getDirChar(NHW_Map.DIR_DR);
		else
			dir = dy < 0 ? mMap.getDirChar(NHW_Map.DIR_UL) : mMap.getDirChar(NHW_Map.DIR_DL);

		return dir;
	}

	// ____________________________________________________________________________________
	private void sendDirKeyCmd(int c)
	{
		if(mMap.mIsBlocking || mMap.mCommands.isMouseLocked() || mIsdPadCenterDown || mIsTrackBallDown)
		{
			mIsPannedSinceDown = true;
			mIsViewPanned = true;
			mMap.setCursorPos(mMap.mCursorPos.x + mMap.dxFromKey(c), mMap.mCursorPos.y + mMap.dyFromKey(c));
			mMap.centerView(mMap.mCursorPos.x, mMap.mCursorPos.y);
		}
		else
		{
			mMap.mCommands.sendDirKeyCmd(c);
		}
	}

	// ____________________________________________________________________________________
	private NHW_Map.TouchResult getTouchResult(int tileX, int tileY, float distFromSelfSquared)
	{
		if(mMap.mCommands.isMouseLocked())
			return NHW_Map.TouchResult.SEND_POS;

		if(mMap.mCommands.expectsDirection())
			return NHW_Map.TouchResult.SEND_DIR;

		if(mMap.mPlayerPos.equals(tileX, tileY) || distFromSelfSquared < mMap.mSelfRadiusSquared)
			return NHW_Map.TouchResult.SEND_MY_POS;

		MapViewport.Travel travelOption = mMap.getTravelOption();

		if(travelOption == MapViewport.Travel.Never)
			return NHW_Map.TouchResult.SEND_DIR;

		if(travelOption == MapViewport.Travel.Always)
			return NHW_Map.TouchResult.SEND_POS;

		if(!mIsViewPanned)
			return NHW_Map.TouchResult.SEND_DIR;

		// Don't send pos command if clicking within a few tiles from the player

		int dx = Math.abs(tileX - mMap.mPlayerPos.x);
		int dy = Math.abs(tileY - mMap.mPlayerPos.y);

		// . . . . .
		// . . . . .
		// . . @ . .
		// . . . . .
		// . . . . .
		if(dx <= 3 && dy <= 3)
			return NHW_Map.TouchResult.SEND_DIR;

		return NHW_Map.TouchResult.SEND_POS;
	}

	// ____________________________________________________________________________________
	boolean handleKeyDown(int nhKey, int keyCode)
	{
		if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
		{
			mIsdPadCenterDown = true;
			mIsPannedSinceDown = false;
			return true;
		}

		switch(keyCode)
		{
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_DOWN:
			sendDirKeyCmd(nhKey);
			return true;
		}

		if(mMap.mCommands.isMouseLocked())
		{
			if(mPickChars.contains((char)nhKey))
			{
				mMap.onCursorPosClicked();
				return true;
			}
			if(mCancelKeys.contains((char)nhKey))
			{
				mMap.mCommands.sendDirKeyCmd(nhKey);
				return true;
			}
		}

		if(mMap.mIsBlocking)
		{
			mMap.setBlocking(false);
			return true;
		}

		return false;
	}

	// ____________________________________________________________________________________
	boolean handleKeyUp(int keyCode)
	{
		if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
		{
			if(mIsdPadCenterDown && !mIsPannedSinceDown)
				mMap.onCursorPosClicked();
			mIsdPadCenterDown = false;
			return true;
		}
		return false;
	}
}
