package com.tbd.forkfront;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.preference.PreferenceManager;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.appcompat.widget.AppCompatTextView;

// Used instead of TextView for monospace mode support
public class NH_TextView extends AppCompatTextView {

	private static final float MAX_SIZE_SP = 20;
	private static float DISPLAY_WIDTH_PX;
	private static float FITTED_SIZE_PX;
	private static int REVISION;

	private int mRevision;
	private Typeface mOriginalTypeface;
	private float mOriginalSizePx;
	private boolean mIsFittedMode = true;

	public NH_TextView (Context context) {
		super(context);
		init();
	}

	public NH_TextView (Context context, AttributeSet attrs) {
		super(context, attrs);
		readAttrs(context, attrs);
		init();
	}

	public NH_TextView (Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		readAttrs(context, attrs);
		init();
	}

	private void readAttrs(Context context, AttributeSet attrs) {
		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NH_TextView);
			mIsFittedMode = a.getBoolean(R.styleable.NH_TextView_useFittedSize, true);
			a.recycle();
		}
	}

	private void init() {
		mOriginalSizePx = getTextSize();
		mOriginalTypeface = getTypeface();
		if(REVISION == 0) {
			int windowWidth = WindowMetricsHelper.getWindowWidth(getContext());
			float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
			updateFontSize(windowWidth, scaledDensity);
		}
//		update();
	}

	@Override
	protected void onAttachedToWindow() {
		if(mRevision != REVISION)
			update();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		prefs.registerOnSharedPreferenceChangeListener(onPrefsChanged);

		super.onAttachedToWindow();
	}

	@Override
	protected void onDetachedFromWindow() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		prefs.unregisterOnSharedPreferenceChangeListener(onPrefsChanged);

		super.onDetachedFromWindow();
	}

	private SharedPreferences.OnSharedPreferenceChangeListener onPrefsChanged = new SharedPreferences.OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			if("monospace".equals(key)) {
				update();
			}
		}
	};

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if(mRevision != REVISION)
			update();
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		int windowWidth = WindowMetricsHelper.getWindowWidth(getContext());
		if(DISPLAY_WIDTH_PX != windowWidth) {
			float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
			updateFontSize(windowWidth, scaledDensity);
		}

		if(mRevision != REVISION)
			update();
	}

	private void update() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean isMonospaceMode = prefs.getBoolean("monospace", false);
		if(isMonospaceMode) {
			updateMode(true, Typeface.MONOSPACE, mIsFittedMode ? FITTED_SIZE_PX : mOriginalSizePx);
		} else {
			updateMode(false, mOriginalTypeface, mOriginalSizePx);
		}
		mRevision = REVISION;
	}

	private static void updateFontSize(int windowWidth, float scaledDensity) {
		DISPLAY_WIDTH_PX = windowWidth;
		REVISION++;

		Paint paint = new Paint();
		paint.setTypeface(Typeface.MONOSPACE);
		float baseSize = 30;
		paint.setTextSize(baseSize);
		String max = "01234567890123456789012345678901234567890123456789012345678901234567890123456789";
		float baseWidth = paint.measureText(max);
		float fontScale = DISPLAY_WIDTH_PX / baseWidth;
		FITTED_SIZE_PX = (float)Math.floor(baseSize * fontScale);
		float maxSizePx = MAX_SIZE_SP * scaledDensity;
		if(FITTED_SIZE_PX > maxSizePx)
			FITTED_SIZE_PX = maxSizePx;
	}

	protected void updateMode(boolean monospaceMode, Typeface typeface, float size) {
		setTypeface(typeface);
		setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
	}

	protected float getOriginalTextSize() {
		return mOriginalSizePx;
	}

	public void setBaseTextSize(int unit, float size) {
		mOriginalSizePx = TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics());
		mIsFittedMode = false;
		update();
	}
}
