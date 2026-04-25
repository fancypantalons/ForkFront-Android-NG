package com.tbd.forkfront;

import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.Input.Modifier;

public class NH_Question
{
	private NetHackIO mIO;
	private String mQuestion;
	private char[] mChoices;
	private int mDefIdx;
	private int mDefCh;
	private UI mUI;
	private int[] mBtns = new int[]{R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3};
	private NH_State mState;

	// ____________________________________________________________________________________
	public NH_Question(NetHackIO io, NH_State state)
	{
		mIO = io;
		mState = state;
	}

	// ____________________________________________________________________________________
	public void show(AppCompatActivity context, String question, byte[] choices, int def)
	{
		boolean alreadyShowing = isShowing();
		if(mUI != null)
			mUI.dismiss();
	
		mDefCh = def;
		mQuestion = question;
		mChoices = new char[choices.length];
		mDefIdx = 0;
		for(int i = 0; i < choices.length; i++)
		{
			mChoices[i] = (char)choices[i];
			if(mChoices[i] == def)
				mDefIdx = i;
		}

		mUI = new UI(context);
		if (!alreadyShowing) {
			mState.pushContext(UiContext.QUESTION);
		}
	}

	// ____________________________________________________________________________________
	public void setContext(AppCompatActivity context)
	{
		if(mUI != null)
			mUI = new UI(context);
	}

	// ____________________________________________________________________________________
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Modifier> modifiers, int repeatCount)
	{
		if(mUI == null)
			return KeyEventResult.IGNORED;
		return mUI.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
	}

	public boolean isShowing()
	{
		return mUI != null && mUI.mRoot != null && mUI.mRoot.getVisibility() == View.VISIBLE;
	}

	public boolean handleGamepadKey(KeyEvent ev)
	{
		return mUI != null && mUI.handleGamepadKey(ev);
	}

	public boolean handleGamepadMotion(MotionEvent ev)
	{
		return false;
	}

	// ____________________________________________________________________________________ //
	// 																						//
	// ____________________________________________________________________________________ //
	private class UI
	{
		private View mRoot;
		private boolean mIsDisabled;

		public boolean handleGamepadKey(KeyEvent ev)
		{
			if(mIsDisabled || ev.getAction() != KeyEvent.ACTION_DOWN) return false;

			switch(ev.getKeyCode())
			{
			case KeyEvent.KEYCODE_BUTTON_A:
			{
				int focused = getFocusedChoice();
				if(focused != 0) { select(focused); return true; }
				return false;
			}
			case KeyEvent.KEYCODE_BUTTON_B:
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				select(mapInput('\033'));
				return true;
			case KeyEvent.KEYCODE_BUTTON_Y:
				select(mChoices[mDefIdx]);
				return true;
			}
			return false;
		}

		private int getFocusedChoice()
		{
			try
			{
				View focus = mRoot.findFocus();
				if(focus != null)
				{
					for(int i = 0; i < mBtns.length; i++)
					{
						if(focus.getId() == mBtns[i])
							return mChoices[i];
					}
				}
			}
			catch(Exception e)
			{
			}
			return 0;
		}

		// ____________________________________________________________________________________
		private String getChoiceLabel(char choice)
		{
			switch(choice)
			{
			case 'y':
			case 'Y': return "Yes";
			case 'n':
			case 'N': return "No";
			case 'q':
			case 'Q': return "Cancel";
			case 'a':
			case 'A': return "All";
			case 'r':
			case 'R': return "Right";
			case 'l':
			case 'L': return "Left";
			case ' ': return "Continue";
			case '-': return "None";
			default: return Character.toString(choice);
			}
		}

		// ____________________________________________________________________________________
		public UI(AppCompatActivity context)
		{
			switch(mChoices.length)
			{
			case 1:
				mRoot = Util.inflate(context, R.layout.dialog_question_1, context.findViewById(R.id.dlg_frame));
			break;
			case 2:
				mRoot = Util.inflate(context, R.layout.dialog_question_2, context.findViewById(R.id.dlg_frame));
			break;
			case 3:
				mRoot = Util.inflate(context, R.layout.dialog_question_3, context.findViewById(R.id.dlg_frame));
			break;
			case 4:
				mRoot = Util.inflate(context, R.layout.dialog_question_4, context.findViewById(R.id.dlg_frame));
			break;
			}

			if(mChoices.length == 1)
			{
				Button btn = mRoot.findViewById(R.id.btn_0);
				btn.setText(getChoiceLabel(mChoices[0]));
				btn.setFocusable(true);
				btn.setFocusableInTouchMode(true);
				btn.setOnClickListener(new OnClickListener()
				{
					public void onClick(View v)
					{
						select(mChoices[0]);
					}
				});
			}
			else
			{
				for(int i = 0; i < mChoices.length; i++)
				{
					final int a = i;
					Button btn = (Button)mRoot.findViewById(mBtns[i]);
					btn.setText(getChoiceLabel(mChoices[i]));
					btn.setFocusable(true);
					btn.setFocusableInTouchMode(true);
					btn.setOnClickListener(new OnClickListener()
					{
						public void onClick(View v)
						{
							Log.print("select: " + Integer.toString(mChoices[a]));
							select(mChoices[a]);
						}
					});
					btn.setOnKeyListener(mKeyListener);
				}
			}

			((TextView)mRoot.findViewById(R.id.title)).setText(mQuestion);

			if(mDefIdx >= 0) {
				mRoot.findViewById(mBtns[mDefIdx]).requestFocus();
				maybeDisableInput();
			}
			else {
				mRoot.requestFocus();
			}

			mState.hideControls();
		}

		private void maybeDisableInput() {
			if(mChoices.length == 2 && mQuestion.startsWith("Really")) {
				mIsDisabled = true;
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						mIsDisabled = false;
					}
				}, 1000);
			}
		}

		// ____________________________________________________________________________________
		private OnKeyListener mKeyListener = new OnKeyListener()
		{
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event)
			{
				if(event.getAction() != KeyEvent.ACTION_DOWN)
					return false;

				return handleKeyDown('\0', 0, keyCode, null, 0) == KeyEventResult.HANDLED;
			}
		};

		// ____________________________________________________________________________________
		public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Modifier> modifiers, int repeatCount)
		{
			if(mRoot == null || mIsDisabled)
				return KeyEventResult.IGNORED;

			switch(keyCode)
			{
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
				return KeyEventResult.RETURN_TO_SYSTEM;
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER:
			{
				int focused = getFocusedChoice();
				if(focused != 0)
				{
					select(focused);
					return KeyEventResult.HANDLED;
				}
				return KeyEventResult.IGNORED;
			}
			case KeyEvent.KEYCODE_BACK:
				select(mapInput('\033'));
			break;

			default:
				int choice = mapInput(ch);
				if(choice != 0)
				{
					select(choice);
					return KeyEventResult.HANDLED;
				}
				return KeyEventResult.RETURN_TO_SYSTEM;
			}
			return KeyEventResult.HANDLED;
		}

		// ____________________________________________________________________________________
		public void select(int ch)
		{
			if(mRoot != null)
			{
				mIO.sendKeyCmd((char)ch);
				mState.popContext(UiContext.QUESTION);
				dismiss();
			}
		}

		// ____________________________________________________________________________________
		public void dismiss()
		{
			if(mRoot != null)
			{
				mRoot.setVisibility(View.GONE);
				((ViewGroup)mRoot.getParent()).removeView(mRoot);
				mRoot = null;
				mState.showControls();
			}
			mUI = null;
		}

		// ____________________________________________________________________________________
		private int mapInput(int ch)
		{
			switch(ch)
			{
			case ' ':
			case '\n':
			case '\r':
				return getFocusedChoice();
				
			case '\033':
				if(hasChoice('q'))
					return 'q';
				if(hasChoice('n'))
					return 'n';
				return mDefCh;
				
			default:
				if(hasChoice(ch))
					return ch;
				return 0;
			}
		}

		public boolean hasChoice(int ch)
		{
			for(char c : mChoices)
				if(c == ch)
					return true;
			return false;
		}
	}
}
