package com.tbd.forkfront.window.map;

import android.view.KeyEvent;
import android.view.MotionEvent;

public class GamepadCursorController {
    private final NHW_Map mMap;
    private boolean mIsGamepadCursorMode;
    private long mLastCursorMoveMs;

    public GamepadCursorController(NHW_Map map) {
        mMap = map;
    }

    public void begin() {
        mIsGamepadCursorMode = true;
        if (mMap.mCursorPos.x < 0 || mMap.mCursorPos.y < 0) {
            mMap.setCursorPos(mMap.mPlayerPos.x, mMap.mPlayerPos.y);
        }
        mMap.centerView(mMap.mCursorPos.x, mMap.mCursorPos.y);
        if (mMap.mUI != null) {
            mMap.mUI.invalidate();
        }
    }

    public void end() {
        mIsGamepadCursorMode = false;
        if (mMap.mUI != null) {
            mMap.mUI.invalidate();
        }
    }

    public boolean handleKey(KeyEvent ev) {
        if (!mIsGamepadCursorMode) return false;
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;

        long now = System.currentTimeMillis();
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return moveCursor(0, -1, now);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return moveCursor(0, 1, now);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return moveCursor(-1, 0, now);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return moveCursor(1, 0, now);
            case KeyEvent.KEYCODE_BUTTON_A:
                mMap.mCommands.sendPosCmd(mMap.mCursorPos.x, mMap.mCursorPos.y);
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                mMap.mCommands.sendDirKeyCmd('\033');
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                mMap.setCursorPos(mMap.mPlayerPos.x, mMap.mPlayerPos.y);
                mMap.centerView(mMap.mCursorPos.x, mMap.mCursorPos.y);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                return moveCursor(-5, 0, now);
            case KeyEvent.KEYCODE_BUTTON_R1:
                return moveCursor(5, 0, now);
        }
        return false;
    }

    private boolean moveCursor(int dx, int dy, long now) {
        if (now - mLastCursorMoveMs < 100) return true;
        mMap.setCursorPos(
                Math.max(0, Math.min(NHW_Map.TileCols - 1, mMap.mCursorPos.x + dx)),
                Math.max(0, Math.min(NHW_Map.TileRows - 1, mMap.mCursorPos.y + dy))
        );
        mMap.centerView(mMap.mCursorPos.x, mMap.mCursorPos.y);
        mLastCursorMoveMs = now;
        return true;
    }

    public boolean handleMotion(MotionEvent ev) {
        return false;
    }

    public boolean isActive() {
        return mIsGamepadCursorMode;
    }
}
