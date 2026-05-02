package com.tbd.forkfront.window.menu;

import android.text.SpannableString;
import android.text.Spanned;
import com.tbd.forkfront.window.text.TextAttr;

public class MenuItem {
  private final int mTile;
  private final long mIdent;
  private char mAccelerator;
  private final char mGroupacc;
  private final int mAttr;
  private String mName;
  private final Spanned mText;
  private final Spanned mSubText;
  private Spanned mAccText;
  private int mCount;
  private int mMaxCount;

  public MenuItem(
      int tile,
      long ident,
      int accelerator,
      int groupacc,
      int attr,
      String str,
      int selected,
      Integer color,
      android.content.Context context) {
    mTile = tile;
    mIdent = ident;
    mAccelerator = (char) accelerator;
    mGroupacc = (char) groupacc;

    if (mAccelerator == 0 && mIdent == 0 && attr == TextAttr.ATTR_BOLD)
      mAttr = TextAttr.ATTR_INVERSE; // Special case for group headers
    else mAttr = attr; // Else regular entry

    String text = str;
    int lsp = text.lastIndexOf(" (") + 1;
    int rsp = text.lastIndexOf(')');
    int lwp = text.lastIndexOf('{');
    int rwp = text.lastIndexOf('}');
    if (!isHeader()
        && (lsp > 0 || lwp > 0)
        && (rsp > lsp && rsp == text.length() - 1 || rwp > lwp && rwp == text.length() - 1)) {
      boolean hasStatus = rsp > lsp;
      mName = text.substring(0, hasStatus ? lsp : lwp);
      String subText = "";
      if (hasStatus) subText = text.substring(lsp + 1, rsp);
      if (rwp > lwp) {
        if (hasStatus) subText += "; ";
        subText += "w:" + text.substring(lwp + 1, rwp);
      }
      mSubText = TextAttr.style(subText, TextAttr.ATTR_DIM, context);
    } else {
      mName = text;
      mSubText = null;
    }
    if (color == null || color == -1) mText = TextAttr.style(mName, mAttr, context);
    else {
      int finalColor = color;
      if (context != null) {
        int[] palette = TextAttr.getPalette(context);
        if (color >= 0 && color < palette.length) {
          finalColor = palette[color];
        }
      }
      mText = TextAttr.style(mName, mAttr, finalColor);
    }

    setAcc(mAccelerator);

    mCount = selected > 0 ? -1 : 0;
    mMaxCount = 0;

    int i;
    for (i = 0; i < mName.length(); i++) {
      char c = mName.charAt(i);
      if (c < '0' || c > '9') break;
      mMaxCount = mMaxCount * 10 + c - '0';
    }
    if (i > 0 && mMaxCount > 0) mName = mName.substring(i).trim();
    else mMaxCount = 1;
  }

  public String getName() {
    return mName;
  }

  public Spanned getText() {
    return mText;
  }

  public Spanned getSubText() {
    return mSubText;
  }

  public Spanned getAccText() {
    return mAccText;
  }

  public boolean hasSubText() {
    return mSubText != null && mSubText.length() > 0;
  }

  public boolean isHeader() {
    return mAccelerator == 0 && mIdent == 0 && mAttr == TextAttr.ATTR_INVERSE;
  }

  public long getId() {
    return mIdent;
  }

  public boolean hasTile() {
    return mTile >= 0;
  }

  public int getTile() {
    return mTile;
  }

  public void setCount(int c) {
    if (c < 0 || c >= mMaxCount) mCount = -1;
    else mCount = c;
  }

  public int getCount() {
    return mCount;
  }

  public int getMaxCount() {
    return mMaxCount;
  }

  public boolean isSelected() {
    return mCount != 0;
  }

  public void setSelected(boolean selected) {
    mCount = selected ? -1 : 0;
  }

  public boolean hasAcc() {
    return mAccelerator != 0;
  }

  public char getAcc() {
    return mAccelerator;
  }

  public void setAcc(char acc) {
    mAccelerator = acc;
    if (acc != 0) mAccText = new SpannableString(Character.toString(acc) + "   ");
    else mAccText = new SpannableString("    ");
  }

  public char getGroupAcc() {
    return mGroupacc;
  }

  public boolean isSelectable() {
    return mIdent != 0;
  }
}
