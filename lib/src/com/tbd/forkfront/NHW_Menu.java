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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

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
		if(mFragment != null && mFragment.isAdded())
			mFragment.mContext = context;
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
				.commitNow();
		}
	}

	// ____________________________________________________________________________________
	private void hide()
	{
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

	// ____________________________________________________________________________________
	private void close()
	{
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
				.commitNow();
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
	}

	// ____________________________________________________________________________________
	@Override
	public void setCursorPos(int x, int y) {
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
	public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount, boolean bSoftInput)
	{
		if(mFragment != null && mFragment.isAdded())
			return mFragment.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);
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
		AppCompatActivity mContext;
		private View mRoot;

		private ListView mListView;
		private AmountSelector mAmountSelector;
		private Button mSelectAllBtn;

		public NHW_MenuFragment(NHW_Menu menu)
		{
			mMenu = menu;
		}

		@Override
		public void onAttach(@NonNull android.content.Context context)
		{
			super.onAttach(context);
			mContext = (AppCompatActivity) context;
		}

		@Override
		public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
		{
			if(mMenu.mType == Type.Text)
				mRoot = createTextDlg(inflater, container);
			else if(mMenu.mType == Type.Menu)
				mRoot = createMenu(inflater, container);

			if(mRoot != null)
				mRoot.requestFocus();

			return mRoot;
		}

		// ____________________________________________________________________________________
		public KeyEventResult handleKeyDown(char ch, int nhKey, int keyCode, Set<Modifier> modifiers, int repeatCount, boolean bSoftInput)
		{
			if(mAmountSelector != null)
				return mAmountSelector.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);

			if(ch == '<')
				keyCode = KeyEvent.KEYCODE_PAGE_UP;
			else if(ch == '>')
				keyCode = KeyEvent.KEYCODE_PAGE_DOWN;

			if(!isShowing() || keyCode < 0)
				return KeyEventResult.IGNORED;

			if(mMenu.mType == Type.Text)
			{
				switch(keyCode)
				{
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
					if(bSoftInput)
						menuOk();
					else
						return KeyEventResult.RETURN_TO_SYSTEM;
				break;

				case KeyEvent.KEYCODE_SPACE:
					if(bSoftInput)
					{
						if(mMenu.mHow == MenuSelectMode.PickNone)
							menuOk();
						else
							toggleItemOrGroupAt(mListView.getSelectedItemPosition());
					}
					else
						return KeyEventResult.RETURN_TO_SYSTEM;
				break;

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
			if(mMenu.mHow == MenuSelectMode.PickOne)
			{
				if(amount > 0)
					sendSelectOne(item, amount);
				else if(amount == 0)
					sendCancelSelect();
			}
			else
			{
				item.setCount(amount);
				((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
			}
		}

		// ____________________________________________________________________________________
		public boolean isShowing()
		{
			return mRoot != null && mRoot.getVisibility() == View.VISIBLE;
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

		// ____________________________________________________________________________________
		private void sendSelectOne(MenuItem item, int count)
		{
			if(isShowing())
			{
				long id = item.getId();
				if(count > 0)
					count = Math.min(count, item.getMaxCount());
				mMenu.mIO.sendSelectCmd(id, count);
				mMenu.hide();
			}
		}

		// ____________________________________________________________________________________
		private void sendSelectChecked()
		{
			if(isShowing())
			{
				ArrayList<MenuItem> items = new ArrayList<MenuItem>();
				for(MenuItem i : mMenu.mItems)
					if(i.isSelected())
						items.add(i);
				mMenu.mIO.sendSelectCmd(items);
				mMenu.hide();
			}
		}

		// ____________________________________________________________________________________
		private void sendCancelSelect()
		{
			if(isShowing())
			{
				mMenu.mIO.sendCancelSelectCmd();
				mMenu.hide();
			}
		}

		// ____________________________________________________________________________________
		private void toggleItemOrGroupAt(int itemPos)
		{
			if(mMenu.mHow != MenuSelectMode.PickMany)
				return;

			if(itemPos < 0 || itemPos >= mMenu.mItems.size())
				return;

			MenuItem item = mMenu.mItems.get(itemPos);
			if(item.isHeader())
				toggleGroupAt(itemPos);
			else
				toggleItem(item, true);
		}

		// ____________________________________________________________________________________
		private void toggleGroupAt(int itemPos)
		{
			mMenu.mKeyboardCount = -1;

			boolean select = false;

			MenuItem item;

			itemPos++;
			int lastItemPos = itemPos;
			for(; lastItemPos < mMenu.mItems.size(); lastItemPos++)
			{
				item = mMenu.mItems.get(lastItemPos);
				if(item.isHeader())
					break;
				if(item.isSelectable() && !item.isSelected())
					select = true;
			}

			for(; itemPos < lastItemPos; itemPos++)
			{
				item = mMenu.mItems.get(itemPos);

				if(item.isHeader() || !item.isSelectable())
					continue;

				item.setCount(select ? -1 : 0);
			}

			((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
		}

		// ____________________________________________________________________________________
		private int getAccelerator(char acc)
		{
			for(int i = 0; i < mMenu.mItems.size(); i++)
			{
				MenuItem item = mMenu.mItems.get(i);
				if(item.getAcc() == acc && !item.isHeader() && item.isSelectable())
					return i;
			}
			return -1;
		}

		// ____________________________________________________________________________________
		private boolean menuSelect(char acc)
		{
			if(acc == 0)
				return false;
			if(isShowing() && mMenu.mHow != MenuSelectMode.PickNone)
			{
				boolean bRet = false;
				int i = getAccelerator(acc);
				if(i >= 0)
				{
					MenuItem item = mMenu.mItems.get(i);
					if(mMenu.mHow == MenuSelectMode.PickOne)
						sendSelectOne(item, mMenu.mKeyboardCount);
					else
						toggleItem(item, false);
					bRet = true;
				}
				else if(mMenu.mHow == MenuSelectMode.PickMany)
				{
					mMenu.mKeyboardCount = -1;
					for(MenuItem item : mMenu.mItems)
					{
						if(item.getGroupAcc() == acc && !item.isHeader() && item.isSelectable())
						{
							toggleItem(item, false);
							bRet = true;
						}
					}
				}

				if(bRet)
					((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();

				return bRet;
			}
			return false;
		}

		// ____________________________________________________________________________________
		private void toggleItem(MenuItem item, boolean notifyAdapter)
		{
			if(!item.isHeader() && item.isSelectable())
			{
				if(mMenu.mKeyboardCount >= 0)
					item.setCount(mMenu.mKeyboardCount);
				else
					item.setCount(item.isSelected() ? 0 : -1);
				if(notifyAdapter)
					((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
			}
			mMenu.mKeyboardCount = -1;
		}

		// ____________________________________________________________________________________
		private void selectAll()
		{
			if(isShowing() && mMenu.mHow == MenuSelectMode.PickMany)
			{
				for(int i = 0; i < mMenu.mItems.size(); i++)
				{
					MenuItem item = mMenu.mItems.get(i);
					if(!item.isHeader() && item.isSelectable())
						item.setCount(-1);
				}
				((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
				mMenu.mKeyboardCount = -1;
				if(mSelectAllBtn != null)
					mSelectAllBtn.setText("Clear all");
			}
		}

		// ____________________________________________________________________________________
		private void clearAll()
		{
			if(isShowing() && mMenu.mHow == MenuSelectMode.PickMany)
			{
				for(int i = 0; i < mMenu.mItems.size(); i++)
				{
					MenuItem item = mMenu.mItems.get(i);
					if(!item.isHeader() && item.isSelectable())
						item.setCount(0);
				}
				((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
				mMenu.mKeyboardCount = -1;
				if(mSelectAllBtn != null)
					mSelectAllBtn.setText("Select all");
			}
		}

		// ____________________________________________________________________________________
		public View createTextDlg(LayoutInflater inflater, ViewGroup container)
		{
			Log.print("create text dlg");

			View root = inflater.inflate(R.layout.dialog_text, container, false);
			((TextView)root.findViewById(R.id.text_view)).setText(mMenu.mBuilder);

			View btn = root.findViewById(R.id.btn_ok);
			btn.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					mMenu.close();
				}
			});

			root.setOnKeyListener(new OnKeyListener()
			{
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event)
				{
					Log.print("MENU ONKEY");
					return false;
				}
			});

			btn.requestFocus();
			btn.requestFocusFromTouch();

			return root;
		}

		// ____________________________________________________________________________________
		public View createMenu(LayoutInflater inflater, ViewGroup container)
		{
			View root = inflateLayout(mMenu.mHow, inflater, container);

			mListView.setAdapter(new MenuItemAdapter(mContext, R.layout.menu_item, mMenu.mItems, mMenu.mTileset, mMenu.mHow));

			root.requestFocus();

			if(mMenu.mTitle.length() > 0)
			{
				((TextView)root.findViewById(R.id.title)).setVisibility(View.VISIBLE);
				((TextView)root.findViewById(R.id.title)).setText(mMenu.mTitle);
			}
			else
				((TextView)root.findViewById(R.id.title)).setVisibility(View.GONE);

			return root;
		}

		// ____________________________________________________________________________________
		private View inflateLayout(MenuSelectMode how, LayoutInflater inflater, ViewGroup container)
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
					mAmountSelector = new AmountSelector(NHW_MenuFragment.this, mContext, mMenu.mTileset, item);
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

		public void preferencesUpdated(SharedPreferences prefs) {
			if(mListView != null && mListView.getAdapter() != null) {
				mListView.invalidateViews();
				((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
			}
		}
	}

}
