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
import android.nfc.cardemulation.AidGroup;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
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
public class RegisteredServicesCache {
    static final boolean DEBUG = false;
    static final String TAG = "RegisteredServicesCache";
    static final String XML_INDENT_OUTPUT_FEATURE = "http://xmlpull.org/v1/doc/features.html#indent-output";
    final Callback mCallback;
    final Context mContext;
    final AtomicFile mDynamicSettingsFile;
    final AtomicReference<BroadcastReceiver> mReceiver;
    final Object mLock = new Object();
    final SparseArray<UserServices> mUserServices = new SparseArray<>();

    /* loaded from: classes.dex */
    public interface Callback {
        void onServicesUpdated(int i, List<ApduServiceInfo> list);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public static class DynamicSettings {
        public final HashMap<String, AidGroup> aidGroups = Maps.newHashMap();
        public String offHostSE;
        public final int uid;

        DynamicSettings(int uid) {
            this.uid = uid;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class UserServices {
        final HashMap<ComponentName, DynamicSettings> dynamicSettings;
        final HashMap<ComponentName, ApduServiceInfo> services;

        private UserServices() {
            this.services = Maps.newHashMap();
            this.dynamicSettings = Maps.newHashMap();
        }
    }

    private UserServices findOrCreateUserLocked(int userId) {
        UserServices services = this.mUserServices.get(userId);
        if (services == null) {
            UserServices services2 = new UserServices();
            this.mUserServices.put(userId, services2);
            return services2;
        }
        return services;
    }

    public RegisteredServicesCache(Context context, Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        BroadcastReceiver receiver = new BroadcastReceiver() { // from class: com.android.nfc.cardemulation.RegisteredServicesCache.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                int uid = intent.getIntExtra("android.intent.extra.UID", -1);
                String action = intent.getAction();
                if (uid != -1) {
                    boolean replaced = RegisteredServicesCache.DEBUG;
                    if (intent.getBooleanExtra("android.intent.extra.REPLACING", RegisteredServicesCache.DEBUG) && ("android.intent.action.PACKAGE_ADDED".equals(action) || "android.intent.action.PACKAGE_REMOVED".equals(action))) {
                        replaced = true;
                    }
                    if (!replaced) {
                        int currentUser = ActivityManager.getCurrentUser();
                        if (currentUser == UserHandle.getUserId(uid)) {
                            RegisteredServicesCache.this.invalidateCache(UserHandle.getUserId(uid));
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
        this.mDynamicSettingsFile = new AtomicFile(new File(dataDir, "dynamic_aids.xml"));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void initialize() {
        synchronized (this.mLock) {
            readDynamicSettingsLocked();
        }
        invalidateCache(ActivityManager.getCurrentUser());
    }

    void dump(ArrayList<ApduServiceInfo> services) {
        Iterator<ApduServiceInfo> it = services.iterator();
        while (it.hasNext()) {
            it.next();
        }
    }

    boolean containsServiceLocked(ArrayList<ApduServiceInfo> services, ComponentName serviceName) {
        Iterator<ApduServiceInfo> it = services.iterator();
        while (it.hasNext()) {
            ApduServiceInfo service = it.next();
            if (service.getComponent().equals(serviceName)) {
                return true;
            }
        }
        return DEBUG;
    }

    public boolean hasService(int userId, ComponentName service) {
        if (getService(userId, service) != null) {
            return true;
        }
        return DEBUG;
    }

    public ApduServiceInfo getService(int userId, ComponentName service) {
        ApduServiceInfo apduServiceInfo;
        synchronized (this.mLock) {
            UserServices userServices = findOrCreateUserLocked(userId);
            apduServiceInfo = userServices.services.get(service);
        }
        return apduServiceInfo;
    }

    public List<ApduServiceInfo> getServices(int userId) {
        ArrayList<ApduServiceInfo> services = new ArrayList<>();
        synchronized (this.mLock) {
            UserServices userServices = findOrCreateUserLocked(userId);
            services.addAll(userServices.services.values());
        }
        return services;
    }

    public List<ApduServiceInfo> getServicesForCategory(int userId, String category) {
        ArrayList<ApduServiceInfo> services = new ArrayList<>();
        synchronized (this.mLock) {
            UserServices userServices = findOrCreateUserLocked(userId);
            for (ApduServiceInfo service : userServices.services.values()) {
                if (service.hasCategory(category)) {
                    services.add(service);
                }
            }
        }
        return services;
    }

    ArrayList<ApduServiceInfo> getInstalledServices(int userId) {
        boolean onHost;
        ServiceInfo si;
        ComponentName componentName;
        try {
            Context context = this.mContext;
            UserHandle userHandle = new UserHandle(userId);
            boolean z = DEBUG;
            PackageManager pm = context.createPackageContextAsUser("android", 0, userHandle).getPackageManager();
            ArrayList<ApduServiceInfo> validServices = new ArrayList<>();
            List<ResolveInfo> resolvedServices = new ArrayList<>(pm.queryIntentServicesAsUser(new Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE"), 128, userId));
            List<ResolveInfo> resolvedOffHostServices = pm.queryIntentServicesAsUser(new Intent("android.nfc.cardemulation.action.OFF_HOST_APDU_SERVICE"), 128, userId);
            resolvedServices.addAll(resolvedOffHostServices);
            for (ResolveInfo resolvedService : resolvedServices) {
                try {
                    onHost = !resolvedOffHostServices.contains(resolvedService) ? true : z;
                    si = resolvedService.serviceInfo;
                    componentName = new ComponentName(si.packageName, si.name);
                } catch (IOException e) {
                    Log.w(TAG, "Unable to load component info " + resolvedService.toString(), e);
                } catch (XmlPullParserException e2) {
                    Log.w(TAG, "Unable to load component info " + resolvedService.toString(), e2);
                }
                if (pm.checkPermission("android.permission.NFC", si.packageName) != 0) {
                    Log.e(TAG, "Skipping application component " + componentName + ": it must request the permission android.permission.NFC");
                    z = DEBUG;
                } else if ("android.permission.BIND_NFC_SERVICE".equals(si.permission)) {
                    ApduServiceInfo service = new ApduServiceInfo(pm, resolvedService, onHost);
                    validServices.add(service);
                    z = DEBUG;
                } else {
                    Log.e(TAG, "Skipping APDU service " + componentName + ": it does not require the permission android.permission.BIND_NFC_SERVICE");
                    z = DEBUG;
                }
            }
            return validServices;
        } catch (PackageManager.NameNotFoundException e3) {
            Log.e(TAG, "Could not create user package context");
            return null;
        }
    }

    public void invalidateCache(int userId) {
        ArrayList<ApduServiceInfo> validServices = getInstalledServices(userId);
        if (validServices == null) {
            return;
        }
        synchronized (this.mLock) {
            UserServices userServices = findOrCreateUserLocked(userId);
            Iterator<Map.Entry<ComponentName, ApduServiceInfo>> it = userServices.services.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ComponentName, ApduServiceInfo> entry = it.next();
                if (!containsServiceLocked(validServices, entry.getKey())) {
                    Log.d(TAG, "Service removed: " + entry.getKey());
                    it.remove();
                }
            }
            Iterator<ApduServiceInfo> it2 = validServices.iterator();
            while (it2.hasNext()) {
                ApduServiceInfo service = it2.next();
                userServices.services.put(service.getComponent(), service);
            }
            ArrayList<ComponentName> toBeRemoved = new ArrayList<>();
            for (Map.Entry<ComponentName, DynamicSettings> entry2 : userServices.dynamicSettings.entrySet()) {
                ComponentName component = entry2.getKey();
                DynamicSettings dynamicSettings = entry2.getValue();
                ApduServiceInfo serviceInfo = userServices.services.get(component);
                if (serviceInfo != null && serviceInfo.getUid() == dynamicSettings.uid) {
                    for (AidGroup group : dynamicSettings.aidGroups.values()) {
                        serviceInfo.setOrReplaceDynamicAidGroup(group);
                    }
                    if (dynamicSettings.offHostSE != null) {
                        serviceInfo.setOffHostSecureElement(dynamicSettings.offHostSE);
                    }
                }
                toBeRemoved.add(component);
            }
            if (toBeRemoved.size() > 0) {
                Iterator<ComponentName> it3 = toBeRemoved.iterator();
                while (it3.hasNext()) {
                    ComponentName component2 = it3.next();
                    Log.d(TAG, "Removing dynamic AIDs registered by " + component2);
                    userServices.dynamicSettings.remove(component2);
                }
                writeDynamicSettingsLocked();
            }
        }
        this.mCallback.onServicesUpdated(userId, Collections.unmodifiableList(validServices));
        dump(validServices);
    }

    private void readDynamicSettingsLocked() {
        int i;
        int i2;
        FileInputStream fis = null;
        try {
            try {
                try {
                } catch (Throwable th) {
                    if (0 != 0) {
                        try {
                            fis.close();
                        } catch (IOException e) {
                        }
                    }
                    throw th;
                }
            } catch (Exception e2) {
                Log.e(TAG, "Could not parse dynamic AIDs file, trashing.");
                this.mDynamicSettingsFile.delete();
                if (0 == 0) {
                    return;
                }
                fis.close();
            }
            if (!this.mDynamicSettingsFile.getBaseFile().exists()) {
                Log.d(TAG, "Dynamic AIDs file does not exist.");
                if (0 != 0) {
                    try {
                        fis.close();
                        return;
                    } catch (IOException e3) {
                        return;
                    }
                }
                return;
            }
            FileInputStream fis2 = this.mDynamicSettingsFile.openRead();
            XmlPullParser parser = Xml.newPullParser();
            String str = null;
            parser.setInput(fis2, null);
            int eventType = parser.getEventType();
            while (true) {
                i = 1;
                i2 = 2;
                if (eventType == 2 || eventType == 1) {
                    break;
                }
                eventType = parser.next();
            }
            if (AppChooserActivity.EXTRA_APDU_SERVICES.equals(parser.getName())) {
                boolean inService = DEBUG;
                ComponentName currentComponent = null;
                int currentUid = -1;
                String currentOffHostSE = null;
                ArrayList<AidGroup> currentGroups = new ArrayList<>();
                int eventType2 = eventType;
                while (eventType2 != i) {
                    String tagName = parser.getName();
                    if (eventType2 == i2) {
                        if (NotificationCompat.CATEGORY_SERVICE.equals(tagName) && parser.getDepth() == i2) {
                            String compString = parser.getAttributeValue(str, "component");
                            String uidString = parser.getAttributeValue(str, "uid");
                            String offHostString = parser.getAttributeValue(str, "offHostSE");
                            if (compString == null || uidString == null) {
                                Log.e(TAG, "Invalid service attributes");
                            } else {
                                try {
                                    currentUid = Integer.parseInt(uidString);
                                    currentComponent = ComponentName.unflattenFromString(compString);
                                    currentOffHostSE = offHostString;
                                    inService = true;
                                } catch (NumberFormatException e4) {
                                    Log.e(TAG, "Could not parse service uid");
                                }
                            }
                        }
                        if ("aid-group".equals(tagName) && parser.getDepth() == 3 && inService) {
                            AidGroup group = AidGroup.createFromXml(parser);
                            if (group != null) {
                                currentGroups.add(group);
                            } else {
                                Log.e(TAG, "Could not parse AID group.");
                            }
                        }
                    } else if (eventType2 == 3 && NotificationCompat.CATEGORY_SERVICE.equals(tagName)) {
                        if (currentComponent != null && currentUid >= 0) {
                            if (currentGroups.size() <= 0 && currentOffHostSE == null) {
                            }
                            int userId = UserHandle.getUserId(currentUid);
                            DynamicSettings dynSettings = new DynamicSettings(currentUid);
                            Iterator<AidGroup> it = currentGroups.iterator();
                            while (it.hasNext()) {
                                AidGroup group2 = it.next();
                                dynSettings.aidGroups.put(group2.getCategory(), group2);
                                eventType2 = eventType2;
                            }
                            dynSettings.offHostSE = currentOffHostSE;
                            UserServices services = findOrCreateUserLocked(userId);
                            services.dynamicSettings.put(currentComponent, dynSettings);
                        }
                        currentGroups.clear();
                        currentUid = -1;
                        currentComponent = null;
                        inService = false;
                        currentOffHostSE = null;
                    }
                    eventType2 = parser.next();
                    str = null;
                    i = 1;
                    i2 = 2;
                }
            }
            if (fis2 != null) {
                fis2.close();
            }
        } catch (IOException e5) {
        }
    }

    private boolean writeDynamicSettingsLocked() {
        FileOutputStream fos = null;
        try {
            fos = this.mDynamicSettingsFile.startWrite();
            FastXmlSerializer fastXmlSerializer = new FastXmlSerializer();
            fastXmlSerializer.setOutput(fos, "utf-8");
            fastXmlSerializer.startDocument(null, true);
            fastXmlSerializer.setFeature(XML_INDENT_OUTPUT_FEATURE, true);
            fastXmlSerializer.startTag(null, AppChooserActivity.EXTRA_APDU_SERVICES);
            for (int i = 0; i < this.mUserServices.size(); i++) {
                UserServices user = this.mUserServices.valueAt(i);
                for (Map.Entry<ComponentName, DynamicSettings> service : user.dynamicSettings.entrySet()) {
                    fastXmlSerializer.startTag(null, NotificationCompat.CATEGORY_SERVICE);
                    fastXmlSerializer.attribute(null, "component", service.getKey().flattenToString());
                    fastXmlSerializer.attribute(null, "uid", Integer.toString(service.getValue().uid));
                    if (service.getValue().offHostSE != null) {
                        fastXmlSerializer.attribute(null, "offHostSE", service.getValue().offHostSE);
                    }
                    for (AidGroup group : service.getValue().aidGroups.values()) {
                        group.writeAsXml(fastXmlSerializer);
                    }
                    fastXmlSerializer.endTag(null, NotificationCompat.CATEGORY_SERVICE);
                }
            }
            fastXmlSerializer.endTag(null, AppChooserActivity.EXTRA_APDU_SERVICES);
            fastXmlSerializer.endDocument();
            this.mDynamicSettingsFile.finishWrite(fos);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error writing dynamic AIDs", e);
            if (fos != null) {
                this.mDynamicSettingsFile.failWrite(fos);
            }
            return DEBUG;
        }
    }

    public boolean setOffHostSecureElement(int userId, int uid, ComponentName componentName, String offHostSE) {
        synchronized (this.mLock) {
            UserServices services = findOrCreateUserLocked(userId);
            ApduServiceInfo serviceInfo = getService(userId, componentName);
            if (serviceInfo == null) {
                Log.e(TAG, "Service " + componentName + " does not exist.");
                return DEBUG;
            } else if (serviceInfo.getUid() != uid) {
                Log.e(TAG, "UID mismatch.");
                return DEBUG;
            } else {
                if (offHostSE != null && !serviceInfo.isOnHost()) {
                    DynamicSettings dynSettings = services.dynamicSettings.get(componentName);
                    if (dynSettings == null) {
                        dynSettings = new DynamicSettings(uid);
                    }
                    dynSettings.offHostSE = offHostSE;
                    boolean success = writeDynamicSettingsLocked();
                    if (!success) {
                        Log.e(TAG, "Failed to persist AID group.");
                        dynSettings.offHostSE = null;
                        return DEBUG;
                    }
                    serviceInfo.setOffHostSecureElement(offHostSE);
                    ArrayList<ApduServiceInfo> newServices = new ArrayList<>((Collection<? extends ApduServiceInfo>) services.services.values());
                    this.mCallback.onServicesUpdated(userId, newServices);
                    return true;
                }
                Log.e(TAG, "OffHostSE mismatch with Service type");
                return DEBUG;
            }
        }
    }

    public boolean unsetOffHostSecureElement(int userId, int uid, ComponentName componentName) {
        synchronized (this.mLock) {
            UserServices services = findOrCreateUserLocked(userId);
            ApduServiceInfo serviceInfo = getService(userId, componentName);
            if (serviceInfo == null) {
                Log.e(TAG, "Service " + componentName + " does not exist.");
                return DEBUG;
            } else if (serviceInfo.getUid() != uid) {
                Log.e(TAG, "UID mismatch.");
                return DEBUG;
            } else {
                if (!serviceInfo.isOnHost() && serviceInfo.getOffHostSecureElement() != null) {
                    DynamicSettings dynSettings = services.dynamicSettings.get(componentName);
                    String offHostSE = dynSettings.offHostSE;
                    dynSettings.offHostSE = null;
                    boolean success = writeDynamicSettingsLocked();
                    if (!success) {
                        Log.e(TAG, "Failed to persist AID group.");
                        dynSettings.offHostSE = offHostSE;
                        return DEBUG;
                    }
                    serviceInfo.unsetOffHostSecureElement();
                    ArrayList<ApduServiceInfo> newServices = new ArrayList<>((Collection<? extends ApduServiceInfo>) services.services.values());
                    this.mCallback.onServicesUpdated(userId, newServices);
                    return true;
                }
                Log.e(TAG, "OffHostSE is not set");
                return DEBUG;
            }
        }
    }

    public boolean registerAidGroupForService(int userId, int uid, ComponentName componentName, AidGroup aidGroup) {
        ArrayList<ApduServiceInfo> newServices = null;
        synchronized (this.mLock) {
            UserServices services = findOrCreateUserLocked(userId);
            ApduServiceInfo serviceInfo = getService(userId, componentName);
            if (serviceInfo == null) {
                Log.e(TAG, "Service " + componentName + " does not exist.");
                return DEBUG;
            } else if (serviceInfo.getUid() != uid) {
                Log.e(TAG, "UID mismatch.");
                return DEBUG;
            } else {
                List<String> aids = aidGroup.getAids();
                for (String aid : aids) {
                    if (!CardEmulation.isValidAid(aid)) {
                        Log.e(TAG, "AID " + aid + " is not a valid AID");
                        return DEBUG;
                    }
                }
                serviceInfo.setOrReplaceDynamicAidGroup(aidGroup);
                DynamicSettings dynSettings = services.dynamicSettings.get(componentName);
                if (dynSettings == null) {
                    dynSettings = new DynamicSettings(uid);
                    dynSettings.offHostSE = null;
                    services.dynamicSettings.put(componentName, dynSettings);
                }
                dynSettings.aidGroups.put(aidGroup.getCategory(), aidGroup);
                boolean success = writeDynamicSettingsLocked();
                if (success) {
                    newServices = new ArrayList<>((Collection<? extends ApduServiceInfo>) services.services.values());
                } else {
                    Log.e(TAG, "Failed to persist AID group.");
                    dynSettings.aidGroups.remove(aidGroup.getCategory());
                }
                if (success) {
                    this.mCallback.onServicesUpdated(userId, newServices);
                }
                return success;
            }
        }
    }

    public AidGroup getAidGroupForService(int userId, int uid, ComponentName componentName, String category) {
        ApduServiceInfo serviceInfo = getService(userId, componentName);
        if (serviceInfo != null) {
            if (serviceInfo.getUid() != uid) {
                Log.e(TAG, "UID mismatch");
                return null;
            }
            return serviceInfo.getDynamicAidGroupForCategory(category);
        }
        Log.e(TAG, "Could not find service " + componentName);
        return null;
    }

    public boolean removeAidGroupForService(int userId, int uid, ComponentName componentName, String category) {
        boolean success = DEBUG;
        ArrayList<ApduServiceInfo> newServices = null;
        synchronized (this.mLock) {
            UserServices services = findOrCreateUserLocked(userId);
            ApduServiceInfo serviceInfo = getService(userId, componentName);
            if (serviceInfo != null) {
                if (serviceInfo.getUid() != uid) {
                    Log.e(TAG, "UID mismatch");
                    return DEBUG;
                } else if (!serviceInfo.removeDynamicAidGroupForCategory(category)) {
                    Log.e(TAG, " Could not find dynamic AIDs for category " + category);
                    return DEBUG;
                } else {
                    DynamicSettings dynSettings = services.dynamicSettings.get(componentName);
                    if (dynSettings != null) {
                        AidGroup deletedGroup = dynSettings.aidGroups.remove(category);
                        success = writeDynamicSettingsLocked();
                        if (success) {
                            newServices = new ArrayList<>((Collection<? extends ApduServiceInfo>) services.services.values());
                        } else {
                            Log.e(TAG, "Could not persist deleted AID group.");
                            dynSettings.aidGroups.put(category, deletedGroup);
                            return DEBUG;
                        }
                    } else {
                        Log.e(TAG, "Could not find aid group in local cache.");
                    }
                }
            } else {
                Log.e(TAG, "Service " + componentName + " does not exist.");
            }
            if (success) {
                this.mCallback.onServicesUpdated(userId, newServices);
            }
            return success;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Registered HCE services for current user: ");
        UserServices userServices = findOrCreateUserLocked(ActivityManager.getCurrentUser());
        for (ApduServiceInfo service : userServices.services.values()) {
            service.dump(fd, pw, args);
            pw.println("");
        }
        pw.println("");
    }
}
