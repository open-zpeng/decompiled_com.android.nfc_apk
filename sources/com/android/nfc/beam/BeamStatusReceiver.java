package com.android.nfc.beam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;
import com.android.nfc.R;
import java.io.File;
/* loaded from: classes.dex */
public class BeamStatusReceiver extends BroadcastReceiver {
    public static final String ACTION_CANCEL_HANDOVER_TRANSFER = "com.android.nfc.handover.action.CANCEL_HANDOVER_TRANSFER";
    private static final String ACTION_HANDOVER_STARTED = "android.nfc.handover.intent.action.HANDOVER_STARTED";
    private static final String ACTION_STOP_BLUETOOTH_TRANSFER = "android.btopp.intent.action.STOP_HANDOVER_TRANSFER";
    private static final String ACTION_TRANSFER_DONE = "android.nfc.handover.intent.action.TRANSFER_DONE";
    private static final String ACTION_TRANSFER_PROGRESS = "android.nfc.handover.intent.action.TRANSFER_PROGRESS";
    public static final String BEAM_STATUS_PERMISSION = "android.permission.NFC_HANDOVER_STATUS";
    private static final boolean DBG = true;
    public static final int DIRECTION_INCOMING = 0;
    public static final int DIRECTION_OUTGOING = 1;
    public static final String EXTRA_ADDRESS = "android.nfc.handover.intent.extra.ADDRESS";
    private static final String EXTRA_HANDOVER_DATA_LINK_TYPE = "android.nfc.handover.intent.extra.HANDOVER_DATA_LINK_TYPE";
    public static final String EXTRA_INCOMING = "com.android.nfc.handover.extra.INCOMING";
    private static final String EXTRA_OBJECT_COUNT = "android.nfc.handover.intent.extra.OBJECT_COUNT";
    public static final String EXTRA_TRANSFER_ID = "android.nfc.handover.intent.extra.TRANSFER_ID";
    private static final String EXTRA_TRANSFER_MIMETYPE = "android.nfc.handover.intent.extra.TRANSFER_MIME_TYPE";
    private static final String EXTRA_TRANSFER_PROGRESS = "android.nfc.handover.intent.extra.TRANSFER_PROGRESS";
    private static final String EXTRA_TRANSFER_STATUS = "android.nfc.handover.intent.extra.TRANSFER_STATUS";
    private static final String EXTRA_TRANSFER_URI = "android.nfc.handover.intent.extra.TRANSFER_URI";
    private static final int HANDOVER_TRANSFER_STATUS_FAILURE = 1;
    private static final int HANDOVER_TRANSFER_STATUS_SUCCESS = 0;
    private static final String TAG = "BeamStatusReceiver";
    private final Context mContext;
    private final BeamTransferManager mTransferManager;

    /* JADX INFO: Access modifiers changed from: package-private */
    public BeamStatusReceiver(Context context, BeamTransferManager transferManager) {
        this.mContext = context;
        this.mTransferManager = transferManager;
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int dataLinkType = intent.getIntExtra(EXTRA_HANDOVER_DATA_LINK_TYPE, 1);
        if (ACTION_CANCEL_HANDOVER_TRANSFER.equals(action)) {
            BeamTransferManager beamTransferManager = this.mTransferManager;
            if (beamTransferManager != null) {
                beamTransferManager.cancel();
            }
        } else if (ACTION_TRANSFER_PROGRESS.equals(action) || ACTION_TRANSFER_DONE.equals(action) || ACTION_HANDOVER_STARTED.equals(action)) {
            handleTransferEvent(intent, dataLinkType);
        }
    }

    public IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter(ACTION_TRANSFER_DONE);
        filter.addAction(ACTION_TRANSFER_PROGRESS);
        filter.addAction(ACTION_CANCEL_HANDOVER_TRANSFER);
        filter.addAction(ACTION_HANDOVER_STARTED);
        return filter;
    }

    private void handleTransferEvent(Intent intent, int deviceType) {
        int count;
        String action = intent.getAction();
        int id = intent.getIntExtra(EXTRA_TRANSFER_ID, -1);
        String sourceAddress = intent.getStringExtra(EXTRA_ADDRESS);
        if (sourceAddress == null) {
            return;
        }
        BeamTransferManager beamTransferManager = this.mTransferManager;
        if (beamTransferManager == null) {
            if (id != -1 && deviceType == 1) {
                Log.d(TAG, "Didn't find transfer, stopping");
                Intent cancelIntent = new Intent(ACTION_STOP_BLUETOOTH_TRANSFER);
                cancelIntent.putExtra(EXTRA_TRANSFER_ID, id);
                cancelIntent.setPackage(this.mContext.getString(R.string.bluetooth_package));
                this.mContext.sendBroadcast(cancelIntent);
                return;
            }
            return;
        }
        beamTransferManager.setBluetoothTransferId(id);
        if (!action.equals(ACTION_TRANSFER_DONE)) {
            if (action.equals(ACTION_TRANSFER_PROGRESS)) {
                float progress = intent.getFloatExtra(EXTRA_TRANSFER_PROGRESS, 0.0f);
                this.mTransferManager.updateFileProgress(progress);
                return;
            } else if (action.equals(ACTION_HANDOVER_STARTED) && (count = intent.getIntExtra(EXTRA_OBJECT_COUNT, 0)) > 0) {
                this.mTransferManager.setObjectCount(count);
                return;
            } else {
                return;
            }
        }
        int handoverStatus = intent.getIntExtra(EXTRA_TRANSFER_STATUS, 1);
        if (handoverStatus == 0) {
            String uriString = intent.getStringExtra(EXTRA_TRANSFER_URI);
            String mimeType = intent.getStringExtra(EXTRA_TRANSFER_MIMETYPE);
            Uri uri = Uri.parse(uriString);
            if (uri != null && uri.getScheme() == null) {
                uri = Uri.fromFile(new File(uri.getPath()));
            }
            this.mTransferManager.finishTransfer(DBG, uri, mimeType);
            return;
        }
        this.mTransferManager.finishTransfer(false, null, null);
    }
}
