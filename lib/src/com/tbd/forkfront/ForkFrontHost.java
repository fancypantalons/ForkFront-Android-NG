package com.tbd.forkfront;
import com.tbd.forkfront.context.CmdRegistry;

import java.util.function.Consumer;

/**
 * Narrow interface for ForkFront activity to break circular dependencies
 * with WidgetLayoutController and NH_State.
 */
public interface ForkFrontHost {
    void expandCommandPalette(CmdRegistry.OnCommandListener onSelected);
    void setDrawerEditMode(boolean enabled);
    void launchSettings();
}
