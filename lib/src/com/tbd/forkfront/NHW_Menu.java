package com.tbd.forkfront;

import java.util.ArrayList;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.Input.Modifier;

public class NHW_Menu implements NH_Window
{
	NetHackIO mIO;
	ArrayList<MenuItem> mItems;
	String mTitle;
	SpannableStringBuilder mBuilder;
	Tileset mTileset;
	private boolean mIsBlocking;
	private NHW_MenuFragment mFragment;
	private boolean mIsVisible;
	private AppCompatActivity mContext;
	private NH_State mState;

	private enum Type
	{
		None,
		Menu,
		Text
	}

	Type mType;
	MenuSelectMode mHow;
	private int mWid;
	int mKeyboardCount;

	// ____________________________________________________________________________________
	public NHW_Menu(int wid, AppCompatActivity context, NetHackIO io, Tileset tileset)
	{
		mWid = wid;
		mIO = io;
		mTileset = tileset;
		mType = Type.None;
		mKeyboardCount = -1;
		mContext = context;
		mState = new androidx.lifecycle.ViewModelProvider(context).get(NetHackViewModel.class).getState();
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return mTitle;
	}

	// ____________________________________________________________________________________
	@Override
	public void setContext(AppCompatActivity context)
	{
		mContext = context;
		// Fragment will get context updates through its lifecycle methods (onAttach)
		// No need to manually update fragment's context field
	}

	// ____________________________________________________________________________________
	@Override
	public void clear()
	{
		throw new UnsupportedOperationException();
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsVisible = true;
		if(mBuilder != null && mType == Type.None)
		{
			mType = Type.Text;
		}
		mKeyboardCount = -1;
		mIsBlocking = bBlocking;

		if(mFragment == null || !mFragment.isAdded())
		{
			mFragment = new NHW_MenuFragment(this);
			mContext.getSupportFragmentManager()
				.beginTransaction()
				.add(R.id.window_fragment_host, mFragment, "nhw_" + mWid)
				.commit();
		}
		mState.pushContext(mType == Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
	}

	// ____________________________________________________________________________________
	private void hide()
	{
		if (mIsVisible) {
			mState.popContext(mType == Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
		}
		mIsVisible = false;
		removeFragment();
	}

	// ____________________________________________________________________________________
	@Override
	public void destroy()
	{
		mIsVisible = false;
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
		return mFragment != null && mFragment.isAdded() && mFragment.handleGamepadKey(ev);
	}

	@Override
	public boolean handleGamepadMotion(MotionEvent ev)
	{
		return mFragment != null && mFragment.isAdded() && mFragment.handleGamepadMotion(ev);
	}

	// ____________________________________________________________________________________
	private void close()
	{
		if (mIsVisible) {
			mState.popContext(mType == Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
		}
		if(mIsBlocking)
			mIO.sendKeyCmd(' ');
		removeFragment();
		mIsBlocking = false;
		mItems = null;
		mBuilder = null;
		mType = Type.None;
		mKeyboardCount = -1;
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
	@Override
	public void printString(final int attr, final String str, int append, int color)
	{
		if(mBuilder == null)
		{
			mBuilder = new SpannableStringBuilder(str);
			mItems = null;
		}
		else
		{
			mBuilder.append('\n');
			mBuilder.append(TextAttr.style(str, attr));
		}
		if(mFragment != null && mFragment.isAdded())
			mFragment.updateText();
	}

	// ____________________________________________________________________________________
	@Override
	public void setCursorPos(int x, int y) {
	}

	@Override
	public boolean isVisible() {
		return mIsVisible;
	}

	// ____________________________________________________________________________________
	public void startMenu()
	{
		mItems = new ArrayList<MenuItem>(100);
		mBuilder = null;
	}

	// ____________________________________________________________________________________
	public void addMenu(int tile, long ident, int accelerator, int groupacc, int attr, String str, int preselected, int color)
	{
		android.util.Log.d("NHW_Menu", "addMenu: " + str + " ident=" + ident + " acc=" + (char)accelerator);
		if(str.length() == 0 && tile < 0)
			return;
		// start_menu is not always called
		if(mItems == null)
			startMenu();
		mItems.add(new MenuItem(tile, ident, accelerator, groupacc, attr, str, preselected, color));
	}

	// ____________________________________________________________________________________
	public void endMenu(String prompt)
	{
		mTitle = prompt;
	}

	// ____________________________________________________________________________________
	@Override
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount)
	{
		if(mFragment != null && mFragment.isAdded()) {
			android.util.Log.d("NHW_Menu", "Delegating handleKeyDown to fragment");
			return mFragment.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
		}
		android.util.Log.d("NHW_Menu", "Fragment not ready for handleKeyDown");
		return KeyEventResult.IGNORED;
	}

	// ____________________________________________________________________________________
	private void generateAccelerators()
	{
		for(MenuItem i : mItems)
			if(i.hasAcc())
				return;
		char acc = 'a';
		for(MenuItem i : mItems)
		{
			if(!i.isHeader() && i.isSelectable() && acc != 0)
			{
				i.setAcc(acc);
				acc++;
				if(acc == 'z' + 1)
					acc = 'A';
				else if(acc == 'Z' + 1)
					acc = 0;
			}
		}
	}

	// ____________________________________________________________________________________
	public void selectMenu(MenuSelectMode how)
	{
		mType = Type.Menu;
		mKeyboardCount = -1;
		mHow = how;
		show(false);
	}

	// ____________________________________________________________________________________
	@Override
	public void preferencesUpdated(SharedPreferences prefs) {
		if(mFragment != null && mFragment.isAdded())
			mFragment.preferencesUpdated(prefs);
	}

	// ____________________________________________________________________________________ //
	// 																						//
	// ____________________________________________________________________________________ //
	public static class NHW_MenuFragment extends Fragment implements AmountSelector.Listener
	{
		private final NHW_Menu mMenu;
		private View mRoot;

		private ListView mListView;
		private AmountSelector mAmountSelector;
		private Button mSelectAllBtn;

		public boolean handleGamepadKey(KeyEvent ev)
		{
			if(mAmountSelector != null) return mAmountSelector.handleGamepadKey(ev);
			if(ev.getAction() != KeyEvent.ACTION_DOWN) return false;

			android.util.Log.d("NHW_Menu", "Fragment handleGamepadKey: " + ev.getKeyCode());
			switch(ev.getKeyCode())
			{
			case KeyEvent.KEYCODE_BUTTON_A:
				if(mMenu.mType == Type.Text) { mMenu.close(); return true; }
				if(mListView != null && !mListView.hasFocus()) return false;
				return handleA();
			case KeyEvent.KEYCODE_BUTTON_B:
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				if(mMenu.mType == Type.Text) { mMenu.close(); return true; }
				mMenu.mKeyboardCount = -1;
				sendCancelSelect();
				return true;
			case KeyEvent.KEYCODE_BUTTON_X:
				if(mMenu.mType == Type.Menu && mMenu.mHow != MenuSelectMode.PickNone) {
					int pos = mListView.getSelectedItemPosition();
					if(pos == ListView.INVALID_POSITION) pos = mListView.getFirstVisiblePosition();
					if(pos != ListView.INVALID_POSITION && pos < mMenu.mItems.size()) {
						MenuItem item = mMenu.mItems.get(pos);
						if(item.isSelectable() && item.getMaxCount() > 1) {
							mAmountSelector = new AmountSelector(this, (AppCompatActivity)requireActivity(), mMenu.mTileset, item);
							return true;
						}
					}
					if(mMenu.mHow == MenuSelectMode.PickMany) {
						if(mSelectAllBtn != null) mSelectAllBtn.performClick();
						else selectAll();
					}
					else mMenu.mKeyboardCount = -1;
					return true;
				}
				break;
			case KeyEvent.KEYCODE_BUTTON_L1:
				if(mMenu.mType == Type.Menu && mMenu.mHow == MenuSelectMode.PickMany) {
					sendSelectChecked();
					return true;
				}
				if (mMenu.mType == Type.Text)
					return navigateScrollView(KeyEvent.KEYCODE_DPAD_LEFT) == KeyEventResult.HANDLED;
				return navigateListView(KeyEvent.KEYCODE_PAGE_UP) == KeyEventResult.HANDLED;
			case KeyEvent.KEYCODE_BUTTON_R1:
				if (mMenu.mType == Type.Text)
					return navigateScrollView(KeyEvent.KEYCODE_DPAD_RIGHT) == KeyEventResult.HANDLED;
				return navigateListView(KeyEvent.KEYCODE_PAGE_DOWN) == KeyEventResult.HANDLED;
			case KeyEvent.KEYCODE_BUTTON_L2:
				if (mMenu.mType == Type.Text)
					return navigateScrollView(KeyEvent.KEYCODE_MOVE_HOME) == KeyEventResult.HANDLED;
				if (mListView != null)
				{
					mListView.setSelection(0);
					return true;
				}
				return false;
			case KeyEvent.KEYCODE_BUTTON_R2:
				if (mMenu.mType == Type.Text)
					return navigateScrollView(KeyEvent.KEYCODE_MOVE_END) == KeyEventResult.HANDLED;
				if (mListView != null)
				{
					mListView.setSelection(mListView.getCount() - 1);
					return true;
				}
				return false;
			}
			return false;
		}

		private boolean handleA()
		{
			int pos = mListView.getSelectedItemPosition();
			if(pos == ListView.INVALID_POSITION) pos = mListView.getFirstVisiblePosition();
			if(pos != ListView.INVALID_POSITION)
			{
				MenuItem item = (MenuItem)mListView.getItemAtPosition(pos);
				if(mMenu.mHow == MenuSelectMode.PickOne) sendSelectOne(item, mMenu.mKeyboardCount);
				else toggleItemOrGroupAt(pos);
				return true;
			}
			return false;
		}

		public boolean handleGamepadMotion(MotionEvent ev)
		{
			return false;
		}

		public NHW_MenuFragment(NHW_Menu menu)
		{
			mMenu = menu;
		}

		@Override
		public void onAttach(@NonNull android.content.Context context)
		{
			super.onAttach(context);
			// Context is available through requireActivity() - no need to store it
		}

		@Override
		public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
		{
			if(mMenu.mType == Type.Text)
				mRoot = createTextDlg(inflater, container);
			else if(mMenu.mType == Type.Menu)
				mRoot = createMenu(inflater, container);

			if(mRoot != null) {
				mRoot.requestFocus();
			}

			return mRoot;
		}

		// ____________________________________________________________________________________
		void updateText()
		{
			if(mMenu.mType == Type.Text && mRoot != null)
			{
				NH_TextView tv = mRoot.findViewById(R.id.text_view);
				if(tv != null && mMenu.mBuilder != null)
					tv.setText(mMenu.mBuilder);
			}
		}

		// ____________________________________________________________________________________
		public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Modifier> modifiers, int repeatCount)
		{
			android.util.Log.d("NHW_Menu", "Fragment handleKeyDown: Key=" + keyCode + " Showing=" + isShowing());
			if(mAmountSelector != null)
				return mAmountSelector.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);

			if(ch == '<')
				keyCode = KeyEvent.KEYCODE_PAGE_UP;
			else if(ch == '>')
				keyCode = KeyEvent.KEYCODE_PAGE_DOWN;

			if(keyCode < 0)
				return KeyEventResult.IGNORED;

			if(mMenu.mType == Type.Text)
			{
				switch(keyCode)
				{
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_DOWN:
				case KeyEvent.KEYCODE_DPAD_LEFT:
				case KeyEvent.KEYCODE_DPAD_RIGHT:
				case KeyEvent.KEYCODE_DPAD_CENTER:
					return navigateScrollView(keyCode);

				case KeyEvent.KEYCODE_ESCAPE:
				case KeyEvent.KEYCODE_ENTER:
				case KeyEvent.KEYCODE_BACK:
					mMenu.close();
				break;

				case KeyEvent.KEYCODE_SPACE:
				case KeyEvent.KEYCODE_PAGE_DOWN:
					((ScrollView)mRoot.findViewById(R.id.scrollview)).pageScroll(ScrollView.FOCUS_DOWN);
				break;

				case KeyEvent.KEYCODE_PAGE_UP:
					((ScrollView)mRoot.findViewById(R.id.scrollview)).pageScroll(ScrollView.FOCUS_UP);
				break;

				default:
					return KeyEventResult.RETURN_TO_SYSTEM;
				}
				return KeyEventResult.HANDLED;
			}

			if(mMenu.mType == Type.Menu)
			{
				switch(keyCode)
				{
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_DOWN:
				case KeyEvent.KEYCODE_DPAD_LEFT:
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					return KeyEventResult.RETURN_TO_SYSTEM;

				case KeyEvent.KEYCODE_DPAD_CENTER:
					if(mListView != null && !mListView.hasFocus()) return KeyEventResult.RETURN_TO_SYSTEM;
					return navigateListView(keyCode);

				case KeyEvent.KEYCODE_ESCAPE:
				case KeyEvent.KEYCODE_BACK:
					mMenu.mKeyboardCount = -1;
					sendCancelSelect();
				break;

				case KeyEvent.KEYCODE_PAGE_DOWN:
					mMenu.mKeyboardCount = -1;
					mListView.setSelection(mListView.getLastVisiblePosition());
					break;

				case KeyEvent.KEYCODE_PAGE_UP:
					mMenu.mKeyboardCount = -1;
					if(mListView.getFirstVisiblePosition() == 0)
					{
						mListView.setSelection(0);
					}
					else
					{
						MenuItem item = (MenuItem)mListView.getItemAtPosition(mListView.getFirstVisiblePosition());
						View itemView = item.getView();
						int itemHeight = itemView != null ? itemView.getHeight() : 0;
						int margin = mListView.getDividerHeight() + 1;
						if(itemHeight > mListView.getHeight() - margin)
							itemHeight = mListView.getHeight() - margin;
						mListView.setSelectionFromTop(mListView.getFirstVisiblePosition(), mListView.getHeight() - itemHeight);
					}
					break;

				case KeyEvent.KEYCODE_ENTER:
					return KeyEventResult.RETURN_TO_SYSTEM;

				case KeyEvent.KEYCODE_SPACE:
					return KeyEventResult.RETURN_TO_SYSTEM;

				default:
					if(mMenu.mHow == MenuSelectMode.PickNone)
					{
						if(getAccelerator(ch) >= 0)
						{
							menuOk();
							return KeyEventResult.HANDLED;
						}
						return KeyEventResult.RETURN_TO_SYSTEM;
					}
					else if(ch >= '0' && ch <= '9')
					{
						if(mMenu.mKeyboardCount < 0)
							mMenu.mKeyboardCount = 0;
						mMenu.mKeyboardCount = mMenu.mKeyboardCount * 10 + ch - '0';
						return KeyEventResult.HANDLED;
					}
					else if(menuSelect(ch))
						return KeyEventResult.HANDLED;
					else if(mMenu.mHow == MenuSelectMode.PickMany)
					{
						if(ch == '.' || keyCode == KeyEvent.KEYCODE_PERIOD)
						{
							selectAll();
							return KeyEventResult.HANDLED;
						}
						else if(ch == '-' || keyCode == KeyEvent.KEYCODE_MINUS)
						{
							clearAll();
							return KeyEventResult.HANDLED;
						}
					}
					return KeyEventResult.RETURN_TO_SYSTEM;
				}
				return KeyEventResult.HANDLED;
			}
			return KeyEventResult.IGNORED;
		}

		// ____________________________________________________________________________________
		@Override
		public void onDismissCount(MenuItem item, int amount)
		{
			mAmountSelector = null;
			if(mListView != null)
				mListView.requestFocus();

			if(mMenu.mHow == MenuSelectMode.PickOne)
			{
				if(amount > 0)
					sendSelectOne(item, amount);
				else if(amount == 0)
					sendCancelSelect();
			}
			else
			{
				if(amount >= 0)
				{
					item.setCount(amount);
					item.setSelected(amount > 0);
					((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
				}
			}
		}

		// ____________________________________________________________________________________
		public View inflateLayout(MenuSelectMode how, LayoutInflater inflater, ViewGroup container)
		{
			View root;
			switch(how)
			{
			case PickNone:
				root = inflater.inflate(R.layout.dialog_menu1, container, false);
			break;

			case PickOne:
				mMenu.generateAccelerators();
				root = inflater.inflate(R.layout.dialog_menu1, container, false);
			break;

			case PickMany:
				mMenu.generateAccelerators();
				root = inflater.inflate(R.layout.dialog_menu3, container, false);
			break;

			default:
				root = inflater.inflate(R.layout.dialog_menu1, container, false);
			break;
			}

			mSelectAllBtn = (Button)root.findViewById(R.id.btn_all);
			if(mSelectAllBtn != null)
				mSelectAllBtn.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						if("Clear all".equals(mSelectAllBtn.getText().toString()))
							clearAll();
						else
							selectAll();
					}
				});

			View btn = root.findViewById(R.id.btn_ok);
			if(btn != null)
				btn.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						menuOk();
					}
				});

			btn = root.findViewById(R.id.btn_cancel);
			if(btn != null)
				btn.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						sendCancelSelect();
					}
				});

			mListView = (ListView)root.findViewById(R.id.menu_list);

			mListView.setOnItemClickListener(new OnItemClickListener()
			{
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id)
				{
					switch(mMenu.mHow)
					{
					case PickNone:
						sendSelectNone();
					break;
					case PickOne:
						MenuItem item = mMenu.mItems.get(position);
						if(!item.isHeader() && item.isSelectable())
							sendSelectOne(item, mMenu.mKeyboardCount);
					break;
					case PickMany:
						toggleItemOrGroupAt(position);
					break;
					}
				}
			});

			mListView.setOnItemLongClickListener(new OnItemLongClickListener()
			{
				@Override
				public boolean onItemLongClick(AdapterView<?> parent, final View view, int position, long id)
				{
					final MenuItem item = mMenu.mItems.get(position);
					if(!item.isSelectable())
						return false;
					if(item.getMaxCount() < 2 || mMenu.mHow == MenuSelectMode.PickNone)
						return false;
					mAmountSelector = new AmountSelector(NHW_MenuFragment.this, (AppCompatActivity)requireActivity(), mMenu.mTileset, item);
					return true;
				}
			});

			mListView.setOnKeyListener(new OnKeyListener()
			{
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event)
				{
					Log.print("MENU ONKEY");
					return false;
				}
			});

			return root;
		}

		// ____________________________________________________________________________________
		public View createTextDlg(LayoutInflater inflater, ViewGroup container)
		{
			View root = inflater.inflate(R.layout.dialog_text, container, false);
			View btn = root.findViewById(R.id.btn_ok);
			btn.setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					mMenu.close();
				}
			});
			btn.requestFocus();
			updateText(root);
			return root;
		}

		// ____________________________________________________________________________________
		private void updateText(View root)
		{
			if(mMenu.mBuilder != null)
				((NH_TextView)root.findViewById(R.id.text_view)).setText(mMenu.mBuilder);
		}

		// ____________________________________________________________________________________
		public View createMenu(LayoutInflater inflater, ViewGroup container) {
			View root = inflateLayout(mMenu.mHow, inflater, container);

			mListView.setAdapter(new MenuItemAdapter((AppCompatActivity) requireActivity(), R.layout.menu_item, mMenu.mItems, mMenu.mTileset, mMenu.mHow));
			mListView.setSelector(R.drawable.nh_gamepad_list_selector);
			mListView.setDrawSelectorOnTop(true);
			mListView.setItemsCanFocus(false);
			mListView.setChoiceMode(mMenu.mHow == MenuSelectMode.PickMany ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
			mListView.setFocusable(true);
			mListView.setFocusableInTouchMode(true);

			if (mMenu.mHow != MenuSelectMode.PickNone) {
				mListView.requestFocus();
				// Initial selection for gamepad
				for (int i = 0; i < mListView.getCount(); i++) {
					if (mListView.getAdapter().isEnabled(i)) {
						mListView.setSelection(i);
						break;
					}
				}
			} else {
				root.requestFocus();
			}

			if (mMenu.mTitle.length() > 0) {
				((NH_TextView) root.findViewById(R.id.title)).setVisibility(View.VISIBLE);
				((NH_TextView) root.findViewById(R.id.title)).setText(mMenu.mTitle);
			}

			return root;
		}

		// ____________________________________________________________________________________
		private void menuOk()
		{
			switch(mMenu.mHow)
			{
			case PickNone:
				sendSelectNone();
			break;
			case PickOne:
				int itemPos = mListView.getSelectedItemPosition();
				if(itemPos >= 0 && itemPos < mMenu.mItems.size())
					sendSelectOne(mMenu.mItems.get(itemPos), mMenu.mKeyboardCount);
			break;
			case PickMany:
				sendSelectChecked();
			break;
			}
		}

		// ____________________________________________________________________________________
		private void toggleItemOrGroupAt(int pos)
		{
			if(pos < 0 || pos >= mMenu.mItems.size())
				return;
			MenuItem item = mMenu.mItems.get(pos);
			if(item.isHeader())
			{
				boolean allSelected = true;
				for(int i = pos + 1; i < mMenu.mItems.size(); i++)
				{
					MenuItem sub = mMenu.mItems.get(i);
					if(sub.isHeader())
						break;
					if(sub.isSelectable() && !sub.isSelected())
					{
						allSelected = false;
						break;
					}
				}
				for(int i = pos + 1; i < mMenu.mItems.size(); i++)
				{
					MenuItem sub = mMenu.mItems.get(i);
					if(sub.isHeader())
						break;
					if(sub.isSelectable())
					{
						sub.setSelected(!allSelected);
						sub.setCount(sub.isSelected() ? sub.getMaxCount() : 0);
					}
				}
			}
			else if(item.isSelectable())
			{
				item.setSelected(!item.isSelected());
				item.setCount(item.isSelected() ? item.getMaxCount() : 0);
			}
			((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
			updateSelectAllBtn();
		}

		// ____________________________________________________________________________________
		private void selectAll()
		{
			for(MenuItem item : mMenu.mItems)
			{
				if(item.isSelectable())
				{
					item.setSelected(true);
					item.setCount(item.getMaxCount());
				}
			}
			((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
			updateSelectAllBtn();
		}

		// ____________________________________________________________________________________
		private void clearAll()
		{
			if(isShowing() && mMenu.mHow == MenuSelectMode.PickMany)
			{
				for(MenuItem item : mMenu.mItems)
				{
					if(item.isSelectable())
					{
						item.setSelected(false);
						item.setCount(0);
					}
				}
				((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
				updateSelectAllBtn();
			}
		}

		// ____________________________________________________________________________________
		private void updateSelectAllBtn()
		{
			if(mSelectAllBtn == null)
				return;
			boolean any = false;
			for(MenuItem item : mMenu.mItems)
			{
				if(item.isSelectable() && item.isSelected())
				{
					any = true;
					break;
				}
			}
			mSelectAllBtn.setText(any ? "Clear all" : "Select all");
		}

		// ____________________________________________________________________________________
		private void sendSelectChecked()
		{
			if(isShowing())
			{
				ArrayList<MenuItem> items = new ArrayList<>();
				StringBuilder sb = new StringBuilder("sendSelectChecked: [");
				for(MenuItem item : mMenu.mItems)
				{
					if(item.isSelected()) {
						items.add(item);
						sb.append(Long.toHexString(item.getId())).append("(").append(item.getCount()).append("), ");
					}
				}
				if (items.size() > 0) sb.setLength(sb.length() - 2);
				sb.append("]");
				Log.print(sb.toString());

				mMenu.mIO.sendSelectCmd(items);
				mMenu.hide();
			}
		}

		// ____________________________________________________________________________________
		private void sendCancelSelect()
		{
			if(isShowing())
			{
				Log.print("sendCancelSelect");
				mMenu.mIO.sendCancelSelectCmd();
				mMenu.hide();
			}
		}

		// ____________________________________________________________________________________
		private void sendSelectOne(MenuItem item, int count)
		{
			if(isShowing())
			{
				if(!item.isHeader() && item.isSelectable())
				{
					Log.print("sendSelectOne: id=" + Long.toHexString(item.getId()) + " count=" + count);
					mMenu.mIO.sendSelectCmd(item.getId(), count);
					mMenu.hide();
				}
			}
		}

		// ____________________________________________________________________________________
		private boolean isShowing()
		{
			return mRoot != null && mRoot.getVisibility() == View.VISIBLE;
		}

		// ____________________________________________________________________________________
		private int getAccelerator(char ch)
		{
			for(int i = 0; i < mMenu.mItems.size(); i++)
			{
				MenuItem item = mMenu.mItems.get(i);
				if(item.getAcc() == ch)
					return i;
			}
			return -1;
		}

		// ____________________________________________________________________________________
		private boolean menuSelect(char ch)
		{
			if(mMenu.mHow == MenuSelectMode.PickNone)
				return false;

			int pos = getAccelerator(ch);
			if(pos >= 0)
			{
				MenuItem item = mMenu.mItems.get(pos);
				if(!item.isHeader() && item.isSelectable())
				{
					if(mMenu.mHow == MenuSelectMode.PickOne)
						sendSelectOne(item, mMenu.mKeyboardCount);
					else
						toggleItemOrGroupAt(pos);
					return true;
				}
			}
			return false;
		}

		// ____________________________________________________________________________________
		private KeyEventResult navigateScrollView(int keyCode)
		{
			ScrollView sv = mRoot.findViewById(R.id.scrollview);
			if(sv == null) return KeyEventResult.IGNORED;

			switch(keyCode)
			{
			case KeyEvent.KEYCODE_DPAD_UP:
				sv.smoothScrollBy(0, -sv.getHeight() / 4);
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				sv.smoothScrollBy(0, sv.getHeight() / 4);
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				sv.pageScroll(View.FOCUS_UP);
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				sv.pageScroll(View.FOCUS_DOWN);
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
				mMenu.close();
				break;
			case KeyEvent.KEYCODE_MOVE_HOME:
				sv.fullScroll(View.FOCUS_UP);
				break;
			case KeyEvent.KEYCODE_MOVE_END:
				sv.fullScroll(View.FOCUS_DOWN);
				break;
			default:
				return KeyEventResult.IGNORED;
			}
			return KeyEventResult.HANDLED;
		}

		private KeyEventResult navigateListView(int keyCode)
		{
			if(mListView == null) return KeyEventResult.IGNORED;

			if (!mListView.hasFocus()) {
				mListView.requestFocus();
			}

			int pos = mListView.getSelectedItemPosition();
			boolean wasInvalid = (pos == ListView.INVALID_POSITION);
			if(wasInvalid)
				pos = (keyCode == KeyEvent.KEYCODE_DPAD_UP) ? mListView.getCount() - 1 : mListView.getFirstVisiblePosition();

			switch(keyCode)
			{
			case KeyEvent.KEYCODE_DPAD_UP:
				if(!wasInvalid)
					pos--;
				while(pos >= 0 && !mListView.getAdapter().isEnabled(pos))
					pos--;
				if(pos >= 0)
				{
					mListView.setSelection(pos);
				}
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				if(!wasInvalid)
					pos++;
				while(pos < mListView.getCount() && !mListView.getAdapter().isEnabled(pos))
					pos++;
				if(pos < mListView.getCount())
				{
					mListView.setSelection(pos);
				}
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
				if(pos != ListView.INVALID_POSITION)
				{
					MenuItem item = (MenuItem)mListView.getItemAtPosition(pos);
					if(mMenu.mHow == MenuSelectMode.PickOne)
						sendSelectOne(item, mMenu.mKeyboardCount);
					else
						toggleItemOrGroupAt(pos);
				}
				break;
			case KeyEvent.KEYCODE_PAGE_UP:
				mListView.smoothScrollBy(-mListView.getHeight() * 3 / 4, 100);
				break;
			case KeyEvent.KEYCODE_PAGE_DOWN:
				mListView.smoothScrollBy(mListView.getHeight() * 3 / 4, 100);
				break;
			default:
				return KeyEventResult.IGNORED;
			}
			return KeyEventResult.HANDLED;
		}

		// ____________________________________________________________________________________
		private void sendSelectNone()
		{
			if(isShowing())
			{
				mMenu.mIO.sendSelectNoneCmd();
				mMenu.hide();
			}
		}

		public void preferencesUpdated(SharedPreferences prefs) {
			if(mListView != null && mListView.getAdapter() != null) {
				mListView.invalidateViews();
				((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
			}
                }
        }
}
