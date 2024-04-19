package com.android.nfc;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserHandle;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes.dex */
public class RegisteredComponentCache {
    private static final boolean DEBUG = false;
    private static final String TAG = "RegisteredComponentCache";
    final String mAction;
    private ArrayList<ComponentInfo> mComponents;
    final Context mContext;
    final String mMetaDataName;
    final AtomicReference<BroadcastReceiver> mReceiver;

    public RegisteredComponentCache(Context context, String action, String metaDataName) {
        this.mContext = context;
        this.mAction = action;
        this.mMetaDataName = metaDataName;
        generateComponentsList();
        BroadcastReceiver receiver = new BroadcastReceiver() { // from class: com.android.nfc.RegisteredComponentCache.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context1, Intent intent) {
                RegisteredComponentCache.this.generateComponentsList();
            }
        };
        this.mReceiver = new AtomicReference<>(receiver);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addAction("android.intent.action.PACKAGE_CHANGED");
        intentFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter.addDataScheme("package");
        this.mContext.registerReceiverAsUser(receiver, UserHandle.ALL, intentFilter, null, null);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
        this.mContext.registerReceiverAsUser(receiver, UserHandle.ALL, sdFilter, null, null);
        IntentFilter userFilter = new IntentFilter();
        userFilter.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiverAsUser(receiver, UserHandle.ALL, userFilter, null, null);
    }

    /* loaded from: classes.dex */
    public static class ComponentInfo {
        public final ResolveInfo resolveInfo;
        public final String[] techs;

        ComponentInfo(ResolveInfo resolveInfo, String[] techs) {
            this.resolveInfo = resolveInfo;
            this.techs = techs;
        }

        public String toString() {
            String[] strArr;
            StringBuilder out = new StringBuilder("ComponentInfo: ");
            out.append(this.resolveInfo);
            out.append(", techs: ");
            for (String tech : this.techs) {
                out.append(tech);
                out.append(", ");
            }
            return out.toString();
        }
    }

    public ArrayList<ComponentInfo> getComponents() {
        ArrayList<ComponentInfo> arrayList;
        synchronized (this) {
            arrayList = this.mComponents;
        }
        return arrayList;
    }

    public void close() {
        BroadcastReceiver receiver = this.mReceiver.getAndSet(null);
        if (receiver != null) {
            this.mContext.unregisterReceiver(receiver);
        }
    }

    protected void finalize() throws Throwable {
        if (this.mReceiver.get() != null) {
            Log.e(TAG, "RegisteredServicesCache finalized without being closed");
        }
        close();
        super.finalize();
    }

    void dump(ArrayList<ComponentInfo> components) {
        Iterator<ComponentInfo> it = components.iterator();
        while (it.hasNext()) {
            ComponentInfo component = it.next();
            Log.i(TAG, component.toString());
        }
    }

    void generateComponentsList() {
        try {
            UserHandle currentUser = new UserHandle(ActivityManager.getCurrentUser());
            PackageManager pm = this.mContext.createPackageContextAsUser("android", 0, currentUser).getPackageManager();
            ArrayList<ComponentInfo> components = new ArrayList<>();
            List<ResolveInfo> resolveInfos = pm.queryIntentActivitiesAsUser(new Intent(this.mAction), 128, ActivityManager.getCurrentUser());
            for (ResolveInfo resolveInfo : resolveInfos) {
                try {
                    parseComponentInfo(pm, resolveInfo, components);
                } catch (IOException e) {
                    Log.w(TAG, "Unable to load component info " + resolveInfo.toString(), e);
                } catch (XmlPullParserException e2) {
                    Log.w(TAG, "Unable to load component info " + resolveInfo.toString(), e2);
                }
            }
            synchronized (this) {
                this.mComponents = components;
            }
        } catch (PackageManager.NameNotFoundException e3) {
            Log.e(TAG, "Could not create user package context");
        }
    }

    void parseComponentInfo(PackageManager pm, ResolveInfo info, ArrayList<ComponentInfo> components) throws XmlPullParserException, IOException {
        ActivityInfo ai = info.activityInfo;
        XmlResourceParser parser = null;
        try {
            try {
                XmlResourceParser parser2 = ai.loadXmlMetaData(pm, this.mMetaDataName);
                if (parser2 == null) {
                    throw new XmlPullParserException("No " + this.mMetaDataName + " meta-data");
                }
                parseTechLists(pm.getResourcesForApplication(ai.applicationInfo), ai.packageName, parser2, info, components);
                parser2.close();
            } catch (PackageManager.NameNotFoundException e) {
                throw new XmlPullParserException("Unable to load resources for " + ai.packageName);
            }
        } catch (Throwable th) {
            if (0 != 0) {
                parser.close();
            }
            throw th;
        }
    }

    void parseTechLists(Resources res, String packageName, XmlPullParser parser, ResolveInfo resolveInfo, ArrayList<ComponentInfo> components) throws XmlPullParserException, IOException {
        int size;
        int eventType = parser.getEventType();
        while (eventType != 2) {
            eventType = parser.next();
        }
        ArrayList<String> items = new ArrayList<>();
        int eventType2 = parser.next();
        do {
            String tagName = parser.getName();
            if (eventType2 == 2 && "tech".equals(tagName)) {
                items.add(parser.nextText());
            } else if (eventType2 == 3 && "tech-list".equals(tagName) && (size = items.size()) > 0) {
                String[] techs = new String[size];
                String[] techs2 = (String[]) items.toArray(techs);
                items.clear();
                components.add(new ComponentInfo(resolveInfo, techs2));
            }
            eventType2 = parser.next();
        } while (eventType2 != 1);
    }
}
