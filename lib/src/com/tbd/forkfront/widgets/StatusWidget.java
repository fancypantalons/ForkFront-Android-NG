package com.tbd.forkfront.widgets;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.widget.LinearLayout;
import com.tbd.forkfront.R;
import com.tbd.forkfront.ui.AutoFitTextView;
import com.tbd.forkfront.ui.ThemeUtils;
import com.tbd.forkfront.window.message.NHW_Status;
import com.tbd.forkfront.window.text.TextAttr;
import java.util.HashMap;
import java.util.Map;

public class StatusWidget extends ControlWidget implements NHW_Status.StatusUpdateListener {
  private static final int ROW_COUNT = 3;

  private NHW_Status mStatusWindow;
  private AutoFitTextView[] mViews;
  private SpannableStringBuilder[] mRows;
  private Map<Integer, NHW_Status.StatusField> mFieldCache;
  private long mConditionMask;
  private long[] mConditionColorMasks;
  private boolean mNeedsRender;

  private static final String[] CONDITION_NAMES = {
    "Stone", "Slime", "Strngl", "FoodPois", "TermIll",
    "Blind", "Deaf", "Stun", "Conf", "Hallu",
    "Lev", "Fly", "Ride"
  };

  private static final long[] CONDITION_BITS = {
    0x00000001L, 0x00000002L, 0x00000004L, 0x00000008L,
    0x00000010L, 0x00000020L, 0x00000040L, 0x00000080L,
    0x00000100L, 0x00000200L, 0x00000400L, 0x00000800L,
    0x00001000L
  };

  public StatusWidget(Context context, NHW_Status statusWindow) {
    super(context, createStatusView(context), "status");
    android.util.Log.d("StatusWidget", "Constructor called");
    mStatusWindow = statusWindow;
    mFieldCache = new HashMap<>();
    mConditionColorMasks = new long[20];
    mRows = new SpannableStringBuilder[ROW_COUNT];
    for (int i = 0; i < ROW_COUNT; i++) {
      mRows[i] = new SpannableStringBuilder();
    }

    // Initialize view references directly
    mViews = new AutoFitTextView[ROW_COUNT];
    LinearLayout container = (LinearLayout) getContentView();
    for (int i = 0; i < ROW_COUNT; i++) {
      mViews[i] = (AutoFitTextView) container.getChildAt(i);
    }
    android.util.Log.d(
        "StatusWidget", "Views initialized: view0=" + mViews[0] + ", view1=" + mViews[1]);

    // Register as listener
    statusWindow.addListener(this);
    android.util.Log.d("StatusWidget", "Registered as listener");
  }

  private static LinearLayout createStatusView(Context context) {
    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setLayoutParams(
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    int color = ThemeUtils.resolveColor(context, R.attr.colorOnSurface, R.color.md_theme_onSurface);

    for (int i = 0; i < ROW_COUNT; i++) {
      AutoFitTextView line = new AutoFitTextView(context);
      line.setTextColor(color);
      line.setTextSize(15);
      line.setLayoutParams(
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      container.addView(line);
    }

    return container;
  }

  // StatusUpdateListener implementation

  @Override
  public void onFieldUpdated(int fieldIdx, NHW_Status.StatusField field) {
    mFieldCache.put(fieldIdx, field);
    mNeedsRender = true;
  }

  @Override
  public void onConditionsUpdated(long mask, long[] colorMasks) {
    mConditionMask = mask;
    System.arraycopy(
        colorMasks,
        0,
        mConditionColorMasks,
        0,
        Math.min(colorMasks.length, mConditionColorMasks.length));
    mNeedsRender = true;
  }

  @Override
  public void onFlush() {
    if (mNeedsRender) {
      render();
      mNeedsRender = false;
    }
  }

  @Override
  public void onReset() {
    mNeedsRender = true;
    render();
  }

  @Override
  public void setFontSize(int size) {
    super.setFontSize(size);
    if (mViews != null) {
      for (AutoFitTextView v : mViews) {
        if (v != null) {
          v.setBaseTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
        }
      }
    }
  }

  @Override
  public void setWidgetData(WidgetData data) {
    super.setWidgetData(data);
    setFontSize(data.fontSize);
  }

  private void render() {
    if (mViews == null) return;
    for (int i = 0; i < ROW_COUNT; i++) {
      if (mViews[i] == null) return;
    }

    for (int i = 0; i < ROW_COUNT; i++) {
      mRows[i] = new SpannableStringBuilder();
    }

    buildRow1();
    buildRow2();
    buildRow3();

    for (int i = 0; i < ROW_COUNT; i++) {
      mViews[i].setText(mRows[i]);
      mViews[i].requestLayout();
    }
  }

  private void buildRow1() {
    NHW_Status.StatusField title = mFieldCache.get(NHW_Status.BL_TITLE);
    if (title != null && title.enabled && !title.value.isEmpty()) {
      appendField(0, title.value + " ", title.color);
    }

    NHW_Status.StatusField align = mFieldCache.get(NHW_Status.BL_ALIGN);
    if (align != null && align.enabled && !align.value.isEmpty()) {
      appendField(0, align.value + " ", align.color);
    }

    NHW_Status.StatusField score = mFieldCache.get(NHW_Status.BL_SCORE);
    if (score != null && score.enabled && !score.value.isEmpty()) {
      appendField(0, "S:" + score.value, score.color);
    }
  }

  private void buildRow2() {
    appendStat(1, NHW_Status.BL_STR, "St:");
    appendStat(1, NHW_Status.BL_DX, "Dx:");
    appendStat(1, NHW_Status.BL_CO, "Co:");
    appendStat(1, NHW_Status.BL_IN, "In:");
    appendStat(1, NHW_Status.BL_WI, "Wi:");
    appendStat(1, NHW_Status.BL_CH, "Ch:");
  }

  private void buildRow3() {
    NHW_Status.StatusField levelDesc = mFieldCache.get(NHW_Status.BL_LEVELDESC);
    if (levelDesc != null && levelDesc.enabled && !levelDesc.value.isEmpty()) {
      appendField(2, levelDesc.value + " ", levelDesc.color);
    }

    NHW_Status.StatusField gold = mFieldCache.get(NHW_Status.BL_GOLD);
    if (gold != null && gold.enabled && !gold.value.isEmpty()) {
      appendField(2, "$:" + gold.value + " ", gold.color);
    }

    NHW_Status.StatusField hp = mFieldCache.get(NHW_Status.BL_HP);
    NHW_Status.StatusField hpmax = mFieldCache.get(NHW_Status.BL_HPMAX);
    if (hp != null && hp.enabled && hpmax != null && hpmax.enabled) {
      appendField(2, "HP:" + hp.value + "(" + hpmax.value + ") ", hp.color);
    }

    NHW_Status.StatusField ene = mFieldCache.get(NHW_Status.BL_ENE);
    NHW_Status.StatusField enemax = mFieldCache.get(NHW_Status.BL_ENEMAX);
    if (ene != null && ene.enabled && enemax != null && enemax.enabled) {
      appendField(2, "Pw:" + ene.value + "(" + enemax.value + ") ", ene.color);
    }

    NHW_Status.StatusField ac = mFieldCache.get(NHW_Status.BL_AC);
    if (ac != null && ac.enabled) {
      appendField(2, "AC:" + ac.value + " ", ac.color);
    }

    NHW_Status.StatusField xp = mFieldCache.get(NHW_Status.BL_XP);
    if (xp != null && xp.enabled) {
      String xpText = "Xp:" + xp.value;

      NHW_Status.StatusField exp = mFieldCache.get(NHW_Status.BL_EXP);
      if (exp != null && exp.enabled && !exp.value.isEmpty()) {
        xpText += "/" + exp.value;
      }

      appendField(2, xpText + " ", xp.color);
    }

    NHW_Status.StatusField time = mFieldCache.get(NHW_Status.BL_TIME);
    if (time != null && time.enabled && !time.value.isEmpty()) {
      appendField(2, "T:" + time.value + " ", time.color);
    }

    NHW_Status.StatusField hunger = mFieldCache.get(NHW_Status.BL_HUNGER);
    if (hunger != null && hunger.enabled && !hunger.value.isEmpty()) {
      appendField(2, hunger.value + " ", hunger.color);
    }

    NHW_Status.StatusField cap = mFieldCache.get(NHW_Status.BL_CAP);
    if (cap != null && cap.enabled && !cap.value.isEmpty()) {
      appendField(2, cap.value + " ", cap.color);
    }

    appendConditions(2);
  }

  private void appendStat(int row, int fieldIdx, String prefix) {
    NHW_Status.StatusField field = mFieldCache.get(fieldIdx);
    if (field != null && field.enabled && !field.value.isEmpty()) {
      appendField(row, prefix + field.value + " ", field.color);
    }
  }

  private void appendField(int row, String text, int color) {
    int attr = (color >> 8) & 0xFF;
    int nhColor = color & 0xFF;
    int rgbColor = nhColorToRGB(nhColor);
    mRows[row].append(TextAttr.style(text, attr, rgbColor));
  }

  private void appendConditions(int row) {
    // Display active conditions from bitmask
    for (int i = 0; i < CONDITION_BITS.length && i < CONDITION_NAMES.length; i++) {
      if ((mConditionMask & CONDITION_BITS[i]) != 0) {
        int color = findConditionColor(CONDITION_BITS[i]);
        appendField(row, CONDITION_NAMES[i] + " ", color);
      }
    }
  }

  private int findConditionColor(long conditionBit) {
    // Check colormasks to find the color for this condition
    for (int i = 0; i < mConditionColorMasks.length; i++) {
      if ((mConditionColorMasks[i] & conditionBit) != 0) {
        return i;
      }
    }
    return 0; // Default: no special color
  }

  private int nhColorToRGB(int nhColor) {
    int[] palette = TextAttr.getPalette(getContext());
    if (nhColor >= 0 && nhColor < palette.length) {
      return palette[nhColor];
    }
    return 0xFF000000; // Default to black
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (mStatusWindow != null) {
      mStatusWindow.removeListener(this);
    }
  }
}
