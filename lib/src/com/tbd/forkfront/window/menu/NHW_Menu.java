package com.tbd.forkfront.window.menu;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.R;
import com.tbd.forkfront.Log;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.engine.NetHackIO;
import com.tbd.forkfront.window.text.TextAttr;
import com.tbd.forkfront.window.text.NH_TextView;
import com.tbd.forkfront.window.map.Tileset;
import com.tbd.forkfront.window.AbstractNhWindow;
import com.tbd.forkfront.window.NH_Window;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.input.Input;

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
import com.tbd.forkfront.gamepad.GamepadDeviceWatcher;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.input.Input.Modifier;

public class NHW_Menu extends AbstractNhWindow
{
	private final MenuModel mModel;
	private final MenuSelectionService mSelectionService;
	private NHW_MenuFragment mFragment;

	// ____________________________________________________________________________________
	public NHW_Menu(int wid, AppCompatActivity context, EngineCommandSender io, Tileset tileset)
	{
		super(wid, context, io);
		mModel = new MenuModel(tileset);
		mSelectionService = new MenuSelectionService(io);
	}

	public MenuModel getModel() {
		return mModel;
	}

	public MenuSelectionService getSelectionService() {
		return mSelectionService;
	}

	// ____________________________________________________________________________________
	@Override
	public String getTitle()
	{
		return mModel.mTitle;
	}

	// ____________________________________________________________________________________
	@Override
	public void show(boolean bBlocking)
	{
		mIsVisible = true;
		if(mModel.mBuilder != null && mModel.mType == MenuModel.Type.None)
		{
			mModel.mType = MenuModel.Type.Text;
		}
		mModel.mKeyboardCount = -1;
		mIsBlocking = bBlocking;

		if(mFragment == null || !mFragment.isAdded())
		{
			mFragment = new NHW_MenuFragment(this);
			addFragment(mFragment);
		}
		mState.getGamepadContext().pushContext(mModel.mType == MenuModel.Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
	}

	// ____________________________________________________________________________________
	private void hide()
	{
		if (mIsVisible) {
			mState.getGamepadContext().popContext(mModel.mType == MenuModel.Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
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
	public void close()
	{
		hide();
		super.close();
		mIsBlocking = false;
		mModel.mItems = null;
		mModel.mBuilder = null;
		mModel.mType = MenuModel.Type.None;
		mModel.mKeyboardCount = -1;
	}

	// ____________________________________________________________________________________
	protected void removeFragment()
	{
		super.removeFragment();
		mFragment = null;
	}

	// ____________________________________________________________________________________
	@Override
	public void clear()
	{
		// Menus are ephemeral and do not support clear.
		// This is a safe no-op to satisfy the NH_Window contract.
	}

	// ____________________________________________________________________________________
	@Override
	public void printString(final int attr, final String str, int append, int color)
	{
		if(mModel.mBuilder == null)
		{
			mModel.mBuilder = new SpannableStringBuilder(str);
			mModel.mItems = null;
		}
		else
		{
			mModel.mBuilder.append('\n');
			mModel.mBuilder.append(TextAttr.style(str, attr, mContext));
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
		mModel.mItems = new ArrayList<MenuItem>(100);
		mModel.mBuilder = null;
	}

	// ____________________________________________________________________________________
	public void addMenu(int tile, long ident, int accelerator, int groupacc, int attr, String str, int preselected, int color)
	{
		if(str.length() == 0 && tile < 0)
			return;
		// start_menu is not always called
		if(mModel.mItems == null)
			startMenu();
		mModel.mItems.add(new MenuItem(tile, ident, accelerator, groupacc, attr, str, preselected, color, mContext));
	}

	// ____________________________________________________________________________________
	public void endMenu(String prompt)
	{
		mModel.mTitle = prompt;
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
		for(MenuItem i : mModel.mItems)
			if(i.hasAcc())
				return;
		char acc = 'a';
		for(MenuItem i : mModel.mItems)
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
		mModel.mType = MenuModel.Type.Menu;
		mModel.mKeyboardCount = -1;
		mModel.mHow = how;
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
				if(mMenu.getModel().getType() == MenuModel.Type.Text) { mMenu.close(); return true; }
				if(mListView != null && !mListView.hasFocus()) return false;
				return handleA();
			case KeyEvent.KEYCODE_BUTTON_B:
			case KeyEvent.KEYCODE_BUTTON_SELECT:
				if(mMenu.getModel().getType() == MenuModel.Type.Text) { mMenu.close(); return true; }
				mMenu.getModel().mKeyboardCount = -1;
				sendCancelSelect();
				return true;
			case KeyEvent.KEYCODE_BUTTON_X:
				if(mMenu.getModel().getType() == MenuModel.Type.Menu && mMenu.getModel().mHow != MenuSelectMode.PickNone) {
					int pos = mListView.getSelectedItemPosition();
					if(pos == ListView.INVALID_POSITION) pos = mListView.getFirstVisiblePosition();
					if(pos != ListView.INVALID_POSITION && pos < mMenu.getModel().mItems.size()) {
						MenuItem item = mMenu.getModel().mItems.get(pos);
						if(item.isSelectable() && item.getMaxCount() > 1) {
							mAmountSelector = new AmountSelector(this, (AppCompatActivity)requireActivity(), mMenu.mModel.getTileset(), item);
							return true;
						}
					}
					if(mMenu.getModel().mHow == MenuSelectMode.PickMany) {
						if(mSelectAllBtn != null) mSelectAllBtn.performClick();
						else selectAll();
					}
					else mMenu.getModel().mKeyboardCount = -1;
					return true;
				}
				break;
			case KeyEvent.KEYCODE_BUTTON_L1:
				if(mMenu.getModel().getType() == MenuModel.Type.Menu && mMenu.getModel().mHow == MenuSelectMode.PickMany) {
					sendSelectChecked();
					return true;
				}
				if (mMenu.getModel().getType() == MenuModel.Type.Text)
					return navigateScrollView(KeyEvent.KEYCODE_DPAD_LEFT) == KeyEventResult.HANDLED;
				return navigateListView(KeyEvent.KEYCODE_PAGE_UP) == KeyEventResult.HANDLED;
			case KeyEvent.KEYCODE_BUTTON_R1:
				if (mMenu.getModel().getType() == MenuModel.Type.Text)
					return navigateScrollView(KeyEvent.KEYCODE_DPAD_RIGHT) == KeyEventResult.HANDLED;
				return navigateListView(KeyEvent.KEYCODE_PAGE_DOWN) == KeyEventResult.HANDLED;
			case KeyEvent.KEYCODE_BUTTON_L2:
				if (mMenu.getModel().getType() == MenuModel.Type.Text)
					return navigateScrollView(KeyEvent.KEYCODE_MOVE_HOME) == KeyEventResult.HANDLED;
				if (mListView != null)
				{
					mListView.setSelection(0);
					return true;
				}
				return false;
			case KeyEvent.KEYCODE_BUTTON_R2:
				if (mMenu.getModel().getType() == MenuModel.Type.Text)
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
				if(mMenu.getModel().mHow == MenuSelectMode.PickOne) sendSelectOne(item, mMenu.getModel().mKeyboardCount);
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
			if(mMenu.getModel().getType() == MenuModel.Type.Text)
				mRoot = createTextDlg(inflater, container);
			else if(mMenu.getModel().getType() == MenuModel.Type.Menu)
				mRoot = createMenu(inflater, container);

			if(mRoot != null) {
				mRoot.requestFocus();
			}

			return mRoot;
		}

		// ____________________________________________________________________________________
		void updateText()
		{
			if(mMenu.getModel().getType() == MenuModel.Type.Text && mRoot != null)
			{
				NH_TextView tv = mRoot.findViewById(R.id.text_view);
				if(tv != null && mMenu.getModel().mBuilder != null)
					tv.setText(mMenu.getModel().mBuilder);
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

			if(mMenu.getModel().getType() == MenuModel.Type.Text)
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

			if(mMenu.getModel().getType() == MenuModel.Type.Menu)
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
					mMenu.getModel().mKeyboardCount = -1;
					sendCancelSelect();
				break;

				case KeyEvent.KEYCODE_PAGE_DOWN:
					mMenu.getModel().mKeyboardCount = -1;
					mListView.setSelection(mListView.getLastVisiblePosition());
					break;

				case KeyEvent.KEYCODE_PAGE_UP:
					mMenu.getModel().mKeyboardCount = -1;
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
					if(mMenu.getModel().mHow == MenuSelectMode.PickNone)
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
						if(mMenu.getModel().mKeyboardCount < 0)
							mMenu.getModel().mKeyboardCount = 0;
						mMenu.getModel().mKeyboardCount = mMenu.getModel().mKeyboardCount * 10 + ch - '0';
						return KeyEventResult.HANDLED;
					}
					else if(menuSelect(ch))
						return KeyEventResult.HANDLED;
					else if(mMenu.getModel().mHow == MenuSelectMode.PickMany)
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

			if(mMenu.getModel().mHow == MenuSelectMode.PickOne)
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
					switch(mMenu.getModel().mHow)
					{
					case PickNone:
						sendSelectNone();
					break;
					case PickOne:
						MenuItem item = mMenu.getModel().mItems.get(position);
						if(!item.isHeader() && item.isSelectable())
							sendSelectOne(item, mMenu.getModel().mKeyboardCount);
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
					final MenuItem item = mMenu.getModel().mItems.get(position);
					if(!item.isSelectable())
						return false;
					if(item.getMaxCount() < 2 || mMenu.getModel().mHow == MenuSelectMode.PickNone)
						return false;
					mAmountSelector = new AmountSelector(NHW_MenuFragment.this, (AppCompatActivity)requireActivity(), mMenu.mModel.getTileset(), item);
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

			View closeBtn = root.findViewById(R.id.btn_close);
			if(closeBtn != null)
			{
				closeBtn.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						mMenu.close();
					}
				});
			}

			return root;
		}

		// ____________________________________________________________________________________
		private void updateText(View root)
		{
			if(mMenu.getModel().mBuilder != null)
				((NH_TextView)root.findViewById(R.id.text_view)).setText(mMenu.getModel().mBuilder);
		}

		// ____________________________________________________________________________________
		public View createMenu(LayoutInflater inflater, ViewGroup container) {
			View root = inflateLayout(mMenu.getModel().mHow, inflater, container);

			mListView.setAdapter(new MenuItemAdapter((AppCompatActivity) requireActivity(), R.layout.menu_item, mMenu.getModel().mItems, mMenu.mModel.getTileset(), mMenu.getModel().mHow));
			mListView.setSelector(R.drawable.nh_gamepad_list_selector);
			mListView.setDrawSelectorOnTop(true);
			mListView.setItemsCanFocus(false);
			mListView.setChoiceMode(mMenu.getModel().mHow == MenuSelectMode.PickMany ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
			mListView.setFocusable(true);
			if(GamepadDeviceWatcher.isGamepadConnected(requireActivity()))
				mListView.setFocusableInTouchMode(true);

			if (mMenu.getModel().mHow != MenuSelectMode.PickNone) {
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

			if (mMenu.getModel().mTitle.length() > 0) {
				((NH_TextView) root.findViewById(R.id.title)).setVisibility(View.VISIBLE);
				((NH_TextView) root.findViewById(R.id.title)).setText(mMenu.getModel().mTitle);
			}

			View closeBtn = root.findViewById(R.id.btn_close);
			if(closeBtn != null)
			{
				closeBtn.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View view)
					{
						mMenu.getModel().mKeyboardCount = -1;
						sendCancelSelect();
					}
				});
			}

			return root;
		}

		// ____________________________________________________________________________________
		private void menuOk()
		{
			switch(mMenu.getModel().mHow)
			{
			case PickNone:
				sendSelectNone();
			break;
			case PickOne:
				int itemPos = mListView.getSelectedItemPosition();
				if(itemPos >= 0 && itemPos < mMenu.getModel().mItems.size())
					sendSelectOne(mMenu.getModel().mItems.get(itemPos), mMenu.getModel().mKeyboardCount);
			break;
			case PickMany:
				sendSelectChecked();
			break;
			}
		}

		// ____________________________________________________________________________________
		private void toggleItemOrGroupAt(int pos)
		{
			if(pos < 0 || pos >= mMenu.getModel().mItems.size())
				return;
			MenuItem item = mMenu.getModel().mItems.get(pos);
			if(item.isHeader())
			{
				boolean allSelected = true;
				for(int i = pos + 1; i < mMenu.getModel().mItems.size(); i++)
				{
					MenuItem sub = mMenu.getModel().mItems.get(i);
					if(sub.isHeader())
						break;
					if(sub.isSelectable() && !sub.isSelected())
					{
						allSelected = false;
						break;
					}
				}
				for(int i = pos + 1; i < mMenu.getModel().mItems.size(); i++)
				{
					MenuItem sub = mMenu.getModel().mItems.get(i);
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
			
			if(mListView != null) {
				((MenuItemAdapter)mListView.getAdapter()).notifyDataSetChanged();
				mListView.invalidateViews();
			}
			updateSelectAllBtn();
		}

		// ____________________________________________________________________________________
		private void selectAll()
		{
			for(MenuItem item : mMenu.getModel().mItems)
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
			if(isShowing() && mMenu.getModel().mHow == MenuSelectMode.PickMany)
			{
				for(MenuItem item : mMenu.getModel().mItems)
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
			for(MenuItem item : mMenu.getModel().mItems)
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
				for(MenuItem item : mMenu.getModel().mItems)
				{
					if(item.isSelected()) {
						items.add(item);
						sb.append(Long.toHexString(item.getId())).append("(").append(item.getCount()).append("), ");
					}
				}
				if (items.size() > 0) sb.setLength(sb.length() - 2);
				sb.append("]");
				Log.print(sb.toString());

				mMenu.getSelectionService().sendSelectChecked(items);
				mMenu.hide();
			}
		}

		// ____________________________________________________________________________________
		private void sendCancelSelect()
		{
			if(isShowing())
			{
				Log.print("sendCancelSelect");
				mMenu.getSelectionService().sendCancelSelect();
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
					mMenu.getSelectionService().sendSelectOne(item, count);
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
			for(int i = 0; i < mMenu.getModel().mItems.size(); i++)
			{
				MenuItem item = mMenu.getModel().mItems.get(i);
				if(item.getAcc() == ch)
					return i;
			}
			return -1;
		}

		// ____________________________________________________________________________________
		private boolean menuSelect(char ch)
		{
			if(mMenu.getModel().mHow == MenuSelectMode.PickNone)
				return false;

			int pos = getAccelerator(ch);
			if(pos >= 0)
			{
				MenuItem item = mMenu.getModel().mItems.get(pos);
				if(!item.isHeader() && item.isSelectable())
				{
					if(mMenu.getModel().mHow == MenuSelectMode.PickOne)
						sendSelectOne(item, mMenu.getModel().mKeyboardCount);
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
					if(mMenu.getModel().mHow == MenuSelectMode.PickOne)
						sendSelectOne(item, mMenu.getModel().mKeyboardCount);
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
				mMenu.getSelectionService().sendSelectNone();
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
