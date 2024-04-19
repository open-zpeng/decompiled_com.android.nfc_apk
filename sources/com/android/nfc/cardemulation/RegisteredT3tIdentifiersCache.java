package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.nfc.cardemulation.NfcFServiceInfo;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/* loaded from: classes.dex */
public class RegisteredT3tIdentifiersCache {
    static final boolean DBG = false;
    static final String TAG = "RegisteredT3tIdentifiersCache";
    final Context mContext;
    ComponentName mEnabledForegroundService;
    final SystemCodeRoutingManager mRoutingManager;
    List<NfcFServiceInfo> mServices = new ArrayList();
    final HashMap<String, NfcFServiceInfo> mForegroundT3tIdentifiersCache = new HashMap<>();
    final Object mLock = new Object();
    boolean mNfcEnabled = DBG;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class T3tIdentifier {
        public final String nfcid2;
        public final String systemCode;
        public final String t3tPmm;

        T3tIdentifier(String systemCode, String nfcid2, String t3tPmm) {
            this.systemCode = systemCode;
            this.nfcid2 = nfcid2;
            this.t3tPmm = t3tPmm;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return RegisteredT3tIdentifiersCache.DBG;
            }
            T3tIdentifier that = (T3tIdentifier) o;
            if (this.systemCode.equalsIgnoreCase(that.systemCode) && this.nfcid2.equalsIgnoreCase(that.nfcid2)) {
                return true;
            }
            return RegisteredT3tIdentifiersCache.DBG;
        }

        public int hashCode() {
            int result = this.systemCode.hashCode();
            return (result * 31) + this.nfcid2.hashCode();
        }
    }

    public RegisteredT3tIdentifiersCache(Context context) {
        Log.d(TAG, TAG);
        this.mContext = context;
        this.mRoutingManager = new SystemCodeRoutingManager();
    }

    public NfcFServiceInfo resolveNfcid2(String nfcid2) {
        NfcFServiceInfo resolveInfo;
        synchronized (this.mLock) {
            resolveInfo = this.mForegroundT3tIdentifiersCache.get(nfcid2);
            StringBuilder sb = new StringBuilder();
            sb.append("Resolved to: ");
            sb.append(resolveInfo == null ? "null" : resolveInfo.toString());
            Log.d(TAG, sb.toString());
        }
        return resolveInfo;
    }

    void generateForegroundT3tIdentifiersCacheLocked() {
        this.mForegroundT3tIdentifiersCache.clear();
        if (this.mEnabledForegroundService != null) {
            Iterator<NfcFServiceInfo> it = this.mServices.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                NfcFServiceInfo service = it.next();
                if (this.mEnabledForegroundService.equals(service.getComponent())) {
                    if (!service.getSystemCode().equalsIgnoreCase("NULL") && !service.getNfcid2().equalsIgnoreCase("NULL")) {
                        this.mForegroundT3tIdentifiersCache.put(service.getNfcid2(), service);
                    }
                }
            }
        }
        updateRoutingLocked(DBG);
    }

    void updateRoutingLocked(boolean force) {
        if (!this.mNfcEnabled) {
            Log.d(TAG, "Not updating routing table because NFC is off.");
            return;
        }
        List<T3tIdentifier> t3tIdentifiers = new ArrayList<>();
        if (force) {
            this.mRoutingManager.configureRouting(t3tIdentifiers);
        }
        for (Map.Entry<String, NfcFServiceInfo> entry : this.mForegroundT3tIdentifiersCache.entrySet()) {
            t3tIdentifiers.add(new T3tIdentifier(entry.getValue().getSystemCode(), entry.getValue().getNfcid2(), entry.getValue().getT3tPmm()));
        }
        this.mRoutingManager.configureRouting(t3tIdentifiers);
    }

    public void onSecureNfcToggled() {
        synchronized (this.mLock) {
            updateRoutingLocked(true);
        }
    }

    public void onServicesUpdated(int userId, List<NfcFServiceInfo> services) {
        synchronized (this.mLock) {
            if (ActivityManager.getCurrentUser() == userId) {
                this.mServices = services;
            } else {
                Log.d(TAG, "Ignoring update because it's not for the current user.");
            }
        }
    }

    public void onEnabledForegroundNfcFServiceChanged(ComponentName component) {
        synchronized (this.mLock) {
            if (component != null) {
                if (this.mEnabledForegroundService != null) {
                    return;
                }
                this.mEnabledForegroundService = component;
            } else if (this.mEnabledForegroundService == null) {
                return;
            } else {
                this.mEnabledForegroundService = null;
            }
            generateForegroundT3tIdentifiersCacheLocked();
        }
    }

    public void onNfcEnabled() {
        synchronized (this.mLock) {
            this.mNfcEnabled = true;
        }
    }

    public void onNfcDisabled() {
        synchronized (this.mLock) {
            this.mNfcEnabled = DBG;
            this.mForegroundT3tIdentifiersCache.clear();
            this.mEnabledForegroundService = null;
        }
        this.mRoutingManager.onNfccRoutingTableCleared();
    }

    public void onUserSwitched() {
        synchronized (this.mLock) {
            this.mForegroundT3tIdentifiersCache.clear();
            updateRoutingLocked(DBG);
            this.mEnabledForegroundService = null;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("T3T Identifier cache entries: ");
        for (Map.Entry<String, NfcFServiceInfo> entry : this.mForegroundT3tIdentifiersCache.entrySet()) {
            pw.println("    NFCID2: " + entry.getKey());
            pw.println("    NfcFServiceInfo: ");
            entry.getValue().dump(fd, pw, args);
        }
        pw.println("");
        this.mRoutingManager.dump(fd, pw, args);
        pw.println("");
    }
}
