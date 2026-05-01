package com.tbd.forkfront.window;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.tbd.forkfront.window.map.NHW_Map;
import com.tbd.forkfront.window.message.NHW_Message;
import com.tbd.forkfront.window.message.NHW_Status;

/**
 * Registry for all active NetHack windows.
 * Manages window list and Z-order.
 * Must be accessed only from the main thread.
 */
public final class WindowRegistry {
    private final List<NH_Window> mWindows = new ArrayList<>();

    @MainThread
    public WindowRegistry() {
        checkMainThread();
    }

    private static void checkMainThread() {
        assert android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
            : "WindowRegistry must only be accessed on the main thread";
    }

    @MainThread
    public void add(NH_Window w) {
        checkMainThread();
        mWindows.add(w);
    }

    @MainThread
    public boolean remove(int wid) {
        checkMainThread();
        int index = indexOf(wid);
        if (index >= 0) {
            mWindows.get(index).destroy();
            mWindows.remove(index);
            return true;
        }
        return false;
    }

    @MainThread
    @Nullable
    public NH_Window get(int wid) {
        checkMainThread();
        int index = indexOf(wid);
        return index >= 0 ? mWindows.get(index) : null;
    }

    @MainThread
    public int indexOf(int wid) {
        checkMainThread();
        for (int i = 0; i < mWindows.size(); i++) {
            if (mWindows.get(i).id() == wid) {
                return i;
            }
        }
        return -1;
    }

    @MainThread
    @Nullable
    public NH_Window toFront(int wid) {
        checkMainThread();
        int i = indexOf(wid);
        NH_Window w = null;
        if (i >= 0) {
            w = mWindows.get(i);
            if (i < mWindows.size() - 1) {
                mWindows.remove(i);
                mWindows.add(w);
            }
        }
        return w;
    }

    @MainThread
    @Nullable
    public NH_Window topVisibleNonSystem() {
        checkMainThread();
        for (int i = mWindows.size() - 1; i >= 0; i--) {
            NH_Window w = mWindows.get(i);
            if (isSystemWindow(w)) {
                continue;
            }
            if (w.isVisible()) {
                return w;
            }
        }
        return null;
    }

    @MainThread
    public void forEach(Consumer<NH_Window> f) {
        checkMainThread();
        List<NH_Window> snapshot = new ArrayList<>(mWindows);
        for (NH_Window w : snapshot) {
            f.accept(w);
        }
    }

    @MainThread
    public void forEachExcept(Consumer<NH_Window> f, NH_Window... exclude) {
        checkMainThread();
        List<NH_Window> snapshot = new ArrayList<>(mWindows);
        for (NH_Window w : snapshot) {
            boolean excluded = false;
            for (NH_Window ex : exclude) {
                if (w == ex) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                f.accept(w);
            }
        }
    }

    @MainThread
    public int size() {
        checkMainThread();
        return mWindows.size();
    }

    @MainThread
    @Nullable
    public NH_Window getAt(int index) {
        checkMainThread();
        if (index >= 0 && index < mWindows.size()) {
            return mWindows.get(index);
        }
        return null;
    }

    public static boolean isSystemWindow(NH_Window w) {
        return w instanceof NHW_Map
            || w instanceof NHW_Message
            || w instanceof NHW_Status;
    }
}
