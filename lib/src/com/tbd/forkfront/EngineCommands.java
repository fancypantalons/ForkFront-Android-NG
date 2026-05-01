package com.tbd.forkfront;
import com.tbd.forkfront.context.CmdRegistry;

import android.os.Handler;
import com.tbd.forkfront.NetHackIO;

/**
 * Handles outbound command sending to the NetHack engine.
 */
public final class EngineCommands {
    private boolean mIsDPadActive;
    private boolean mIsMouseLocked;
    private boolean mNumPad;
    private final NetHackIO mIO;
    private final MapInputCoordinator mMapCursor;

    public EngineCommands(NetHackIO io, MapInputCoordinator mapCursor) {
        mIO = io;
        mMapCursor = mapCursor;
    }

    public boolean sendKeyCmd(int key) {
        if (key <= 0 || key > 0xff)
            return false;
        mIO.sendKeyCmd((char) key);
        return true;
    }

    public boolean sendDirKeyCmd(int key) {
        if (key <= 0 || key > 0xff)
            return false;
        if (key == 0x80 || key == '\033') {
            mIsMouseLocked = false;
            mMapCursor.exitMapCursorMode();
        }
        if (mIsDPadActive)
            mIO.sendKeyCmd((char) key);
        else
            mIO.sendDirKeyCmd((char) key);
        return true;
    }

    public void sendStringCmd(String str) {
        if (str.startsWith("#")) {
            String cmd = str.substring(1).trim();
            mIO.pushInput(cmd);
            mIO.sendKeyCmd('#');
        } else {
            mIO.sendLineCmd(str);
        }
    }

    public void sendPosCmd(int x, int y) {
        mIsMouseLocked = false;
        mMapCursor.exitMapCursorMode();
        mIO.sendPosCmd(x, y);
    }

    public void executeCommand(CmdRegistry.CmdInfo cmd) {
        String command = cmd.getCommand();
        if (command.startsWith("#")) {
            sendStringCmd(command + "\n");
        } else if (!command.isEmpty()) {
            sendKeyCmd(command.charAt(0));
        }
    }

    public void saveAndQuit() {
        mIO.saveAndQuit();
    }

    public void saveState() {
        mIO.saveState();
    }

    public void waitReady() {
        mIO.waitReady();
    }

    public Handler getHandler() {
        return mIO.getHandler();
    }

    public boolean isMouseLocked() {
        return mIsMouseLocked;
    }

    public boolean expectsDirection() {
        return mIsDPadActive;
    }

    public boolean isNumPadOn() {
        return mNumPad;
    }

    // Package-private — called only by NhEngineCallbacks (or NH_State for now)
    void setDPadActive(boolean b) {
        mIsDPadActive = b;
    }

    void setMouseLocked(boolean b) {
        mIsMouseLocked = b;
    }

    void setNumPad(boolean b) {
        mNumPad = b;
    }
}
