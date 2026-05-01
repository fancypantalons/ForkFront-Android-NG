package com.tbd.forkfront.gamepad;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Runtime state machine that detects chord completions and fires bindings.
 *
 * Rules:
 * - Bindings fire on press (ACTION_DOWN), not release.
 * - Pure modifiers (buttons that only appear as modifiers in chords) are swallowed silently.
 * - Chord modifiers that have been consumed are swallowed on release.
 * - Auto-repeat re-fires the same binding (except NH_STRING targets, filtered by caller).
 */
public class ChordTracker {
    private final SortedSet<ButtonId> held = new TreeSet<>();
    private final Set<ButtonId> suppressedDown = new HashSet<>();
    private final Set<ButtonId> chordModifiersConsumed = new HashSet<>();
    /** Caches the binding dispatched for each primary key so auto-repeat
     *  re-fires the exact same binding even if held set changes. */
    private final java.util.Map<ButtonId, KeyBinding> activeBindings = new java.util.HashMap<>();

    public enum Result { HANDLED, IGNORED }

    public interface BindingDispatcher {
        void dispatch(KeyBinding binding, boolean isRepeat);
    }

    private final KeyBindingMap mMap;
    private final BindingDispatcher mDispatcher;

    public ChordTracker(KeyBindingMap map, BindingDispatcher dispatcher) {
        this.mMap = map;
        this.mDispatcher = dispatcher;
    }

    public Result onKeyDown(int keycode, int repeatCount) {
        ButtonId k = new ButtonId(keycode);

        if (repeatCount > 0) {
            // Auto-repeat: re-fire if this key was the primary in a previously fired binding
            if (suppressedDown.contains(k)) {
                KeyBinding binding = activeBindings.get(k);
                if (binding != null && binding.chord.primary.code == keycode) {
                    mDispatcher.dispatch(binding, true);
                }
                return Result.HANDLED; // swallow regardless (was suppressed on initial down)
            }
            return Result.IGNORED;
        }

        // Find the best binding match
        KeyBinding binding = mMap.findLongestMatch(held, k);
        if (binding != null) {
            suppressedDown.add(k);
            activeBindings.put(k, binding);
            for (ButtonId mod : binding.chord.modifiers()) {
                chordModifiersConsumed.add(mod);
            }
            held.add(k);
            mDispatcher.dispatch(binding, false);
            return Result.HANDLED;
        }

        // Pure modifier: swallow silently
        if (mMap.isPureModifier(keycode)) {
            held.add(k);
            return Result.HANDLED;
        }

        held.add(k);
        return Result.IGNORED;
    }

    public Result onKeyUp(int keycode) {
        ButtonId k = new ButtonId(keycode);
        held.remove(k);
        activeBindings.remove(k);

        if (suppressedDown.remove(k)) {
            return Result.HANDLED;
        }
        if (chordModifiersConsumed.remove(k)) {
            return Result.HANDLED;
        }

        // Edge case: pure modifier with a solo binding fires on release.
        // (Only applies if the user explicitly bound L1 alone in addition to L1+A.)
        if (mMap.isPureModifier(keycode)) {
            KeyBinding solo = mMap.find(Chord.single(keycode));
            if (solo != null) {
                mDispatcher.dispatch(solo, false);
            }
            return Result.HANDLED;
        }

        return Result.IGNORED;
    }

    /** Clear all state. Call from Activity.onPause and on device removal. */
    public void reset() {
        held.clear();
        suppressedDown.clear();
        chordModifiersConsumed.clear();
        activeBindings.clear();
    }

    public Set<ButtonId> getHeld() {
        return Collections.unmodifiableSet(held);
    }
}
