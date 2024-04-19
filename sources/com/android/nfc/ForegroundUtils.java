package com.android.nfc;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
/* loaded from: classes.dex */
public class ForegroundUtils extends IProcessObserver.Stub {
    static final boolean DBG = false;
    private final String TAG;
    private final SparseArray<List<Callback>> mBackgroundCallbacks;
    private final SparseArray<SparseBooleanArray> mForegroundUidPids;
    private final IActivityManager mIActivityManager;
    private final Object mLock;

    /* loaded from: classes.dex */
    public interface Callback {
        void onUidToBackground(int i);
    }

    /* loaded from: classes.dex */
    private static class Singleton {
        private static final ForegroundUtils INSTANCE = new ForegroundUtils();

        private Singleton() {
        }
    }

    private ForegroundUtils() {
        this.TAG = "ForegroundUtils";
        this.mLock = new Object();
        this.mForegroundUidPids = new SparseArray<>();
        this.mBackgroundCallbacks = new SparseArray<>();
        this.mIActivityManager = ActivityManager.getService();
        try {
            this.mIActivityManager.registerProcessObserver(this);
        } catch (RemoteException e) {
            Log.e("ForegroundUtils", "ForegroundUtils: could not get IActivityManager");
        }
    }

    public static ForegroundUtils getInstance() {
        return Singleton.INSTANCE;
    }

    public boolean registerUidToBackgroundCallback(Callback callback, int uid) {
        synchronized (this.mLock) {
            if (!isInForegroundLocked(uid)) {
                return DBG;
            }
            List<Callback> callbacks = this.mBackgroundCallbacks.get(uid, new ArrayList());
            callbacks.add(callback);
            this.mBackgroundCallbacks.put(uid, callbacks);
            return true;
        }
    }

    public boolean isInForeground(int uid) {
        boolean isInForegroundLocked;
        synchronized (this.mLock) {
            isInForegroundLocked = isInForegroundLocked(uid);
        }
        return isInForegroundLocked;
    }

    public List<Integer> getForegroundUids() {
        ArrayList<Integer> uids = new ArrayList<>(this.mForegroundUidPids.size());
        synchronized (this.mLock) {
            for (int i = 0; i < this.mForegroundUidPids.size(); i++) {
                uids.add(Integer.valueOf(this.mForegroundUidPids.keyAt(i)));
            }
        }
        return uids;
    }

    private boolean isInForegroundLocked(int uid) {
        if (this.mForegroundUidPids.get(uid) != null) {
            return true;
        }
        return DBG;
    }

    private void handleUidToBackground(int uid) {
        ArrayList<Callback> pendingCallbacks = null;
        synchronized (this.mLock) {
            List<Callback> callbacks = this.mBackgroundCallbacks.get(uid);
            if (callbacks != null) {
                pendingCallbacks = new ArrayList<>(callbacks);
                this.mBackgroundCallbacks.remove(uid);
            }
        }
        if (pendingCallbacks != null) {
            Iterator<Callback> it = pendingCallbacks.iterator();
            while (it.hasNext()) {
                Callback callback = it.next();
                callback.onUidToBackground(uid);
            }
        }
    }

    public void onForegroundActivitiesChanged(int pid, int uid, boolean hasForegroundActivities) throws RemoteException {
        boolean uidToBackground = DBG;
        synchronized (this.mLock) {
            SparseBooleanArray foregroundPids = this.mForegroundUidPids.get(uid, new SparseBooleanArray());
            if (hasForegroundActivities) {
                foregroundPids.put(pid, true);
            } else {
                foregroundPids.delete(pid);
            }
            if (foregroundPids.size() == 0) {
                this.mForegroundUidPids.remove(uid);
                uidToBackground = true;
            } else {
                this.mForegroundUidPids.put(uid, foregroundPids);
            }
        }
        if (uidToBackground) {
            handleUidToBackground(uid);
        }
    }

    public void onForegroundServicesChanged(int pid, int uid, int fgServiceTypes) {
    }

    public void onProcessDied(int pid, int uid) throws RemoteException {
        onForegroundActivitiesChanged(pid, uid, DBG);
    }
}
