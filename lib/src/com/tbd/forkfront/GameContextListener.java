package com.tbd.forkfront;

import java.util.List;

/**
 * Listener interface for consumers that react to changes in the current
 * game context, such as the set of contextually-relevant commands.
 */
public interface GameContextListener {
    void onContextualActionsChanged(List<CmdRegistry.CmdInfo> actions);
}
