package com.tbd.forkfront;

import java.util.Set;
import androidx.appcompat.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.google.android.material.slider.Slider;
import android.widget.TextView;
import com.tbd.forkfront.gamepad.GamepadDeviceWatcher;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.*;
import com.tbd.forkfront.Input.Modifier;

public class AmountSelector
{
	public interface Listener
	{
		void onDismissCount(MenuItem item, int amount);
	}

	private View mRoot;
	private TextView mAmountText;
	private int mMax;
	private MenuItem mItem;
	private Listener mListener;
	private NH_State mState;

	private AmountTuner mAmountTuner = new AmountTuner();
	
	class AmountTuner implements Runnable
	{
		private Slider mSeek;
		private View mView;
		private boolean mActive;
		private boolean mIncrease;
		long mTime;

		public void start(View v, Slider seek, boolean increase)
		{
			mView = v;
			mSeek = seek;
			mIncrease = increase;
			seek.setValue(Math.min(seek.getValueTo(), Math.max(seek.getValueFrom(), seek.getValue() + (increase ? 1 : -1))));
			mActive = true;
			mTime = System.currentTimeMillis() + 250;
			v.postDelayed(this, 250);
		}

		public void increase(View v, Slider seek)
		{
			seek.setValue(Math.min(seek.getValueTo(), seek.getValue() + 1));
		}

		public void decrease(View v, Slider seek)
		{
			seek.setValue(Math.max(seek.getValueFrom(), seek.getValue() - 1));
		}

		public void stop(View v)
		{
			if(v == this.mView)
				mActive = false;
		}

		public void run()
		{
			if(!mActive)
				return;

			long dt = (int)(System.currentTimeMillis() - mTime);
			int max = (int)mSeek.getValueTo() / 10;

			int amount = 1;
			if(dt > 700 && max > 0)
				amount = Math.min(max, (int)Math.pow(3.0, (double)dt / 700.0));

			mSeek.setValue(Math.min(mSeek.getValueTo(), Math.max(mSeek.getValueFrom(), mSeek.getValue() + (mIncrease ? amount : -amount))));
			mView.postDelayed(this, 100);
		}
	}

	// ____________________________________________________________________________________
	public AmountSelector(Listener listener, AppCompatActivity context, Tileset tileset, MenuItem item)
	{
		mItem = item;
		mListener = listener;
		mMax = item.getMaxCount();
		mState = new androidx.lifecycle.ViewModelProvider(context).get(NetHackViewModel.class).getState();

		mRoot = Util.inflate(context, R.layout.amount_selector, R.id.dlg_frame);
		ImageView tileView = (ImageView)mRoot.findViewById(R.id.amount_tile);
		final Slider seek = ((Slider)mRoot.findViewById(R.id.amount_slider));
		if(item.getTile() != 0 && tileset.hasTiles())
		{
			tileView.setVisibility(View.VISIBLE);
			tileView.setImageDrawable(new TileDrawable(tileset, item.getTile()));
		}
		else
		{
			tileView.setVisibility(View.GONE);
		}

		((TextView)mRoot.findViewById(R.id.amount_title)).setText(" " + item.getName().toString());

		mAmountText = (TextView)mRoot.findViewById(R.id.amount);
		int pad = 9;
		while(pad <= mMax)
			pad = pad * 10 + 9;
		int w = (int)Math.floor(mAmountText.getPaint().measureText(" " + Integer.toString(pad)));
		mAmountText.setWidth(w);

		seek.addOnChangeListener(new Slider.OnChangeListener()
		{
			@Override
			public void onValueChange(Slider slider, float value, boolean fromUser)
			{
				mAmountText.setText(Integer.toString((int)value));
			}
		});
		seek.setValueTo(mMax);
		seek.setValue(mMax);

		mRoot.findViewById(R.id.btn_inc).setFocusable(true);
		if(GamepadDeviceWatcher.isGamepadConnected(context))
			mRoot.findViewById(R.id.btn_inc).setFocusableInTouchMode(true);
		mRoot.findViewById(R.id.btn_inc).setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				if(event.getAction() == MotionEvent.ACTION_DOWN)
				{
					mAmountTuner.start(v, seek, true);
				}
				else if(event.getAction() == MotionEvent.ACTION_UP)
				{
					mAmountTuner.stop(v);
					v.performClick();
				}
				return true;
			}
		});

		mRoot.findViewById(R.id.btn_dec).setFocusable(true);
		if(GamepadDeviceWatcher.isGamepadConnected(context))
			mRoot.findViewById(R.id.btn_dec).setFocusableInTouchMode(true);
		mRoot.findViewById(R.id.btn_dec).setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				if(event.getAction() == MotionEvent.ACTION_DOWN)
				{
					mAmountTuner.start(v, seek, false);
				}
				else if(event.getAction() == MotionEvent.ACTION_UP)
				{
					mAmountTuner.stop(v);
					v.performClick();
				}
				return true;
			}
		});

		mRoot.findViewById(R.id.btn_0).setFocusable(true);
		if(GamepadDeviceWatcher.isGamepadConnected(context))
			mRoot.findViewById(R.id.btn_0).setFocusableInTouchMode(true);
		mRoot.findViewById(R.id.btn_0).setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				if(mRoot != null)
				{
					dismiss((int)seek.getValue());
				}
			}
		});
		mRoot.findViewById(R.id.btn_1).setFocusable(true);
		if(GamepadDeviceWatcher.isGamepadConnected(context))
			mRoot.findViewById(R.id.btn_1).setFocusableInTouchMode(true);
		mRoot.findViewById(R.id.btn_1).setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				dismiss(-1);
			}
		});

		seek.requestFocus();
		seek.requestFocusFromTouch();

		mState.getGamepadContext().pushContext(UiContext.AMOUNT_SELECTOR);
	}

	public boolean handleGamepadKey(KeyEvent ev)
	{
		if(mRoot == null || ev.getAction() != KeyEvent.ACTION_DOWN) return false;
		Slider seek = (Slider)mRoot.findViewById(R.id.amount_slider);
		switch(ev.getKeyCode())
		{
		case KeyEvent.KEYCODE_BUTTON_A:
			dismiss((int)seek.getValue());
			return true;
		case KeyEvent.KEYCODE_BUTTON_B:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
			dismiss(-1);
			return true;
		case KeyEvent.KEYCODE_BUTTON_L1:
			seek.setValue(Math.max(seek.getValueFrom(), seek.getValue() - 1));
			return true;
		case KeyEvent.KEYCODE_BUTTON_R1:
			seek.setValue(Math.min(seek.getValueTo(), seek.getValue() + 1));
			return true;
		case KeyEvent.KEYCODE_BUTTON_L2:
			seek.setValue(seek.getValueFrom());
			return true;
		case KeyEvent.KEYCODE_BUTTON_R2:
			seek.setValue(seek.getValueTo());
			return true;
		}
		return false;
	}

	public boolean handleGamepadMotion(MotionEvent ev)
	{
		return false;
	}

	// ____________________________________________________________________________________
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Modifier> modifiers, int repeatCount)
	{
		if(mRoot == null)
			return KeyEventResult.IGNORED;

		Slider seek = mRoot.findViewById(R.id.amount_slider);
		switch(keyCode)
		{
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			mAmountTuner.increase(mRoot, seek);
			return KeyEventResult.HANDLED;

		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_LEFT:
			mAmountTuner.decrease(mRoot, seek);
			return KeyEventResult.HANDLED;

		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			dismiss((int)seek.getValue());
			return KeyEventResult.HANDLED;

		case KeyEvent.KEYCODE_BACK:
			dismiss(-1);
		break;

		default:
			return KeyEventResult.RETURN_TO_SYSTEM;
		}
		return KeyEventResult.HANDLED;
	}

	public void dismiss(int amount)
	{
		if(mRoot != null)
		{
			mState.getGamepadContext().popContext(UiContext.AMOUNT_SELECTOR);
			mRoot.setVisibility(View.GONE);
			((ViewGroup)mRoot.getParent()).removeView(mRoot);
			mRoot = null;
			mListener.onDismissCount(mItem, amount);
		}
	}
}
