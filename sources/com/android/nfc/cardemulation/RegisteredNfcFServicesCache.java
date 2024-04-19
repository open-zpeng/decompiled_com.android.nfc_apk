package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.nfc.cardemulation.NfcFCardEmulation;
import android.nfc.cardemulation.NfcFServiceInfo;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;
import android.util.SparseArray;
import android.util.Xml;
import androidx.core.app.NotificationCompat;
import com.android.internal.util.FastXmlSerializer;
import com.google.android.collect.Maps;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes.dex */
public class RegisteredNfcFServicesCache {
    static final boolean DBG = false;
    static final String TAG = "RegisteredNfcFServicesCache";
    static final String XML_INDENT_OUTPUT_FEATURE = "http://xmlpull.org/v1/doc/features.html#indent-output";
    final Callback mCallback;
    final Context mContext;
    final AtomicFile mDynamicSystemCodeNfcid2File;
    final AtomicReference<BroadcastReceiver> mReceiver;
    final Object mLock = new Object();
    final SparseArray<UserServices> mUserServices = new SparseArray<>();
    boolean mActivated = DBG;
    boolean mUserSwitched = DBG;

    /* loaded from: classes.dex */
    public interface Callback {
        void onNfcFServicesUpdated(int i, List<NfcFServiceInfo> list);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public static class DynamicSystemCode {
        public final String systemCode;
        public final int uid;

        DynamicSystemCode(int uid, String systemCode) {
            this.uid = uid;
            this.systemCode = systemCode;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public static class DynamicNfcid2 {
        public final String nfcid2;
        public final int uid;

        DynamicNfcid2(int uid, String nfcid2) {
            this.uid = uid;
            this.nfcid2 = nfcid2;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class UserServices {
        final HashMap<ComponentName, DynamicNfcid2> dynamicNfcid2;
        final HashMap<ComponentName, DynamicSystemCode> dynamicSystemCode;
        final HashMap<ComponentName, NfcFServiceInfo> services;

        private UserServices() {
            this.services = Maps.newHashMap();
            this.dynamicSystemCode = Maps.newHashMap();
            this.dynamicNfcid2 = Maps.newHashMap();
        }
    }

    private UserServices findOrCreateUserLocked(int userId) {
        UserServices userServices = this.mUserServices.get(userId);
        if (userServices == null) {
            UserServices userServices2 = new UserServices();
            this.mUserServices.put(userId, userServices2);
            return userServices2;
        }
        return userServices;
    }

    public RegisteredNfcFServicesCache(Context context, Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        BroadcastReceiver receiver = new BroadcastReceiver() { // from class: com.android.nfc.cardemulation.RegisteredNfcFServicesCache.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                int uid = intent.getIntExtra("android.intent.extra.UID", -1);
                String action = intent.getAction();
                if (uid != -1) {
                    boolean replaced = RegisteredNfcFServicesCache.DBG;
                    if (intent.getBooleanExtra("android.intent.extra.REPLACING", RegisteredNfcFServicesCache.DBG) && ("android.intent.action.PACKAGE_ADDED".equals(action) || "android.intent.action.PACKAGE_REMOVED".equals(action))) {
                        replaced = true;
                    }
                    if (!replaced) {
                        int currentUser = ActivityManager.getCurrentUser();
                        if (currentUser == UserHandle.getUserId(uid)) {
                            RegisteredNfcFServicesCache.this.invalidateCache(UserHandle.getUserId(uid));
                        }
                    }
                }
            }
        };
        this.mReceiver = new AtomicReference<>(receiver);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addAction("android.intent.action.PACKAGE_CHANGED");
        intentFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter.addAction("android.intent.action.PACKAGE_REPLACED");
        intentFilter.addAction("android.intent.action.PACKAGE_FIRST_LAUNCH");
        intentFilter.addAction("android.intent.action.PACKAGE_RESTARTED");
        intentFilter.addDataScheme("package");
        this.mContext.registerReceiverAsUser(this.mReceiver.get(), UserHandle.ALL, intentFilter, null, null);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
        this.mContext.registerReceiverAsUser(this.mReceiver.get(), UserHandle.ALL, sdFilter, null, null);
        File dataDir = this.mContext.getFilesDir();
        this.mDynamicSystemCodeNfcid2File = new AtomicFile(new File(dataDir, "dynamic_systemcode_nfcid2.xml"));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void initialize() {
        synchronized (this.mLock) {
            readDynamicSystemCodeNfcid2Locked();
        }
        invalidateCache(ActivityManager.getCurrentUser());
    }

    void dump(ArrayList<NfcFServiceInfo> services) {
        Iterator<NfcFServiceInfo> it = services.iterator();
        while (it.hasNext()) {
            NfcFServiceInfo service = it.next();
            Log.d(TAG, service.toString());
        }
    }

    boolean containsServiceLocked(ArrayList<NfcFServiceInfo> services, ComponentName componentName) {
        Iterator<NfcFServiceInfo> it = services.iterator();
        while (it.hasNext()) {
            NfcFServiceInfo service = it.next();
            if (service.getComponent().equals(componentName)) {
                return true;
            }
        }
        return DBG;
    }

    public boolean hasService(int userId, ComponentName componentName) {
        if (getService(userId, componentName) != null) {
            return true;
        }
        return DBG;
    }

    public NfcFServiceInfo getService(int userId, ComponentName componentName) {
        NfcFServiceInfo nfcFServiceInfo;
        synchronized (this.mLock) {
            UserServices userServices = findOrCreateUserLocked(userId);
            nfcFServiceInfo = userServices.services.get(componentName);
        }
        return nfcFServiceInfo;
    }

    public List<NfcFServiceInfo> getServices(int userId) {
        ArrayList<NfcFServiceInfo> services = new ArrayList<>();
        synchronized (this.mLock) {
            UserServices userServices = findOrCreateUserLocked(userId);
            services.addAll(userServices.services.values());
        }
        return services;
    }

    ArrayList<NfcFServiceInfo> getInstalledServices(int userId) {
        try {
            PackageManager pm = this.mContext.createPackageContextAsUser("android", 0, new UserHandle(userId)).getPackageManager();
            ArrayList<NfcFServiceInfo> validServices = new ArrayList<>();
            List<ResolveInfo> resolvedServices = pm.queryIntentServicesAsUser(new Intent("android.nfc.cardemulation.action.HOST_NFCF_SERVICE"), 128, userId);
            for (ResolveInfo resolvedService : resolvedServices) {
                try {
                    ServiceInfo si = resolvedService.serviceInfo;
                    ComponentName componentName = new ComponentName(si.packageName, si.name);
                    if (pm.checkPermission("android.permission.NFC", si.packageName) != 0) {
                        Log.e(TAG, "Skipping NfcF service " + componentName + ": it does not require the permission android.permission.NFC");
                    } else if ("android.permission.BIND_NFC_SERVICE".equals(si.permission)) {
                        NfcFServiceInfo service = new NfcFServiceInfo(pm, resolvedService);
                        validServices.add(service);
                    } else {
                        Log.e(TAG, "Skipping NfcF service " + componentName + ": it does not require the permission android.permission.BIND_NFC_SERVICE");
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Unable to load component info " + resolvedService.toString(), e);
                } catch (XmlPullParserException e2) {
                    Log.w(TAG, "Unable to load component info " + resolvedService.toString(), e2);
                }
            }
            return validServices;
        } catch (PackageManager.NameNotFoundException e3) {
            Log.e(TAG, "Could not create user package context");
            return null;
        }
    }

    public void invalidateCache(int userId) {
        ArrayList<NfcFServiceInfo> newServices;
        ArrayList<NfcFServiceInfo> cachedServices;
        ArrayList<NfcFServiceInfo> validServices;
        ArrayList<NfcFServiceInfo> validServices2 = getInstalledServices(userId);
        if (validServices2 == null) {
            return;
        }
        ArrayList<NfcFServiceInfo> newServices2 = null;
        synchronized (this.mLock) {
            try {
                UserServices userServices = findOrCreateUserLocked(userId);
                ArrayList<NfcFServiceInfo> cachedServices2 = new ArrayList<>((Collection<? extends NfcFServiceInfo>) userServices.services.values());
                ArrayList<NfcFServiceInfo> toBeAdded = new ArrayList<>();
                ArrayList<NfcFServiceInfo> toBeRemoved = new ArrayList<>();
                boolean matched = DBG;
                Iterator<NfcFServiceInfo> it = validServices2.iterator();
                while (it.hasNext()) {
                    try {
                        NfcFServiceInfo validService = it.next();
                        Iterator<NfcFServiceInfo> it2 = cachedServices2.iterator();
                        while (true) {
                            if (!it2.hasNext()) {
                                break;
                            } else if (validService.equals(it2.next())) {
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) {
                            toBeAdded.add(validService);
                        }
                        matched = DBG;
                    } catch (Throwable th) {
                        th = th;
                        while (true) {
                            try {
                                break;
                            } catch (Throwable th2) {
                                th = th2;
                            }
                        }
                        throw th;
                    }
                }
                Iterator<NfcFServiceInfo> it3 = cachedServices2.iterator();
                while (it3.hasNext()) {
                    NfcFServiceInfo cachedService = it3.next();
                    Iterator<NfcFServiceInfo> it4 = validServices2.iterator();
                    while (true) {
                        if (!it4.hasNext()) {
                            break;
                        } else if (cachedService.equals(it4.next())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        toBeRemoved.add(cachedService);
                    }
                    matched = DBG;
                }
                if (this.mUserSwitched) {
                    Log.d(TAG, "User switched, rebuild internal cache");
                    this.mUserSwitched = DBG;
                } else if (toBeAdded.size() == 0 && toBeRemoved.size() == 0) {
                    Log.d(TAG, "Service unchanged, not updating");
                    return;
                }
                Iterator<NfcFServiceInfo> it5 = toBeAdded.iterator();
                while (it5.hasNext()) {
                    NfcFServiceInfo service = it5.next();
                    userServices.services.put(service.getComponent(), service);
                }
                Iterator<NfcFServiceInfo> it6 = toBeRemoved.iterator();
                while (it6.hasNext()) {
                    userServices.services.remove(it6.next().getComponent());
                }
                ArrayList<ComponentName> toBeRemovedDynamicSystemCode = new ArrayList<>();
                for (Map.Entry<ComponentName, DynamicSystemCode> entry : userServices.dynamicSystemCode.entrySet()) {
                    try {
                        ComponentName componentName = entry.getKey();
                        DynamicSystemCode dynamicSystemCode = entry.getValue();
                        NfcFServiceInfo service2 = userServices.services.get(componentName);
                        if (service2 != null) {
                            validServices = validServices2;
                            try {
                                if (service2.getUid() == dynamicSystemCode.uid) {
                                    service2.setOrReplaceDynamicSystemCode(dynamicSystemCode.systemCode);
                                    validServices2 = validServices;
                                }
                            } catch (Throwable th3) {
                                th = th3;
                                while (true) {
                                    break;
                                    break;
                                }
                                throw th;
                            }
                        } else {
                            validServices = validServices2;
                        }
                        toBeRemovedDynamicSystemCode.add(componentName);
                        validServices2 = validServices;
                    } catch (Throwable th4) {
                        th = th4;
                        while (true) {
                            break;
                            break;
                        }
                        throw th;
                    }
                }
                try {
                    ArrayList<ComponentName> toBeRemovedDynamicNfcid2 = new ArrayList<>();
                    for (Map.Entry<ComponentName, DynamicNfcid2> entry2 : userServices.dynamicNfcid2.entrySet()) {
                        try {
                            ComponentName componentName2 = entry2.getKey();
                            DynamicNfcid2 dynamicNfcid2 = entry2.getValue();
                            NfcFServiceInfo service3 = userServices.services.get(componentName2);
                            if (service3 != null) {
                                newServices = newServices2;
                                try {
                                    if (service3.getUid() == dynamicNfcid2.uid) {
                                        service3.setOrReplaceDynamicNfcid2(dynamicNfcid2.nfcid2);
                                        newServices2 = newServices;
                                    }
                                } catch (Throwable th5) {
                                    th = th5;
                                    while (true) {
                                        break;
                                        break;
                                    }
                                    throw th;
                                }
                            } else {
                                newServices = newServices2;
                            }
                            toBeRemovedDynamicNfcid2.add(componentName2);
                            newServices2 = newServices;
                        } catch (Throwable th6) {
                            th = th6;
                            while (true) {
                                break;
                                break;
                            }
                            throw th;
                        }
                    }
                    newServices = newServices2;
                    Iterator<ComponentName> it7 = toBeRemovedDynamicSystemCode.iterator();
                    while (it7.hasNext()) {
                        ComponentName removedComponent = it7.next();
                        Log.d(TAG, "Removing dynamic System Code registered by " + removedComponent);
                        userServices.dynamicSystemCode.remove(removedComponent);
                    }
                    Iterator<ComponentName> it8 = toBeRemovedDynamicNfcid2.iterator();
                    while (it8.hasNext()) {
                        ComponentName removedComponent2 = it8.next();
                        Log.d(TAG, "Removing dynamic NFCID2 registered by " + removedComponent2);
                        userServices.dynamicNfcid2.remove(removedComponent2);
                    }
                    boolean nfcid2Assigned = DBG;
                    for (Map.Entry<ComponentName, NfcFServiceInfo> entry3 : userServices.services.entrySet()) {
                        NfcFServiceInfo service4 = entry3.getValue();
                        if (service4.getNfcid2().equalsIgnoreCase("RANDOM")) {
                            String randomNfcid2 = generateRandomNfcid2();
                            service4.setOrReplaceDynamicNfcid2(randomNfcid2);
                            cachedServices = cachedServices2;
                            userServices.dynamicNfcid2.put(entry3.getKey(), new DynamicNfcid2(service4.getUid(), randomNfcid2));
                            nfcid2Assigned = true;
                        } else {
                            cachedServices = cachedServices2;
                        }
                        cachedServices2 = cachedServices;
                    }
                    if (toBeRemovedDynamicSystemCode.size() > 0 || toBeRemovedDynamicNfcid2.size() > 0 || nfcid2Assigned) {
                        writeDynamicSystemCodeNfcid2Locked();
                    }
                    this.mCallback.onNfcFServicesUpdated(userId, Collections.unmodifiableList(new ArrayList<>((Collection<? extends NfcFServiceInfo>) userServices.services.values())));
                } catch (Throwable th7) {
                    th = th7;
                }
            } catch (Throwable th8) {
                th = th8;
            }
        }
    }

    private void readDynamicSystemCodeNfcid2Locked() {
        int i;
        FileInputStream fis = null;
        try {
            try {
                try {
                } catch (Exception e) {
                    Log.e(TAG, "Could not parse dynamic System Code, NFCID2 file, trashing.");
                    this.mDynamicSystemCodeNfcid2File.delete();
                    if (0 == 0) {
                        return;
                    }
                    fis.close();
                }
                if (!this.mDynamicSystemCodeNfcid2File.getBaseFile().exists()) {
                    Log.d(TAG, "Dynamic System Code, NFCID2 file does not exist.");
                    if (0 != 0) {
                        try {
                            fis.close();
                            return;
                        } catch (IOException e2) {
                            return;
                        }
                    }
                    return;
                }
                FileInputStream fis2 = this.mDynamicSystemCodeNfcid2File.openRead();
                XmlPullParser parser = Xml.newPullParser();
                String str = null;
                parser.setInput(fis2, null);
                int eventType = parser.getEventType();
                while (true) {
                    if (eventType == 2 || eventType == 1) {
                        break;
                    }
                    eventType = parser.next();
                }
                if (AppChooserActivity.EXTRA_APDU_SERVICES.equals(parser.getName())) {
                    ComponentName componentName = null;
                    int currentUid = -1;
                    String systemCode = null;
                    String nfcid2 = null;
                    int eventType2 = eventType;
                    for (i = 1; eventType2 != i; i = 1) {
                        String tagName = parser.getName();
                        if (eventType2 == 2) {
                            if (NotificationCompat.CATEGORY_SERVICE.equals(tagName) && parser.getDepth() == 2) {
                                String compString = parser.getAttributeValue(str, "component");
                                String uidString = parser.getAttributeValue(str, "uid");
                                String systemCodeString = parser.getAttributeValue(str, "system-code");
                                parser.getAttributeValue(str, "description");
                                String nfcid2String = parser.getAttributeValue(str, "nfcid2");
                                if (compString == null || uidString == null) {
                                    Log.e(TAG, "Invalid service attributes");
                                } else {
                                    try {
                                        componentName = ComponentName.unflattenFromString(compString);
                                        currentUid = Integer.parseInt(uidString);
                                        systemCode = systemCodeString;
                                        nfcid2 = nfcid2String;
                                    } catch (NumberFormatException e3) {
                                        Log.e(TAG, "Could not parse service uid");
                                    }
                                }
                            }
                        } else if (eventType2 == 3 && NotificationCompat.CATEGORY_SERVICE.equals(tagName)) {
                            if (componentName != null && currentUid >= 0) {
                                int userId = UserHandle.getUserId(currentUid);
                                UserServices userServices = findOrCreateUserLocked(userId);
                                if (systemCode != null) {
                                    DynamicSystemCode dynamicSystemCode = new DynamicSystemCode(currentUid, systemCode);
                                    userServices.dynamicSystemCode.put(componentName, dynamicSystemCode);
                                }
                                if (nfcid2 != null) {
                                    DynamicNfcid2 dynamicNfcid2 = new DynamicNfcid2(currentUid, nfcid2);
                                    userServices.dynamicNfcid2.put(componentName, dynamicNfcid2);
                                }
                            }
                            systemCode = null;
                            nfcid2 = null;
                            componentName = null;
                            currentUid = -1;
                        }
                        eventType2 = parser.next();
                        str = null;
                    }
                }
                if (fis2 != null) {
                    fis2.close();
                }
            } catch (IOException e4) {
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    fis.close();
                } catch (IOException e5) {
                }
            }
            throw th;
        }
    }

    private boolean writeDynamicSystemCodeNfcid2Locked() {
        FileOutputStream fos = null;
        try {
            fos = this.mDynamicSystemCodeNfcid2File.startWrite();
            FastXmlSerializer fastXmlSerializer = new FastXmlSerializer();
            fastXmlSerializer.setOutput(fos, "utf-8");
            fastXmlSerializer.startDocument(null, true);
            fastXmlSerializer.setFeature(XML_INDENT_OUTPUT_FEATURE, true);
            fastXmlSerializer.startTag(null, AppChooserActivity.EXTRA_APDU_SERVICES);
            for (int i = 0; i < this.mUserServices.size(); i++) {
                UserServices userServices = this.mUserServices.valueAt(i);
                for (Map.Entry<ComponentName, DynamicSystemCode> entry : userServices.dynamicSystemCode.entrySet()) {
                    fastXmlSerializer.startTag(null, NotificationCompat.CATEGORY_SERVICE);
                    fastXmlSerializer.attribute(null, "component", entry.getKey().flattenToString());
                    fastXmlSerializer.attribute(null, "uid", Integer.toString(entry.getValue().uid));
                    fastXmlSerializer.attribute(null, "system-code", entry.getValue().systemCode);
                    if (userServices.dynamicNfcid2.containsKey(entry.getKey())) {
                        fastXmlSerializer.attribute(null, "nfcid2", userServices.dynamicNfcid2.get(entry.getKey()).nfcid2);
                    }
                    fastXmlSerializer.endTag(null, NotificationCompat.CATEGORY_SERVICE);
                }
                for (Map.Entry<ComponentName, DynamicNfcid2> entry2 : userServices.dynamicNfcid2.entrySet()) {
                    if (!userServices.dynamicSystemCode.containsKey(entry2.getKey())) {
                        fastXmlSerializer.startTag(null, NotificationCompat.CATEGORY_SERVICE);
                        fastXmlSerializer.attribute(null, "component", entry2.getKey().flattenToString());
                        fastXmlSerializer.attribute(null, "uid", Integer.toString(entry2.getValue().uid));
                        fastXmlSerializer.attribute(null, "nfcid2", entry2.getValue().nfcid2);
                        fastXmlSerializer.endTag(null, NotificationCompat.CATEGORY_SERVICE);
                    }
                }
            }
            fastXmlSerializer.endTag(null, AppChooserActivity.EXTRA_APDU_SERVICES);
            fastXmlSerializer.endDocument();
            this.mDynamicSystemCodeNfcid2File.finishWrite(fos);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error writing dynamic System Code, NFCID2", e);
            if (fos != null) {
                this.mDynamicSystemCodeNfcid2File.failWrite(fos);
                return DBG;
            }
            return DBG;
        }
    }

    public boolean registerSystemCodeForService(int userId, int uid, ComponentName componentName, String systemCode) {
        ArrayList<NfcFServiceInfo> newServices = null;
        synchronized (this.mLock) {
            if (this.mActivated) {
                Log.d(TAG, "failed to register System Code during activation");
                return DBG;
            }
            UserServices userServices = findOrCreateUserLocked(userId);
            NfcFServiceInfo service = getService(userId, componentName);
            if (service == null) {
                Log.e(TAG, "Service " + componentName + " does not exist.");
                return DBG;
            } else if (service.getUid() != uid) {
                Log.e(TAG, "UID mismatch.");
                return DBG;
            } else if (!systemCode.equalsIgnoreCase("NULL") && !NfcFCardEmulation.isValidSystemCode(systemCode)) {
                Log.e(TAG, "System Code " + systemCode + " is not a valid System Code");
                return DBG;
            } else {
                String systemCode2 = systemCode.toUpperCase();
                DynamicSystemCode oldDynamicSystemCode = userServices.dynamicSystemCode.get(componentName);
                DynamicSystemCode dynamicSystemCode = new DynamicSystemCode(uid, systemCode2);
                userServices.dynamicSystemCode.put(componentName, dynamicSystemCode);
                boolean success = writeDynamicSystemCodeNfcid2Locked();
                if (success) {
                    service.setOrReplaceDynamicSystemCode(systemCode2);
                    newServices = new ArrayList<>((Collection<? extends NfcFServiceInfo>) userServices.services.values());
                } else {
                    Log.e(TAG, "Failed to persist System Code.");
                    if (oldDynamicSystemCode == null) {
                        userServices.dynamicSystemCode.remove(componentName);
                    } else {
                        userServices.dynamicSystemCode.put(componentName, oldDynamicSystemCode);
                    }
                }
                if (success) {
                    this.mCallback.onNfcFServicesUpdated(userId, newServices);
                }
                return success;
            }
        }
    }

    public String getSystemCodeForService(int userId, int uid, ComponentName componentName) {
        NfcFServiceInfo service = getService(userId, componentName);
        if (service != null) {
            if (service.getUid() != uid) {
                Log.e(TAG, "UID mismatch");
                return null;
            }
            return service.getSystemCode();
        }
        Log.e(TAG, "Could not find service " + componentName);
        return null;
    }

    public boolean removeSystemCodeForService(int userId, int uid, ComponentName componentName) {
        return registerSystemCodeForService(userId, uid, componentName, "NULL");
    }

    public boolean setNfcid2ForService(int userId, int uid, ComponentName componentName, String nfcid2) {
        ArrayList<NfcFServiceInfo> newServices = null;
        synchronized (this.mLock) {
            if (this.mActivated) {
                Log.d(TAG, "failed to set NFCID2 during activation");
                return DBG;
            }
            UserServices userServices = findOrCreateUserLocked(userId);
            NfcFServiceInfo service = getService(userId, componentName);
            if (service == null) {
                Log.e(TAG, "Service " + componentName + " does not exist.");
                return DBG;
            } else if (service.getUid() != uid) {
                Log.e(TAG, "UID mismatch.");
                return DBG;
            } else if (!NfcFCardEmulation.isValidNfcid2(nfcid2)) {
                Log.e(TAG, "NFCID2 " + nfcid2 + " is not a valid NFCID2");
                return DBG;
            } else {
                String nfcid22 = nfcid2.toUpperCase();
                DynamicNfcid2 oldDynamicNfcid2 = userServices.dynamicNfcid2.get(componentName);
                DynamicNfcid2 dynamicNfcid2 = new DynamicNfcid2(uid, nfcid22);
                userServices.dynamicNfcid2.put(componentName, dynamicNfcid2);
                boolean success = writeDynamicSystemCodeNfcid2Locked();
                if (success) {
                    service.setOrReplaceDynamicNfcid2(nfcid22);
                    newServices = new ArrayList<>((Collection<? extends NfcFServiceInfo>) userServices.services.values());
                } else {
                    Log.e(TAG, "Failed to persist NFCID2.");
                    if (oldDynamicNfcid2 == null) {
                        userServices.dynamicNfcid2.remove(componentName);
                    } else {
                        userServices.dynamicNfcid2.put(componentName, oldDynamicNfcid2);
                    }
                }
                if (success) {
                    this.mCallback.onNfcFServicesUpdated(userId, newServices);
                }
                return success;
            }
        }
    }

    public String getNfcid2ForService(int userId, int uid, ComponentName componentName) {
        NfcFServiceInfo service = getService(userId, componentName);
        if (service != null) {
            if (service.getUid() != uid) {
                Log.e(TAG, "UID mismatch");
                return null;
            }
            return service.getNfcid2();
        }
        Log.e(TAG, "Could not find service " + componentName);
        return null;
    }

    public void onHostEmulationActivated() {
        synchronized (this.mLock) {
            this.mActivated = true;
        }
    }

    public void onHostEmulationDeactivated() {
        synchronized (this.mLock) {
            this.mActivated = DBG;
        }
    }

    public void onNfcDisabled() {
        synchronized (this.mLock) {
            this.mActivated = DBG;
        }
    }

    public void onUserSwitched() {
        synchronized (this.mLock) {
            this.mUserSwitched = true;
        }
    }

    private String generateRandomNfcid2() {
        long randomNfcid2 = ((long) Math.floor(Math.random() * ((281474976710655L - 0) + 1))) + 0;
        return String.format("02FE%02X%02X%02X%02X%02X%02X", Long.valueOf((randomNfcid2 >>> 40) & 255), Long.valueOf((randomNfcid2 >>> 32) & 255), Long.valueOf((randomNfcid2 >>> 24) & 255), Long.valueOf((randomNfcid2 >>> 16) & 255), Long.valueOf((randomNfcid2 >>> 8) & 255), Long.valueOf((randomNfcid2 >>> 0) & 255));
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Registered HCE-F services for current user: ");
        synchronized (this.mLock) {
            UserServices userServices = findOrCreateUserLocked(ActivityManager.getCurrentUser());
            for (NfcFServiceInfo service : userServices.services.values()) {
                service.dump(fd, pw, args);
                pw.println("");
            }
            pw.println("");
        }
    }
}
