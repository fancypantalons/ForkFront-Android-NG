package com.tbd.forkfront.engine;

import java.util.ArrayList;
import android.os.Handler;
import com.tbd.forkfront.window.menu.MenuItem;

import androidx.annotation.AnyThread;

/**
 * Interface for sending commands to the NetHack engine.
 * Decouples UI components from the concrete NetHackIO implementation.
 */
public interface EngineCommandSender {
    @AnyThread
    void sendKeyCmd(char key);
    @AnyThread
    void sendDirKeyCmd(char key);
    @AnyThread
    void sendPosCmd(int x, int y);
    @AnyThread
    void sendLineCmd(String str);
    @AnyThread
    void sendSelectCmd(long id, int count);
    @AnyThread
    void sendSelectCmd(ArrayList<MenuItem> items);
    @AnyThread
    void sendSelectNoneCmd();
    @AnyThread
    void sendCancelSelectCmd();
    
    // Lifecycle and sync
    @AnyThread
    void pushInput(String str);
    @AnyThread
    void saveAndQuit();
    @AnyThread
    void saveState();
    @AnyThread
    void waitReady();
    @AnyThread
    Handler getHandler();
}
