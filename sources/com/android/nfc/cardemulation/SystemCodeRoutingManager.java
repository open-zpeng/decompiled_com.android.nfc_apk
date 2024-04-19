package com.android.nfc.cardemulation;

import android.util.Log;
import com.android.nfc.NfcService;
import com.android.nfc.cardemulation.RegisteredT3tIdentifiersCache;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes.dex */
public class SystemCodeRoutingManager {
    static final boolean DBG = false;
    static final String TAG = "SystemCodeRoutingManager";
    final Object mLock = new Object();
    List<RegisteredT3tIdentifiersCache.T3tIdentifier> mConfiguredT3tIdentifiers = new ArrayList();

    public boolean configureRouting(List<RegisteredT3tIdentifiersCache.T3tIdentifier> t3tIdentifiers) {
        List<RegisteredT3tIdentifiersCache.T3tIdentifier> toBeAdded = new ArrayList<>();
        List<RegisteredT3tIdentifiersCache.T3tIdentifier> toBeRemoved = new ArrayList<>();
        synchronized (this.mLock) {
            for (RegisteredT3tIdentifiersCache.T3tIdentifier t3tIdentifier : t3tIdentifiers) {
                if (!this.mConfiguredT3tIdentifiers.contains(t3tIdentifier)) {
                    toBeAdded.add(t3tIdentifier);
                }
            }
            for (RegisteredT3tIdentifiersCache.T3tIdentifier t3tIdentifier2 : this.mConfiguredT3tIdentifiers) {
                if (!t3tIdentifiers.contains(t3tIdentifier2)) {
                    toBeRemoved.add(t3tIdentifier2);
                }
            }
            if (toBeAdded.size() <= 0 && toBeRemoved.size() <= 0) {
                Log.d(TAG, "Routing table unchanged, not updating");
                return DBG;
            }
            for (RegisteredT3tIdentifiersCache.T3tIdentifier t3tIdentifier3 : toBeRemoved) {
                NfcService.getInstance().deregisterT3tIdentifier(t3tIdentifier3.systemCode, t3tIdentifier3.nfcid2, t3tIdentifier3.t3tPmm);
            }
            for (RegisteredT3tIdentifiersCache.T3tIdentifier t3tIdentifier4 : toBeAdded) {
                NfcService.getInstance().registerT3tIdentifier(t3tIdentifier4.systemCode, t3tIdentifier4.nfcid2, t3tIdentifier4.t3tPmm);
            }
            this.mConfiguredT3tIdentifiers = t3tIdentifiers;
            NfcService.getInstance().commitRouting();
            return true;
        }
    }

    public void onNfccRoutingTableCleared() {
        synchronized (this.mLock) {
            NfcService.getInstance().clearT3tIdentifiersCache();
            this.mConfiguredT3tIdentifiers.clear();
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("HCE-F routing table:");
        synchronized (this.mLock) {
            for (RegisteredT3tIdentifiersCache.T3tIdentifier t3tIdentifier : this.mConfiguredT3tIdentifiers) {
                pw.println("    " + t3tIdentifier.systemCode + "/" + t3tIdentifier.nfcid2);
            }
        }
    }
}
