package com.android.nfc.cardemulation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.nfc.R;
/* loaded from: classes.dex */
public class TapAgainDialog extends AlertActivity implements DialogInterface.OnClickListener {
    public static final String ACTION_CLOSE = "com.android.nfc.cardemulation.action.CLOSE_TAP_DIALOG";
    public static final String EXTRA_APDU_SERVICE = "apdu_service";
    public static final String EXTRA_CATEGORY = "category";
    private CardEmulation mCardEmuManager;
    private boolean mClosedOnRequest = false;
    final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.nfc.cardemulation.TapAgainDialog.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            TapAgainDialog.this.mClosedOnRequest = true;
            TapAgainDialog.this.finish();
        }
    };

    /* JADX WARN: Multi-variable type inference failed */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(16974546);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        this.mCardEmuManager = CardEmulation.getInstance(adapter);
        Intent intent = getIntent();
        String category = intent.getStringExtra("category");
        ApduServiceInfo serviceInfo = intent.getParcelableExtra(EXTRA_APDU_SERVICE);
        IntentFilter filter = new IntentFilter(ACTION_CLOSE);
        filter.addAction("android.intent.action.SCREEN_OFF");
        registerReceiver(this.mReceiver, filter);
        AlertController.AlertParams ap = this.mAlertParams;
        ap.mTitle = "";
        ap.mView = getLayoutInflater().inflate(R.layout.tapagain, (ViewGroup) null);
        PackageManager pm = getPackageManager();
        TextView tv = (TextView) ap.mView.findViewById(R.id.textview);
        String description = serviceInfo.getDescription();
        if (description == null) {
            CharSequence label = serviceInfo.loadLabel(pm);
            if (label == null) {
                finish();
            } else {
                description = label.toString();
            }
        }
        if ("payment".equals(category)) {
            String formatString = getString(R.string.tap_again_to_pay);
            tv.setText(String.format(formatString, description));
        } else {
            String formatString2 = getString(R.string.tap_again_to_complete);
            tv.setText(String.format(formatString2, description));
        }
        ap.mNegativeButtonText = getString(17039360);
        setupAlert();
        Window window = getWindow();
        window.addFlags(4194304);
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    protected void onStop() {
        super.onStop();
        if (!this.mClosedOnRequest) {
            this.mCardEmuManager.setDefaultForNextTap(null);
        }
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        finish();
    }
}
