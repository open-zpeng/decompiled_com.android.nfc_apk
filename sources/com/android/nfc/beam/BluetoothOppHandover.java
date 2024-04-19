package com.android.nfc.beam;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import com.android.nfc.R;
import java.util.ArrayList;
import java.util.Iterator;
/* loaded from: classes.dex */
public class BluetoothOppHandover implements Handler.Callback {
    static final String ACTION_HANDOVER_SEND = "android.nfc.handover.intent.action.HANDOVER_SEND";
    static final String ACTION_HANDOVER_SEND_MULTIPLE = "android.nfc.handover.intent.action.HANDOVER_SEND_MULTIPLE";
    static final boolean DBG = true;
    static final int MSG_START_SEND = 0;
    static final int REMOTE_BT_ENABLE_DELAY_MS = 5000;
    static final int STATE_COMPLETE = 3;
    static final int STATE_INIT = 0;
    static final int STATE_TURNING_ON = 1;
    static final int STATE_WAITING = 2;
    static final String TAG = "BluetoothOppHandover";
    final Context mContext;
    final BluetoothDevice mDevice;
    final Handler mHandler;
    final boolean mRemoteActivating;
    final ArrayList<Uri> mUris;
    final Long mCreateTime = Long.valueOf(SystemClock.elapsedRealtime());
    int mState = 0;

    public BluetoothOppHandover(Context context, BluetoothDevice device, ArrayList<Uri> uris, boolean remoteActivating) {
        this.mContext = context;
        this.mDevice = device;
        this.mUris = uris;
        this.mRemoteActivating = remoteActivating;
        this.mHandler = new Handler(context.getMainLooper(), this);
    }

    public void start() {
        if (this.mRemoteActivating) {
            Long timeElapsed = Long.valueOf(SystemClock.elapsedRealtime() - this.mCreateTime.longValue());
            if (timeElapsed.longValue() < 5000) {
                this.mHandler.sendEmptyMessageDelayed(0, 5000 - timeElapsed.longValue());
                return;
            } else {
                sendIntent();
                return;
            }
        }
        sendIntent();
    }

    void complete() {
        this.mState = 3;
    }

    void sendIntent() {
        Intent intent = new Intent();
        intent.setPackage(this.mContext.getString(R.string.bluetooth_package));
        String mimeType = MimeTypeUtil.getMimeTypeForUri(this.mContext, this.mUris.get(0));
        intent.setType(mimeType);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
        Iterator<Uri> it = this.mUris.iterator();
        while (it.hasNext()) {
            Uri uri = it.next();
            try {
                this.mContext.grantUriPermission(this.mContext.getString(R.string.bluetooth_package), uri, 1);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to transfer permission to Bluetooth process.");
            }
        }
        if (this.mUris.size() == 1) {
            intent.setAction(ACTION_HANDOVER_SEND);
            intent.putExtra("android.intent.extra.STREAM", this.mUris.get(0));
        } else {
            intent.setAction(ACTION_HANDOVER_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra("android.intent.extra.STREAM", this.mUris);
        }
        Log.d(TAG, "Handing off outging transfer to BT");
        intent.addFlags(268435456);
        this.mContext.sendBroadcast(intent);
        complete();
    }

    @Override // android.os.Handler.Callback
    public boolean handleMessage(Message msg) {
        if (msg.what == 0) {
            sendIntent();
            return DBG;
        }
        return false;
    }
}
