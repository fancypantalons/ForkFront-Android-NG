package com.tbd.forkfront.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;

import com.tbd.forkfront.NHW_Map;

import android.graphics.Point;

/**
 * ContextualActionsEngine computes the available contextual actions based on the player's surroundings.
 * It maps glyphs and object flags to NetHack commands.
 */
public final class ContextualActionsEngine {
    // NetHack 3.6 glyph offsets — must match C engine's include/display.h
    private static final int GLYPH_MON_OFF   = 0;
    private static final int GLYPH_PET_OFF   = 381;
    private static final int GLYPH_INVIS_OFF = 762;
    private static final int GLYPH_DETECT_OFF = 763;
    private static final int GLYPH_BODY_OFF  = 1144;
    private static final int GLYPH_RIDDEN_OFF = 1525;
    private static final int GLYPH_OBJ_OFF   = 1906;
    private static final int GLYPH_CMAP_OFF  = 2359;

    private int mPlayerObjectFlags;
    private int mNearbyMonstersMask;
    private final CopyOnWriteArrayList<GameContextListener> mListeners = new CopyOnWriteArrayList<>();
    private volatile List<CmdRegistry.CmdInfo> mCurrentActions = Collections.emptyList();
    private final Supplier<NHW_Map> mMapProvider;

    public ContextualActionsEngine(Supplier<NHW_Map> mapProvider) {
        mMapProvider = mapProvider;
    }

    /**
     * Called when the engine's viewport or player position changes.
     */
    @MainThread
    public void onCliparound(int x, int y, int px, int py, int objectFlags, int nearbyMonsters) {
        mPlayerObjectFlags = objectFlags;
        mNearbyMonstersMask = nearbyMonsters;
        NHW_Map map = mMapProvider.get();
        if (map != null) {
            map.cliparound(x, y, px, py);
        }
        recompute();
    }

    /**
     * Recomputes the list of available contextual actions based on the current state.
     */
    @MainThread
    public void recompute() {
        NHW_Map map = mMapProvider.get();
        if (map == null) return;

        Point pos = map.getPlayerPos();
        if (pos == null) return;

        List<String> actionKeys = new ArrayList<>();
        
        // ALWAYS add search first
        actionKeys.add("s");
        
        // 1. Check surrounding tiles (Secondary)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = pos.x + dx;
                int ny = pos.y + dy;
                char tile = map.getTileChar(nx, ny);
                int glyph = map.getTileGlyph(nx, ny);
                
                // Doors
                if (tile == '+' ) { // Closed door
                    if (!actionKeys.contains("o")) actionKeys.add("o");
                    if (!actionKeys.contains(String.valueOf((char)4))) actionKeys.add(String.valueOf((char)4)); // Kick
                }
                if (tile == '-' || tile == '|') { // Open door
                    if (glyph >= GLYPH_CMAP_OFF) {
                        if (!actionKeys.contains("c")) actionKeys.add("c");
                    }
                }
            }
        }

        // Monsters (Secondary)
        if (mNearbyMonstersMask != 0) {
            if (!actionKeys.contains("#chat")) actionKeys.add("#chat");
        }

        // 2. Check player's position (PRIMARY - append last)
        if ((mPlayerObjectFlags & 8) != 0) if (!actionKeys.contains("<")) actionKeys.add("<");
        if ((mPlayerObjectFlags & 16) != 0) if (!actionKeys.contains(">")) actionKeys.add(">");
        if ((mPlayerObjectFlags & 32) != 0) { 
            if (!actionKeys.contains("#pray")) actionKeys.add("#pray"); 
            if (!actionKeys.contains("#offer")) actionKeys.add("#offer"); 
        }
        if ((mPlayerObjectFlags & 64) != 0) { 
            if (!actionKeys.contains("q")) actionKeys.add("q"); 
            if (!actionKeys.contains("D")) actionKeys.add("D"); 
        }
        if ((mPlayerObjectFlags & 128) != 0) if (!actionKeys.contains("#sit")) actionKeys.add("#sit");

        if ((mPlayerObjectFlags & 1) != 0) {
            if (!actionKeys.contains(",")) actionKeys.add(","); // Pick up
            if ((mPlayerObjectFlags & 2) != 0) {
                if (!actionKeys.contains("#loot")) actionKeys.add("#loot");
            }
            if ((mPlayerObjectFlags & 4) != 0) {
                if (!actionKeys.contains("e")) actionKeys.add("e"); // Eat
            }
        }

        List<CmdRegistry.CmdInfo> actions = new ArrayList<>();
        for (String key : actionKeys) {
            CmdRegistry.CmdInfo info = CmdRegistry.get(key);
            if (info != null) {
                actions.add(info);
            }
        }

        mCurrentActions = actions;

        for (GameContextListener listener : mListeners) {
            listener.onContextualActionsChanged(actions);
        }
    }

    @AnyThread
    public void register(GameContextListener l) {
        mListeners.add(l);
        l.onContextualActionsChanged(mCurrentActions);
    }

    @AnyThread
    public void unregister(GameContextListener l) {
        mListeners.remove(l);
    }

    @AnyThread
    public List<CmdRegistry.CmdInfo> snapshot() {
        return new ArrayList<>(mCurrentActions);
    }
}
