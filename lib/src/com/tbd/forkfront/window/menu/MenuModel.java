package com.tbd.forkfront.window.menu;

import java.util.ArrayList;
import android.text.SpannableStringBuilder;
import com.tbd.forkfront.window.map.Tileset;

/**
 * Data model for a NetHack menu.
 * Stores items, title, and current selection state.
 */
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

    public void startMenu() {
        mItems = new ArrayList<>(100);
        mBuilder = null;
    }

    public void addItem(MenuItem item) {
        if (mItems == null) {
            startMenu();
        }
        mItems.add(item);
    }

    public void endMenu(String prompt) {
        mTitle = prompt;
    }

    public void selectMenu(MenuSelectMode how) {
        mType = Type.Menu;
        mKeyboardCount = -1;
        mHow = how;
    }

    public void setType(Type type) {
        mType = type;
    }

    public Type getType() {
        return mType;
    }

    public ArrayList<MenuItem> getItems() {
        return mItems;
    }

    public String getTitle() {
        return mTitle;
    }

    public SpannableStringBuilder getBuilder() {
        return mBuilder;
    }

    public void setBuilder(SpannableStringBuilder builder) {
        mBuilder = builder;
    }

    public MenuSelectMode getHow() {
        return mHow;
    }

    public int getKeyboardCount() {
        return mKeyboardCount;
    }

    public void setKeyboardCount(int keyboardCount) {
        mKeyboardCount = keyboardCount;
    }

    public Tileset getTileset() {
        return mTileset;
    }
}
