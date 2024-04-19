package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.nfc.cardemulation.ApduServiceInfo;
import android.util.Log;
import com.android.nfc.cardemulation.AidRoutingManager;
import com.google.android.collect.Maps;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.TreeMap;
/* loaded from: classes.dex */
public class RegisteredAidCache {
    static final int AID_ROUTE_QUAL_PREFIX = 16;
    static final int AID_ROUTE_QUAL_SUBSET = 32;
    static final boolean DBG = false;
    static final String TAG = "RegisteredAidCache";
    final Context mContext;
    boolean mSupportsPrefixes;
    boolean mSupportsSubset;
    final TreeMap<String, ArrayList<ServiceAidInfo>> mAidServices = new TreeMap<>();
    final TreeMap<String, AidResolveInfo> mAidCache = new TreeMap<>();
    final AidResolveInfo EMPTY_RESOLVE_INFO = new AidResolveInfo();
    final Object mLock = new Object();
    boolean mNfcEnabled = DBG;
    final AidRoutingManager mRoutingManager = new AidRoutingManager();
    ComponentName mPreferredPaymentService = null;
    ComponentName mPreferredForegroundService = null;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class ServiceAidInfo {
        String aid;
        String category;
        ApduServiceInfo service;

        ServiceAidInfo() {
        }

        public String toString() {
            return "ServiceAidInfo{service=" + this.service.getComponent() + ", aid='" + this.aid + "', category='" + this.category + "'}";
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return RegisteredAidCache.DBG;
            }
            ServiceAidInfo that = (ServiceAidInfo) o;
            if (this.aid.equals(that.aid) && this.category.equals(that.category) && this.service.equals(that.service)) {
                return true;
            }
            return RegisteredAidCache.DBG;
        }

        public int hashCode() {
            int result = this.service.hashCode();
            return (((result * 31) + this.aid.hashCode()) * 31) + this.category.hashCode();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class AidResolveInfo {
        List<ApduServiceInfo> services = new ArrayList();
        ApduServiceInfo defaultService = null;
        String category = null;
        boolean mustRoute = true;
        ReslovedPrefixConflictAid prefixInfo = null;

        AidResolveInfo() {
        }

        public String toString() {
            return "AidResolveInfo{services=" + this.services + ", defaultService=" + this.defaultService + ", category='" + this.category + "', mustRoute=" + this.mustRoute + '}';
        }
    }

    public RegisteredAidCache(Context context) {
        this.mSupportsPrefixes = DBG;
        this.mSupportsSubset = DBG;
        this.mContext = context;
        this.mSupportsPrefixes = this.mRoutingManager.supportsAidPrefixRouting();
        this.mSupportsSubset = this.mRoutingManager.supportsAidSubsetRouting();
        boolean z = this.mSupportsPrefixes;
        boolean z2 = this.mSupportsSubset;
    }

    /* JADX WARN: Removed duplicated region for block: B:34:0x00bf A[Catch: all -> 0x00fa, TryCatch #0 {, blocks: (B:4:0x0007, B:6:0x000f, B:7:0x0018, B:9:0x001a, B:11:0x0023, B:14:0x0028, B:47:0x00f8, B:15:0x0033, B:16:0x005d, B:18:0x0063, B:22:0x0082, B:24:0x009e, B:27:0x00a7, B:30:0x00af, B:32:0x00b5, B:34:0x00bf, B:36:0x00c3, B:37:0x00ca, B:38:0x00d2, B:39:0x00d8, B:41:0x00de, B:43:0x00ec, B:23:0x0089), top: B:52:0x0007 }] */
    /* JADX WARN: Removed duplicated region for block: B:41:0x00de A[Catch: all -> 0x00fa, TryCatch #0 {, blocks: (B:4:0x0007, B:6:0x000f, B:7:0x0018, B:9:0x001a, B:11:0x0023, B:14:0x0028, B:47:0x00f8, B:15:0x0033, B:16:0x005d, B:18:0x0063, B:22:0x0082, B:24:0x009e, B:27:0x00a7, B:30:0x00af, B:32:0x00b5, B:34:0x00bf, B:36:0x00c3, B:37:0x00ca, B:38:0x00d2, B:39:0x00d8, B:41:0x00de, B:43:0x00ec, B:23:0x0089), top: B:52:0x0007 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public com.android.nfc.cardemulation.RegisteredAidCache.AidResolveInfo resolveAid(java.lang.String r17) {
        /*
            Method dump skipped, instructions count: 253
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.cardemulation.RegisteredAidCache.resolveAid(java.lang.String):com.android.nfc.cardemulation.RegisteredAidCache$AidResolveInfo");
    }

    public boolean supportsAidPrefixRegistration() {
        return this.mSupportsPrefixes;
    }

    public boolean supportsAidSubsetRegistration() {
        return this.mSupportsSubset;
    }

    public boolean isDefaultServiceForAid(int userId, ComponentName service, String aid) {
        AidResolveInfo resolveInfo = resolveAid(aid);
        if (resolveInfo == null || resolveInfo.services == null || resolveInfo.services.size() == 0) {
            return DBG;
        }
        if (resolveInfo.defaultService != null) {
            return service.equals(resolveInfo.defaultService.getComponent());
        }
        return resolveInfo.services.size() == 1 ? service.equals(resolveInfo.services.get(0).getComponent()) : DBG;
    }

    AidResolveInfo resolveAidConflictLocked(Collection<ServiceAidInfo> conflictingServices, boolean makeSingleServiceDefault) {
        if (conflictingServices == null || conflictingServices.size() == 0) {
            Log.e(TAG, "resolveAidConflict: No services passed in.");
            return null;
        }
        AidResolveInfo resolveInfo = new AidResolveInfo();
        resolveInfo.category = "other";
        ApduServiceInfo matchedForeground = null;
        ApduServiceInfo matchedPayment = null;
        for (ServiceAidInfo serviceAidInfo : conflictingServices) {
            boolean serviceClaimsPaymentAid = "payment".equals(serviceAidInfo.category);
            if (serviceAidInfo.service.getComponent().equals(this.mPreferredForegroundService)) {
                resolveInfo.services.add(serviceAidInfo.service);
                if (serviceClaimsPaymentAid) {
                    resolveInfo.category = "payment";
                }
                matchedForeground = serviceAidInfo.service;
            } else if (serviceAidInfo.service.getComponent().equals(this.mPreferredPaymentService) && serviceClaimsPaymentAid) {
                resolveInfo.services.add(serviceAidInfo.service);
                resolveInfo.category = "payment";
                matchedPayment = serviceAidInfo.service;
            } else if (!serviceClaimsPaymentAid) {
                resolveInfo.services.add(serviceAidInfo.service);
            }
        }
        if (matchedForeground != null) {
            resolveInfo.defaultService = matchedForeground;
        } else if (matchedPayment != null) {
            resolveInfo.defaultService = matchedPayment;
        } else if (resolveInfo.services.size() == 1 && makeSingleServiceDefault) {
            resolveInfo.defaultService = resolveInfo.services.get(0);
        }
        return resolveInfo;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public class DefaultServiceInfo {
        ServiceAidInfo foregroundDefault;
        ServiceAidInfo paymentDefault;

        DefaultServiceInfo() {
        }
    }

    DefaultServiceInfo findDefaultServices(ArrayList<ServiceAidInfo> serviceAidInfos) {
        DefaultServiceInfo defaultServiceInfo = new DefaultServiceInfo();
        Iterator<ServiceAidInfo> it = serviceAidInfos.iterator();
        while (it.hasNext()) {
            ServiceAidInfo serviceAidInfo = it.next();
            boolean serviceClaimsPaymentAid = "payment".equals(serviceAidInfo.category);
            if (serviceAidInfo.service.getComponent().equals(this.mPreferredForegroundService)) {
                defaultServiceInfo.foregroundDefault = serviceAidInfo;
            } else if (serviceAidInfo.service.getComponent().equals(this.mPreferredPaymentService) && serviceClaimsPaymentAid) {
                defaultServiceInfo.paymentDefault = serviceAidInfo;
            }
        }
        return defaultServiceInfo;
    }

    AidResolveInfo resolveAidConflictLocked(ArrayList<ServiceAidInfo> aidServices, ArrayList<ServiceAidInfo> conflictingServices) {
        DefaultServiceInfo aidDefaultInfo = findDefaultServices(aidServices);
        DefaultServiceInfo conflictingDefaultInfo = findDefaultServices(conflictingServices);
        if (aidDefaultInfo.foregroundDefault != null) {
            final AidResolveInfo resolveinfo = resolveAidConflictLocked((Collection<ServiceAidInfo>) aidServices, true);
            if (isSubset(aidServices.get(0).aid)) {
                resolveinfo.prefixInfo = findPrefixConflictForSubsetAid(aidServices.get(0).aid, new ArrayList<ApduServiceInfo>() { // from class: com.android.nfc.cardemulation.RegisteredAidCache.1
                    {
                        add(resolveinfo.defaultService);
                    }
                }, true);
            }
            return resolveinfo;
        } else if (aidDefaultInfo.paymentDefault != null) {
            if (conflictingDefaultInfo.foregroundDefault != null) {
                return this.EMPTY_RESOLVE_INFO;
            }
            final AidResolveInfo resolveinfo2 = resolveAidConflictLocked((Collection<ServiceAidInfo>) aidServices, true);
            if (isSubset(aidServices.get(0).aid)) {
                resolveinfo2.prefixInfo = findPrefixConflictForSubsetAid(aidServices.get(0).aid, new ArrayList<ApduServiceInfo>() { // from class: com.android.nfc.cardemulation.RegisteredAidCache.2
                    {
                        add(resolveinfo2.defaultService);
                    }
                }, true);
            }
            return resolveinfo2;
        } else if (conflictingDefaultInfo.foregroundDefault != null || conflictingDefaultInfo.paymentDefault != null) {
            return this.EMPTY_RESOLVE_INFO;
        } else {
            AidResolveInfo resolveinfo3 = resolveAidConflictLocked(aidServices, conflictingServices.isEmpty());
            if (isSubset(aidServices.get(0).aid)) {
                ArrayList<ApduServiceInfo> apduServiceList = new ArrayList<>();
                Iterator<ServiceAidInfo> it = conflictingServices.iterator();
                while (it.hasNext()) {
                    ServiceAidInfo serviceInfo = it.next();
                    apduServiceList.add(serviceInfo.service);
                }
                Iterator<ServiceAidInfo> it2 = aidServices.iterator();
                while (it2.hasNext()) {
                    ServiceAidInfo serviceInfo2 = it2.next();
                    apduServiceList.add(serviceInfo2.service);
                }
                resolveinfo3.prefixInfo = findPrefixConflictForSubsetAid(aidServices.get(0).aid, apduServiceList, DBG);
            }
            return resolveinfo3;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:71:0x0184 A[SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:72:0x0176 A[SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    void generateServiceMapLocked(java.util.List<android.nfc.cardemulation.ApduServiceInfo> r17) {
        /*
            Method dump skipped, instructions count: 408
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.cardemulation.RegisteredAidCache.generateServiceMapLocked(java.util.List):void");
    }

    static boolean isExact(String aid) {
        if (aid.endsWith("*") || aid.endsWith("#")) {
            return DBG;
        }
        return true;
    }

    static boolean isPrefix(String aid) {
        return aid.endsWith("*");
    }

    static boolean isSubset(String aid) {
        return aid.endsWith("#");
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class ReslovedPrefixConflictAid {
        String prefixAid = null;
        boolean matchingSubset = RegisteredAidCache.DBG;

        ReslovedPrefixConflictAid() {
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class AidConflicts {
        NavigableMap<String, ArrayList<ServiceAidInfo>> conflictMap;
        final ArrayList<ServiceAidInfo> services = new ArrayList<>();
        final HashSet<String> aids = new HashSet<>();

        AidConflicts() {
        }
    }

    ReslovedPrefixConflictAid findPrefixConflictForSubsetAid(String subsetAid, ArrayList<ApduServiceInfo> prefixServices, boolean priorityRootAid) {
        ArrayList<String> prefixAids = new ArrayList<>();
        String minPrefix = null;
        String plainSubsetAid = subsetAid.substring(0, subsetAid.length() - 1);
        Iterator<ApduServiceInfo> it = prefixServices.iterator();
        while (it.hasNext()) {
            ApduServiceInfo service = it.next();
            for (String prefixAid : service.getPrefixAids()) {
                String plainPrefix = prefixAid.substring(0, prefixAid.length() - 1);
                if (plainSubsetAid.startsWith(plainPrefix)) {
                    if (priorityRootAid) {
                        if ("payment".equals(service.getCategoryForAid(prefixAid)) || service.getComponent().equals(this.mPreferredForegroundService)) {
                            prefixAids.add(prefixAid);
                        }
                    } else {
                        prefixAids.add(prefixAid);
                    }
                }
            }
        }
        if (prefixAids.size() > 0) {
            minPrefix = (String) Collections.min(prefixAids);
        }
        ReslovedPrefixConflictAid resolvedPrefix = new ReslovedPrefixConflictAid();
        resolvedPrefix.prefixAid = minPrefix;
        if (minPrefix != null && plainSubsetAid.equalsIgnoreCase(minPrefix.substring(0, minPrefix.length() - 1))) {
            resolvedPrefix.matchingSubset = true;
        }
        return resolvedPrefix;
    }

    AidConflicts findConflictsForPrefixLocked(String prefixAid) {
        AidConflicts prefixConflicts = new AidConflicts();
        String plainAid = prefixAid.substring(0, prefixAid.length() - 1);
        String lastAidWithPrefix = String.format("%-32s", plainAid).replace(' ', 'F');
        prefixConflicts.conflictMap = this.mAidServices.subMap(plainAid, true, lastAidWithPrefix, true);
        for (Map.Entry<String, ArrayList<ServiceAidInfo>> entry : prefixConflicts.conflictMap.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(prefixAid)) {
                prefixConflicts.services.addAll(entry.getValue());
                prefixConflicts.aids.add(entry.getKey());
            }
        }
        return prefixConflicts;
    }

    AidConflicts findConflictsForSubsetAidLocked(String subsetAid) {
        AidConflicts subsetConflicts = new AidConflicts();
        subsetAid.substring(0, subsetAid.length() - 1);
        String plainSubsetAid = subsetAid.substring(0, subsetAid.length() - 1);
        subsetAid.substring(0, 10);
        subsetConflicts.conflictMap = new TreeMap();
        for (Map.Entry<String, ArrayList<ServiceAidInfo>> entry : this.mAidServices.entrySet()) {
            String aid = entry.getKey();
            String plainAid = aid;
            if (isSubset(aid) || isPrefix(aid)) {
                plainAid = aid.substring(0, aid.length() - 1);
            }
            if (plainSubsetAid.startsWith(plainAid)) {
                subsetConflicts.conflictMap.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, ArrayList<ServiceAidInfo>> entry2 : subsetConflicts.conflictMap.entrySet()) {
            if (!entry2.getKey().equalsIgnoreCase(subsetAid)) {
                subsetConflicts.services.addAll(entry2.getValue());
                subsetConflicts.aids.add(entry2.getKey());
            }
        }
        return subsetConflicts;
    }

    void generateAidCacheLocked() {
        boolean z;
        this.mAidCache.clear();
        TreeMap<String, AidResolveInfo> aidCache = new TreeMap<>();
        PriorityQueue<String> aidsToResolve = new PriorityQueue<>(this.mAidServices.keySet());
        aidCache.clear();
        while (true) {
            boolean isEmpty = aidsToResolve.isEmpty();
            z = DBG;
            if (isEmpty) {
                break;
            }
            ArrayList<String> resolvedAids = new ArrayList<>();
            String aidToResolve = aidsToResolve.peek();
            if (aidsToResolve.contains(aidToResolve + "*")) {
                aidToResolve = aidToResolve + "*";
            }
            if (isPrefix(aidToResolve)) {
                ArrayList<ServiceAidInfo> prefixServices = new ArrayList<>(this.mAidServices.get(aidToResolve));
                AidConflicts prefixConflicts = findConflictsForPrefixLocked(aidToResolve);
                AidResolveInfo resolveInfo = resolveAidConflictLocked(prefixServices, prefixConflicts.services);
                aidCache.put(aidToResolve, resolveInfo);
                resolvedAids.add(aidToResolve);
                if (resolveInfo.defaultService != null) {
                    resolvedAids.addAll(prefixConflicts.aids);
                    for (String aid : resolveInfo.defaultService.getSubsetAids()) {
                        if (prefixConflicts.aids.contains(aid) && ("payment".equals(resolveInfo.defaultService.getCategoryForAid(aid)) || resolveInfo.defaultService.getComponent().equals(this.mPreferredForegroundService))) {
                            aidCache.put(aid, resolveAidConflictLocked(this.mAidServices.get(aid), DBG));
                            Log.d(TAG, "AID " + aid + " shared with prefix; adding subset .");
                        }
                    }
                } else if (resolveInfo.services.size() > 0) {
                    boolean foundChildService = DBG;
                    for (Map.Entry<String, ArrayList<ServiceAidInfo>> entry : prefixConflicts.conflictMap.entrySet()) {
                        if (!entry.getKey().equalsIgnoreCase(aidToResolve)) {
                            AidResolveInfo childResolveInfo = resolveAidConflictLocked(entry.getValue(), DBG);
                            childResolveInfo.mustRoute = DBG;
                            aidCache.put(entry.getKey(), childResolveInfo);
                            resolvedAids.add(entry.getKey());
                            foundChildService |= !childResolveInfo.services.isEmpty();
                        }
                    }
                    if (!foundChildService && resolveInfo.services.size() == 1) {
                        resolveInfo.defaultService = resolveInfo.services.get(0);
                    }
                }
            } else {
                ArrayList<ServiceAidInfo> conflictingServiceInfos = new ArrayList<>(this.mAidServices.get(aidToResolve));
                aidCache.put(aidToResolve, resolveAidConflictLocked((Collection<ServiceAidInfo>) conflictingServiceInfos, true));
                resolvedAids.add(aidToResolve);
            }
            aidsToResolve.removeAll(resolvedAids);
            resolvedAids.clear();
        }
        PriorityQueue<String> reversedQueue = new PriorityQueue<>(1, Collections.reverseOrder());
        reversedQueue.addAll(aidCache.keySet());
        while (!reversedQueue.isEmpty()) {
            ArrayList<String> resolvedAids2 = new ArrayList<>();
            String aidToResolve2 = reversedQueue.peek();
            if (isPrefix(aidToResolve2)) {
                String matchingSubset = aidToResolve2.substring(z ? 1 : 0, aidToResolve2.length() - 1) + "#";
                if (reversedQueue.contains(matchingSubset)) {
                    aidToResolve2 = aidToResolve2.substring(z ? 1 : 0, aidToResolve2.length() - 1) + "#";
                }
            }
            if (isSubset(aidToResolve2)) {
                ArrayList<ServiceAidInfo> subsetServices = new ArrayList<>(this.mAidServices.get(aidToResolve2));
                AidConflicts aidConflicts = findConflictsForSubsetAidLocked(aidToResolve2);
                AidResolveInfo resolveInfo2 = resolveAidConflictLocked(subsetServices, aidConflicts.services);
                this.mAidCache.put(aidToResolve2, resolveInfo2);
                resolvedAids2.add(aidToResolve2);
                if (resolveInfo2.defaultService != null) {
                    if (resolveInfo2.prefixInfo != null && resolveInfo2.prefixInfo.prefixAid != null && !resolveInfo2.prefixInfo.matchingSubset) {
                        this.mAidCache.put(resolveInfo2.prefixInfo.prefixAid, resolveAidConflictLocked(this.mAidServices.get(resolveInfo2.prefixInfo.prefixAid), z));
                    }
                    resolvedAids2.addAll(aidConflicts.aids);
                } else if (resolveInfo2.services.size() > 0) {
                    boolean foundChildService2 = DBG;
                    for (Map.Entry<String, ArrayList<ServiceAidInfo>> entry2 : aidConflicts.conflictMap.entrySet()) {
                        if (!entry2.getKey().equalsIgnoreCase(aidToResolve2)) {
                            AidResolveInfo childResolveInfo2 = resolveAidConflictLocked(entry2.getValue(), z);
                            childResolveInfo2.mustRoute = z;
                            this.mAidCache.put(entry2.getKey(), childResolveInfo2);
                            resolvedAids2.add(entry2.getKey());
                            foundChildService2 = (!childResolveInfo2.services.isEmpty()) | foundChildService2;
                        }
                        z = DBG;
                    }
                    if (resolveInfo2.prefixInfo != null && resolveInfo2.prefixInfo.prefixAid != null && !resolveInfo2.prefixInfo.matchingSubset) {
                        this.mAidCache.put(resolveInfo2.prefixInfo.prefixAid, resolveAidConflictLocked(this.mAidServices.get(resolveInfo2.prefixInfo.prefixAid), DBG));
                    }
                    if (!foundChildService2 && resolveInfo2.services.size() == 1) {
                        resolveInfo2.defaultService = resolveInfo2.services.get(0);
                    }
                }
            } else {
                this.mAidCache.put(aidToResolve2, aidCache.get(aidToResolve2));
                resolvedAids2.add(aidToResolve2);
            }
            reversedQueue.removeAll(resolvedAids2);
            resolvedAids2.clear();
            z = DBG;
        }
        updateRoutingLocked(DBG);
    }

    void updateRoutingLocked(boolean force) {
        if (!this.mNfcEnabled) {
            return;
        }
        HashMap<String, AidRoutingManager.AidEntry> routingEntries = Maps.newHashMap();
        for (Map.Entry<String, AidResolveInfo> aidEntry : this.mAidCache.entrySet()) {
            String aid = aidEntry.getKey();
            AidResolveInfo resolveInfo = aidEntry.getValue();
            if (resolveInfo.mustRoute) {
                AidRoutingManager aidRoutingManager = this.mRoutingManager;
                Objects.requireNonNull(aidRoutingManager);
                AidRoutingManager.AidEntry aidType = new AidRoutingManager.AidEntry();
                if (aid.endsWith("#")) {
                    aidType.aidInfo |= 32;
                }
                if (aid.endsWith("*") || (resolveInfo.prefixInfo != null && resolveInfo.prefixInfo.matchingSubset)) {
                    aidType.aidInfo |= 16;
                }
                if (resolveInfo.services.size() != 0) {
                    if (resolveInfo.defaultService != null) {
                        aidType.isOnHost = resolveInfo.defaultService.isOnHost();
                        if (!aidType.isOnHost) {
                            aidType.offHostSE = resolveInfo.defaultService.getOffHostSecureElement();
                        }
                        routingEntries.put(aid, aidType);
                    } else if (resolveInfo.services.size() == 1) {
                        if (resolveInfo.category.equals("payment")) {
                            aidType.isOnHost = true;
                        } else {
                            aidType.isOnHost = resolveInfo.services.get(0).isOnHost();
                            if (!aidType.isOnHost) {
                                aidType.offHostSE = resolveInfo.services.get(0).getOffHostSecureElement();
                            }
                        }
                        routingEntries.put(aid, aidType);
                    } else if (resolveInfo.services.size() > 1) {
                        boolean onHost = DBG;
                        String offHostSE = null;
                        Iterator<ApduServiceInfo> it = resolveInfo.services.iterator();
                        while (true) {
                            if (!it.hasNext()) {
                                break;
                            }
                            ApduServiceInfo service = it.next();
                            onHost |= service.isOnHost();
                            if (!onHost) {
                                if (offHostSE == null) {
                                    offHostSE = service.getOffHostSecureElement();
                                } else if (!offHostSE.equals(service.getOffHostSecureElement())) {
                                    offHostSE = null;
                                    onHost = true;
                                    break;
                                }
                            }
                        }
                        aidType.isOnHost = onHost;
                        aidType.offHostSE = onHost ? null : offHostSE;
                        routingEntries.put(aid, aidType);
                    }
                }
            }
        }
        this.mRoutingManager.configureRouting(routingEntries, force);
    }

    public void onServicesUpdated(int userId, List<ApduServiceInfo> services) {
        synchronized (this.mLock) {
            if (ActivityManager.getCurrentUser() == userId) {
                generateServiceMapLocked(services);
                generateAidCacheLocked();
            }
        }
    }

    public void onPreferredPaymentServiceChanged(ComponentName service) {
        synchronized (this.mLock) {
            this.mPreferredPaymentService = service;
            generateAidCacheLocked();
        }
    }

    public void onPreferredForegroundServiceChanged(ComponentName service) {
        synchronized (this.mLock) {
            this.mPreferredForegroundService = service;
            generateAidCacheLocked();
        }
    }

    public void onNfcDisabled() {
        synchronized (this.mLock) {
            this.mNfcEnabled = DBG;
        }
        this.mRoutingManager.onNfccRoutingTableCleared();
    }

    public void onNfcEnabled() {
        synchronized (this.mLock) {
            this.mNfcEnabled = true;
            updateRoutingLocked(DBG);
        }
    }

    public void onSecureNfcToggled() {
        synchronized (this.mLock) {
            updateRoutingLocked(true);
        }
    }

    String dumpEntry(Map.Entry<String, AidResolveInfo> entry) {
        StringBuilder sb = new StringBuilder();
        String category = entry.getValue().category;
        ApduServiceInfo defaultServiceInfo = entry.getValue().defaultService;
        sb.append("    \"" + entry.getKey() + "\" (category: " + category + ")\n");
        ComponentName defaultComponent = defaultServiceInfo != null ? defaultServiceInfo.getComponent() : null;
        for (ApduServiceInfo serviceInfo : entry.getValue().services) {
            sb.append("        ");
            if (serviceInfo.getComponent().equals(defaultComponent)) {
                sb.append("*DEFAULT* ");
            }
            sb.append(serviceInfo.getComponent() + " (Description: " + serviceInfo.getDescription() + ")\n");
        }
        return sb.toString();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("    AID cache entries: ");
        for (Map.Entry<String, AidResolveInfo> entry : this.mAidCache.entrySet()) {
            pw.println(dumpEntry(entry));
        }
        pw.println("    Service preferred by foreground app: " + this.mPreferredForegroundService);
        pw.println("    Preferred payment service: " + this.mPreferredPaymentService);
        pw.println("");
        this.mRoutingManager.dump(fd, pw, args);
        pw.println("");
    }
}
