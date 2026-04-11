package com.tbd.forkfront;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.tbd.forkfront.Tileset;
import com.tbd.forkfront.Util;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class TilesetPreference extends Preference {
    private final String TTY = "TTY";

    private List<String> mEntries;
    private List<String> mEntryValues;
    private TextView mTilesetPath;
    private EditText mTileW;
    private EditText mTileH;
    private ViewGroup mTilesetUI;
    private LinearLayout mRoot;
    private String mCustomTilesetPath;
    private Bitmap mCustomTileset;
    private ImageButton mBrowse;
    private boolean mTileWFocus;
    private boolean mTileHFocus;
    private ImagePickerLauncher mImagePickerLauncher;

    public interface ImagePickerLauncher {
        void launchImagePicker();
    }

    public TilesetPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEntries = Arrays.asList(context.getResources().getStringArray(R.array.tileNames));
        mEntryValues = Arrays.asList(context.getResources().getStringArray(R.array.tileValues));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mRoot = (LinearLayout) holder.itemView;

        if (mRoot.getChildCount() < mEntries.size() + 1) {
            createChoices();
        }

        mTilesetUI = (ViewGroup) mRoot.findViewById(R.id.customTilesUI);
        mTileW = (EditText) mRoot.findViewById(R.id.tileW);
        mTileH = (EditText) mRoot.findViewById(R.id.tileH);

        RadioButton customBtn = (RadioButton) mRoot.findViewById(R.id.custom_tiles);
        if (customBtn != null) {
            customBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setCustomUIEnabled(isChecked);
                }
            });
        }

        mBrowse = (ImageButton) mRoot.findViewById(R.id.browse);
        if (mBrowse != null) {
            mBrowse.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choseCustomTilesetImage();
                }
            });
        }

        mTilesetPath = (TextView) mRoot.findViewById(R.id.image_path);

        if (mTileW != null && mTileH != null) {
            mTileW.setSelectAllOnFocus(true);
            mTileH.setSelectAllOnFocus(true);

            TextView.OnEditorActionListener onEditorActionListener = new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        mTileWFocus = false;
                        mTileHFocus = false;
                    }
                    return false;
                }
            };
            mTileW.setOnEditorActionListener(onEditorActionListener);
            mTileH.setOnEditorActionListener(onEditorActionListener);

            mTileW.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    mTileWFocus = hasFocus;
                }
            });

            mTileH.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    mTileHFocus = hasFocus;
                }
            });
        }

        bindViewLogic();
    }

    private void bindViewLogic() {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs == null) return;

        String currentValue = prefs.getString("tileset", TTY);

        int i = mEntryValues.indexOf(currentValue);
        if (i < 0) i = mEntryValues.indexOf(TTY);

        if (!prefs.getBoolean("customTiles", false)) {
            View v = mRoot.getChildAt(i);
            if (v instanceof RadioButton) {
                ((RadioButton) v).setChecked(true);
            }
        }

        if (mTilesetPath != null) mTilesetPath.setText(prefs.getString("customTileset", ""));
        if (mTileW != null) mTileW.setText(Integer.toString(prefs.getInt("customTileW", 32)));
        if (mTileH != null) mTileH.setText(Integer.toString(prefs.getInt("customTileH", 32)));
        updateTileIcon();

        if (mTileW != null) mTileW.addTextChangedListener(updateCustom);
        if (mTileH != null) mTileH.addTextChangedListener(updateCustom);
        if (mTilesetPath != null) mTilesetPath.addTextChangedListener(updateCustom);

        if (mTileWFocus && mTileW != null) mTileW.requestFocus();
        else if (mTileHFocus && mTileH != null) mTileH.requestFocus();

        mTileWFocus = false;
        mTileHFocus = false;
    }

    private void choseCustomTilesetImage() {
        if (mImagePickerLauncher != null) {
            mImagePickerLauncher.launchImagePicker();
        }
    }

    public void handleImageResult(Uri imageUri) {
        if (imageUri != null && createCustomTilesetLocalCopy(imageUri)) {
            String path = queryPath(imageUri);
            if (mTilesetPath != null) mTilesetPath.setText(path);
        }
    }

    private boolean createCustomTilesetLocalCopy(Uri from) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = getContext().getContentResolver().openInputStream(from);
            File file = Tileset.getLocalTilesetFile(getContext());
            outputStream = new FileOutputStream(file, false);
            Util.copy(inputStream, outputStream);
            return true;
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error loading tileset", Toast.LENGTH_LONG).show();
        } finally {
            if (inputStream != null) try { inputStream.close(); } catch (IOException e) {}
            if (outputStream != null) try { outputStream.close(); } catch (IOException e) {}
        }
        return false;
    }

    public String queryPath(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContext().getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            try {
                int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                if (column_index >= 0 && cursor.moveToFirst()) {
                    String path = cursor.getString(column_index);
                    if (path != null && path.length() > 0) return path;
                }
            } finally {
                cursor.close();
            }
        }
        return uri.getPath();
    }

    private void setCustomUIEnabled(boolean enabled) {
        if (enabled) persistCustom();
        if (mTilesetUI != null) setTreeEnabled(mTilesetUI, enabled);
        if (mTilesetPath != null) mTilesetPath.setEnabled(false);

        if (!enabled) {
            if (mTileW != null) mTileW.clearFocus();
            if (mTileH != null) mTileH.clearFocus();
            mTileWFocus = false;
            mTileHFocus = false;
        }
    }

    private void setTreeEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                setTreeEnabled(group.getChildAt(i), enabled);
        }
    }

    private void createChoices() {
        if (mRoot == null) return;
        for (int i = mEntries.size() - 1; i >= 0; i--) {
            RadioButton button = new RadioButton(getContext());
            button.setText(mEntries.get(i));
            button.setTag(mEntryValues.get(i));
            button.setOnCheckedChangeListener(tilesetChecked);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mRoot.addView(button, 0, params);
        }
    }

    private void persistTileset(String id, int tileW, int tileH, boolean custom) {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("tileset", id);
            editor.putInt("tileW", tileW);
            editor.putInt("tileH", tileH);
            editor.putBoolean("customTiles", custom);
            if (custom) {
                editor.putString("customTileset", id);
                editor.putInt("customTileW", tileW);
                editor.putInt("customTileH", tileH);
            }
            editor.commit();
        }
    }

    private void updateTileIcon() {
        if (mRoot == null) return;

        SharedPreferences prefs = getSharedPreferences();
        if (prefs == null) return;

        boolean custom = prefs.getBoolean("customTiles", false);
        String id = prefs.getString("tileset", TTY);

        if (!custom && id.equals(TTY)) {
        } else {
            Bitmap bmp = null;
            if (custom) {
                if (mCustomTileset == null || !mCustomTilesetPath.equals(id)) {
                    File file = Tileset.getLocalTilesetFile(getContext());
                    if (file.exists()) {
                        mCustomTileset = BitmapFactory.decodeFile(file.getAbsolutePath());
                        mCustomTilesetPath = id;
                    }
                }
                bmp = mCustomTileset;
            } else {
                int resID = getContext().getResources().getIdentifier(id, "drawable", getContext().getPackageName());
                if (resID != 0) {
                    Drawable drawable = getContext().getResources().getDrawable(resID);
                    if (drawable instanceof BitmapDrawable) bmp = ((BitmapDrawable) drawable).getBitmap();
                }
            }

            if (bmp != null) {
                int tw = prefs.getInt("tileW", 32);
                int th = prefs.getInt("tileH", 32);
                if (tw > 0 && th > 0 && bmp.getWidth() >= tw && bmp.getHeight() >= th) {
                    Bitmap tile = Bitmap.createBitmap(bmp, 0, 0, tw, th);
                } else {
                }
            } else {
            }
        }
    }

    private CompoundButton.OnCheckedChangeListener tilesetChecked = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                String id = (String) buttonView.getTag();
                if (id != null) {
                    int tileW = 32, tileH = 32;
                    if (!id.equals(TTY)) {
                        int ih = id.lastIndexOf('x');
                        if (ih > 0) {
                            tileW = Integer.parseInt(id.substring(id.lastIndexOf('_') + 1, ih));
                            tileH = tileW;
                            if (ih != id.length())
                                tileH = Integer.parseInt(id.substring(ih + 1, id.length()));
                        }
                    }
                    persistTileset(id, tileW, tileH, false);
                }
            }
        }
    };

    private TextWatcher updateCustom = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            persistCustom();
        }
    };

    private void persistCustom() {
        try {
            int w = 0;
            if (mTileW != null) w = Integer.parseInt(mTileW.getText().toString());
            int h = 0;
            if (mTileH != null) h = Integer.parseInt(mTileH.getText().toString());
            String path = mTilesetPath != null ? mTilesetPath.getText().toString() : "";
            if (w > 0 && h > 0 && path.length() > 0) {
                persistTileset(path, w, h, true);
            }
        } catch (NumberFormatException e) {
        }
    }

    public void setImagePickerLauncher(ImagePickerLauncher launcher) {
        mImagePickerLauncher = launcher;
    }
}
