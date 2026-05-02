package com.tbd.forkfront.window.map;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class TileDrawable extends Drawable {
  private Tileset mTileset;
  private int mTile;

  public TileDrawable(Tileset tileset, int iTile) {
    mTileset = tileset;
    mTile = iTile;
  }

  @Override
  public int getIntrinsicWidth() {
    return mTileset.getTileWidth();
  }

  @Override
  public int getIntrinsicHeight() {
    return mTileset.getTileHeight();
  }

  @Override
  public void draw(Canvas canvas) {
    mTileset.drawTile(canvas, mTile, getBounds(), null);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.OPAQUE;
  }

  @Override
  public void setAlpha(int alpha) {}

  @Override
  public void setColorFilter(ColorFilter cf) {}
}
