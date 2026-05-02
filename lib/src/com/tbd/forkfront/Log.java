package com.tbd.forkfront;

public class Log {
  public static void print(String string) {
    if (DEBUG.isOn()) android.util.Log.i("NetHack", string);
  }

  public static void print(int i) {
    if (DEBUG.isOn()) print(Integer.toString(i));
  }
}
