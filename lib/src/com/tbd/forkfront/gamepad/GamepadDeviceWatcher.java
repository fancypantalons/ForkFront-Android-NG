package com.tbd.forkfront.gamepad;

import android.content.Context;
import android.hardware.input.InputManager;
import android.view.InputDevice;

public class GamepadDeviceWatcher implements InputManager.InputDeviceListener {
    private static final String TAG = "GamepadDeviceWatcher";

    private final InputManager mInputManager;
    private final GamepadDispatcher mDispatcher;

    public GamepadDeviceWatcher(Context ctx, GamepadDispatcher dispatcher) {
        mInputManager = (InputManager) ctx.getSystemService(Context.INPUT_SERVICE);
        mDispatcher = dispatcher;
    }

    public void register() {
        if (mInputManager != null) {
            mInputManager.registerInputDeviceListener(this, null);
        }
    }

    public void unregister() {
        if (mInputManager != null) {
            mInputManager.unregisterInputDeviceListener(this);
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        android.util.Log.d(TAG, "Input device added: " + deviceId);
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        android.util.Log.d(TAG, "Input device removed: " + deviceId + " — resetting tracker");
        mDispatcher.resetTracker();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {}
}
