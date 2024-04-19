package com.android.nfc.handover;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.OobData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import com.android.nfc.handover.BluetoothPeripheralHandover;
import java.util.Set;
/* loaded from: classes.dex */
public class PeripheralHandoverService extends Service implements BluetoothPeripheralHandover.Callback {
    public static final String BUNDLE_TRANSFER = "transfer";
    static final boolean DBG = true;
    public static final String EXTRA_BT_ENABLED = "bt_enabled";
    public static final String EXTRA_CLIENT = "client";
    public static final String EXTRA_PERIPHERAL_CLASS = "class";
    public static final String EXTRA_PERIPHERAL_DEVICE = "device";
    public static final String EXTRA_PERIPHERAL_NAME = "headsetname";
    public static final String EXTRA_PERIPHERAL_OOB_DATA = "oobdata";
    public static final String EXTRA_PERIPHERAL_TRANSPORT = "transporttype";
    public static final String EXTRA_PERIPHERAL_UUIDS = "uuids";
    public static final int MSG_HEADSET_CONNECTED = 0;
    public static final int MSG_HEADSET_NOT_CONNECTED = 1;
    static final int MSG_PAUSE_POLLING = 0;
    private static final int PAUSE_DELAY_MILLIS = 300;
    private static final int PAUSE_POLLING_TIMEOUT_MS = 35000;
    static final String TAG = "PeripheralHandoverService";
    private static final Object sLock = new Object();
    BluetoothPeripheralHandover mBluetoothPeripheralHandover;
    Messenger mClient;
    BluetoothDevice mDevice;
    NfcAdapter mNfcAdapter;
    final BroadcastReceiver mBluetoothStatusReceiver = new BroadcastReceiver() { // from class: com.android.nfc.handover.PeripheralHandoverService.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                PeripheralHandoverService.this.handleBluetoothStateChanged(intent);
            }
        }
    };
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    Handler mHandler = new MessageHandler();
    final Messenger mMessenger = new Messenger(this.mHandler);
    boolean mBluetoothHeadsetConnected = false;
    boolean mBluetoothEnabledByNfc = false;
    int mStartId = 0;

    /* loaded from: classes.dex */
    class MessageHandler extends Handler {
        MessageHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                PeripheralHandoverService.this.mNfcAdapter.pausePolling(PeripheralHandoverService.PAUSE_POLLING_TIMEOUT_MS);
            }
        }
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (sLock) {
            if (this.mStartId != 0) {
                this.mStartId = startId;
                return 1;
            }
            this.mStartId = startId;
            if (intent == null) {
                Log.e(TAG, "Intent is null, can't do peripheral handover.");
                stopSelf(startId);
                return 2;
            } else if (doPeripheralHandover(intent.getExtras())) {
                return 1;
            } else {
                stopSelf(startId);
                return 2;
            }
        }
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
        IntentFilter filter = new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED");
        registerReceiver(this.mBluetoothStatusReceiver, filter);
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mBluetoothStatusReceiver);
    }

    boolean doPeripheralHandover(Bundle msgData) {
        ParcelUuid[] uuids;
        if (this.mBluetoothPeripheralHandover != null) {
            Log.d(TAG, "Ignoring pairing request, existing handover in progress.");
            return DBG;
        } else if (msgData != null) {
            this.mDevice = (BluetoothDevice) msgData.getParcelable(EXTRA_PERIPHERAL_DEVICE);
            String name = msgData.getString(EXTRA_PERIPHERAL_NAME);
            int transport = msgData.getInt(EXTRA_PERIPHERAL_TRANSPORT);
            OobData oobData = msgData.getParcelable(EXTRA_PERIPHERAL_OOB_DATA);
            Parcelable[] parcelables = msgData.getParcelableArray(EXTRA_PERIPHERAL_UUIDS);
            BluetoothClass btClass = (BluetoothClass) msgData.getParcelable(EXTRA_PERIPHERAL_CLASS);
            if (parcelables == null) {
                uuids = null;
            } else {
                ParcelUuid[] uuids2 = new ParcelUuid[parcelables.length];
                for (int i = 0; i < parcelables.length; i++) {
                    uuids2[i] = (ParcelUuid) parcelables[i];
                }
                uuids = uuids2;
            }
            this.mClient = (Messenger) msgData.getParcelable(EXTRA_CLIENT);
            this.mBluetoothEnabledByNfc = msgData.getBoolean(EXTRA_BT_ENABLED);
            this.mBluetoothPeripheralHandover = new BluetoothPeripheralHandover(this, this.mDevice, name, transport, oobData, uuids, btClass, this);
            if (transport == 2) {
                Handler handler = this.mHandler;
                handler.sendMessageDelayed(handler.obtainMessage(0), 300L);
            }
            if (this.mBluetoothAdapter.isEnabled()) {
                if (!this.mBluetoothPeripheralHandover.start()) {
                    this.mHandler.removeMessages(0);
                    this.mNfcAdapter.resumePolling();
                    return DBG;
                }
                return DBG;
            } else if (!enableBluetooth()) {
                Log.e(TAG, "Error enabling Bluetooth.");
                this.mBluetoothPeripheralHandover = null;
                return false;
            } else {
                return DBG;
            }
        } else {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleBluetoothStateChanged(Intent intent) {
        BluetoothPeripheralHandover bluetoothPeripheralHandover;
        int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
        if (state == 12 && (bluetoothPeripheralHandover = this.mBluetoothPeripheralHandover) != null && !bluetoothPeripheralHandover.hasStarted() && !this.mBluetoothPeripheralHandover.start()) {
            this.mNfcAdapter.resumePolling();
        }
    }

    @Override // com.android.nfc.handover.BluetoothPeripheralHandover.Callback
    public void onBluetoothPeripheralHandoverComplete(boolean connected) {
        int transport = this.mBluetoothPeripheralHandover.mTransport;
        this.mBluetoothPeripheralHandover = null;
        this.mBluetoothHeadsetConnected = connected;
        if (transport == 2 && !connected) {
            if (this.mHandler.hasMessages(0)) {
                this.mHandler.removeMessages(0);
            }
            this.mNfcAdapter.resumePolling();
        }
        disableBluetoothIfNeeded();
        replyToClient(connected);
        synchronized (sLock) {
            stopSelf(this.mStartId);
            this.mStartId = 0;
        }
    }

    boolean enableBluetooth() {
        if (this.mBluetoothAdapter.isEnabled()) {
            return DBG;
        }
        this.mBluetoothEnabledByNfc = DBG;
        return this.mBluetoothAdapter.enableNoAutoConnect();
    }

    void disableBluetoothIfNeeded() {
        if (this.mBluetoothEnabledByNfc && !hasConnectedBluetoothDevices() && !this.mBluetoothHeadsetConnected) {
            this.mBluetoothAdapter.disable();
            this.mBluetoothEnabledByNfc = false;
        }
    }

    boolean hasConnectedBluetoothDevices() {
        Set<BluetoothDevice> bondedDevices = this.mBluetoothAdapter.getBondedDevices();
        if (bondedDevices != null) {
            for (BluetoothDevice device : bondedDevices) {
                if (!device.equals(this.mDevice) && device.isConnected()) {
                    return DBG;
                }
            }
            return false;
        }
        return false;
    }

    void replyToClient(boolean connected) {
        if (this.mClient == null) {
            return;
        }
        int msgId = !connected ? 1 : 0;
        Message msg = Message.obtain((Handler) null, msgId);
        msg.arg1 = this.mBluetoothEnabledByNfc ? 1 : 0;
        try {
            this.mClient.send(msg);
        } catch (RemoteException e) {
        }
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public boolean onUnbind(Intent intent) {
        return false;
    }
}
