package com.android.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;
/* loaded from: classes.dex */
public class ConfirmConnectToWifiNetworkActivity extends Activity implements View.OnClickListener, DialogInterface.OnDismissListener {
    public static final int ENABLE_WIFI_TIMEOUT_MILLIS = 5000;
    private AlertDialog mAlertDialog;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.nfc.ConfirmConnectToWifiNetworkActivity.3
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                int wifiState = intent.getIntExtra("wifi_state", 0);
                if (ConfirmConnectToWifiNetworkActivity.this.mCurrentWifiConfiguration != null && wifiState == 3 && ConfirmConnectToWifiNetworkActivity.this.getAndClearEnableWifiInProgress()) {
                    ConfirmConnectToWifiNetworkActivity confirmConnectToWifiNetworkActivity = ConfirmConnectToWifiNetworkActivity.this;
                    confirmConnectToWifiNetworkActivity.doConnect((WifiManager) confirmConnectToWifiNetworkActivity.getSystemService("wifi"));
                }
            }
        }
    };
    private WifiConfiguration mCurrentWifiConfiguration;
    private boolean mEnableWifiInProgress;
    private Handler mHandler;

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        this.mCurrentWifiConfiguration = (WifiConfiguration) intent.getParcelableExtra(NfcWifiProtectedSetup.EXTRA_WIFI_CONFIG);
        String printableSsid = this.mCurrentWifiConfiguration.getPrintableSsid();
        this.mAlertDialog = new AlertDialog.Builder(this, 5).setTitle(R.string.title_connect_to_network).setMessage(String.format(getResources().getString(R.string.prompt_connect_to_network), printableSsid)).setOnDismissListener(this).setNegativeButton(17039360, (DialogInterface.OnClickListener) null).setPositiveButton(R.string.wifi_connect, (DialogInterface.OnClickListener) null).create();
        this.mEnableWifiInProgress = false;
        this.mHandler = new Handler();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        registerReceiver(this.mBroadcastReceiver, intentFilter);
        this.mAlertDialog.show();
        super.onCreate(savedInstanceState);
        this.mAlertDialog.getButton(-1).setOnClickListener(this);
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View v) {
        WifiManager wifiManager = (WifiManager) getSystemService("wifi");
        if (!isChangeWifiStateGranted()) {
            showFailToast();
        } else if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            this.mEnableWifiInProgress = true;
            this.mHandler.postDelayed(new Runnable() { // from class: com.android.nfc.ConfirmConnectToWifiNetworkActivity.1
                @Override // java.lang.Runnable
                public void run() {
                    if (ConfirmConnectToWifiNetworkActivity.this.getAndClearEnableWifiInProgress()) {
                        ConfirmConnectToWifiNetworkActivity.this.showFailToast();
                        ConfirmConnectToWifiNetworkActivity.this.finish();
                    }
                }
            }, 5000L);
        } else {
            doConnect(wifiManager);
        }
        this.mAlertDialog.dismiss();
    }

    private boolean isChangeWifiStateGranted() {
        AppOpsManager appOps = (AppOpsManager) getSystemService("appops");
        int modeChangeWifiState = appOps.checkOpNoThrow(71, Binder.getCallingUid(), getPackageName());
        return modeChangeWifiState == 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doConnect(WifiManager wifiManager) {
        WifiConfiguration wifiConfiguration = this.mCurrentWifiConfiguration;
        wifiConfiguration.hiddenSSID = true;
        int networkId = wifiManager.addNetwork(wifiConfiguration);
        if (networkId < 0) {
            showFailToast();
        } else {
            wifiManager.connect(networkId, new WifiManager.ActionListener() { // from class: com.android.nfc.ConfirmConnectToWifiNetworkActivity.2
                public void onSuccess() {
                    Toast.makeText(ConfirmConnectToWifiNetworkActivity.this, (int) R.string.status_wifi_connected, 0).show();
                }

                public void onFailure(int reason) {
                    ConfirmConnectToWifiNetworkActivity.this.showFailToast();
                }
            });
        }
        finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFailToast() {
        Toast.makeText(this, (int) R.string.status_unable_to_connect, 0).show();
    }

    @Override // android.content.DialogInterface.OnDismissListener
    public void onDismiss(DialogInterface dialog) {
        if (!this.mEnableWifiInProgress && !isChangingConfigurations()) {
            finish();
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.mAlertDialog.dismiss();
        unregisterReceiver(this.mBroadcastReceiver);
        super.onDestroy();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean getAndClearEnableWifiInProgress() {
        boolean enableWifiInProgress;
        synchronized (this) {
            enableWifiInProgress = this.mEnableWifiInProgress;
            this.mEnableWifiInProgress = false;
        }
        return enableWifiInProgress;
    }
}
