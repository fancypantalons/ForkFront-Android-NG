package com.tbd.forkfront;

import android.app.Application;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;

import java.util.LinkedList;
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
    private AppCompatActivity mCurrentActivity;

    public NetHackViewModel() {
        super();
        Log.d(TAG, "NetHackViewModel created");
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
            Log.d(TAG, "Initializing NetHackViewModel with Application context");
            // Create NH_State first (it needs NetHackIO, but we'll create with null handler)
            mNetHackIO = new NetHackIO(app, null, decoder);
            mNHState = new NH_State(app, decoder, mNetHackIO);
            // Inject NH_State's handler into NetHackIO after both are created
            try {
                java.lang.reflect.Field handlerField = NetHackIO.class.getDeclaredField("mNhHandler");
                handlerField.setAccessible(true);
                handlerField.set(mNetHackIO, mNHState.getNhHandler());
            } catch (Exception e) {
                throw new RuntimeException("Failed to inject NhHandler into NetHackIO", e);
            }
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
        Log.d(TAG, "NetHackViewModel cleared - saving and quitting");
        if (mNHState != null) {
            mNHState.saveAndQuit();
        }
        super.onCleared();
    }
}
