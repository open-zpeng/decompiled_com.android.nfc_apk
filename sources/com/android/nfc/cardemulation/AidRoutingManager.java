package com.android.nfc.cardemulation;

import android.util.Log;
import android.util.SparseArray;
import android.util.StatsLog;
import com.android.nfc.NfcService;
import com.android.nfc.snep.SnepMessage;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
/* loaded from: classes.dex */
public class AidRoutingManager {
    static final int AID_MATCHING_EXACT_ONLY = 0;
    static final int AID_MATCHING_EXACT_OR_PREFIX = 1;
    static final int AID_MATCHING_EXACT_OR_SUBSET_OR_PREFIX = 3;
    static final int AID_MATCHING_PREFIX_ONLY = 2;
    static final boolean DBG = false;
    static final int ROUTE_HOST = 0;
    static final String TAG = "AidRoutingManager";
    int mMaxAidRoutingTableSize;
    final Object mLock = new Object();
    SparseArray<Set<String>> mAidRoutingTable = new SparseArray<>();
    HashMap<String, Integer> mRouteForAid = new HashMap<>();
    int mDefaultRoute = doGetDefaultRouteDestination();
    final int mDefaultOffHostRoute = doGetDefaultOffHostRouteDestination();
    final byte[] mOffHostRouteUicc = doGetOffHostUiccDestination();
    final byte[] mOffHostRouteEse = doGetOffHostEseDestination();
    final int mAidMatchingSupport = doGetAidMatchingMode();
    int mDefaultIsoDepRoute = doGetDefaultIsoDepRouteDestination();

    private native int doGetAidMatchingMode();

    private native int doGetDefaultIsoDepRouteDestination();

    private native int doGetDefaultOffHostRouteDestination();

    private native int doGetDefaultRouteDestination();

    private native byte[] doGetOffHostEseDestination();

    private native byte[] doGetOffHostUiccDestination();

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class AidEntry {
        int aidInfo;
        boolean isOnHost;
        String offHostSE;
        int route;

        /* JADX INFO: Access modifiers changed from: package-private */
        public AidEntry() {
        }
    }

    public boolean supportsAidPrefixRouting() {
        int i = this.mAidMatchingSupport;
        if (i == 1 || i == 2 || i == 3) {
            return true;
        }
        return DBG;
    }

    public boolean supportsAidSubsetRouting() {
        if (this.mAidMatchingSupport == 3) {
            return true;
        }
        return DBG;
    }

    public int calculateAidRouteSize(HashMap<String, AidEntry> routeCache) {
        int routeTableSize = 0;
        for (Map.Entry<String, AidEntry> aidEntry : routeCache.entrySet()) {
            String aid = aidEntry.getKey();
            routeTableSize = aid.endsWith("*") ? routeTableSize + ((aid.length() - 1) / 2) + 4 : routeTableSize + (aid.length() / 2) + 4;
        }
        return routeTableSize;
    }

    private void clearNfcRoutingTableLocked() {
        for (Map.Entry<String, Integer> aidEntry : this.mRouteForAid.entrySet()) {
            String aid = aidEntry.getKey();
            if (aid.endsWith("*")) {
                int i = this.mAidMatchingSupport;
                if (i == 0) {
                    Log.e(TAG, "Device does not support prefix AIDs but AID [" + aid + "] is registered");
                } else if (i == 2) {
                    aid = aid.substring(0, aid.length() - 1);
                } else if (i == 1 || i == 3) {
                    aid = aid.substring(0, aid.length() - 1);
                }
            } else if (aid.endsWith("#")) {
                int i2 = this.mAidMatchingSupport;
                if (i2 == 0) {
                    Log.e(TAG, "Device does not support subset AIDs but AID [" + aid + "] is registered");
                } else if (i2 == 2 || i2 == 1) {
                    Log.e(TAG, "Device does not support subset AIDs but AID [" + aid + "] is registered");
                } else if (i2 == 3) {
                    aid = aid.substring(0, aid.length() - 1);
                }
            }
            NfcService.getInstance().unrouteAids(aid);
        }
        int nciVersion = NfcService.getInstance().getNciVersion();
        NfcService.getInstance();
        if (nciVersion != 16) {
            NfcService.getInstance().unrouteAids("");
        }
    }

    private int getRouteForSecureElement(String se) {
        int index;
        if (se == null || se.length() <= 3) {
            return 0;
        }
        try {
            if (se.startsWith("eSE") && this.mOffHostRouteEse != null) {
                int index2 = Integer.parseInt(se.substring(3));
                if (this.mOffHostRouteEse.length >= index2 && index2 > 0) {
                    return this.mOffHostRouteEse[index2 - 1] & SnepMessage.RESPONSE_REJECT;
                }
            } else if (se.startsWith("SIM") && this.mOffHostRouteUicc != null && this.mOffHostRouteUicc.length >= (index = Integer.parseInt(se.substring(3))) && index > 0) {
                return this.mOffHostRouteUicc[index - 1] & SnepMessage.RESPONSE_REJECT;
            }
            if (this.mOffHostRouteEse == null && this.mOffHostRouteUicc == null) {
                return this.mDefaultOffHostRoute;
            }
        } catch (NumberFormatException e) {
        }
        return 0;
    }

    public boolean configureRouting(HashMap<String, AidEntry> aidMap, boolean force) {
        boolean aidRouteResolved;
        int route;
        boolean aidRouteResolved2;
        boolean aidRouteResolved3 = DBG;
        HashMap<String, AidEntry> aidRoutingTableCache = new HashMap<>(aidMap.size());
        ArrayList<Integer> seList = new ArrayList<>();
        seList.add(0);
        SparseArray<Set<String>> aidRoutingTable = new SparseArray<>(aidMap.size());
        HashMap<String, Integer> routeForAid = new HashMap<>(aidMap.size());
        HashMap<String, Integer> infoForAid = new HashMap<>(aidMap.size());
        for (Map.Entry<String, AidEntry> aidEntry : aidMap.entrySet()) {
            int route2 = 0;
            if (!aidEntry.getValue().isOnHost) {
                String offHostSE = aidEntry.getValue().offHostSE;
                if (offHostSE == null) {
                    route2 = this.mDefaultOffHostRoute;
                } else {
                    route2 = getRouteForSecureElement(offHostSE);
                    if (route2 == 0) {
                        Log.e(TAG, "Invalid Off host Aid Entry " + offHostSE);
                    }
                }
            }
            if (!seList.contains(Integer.valueOf(route2))) {
                seList.add(Integer.valueOf(route2));
            }
            aidEntry.getValue().route = route2;
            int aidType = aidEntry.getValue().aidInfo;
            String aid = aidEntry.getKey();
            Set<String> entries = aidRoutingTable.get(route2, new HashSet());
            entries.add(aid);
            aidRoutingTable.put(route2, entries);
            routeForAid.put(aid, Integer.valueOf(route2));
            infoForAid.put(aid, Integer.valueOf(aidType));
        }
        synchronized (this.mLock) {
            try {
                try {
                    if (routeForAid.equals(this.mRouteForAid) && !force) {
                        return DBG;
                    }
                    clearNfcRoutingTableLocked();
                    this.mRouteForAid = routeForAid;
                    this.mAidRoutingTable = aidRoutingTable;
                    this.mMaxAidRoutingTableSize = NfcService.getInstance().getAidRoutingTableSize();
                    int index = 0;
                    while (true) {
                        if (index >= seList.size()) {
                            break;
                        }
                        this.mDefaultRoute = seList.get(index).intValue();
                        aidRoutingTableCache.clear();
                        if (this.mAidMatchingSupport == 2) {
                            Set<String> defaultRouteAids = this.mAidRoutingTable.get(this.mDefaultRoute);
                            if (defaultRouteAids == null) {
                                aidRouteResolved = aidRouteResolved3;
                            } else {
                                Iterator<String> it = defaultRouteAids.iterator();
                                while (it.hasNext()) {
                                    String defaultRouteAid = it.next();
                                    for (Map.Entry<String, Integer> aidEntry2 : this.mRouteForAid.entrySet()) {
                                        String aid2 = aidEntry2.getKey();
                                        int route3 = aidEntry2.getValue().intValue();
                                        String defaultRouteAid2 = defaultRouteAid;
                                        if (defaultRouteAid2.startsWith(aid2)) {
                                            aidRouteResolved2 = aidRouteResolved3;
                                            if (route3 != this.mDefaultRoute) {
                                                try {
                                                    aidRoutingTableCache.put(defaultRouteAid2, aidMap.get(defaultRouteAid2));
                                                } catch (Throwable th) {
                                                    th = th;
                                                    throw th;
                                                }
                                            } else {
                                                continue;
                                            }
                                        } else {
                                            aidRouteResolved2 = aidRouteResolved3;
                                        }
                                        defaultRouteAid = defaultRouteAid2;
                                        aidRouteResolved3 = aidRouteResolved2;
                                    }
                                }
                                aidRouteResolved = aidRouteResolved3;
                            }
                        } else {
                            aidRouteResolved = aidRouteResolved3;
                        }
                        for (int i = 0; i < this.mAidRoutingTable.size(); i++) {
                            int route4 = this.mAidRoutingTable.keyAt(i);
                            if (route4 != this.mDefaultRoute) {
                                Set<String> aidsForRoute = this.mAidRoutingTable.get(route4);
                                for (String aid3 : aidsForRoute) {
                                    if (aid3.endsWith("*")) {
                                        if (this.mAidMatchingSupport == 0) {
                                            Log.e(TAG, "This device does not support prefix AIDs.");
                                            route = route4;
                                        } else if (this.mAidMatchingSupport == 2) {
                                            aidRoutingTableCache.put(aid3.substring(0, aid3.length() - 1), aidMap.get(aid3));
                                            route = route4;
                                        } else {
                                            if (this.mAidMatchingSupport != 1 && this.mAidMatchingSupport != 3) {
                                                route = route4;
                                            }
                                            aidRoutingTableCache.put(aid3.substring(0, aid3.length() - 1), aidMap.get(aid3));
                                            route = route4;
                                        }
                                    } else if (aid3.endsWith("#")) {
                                        if (this.mAidMatchingSupport == 0) {
                                            StringBuilder sb = new StringBuilder();
                                            route = route4;
                                            sb.append("Device does not support subset AIDs but AID [");
                                            sb.append(aid3);
                                            sb.append("] is registered");
                                            Log.e(TAG, sb.toString());
                                        } else {
                                            route = route4;
                                            if (this.mAidMatchingSupport != 2 && this.mAidMatchingSupport != 1) {
                                                if (this.mAidMatchingSupport == 3) {
                                                    aidRoutingTableCache.put(aid3.substring(0, aid3.length() - 1), aidMap.get(aid3));
                                                }
                                            }
                                            Log.e(TAG, "Device does not support subset AIDs but AID [" + aid3 + "] is registered");
                                        }
                                    } else {
                                        route = route4;
                                        aidRoutingTableCache.put(aid3, aidMap.get(aid3));
                                    }
                                    route4 = route;
                                }
                            }
                        }
                        if (this.mDefaultRoute != this.mDefaultIsoDepRoute) {
                            int nciVersion = NfcService.getInstance().getNciVersion();
                            NfcService.getInstance();
                            if (nciVersion != 16) {
                                AidEntry entry = new AidEntry();
                                entry.route = this.mDefaultRoute;
                                if (this.mDefaultRoute == 0) {
                                    entry.isOnHost = true;
                                } else {
                                    entry.isOnHost = DBG;
                                }
                                entry.aidInfo = 16;
                                aidRoutingTableCache.put("", entry);
                            }
                        }
                        if (calculateAidRouteSize(aidRoutingTableCache) > this.mMaxAidRoutingTableSize) {
                            index++;
                            aidRouteResolved3 = aidRouteResolved;
                        } else {
                            aidRouteResolved3 = true;
                            break;
                        }
                    }
                    if (aidRouteResolved3) {
                        commit(aidRoutingTableCache);
                    } else {
                        StatsLog.write(134, 3, 0, 0);
                        Log.e(TAG, "RoutingTable unchanged because it's full, not updating");
                    }
                    return true;
                } catch (Throwable th2) {
                    th = th2;
                }
            } catch (Throwable th3) {
                th = th3;
            }
        }
    }

    private void commit(HashMap<String, AidEntry> routeCache) {
        if (routeCache != null) {
            for (Map.Entry<String, AidEntry> aidEntry : routeCache.entrySet()) {
                int route = aidEntry.getValue().route;
                int aidType = aidEntry.getValue().aidInfo;
                String aid = aidEntry.getKey();
                NfcService.getInstance().routeAids(aid, route, aidType);
            }
        }
        NfcService.getInstance().commitRouting();
    }

    public void onNfccRoutingTableCleared() {
        synchronized (this.mLock) {
            this.mAidRoutingTable.clear();
            this.mRouteForAid.clear();
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Routing table:");
        StringBuilder sb = new StringBuilder();
        sb.append("    Default route: ");
        sb.append(this.mDefaultRoute == 0 ? "host" : "secure element");
        pw.println(sb.toString());
        synchronized (this.mLock) {
            for (int i = 0; i < this.mAidRoutingTable.size(); i++) {
                Set<String> aids = this.mAidRoutingTable.valueAt(i);
                pw.println("    Routed to 0x" + Integer.toHexString(this.mAidRoutingTable.keyAt(i)) + ":");
                for (String aid : aids) {
                    pw.println("        \"" + aid + "\"");
                }
            }
        }
    }
}
