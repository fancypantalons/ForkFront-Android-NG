package com.tbd.forkfront;

import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.*;
import android.widget.ScrollView;
import android.widget.TextView;
import com.tbd.forkfront.gamepad.UiContext;

public class NHW_Text implements NH_Window
{
	private NetHackIO mIO;
	private boolean mIsBlocking;
	private boolean mIsVisible;
	SpannableStringBuilder mBuilder;
	private AppCompatActivity mContext;
	private NHW_TextFragment mFragment;
	private int mWid;
	private NH_State mState;

	// ____________________________________________________________________________________
	public NHW_Text(int wid, AppCompatActivity context, NetHackIO io)
	{
		mWid = wid;
		mIO = io;
		mBuilder = new SpannableStringBuilder();
		mContext = context;
		mState = new androidx.lifecycle.ViewModelProvider(context).get(NetHackViewModel.class).getState();
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return "NHW_Text";
	}

	// ____________________________________________________________________________________
	@Override
	public void setContext(AppCompatActivity context)
	{
		mContext = context;
	}

	// ____________________________________________________________________________________
	@Override
	public void clear()
	{
		mBuilder = new SpannableStringBuilder();
		if(mFragment != null && mFragment.isAdded())
			mFragment.updateText();
	}

	// ____________________________________________________________________________________
	@Override
	public void printString(int attr, String str, int append, int color)
	{
		if(mBuilder.length() > 0)
			mBuilder.append('\n');
		mBuilder.append(TextAttr.style(str, attr));
		if(mFragment != null && mFragment.isAdded())
			mFragment.updateText();
	}

	// ____________________________________________________________________________________
	@Override
	public void setCursorPos(int x, int y)
	{
	}

	@Override
	public void preferencesUpdated(SharedPreferences prefs) {
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsBlocking = bBlocking;
		mIsVisible = true;
		if(mFragment == null)
			mFragment = new NHW_TextFragment(this);
		if(!mFragment.isAdded())
		{
			mContext.getSupportFragmentManager()
				.beginTransaction()
				.add(R.id.window_fragment_host, mFragment, "nhw_" + mWid)
				.commit();
		}
		mState.pushContext(UiContext.TEXT_WINDOW);
	}

	// ____________________________________________________________________________________
	private void hide()
	{
		if (mIsVisible) {
			mState.popContext(UiContext.TEXT_WINDOW);
		}
		mIsVisible = false;
		removeFragment();
	}

	// ____________________________________________________________________________________
	@Override
	public void destroy()
	{
		close();
	}

	// ____________________________________________________________________________________
	@Override
	public int id()
	{
		return mWid;
	}

	@Override
	public boolean handleGamepadKey(KeyEvent ev)
	{
		return isVisible() && mFragment != null && mFragment.isAdded() && mFragment.handleGamepadKey(ev);
	}

	@Override
	public boolean handleGamepadMotion(MotionEvent ev)
	{
		return false;
	}

	@Override
	public boolean isVisible()
	{
		return mIsVisible;
	}

	// ____________________________________________________________________________________
	void close()
	{
		if (mIsVisible) {
			mState.popContext(UiContext.TEXT_WINDOW);
		}
		if(mIsBlocking)
			mIO.sendKeyCmd(' ');
		mIsBlocking = false;
		mIsVisible = false;
		removeFragment();
	}

	// ____________________________________________________________________________________
	private void removeFragment()
	{
		if(mFragment != null && mFragment.isAdded())
		{
			mContext.getSupportFragmentManager()
				.beginTransaction()
				.remove(mFragment)
				.commit();
		}
		mFragment = null;
	}

	// ____________________________________________________________________________________
	public void scrollToEnd()
	{
		if(mFragment != null && mFragment.isAdded())
			mFragment.scrollToEnd();
	}

	// ____________________________________________________________________________________
	@Override
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
	{
		if(mFragment != null && mFragment.isAdded())
			return mFragment.handleKeyDown(ch, nhKey, keyCode, modifiers);
		return KeyEventResult.IGNORED;
	}

	// ____________________________________________________________________________________ //
	// 																						//
	// ____________________________________________________________________________________ //
	public static class NHW_TextFragment extends Fragment
	{
		private final NHW_Text mParent;

		NHW_TextFragment(NHW_Text parent)
		{
			mParent = parent;
		}

		@Override
		public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
		{
			ScrollView scroll = (ScrollView) inflater.inflate(R.layout.textwindow, container, false);
			NH_TextView textView = scroll.findViewById(R.id.text_view);

			if(mParent.mBuilder.length() > 0)
				textView.setText(mParent.mBuilder);

			scroll.setOnTouchListener(mTouchListener);
			textView.setOnTouchListener(mTouchListener);

			return scroll;
		}

		// ____________________________________________________________________________________
		void updateText()
		{
			View view = getView();
			if(view instanceof ScrollView)
			{
				ScrollView scroll = (ScrollView) view;
				NH_TextView tv = scroll.findViewById(R.id.text_view);
				if(mParent.mBuilder.length() > 0)
					tv.setText(mParent.mBuilder);
				else
					tv.setText(null);
			}
		}

		// ____________________________________________________________________________________
		void scrollToEnd()
		{
			View view = getView();
			if(view instanceof ScrollView)
			{
				final ScrollView scroll = (ScrollView) view;
				scroll.post(new Runnable()
				{
					@Override
					public void run()
					{
						scroll.fullScroll(ScrollView.FOCUS_DOWN);
					}
				});
			}
		}

		// ____________________________________________________________________________________
		public boolean handleGamepadKey(KeyEvent ev)
		{
			if(ev.getAction() != KeyEvent.ACTION_DOWN) return false;
			switch(ev.getKeyCode())
			{
			case KeyEvent.KEYCODE_BUTTON_A:
			case KeyEvent.KEYCODE_BUTTON_B:
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				mParent.close();
				return true;
			}
			return false;
		}

		public boolean handleGamepadMotion(MotionEvent ev)
		{
			return false;
		}

		// ____________________________________________________________________________________
		KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers)
		{
			if(ch == '<')
				keyCode = KeyEvent.KEYCODE_PAGE_UP;
			else if(ch == '>')
				keyCode = KeyEvent.KEYCODE_PAGE_DOWN;

			View view = getView();
			ScrollView scroll = (view instanceof ScrollView) ? (ScrollView) view : null;
			NH_TextView textView = (scroll != null) ? scroll.findViewById(R.id.text_view) : null;

			switch(keyCode)
			{
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_ESCAPE:
			case KeyEvent.KEYCODE_DPAD_CENTER:
				mParent.close();
			break;

			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_VOLUME_UP:
				if(scroll != null && textView != null)
					scroll.scrollBy(0, -textView.getLineHeight());
			break;

			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if(scroll != null && textView != null)
					scroll.scrollBy(0, textView.getLineHeight());
			break;

			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_PAGE_UP:
				if(scroll != null)
					scroll.pageScroll(View.FOCUS_UP);
			break;

			case KeyEvent.KEYCODE_SPACE:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_PAGE_DOWN:
				if(scroll != null)
				{
					if(isScrolledToBottom(scroll))
						mParent.close();
					else
						scroll.pageScroll(View.FOCUS_DOWN);
				}
			break;
			}
			return KeyEventResult.HANDLED;
		}

		// ____________________________________________________________________________________
		private boolean isScrolledToBottom(ScrollView scroll)
		{
			int count = scroll.getChildCount();
			if(count > 0) {
				View view = scroll.getChildAt(count - 1);
				if(scroll.getScrollY() + scroll.getHeight() >= view.getBottom())
					return true;
			}
			return false;
		}

		// ____________________________________________________________________________________
		private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
			Integer mPointerId;
			float mPointerX, mPointerY;
			boolean mIsScrolling;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int actionIndex = (event.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
				int action = event.getAction() & MotionEvent.ACTION_MASK;

				switch(action) {
					case MotionEvent.ACTION_DOWN:
						mPointerId = event.getPointerId(actionIndex);
						mPointerX = event.getRawX();
						mPointerY = event.getRawY();
					break;

					case MotionEvent.ACTION_MOVE:
						int pointerId = event.getPointerId(actionIndex);
						if(mPointerId != null && mPointerId == pointerId) {
							float posX = event.getRawX();
							float posY = event.getRawY();

							float dx = posX - mPointerX;
							float dy = posY - mPointerY;

							float th = ViewConfiguration.get(requireContext()).getScaledTouchSlop();

							if(Math.abs(dx) > th || Math.abs(dy) > th) {
								mIsScrolling = true;
							}
						}
					break;

					case MotionEvent.ACTION_UP:
						mPointerId = null;
						if(!mIsScrolling) {
							mParent.close();
						}
						mIsScrolling = false;
					break;
				}
				return false;
			}
		};
	}
}
