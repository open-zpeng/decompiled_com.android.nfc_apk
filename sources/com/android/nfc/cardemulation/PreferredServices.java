package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.nfc.cardemulation.ApduServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import com.android.nfc.ForegroundUtils;
import com.android.nfc.cardemulation.RegisteredAidCache;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
/* loaded from: classes.dex */
public class PreferredServices implements ForegroundUtils.Callback {
    static final boolean DBG = false;
    static final String TAG = "PreferredCardEmulationServices";
    static final Uri paymentDefaultUri = Settings.Secure.getUriFor("nfc_payment_default_component");
    static final Uri paymentForegroundUri = Settings.Secure.getUriFor("nfc_payment_foreground");
    final RegisteredAidCache mAidCache;
    final Callback mCallback;
    final Context mContext;
    ComponentName mForegroundCurrent;
    ComponentName mForegroundRequested;
    int mForegroundUid;
    ComponentName mNextTapDefault;
    final RegisteredServicesCache mServiceCache;
    final ForegroundUtils mForegroundUtils = ForegroundUtils.getInstance();
    final Handler mHandler = new Handler(Looper.getMainLooper());
    final Object mLock = new Object();
    PaymentDefaults mPaymentDefaults = new PaymentDefaults();
    boolean mClearNextTapDefault = DBG;
    final SettingsObserver mSettingsObserver = new SettingsObserver(this.mHandler);

    /* loaded from: classes.dex */
    public interface Callback {
        void onPreferredForegroundServiceChanged(ComponentName componentName);

        void onPreferredPaymentServiceChanged(ComponentName componentName);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class PaymentDefaults {
        ComponentName currentPreferred;
        boolean preferForeground;
        ComponentName settingsDefault;

        PaymentDefaults() {
        }
    }

    public PreferredServices(Context context, RegisteredServicesCache serviceCache, RegisteredAidCache aidCache, Callback callback) {
        this.mContext = context;
        this.mServiceCache = serviceCache;
        this.mAidCache = aidCache;
        this.mCallback = callback;
        this.mContext.getContentResolver().registerContentObserver(paymentDefaultUri, true, this.mSettingsObserver, -1);
        this.mContext.getContentResolver().registerContentObserver(paymentForegroundUri, true, this.mSettingsObserver, -1);
        loadDefaultsFromSettings(ActivityManager.getCurrentUser());
    }

    /* loaded from: classes.dex */
    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            int currentUser = ActivityManager.getCurrentUser();
            PreferredServices.this.loadDefaultsFromSettings(currentUser);
        }
    }

    void loadDefaultsFromSettings(int userId) {
        boolean paymentPreferForegroundChanged;
        boolean paymentDefaultChanged = DBG;
        String name = Settings.Secure.getStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", userId);
        ComponentName newDefault = name != null ? ComponentName.unflattenFromString(name) : null;
        boolean preferForeground = DBG;
        boolean z = true;
        try {
            preferForeground = Settings.Secure.getIntForUser(this.mContext.getContentResolver(), "nfc_payment_foreground", userId) != 0;
        } catch (Settings.SettingNotFoundException e) {
        }
        synchronized (this.mLock) {
            if (preferForeground == this.mPaymentDefaults.preferForeground) {
                z = false;
            }
            paymentPreferForegroundChanged = z;
            this.mPaymentDefaults.preferForeground = preferForeground;
            this.mPaymentDefaults.settingsDefault = newDefault;
            if (newDefault != null && !newDefault.equals(this.mPaymentDefaults.currentPreferred)) {
                paymentDefaultChanged = true;
                this.mPaymentDefaults.currentPreferred = newDefault;
            } else if (newDefault == null && this.mPaymentDefaults.currentPreferred != null) {
                paymentDefaultChanged = true;
                this.mPaymentDefaults.currentPreferred = newDefault;
            }
        }
        if (paymentDefaultChanged) {
            this.mCallback.onPreferredPaymentServiceChanged(newDefault);
        }
        if (paymentPreferForegroundChanged) {
            computePreferredForegroundService();
        }
    }

    void computePreferredForegroundService() {
        ComponentName preferredService;
        boolean changed = DBG;
        synchronized (this.mLock) {
            preferredService = this.mNextTapDefault;
            if (preferredService == null) {
                preferredService = this.mForegroundRequested;
            }
            if (preferredService != null && !preferredService.equals(this.mForegroundCurrent)) {
                this.mForegroundCurrent = preferredService;
                changed = true;
            } else if (preferredService == null && this.mForegroundCurrent != null) {
                this.mForegroundCurrent = preferredService;
                changed = true;
            }
        }
        if (changed) {
            this.mCallback.onPreferredForegroundServiceChanged(preferredService);
        }
    }

    public boolean setDefaultForNextTap(ComponentName service) {
        synchronized (this.mLock) {
            this.mNextTapDefault = service;
        }
        computePreferredForegroundService();
        return true;
    }

    public void onServicesUpdated() {
        boolean changed = DBG;
        synchronized (this.mLock) {
            if (this.mForegroundCurrent != null && !isForegroundAllowedLocked(this.mForegroundCurrent)) {
                Log.d(TAG, "Removing foreground preferred service.");
                this.mForegroundRequested = null;
                this.mForegroundUid = -1;
                changed = true;
            }
        }
        if (changed) {
            computePreferredForegroundService();
        }
    }

    boolean isForegroundAllowedLocked(ComponentName service) {
        if (service.equals(this.mPaymentDefaults.currentPreferred)) {
            return true;
        }
        ApduServiceInfo serviceInfo = this.mServiceCache.getService(ActivityManager.getCurrentUser(), service);
        if (serviceInfo == null) {
            Log.d(TAG, "Requested foreground service unexpectedly removed");
            return DBG;
        } else if (this.mPaymentDefaults.preferForeground) {
            return true;
        } else {
            if (serviceInfo.hasCategory("payment")) {
                Log.d(TAG, "User doesn't allow payment services to be overridden.");
                return DBG;
            }
            List<String> otherAids = serviceInfo.getAids();
            ApduServiceInfo paymentServiceInfo = this.mServiceCache.getService(ActivityManager.getCurrentUser(), this.mPaymentDefaults.currentPreferred);
            if (paymentServiceInfo == null || otherAids == null || otherAids.size() <= 0) {
                return true;
            }
            for (String aid : otherAids) {
                RegisteredAidCache.AidResolveInfo resolveInfo = this.mAidCache.resolveAid(aid);
                if ("payment".equals(resolveInfo.category) && paymentServiceInfo.equals(resolveInfo.defaultService)) {
                    return DBG;
                }
            }
            return true;
        }
    }

    public boolean registerPreferredForegroundService(ComponentName service, int callingUid) {
        boolean success = DBG;
        synchronized (this.mLock) {
            if (isForegroundAllowedLocked(service)) {
                if (this.mForegroundUtils.registerUidToBackgroundCallback(this, callingUid)) {
                    this.mForegroundRequested = service;
                    this.mForegroundUid = callingUid;
                    success = true;
                } else {
                    Log.e(TAG, "Calling UID is not in the foreground, ignorning!");
                    success = DBG;
                }
            } else {
                Log.e(TAG, "Requested foreground service conflicts or was removed.");
            }
        }
        if (success) {
            computePreferredForegroundService();
        }
        return success;
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
            computePreferredForegroundService();
        }
        return success;
    }

    public boolean unregisteredPreferredForegroundService(int callingUid) {
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
            this.mClearNextTapDefault = this.mNextTapDefault != null ? true : DBG;
        }
    }

    public void onHostEmulationDeactivated() {
        boolean changed = DBG;
        synchronized (this.mLock) {
            if (this.mClearNextTapDefault) {
                if (this.mNextTapDefault != null) {
                    this.mNextTapDefault = null;
                    changed = true;
                }
                this.mClearNextTapDefault = DBG;
            }
        }
        if (changed) {
            computePreferredForegroundService();
        }
    }

    public void onUserSwitched(int userId) {
        loadDefaultsFromSettings(userId);
    }

    public boolean packageHasPreferredService(String packageName) {
        if (packageName == null) {
            return DBG;
        }
        if (this.mPaymentDefaults.currentPreferred == null || !packageName.equals(this.mPaymentDefaults.currentPreferred.getPackageName())) {
            ComponentName componentName = this.mForegroundCurrent;
            if (componentName == null || !packageName.equals(componentName.getPackageName())) {
                return DBG;
            }
            return true;
        }
        return true;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Preferred services (in order of importance): ");
        pw.println("    *** Current preferred foreground service: " + this.mForegroundCurrent);
        pw.println("    *** Current preferred payment service: " + this.mPaymentDefaults.currentPreferred);
        pw.println("        Next tap default: " + this.mNextTapDefault);
        pw.println("        Default for foreground app (UID: " + this.mForegroundUid + "): " + this.mForegroundRequested);
        StringBuilder sb = new StringBuilder();
        sb.append("        Default in payment settings: ");
        sb.append(this.mPaymentDefaults.settingsDefault);
        pw.println(sb.toString());
        pw.println("        Payment settings allows override: " + this.mPaymentDefaults.preferForeground);
        pw.println("");
    }
}
