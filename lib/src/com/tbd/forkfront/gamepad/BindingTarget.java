package com.tbd.forkfront.gamepad;
import com.tbd.forkfront.context.CmdRegistry;

public abstract class BindingTarget {
    public enum Kind { NH_KEY, NH_STRING, UI_ACTION }

    public abstract Kind kind();

    public static final class NhKey extends BindingTarget {
        public final char ch; // e.g. 'h', '\033', '\004' (kick)
        public NhKey(char ch) { this.ch = ch; }
        @Override public Kind kind() { return Kind.NH_KEY; }
        @Override public String toString() { return "NhKey(" + (int) ch + ")"; }
    }

    public static final class NhString extends BindingTarget {
        public final String line; // e.g. "#pray\n"
        public NhString(String line) { this.line = line; }
        @Override public Kind kind() { return Kind.NH_STRING; }
        @Override public String toString() { return "NhString(" + line + ")"; }
    }

    public static final class UiAction extends BindingTarget {
        public final UiActionId id;
        public UiAction(UiActionId id) { this.id = id; }
        @Override public Kind kind() { return Kind.UI_ACTION; }
        @Override public String toString() { return "UiAction(" + id + ")"; }
    }

    /**
     * Create a target from a command key string (from CmdRegistry or asset JSON).
     * "#pray" → NhString("#pray\n")
     * Single char → NhKey
     */
    public static BindingTarget fromCmdKey(String cmdKey) {
        if (cmdKey == null || cmdKey.isEmpty()) return null;
        if (cmdKey.startsWith("#")) {
            return new NhString(cmdKey + "\n");
        }
        if (cmdKey.length() == 1) {
            return new NhKey(cmdKey.charAt(0));
        }
        // Multi-char strings are sent as-is
        return new NhString(cmdKey);
    }
}
