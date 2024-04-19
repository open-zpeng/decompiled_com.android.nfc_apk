package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.nfc.cardemulation.NfcFServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.android.nfc.ForegroundUtils;
import java.io.FileDescriptor;
import java.io.PrintWriter;
/* loaded from: classes.dex */
public class EnabledNfcFServices implements ForegroundUtils.Callback {
    static final boolean DBG = false;
    static final String TAG = "EnabledNfcFCardEmulationServices";
    final Callback mCallback;
    final Context mContext;
    final RegisteredNfcFServicesCache mNfcFServiceCache;
    final RegisteredT3tIdentifiersCache mT3tIdentifiersCache;
    final ForegroundUtils mForegroundUtils = ForegroundUtils.getInstance();
    final Handler mHandler = new Handler(Looper.getMainLooper());
    final Object mLock = new Object();
    ComponentName mForegroundComponent = null;
    ComponentName mForegroundRequested = null;
    int mForegroundUid = -1;
    boolean mComputeFgRequested = DBG;
    boolean mActivated = DBG;

    /* loaded from: classes.dex */
    public interface Callback {
        void onEnabledForegroundNfcFServiceChanged(ComponentName componentName);
    }

    public EnabledNfcFServices(Context context, RegisteredNfcFServicesCache nfcFServiceCache, RegisteredT3tIdentifiersCache t3tIdentifiersCache, Callback callback) {
        this.mContext = context;
        this.mNfcFServiceCache = nfcFServiceCache;
        this.mT3tIdentifiersCache = t3tIdentifiersCache;
        this.mCallback = callback;
    }

    void computeEnabledForegroundService() {
        boolean changed = DBG;
        synchronized (this.mLock) {
            if (this.mActivated) {
                Log.d(TAG, "configuration will be postponed until deactivation");
                this.mComputeFgRequested = true;
                return;
            }
            this.mComputeFgRequested = DBG;
            ComponentName foregroundRequested = this.mForegroundRequested;
            if (this.mForegroundRequested != null && (this.mForegroundComponent == null || !this.mForegroundRequested.equals(this.mForegroundComponent))) {
                this.mForegroundComponent = this.mForegroundRequested;
                changed = true;
            } else if (this.mForegroundRequested == null && this.mForegroundComponent != null) {
                this.mForegroundComponent = this.mForegroundRequested;
                changed = true;
            }
            if (changed) {
                this.mCallback.onEnabledForegroundNfcFServiceChanged(foregroundRequested);
            }
        }
    }

    public void onServicesUpdated() {
        boolean changed = DBG;
        synchronized (this.mLock) {
            if (this.mForegroundComponent != null) {
                Log.d(TAG, "Removing foreground enabled service because of service update.");
                this.mForegroundRequested = null;
                this.mForegroundUid = -1;
                changed = true;
            }
        }
        if (changed) {
            computeEnabledForegroundService();
        }
    }

    public boolean registerEnabledForegroundService(ComponentName service, int callingUid) {
        boolean success = DBG;
        synchronized (this.mLock) {
            NfcFServiceInfo serviceInfo = this.mNfcFServiceCache.getService(ActivityManager.getCurrentUser(), service);
            if (serviceInfo == null) {
                return DBG;
            }
            if (!serviceInfo.getSystemCode().equalsIgnoreCase("NULL") && !serviceInfo.getNfcid2().equalsIgnoreCase("NULL") && !serviceInfo.getT3tPmm().equalsIgnoreCase("NULL")) {
                if (service.equals(this.mForegroundRequested)) {
                    Log.e(TAG, "The servcie is already requested to the foreground service.");
                    return true;
                }
                if (this.mForegroundUtils.registerUidToBackgroundCallback(this, callingUid)) {
                    this.mForegroundRequested = service;
                    this.mForegroundUid = callingUid;
                    success = true;
                } else {
                    Log.e(TAG, "Calling UID is not in the foreground, ignorning!");
                }
                if (success) {
                    computeEnabledForegroundService();
                }
                return success;
            }
            return DBG;
        }
    }

    boolean unregisterForegroundService(int uid) {
        boolean success = DBG;
        synchronized (this.mLock) {
            if (this.mForegroundUid == uid) {
                this.mForegroundRequested = null;
                this.mForegroundUid = -1;
                success = true;
            }
        }
        if (success) {
            computeEnabledForegroundService();
        }
        return success;
    }

    public boolean unregisteredEnabledForegroundService(int callingUid) {
        if (this.mForegroundUtils.isInForeground(callingUid)) {
            return unregisterForegroundService(callingUid);
        }
        Log.e(TAG, "Calling UID is not in the foreground, ignorning!");
        return DBG;
    }

    @Override // com.android.nfc.ForegroundUtils.Callback
    public void onUidToBackground(int uid) {
        unregisterForegroundService(uid);
    }

    public void onHostEmulationActivated() {
        synchronized (this.mLock) {
            this.mActivated = true;
        }
    }

    public void onHostEmulationDeactivated() {
        boolean needComputeFg = DBG;
        synchronized (this.mLock) {
            this.mActivated = DBG;
            if (this.mComputeFgRequested) {
                needComputeFg = true;
            }
        }
        if (needComputeFg) {
            Log.d(TAG, "do postponed configuration");
            computeEnabledForegroundService();
        }
    }

    public void onNfcDisabled() {
        synchronized (this.mLock) {
            this.mForegroundComponent = null;
            this.mForegroundRequested = null;
            this.mActivated = DBG;
            this.mComputeFgRequested = DBG;
            this.mForegroundUid = -1;
        }
    }

    public void onUserSwitched(int userId) {
        synchronized (this.mLock) {
            this.mForegroundComponent = null;
            this.mForegroundRequested = null;
            this.mActivated = DBG;
            this.mComputeFgRequested = DBG;
            this.mForegroundUid = -1;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
    }
}
