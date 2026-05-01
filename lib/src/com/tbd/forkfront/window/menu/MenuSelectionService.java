package com.tbd.forkfront.window.menu;

import java.util.ArrayList;
import com.tbd.forkfront.engine.EngineCommandSender;

/**
 * Service for sending menu selection commands to the NetHack engine.
 */
public class MenuSelectionService {
    private final EngineCommandSender mIO;

    public MenuSelectionService(EngineCommandSender io) {
        mIO = io;
    }

    public void sendSelectChecked(ArrayList<MenuItem> items) {
        mIO.sendSelectCmd(items);
    }

    public void sendCancelSelect() {
        mIO.sendCancelSelectCmd();
    }

    public void sendSelectOne(MenuItem item, int count) {
        mIO.sendSelectCmd(item.getId(), count);
    }

    public void sendSelectNone() {
        mIO.sendSelectNoneCmd();
    }
    
    public void sendContinue() {
        mIO.sendKeyCmd(' ');
    }
}
