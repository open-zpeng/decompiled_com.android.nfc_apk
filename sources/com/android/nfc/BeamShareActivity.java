package com.android.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.webkit.URLUtil;
import java.util.ArrayList;
/* loaded from: classes.dex */
public class BeamShareActivity extends Activity {
    static final boolean DBG = false;
    static final String TAG = "BeamShareActivity";
    Intent mLaunchIntent;
    NdefMessage mNdefMessage;
    NfcAdapter mNfcAdapter;
    final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.nfc.BeamShareActivity.4
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            intent.getAction();
            if ("android.nfc.action.ADAPTER_STATE_CHANGED".equals(intent.getAction())) {
                int state = intent.getIntExtra("android.nfc.extra.ADAPTER_STATE", 1);
                if (state == 3) {
                    BeamShareActivity beamShareActivity = BeamShareActivity.this;
                    beamShareActivity.parseShareIntentAndFinish(beamShareActivity.mLaunchIntent);
                }
            }
        }
    };
    ArrayList<Uri> mUris;

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mUris = new ArrayList<>();
        this.mNdefMessage = null;
        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        this.mLaunchIntent = getIntent();
        NfcAdapter nfcAdapter = this.mNfcAdapter;
        if (nfcAdapter == null) {
            Log.e(TAG, "NFC adapter not present.");
            finish();
        } else if (!nfcAdapter.isEnabled()) {
            showNfcDialogAndExit(R.string.beam_requires_nfc_enabled);
        } else {
            parseShareIntentAndFinish(this.mLaunchIntent);
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        try {
            unregisterReceiver(this.mReceiver);
        } catch (Exception e) {
            Log.w(TAG, e.getMessage());
        }
        super.onDestroy();
    }

    private void showNfcDialogAndExit(int msgId) {
        IntentFilter filter = new IntentFilter("android.nfc.action.ADAPTER_STATE_CHANGED");
        registerReceiverAsUser(this.mReceiver, UserHandle.ALL, filter, null, null);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this, 5);
        dialogBuilder.setMessage(msgId);
        dialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() { // from class: com.android.nfc.BeamShareActivity.1
            @Override // android.content.DialogInterface.OnCancelListener
            public void onCancel(DialogInterface dialogInterface) {
                BeamShareActivity.this.finish();
            }
        });
        dialogBuilder.setPositiveButton(17039379, new DialogInterface.OnClickListener() { // from class: com.android.nfc.BeamShareActivity.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int id) {
                if (!BeamShareActivity.this.mNfcAdapter.isEnabled()) {
                    BeamShareActivity.this.mNfcAdapter.enable();
                    return;
                }
                BeamShareActivity beamShareActivity = BeamShareActivity.this;
                beamShareActivity.parseShareIntentAndFinish(beamShareActivity.mLaunchIntent);
            }
        });
        dialogBuilder.setNegativeButton(17039369, new DialogInterface.OnClickListener() { // from class: com.android.nfc.BeamShareActivity.3
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i) {
                BeamShareActivity.this.finish();
            }
        });
        dialogBuilder.show();
    }

    void tryUri(Uri uri) {
        if (uri.getScheme().equalsIgnoreCase("content") || uri.getScheme().equalsIgnoreCase("file")) {
            this.mUris.add(uri);
        } else {
            this.mNdefMessage = new NdefMessage(NdefRecord.createUri(uri), new NdefRecord[0]);
        }
    }

    void tryText(String text) {
        if (URLUtil.isValidUrl(text)) {
            Uri parsedUri = Uri.parse(text);
            tryUri(parsedUri);
            return;
        }
        this.mNdefMessage = new NdefMessage(NdefRecord.createTextRecord(null, text), new NdefRecord[0]);
    }

    /* JADX WARN: Code restructure failed: missing block: B:58:0x0119, code lost:
        android.widget.Toast.makeText(getApplicationContext(), (int) com.android.nfc.R.string.beam_requires_external_storage_permission, 0).show();
        android.util.Log.e(com.android.nfc.BeamShareActivity.TAG, "File based Uri doesn't have External Storage Permission.");
        android.util.EventLog.writeEvent(1397638484, "37287958", java.lang.Integer.valueOf(r0), r12.getPath());
     */
    /* JADX WARN: Removed duplicated region for block: B:72:0x0169  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void parseShareIntentAndFinish(android.content.Intent r17) {
        /*
            Method dump skipped, instructions count: 410
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.BeamShareActivity.parseShareIntentAndFinish(android.content.Intent):void");
    }
}
