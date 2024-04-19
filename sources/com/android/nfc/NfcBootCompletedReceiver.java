package com.android.nfc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
/* loaded from: classes.dex */
public class NfcBootCompletedReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
            PackageManager pm = context.getPackageManager();
            if (!pm.hasSystemFeature("android.hardware.nfc.any")) {
                pm.setApplicationEnabledSetting(context.getPackageName(), 2, 0);
            }
        }
    }
}
