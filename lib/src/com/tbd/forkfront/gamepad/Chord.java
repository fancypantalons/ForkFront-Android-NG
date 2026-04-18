package com.tbd.forkfront.gamepad;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

public final class Chord {
    public final SortedSet<ButtonId> all;  // unmodifiable, size >= 1
    public final ButtonId primary;

    public Chord(SortedSet<ButtonId> buttons, ButtonId primary) {
        if (!buttons.contains(primary)) {
            throw new IllegalArgumentException("primary must be in all");
        }
        this.all = Collections.unmodifiableSortedSet(new TreeSet<>(buttons));
        this.primary = primary;
    }

    public static Chord single(int keycode) {
        ButtonId b = new ButtonId(keycode);
        TreeSet<ButtonId> s = new TreeSet<>();
        s.add(b);
        return new Chord(s, b);
    }

    public SortedSet<ButtonId> modifiers() {
        TreeSet<ButtonId> mods = new TreeSet<>(all);
        mods.remove(primary);
        return Collections.unmodifiableSortedSet(mods);
    }

    public boolean isSimple() {
        return all.size() == 1;
    }

    public String displayName() {
        StringBuilder sb = new StringBuilder();
        for (ButtonId b : all) {
            if (!b.equals(primary)) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(b.displayName());
            }
        }
        if (sb.length() > 0) sb.append(" + ");
        sb.append(primary.displayName());
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Chord)) return false;
        Chord c = (Chord) o;
        return all.equals(c.all) && primary.equals(c.primary);
    }

    @Override
    public int hashCode() {
        return 31 * all.hashCode() + primary.hashCode();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
