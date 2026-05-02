package com.tbd.forkfront.gamepad;

import android.view.MotionEvent;

/**
 * Converts analog axis events (triggers, HAT, sticks) into synthetic button press/release events
 * using threshold detection and hysteresis.
 */
public class AxisNormalizer {
  private static final float TRIGGER_PRESS_THRESHOLD = 0.5f;
  private static final float TRIGGER_RELEASE_THRESHOLD = 0.3f;
  private static final float STICK_DEADZONE = 0.3f;

  // Left-stick magnitude hysteresis: different enter/exit thresholds.
  private static final float LSTICK_ENTER_MAG = 0.40f;
  private static final float LSTICK_EXIT_MAG = 0.25f;

  // Left-stick octant hysteresis: must push this many radians (7.5°) past the
  // nearest octant boundary before the current octant changes.
  private static final double LSTICK_OCTANT_HYSTERESIS = Math.PI / 24.0;

  private boolean mLTriggerDown = false;
  private boolean mRTriggerDown = false;

  // Left stick: -1 = center/deadzone; 0..7 = N, NE, E, SE, S, SW, W, NW
  private int mLStickOctant = -1;
  private boolean mLStickMagActive = false;

  // HAT axis state
  private float mLastHatX = 0;
  private float mLastHatY = 0;

  public interface EventSink {
    void onSynthPress(int buttonCode);

    void onSynthRelease(int buttonCode);

    void onRightStickMoved(float x, float y);
  }

  private final GamepadOptions mOpts;

  public AxisNormalizer(GamepadOptions opts) {
    this.mOpts = opts;
  }

  public void handleGenericMotion(MotionEvent ev, EventSink sink) {
    processTriggers(ev, sink);
    processHat(ev, sink);
    if (mOpts.leftStickMovement) {
      processLeftStick(ev, sink);
    }
    processRightStick(ev, sink);
  }

  private void processTriggers(MotionEvent ev, EventSink sink) {
    float lt = ev.getAxisValue(MotionEvent.AXIS_LTRIGGER);
    if (lt == 0) lt = ev.getAxisValue(MotionEvent.AXIS_BRAKE);
    boolean ltNow =
        lt >= TRIGGER_PRESS_THRESHOLD || (mLTriggerDown && lt > TRIGGER_RELEASE_THRESHOLD);
    if (!mLTriggerDown && ltNow) sink.onSynthPress(ButtonId.AXIS_LTRIGGER_PSEUDO);
    else if (mLTriggerDown && !ltNow) sink.onSynthRelease(ButtonId.AXIS_LTRIGGER_PSEUDO);
    mLTriggerDown = ltNow;

    float rt = ev.getAxisValue(MotionEvent.AXIS_RTRIGGER);
    if (rt == 0) rt = ev.getAxisValue(MotionEvent.AXIS_GAS);
    boolean rtNow =
        rt >= TRIGGER_PRESS_THRESHOLD || (mRTriggerDown && rt > TRIGGER_RELEASE_THRESHOLD);
    if (!mRTriggerDown && rtNow) sink.onSynthPress(ButtonId.AXIS_RTRIGGER_PSEUDO);
    else if (mRTriggerDown && !rtNow) sink.onSynthRelease(ButtonId.AXIS_RTRIGGER_PSEUDO);
    mRTriggerDown = rtNow;
  }

  private static int classifyHat(float v) {
    return v < -0.5f ? -1 : v > 0.5f ? 1 : 0;
  }

  private void processHat(MotionEvent ev, EventSink sink) {
    float hatX = ev.getAxisValue(MotionEvent.AXIS_HAT_X);
    float hatY = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
    if (hatX == mLastHatX && hatY == mLastHatY) return;

    // Only emit events for axes whose state class actually changed.
    int oldX = classifyHat(mLastHatX);
    int newX = classifyHat(hatX);
    if (oldX != newX) {
      if (oldX == -1) sink.onSynthRelease(ButtonId.AXIS_HAT_LEFT_PSEUDO);
      else if (oldX == 1) sink.onSynthRelease(ButtonId.AXIS_HAT_RIGHT_PSEUDO);
      if (newX == -1) sink.onSynthPress(ButtonId.AXIS_HAT_LEFT_PSEUDO);
      else if (newX == 1) sink.onSynthPress(ButtonId.AXIS_HAT_RIGHT_PSEUDO);
    }

    int oldY = classifyHat(mLastHatY);
    int newY = classifyHat(hatY);
    if (oldY != newY) {
      if (oldY == -1) sink.onSynthRelease(ButtonId.AXIS_HAT_UP_PSEUDO);
      else if (oldY == 1) sink.onSynthRelease(ButtonId.AXIS_HAT_DOWN_PSEUDO);
      if (newY == -1) sink.onSynthPress(ButtonId.AXIS_HAT_UP_PSEUDO);
      else if (newY == 1) sink.onSynthPress(ButtonId.AXIS_HAT_DOWN_PSEUDO);
    }

    mLastHatX = hatX;
    mLastHatY = hatY;
  }

  private void processLeftStick(MotionEvent ev, EventSink sink) {
    float x = ev.getAxisValue(MotionEvent.AXIS_X);
    float y = ev.getAxisValue(MotionEvent.AXIS_Y);
    float mag = (float) Math.sqrt(x * x + y * y);

    // Magnitude hysteresis: different enter/exit thresholds to prevent deadzone-edge toggling.
    if (!mLStickMagActive) {
      if (mag < LSTICK_ENTER_MAG) {
        // Still in deadzone — emit release if we somehow had an octant.
        if (mLStickOctant >= 0) {
          sink.onSynthRelease(lStickOctantToPseudo(mLStickOctant));
          mLStickOctant = -1;
        }
        return;
      }
      mLStickMagActive = true;
    } else if (mag < LSTICK_EXIT_MAG) {
      mLStickMagActive = false;
      if (mLStickOctant >= 0) {
        sink.onSynthRelease(lStickOctantToPseudo(mLStickOctant));
        mLStickOctant = -1;
      }
      return;
    }

    // atan2(x, -y) gives angle from North (0), clockwise to East (PI/2), etc.
    double angle = Math.atan2(x, -y);
    if (angle < 0) angle += 2 * Math.PI;

    // Octant with angular hysteresis: the current octant is sticky — we only leave it
    // when the angle has moved LSTICK_OCTANT_HYSTERESIS past the nearest boundary.
    int newOctant;
    if (mLStickOctant < 0) {
      // No current octant: snap to nearest.
      newOctant = (int) Math.round(angle / (Math.PI / 4)) % 8;
    } else {
      double currentCenter = mLStickOctant * (Math.PI / 4);
      double distFromCenter = angleDiff(angle, currentCenter);
      // Standard boundary is at PI/8 (22.5°); with hysteresis we require PI/8 + H.
      if (distFromCenter > (Math.PI / 8) + LSTICK_OCTANT_HYSTERESIS) {
        newOctant = (int) Math.round(angle / (Math.PI / 4)) % 8;
      } else {
        newOctant = mLStickOctant; // stay
      }
    }

    if (newOctant == mLStickOctant) return;

    if (mLStickOctant >= 0) {
      sink.onSynthRelease(lStickOctantToPseudo(mLStickOctant));
    }
    mLStickOctant = newOctant;
    if (mLStickOctant >= 0) {
      sink.onSynthPress(lStickOctantToPseudo(mLStickOctant));
    }
  }

  private static double angleDiff(double a, double b) {
    double d = Math.abs(a - b);
    return d > Math.PI ? 2 * Math.PI - d : d;
  }

  private void processRightStick(MotionEvent ev, EventSink sink) {
    float rx = ev.getAxisValue(MotionEvent.AXIS_Z);
    float ry = ev.getAxisValue(MotionEvent.AXIS_RZ);
    float mag = (float) Math.sqrt(rx * rx + ry * ry);
    if (mag >= STICK_DEADZONE) {
      sink.onRightStickMoved(rx, ry);
    } else {
      sink.onRightStickMoved(0, 0);
    }
  }

  private static int lStickOctantToPseudo(int octant) {
    switch (octant) {
      case 0:
        return ButtonId.LSTICK_UP_PSEUDO;
      case 1:
        return ButtonId.LSTICK_UR_PSEUDO;
      case 2:
        return ButtonId.LSTICK_RIGHT_PSEUDO;
      case 3:
        return ButtonId.LSTICK_DR_PSEUDO;
      case 4:
        return ButtonId.LSTICK_DOWN_PSEUDO;
      case 5:
        return ButtonId.LSTICK_DL_PSEUDO;
      case 6:
        return ButtonId.LSTICK_LEFT_PSEUDO;
      case 7:
        return ButtonId.LSTICK_UL_PSEUDO;
      default:
        return ButtonId.LSTICK_UP_PSEUDO;
    }
  }

  /** Convert a LSTICK_*_PSEUDO code to the corresponding NetHack direction character. */
  public static char pseudoToNhDir(int pseudoCode) {
    switch (pseudoCode) {
      case ButtonId.LSTICK_UP_PSEUDO:
        return 'k';
      case ButtonId.LSTICK_DOWN_PSEUDO:
        return 'j';
      case ButtonId.LSTICK_LEFT_PSEUDO:
        return 'h';
      case ButtonId.LSTICK_RIGHT_PSEUDO:
        return 'l';
      case ButtonId.LSTICK_UL_PSEUDO:
        return 'y';
      case ButtonId.LSTICK_UR_PSEUDO:
        return 'u';
      case ButtonId.LSTICK_DL_PSEUDO:
        return 'b';
      case ButtonId.LSTICK_DR_PSEUDO:
        return 'n';
      default:
        return 0;
    }
  }

  public static boolean isLStickPseudo(int code) {
    return code >= ButtonId.LSTICK_UP_PSEUDO && code <= ButtonId.LSTICK_DR_PSEUDO;
  }

  public static boolean isHatPseudo(int code) {
    return code >= ButtonId.AXIS_HAT_LEFT_PSEUDO && code <= ButtonId.AXIS_HAT_DOWN_PSEUDO;
  }

  /** Convert a HAT pseudo-code to the equivalent DPAD keycode. */
  public static int hatPseudoToDpad(int pseudo) {
    switch (pseudo) {
      case ButtonId.AXIS_HAT_LEFT_PSEUDO:
        return android.view.KeyEvent.KEYCODE_DPAD_LEFT;
      case ButtonId.AXIS_HAT_RIGHT_PSEUDO:
        return android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
      case ButtonId.AXIS_HAT_UP_PSEUDO:
        return android.view.KeyEvent.KEYCODE_DPAD_UP;
      case ButtonId.AXIS_HAT_DOWN_PSEUDO:
        return android.view.KeyEvent.KEYCODE_DPAD_DOWN;
      default:
        return -1;
    }
  }

  /** Reset all axis state. Call from onPause / device removal. */
  public void reset(EventSink sink) {
    if (mLTriggerDown) {
      sink.onSynthRelease(ButtonId.AXIS_LTRIGGER_PSEUDO);
      mLTriggerDown = false;
    }
    if (mRTriggerDown) {
      sink.onSynthRelease(ButtonId.AXIS_RTRIGGER_PSEUDO);
      mRTriggerDown = false;
    }
    if (mLStickOctant >= 0) {
      sink.onSynthRelease(lStickOctantToPseudo(mLStickOctant));
      mLStickOctant = -1;
    }
    mLStickMagActive = false;
    mLastHatX = 0;
    mLastHatY = 0;
  }

  public void reset() {
    mLTriggerDown = false;
    mRTriggerDown = false;
    mLStickOctant = -1;
    mLStickMagActive = false;
    mLastHatX = 0;
    mLastHatY = 0;
  }
}
