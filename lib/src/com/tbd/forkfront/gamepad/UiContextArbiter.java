package com.tbd.forkfront.gamepad;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Stack-based context arbiter.
 *
 * <p>The bottom of the stack is always GAMEPLAY. Explicit pushes/pops handle modal UI. Two implicit
 * overrides are applied at read-time when top == GAMEPLAY: - expectsDirection() == true →
 * DIRECTION_PROMPT - isMouseLocked() == true → MAP_CURSOR
 */
public final class UiContextArbiter {

  public interface ContextOverrideQuery {
    boolean expectsDirection();

    boolean isMouseLocked();
  }

  public interface Listener {
    void onContextChanged(UiContext newContext);
  }

  private final Deque<UiContext> mStack = new ArrayDeque<>();
  private final ContextOverrideQuery mQuery;
  private final List<Listener> mListeners = new ArrayList<>();

  public UiContextArbiter(ContextOverrideQuery query) {
    this.mQuery = query;
    mStack.push(UiContext.GAMEPLAY); // base context; never popped
  }

  public void push(UiContext c) {
    mStack.push(c);
    notifyListeners();
  }

  public void pushUnique(UiContext c) {
    if (!mStack.contains(c)) {
      push(c);
    }
  }

  public boolean contains(UiContext c) {
    return mStack.contains(c);
  }

  /** Pop only if the top matches the expected context. Logs a mismatch and no-ops otherwise. */
  public void pop(UiContext expected) {
    UiContext top = mStack.isEmpty() ? null : mStack.peek();
    if (top == expected) {
      mStack.pop();
      // Ensure base GAMEPLAY is always present
      if (mStack.isEmpty()) mStack.push(UiContext.GAMEPLAY);
      notifyListeners();
    } else {
      android.util.Log.w(
          "UiContextArbiter", "pop(" + expected + ") called but top is " + top + " — ignoring");
    }
  }

  /**
   * Removes specific context from anywhere in the stack. Used for out-of-order cleanup (e.g.
   * Settings vs Drawer).
   */
  public void remove(UiContext target) {
    if (mStack.remove(target)) {
      if (mStack.isEmpty()) mStack.push(UiContext.GAMEPLAY);
      notifyListeners();
    }
  }

  /** Return the effective current context including implicit overrides. */
  public UiContext current() {
    UiContext top = mStack.isEmpty() ? UiContext.GAMEPLAY : mStack.peek();
    if (top == UiContext.GAMEPLAY && mQuery != null) {
      if (mQuery.expectsDirection()) return UiContext.DIRECTION_PROMPT;
      if (mQuery.isMouseLocked()) return UiContext.MAP_CURSOR;
    }
    return top;
  }

  public void addListener(Listener l) {
    mListeners.add(l);
  }

  public void removeListener(Listener l) {
    mListeners.remove(l);
  }

  private void notifyListeners() {
    UiContext ctx = current();
    for (int i = 0, n = mListeners.size(); i < n; i++) {
      mListeners.get(i).onContextChanged(ctx);
    }
  }
}
