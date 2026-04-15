/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tbd.forkfront;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import androidx.preference.PreferenceManager;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.widget.TextView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.tbd.forkfront.Input.Modifier;

import java.io.File;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForkFront extends AppCompatActivity
{
	private NetHackViewModel mViewModel;
	private boolean mBackTracking;

	private final int REQUEST_EXTERNAL_STORAGE = 43;

	// Modern Activity Result API for settings
	private final ActivityResultLauncher<Intent> mSettingsLauncher =
		registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				// Called when Settings activity returns
				if (mViewModel != null && mViewModel.getState() != null) {
					mViewModel.getState().preferencesUpdated();
				}
			}
		);

	public interface RequestExternalStorageResult {
		void onGranted();
		void onDenied();
	}
	private RequestExternalStorageResult mRequestExternalStorageResult;

	// ____________________________________________________________________________________
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Log.print("onCreate");

		// Enable edge-to-edge display
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		// Complete edge-to-edge configuration
		Window window = getWindow();

		// Make system bars transparent
		window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
		window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

		// Enable drawing into display cutout area (notches)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			window.getAttributes().layoutInDisplayCutoutMode =
				WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}

		// Configure system bar appearance for dark content
		WindowInsetsControllerCompat insetsController =
			WindowCompat.getInsetsController(window, window.getDecorView());
		if (insetsController != null) {
			// Use dark icons for status/nav bars on light backgrounds
			// Set to false for dark theme (light icons on dark bars)
			insetsController.setAppearanceLightStatusBars(false);
			insetsController.setAppearanceLightNavigationBars(false);
		}

		if(DEBUG.isOn())
		{
			if(getResources().getString(R.string.namespace).length() == 0
			|| getResources().getString(R.string.nativeDataDir).length() == 0
			|| getResources().getString(R.string.libraryName).length() == 0
			|| getResources().getString(R.string.defaultsFile).length() == 0)
				throw new RuntimeException("missing config vars");
			if(getResources().getBoolean(R.bool.hearseAvailable))
			{
				if(getResources().getString(R.string.hearseClientName).length() == 0
				|| getResources().getString(R.string.hearseNethackVersion).length() == 0
				|| getResources().getString(R.string.hearseRoles).length() == 0)
					throw new RuntimeException("missing config vars");
			}
		}
		// turn off the window's title bar
		supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
		setDefaultKeyMode(DEFAULT_KEYS_DISABLE);
		// takeKeyEvents(true);

		setContentView(R.layout.mainwindow);

		// Apply window insets to avoid system bars cutting off UI elements
		View rootView = findViewById(R.id.base_frame);
		if (rootView != null) {
			ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
				Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
				Insets displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

				// Combine system bars and display cutout insets
				int topInset = Math.max(systemBars.top, displayCutout.top);
				int bottomInset = Math.max(systemBars.bottom, displayCutout.bottom);
				int leftInset = Math.max(systemBars.left, displayCutout.left);
				int rightInset = Math.max(systemBars.right, displayCutout.right);

				// Apply top padding to status display area to avoid status bar
				View statusContainer = findViewById(R.id.nh_stat0);
				if (statusContainer != null && statusContainer.getParent() instanceof View) {
					View parentLayout = (View) statusContainer.getParent();
					parentLayout.setPadding(
						leftInset,
						topInset,
						rightInset,
						parentLayout.getPaddingBottom());
				}


				return WindowInsetsCompat.CONSUMED;
			});
		}

		ensureReadWritePermissions(new RequestExternalStorageResult() {
			@Override
			public void onGranted() {
				goodToGo();
			}

			@Override
			public void onDenied() {
				finish();
			}
		});
	}

	private void goodToGo() {
		// Get or create ViewModel (survives configuration changes)
		mViewModel = new ViewModelProvider(this).get(NetHackViewModel.class);

		ByteDecoder decoder;
		if(getResources().getBoolean(R.bool.useCP437Decoder))
			decoder = new CP437();
		else
			decoder = new ByteDecoder() {
				@Override
				public char decode(int b) {
					return (char)b;
				}

				@Override
				public String decode(byte[] bytes) {
					return new String(bytes);
				}
			};

		// Initialize ViewModel with Application context (only happens once)
		mViewModel.initialize(getApplication(), decoder);

		// Attach current Activity context
		mViewModel.attachActivity(this);

		// Get progress UI elements
		View loadingOverlay = findViewById(R.id.loading_overlay);
		LinearProgressIndicator progressBar = findViewById(R.id.asset_progress);
		TextView progressText = findViewById(R.id.progress_text);

		// Start asset loading with progress callback
		UpdateAssets updateAssets = new UpdateAssets(
			this,
			onAssetsReady,
			(current, total) -> {
				if (progressBar != null && progressText != null) {
					int percentage = (total > 0) ? (int)((current * 100L) / total) : 0;
					progressBar.setMax(total);
					progressBar.setProgress(current);
					progressText.setText(percentage + "%");

					if (loadingOverlay != null && loadingOverlay.getVisibility() != View.VISIBLE) {
						loadingOverlay.setVisibility(View.VISIBLE);
					}
				}
			}
		);
		updateAssets.execute((Void[])null);
	}

	@RequiresApi(Build.VERSION_CODES.M)
	public void ensureReadWritePermissions(final RequestExternalStorageResult requestExternalStorageResult)	{
		// On Android 10+ (API 29+), WRITE_EXTERNAL_STORAGE is deprecated and apps use
		// scoped storage (getExternalFilesDir) which doesn't require permissions
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
		{
			requestExternalStorageResult.onGranted();
			return;
		}

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		{
			if(checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
			{
				if(mRequestExternalStorageResult == null)
				{
					mRequestExternalStorageResult = requestExternalStorageResult;
					requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
				}
				else
				{
					// Chain callbacks if several requests are activated in parallel. This shouldn't happen though.
					final RequestExternalStorageResult prevRequest = mRequestExternalStorageResult;
					mRequestExternalStorageResult = new RequestExternalStorageResult()
					{
						@Override
						public void onGranted()
						{
							prevRequest.onGranted();
							requestExternalStorageResult.onGranted();
						}

						@Override
						public void onDenied()
						{
							prevRequest.onDenied();
							requestExternalStorageResult.onDenied();
						}
					};
				}
			}
			else
			{
				requestExternalStorageResult.onGranted();
			}
		}
		else
		{
			requestExternalStorageResult.onGranted();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		Log.print("onRequestPermissionsResult");
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if(requestCode == REQUEST_EXTERNAL_STORAGE)
		{
			if(permissions.length == 1 && permissions[0].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
			&& grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				mRequestExternalStorageResult.onGranted();
			} else {
				mRequestExternalStorageResult.onDenied();
			}
		}
	}

	// ____________________________________________________________________________________
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		Log.print("onConfigurationChanged");
		NH_State nhState = mViewModel.getState();
		if (nhState != null) {
			nhState.onConfigurationChanged(newConfig);
		}
		super.onConfigurationChanged(newConfig);
	}

	// ____________________________________________________________________________________
	private UpdateAssets.Listener onAssetsReady = new UpdateAssets.Listener()
	{
		@Override
		public void onAssetsReady(File path)
		{
			// Hide loading overlay
			View loadingOverlay = findViewById(R.id.loading_overlay);
			if (loadingOverlay != null) {
				loadingOverlay.setVisibility(View.GONE);
			}

			// Create save directory if it doesn't exist
			File nhSaveDir = new File(path, "save");
			if(!nhSaveDir.exists())
				nhSaveDir.mkdir();

			PreferenceManager.setDefaultValues(ForkFront.this, R.xml.preferences, false);

			// Start engine through ViewModel
			mViewModel.startEngine(path.getAbsolutePath());
		}
	};

	// ____________________________________________________________________________________
	@Override
	protected void onStart()
	{

		Log.print("onStart");
		if(DEBUG.runTrace())
			Debug.startMethodTracing("nethack");
		super.onStart();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onResume()
	{

		Log.print("onResume");
		// Reattach Activity to ViewModel when resuming
		if (mViewModel != null) {
			mViewModel.attachActivity(this);
		}
		super.onResume();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onPause()
	{

		// Detach Activity from ViewModel when pausing
		if (mViewModel != null) {
			mViewModel.detachActivity();
		}
		super.onPause();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onStop()
	{

		Log.print("onStop");
		super.onStop();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDestroy()
	{

		Log.print("onDestroy()");
		// ViewModel's onCleared() will handle saveAndQuit() when Activity is truly finished
		// (not just being recreated for configuration change)
		super.onDestroy();
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{

		Log.print("onCreateOptionsMenu");
		menu.add(0, 1, 0, "Settings");

		return super.onCreateOptionsMenu(menu);
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{

		Log.print(String.format("onOptionsItemSelected(item=%d)", item.getItemId()));
		if(item.getItemId() == 1)
		{
			launchSettings();
			return true;
		}

		return false;
	}

	// ____________________________________________________________________________________
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{

		super.onCreateContextMenu(menu, v, menuInfo);
		mViewModel.getState().onCreateContextMenu(menu, v);
	}

	// ____________________________________________________________________________________
	public void onContextMenuClosed(Menu menu) {
		super.onContextMenuClosed(menu);
		mViewModel.getState().onContextMenuClosed();
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{

		mViewModel.getState().onContextItemSelected(item);
		return super.onContextItemSelected(item);
	}

	// ____________________________________________________________________________________
	/**
	 * Launch the settings activity.
	 * Public method for use by NH_State and other components.
	 */
	public void launchSettings()
	{
		Intent prefsActivity = new Intent(getBaseContext(), Settings.class);
		mSettingsLauncher.launch(prefsActivity);
	}

	// ____________________________________________________________________________________
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{

		Log.print("onSaveInstanceState(Bundle outState)");
		if(mViewModel != null && mViewModel.getState() != null)
			mViewModel.getState().saveState();
	}

	// ____________________________________________________________________________________
	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		// Handle back key long press manually
		if(event.getKeyCode() == KeyEvent.KEYCODE_BACK)
		{
			if(event.getAction() == KeyEvent.ACTION_DOWN)
			{
				if(event.getRepeatCount() == 0)
				{
					mBackTracking = true;
				}
				else if(mBackTracking && event.isLongPress())
				{
					launchSettings();
					mBackTracking = false;
				}
			}
			else if(event.getAction() == KeyEvent.ACTION_UP)
			{
				if(mBackTracking && !event.isCanceled())
				{
					EnumSet<Modifier> modifiers = Input.modifiersFromKeyEvent(event);
					handleKeyDown(event.getKeyCode(), event.getUnicodeChar(), event.getRepeatCount(), modifiers);
				}
				mBackTracking = false;
			}
			return true;
		}
		mBackTracking = false;

		if(event.getAction() == KeyEvent.ACTION_DOWN)
		{
			EnumSet<Modifier> modifiers = Input.modifiersFromKeyEvent(event);
			if(handleKeyDown(event.getKeyCode(), event.getUnicodeChar(), event.getRepeatCount(), modifiers))
				return true;
		}

		return super.dispatchKeyEvent(event);
	}

	// ____________________________________________________________________________________
	public boolean handleKeyDown(int keyCode, int unicodeChar, int repeatCount, EnumSet<Modifier> modifiers)
	{
		int fixedCode = Input.keyCodeToAction(keyCode, this);

		if(fixedCode == KeyEvent.KEYCODE_VOLUME_DOWN || fixedCode == KeyEvent.KEYCODE_VOLUME_UP)
			return false;

		char ch = (char)unicodeChar;

		int nhKey = Input.nhKeyFromKeyCode(fixedCode, ch, modifiers, mViewModel.getState().isNumPadOn());
		
		if(mViewModel.getState().handleKeyDown(ch, nhKey, fixedCode, modifiers, repeatCount, false))
			return true;

		if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
		{
			// Prevent default system sound from playing
			return true;
		}
		return false;
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		int fixedCode = Input.keyCodeToAction(keyCode, this);

		if(fixedCode == KeyEvent.KEYCODE_VOLUME_DOWN || fixedCode == KeyEvent.KEYCODE_VOLUME_UP)
			return false;

		if(mViewModel.getState().handleKeyUp(Input.keyCodeToAction(keyCode, this)))
			return true;
		if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
		{
			// Prevent default system sound from playing
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}
}
