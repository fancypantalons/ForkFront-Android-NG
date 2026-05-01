package com.tbd.forkfront.window.menu;

import java.util.ArrayList;
import android.text.SpannableStringBuilder;
import com.tbd.forkfront.window.map.Tileset;
import androidx.annotation.MainThread;
import android.os.Looper;

/**
 * Data model for a NetHack menu.
 * Stores items, title, and current selection state.
 */
@MainThread
public class MenuModel {
    public enum Type {
        None,
        Menu,
        Text
    }

    public ArrayList<MenuItem> mItems;
    public String mTitle;
    public SpannableStringBuilder mBuilder;
    public Type mType = Type.None;
    public MenuSelectMode mHow;
    public int mKeyboardCount = -1;
    public final Tileset mTileset;

    public MenuModel(Tileset tileset) {
        mTileset = tileset;
    }

    private void checkMainThread() {
        assert Looper.myLooper() == Looper.getMainLooper() : "MenuModel must only be accessed on the main thread";
    }

    public void startMenu() {
        checkMainThread();
        mItems = new ArrayList<>(100);
        mBuilder = null;
    }

    public void addItem(MenuItem item) {
        checkMainThread();
        if (mItems == null) {
            startMenu();
        }
        mItems.add(item);
    }

    public void endMenu(String prompt) {
        checkMainThread();
        mTitle = prompt;
    }

    public void selectMenu(MenuSelectMode how) {
        checkMainThread();
        mType = Type.Menu;
        mKeyboardCount = -1;
        mHow = how;
    }

    public void setType(Type type) {
        checkMainThread();
        mType = type;
    }

    public Type getType() {
        checkMainThread();
        return mType;
    }

    public ArrayList<MenuItem> getItems() {
        checkMainThread();
        return mItems;
    }

    public String getTitle() {
        checkMainThread();
        return mTitle;
    }

    public SpannableStringBuilder getBuilder() {
        checkMainThread();
        return mBuilder;
    }

    public void setBuilder(SpannableStringBuilder builder) {
        checkMainThread();
        mBuilder = builder;
    }

    public MenuSelectMode getHow() {
        checkMainThread();
        return mHow;
    }

    public int getKeyboardCount() {
        checkMainThread();
        return mKeyboardCount;
    }

    public void setKeyboardCount(int keyboardCount) {
        checkMainThread();
        mKeyboardCount = keyboardCount;
    }

    public Tileset getTileset() {
        return mTileset;
    }
}
