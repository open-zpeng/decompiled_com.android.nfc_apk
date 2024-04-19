package com.android.nfc;

import android.app.ActivityManager;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.view.ThreadedRenderer;
import java.util.Iterator;
import java.util.List;
/* loaded from: classes.dex */
public class NfcApplication extends Application {
    static final String NFC_PROCESS = "com.android.nfc";
    static final String TAG = "NfcApplication";
    NfcService mNfcService;

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        PackageManager pm = getApplicationContext().getPackageManager();
        if (!pm.hasSystemFeature("android.hardware.nfc.any")) {
            return;
        }
        boolean isMainProcess = false;
        ActivityManager am = (ActivityManager) getSystemService("activity");
        List processes = am.getRunningAppProcesses();
        Iterator i = processes.iterator();
        while (true) {
            if (!i.hasNext()) {
                break;
            }
            ActivityManager.RunningAppProcessInfo appInfo = i.next();
            if (appInfo.pid == Process.myPid()) {
                isMainProcess = NFC_PROCESS.equals(appInfo.processName);
                break;
            }
        }
        if (UserHandle.myUserId() == 0 && isMainProcess) {
            this.mNfcService = new NfcService(this);
            ThreadedRenderer.enableForegroundTrimming();
        }
    }
}
