package com.android.nfc.beam;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.UserHandle;
import com.android.nfc.NfcService;
import com.android.nfc.R;
import com.android.nfc.handover.HandoverDataParser;
/* loaded from: classes.dex */
public class BeamManager implements Handler.Callback {
    private static final String ACTION_WHITELIST_DEVICE = "android.btopp.intent.action.WHITELIST_DEVICE";
    private static final boolean DBG = false;
    public static final int MSG_BEAM_COMPLETE = 0;
    private static final String TAG = "BeamManager";
    private boolean mBeamInProgress;
    private final Handler mCallback;
    private final Object mLock;
    private NfcService mNfcService;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static final class Singleton {
        public static final BeamManager INSTANCE = new BeamManager();

        private Singleton() {
        }
    }

    private BeamManager() {
        this.mLock = new Object();
        this.mBeamInProgress = DBG;
        this.mCallback = new Handler(Looper.getMainLooper(), this);
        this.mNfcService = NfcService.getInstance();
    }

    public static BeamManager getInstance() {
        return Singleton.INSTANCE;
    }

    public boolean isBeamInProgress() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mBeamInProgress;
        }
        return z;
    }

    public boolean startBeamReceive(Context context, HandoverDataParser.BluetoothHandoverData handoverData) {
        synchronized (this.mLock) {
            if (this.mBeamInProgress) {
                return DBG;
            }
            this.mBeamInProgress = true;
            BeamTransferRecord transferRecord = BeamTransferRecord.forBluetoothDevice(handoverData.device, handoverData.carrierActivating, null);
            Intent receiveIntent = new Intent(context.getApplicationContext(), BeamReceiveService.class);
            receiveIntent.putExtra(BeamReceiveService.EXTRA_BEAM_TRANSFER_RECORD, transferRecord);
            receiveIntent.putExtra("com.android.nfc.beam.TRANSFER_COMPLETE_CALLBACK", new Messenger(this.mCallback));
            whitelistOppDevice(context, handoverData.device);
            context.startServiceAsUser(receiveIntent, UserHandle.CURRENT);
            return true;
        }
    }

    public boolean startBeamSend(Context context, HandoverDataParser.BluetoothHandoverData outgoingHandoverData, Uri[] uris, UserHandle userHandle) {
        synchronized (this.mLock) {
            if (this.mBeamInProgress) {
                return DBG;
            }
            this.mBeamInProgress = true;
            BeamTransferRecord transferRecord = BeamTransferRecord.forBluetoothDevice(outgoingHandoverData.device, outgoingHandoverData.carrierActivating, uris);
            Intent sendIntent = new Intent(context.getApplicationContext(), BeamSendService.class);
            sendIntent.putExtra(BeamSendService.EXTRA_BEAM_TRANSFER_RECORD, transferRecord);
            sendIntent.putExtra("com.android.nfc.beam.TRANSFER_COMPLETE_CALLBACK", new Messenger(this.mCallback));
            context.startServiceAsUser(sendIntent, userHandle);
            return true;
        }
    }

    @Override // android.os.Handler.Callback
    public boolean handleMessage(Message msg) {
        int i = msg.what;
        boolean z = DBG;
        if (i == 0) {
            synchronized (this.mLock) {
                this.mBeamInProgress = DBG;
            }
            if (msg.arg1 == 1) {
                z = true;
            }
            boolean success = z;
            if (success) {
                this.mNfcService.playSound(1);
            }
            return true;
        }
        return DBG;
    }

    void whitelistOppDevice(Context context, BluetoothDevice device) {
        Intent intent = new Intent(ACTION_WHITELIST_DEVICE);
        intent.setPackage(context.getString(R.string.bluetooth_package));
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.addFlags(268435456);
        context.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }
}
