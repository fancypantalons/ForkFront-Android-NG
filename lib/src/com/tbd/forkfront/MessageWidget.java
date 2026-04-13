package com.tbd.forkfront;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MessageWidget extends ControlWidget implements NHW_Message.MessageUpdateListener
{
	private NHW_Message mMessageWindow;
	private TextView mMessageView;
	private TextView mMoreView;
	private List<String> mRecentMessages;
	private int mMoreCount;

	// ____________________________________________________________________________________
	public MessageWidget(Context context, NHW_Message messageWindow)
	{
		super(context, createMessageView(context), "message");
		mMessageWindow = messageWindow;
		mRecentMessages = new ArrayList<>();

		// Initialize view references
		LinearLayout container = (LinearLayout) getContentView();
		mMessageView = (TextView) container.getChildAt(0);
		mMoreView = (TextView) container.getChildAt(1);

		// Register as listener
		messageWindow.addListener(this);

		// Get initial messages
		String recent = messageWindow.getRecentMessages(NHW_Message.SHOW_MAX_LINES);
		if (recent != null && !recent.isEmpty()) {
			mMessageView.setText(recent.trim());
		}
	}

	// ____________________________________________________________________________________
	private static LinearLayout createMessageView(Context context)
	{
		LinearLayout container = new LinearLayout(context);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		));

		// Create message TextView
		TextView messageView = new TextView(context);
		messageView.setTextColor(0xFFFFFFFF);
		messageView.setTextSize(15);
		messageView.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		));

		// Create --More-- TextView
		TextView moreView = new TextView(context);
		moreView.setText("--More--");
		moreView.setTextColor(0xFF000000);
		moreView.setBackgroundColor(0xFFFFFFFF);
		moreView.setTextSize(15);
		moreView.setClickable(true);
		moreView.setVisibility(View.GONE);
		moreView.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		));

		container.addView(messageView);
		container.addView(moreView);

		return container;
	}

	// ____________________________________________________________________________________
	// MessageUpdateListener implementation
	// ____________________________________________________________________________________

	@Override
	public void onMessageAdded(String message)
	{
		mRecentMessages.add(message);
		// Keep only the last SHOW_MAX_LINES messages
		while (mRecentMessages.size() > NHW_Message.SHOW_MAX_LINES) {
			mRecentMessages.remove(0);
		}
		updateDisplay();
	}

	@Override
	public void onMessagesCleared()
	{
		mRecentMessages.clear();
		mMoreCount = 0;
		updateDisplay();
	}

	@Override
	public void onMoreCountChanged(int moreCount)
	{
		mMoreCount = moreCount;
		updateMoreButton();
	}

	// ____________________________________________________________________________________
	private void updateDisplay()
	{
		if (mMessageView == null) {
			return;
		}

		if (mRecentMessages.isEmpty()) {
			mMessageView.setText("");
		} else {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mRecentMessages.size(); i++) {
				if (i > 0) {
					sb.append("\n");
				}
				sb.append(mRecentMessages.get(i));
			}
			mMessageView.setText(sb.toString());
		}

		updateMoreButton();
	}

	// ____________________________________________________________________________________
	private void updateMoreButton()
	{
		if (mMoreView == null) {
			return;
		}

		if (mMoreCount > 0) {
			mMoreView.setText("--" + mMoreCount + " more--");
			mMoreView.setVisibility(View.VISIBLE);
		} else {
			mMoreView.setVisibility(View.GONE);
		}
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();
		if (mMessageWindow != null) {
			mMessageWindow.removeListener(this);
		}
	}
}
