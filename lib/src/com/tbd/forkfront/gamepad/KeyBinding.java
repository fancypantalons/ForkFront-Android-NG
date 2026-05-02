package com.tbd.forkfront.gamepad;

public final class KeyBinding {
  public final Chord chord;
  public final BindingTarget target;
  public final String label; // optional display label; null = use CmdRegistry name
  public final boolean locked; // true = not user-removable (e.g. Start = drawer)
  public final String sourceCmdKey; // CmdRegistry key (e.g. "h" or "#pray") if applicable

  public KeyBinding(
      Chord chord, BindingTarget target, String label, boolean locked, String sourceCmdKey) {
    this.chord = chord;
    this.target = target;
    this.label = label;
    this.locked = locked;
    this.sourceCmdKey = sourceCmdKey;
  }

  @Override
  public String toString() {
    return chord.displayName() + " → " + target;
  }
}
