package com.tbd.forkfront.window.text;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.R;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.engine.NetHackIO;
import com.tbd.forkfront.window.AbstractNhWindow;
import com.tbd.forkfront.window.NH_Window;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.input.Input;

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

public class NHW_Text extends AbstractNhWindow
{
	SpannableStringBuilder mBuilder;
	private NHW_TextFragment mFragment;

	// ____________________________________________________________________________________
	public NHW_Text(int wid, AppCompatActivity context, EngineCommandSender io)
	{
		super(wid, context, io);
		mBuilder = new SpannableStringBuilder();
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return "NHW_Text";
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
		mBuilder.append(TextAttr.style(str, attr, color, mContext));
		if(mFragment != null && mFragment.isAdded())
			mFragment.updateText();
	}

	// ____________________________________________________________________________________
	@Override
	public void setCursorPos(int x, int y)
	{
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsBlocking = bBlocking;
		mIsVisible = true;
		if(mFragment == null)
			mFragment = new NHW_TextFragment(this);
		addFragment(mFragment);
		mState.getGamepadContext().pushContext(UiContext.TEXT_WINDOW);
	}

	// ____________________________________________________________________________________
	private void hide()
	{
		if (mIsVisible) {
			mState.getGamepadContext().popContext(UiContext.TEXT_WINDOW);
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
	public void close()
	{
		hide();
		super.close();
	}

	// ____________________________________________________________________________________
	protected void removeFragment()
	{
		super.removeFragment();
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
			View root = inflater.inflate(R.layout.textwindow, container, false);
			NH_TextView textView = root.findViewById(R.id.text_view);
			ScrollView scroll = textView.getParent() instanceof ScrollView ? (ScrollView) textView.getParent() : null;

			if(mParent.mBuilder.length() > 0)
				textView.setText(mParent.mBuilder);

			if (scroll != null) {
				scroll.setOnTouchListener(mTouchListener);
			}
			textView.setOnTouchListener(mTouchListener);

			View closeBtn = root.findViewById(R.id.btn_close);
			if(closeBtn != null)
			{
				closeBtn.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						mParent.close();
					}
				});
			}

			return root;
		}

		// ____________________________________________________________________________________
		void updateText()
		{
			View view = getView();
			if(view != null)
			{
				NH_TextView tv = view.findViewById(R.id.text_view);
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
			if(view != null)
			{
				final ScrollView scroll = view.findViewById(R.id.text_view).getParent() instanceof ScrollView ? (ScrollView) view.findViewById(R.id.text_view).getParent() : null;
				if (scroll != null) {
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
			ScrollView scroll = null;
			NH_TextView textView = null;
			if (view != null) {
				textView = view.findViewById(R.id.text_view);
				if (textView != null && textView.getParent() instanceof ScrollView) {
					scroll = (ScrollView) textView.getParent();
				}
			}

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
				int actionIndex = event.getActionIndex();
				int action = event.getActionMasked();

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
							v.performClick();
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
