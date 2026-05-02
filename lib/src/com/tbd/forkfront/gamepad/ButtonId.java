package com.tbd.forkfront.gamepad;

public final class ButtonId implements Comparable<ButtonId> {

  // Pseudo-codes for axis-sourced buttons (above normal Android keycode range)
  public static final int AXIS_LTRIGGER_PSEUDO = 0x10001;
  public static final int AXIS_RTRIGGER_PSEUDO = 0x10002;
  public static final int AXIS_HAT_LEFT_PSEUDO = 0x10003;
  public static final int AXIS_HAT_RIGHT_PSEUDO = 0x10004;
  public static final int AXIS_HAT_UP_PSEUDO = 0x10005;
  public static final int AXIS_HAT_DOWN_PSEUDO = 0x10006;

  // Left stick 8-directional pseudo-codes
  public static final int LSTICK_UP_PSEUDO = 0x10010;
  public static final int LSTICK_DOWN_PSEUDO = 0x10011;
  public static final int LSTICK_LEFT_PSEUDO = 0x10012;
  public static final int LSTICK_RIGHT_PSEUDO = 0x10013;
  public static final int LSTICK_UL_PSEUDO = 0x10014; // NW diagonal
  public static final int LSTICK_UR_PSEUDO = 0x10015; // NE diagonal
  public static final int LSTICK_DL_PSEUDO = 0x10016; // SW diagonal
  public static final int LSTICK_DR_PSEUDO = 0x10017; // SE diagonal

  // Right stick pseudo-codes (used for panning in cursor mode)
  public static final int RSTICK_UP_PSEUDO = 0x10020;
  public static final int RSTICK_DOWN_PSEUDO = 0x10021;
  public static final int RSTICK_LEFT_PSEUDO = 0x10022;
  public static final int RSTICK_RIGHT_PSEUDO = 0x10023;

  public final int code; // real KEYCODE_BUTTON_* or a pseudo-code above

  public ButtonId(int code) {
    this.code = code;
  }

  @Override
  public int compareTo(ButtonId o) {
    return Integer.compare(code, o.code);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ButtonId && ((ButtonId) o).code == code;
  }

  @Override
  public int hashCode() {
    return code;
  }

  public String displayName() {
    return ButtonNames.of(code);
  }
}
