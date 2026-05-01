package com.tbd.forkfront;
import com.tbd.forkfront.widgets.ControlWidget;

import android.app.Application;
import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.widgets.WidgetLayoutController;
import java.util.function.Supplier;

/**
 * JNI callback receiver for the NetHack engine.
 * Every method on this class runs on the Android main UI thread.
 */
public final class NhEngineCallbacks implements NH_Handler {
    private final Application mApp;
    private final WindowRegistry mWindows;
    private final ActivityScope mScope;
    private final com.tbd.forkfront.context.ContextualActionsEngine mContextActions;
    private final MapInputCoordinator mMapInput;
    private final EngineCommands mCommands;
    private final Supplier<NHW_Message> mMessageProvider;
    private final Supplier<NHW_Status> mStatusProvider;
    private final Supplier<NHW_Map> mMapProvider;
    private final NH_GetLine mGetLine;
    private final NH_CharacterPicker mPicker;
    private final NH_Question mQuestion;
    private final Tileset mTileset;
    private final NetHackIO mIO;
    private final SoundPlayer mSoundPlayer;
    private final WidgetLayoutController mWidgets;
    private final PreferencesCoordinator mPrefs;

    public NhEngineCallbacks(
            Application app,
            WindowRegistry windows,
            ActivityScope scope,
            com.tbd.forkfront.context.ContextualActionsEngine contextActions,
            MapInputCoordinator mapInput,
            EngineCommands commands,
            Supplier<NHW_Message> message,
            Supplier<NHW_Status> status,
            Supplier<NHW_Map> map,
            NH_GetLine getLine,
            NH_CharacterPicker picker,
            NH_Question question,
            Tileset tileset,
            NetHackIO io,
            SoundPlayer sound,
            WidgetLayoutController widgets,
            PreferencesCoordinator prefs) {
        mApp = app;
        mWindows = windows;
        mScope = scope;
        mContextActions = contextActions;
        mMapInput = mapInput;
        mCommands = commands;
        mMessageProvider = message;
        mStatusProvider = status;
        mMapProvider = map;
        mGetLine = getLine;
        mPicker = picker;
        mQuestion = question;
        mTileset = tileset;
        mIO = io;
        mSoundPlayer = sound;
        mWidgets = widgets;
        mPrefs = prefs;
    }

    @MainThread
    @Override
    public void setLastUsername(String username) {
        mPrefs.setLastUsername(username);
    }

    @MainThread
    @Override
    public void setCursorPos(int wid, int x, int y) {
        NH_Window wnd = mWindows.get(wid);
        if (wnd != null) {
            wnd.setCursorPos(x, y);
        }
    }

    @MainThread
    @Override
    public void putString(int wid, int attr, String msg, int append, int color) {
        NH_Window wnd = mWindows.get(wid);
        if (wnd == null) {
            Log.print("[no wnd] " + msg);
            NHW_Message message = mMessageProvider.get();
            if (message != null) {
                message.printString(attr, msg, append, color);
            }
        } else {
            wnd.printString(attr, msg, append, color);
        }
    }

    @MainThread
    @Override
    public void setHealthColor(int color) {
        NHW_Map map = mMapProvider.get();
        if (map != null) {
            map.setHealthColor(color);
        }
    }

    @MainThread
    @Override
    public void rawPrint(int attr, String msg) {
        NHW_Message message = mMessageProvider.get();
        if (message != null) {
            message.printString(attr, msg, 0, -1);
        }
    }

    @MainThread
    @Override
    public void printTile(int wid, int x, int y, int tile, int bkglyph, int ch, int col, int special) {
        NHW_Map map = mMapProvider.get();
        if (map != null) {
            map.printTile(x, y, tile, bkglyph, ch, col, special);
        }
    }

    @MainThread
    @Override
    public void ynFunction(String question, byte[] choices, int def) {
        mScope.runOnActivity(() -> {
            AppCompatActivity activity = mScope.getActivity();
            if (activity != null) {
                mQuestion.show(activity, question, choices, def);
            }
        });
    }

    @MainThread
    @Override
    public void getLine(String title, int nMaxChars, boolean showLog) {
        mScope.runOnActivity(() -> {
            AppCompatActivity activity = mScope.getActivity();
            if (activity != null) {
                NHW_Message message = mMessageProvider.get();
                if (showLog && message != null) {
                    mGetLine.show(activity, message.getLogLine(2) + title, nMaxChars);
                } else {
                    mGetLine.show(activity, title, nMaxChars);
                }
            }
        });
    }

    @MainThread
    @Override
    public void askName(int nMaxChars, String[] saves) {
        mScope.runOnActivity(() -> {
            if (mScope.getActivity() != null) {
                mPicker.show(nMaxChars, saves);
            }
        });
    }

    @MainThread
    @Override
    public void loadSound(String filename) {
        mSoundPlayer.load(filename);
    }

    @MainThread
    @Override
    public void playSound(String filename, int volume) {
        mSoundPlayer.play(filename, volume);
    }

    @MainThread
    @Override
    public void createWindow(int wid, int type) {
        switch (type) {
            case 1: // #define NHW_MESSAGE 1
            {
                NHW_Message message = mMessageProvider.get();
                if (message != null) {
                    message.setId(wid);
                    mWindows.add(message);
                }
            }
            break;

            case 2: // #define NHW_STATUS 2
            {
                NHW_Status status = mStatusProvider.get();
                if (status != null) {
                    status.setId(wid);
                    mWindows.add(status);
                }
            }
            break;

            case 3: // #define NHW_MAP 3
            {
                NHW_Map map = mMapProvider.get();
                if (map != null) {
                    map.setId(wid);
                    mWindows.add(map);
                }
            }
            break;

            case 4: // #define NHW_MENU 4
                mScope.runOnActivity(() -> {
                    AppCompatActivity activity = mScope.getActivity();
                    if (activity != null) {
                        mWindows.add(new NHW_Menu(wid, activity, mIO, mTileset));
                    }
                });
            break;

            case 5: // #define NHW_TEXT 5
                mScope.runOnActivity(() -> {
                    AppCompatActivity activity = mScope.getActivity();
                    if (activity != null) {
                        mWindows.add(new NHW_Text(wid, activity, mIO));
                    }
                });
            break;
        }
    }

    @MainThread
    @Override
    public void displayWindow(int wid, int bBlocking) {
        mScope.runOnActivity(() -> {
            NH_Window win = mWindows.toFront(wid);
            if (win != null) {
                win.show(bBlocking != 0);
            }
        });
    }

    @MainThread
    @Override
    public void clearWindow(int wid, int isRogueLevel) {
        NH_Window wnd = mWindows.get(wid);
        if (wnd != null) {
            wnd.clear();
            NHW_Map map = mMapProvider.get();
            if (wnd == map && map != null) {
                map.setRogueLevel(isRogueLevel != 0);
            }
        }
    }

    @MainThread
    @Override
    public void destroyWindow(int wid) {
        if (!mWindows.remove(wid)) {
            Log.print("destroyWindow: unknown wid " + wid);
        }
    }

    @MainThread
    @Override
    public void startMenu(int wid) {
        NHW_Menu menu = (NHW_Menu) mWindows.get(wid);
        if (menu != null) {
            menu.startMenu();
        }
    }

    @MainThread
    @Override
    public void addMenu(int wid, int tile, long id, int acc, int groupAcc, int attr, String text, int bSelected, int color) {
        NHW_Menu menu = (NHW_Menu) mWindows.get(wid);
        if (menu != null) {
            menu.addMenu(tile, id, acc, groupAcc, attr, text, bSelected, color);
        }
    }

    @MainThread
    @Override
    public void endMenu(int wid, String prompt) {
        NHW_Menu menu = (NHW_Menu) mWindows.get(wid);
        if (menu != null) {
            menu.endMenu(prompt);
        }
    }

    @MainThread
    @Override
    public void selectMenu(int wid, int how) {
        mScope.runOnActivity(() -> {
            NHW_Menu menu = (NHW_Menu) mWindows.toFront(wid);
            if (menu != null) {
                menu.selectMenu(MenuSelectMode.fromInt(how));
            }
        });
    }

    @MainThread
    @Override
    public void cliparound(int x, int y, int playerX, int playerY, int objectFlags, int nearbyMonsters) {
        mContextActions.onCliparound(x, y, playerX, playerY, objectFlags, nearbyMonsters);
    }

    @MainThread
    @Override
    public void showLog(int bBlocking) {
        NHW_Message message = mMessageProvider.get();
        if (message != null) {
            message.showLog(bBlocking != 0);
        }
    }

    @MainThread
    @Override
    public void editOpts() {
    }

    @MainThread
    @Override
    public void lockMouse() {
        mCommands.setMouseLocked(true);
        mMapInput.enterMapCursorMode();
    }

    @MainThread
    @Override
    public void highlightDPad() {
        mCommands.setDPadActive(true);
        mScope.runOnActivity(() -> {
            AppCompatActivity activity = mScope.getActivity();
            if (activity != null) {
                ControlWidget existingDPad = WidgetLayoutController.findDpadWidget(mWidgets.getPrimaryWidgetLayout());
                if (existingDPad == null) {
                    existingDPad = WidgetLayoutController.findDpadWidget(mWidgets.getSecondaryWidgetLayout());
                }

                if (existingDPad != null) {
                    existingDPad.pulseAttention();
                }
            }
        });
    }

    @MainThread
    @Override
    public void hideDPad() {
        mCommands.setDPadActive(false);
        // updateVisibleState() in NH_State is empty, so skipping for now.
        // If we need to preserve the call chain, we'd need NH_State reference.
    }

    @MainThread
    @Override
    public void setNumPadOption(boolean numPadOn) {
        mCommands.setNumPad(numPadOn);
    }

    @MainThread
    @Override
    public void statusInit() {
        NHW_Status status = mStatusProvider.get();
        if (status != null) {
            status.statusInit();
        }
    }

    @MainThread
    @Override
    public void statusEnableField(int fieldIdx, String name, String fmt, boolean enable) {
        NHW_Status status = mStatusProvider.get();
        if (status != null) {
            status.statusEnableField(fieldIdx, name, fmt, enable);
        }
    }

    @MainThread
    @Override
    public void statusUpdate(int fieldIdx, String value, long conditionMask, int chg, int percent, int color, long[] colormasks) {
        NHW_Status status = mStatusProvider.get();
        if (status != null) {
            status.statusUpdate(fieldIdx, value, conditionMask, chg, percent, color, colormasks);
        }
    }

    @MainThread
    @Override
    public void statusFinish() {
        NHW_Status status = mStatusProvider.get();
        if (status != null) {
            status.statusFinish();
        }
    }

    @MainThread
    @Override
    public void redrawStatus() {
        NHW_Status status = mStatusProvider.get();
        if (status != null) {
            status.redraw();
        }
    }
}
