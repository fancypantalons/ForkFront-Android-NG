package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import androidx.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.*;


public class NH_GetLine
{
	private static final int MAX_HISTORY = 10;
	private UI mUI;
	private NetHackIO mIO;
	private String mTitle;
	private int mMaxChars;
	private NH_State mState;
	private Context mContext;
	private List<String> mHistory;

	// ____________________________________________________________________________________
	public NH_GetLine(NetHackIO io, NH_State state)
	{
		mIO = io;
		mState = state;
	}

	// ____________________________________________________________________________________
	public void show(AppCompatActivity context, final String title, final int nMaxChars)
	{
		mContext = context;
		mTitle = title;
		mMaxChars = nMaxChars;
		mHistory = loadHistory();
		mUI = new UI(context, true, true, getInitText());
		mState.pushContext(UiContext.GETLINE);
	}
	
	// ____________________________________________________________________________________
	public void setContext(AppCompatActivity context)
	{
		mContext = context;
		if(mUI != null)
			mUI = new UI(context, mUI.mSaveHistory, mUI.mSaveHistory, getInitText());
	}

	private String getInitText()
	{
		if(mTitle.contains("For what do you wish"))
			return "";
		if(mTitle.startsWith("Replace annotation \""))
		{
			int i = mTitle.lastIndexOf('"');
			if(i > 19)
				return mTitle.substring(20, i);
		}
		if(mTitle.startsWith("Replace previous annotation \""))
		{
			int i = mTitle.lastIndexOf('"');
			if(i > 28)
				return mTitle.substring(29, i);
		}
		if(mTitle.startsWith("What do you want to call") || mTitle.startsWith("Call ")) {
			int i = mTitle.indexOf(" called ");
			if(i > 0)
				return mTitle.substring(i+8, mTitle.length() - 1);
		}
		if(mTitle.startsWith("What do you want to name")) {
			int i = mTitle.indexOf(" named ");
			if(i > 0)
				return mTitle.substring(i+7, mTitle.length() - 1);
		}
		if(mHistory.size() > 0)
			return mHistory.get(0);
		return "";
	}

	// ____________________________________________________________________________________
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
	{
		if(mUI == null)
			return KeyEventResult.IGNORED;
		return mUI.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
	}

	public boolean isFocused()
	{
		return mUI != null && mUI.mRoot != null && mUI.mInput != null && mUI.mInput.isFocused();
	}

	public boolean handleGamepadKey(KeyEvent ev)
	{
		return mUI != null && mUI.handleGamepadKey(ev);
	}

	public boolean handleGamepadMotion(MotionEvent ev)
	{
		return false;
	}

	// ____________________________________________________________________________________
	private void storeHistory(List<String> history, String newString)
	{
		if( newString.trim().length() == 0 )
			return;
		history.remove(newString);
		history.add(0, newString);
		while(history.size() > MAX_HISTORY)
			history.remove(history.size() - 1);
		StringBuilder builder = new StringBuilder();
		for(String h : history)
		{
			h = h.replace("%", "%1");
			h = h.replace(";", "%2");
			builder.append(h);
			builder.append(';');
		}
		Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
		editor.putString("lineHistory", builder.toString());
		editor.apply();
	}

	// ____________________________________________________________________________________
	private List<String> loadHistory()
	{
		String value = PreferenceManager.getDefaultSharedPreferences(mContext).getString("lineHistory", "");
		String[] strings = value.split(";");
		List<String> history = new ArrayList<>(strings.length);
		for(String s : strings)
		{
			s = s.replace("%2", ";");
			s = s.replace("%1", "%");
			history.add(s);
		}
		return history;
	}

	// ____________________________________________________________________________________ //
	// 																						//
	// ____________________________________________________________________________________ //
	private class UI
	{
		private Context mContext;
		private EditText mInput;

		public boolean handleGamepadKey(KeyEvent ev)
		{
			if(ev.getAction() != KeyEvent.ACTION_DOWN) return false;
			switch(ev.getKeyCode())
			{
			case KeyEvent.KEYCODE_BUTTON_B:
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				cancel();
				return true;
			case KeyEvent.KEYCODE_BUTTON_L1:
				// history cycle - TODO
				return true;
			case KeyEvent.KEYCODE_BUTTON_R1:
				// history cycle - TODO
				return true;
			}
			return false;
		}
		private ListView mHistoryList;
		//private NH_Dialog mDialog;
		private View mRoot;
		private ArrayAdapter<String> mAdapter;
		public boolean mSaveHistory;

		// ____________________________________________________________________________________
		public UI(AppCompatActivity context, boolean saveHistory, boolean showKeyboard, String initText)
		{
			mContext = context;
			
			mSaveHistory = saveHistory;

			mRoot = Util.inflate(context, R.layout.dialog_getline, R.id.dlg_frame);
			mInput = (EditText)mRoot.findViewById(R.id.input);
			mInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(mMaxChars) });
			mInput.setOnKeyListener(new OnKeyListener()
			{
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event)
				{
					if(event.getAction() != KeyEvent.ACTION_DOWN)
						return false;

					if(keyCode == KeyEvent.KEYCODE_ENTER)
						ok();
					else if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
						cancel();
					else if(keyCode == KeyEvent.KEYCODE_SEARCH) // This is doing weird stuff, might as well block it 
						return true;
					return false;
				}
			});

			((TextView)mRoot.findViewById(R.id.title)).setText(mTitle);

			mRoot.findViewById(R.id.history).setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					if(v != null)
					{
						toggleHistory();
					}
				}
			});
			
			mHistoryList = (ListView)mRoot.findViewById(R.id.history_list);
			
			mAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, mHistory);
			mHistoryList.setAdapter(mAdapter);
			
			mHistoryList.setVisibility(View.GONE);

			mHistoryList.setOnItemClickListener(new OnItemClickListener()
			{
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id)
				{
					mInput.setText(mAdapter.getItem(position));
					mInput.selectAll();
					mHistoryList.setVisibility(View.GONE);
				}
			});

			mHistoryList.setOnKeyListener(new OnKeyListener()
			{
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event)
				{
					return false;
				}
			});
			
			mRoot.findViewById(R.id.btn_0).setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					if(v != null)
					{
						ok();
					}
				}
			});
			mRoot.findViewById(R.id.btn_1).setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					cancel();
				}
			});

			mState.hideControls();
			mInput.requestFocus();
			
			mInput.setText(initText);
			mInput.selectAll();
			
			if(showKeyboard)
				Util.showKeyboard(context, mInput);
		}

		// ____________________________________________________________________________________
		protected void toggleHistory() {
			mHistoryList.setVisibility(mHistoryList.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE );
		}

		// ____________________________________________________________________________________
		public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
		{
			if(mRoot == null)
				return KeyEventResult.IGNORED;

			switch(keyCode)
			{
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_DEL:
			case KeyEvent.KEYCODE_FORWARD_DEL:
				return KeyEventResult.RETURN_TO_SYSTEM;

			case KeyEvent.KEYCODE_BACK:
				cancel();
			break;

			case KeyEvent.KEYCODE_ENTER:
				ok();
			break;
			
			default:
				if(ch == '\033')
					cancel();
			break;
			}
			return KeyEventResult.HANDLED;
		}

		// ____________________________________________________________________________________
		public void dismiss()
		{
			Util.hideKeyboard(mContext, mInput);
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
		private void ok()
		{
			if(mRoot != null)
			{
				String text = mInput.getText().toString();
				mIO.sendLineCmd(text);
				if(mSaveHistory)
					storeHistory(mHistory, text);
				mState.popContext(UiContext.GETLINE);
				dismiss();
			}
		}

		// ____________________________________________________________________________________
		private void cancel()
		{
			if(mRoot != null)
			{
				mIO.sendLineCmd("\033 ");
				mState.popContext(UiContext.GETLINE);
				dismiss();
			}
		}
	}
}
