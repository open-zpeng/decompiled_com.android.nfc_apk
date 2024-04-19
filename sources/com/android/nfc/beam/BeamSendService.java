package com.android.nfc.beam;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import com.android.nfc.beam.BeamTransferManager;
/* loaded from: classes.dex */
public class BeamSendService extends Service implements BeamTransferManager.Callback {
    public static final String EXTRA_BEAM_COMPLETE_CALLBACK = "com.android.nfc.beam.TRANSFER_COMPLETE_CALLBACK";
    private BeamStatusReceiver mBeamStatusReceiver;
    private boolean mBluetoothEnabledByNfc;
    private Messenger mCompleteCallback;
    private int mStartId;
    private BeamTransferManager mTransferManager;
    private static String TAG = "BeamSendService";
    private static boolean DBG = true;
    public static String EXTRA_BEAM_TRANSFER_RECORD = BeamReceiveService.EXTRA_BEAM_TRANSFER_RECORD;
    private final BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() { // from class: com.android.nfc.beam.BeamSendService.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                BeamSendService.this.handleBluetoothStateChanged(intent);
            }
        }
    };
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED");
        registerReceiver(this.mBluetoothStateReceiver, filter);
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        BeamStatusReceiver beamStatusReceiver = this.mBeamStatusReceiver;
        if (beamStatusReceiver != null) {
            unregisterReceiver(beamStatusReceiver);
        }
        unregisterReceiver(this.mBluetoothStateReceiver);
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        BeamTransferRecord transferRecord;
        this.mStartId = startId;
        if (intent == null || (transferRecord = (BeamTransferRecord) intent.getParcelableExtra(EXTRA_BEAM_TRANSFER_RECORD)) == null) {
            if (DBG) {
                Log.e(TAG, "No transfer record provided. Stopping.");
            }
            stopSelf(startId);
            return 2;
        }
        this.mCompleteCallback = (Messenger) intent.getParcelableExtra("com.android.nfc.beam.TRANSFER_COMPLETE_CALLBACK");
        if (doTransfer(transferRecord)) {
            if (DBG) {
                Log.i(TAG, "Starting outgoing Beam transfer");
                return 1;
            }
            return 1;
        }
        invokeCompleteCallback(false);
        stopSelf(startId);
        return 2;
    }

    boolean doTransfer(BeamTransferRecord transferRecord) {
        if (createBeamTransferManager(transferRecord)) {
            this.mBeamStatusReceiver = new BeamStatusReceiver(this, this.mTransferManager);
            BeamStatusReceiver beamStatusReceiver = this.mBeamStatusReceiver;
            registerReceiver(beamStatusReceiver, beamStatusReceiver.getIntentFilter(), BeamStatusReceiver.BEAM_STATUS_PERMISSION, new Handler());
            if (transferRecord.dataLinkType == BeamTransferRecord.DATA_LINK_TYPE_BLUETOOTH) {
                if (this.mBluetoothAdapter.isEnabled()) {
                    this.mTransferManager.start();
                } else if (!this.mBluetoothAdapter.enableNoAutoConnect()) {
                    Log.e(TAG, "Error enabling Bluetooth.");
                    this.mTransferManager = null;
                    return false;
                } else {
                    this.mBluetoothEnabledByNfc = true;
                    if (DBG) {
                        String str = TAG;
                        Log.d(str, "Queueing out transfer " + Integer.toString(transferRecord.id));
                    }
                }
            }
            return true;
        }
        return false;
    }

    boolean createBeamTransferManager(BeamTransferRecord transferRecord) {
        if (this.mTransferManager == null && transferRecord.dataLinkType == BeamTransferRecord.DATA_LINK_TYPE_BLUETOOTH) {
            this.mTransferManager = new BeamTransferManager(this, this, transferRecord, false);
            this.mTransferManager.updateNotification();
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleBluetoothStateChanged(Intent intent) {
        BeamTransferManager beamTransferManager;
        int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
        if (state == 12 && (beamTransferManager = this.mTransferManager) != null && beamTransferManager.mDataLinkType == BeamTransferRecord.DATA_LINK_TYPE_BLUETOOTH) {
            this.mTransferManager.start();
        }
    }

    private void invokeCompleteCallback(boolean success) {
        if (this.mCompleteCallback != null) {
            try {
                Message msg = Message.obtain((Handler) null, 0);
                msg.arg1 = success ? 1 : 0;
                this.mCompleteCallback.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to invoke Beam complete callback", e);
            }
        }
    }

    @Override // com.android.nfc.beam.BeamTransferManager.Callback
    public void onTransferComplete(BeamTransferManager transfer, boolean success) {
        if (!success && DBG) {
            String str = TAG;
            Log.d(str, "Transfer failed, final state: " + Integer.toString(transfer.mState));
        }
        if (this.mBluetoothEnabledByNfc) {
            this.mBluetoothEnabledByNfc = false;
            this.mBluetoothAdapter.disable();
        }
        invokeCompleteCallback(success);
        stopSelf(this.mStartId);
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }
}
