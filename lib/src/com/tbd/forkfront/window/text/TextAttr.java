package com.tbd.forkfront.window.text;
import com.tbd.forkfront.R;
import com.tbd.forkfront.ui.ThemeUtils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;

public class TextAttr
{
	public static final int ATTR_NONE = (1<<0);
	public static final int ATTR_BOLD = (1<<1);
	public static final int ATTR_DIM = (1<<2);
	public static final int ATTR_ULINE = (1<<4);
	public static final int ATTR_BLINK = (1<<5);
	public static final int ATTR_INVERSE = (1<<7);
	public static final int ATTR_UNDEFINED = (1<<8);

	private static final int[] TANGO_DARK = {
		0xFF2E3436,	// CLR_BLACK (0) - Aluminum 6
		0xFFCC0000,	// CLR_RED (1) - Scarlet Red 2
		0xFF4E9A06,	// CLR_GREEN (2) - Chameleon 3
		0xFF8F5902, // CLR_BROWN (3) - Chocolate 2
		0xFF3465A4,	// CLR_BLUE (4) - Sky Blue 3
		0xFF75507B,	// CLR_MAGENTA (5) - Plum 3
		0xFF06989A,	// CLR_CYAN (6) - Teal 2
		0xFFA3A7AF,	// CLR_GRAY (7) - Aluminum 2
		0xFFEEEEEC,	// NO_COLOR (8) - Aluminum 1 (Default white)
		0xFFF57900,	// CLR_ORANGE (9) - Orange 2
		0xFF8AE234,	// CLR_BRIGHT_GREEN (10) - Chameleon 1
		0xFFFCE94F,	// CLR_YELLOW (11) - Butter 1
		0xFF729FCF,	// CLR_BRIGHT_BLUE (12) - Sky Blue 1
		0xFFAD7FA8,	// CLR_BRIGHT_MAGENTA (13) - Plum 1
		0xFF34E2E2,	// CLR_BRIGHT_CYAN (14) - Teal 1
		0xFFFFFFFF	// CLR_WHITE (15) - Aluminum 1
	};

	private static final int[] TANGO_LIGHT = {
		0xFF2E3436,	// CLR_BLACK (0) - Aluminum 6
		0xFFCC0000,	// CLR_RED (1) - Scarlet Red 2
		0xFF4E9A06,	// CLR_GREEN (2) - Chameleon 3
		0xFF8F5902, // CLR_BROWN (3) - Chocolate 2
		0xFF204A87,	// CLR_BLUE (4) - Sky Blue 3 (Darker)
		0xFF5C3566,	// CLR_MAGENTA (5) - Plum 3 (Darker)
		0xFF06989A,	// CLR_CYAN (6) - Teal 2
		0xFF555753,	// CLR_GRAY (7) - Aluminum 4 (Darker for light bg)
		0xFF000000,	// NO_COLOR (8) - Black
		0xFFCE5C00,	// CLR_ORANGE (9) - Orange 3
		0xFF4E9A06,	// CLR_BRIGHT_GREEN (10) - Chameleon 3 (Darker)
		0xFFC4A000,	// CLR_YELLOW (11) - Butter 3 (Darker for readability)
		0xFF204A87,	// CLR_BRIGHT_BLUE (12) - Sky Blue 3 (Darker)
		0xFF5C3566,	// CLR_BRIGHT_MAGENTA (13) - Plum 3 (Darker)
		0xFF06989A,	// CLR_BRIGHT_CYAN (14) - Teal 2 (Darker)
		0xFF000000	// CLR_WHITE (15) - Black
	};

	public static Spanned style(String str, int attr) {
		return style(str, attr, (Context)null);
	}

	public static Spanned style(String str, int attr, Context context) {
		int color = Color.WHITE;
		if (context != null) {
			TypedValue typedValue = new TypedValue();
			if (context.getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue, true)) {
				color = typedValue.data;
			}
		}

		if( attr == 0 || attr == ATTR_NONE || attr == ATTR_UNDEFINED)
			return style(str, attr, color);

		if( (attr & ATTR_INVERSE) != 0 ) {
			int inverseColor = (context != null)
					? ThemeUtils.resolveColor(context, R.attr.colorPrimary, R.color.md_theme_primary)
					: 0xFFA8C7FF; // keep a sane dark-theme default for the null-context path
			return style(str, attr, inverseColor);
		}

		if( (attr & ATTR_DIM) != 0 ) {
			int dimColor = (context != null)
					? ThemeUtils.resolveColor(context, R.attr.colorOnSurfaceVariant,
					R.color.md_theme_onSurfaceVariant)
					: Color.GRAY;
			return style(str, attr, dimColor);
		}

		if( (attr & ATTR_BLINK) != 0 )
			return style(str, attr, 0xFFF88017); // INTENTIONAL: game blink color

		return style(str, attr, color);
	}

	public static Spanned style(String str, int attr, int color, Context context) {
		int finalColor = color;
		if (context != null && color >= 0 && color < 16) {
			int[] palette = getPalette(context);
			finalColor = palette[color];
		}
		return style(str, attr, finalColor);
	}

	public static Spanned style(String str, int attr, int color) {

		Spannable span = new SpannableString(str);

		if( (attr & ATTR_INVERSE) == 0 )
			span.setSpan(new ForegroundColorSpan(color), 0, str.length(), 0);
		else {
			// Headers/Inverse: use color (Primary) and force BOLD, no background span
			span.setSpan(new ForegroundColorSpan(color), 0, str.length(), 0);
			span.setSpan(new StyleSpan(Typeface.BOLD), 0, str.length(), 0);
		}

		if( (attr & ATTR_BOLD) != 0 )
			span.setSpan(new StyleSpan(Typeface.BOLD), 0, str.length(), 0);

		if( (attr & ATTR_ULINE) != 0 )
			span.setSpan(new UnderlineSpan(), 0, str.length(), 0);

		return span;
	}

	public static int[] getPalette(Context context) {
		boolean isDark = true;
		if (context != null) {
			int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
			isDark = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
		}

		return isDark ? TANGO_DARK : TANGO_LIGHT;
	}
}
