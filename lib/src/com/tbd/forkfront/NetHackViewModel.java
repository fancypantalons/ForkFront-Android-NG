package com.tbd.forkfront;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ViewModel for managing NetHack engine lifecycle and state.
 *
 * Survives configuration changes (like screen rotation) and manages the NetHack
 * engine thread, game state, and JNI callbacks.
 *
 * NOTE: This is Phase 4.2 - foundation only. Full initialization will be
 * implemented in later phases after NH_State and NetHackIO are refactored.
 */
public class NetHackViewModel extends ViewModel {
    private static final String TAG = "NetHackViewModel";

    private NH_State mNHState;
    private boolean mIsEngineStarted = false;
    private String mDataPath;

    // Pending UI operations that require an Activity context
    private final Queue<Runnable> mPendingUIOperations = new LinkedList<>();
    private AppCompatActivity mCurrentActivity;

    public NetHackViewModel() {
        super();
        Log.d(TAG, "NetHackViewModel created");
    }

    /**
     * Initialize the ViewModel with NH_State instance.
     * For now, accepts existing NH_State created by Activity.
     * Will be refactored in Phase 4.3-4.5 to create state internally.
     *
     * @param nhState NH_State instance
     */
    public void initialize(NH_State nhState) {
        if (mNHState == null) {
            Log.d(TAG, "Initializing NetHackViewModel with NH_State");
            mNHState = nhState;
        } else {
            Log.d(TAG, "NetHackViewModel already initialized, skipping");
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
        Log.d(TAG, "Attaching Activity: " + activity.getClass().getSimpleName());
        mCurrentActivity = activity;

        if (mNHState != null) {
            mNHState.setContext(activity);
        }

        // Process any pending UI operations
        synchronized (mPendingUIOperations) {
            int queueDepth = mPendingUIOperations.size();
            if (queueDepth > 0) {
                Log.d(TAG, "Processing " + queueDepth + " pending UI operations");
            }
            while (!mPendingUIOperations.isEmpty()) {
                Runnable op = mPendingUIOperations.poll();
                if (op != null) {
                    op.run();
                }
            }
        }
    }

    /**
     * Detach the current Activity from the ViewModel.
     * Called when Activity is paused (e.g., going to background).
     * UI operations will be queued until a new Activity is attached.
     */
    public void detachActivity() {
        if (mCurrentActivity != null) {
            Log.d(TAG, "Detaching Activity: " + mCurrentActivity.getClass().getSimpleName());
            mCurrentActivity = null;
        }
    }

    /**
     * Run an operation that requires an Activity context.
     * If an Activity is currently attached and not finishing, runs immediately.
     * Otherwise, queues the operation until an Activity becomes available.
     *
     * @param operation Runnable to execute on Activity
     */
    public void runOnActivity(Runnable operation) {
        if (mCurrentActivity != null && !mCurrentActivity.isFinishing()) {
            operation.run();
        } else {
            // Queue for when Activity becomes available
            synchronized (mPendingUIOperations) {
                mPendingUIOperations.add(operation);
                Log.d(TAG, "Queued UI operation (queue depth: " + mPendingUIOperations.size() + ")");
            }
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
            Log.d(TAG, "Starting NetHack engine with data path: " + dataPath);
            mDataPath = dataPath;
            mNHState.startNetHack(dataPath);
            mIsEngineStarted = true;
        } else if (mIsEngineStarted) {
            Log.d(TAG, "Engine already started, ignoring startEngine() call");
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
        Log.d(TAG, "NetHackViewModel cleared - cleanup will be implemented in later phase");
        // TODO Phase 4.5: Implement proper cleanup (saveAndQuit)
        super.onCleared();
    }
}
