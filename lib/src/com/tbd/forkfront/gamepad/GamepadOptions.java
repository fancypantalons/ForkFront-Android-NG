package com.tbd.forkfront.gamepad;

import android.content.SharedPreferences;

public final class GamepadOptions {
  public final boolean enabled;
  public final boolean leftStickMovement;
  public final boolean synthDiagonals;
  public final int diagonalWindowMs;

  public GamepadOptions(
      boolean enabled, boolean leftStickMovement, boolean synthDiagonals, int diagonalWindowMs) {
    this.enabled = enabled;
    this.leftStickMovement = leftStickMovement;
    this.synthDiagonals = synthDiagonals;
    this.diagonalWindowMs = diagonalWindowMs;
  }

  public static GamepadOptions fromPrefs(SharedPreferences prefs) {
    return new GamepadOptions(
        prefs.getBoolean("gamepad_enabled", true),
        prefs.getBoolean("gamepad_leftstick_movement", true),
        prefs.getBoolean("gamepad_synth_diagonals", true),
        prefs.getInt("gamepad_diagonal_window_ms", 80));
  }

  public static final GamepadOptions DEFAULTS = new GamepadOptions(true, true, true, 80);
}
