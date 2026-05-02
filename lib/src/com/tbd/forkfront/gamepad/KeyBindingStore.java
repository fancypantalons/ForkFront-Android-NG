package com.tbd.forkfront.gamepad;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads and writes the binding list to SharedPreferences as JSON. Key: "gamepad_bindings_v1" */
public class KeyBindingStore {
  private static final String PREF_KEY = "gamepad_bindings_v1";
  private static final int CURRENT_VERSION = 1;

  private KeyBindingStore() {}

  public static void save(SharedPreferences prefs, List<KeyBinding> bindings, String profile) {
    try {
      JSONObject root = new JSONObject();
      root.put("version", CURRENT_VERSION);
      root.put("profile", profile != null ? profile : "generic");

      JSONArray arr = new JSONArray();
      for (KeyBinding kb : bindings) {
        arr.put(bindingToJson(kb));
      }
      root.put("bindings", arr);
      prefs.edit().putString(PREF_KEY, root.toString()).apply();
    } catch (JSONException e) {
      android.util.Log.e("KeyBindingStore", "Failed to save bindings", e);
    }
  }

  /**
   * @return binding list, or null if not yet saved.
   */
  public static List<KeyBinding> load(SharedPreferences prefs) {
    String json = prefs.getString(PREF_KEY, null);
    if (json == null) return null;
    try {
      JSONObject root = new JSONObject(json);
      // Migration hook: if version < CURRENT_VERSION, run migrator here.
      JSONArray arr = root.getJSONArray("bindings");
      List<KeyBinding> result = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
        KeyBinding kb = bindingFromJson(arr.getJSONObject(i));
        if (kb != null) result.add(kb);
      }
      return result;
    } catch (Exception e) {
      android.util.Log.e("KeyBindingStore", "Failed to load bindings; clearing", e);
      prefs.edit().remove(PREF_KEY).apply();
      return null;
    }
  }

  public static boolean hasBindings(SharedPreferences prefs) {
    return prefs.contains(PREF_KEY);
  }

  public static void clear(SharedPreferences prefs) {
    prefs.edit().remove(PREF_KEY).apply();
  }

  private static JSONObject bindingToJson(KeyBinding kb) throws JSONException {
    JSONObject obj = new JSONObject();
    obj.put("chord", chordToJson(kb.chord));
    obj.put("target", targetToJson(kb.target));
    if (kb.sourceCmdKey != null) obj.put("sourceCmdKey", kb.sourceCmdKey);
    obj.put("locked", kb.locked);
    if (kb.label != null) obj.put("label", kb.label);
    return obj;
  }

  private static JSONObject chordToJson(Chord chord) throws JSONException {
    JSONObject obj = new JSONObject();
    JSONArray mods = new JSONArray();
    for (ButtonId mod : chord.modifiers()) {
      mods.put(mod.code);
    }
    obj.put("modifiers", mods);
    obj.put("primary", chord.primary.code);
    return obj;
  }

  private static JSONObject targetToJson(BindingTarget target) throws JSONException {
    JSONObject obj = new JSONObject();
    switch (target.kind()) {
      case NH_KEY:
        obj.put("kind", "NH_KEY");
        obj.put("ch", (int) ((BindingTarget.NhKey) target).ch);
        break;
      case NH_STRING:
        obj.put("kind", "NH_STRING");
        obj.put("line", ((BindingTarget.NhString) target).line);
        break;
      case UI_ACTION:
        obj.put("kind", "UI_ACTION");
        obj.put("id", ((BindingTarget.UiAction) target).id.name());
        break;
    }
    return obj;
  }

  private static KeyBinding bindingFromJson(JSONObject obj) throws JSONException {
    Chord chord = chordFromJson(obj.getJSONObject("chord"));
    if (chord == null) return null;

    BindingTarget target = targetFromJson(obj.getJSONObject("target"));
    if (target == null) return null;

    String sourceCmdKey = obj.has("sourceCmdKey") ? obj.getString("sourceCmdKey") : null;
    boolean locked = obj.optBoolean("locked", false);
    String label = obj.has("label") ? obj.getString("label") : null;

    return new KeyBinding(chord, target, label, locked, sourceCmdKey);
  }

  private static Chord chordFromJson(JSONObject obj) throws JSONException {
    int primaryCode = obj.getInt("primary");
    JSONArray modsArr = obj.optJSONArray("modifiers");

    SortedSet<ButtonId> all = new TreeSet<>();
    ButtonId primary = new ButtonId(primaryCode);
    all.add(primary);
    if (modsArr != null) {
      for (int i = 0; i < modsArr.length(); i++) {
        all.add(new ButtonId(modsArr.getInt(i)));
      }
    }
    try {
      return new Chord(all, primary);
    } catch (Exception e) {
      android.util.Log.w("KeyBindingStore", "Bad chord in JSON", e);
      return null;
    }
  }

  private static BindingTarget targetFromJson(JSONObject obj) {
    try {
      String kind = obj.getString("kind");
      switch (kind) {
        case "NH_KEY":
          {
            int ch = obj.getInt("ch");
            return new BindingTarget.NhKey((char) ch);
          }
        case "NH_STRING":
          return new BindingTarget.NhString(obj.getString("line"));
        case "UI_ACTION":
          {
            UiActionId id = UiActionId.valueOf(obj.getString("id"));
            return new BindingTarget.UiAction(id);
          }
        default:
          android.util.Log.w("KeyBindingStore", "Unknown target kind: " + kind);
          return null;
      }
    } catch (Exception e) {
      android.util.Log.w("KeyBindingStore", "Failed to parse target", e);
      return null;
    }
  }
}
