package com.android.nfc.beam;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import com.android.nfc.beam.BeamTransferManager;
/* loaded from: classes.dex */
public class BeamReceiveService extends Service implements BeamTransferManager.Callback {
    public static final String EXTRA_BEAM_COMPLETE_CALLBACK = "com.android.nfc.beam.TRANSFER_COMPLETE_CALLBACK";
    public static final String EXTRA_BEAM_TRANSFER_RECORD = "com.android.nfc.beam.EXTRA_BEAM_TRANSFER_RECORD";
    private BeamStatusReceiver mBeamStatusReceiver;
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private boolean mBluetoothEnabledByNfc;
    private Messenger mCompleteCallback;
    private int mStartId;
    private BeamTransferManager mTransferManager;
    private static String TAG = "BeamReceiveService";
    private static boolean DBG = true;

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
        if (prepareToReceive(transferRecord)) {
            if (DBG) {
                Log.i(TAG, "Ready for incoming Beam transfer");
                return 1;
            }
            return 1;
        }
        invokeCompleteCallback(false);
        stopSelf(startId);
        return 2;
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        BeamStatusReceiver beamStatusReceiver = this.mBeamStatusReceiver;
        if (beamStatusReceiver != null) {
            unregisterReceiver(beamStatusReceiver);
        }
    }

    boolean prepareToReceive(BeamTransferRecord transferRecord) {
        if (this.mTransferManager == null && transferRecord.dataLinkType == BeamTransferRecord.DATA_LINK_TYPE_BLUETOOTH) {
            if (!this.mBluetoothAdapter.isEnabled()) {
                if (!this.mBluetoothAdapter.enableNoAutoConnect()) {
                    Log.e(TAG, "Error enabling Bluetooth.");
                    return false;
                }
                this.mBluetoothEnabledByNfc = true;
                if (DBG) {
                    String str = TAG;
                    Log.d(str, "Queueing out transfer " + Integer.toString(transferRecord.id));
                }
            }
            this.mTransferManager = new BeamTransferManager(this, this, transferRecord, true);
            this.mBeamStatusReceiver = new BeamStatusReceiver(this, this.mTransferManager);
            BeamStatusReceiver beamStatusReceiver = this.mBeamStatusReceiver;
            registerReceiver(beamStatusReceiver, beamStatusReceiver.getIntentFilter(), BeamStatusReceiver.BEAM_STATUS_PERMISSION, new Handler());
            this.mTransferManager.start();
            this.mTransferManager.updateNotification();
            return true;
        }
        return false;
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
