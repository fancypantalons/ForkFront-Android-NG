package com.tbd.forkfront.engine;

import java.util.ArrayList;
import android.os.Handler;
import com.tbd.forkfront.window.menu.MenuItem;

/**
 * Interface for sending commands to the NetHack engine.
 * Decouples UI components from the concrete NetHackIO implementation.
 */
public interface EngineCommandSender {
    void sendKeyCmd(char key);
    void sendDirKeyCmd(char key);
    void sendPosCmd(int x, int y);
    void sendLineCmd(String str);
    void sendSelectCmd(long id, int count);
    void sendSelectCmd(ArrayList<MenuItem> items);
    void sendSelectNoneCmd();
    void sendCancelSelectCmd();
    
    // Lifecycle and sync
    void pushInput(String str);
    void saveAndQuit();
    void saveState();
    void waitReady();
    Handler getHandler();
}
