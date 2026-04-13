package com.tbd.forkfront;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;

public class StatusWidget extends ControlWidget implements NHW_Status.StatusUpdateListener
{
	private NHW_Status mStatusWindow;
	private AutoFitTextView[] mViews;
	private SpannableStringBuilder[] mRows;
	private Map<Integer, NHW_Status.StatusField> mFieldCache;
	private long mConditionMask;
	private long[] mConditionColorMasks;
	private boolean mNeedsRender;

	// NetHack color palette (matches winandroid.c palette array)
	private static final int[] NH_COLOR_PALETTE = {
		0xFF555555,	// CLR_BLACK (0)
		0xFFFF0000,	// CLR_RED (1)
		0xFF008800,	// CLR_GREEN (2)
		0xFF664411, // CLR_BROWN (3)
		0xFF0000FF,	// CLR_BLUE (4)
		0xFFFF00FF,	// CLR_MAGENTA (5)
		0xFF00FFFF,	// CLR_CYAN (6)
		0xFF888888,	// CLR_GRAY (7)
		0xFFFFFFFF,	// NO_COLOR (8)
		0xFFFF9900,	// CLR_ORANGE (9)
		0xFF00FF00,	// CLR_BRIGHT_GREEN (10)
		0xFFFFFF00,	// CLR_YELLOW (11)
		0xFF0088FF,	// CLR_BRIGHT_BLUE (12)
		0xFFFF77FF,	// CLR_BRIGHT_MAGENTA (13)
		0xFF77FFFF,	// CLR_BRIGHT_CYAN (14)
		0xFFFFFFFF	// CLR_WHITE (15)
	};

	// Condition bit names (from NHW_Status)
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

	// ____________________________________________________________________________________
	public StatusWidget(Context context, NHW_Status statusWindow)
	{
		super(context, createStatusView(context), "status");
		mStatusWindow = statusWindow;
		mFieldCache = new HashMap<>();
		mConditionColorMasks = new long[20];
		mRows = new SpannableStringBuilder[2];
		mRows[0] = new SpannableStringBuilder();
		mRows[1] = new SpannableStringBuilder();

		// Register as listener
		statusWindow.addListener(this);
	}

	// ____________________________________________________________________________________
	private static LinearLayout createStatusView(Context context)
	{
		LinearLayout container = new LinearLayout(context);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		));

		// Create two AutoFitTextViews for the two status lines
		AutoFitTextView line1 = new AutoFitTextView(context);
		line1.setTextColor(0xFFFFFFFF);
		line1.setTextSize(15);
		line1.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		));

		AutoFitTextView line2 = new AutoFitTextView(context);
		line2.setTextColor(0xFFFFFFFF);
		line2.setTextSize(15);
		line2.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		));

		container.addView(line1);
		container.addView(line2);

		return container;
	}

	// ____________________________________________________________________________________
	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();
		LinearLayout container = (LinearLayout) getContentView();
		mViews = new AutoFitTextView[2];
		mViews[0] = (AutoFitTextView) container.getChildAt(0);
		mViews[1] = (AutoFitTextView) container.getChildAt(1);
	}

	// ____________________________________________________________________________________
	// StatusUpdateListener implementation
	// ____________________________________________________________________________________

	@Override
	public void onFieldUpdated(int fieldIdx, NHW_Status.StatusField field)
	{
		mFieldCache.put(fieldIdx, field);
		mNeedsRender = true;
	}

	@Override
	public void onConditionsUpdated(long mask, long[] colorMasks)
	{
		mConditionMask = mask;
		System.arraycopy(colorMasks, 0, mConditionColorMasks, 0,
			Math.min(colorMasks.length, mConditionColorMasks.length));
		mNeedsRender = true;
	}

	@Override
	public void onFlush()
	{
		if (mNeedsRender) {
			render();
			mNeedsRender = false;
		}
	}

	@Override
	public void onReset()
	{
		mNeedsRender = true;
		render();
	}

	// ____________________________________________________________________________________
	private void render()
	{
		if (mViews == null || mViews[0] == null || mViews[1] == null) {
			return;
		}

		mRows[0] = new SpannableStringBuilder();
		mRows[1] = new SpannableStringBuilder();

		buildRow1();
		buildRow2();

		mViews[0].setText(mRows[0]);
		mViews[1].setText(mRows[1]);

		mViews[0].requestLayout();
		mViews[1].requestLayout();
	}

	// ____________________________________________________________________________________
	private void buildRow1()
	{
		// Title (role/rank)
		NHW_Status.StatusField title = mFieldCache.get(NHW_Status.BL_TITLE);
		if (title != null && title.enabled && !title.value.isEmpty()) {
			appendField(0, title.value + " ", title.color);
		}

		// Characteristics: Str, Dex, Con, Int, Wis, Cha
		appendStat(0, NHW_Status.BL_STR, "St:");
		appendStat(0, NHW_Status.BL_DX, "Dx:");
		appendStat(0, NHW_Status.BL_CO, "Co:");
		appendStat(0, NHW_Status.BL_IN, "In:");
		appendStat(0, NHW_Status.BL_WI, "Wi:");
		appendStat(0, NHW_Status.BL_CH, "Ch:");

		// Alignment
		NHW_Status.StatusField align = mFieldCache.get(NHW_Status.BL_ALIGN);
		if (align != null && align.enabled && !align.value.isEmpty()) {
			appendField(0, align.value + " ", align.color);
		}

		// Score (if enabled)
		NHW_Status.StatusField score = mFieldCache.get(NHW_Status.BL_SCORE);
		if (score != null && score.enabled && !score.value.isEmpty()) {
			appendField(0, "S:" + score.value, score.color);
		}
	}

	// ____________________________________________________________________________________
	private void buildRow2()
	{
		// Dungeon level description
		NHW_Status.StatusField levelDesc = mFieldCache.get(NHW_Status.BL_LEVELDESC);
		if (levelDesc != null && levelDesc.enabled && !levelDesc.value.isEmpty()) {
			appendField(1, levelDesc.value + " ", levelDesc.color);
		}

		// Gold
		NHW_Status.StatusField gold = mFieldCache.get(NHW_Status.BL_GOLD);
		if (gold != null && gold.enabled && !gold.value.isEmpty()) {
			appendField(1, "$:" + gold.value + " ", gold.color);
		}

		// HP
		NHW_Status.StatusField hp = mFieldCache.get(NHW_Status.BL_HP);
		NHW_Status.StatusField hpmax = mFieldCache.get(NHW_Status.BL_HPMAX);
		if (hp != null && hp.enabled && hpmax != null && hpmax.enabled) {
			appendField(1, "HP:" + hp.value + "(" + hpmax.value + ") ", hp.color);
		}

		// Power
		NHW_Status.StatusField ene = mFieldCache.get(NHW_Status.BL_ENE);
		NHW_Status.StatusField enemax = mFieldCache.get(NHW_Status.BL_ENEMAX);
		if (ene != null && ene.enabled && enemax != null && enemax.enabled) {
			appendField(1, "Pw:" + ene.value + "(" + enemax.value + ") ", ene.color);
		}

		// AC
		NHW_Status.StatusField ac = mFieldCache.get(NHW_Status.BL_AC);
		if (ac != null && ac.enabled) {
			appendField(1, "AC:" + ac.value + " ", ac.color);
		}

		// XP
		NHW_Status.StatusField xp = mFieldCache.get(NHW_Status.BL_XP);
		if (xp != null && xp.enabled) {
			String xpText = "Xp:" + xp.value;

			// Add experience points if available
			NHW_Status.StatusField exp = mFieldCache.get(NHW_Status.BL_EXP);
			if (exp != null && exp.enabled && !exp.value.isEmpty()) {
				xpText += "/" + exp.value;
			}

			appendField(1, xpText + " ", xp.color);
		}

		// Time
		NHW_Status.StatusField time = mFieldCache.get(NHW_Status.BL_TIME);
		if (time != null && time.enabled && !time.value.isEmpty()) {
			appendField(1, "T:" + time.value + " ", time.color);
		}

		// Hunger
		NHW_Status.StatusField hunger = mFieldCache.get(NHW_Status.BL_HUNGER);
		if (hunger != null && hunger.enabled && !hunger.value.isEmpty()) {
			appendField(1, hunger.value + " ", hunger.color);
		}

		// Encumbrance
		NHW_Status.StatusField cap = mFieldCache.get(NHW_Status.BL_CAP);
		if (cap != null && cap.enabled && !cap.value.isEmpty()) {
			appendField(1, cap.value + " ", cap.color);
		}

		// Conditions
		appendConditions(1);
	}

	// ____________________________________________________________________________________
	private void appendStat(int row, int fieldIdx, String prefix)
	{
		NHW_Status.StatusField field = mFieldCache.get(fieldIdx);
		if (field != null && field.enabled && !field.value.isEmpty()) {
			appendField(row, prefix + field.value + " ", field.color);
		}
	}

	// ____________________________________________________________________________________
	private void appendField(int row, String text, int color)
	{
		int attr = (color >> 8) & 0xFF;
		int nhColor = color & 0xFF;
		int rgbColor = nhColorToRGB(nhColor);
		mRows[row].append(TextAttr.style(text, attr, rgbColor));
	}

	// ____________________________________________________________________________________
	private void appendConditions(int row)
	{
		// Display active conditions from bitmask
		for (int i = 0; i < CONDITION_BITS.length && i < CONDITION_NAMES.length; i++) {
			if ((mConditionMask & CONDITION_BITS[i]) != 0) {
				int color = findConditionColor(CONDITION_BITS[i]);
				appendField(row, CONDITION_NAMES[i] + " ", color);
			}
		}
	}

	// ____________________________________________________________________________________
	private int findConditionColor(long conditionBit)
	{
		// Check colormasks to find the color for this condition
		for (int i = 0; i < mConditionColorMasks.length; i++) {
			if ((mConditionColorMasks[i] & conditionBit) != 0) {
				return i;
			}
		}
		return 0; // Default: no special color
	}

	// ____________________________________________________________________________________
	private static int nhColorToRGB(int nhColor)
	{
		if (nhColor >= 0 && nhColor < NH_COLOR_PALETTE.length) {
			return NH_COLOR_PALETTE[nhColor];
		}
		return 0xFF000000; // Default to black
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();
		if (mStatusWindow != null) {
			mStatusWindow.removeListener(this);
		}
	}
}
