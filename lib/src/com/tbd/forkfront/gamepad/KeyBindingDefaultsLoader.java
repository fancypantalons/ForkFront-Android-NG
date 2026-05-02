package com.tbd.forkfront.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Loads default keybindings from assets and merges new defaults into existing user bindings.
 *
 * <p>Asset priority: default_keybindings/{deviceKey}.json → default_keybindings/generic.json
 */
public class KeyBindingDefaultsLoader {
  private static final String PREF_DEFAULTS_APPLIED = "gamepad_defaults_applied_v1";

  private KeyBindingDefaultsLoader() {}

  // Maps human-readable button names in asset JSON to Android keycodes / pseudo-codes.
  private static final Map<String, Integer> NAME_TO_CODE;

  static {
    Map<String, Integer> m = new HashMap<>();
    m.put("BUTTON_A", KeyEvent.KEYCODE_BUTTON_A);
    m.put("BUTTON_B", KeyEvent.KEYCODE_BUTTON_B);
    m.put("BUTTON_X", KeyEvent.KEYCODE_BUTTON_X);
    m.put("BUTTON_Y", KeyEvent.KEYCODE_BUTTON_Y);
    m.put("L1", KeyEvent.KEYCODE_BUTTON_L1);
    m.put("R1", KeyEvent.KEYCODE_BUTTON_R1);
    m.put("L2", KeyEvent.KEYCODE_BUTTON_L2);
    m.put("R2", KeyEvent.KEYCODE_BUTTON_R2);
    m.put("L3", KeyEvent.KEYCODE_BUTTON_THUMBL);
    m.put("R3", KeyEvent.KEYCODE_BUTTON_THUMBR);
    m.put("BUTTON_START", KeyEvent.KEYCODE_BUTTON_START);
    m.put("BUTTON_SELECT", KeyEvent.KEYCODE_BUTTON_SELECT);
    m.put("DPAD_UP", KeyEvent.KEYCODE_DPAD_UP);
    m.put("DPAD_DOWN", KeyEvent.KEYCODE_DPAD_DOWN);
    m.put("DPAD_LEFT", KeyEvent.KEYCODE_DPAD_LEFT);
    m.put("DPAD_RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT);
    m.put("LSTICK_UP", ButtonId.LSTICK_UP_PSEUDO);
    m.put("LSTICK_DOWN", ButtonId.LSTICK_DOWN_PSEUDO);
    m.put("LSTICK_LEFT", ButtonId.LSTICK_LEFT_PSEUDO);
    m.put("LSTICK_RIGHT", ButtonId.LSTICK_RIGHT_PSEUDO);
    NAME_TO_CODE = Collections.unmodifiableMap(m);
  }

  /** Load defaults for the given device key (may be null for generic). */
  public static List<KeyBinding> loadDefaults(Context ctx, String deviceKey) {
    InputStream is = openAsset(ctx, deviceKey);
    if (is == null) return hardcodedFallback();
    try {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      byte[] chunk = new byte[4096];
      int read;
      while ((read = is.read(chunk)) != -1) {
        baos.write(chunk, 0, read);
      }
      return parseJsonDefaults(new String(baos.toByteArray(), "UTF-8"));
    } catch (Exception e) {
      android.util.Log.e("KeyBindingDefaultsLoader", "Failed to load defaults asset", e);
      return hardcodedFallback();
    } finally {
      try {
        is.close();
      } catch (Exception ignored) {
      }
    }
  }

  private static InputStream openAsset(Context ctx, String deviceKey) {
    if (deviceKey != null) {
      try {
        return ctx.getAssets().open("default_keybindings/" + deviceKey + ".json");
      } catch (Exception ignored) {
      }
    }
    try {
      return ctx.getAssets().open("default_keybindings/generic.json");
    } catch (Exception ignored) {
    }
    return null;
  }

  public static List<KeyBinding> parseJsonDefaults(String json) throws JSONException {
    JSONObject root = new JSONObject(json);
    JSONArray arr = root.getJSONArray("bindings");
    List<KeyBinding> result = new ArrayList<>();
    for (int i = 0; i < arr.length(); i++) {
      try {
        KeyBinding kb = parseBinding(arr.getJSONObject(i));
        if (kb != null) result.add(kb);
      } catch (Exception e) {
        android.util.Log.w("KeyBindingDefaultsLoader", "Skipping bad binding at index " + i, e);
      }
    }
    return result;
  }

  private static KeyBinding parseBinding(JSONObject obj) throws JSONException {
    String chordStr = obj.getString("chord");
    Chord chord = parseChordString(chordStr);
    if (chord == null) return null;

    BindingTarget target;
    boolean locked = obj.optBoolean("locked", false);
    String sourceCmdKey = null;

    if (obj.has("ui")) {
      String uiName = obj.getString("ui");
      UiActionId id = UiActionId.valueOf(uiName);
      target = new BindingTarget.UiAction(id);
      sourceCmdKey = uiName;
    } else if (obj.optBoolean("esc", false)) {
      target = new BindingTarget.NhKey('\033');
      sourceCmdKey = "ESC";
    } else if (obj.has("cmd")) {
      String cmd = obj.getString("cmd");
      target = BindingTarget.fromCmdKey(cmd);
      sourceCmdKey = cmd;
    } else {
      return null;
    }

    return new KeyBinding(chord, target, null, locked, sourceCmdKey);
  }

  private static Chord parseChordString(String str) {
    String[] parts = str.split("\\+");
    if (parts.length == 0) return null;

    List<Integer> codes = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      Integer code = NAME_TO_CODE.get(trimmed);
      if (code == null) {
        android.util.Log.w("KeyBindingDefaultsLoader", "Unknown button name: '" + trimmed + "'");
        return null;
      }
      codes.add(code);
    }

    // The last part is the primary; everything before are modifiers.
    int primaryCode = codes.get(codes.size() - 1);
    SortedSet<ButtonId> all = new TreeSet<>();
    for (int c : codes) all.add(new ButtonId(c));
    try {
      return new Chord(all, new ButtonId(primaryCode));
    } catch (Exception e) {
      android.util.Log.w("KeyBindingDefaultsLoader", "Invalid chord: " + str, e);
      return null;
    }
  }

  /**
   * Merge any new defaults (not previously applied) into an existing binding list. Skips defaults
   * whose chord conflicts with user bindings or whose sourceCmdKey is already present.
   */
  public static List<KeyBinding> mergeNewDefaults(
      Context ctx, String deviceKey, List<KeyBinding> existing, SharedPreferences prefs) {
    List<KeyBinding> defaults = loadDefaults(ctx, deviceKey);
    Set<String> alreadyApplied =
        new HashSet<>(prefs.getStringSet(PREF_DEFAULTS_APPLIED, Collections.<String>emptySet()));

    Set<String> existingCmdKeys = new HashSet<>();
    Set<Chord> existingChords = new HashSet<>();
    for (KeyBinding kb : existing) {
      existingChords.add(kb.chord);
      if (kb.sourceCmdKey != null) existingCmdKeys.add(kb.sourceCmdKey);
    }

    List<KeyBinding> result = new ArrayList<>(existing);
    Set<String> newApplied = new HashSet<>(alreadyApplied);

    for (KeyBinding def : defaults) {
      String key = def.sourceCmdKey;
      if (key == null) continue;
      if (alreadyApplied.contains(key)) continue;

      if (existingCmdKeys.contains(key)) {
        newApplied.add(key);
        continue;
      }
      if (existingChords.contains(def.chord)) {
        newApplied.add(key); // chord conflict; skip but mark as seen
        continue;
      }

      result.add(def);
      existingChords.add(def.chord);
      existingCmdKeys.add(key);
      newApplied.add(key);
    }

    if (!newApplied.equals(alreadyApplied)) {
      prefs.edit().putStringSet(PREF_DEFAULTS_APPLIED, newApplied).apply();
    }
    return result;
  }

  private static List<KeyBinding> hardcodedFallback() {
    List<KeyBinding> bindings = new ArrayList<>();
    // Always provide the locked Start = OPEN_DRAWER binding as a minimum
    bindings.add(
        new KeyBinding(
            Chord.single(KeyEvent.KEYCODE_BUTTON_START),
            new BindingTarget.UiAction(UiActionId.OPEN_DRAWER),
            null,
            true,
            "OPEN_DRAWER"));
    return bindings;
  }
}
