package com.tbd.forkfront.engine;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.engine.NetHackIO;

import android.app.Application;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * ViewModel for managing NetHack engine lifecycle and state.
 *
 * Survives configuration changes (like screen rotation) and manages the NetHack
 * engine thread, game state, and JNI callbacks using Application context to avoid
 * Activity leaks.
 */
public class NetHackViewModel extends ViewModel {
    private static final String TAG = "NetHackViewModel";

    private NetHackIO mNetHackIO;
    private NH_State mNHState;
    private boolean mIsEngineStarted = false;
    private String mDataPath;

    // Pending UI operations that require an Activity context
    private final Queue<Runnable> mPendingUIOperations = new LinkedList<>();
    private volatile AppCompatActivity mCurrentActivity;  // Volatile for cross-thread visibility

    public NetHackViewModel() {
        super();
        android.util.Log.d(TAG, "NetHackViewModel created");
    }

    /**
     * Initialize the ViewModel with Application context and decoder.
     * Creates NetHackIO and NH_State internally using Application context.
     * This only happens once - on first creation.
     *
     * @param app Application context (survives Activity destruction)
     * @param decoder ByteDecoder for NetHack protocol
     */
    public void initialize(Application app, ByteDecoder decoder) {
        if (mNHState == null) {
            android.util.Log.d(TAG, "Initializing NetHackViewModel with Application context");

            // Create NetHackIO with null handler initially
            mNetHackIO = new NetHackIO(app, null, decoder);

            // Create NH_State which creates the handler
            mNHState = new NH_State(app, decoder, mNetHackIO);

            // Set ViewModel reference for deferred UI operations
            mNHState.setViewModel(this);

            // Inject handler using proper API (not reflection)
            mNetHackIO.setHandler(mNHState.getNhHandler());
        } else {
            android.util.Log.d(TAG, "NetHackViewModel already initialized, skipping");
        }
    }

    /**
     * Attach an Activity to the ViewModel.
     * Called when Activity is created or recreated (e.g., after rotation).
     * Processes any pending UI operations that were queued while no Activity was available.
     *
     * @param activity Current Activity instance
     */
    public void attachActivity(AppCompatActivity activity) {
        android.util.Log.d(TAG, "Attaching Activity: " + activity.getClass().getSimpleName());

        synchronized (this) {
            mCurrentActivity = activity;

            if (mNHState != null) {
                mNHState.setContext(activity);
            }
        }

        // Process pending operations without holding lock (prevents deadlock)
        List<Runnable> pending = new ArrayList<>();
        synchronized (mPendingUIOperations) {
            pending.addAll(mPendingUIOperations);
            mPendingUIOperations.clear();
        }

        int queueDepth = pending.size();
        if (queueDepth > 0) {
            android.util.Log.d(TAG, "Processing " + queueDepth + " pending UI operations");
        }

        // Execute on UI thread without holding any locks
        for (Runnable op : pending) {
            activity.runOnUiThread(op);
        }
    }

    /**
     * Detach the current Activity from the ViewModel.
     * Called when Activity is paused (e.g., going to background).
     * UI operations will be queued until a new Activity is attached.
     */
    public void detachActivity() {
        synchronized (this) {
            if (mCurrentActivity != null) {
                android.util.Log.d(TAG, "Detaching Activity: " + mCurrentActivity.getClass().getSimpleName());
                mCurrentActivity = null;
            }
        }
    }

    /**
     * Run an operation that requires an Activity context.
     * Always posts to UI thread for thread safety.
     * If an Activity is currently attached and not finishing, posts immediately.
     * Otherwise, queues the operation until an Activity becomes available.
     *
     * @param operation Runnable to execute on Activity (will run on UI thread)
     */
    public void runOnActivity(Runnable operation) {
        synchronized (this) {
            if (mCurrentActivity != null && !mCurrentActivity.isFinishing()) {
                mCurrentActivity.runOnUiThread(operation);
                android.util.Log.d(TAG, "Posted UI operation to current Activity");
                return;
            }
            mPendingUIOperations.add(operation);
            android.util.Log.d(TAG, "Queued UI operation (queue depth: " + mPendingUIOperations.size() + ")");
        }
    }

    /**
     * Start the NetHack engine thread.
     * Only starts once - subsequent calls are ignored.
     *
     * @param dataPath Path to NetHack data directory
     */
    public void startEngine(String dataPath) {
        if (!mIsEngineStarted && mNHState != null) {
            android.util.Log.d(TAG, "Starting NetHack engine with data path: " + dataPath);
            mDataPath = dataPath;
            mNHState.startNetHack(dataPath);
            mIsEngineStarted = true;
        } else if (mIsEngineStarted) {
            android.util.Log.d(TAG, "Engine already started, ignoring startEngine() call");
        }
    }

    /**
     * Get the current NetHack state.
     *
     * @return NH_State instance, or null if not yet initialized
     */
    public NH_State getState() {
        return mNHState;
    }

    /**
     * Called when the ViewModel is being cleared (Activity is truly finished, not just rotated).
     * Saves the game and performs cleanup.
     */
    @Override
    protected void onCleared() {
        android.util.Log.d(TAG, "NetHackViewModel cleared - saving and quitting");
        if (mNHState != null) {
            mNHState.getCommands().saveAndQuit();
        }
        super.onCleared();
    }
}
