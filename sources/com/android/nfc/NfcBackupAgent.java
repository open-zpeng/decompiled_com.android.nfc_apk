package com.android.nfc;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
/* loaded from: classes.dex */
public class NfcBackupAgent extends BackupAgentHelper {
    static final String SHARED_PREFS_BACKUP_KEY = "shared_prefs";

    @Override // android.app.backup.BackupAgent
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, NfcService.PREF);
        addHelper(SHARED_PREFS_BACKUP_KEY, helper);
    }

    @Override // android.app.backup.BackupAgent
    public void onRestoreFinished() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            SharedPreferences prefs = getSharedPreferences(NfcService.PREF, 4);
            if (prefs.getBoolean("ndef_push_on", false)) {
                nfcAdapter.enableNdefPush();
            } else {
                nfcAdapter.disableNdefPush();
            }
            if (prefs.getBoolean("nfc_on", true)) {
                nfcAdapter.enable();
            } else {
                nfcAdapter.disable();
            }
        }
    }
}
