package com.tbd.forkfront.gamepad;
import com.tbd.forkfront.ForkFront;
import com.tbd.forkfront.NH_State;

/**
 * Maps UiActionId values to calls into ForkFront / NH_State.
 * The host provides the ActionHost interface implementation.
 */
public class UiActionExecutor {

    public interface ActionHost {
        void openDrawer();
        void openSettings();
        void openCommandPalette();
        void openCommandPicker();
        void toggleKeyboard();
        void zoomIn();
        void zoomOut();
        void toggleMapLock();
        void recenterMap();
        void resendLastCmd();
    }

    private final ActionHost mHost;

    public UiActionExecutor(ActionHost host) {
        this.mHost = host;
    }

    public void execute(UiActionId id) {
        if (mHost == null) return;
        switch (id) {
            case OPEN_DRAWER:          mHost.openDrawer();          break;
            case OPEN_SETTINGS:        mHost.openSettings();        break;
            case OPEN_COMMAND_PALETTE: mHost.openCommandPalette();  break;
            case OPEN_COMMAND_PICKER:  mHost.openCommandPicker();   break;
            case TOGGLE_KEYBOARD:      mHost.toggleKeyboard();      break;
            case ZOOM_IN:              mHost.zoomIn();              break;
            case ZOOM_OUT:             mHost.zoomOut();             break;
            case TOGGLE_MAP_LOCK:      mHost.toggleMapLock();       break;
            case RECENTER_MAP:         mHost.recenterMap();         break;
            case RESEND_LAST_CMD:      mHost.resendLastCmd();       break;
        }
    }
}
