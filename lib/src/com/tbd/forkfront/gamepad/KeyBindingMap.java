package com.tbd.forkfront.gamepad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class KeyBindingMap {
  private final Map<Chord, KeyBinding> byChord;
  private final Set<Integer> modifierOnlyKeycodes;

  public KeyBindingMap(List<KeyBinding> bindings) {
    Map<Chord, KeyBinding> map = new LinkedHashMap<>();
    for (KeyBinding kb : bindings) {
      map.put(kb.chord, kb);
    }
    this.byChord = Collections.unmodifiableMap(map);

    // A keycode is a "pure modifier" if it appears as a modifier in at least one chord
    // but never appears as a primary in any chord.
    Set<Integer> usedAsPrimary = new HashSet<>();
    Set<Integer> usedAsModifier = new HashSet<>();
    for (KeyBinding kb : bindings) {
      usedAsPrimary.add(kb.chord.primary.code);
      for (ButtonId mod : kb.chord.modifiers()) {
        usedAsModifier.add(mod.code);
      }
    }
    Set<Integer> pureModifiers = new HashSet<>(usedAsModifier);
    pureModifiers.removeAll(usedAsPrimary);
    this.modifierOnlyKeycodes = Collections.unmodifiableSet(pureModifiers);
  }

  public KeyBinding find(Chord c) {
    return byChord.get(c);
  }

  public boolean isPureModifier(int keycode) {
    return modifierOnlyKeycodes.contains(keycode);
  }

  /**
   * Find the best matching binding given currently-held buttons and a new press. Prefers chords
   * with more modifiers (longest match first).
   */
  public KeyBinding findLongestMatch(Set<ButtonId> held, ButtonId newPress) {
    TreeSet<ButtonId> candidateAll = new TreeSet<>(held);
    candidateAll.add(newPress);

    List<SortedSet<ButtonId>> candidates = generateSubsets(candidateAll, newPress);
    for (SortedSet<ButtonId> candidate : candidates) {
      Chord c = new Chord(candidate, newPress);
      KeyBinding kb = byChord.get(c);
      if (kb != null) return kb;
    }
    return null;
  }

  public Collection<KeyBinding> all() {
    return byChord.values();
  }

  /** Generate all subsets of {@code all} that contain {@code required}, sorted largest first. */
  private static List<SortedSet<ButtonId>> generateSubsets(
      SortedSet<ButtonId> all, ButtonId required) {
    List<ButtonId> optionals = new ArrayList<>();
    for (ButtonId b : all) {
      if (!b.equals(required)) optionals.add(b);
    }

    int n = optionals.size();
    List<SortedSet<ButtonId>> result = new ArrayList<>(1 << n);
    for (int mask = (1 << n) - 1; mask >= 0; mask--) {
      TreeSet<ButtonId> subset = new TreeSet<>();
      subset.add(required);
      for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) subset.add(optionals.get(i));
      }
      result.add(subset);
    }

    // Sort by size descending so largest chords are tried first
    Collections.sort(result, (a, b) -> Integer.compare(b.size(), a.size()));
    return result;
  }
}
