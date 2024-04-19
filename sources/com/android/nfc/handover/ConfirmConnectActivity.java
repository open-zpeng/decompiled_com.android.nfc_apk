package com.android.nfc.handover;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import com.android.nfc.R;
/* loaded from: classes.dex */
public class ConfirmConnectActivity extends Activity {
    BluetoothDevice mDevice;
    AlertDialog mAlert = null;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.nfc.handover.ConfirmConnectActivity.3
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("com.android.nfc.handover.action.TIMEOUT_CONNECT".equals(intent.getAction())) {
                ConfirmConnectActivity.this.finish();
            }
        }
    };

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addSystemFlags(524288);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, 5);
        Intent launchIntent = getIntent();
        this.mDevice = (BluetoothDevice) launchIntent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
        if (this.mDevice == null) {
            finish();
        }
        Resources res = getResources();
        String btExtraName = launchIntent.getStringExtra("android.bluetooth.device.extra.NAME");
        String string = res.getString(R.string.confirm_pairing);
        String confirmString = String.format(string, "\"" + btExtraName.replaceAll("\\r|\\n", "") + "\"");
        builder.setMessage(confirmString).setCancelable(false).setPositiveButton(res.getString(R.string.pair_yes), new DialogInterface.OnClickListener() { // from class: com.android.nfc.handover.ConfirmConnectActivity.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int id) {
                Intent allowIntent = new Intent("com.android.nfc.handover.action.ALLOW_CONNECT");
                allowIntent.putExtra("android.bluetooth.device.extra.DEVICE", ConfirmConnectActivity.this.mDevice);
                allowIntent.setPackage("com.android.nfc");
                ConfirmConnectActivity.this.sendBroadcast(allowIntent);
                ConfirmConnectActivity confirmConnectActivity = ConfirmConnectActivity.this;
                confirmConnectActivity.mAlert = null;
                confirmConnectActivity.finish();
            }
        }).setNegativeButton(res.getString(R.string.pair_no), new DialogInterface.OnClickListener() { // from class: com.android.nfc.handover.ConfirmConnectActivity.1
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int id) {
                Intent denyIntent = new Intent("com.android.nfc.handover.action.DENY_CONNECT");
                denyIntent.putExtra("android.bluetooth.device.extra.DEVICE", ConfirmConnectActivity.this.mDevice);
                denyIntent.setPackage("com.android.nfc");
                ConfirmConnectActivity.this.sendBroadcast(denyIntent);
                ConfirmConnectActivity confirmConnectActivity = ConfirmConnectActivity.this;
                confirmConnectActivity.mAlert = null;
                confirmConnectActivity.finish();
            }
        });
        this.mAlert = builder.create();
        this.mAlert.show();
        registerReceiver(this.mReceiver, new IntentFilter("com.android.nfc.handover.action.TIMEOUT_CONNECT"));
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        unregisterReceiver(this.mReceiver);
        AlertDialog alertDialog = this.mAlert;
        if (alertDialog != null) {
            alertDialog.dismiss();
            Intent denyIntent = new Intent("com.android.nfc.handover.action.DENY_CONNECT");
            denyIntent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
            denyIntent.setPackage("com.android.nfc");
            sendBroadcast(denyIntent);
            this.mAlert = null;
        }
        super.onDestroy();
    }
}
