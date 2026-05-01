package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.view.View;

public class NHW_Status implements NH_Window
{
	// Listener interface for status updates
	public interface StatusUpdateListener {
		void onFieldUpdated(int fieldIdx, StatusField field);
		void onConditionsUpdated(long mask, long[] colorMasks);
		void onFlush();
		void onReset();
	}

	// Field-based status storage
	private Map<Integer, StatusField> mFields;
	private long mConditionMask;
	private long[] mConditionColorMasks;
	private List<StatusUpdateListener> mListeners;

	private NetHackIO mIO;
	private UI mUI;
	private boolean mIsVisible;
	private int mWid;

	// Status field container - made public for listener access
	public static class StatusField {
		public boolean enabled;
		public String name;
		public String value;
		public int color;

		StatusField(String name) {
			this.name = name;
			this.enabled = false;
			this.value = "";
			this.color = 0;
		}

		// Copy constructor for immutable snapshots
		StatusField(StatusField other) {
			this.enabled = other.enabled;
			this.name = other.name;
			this.value = other.value;
			this.color = other.color;
		}
	}

	// Condition bit names
	private static final String[] CONDITION_NAMES = {
		"Stone", "Slime", "Strngl", "FoodPois", "TermIll",
		"Blind", "Deaf", "Stun", "Conf", "Hallu",
		"Lev", "Fly", "Ride"
	};

	private static final long[] CONDITION_BITS = {
		0x00000001L, // BL_MASK_STONE
		0x00000002L, // BL_MASK_SLIME
		0x00000004L, // BL_MASK_STRNGL
		0x00000008L, // BL_MASK_FOODPOIS
		0x00000010L, // BL_MASK_TERMILL
		0x00000020L, // BL_MASK_BLIND
		0x00000040L, // BL_MASK_DEAF
		0x00000080L, // BL_MASK_STUN
		0x00000100L, // BL_MASK_CONF
		0x00000200L, // BL_MASK_HALLU
		0x00000400L, // BL_MASK_LEV
		0x00000800L, // BL_MASK_FLY
		0x00001000L  // BL_MASK_RIDE
	};

	// Field indices (from botl.h)
	public static final int BL_TITLE = 0;
	public static final int BL_STR = 1;
	public static final int BL_DX = 2;
	public static final int BL_CO = 3;
	public static final int BL_IN = 4;
	public static final int BL_WI = 5;
	public static final int BL_CH = 6;
	public static final int BL_ALIGN = 7;
	public static final int BL_SCORE = 8;
	public static final int BL_CAP = 9;
	public static final int BL_GOLD = 10;
	public static final int BL_ENE = 11;
	public static final int BL_ENEMAX = 12;
	public static final int BL_XP = 13;
	public static final int BL_AC = 14;
	public static final int BL_HD = 15;
	public static final int BL_TIME = 16;
	public static final int BL_HUNGER = 17;
	public static final int BL_HP = 18;
	public static final int BL_HPMAX = 19;
	public static final int BL_LEVELDESC = 20;
	public static final int BL_EXP = 21;
	public static final int BL_CONDITION = 22;
	public static final int BL_FLUSH = -1;
	public static final int BL_RESET = -2;

	// ____________________________________________________________________________________
	public NHW_Status(AppCompatActivity context, NetHackIO io)
	{
		mIO = io;
		mFields = new HashMap<>();
		mConditionMask = 0;
		mConditionColorMasks = new long[20]; // BL_ATTCLR_MAX
		mListeners = new ArrayList<>();
		setContext(context);
	}

	// ____________________________________________________________________________________
	public void addListener(StatusUpdateListener listener)
	{
		if (!mListeners.contains(listener)) {
			mListeners.add(listener);
			// Send current state snapshot to new listener
			for (Map.Entry<Integer, StatusField> entry : mFields.entrySet()) {
				listener.onFieldUpdated(entry.getKey(), new StatusField(entry.getValue()));
			}
			listener.onConditionsUpdated(mConditionMask, mConditionColorMasks.clone());
			// Trigger render with current data
			listener.onFlush();
		}
	}

	// ____________________________________________________________________________________
	public void removeListener(StatusUpdateListener listener)
	{
		mListeners.remove(listener);
	}

	// ____________________________________________________________________________________
	public Map<Integer, StatusField> getFields()
	{
		return new HashMap<>(mFields);
	}

	// ____________________________________________________________________________________
	public long getConditionMask()
	{
		return mConditionMask;
	}

	// ____________________________________________________________________________________
	public long[] getConditionColorMasks()
	{
		return mConditionColorMasks.clone();
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return "NHW_Status";
	}

	// ____________________________________________________________________________________
	@Override
	public void setContext(AppCompatActivity context)
	{
		mUI = new UI(context);
		if(mIsVisible)
			mUI.showInternal();
		else
			mUI.hideInternal();
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsVisible = true;
		mUI.showInternal();
		if(bBlocking)
		{
			// unblock immediately
			mIO.sendKeyCmd(' ');
		}
	}

	// ____________________________________________________________________________________
	@Override
	public void destroy()
	{
		hide();
	}

	// ____________________________________________________________________________________
	public void hide()
	{
		mIsVisible = false;
		mUI.hideInternal();
	}

	// ____________________________________________________________________________________
	public void setId(int wid)
	{
		mWid = wid;
	}

	// ____________________________________________________________________________________
	@Override
	public int id()
	{
		return mWid;
	}

	// ____________________________________________________________________________________
	@Override
	public void clear()
	{
		mFields.clear();
		mConditionMask = 0;
		mUI.hideInternal();
	}

	// ____________________________________________________________________________________
	// Field-based status methods (called from JNI)
	// ____________________________________________________________________________________

	public void statusInit()
	{
		// Initialize field storage
		android.util.Log.d("NHW_Status", "statusInit called");
		mFields.clear();
		mConditionMask = 0;
	}

	public void statusEnableField(int fieldIdx, String name, String fmt, boolean enable)
	{
		android.util.Log.d("NHW_Status", "statusEnableField: idx=" + fieldIdx + " name=" + name + " enable=" + enable);
		if (fieldIdx < 0 || fieldIdx == BL_CONDITION) {
			return; // BL_CONDITION handled separately
		}

		StatusField field = mFields.get(fieldIdx);
		if (field == null) {
			field = new StatusField(name);
			mFields.put(fieldIdx, field);
		}
		field.enabled = enable;
		field.name = name;
	}

	public void statusUpdate(int fieldIdx, String value, long conditionMask, int chg, int percent, int color, long[] colormasks)
	{
		// android.util.Log.d("NHW_Status", "statusUpdate: idx=" + fieldIdx + " value=" + value + " condMask=" + conditionMask);
		// Handle special field indices
		if (fieldIdx == BL_FLUSH) {
			// android.util.Log.d("NHW_Status", "BL_FLUSH - rendering");
			mUI.render();
			notifyFlush();
			return;
		}

		if (fieldIdx == BL_RESET) {
			mUI.forceRedraw();
			notifyReset();
			return;
		}

		// Handle BL_CONDITION specially (it's a bitmask, not a string)
		if (fieldIdx == BL_CONDITION) {
			mConditionMask = conditionMask;
			if (colormasks != null) {
				System.arraycopy(colormasks, 0, mConditionColorMasks, 0,
					Math.min(colormasks.length, mConditionColorMasks.length));
			}
			notifyConditionsUpdated();
			return;
		}

		// Regular field update
		StatusField field = mFields.get(fieldIdx);
		if (field == null) {
			field = new StatusField("Field" + fieldIdx);
			mFields.put(fieldIdx, field);
		}

		// Handle BL_GOLD special format ($:123) - strip the "$:" prefix
		if (fieldIdx == BL_GOLD && value != null && value.startsWith("$:")) {
			field.value = value.substring(2);
		} else {
			field.value = value != null ? value : "";
		}

		field.color = color;
		notifyFieldUpdated(fieldIdx, field);
	}

	// ____________________________________________________________________________________
	private void notifyFieldUpdated(int fieldIdx, StatusField field)
	{
		StatusField copy = new StatusField(field);
		for (StatusUpdateListener listener : mListeners) {
			listener.onFieldUpdated(fieldIdx, copy);
		}
	}

	// ____________________________________________________________________________________
	private void notifyConditionsUpdated()
	{
		long maskCopy = mConditionMask;
		long[] colorsCopy = mConditionColorMasks.clone();
		for (StatusUpdateListener listener : mListeners) {
			listener.onConditionsUpdated(maskCopy, colorsCopy);
		}
	}

	// ____________________________________________________________________________________
	private void notifyFlush()
	{
		for (StatusUpdateListener listener : mListeners) {
			listener.onFlush();
		}
	}

	// ____________________________________________________________________________________
	private void notifyReset()
	{
		for (StatusUpdateListener listener : mListeners) {
			listener.onReset();
		}
	}

	public void statusFinish()
	{
		mFields.clear();
	}

	// ____________________________________________________________________________________
	// Legacy NH_Window interface - not used in field-based mode
	// ____________________________________________________________________________________

	@Override
	public void printString(int attr, String str, int append, int color)
	{
		// Not used in field-based status mode
	}

	public void redraw() {
		mUI.render();
	}

	@Override
	public void setCursorPos(int x, int y)
	{
	}

	@Override
	public boolean isVisible()
	{
		return true;
	}

	@Override
	public boolean handleGamepadKey(android.view.KeyEvent ev)
	{
		return false;
	}

	@Override
	public boolean handleGamepadMotion(android.view.MotionEvent ev)
	{
		return false;
	}

	// ____________________________________________________________________________________
	@Override
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
	{
		return KeyEventResult.IGNORED;
	}

	// ____________________________________________________________________________________
	public float getHeight()
	{
		return mUI.getHeight();
	}

	// ____________________________________________________________________________________
	@Override
	public void preferencesUpdated(SharedPreferences prefs)
	{
	}

	// ____________________________________________________________________________________ //
	// NetHack color palette (matches winandroid.c palette array)
	// ____________________________________________________________________________________ //
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

	// Convert NetHack color code (0-15) to Android RGB color
	private static int nhColorToRGB(int nhColor) {
		if (nhColor >= 0 && nhColor < NH_COLOR_PALETTE.length) {
			return NH_COLOR_PALETTE[nhColor];
		}
		return 0xFF000000; // Default to black
	}

	// ____________________________________________________________________________________ //
	// 																						//
	// ____________________________________________________________________________________ //
	private class UI
	{
		private AutoFitTextView[] mViews;
		private SpannableStringBuilder[] mRows;

		// ____________________________________________________________________________________
		public UI(AppCompatActivity context)
		{
			mViews = new AutoFitTextView[2];
			mViews[0] = (AutoFitTextView)context.findViewById(R.id.nh_stat0);
			mViews[1] = (AutoFitTextView)context.findViewById(R.id.nh_stat1);
			mRows = new SpannableStringBuilder[2];
			mRows[0] = new SpannableStringBuilder();
			mRows[1] = new SpannableStringBuilder();

			// Set text color
			mViews[0].setTextColor(0xFFFFFFFF); // White
			mViews[1].setTextColor(0xFFFFFFFF); // White
		}

		// ____________________________________________________________________________________
		public void showInternal()
		{
			mViews[0].setVisibility(View.VISIBLE);
			mViews[1].setVisibility(View.VISIBLE);
		}

		// ____________________________________________________________________________________
		public void hideInternal()
		{
			mViews[0].setVisibility(View.GONE);
			mViews[1].setVisibility(View.GONE);
		}

		// ____________________________________________________________________________________
		public void render()
		{
			mRows[0] = new SpannableStringBuilder();
			mRows[1] = new SpannableStringBuilder();

			// Build first row: Title, stats, alignment, score
			buildRow1();

			// Build second row: Dungeon level, gold, HP, Pw, AC, XP, Time, Hunger, Conditions
			buildRow2();

			mViews[0].setText(mRows[0]);
			mViews[1].setText(mRows[1]);

			// Force layout update
			mViews[0].requestLayout();
			mViews[1].requestLayout();
		}

		// ____________________________________________________________________________________
		private void buildRow1()
		{
			// Title (role/rank)
			StatusField title = mFields.get(BL_TITLE);
			if (title != null && title.enabled && !title.value.isEmpty()) {
				appendField(0, title.value + " ", title.color);
			}

			// Characteristics: Str, Dex, Con, Int, Wis, Cha
			appendStat(0, BL_STR, "St:");
			appendStat(0, BL_DX, "Dx:");
			appendStat(0, BL_CO, "Co:");
			appendStat(0, BL_IN, "In:");
			appendStat(0, BL_WI, "Wi:");
			appendStat(0, BL_CH, "Ch:");

			// Alignment
			StatusField align = mFields.get(BL_ALIGN);
			if (align != null && align.enabled) {
				appendField(0, align.value + " ", align.color);
			}

			// Score
			StatusField score = mFields.get(BL_SCORE);
			if (score != null && score.enabled) {
				appendField(0, "S:" + score.value + " ", score.color);
			}
		}

		// ____________________________________________________________________________________
		private void buildRow2()
		{
			// Dungeon level (Level 1)
			StatusField dlvl = mFields.get(BL_LEVELDESC);
			if (dlvl != null && dlvl.enabled) {
				appendField(1, dlvl.value + " ", dlvl.color);
			}

			// Gold ($:123)
			StatusField gold = mFields.get(BL_GOLD);
			if (gold != null && gold.enabled) {
				appendField(1, "$:" + gold.value + " ", gold.color);
			}

			// HP: current/max
			StatusField hp = mFields.get(BL_HP);
			StatusField hpmax = mFields.get(BL_HPMAX);
			if (hp != null && hp.enabled && hpmax != null && hpmax.enabled) {
				appendField(1, "HP:" + hp.value + "(" + hpmax.value + ") ", hp.color);
			}

			// Pw: current/max (Power/Energy)
			StatusField pw = mFields.get(BL_ENE);
			StatusField pwmax = mFields.get(BL_ENEMAX);
			if (pw != null && pw.enabled && pwmax != null && pwmax.enabled) {
				appendField(1, "Pw:" + pw.value + "(" + pwmax.value + ") ", pw.color);
			}

			// AC
			StatusField ac = mFields.get(BL_AC);
			if (ac != null && ac.enabled) {
				appendField(1, "AC:" + ac.value + " ", ac.color);
			}

			// XP or HD
			StatusField exp = mFields.get(BL_EXP); // Experience LEVEL
			StatusField xp = mFields.get(BL_XP);   // Total XP points
			if (exp != null && exp.enabled) {
				String xpVal = xp != null && xp.enabled ? "/" + xp.value : "";
				appendField(1, "Xp:" + exp.value + xpVal + " ", exp.color);
			}

			// Time (optional)
			StatusField time = mFields.get(BL_TIME);
			if (time != null && time.enabled) {
				appendField(1, "T:" + time.value + " ", time.color);
			}

			// Hunger
			StatusField hunger = mFields.get(BL_HUNGER);
			if (hunger != null && hunger.enabled && !hunger.value.isEmpty()) {
				appendField(1, hunger.value + " ", hunger.color);
			}

			// Conditions (Blind, Stun, etc)
			for (int i = 0; i < CONDITION_BITS.length; i++) {
				if ((mConditionMask & CONDITION_BITS[i]) != 0) {
					int nhColor = (int)mConditionColorMasks[i];
					appendField(1, CONDITION_NAMES[i] + " ", nhColor);
				}
			}
		}

		// Helper to append a characteristic with label
		private void appendStat(int row, int fieldIdx, String label)
		{
			StatusField field = mFields.get(fieldIdx);
			if (field != null && field.enabled) {
				appendField(row, label + field.value + " ", field.color);
			}
		}

		// Append styled text to row
		private void appendField(int row, String text, int nhColor)
		{
			int start = mRows[row].length();
			mRows[row].append(text);
			int end = mRows[row].length();

			// Apply color styling
			int rgb = nhColorToRGB(nhColor);
			mRows[row].setSpan(new android.text.style.ForegroundColorSpan(rgb), start, end, 0);
		}

		public void forceRedraw()
		{
			render();
		}

		public float getHeight()
		{
			return mViews[0].getHeight() + mViews[1].getHeight();
		}
	}
}
