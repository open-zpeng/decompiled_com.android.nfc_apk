package com.android.nfc.handover;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.OobData;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import com.android.nfc.R;
/* loaded from: classes.dex */
public class BluetoothPeripheralHandover implements BluetoothProfile.ServiceListener {
    static final String ACTION_ALLOW_CONNECT = "com.android.nfc.handover.action.ALLOW_CONNECT";
    static final int ACTION_CONNECT = 2;
    static final String ACTION_DENY_CONNECT = "com.android.nfc.handover.action.DENY_CONNECT";
    static final int ACTION_DISCONNECT = 1;
    static final int ACTION_INIT = 0;
    static final String ACTION_TIMEOUT_CONNECT = "com.android.nfc.handover.action.TIMEOUT_CONNECT";
    static final boolean DBG = false;
    static final int MAX_RETRY_COUNT = 3;
    static final int MSG_NEXT_STEP = 2;
    static final int MSG_RETRY = 3;
    static final int MSG_TIMEOUT = 1;
    static final int RESULT_CONNECTED = 1;
    static final int RESULT_DISCONNECTED = 2;
    static final int RESULT_PENDING = 0;
    static final int RETRY_CONNECT_WAIT_TIME_MS = 5000;
    static final int RETRY_PAIRING_WAIT_TIME_MS = 2000;
    static final int STATE_BONDING = 4;
    static final int STATE_COMPLETE = 7;
    static final int STATE_CONNECTING = 5;
    static final int STATE_DISCONNECTING = 6;
    static final int STATE_INIT = 0;
    static final int STATE_INIT_COMPLETE = 2;
    static final int STATE_WAITING_FOR_BOND_CONFIRMATION = 3;
    static final int STATE_WAITING_FOR_PROXIES = 1;
    static final String TAG = "BluetoothPeripheralHandover";
    static final int TIMEOUT_MS = 20000;
    BluetoothA2dp mA2dp;
    int mA2dpResult;
    int mAction;
    final AudioManager mAudioManager;
    final BluetoothAdapter mBluetoothAdapter;
    final Callback mCallback;
    final Context mContext;
    final BluetoothDevice mDevice;
    BluetoothHeadset mHeadset;
    int mHfpResult;
    int mHidResult;
    BluetoothHidHost mInput;
    boolean mIsA2dpAvailable;
    boolean mIsHeadsetAvailable;
    boolean mIsMusicActive;
    final String mName;
    OobData mOobData;
    final boolean mProvisioning;
    int mRetryCount;
    int mState;
    final int mTransport;
    final Object mLock = new Object();
    final Handler mHandler = new Handler() { // from class: com.android.nfc.handover.BluetoothPeripheralHandover.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i != 1) {
                if (i == 2) {
                    BluetoothPeripheralHandover.this.nextStep();
                } else if (i == 3) {
                    BluetoothPeripheralHandover.this.mHandler.removeMessages(3);
                    if (BluetoothPeripheralHandover.this.mState == 4) {
                        BluetoothPeripheralHandover.this.mState = 3;
                    } else if (BluetoothPeripheralHandover.this.mState == 5) {
                        BluetoothPeripheralHandover.this.mState = 4;
                    }
                    BluetoothPeripheralHandover.this.mRetryCount++;
                    BluetoothPeripheralHandover.this.nextStepConnect();
                }
            } else if (BluetoothPeripheralHandover.this.mState == 7) {
            } else {
                Log.i(BluetoothPeripheralHandover.TAG, "Timeout completing BT handover");
                if (BluetoothPeripheralHandover.this.mState == 3) {
                    BluetoothPeripheralHandover.this.mContext.sendBroadcast(new Intent(BluetoothPeripheralHandover.ACTION_TIMEOUT_CONNECT));
                } else if (BluetoothPeripheralHandover.this.mState == 4) {
                    BluetoothPeripheralHandover bluetoothPeripheralHandover = BluetoothPeripheralHandover.this;
                    bluetoothPeripheralHandover.toast(bluetoothPeripheralHandover.getToastString(R.string.pairing_peripheral_failed));
                } else if (BluetoothPeripheralHandover.this.mState == 5) {
                    if (BluetoothPeripheralHandover.this.mHidResult == 0) {
                        BluetoothPeripheralHandover.this.mHidResult = 2;
                    }
                    if (BluetoothPeripheralHandover.this.mA2dpResult == 0) {
                        BluetoothPeripheralHandover.this.mA2dpResult = 2;
                    }
                    if (BluetoothPeripheralHandover.this.mHfpResult == 0) {
                        BluetoothPeripheralHandover.this.mHfpResult = 2;
                    }
                    BluetoothPeripheralHandover.this.nextStepConnect();
                    return;
                }
                BluetoothPeripheralHandover.this.complete(BluetoothPeripheralHandover.DBG);
            }
        }
    };
    final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.nfc.handover.BluetoothPeripheralHandover.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            BluetoothPeripheralHandover.this.handleIntent(intent);
        }
    };

    /* loaded from: classes.dex */
    public interface Callback {
        void onBluetoothPeripheralHandoverComplete(boolean z);
    }

    public BluetoothPeripheralHandover(Context context, BluetoothDevice device, String name, int transport, OobData oobData, ParcelUuid[] uuids, BluetoothClass btClass, Callback callback) {
        checkMainThread();
        this.mContext = context;
        this.mDevice = device;
        this.mName = name;
        this.mTransport = transport;
        this.mOobData = oobData;
        this.mCallback = callback;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        ContentResolver contentResolver = this.mContext.getContentResolver();
        this.mProvisioning = Settings.Global.getInt(contentResolver, "device_provisioned", 0) == 0;
        this.mIsHeadsetAvailable = hasHeadsetCapability(uuids, btClass);
        this.mIsA2dpAvailable = hasA2dpCapability(uuids, btClass);
        if (!this.mIsHeadsetAvailable && !this.mIsA2dpAvailable) {
            this.mIsHeadsetAvailable = true;
            this.mIsA2dpAvailable = true;
        }
        this.mAudioManager = (AudioManager) this.mContext.getSystemService("audio");
        this.mState = 0;
    }

    public boolean hasStarted() {
        if (this.mState != 0) {
            return true;
        }
        return DBG;
    }

    public boolean start() {
        checkMainThread();
        if (this.mState != 0 || this.mBluetoothAdapter == null || (this.mProvisioning && this.mTransport != 2)) {
            return DBG;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        filter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
        filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction(ACTION_ALLOW_CONNECT);
        filter.addAction(ACTION_DENY_CONNECT);
        this.mContext.registerReceiver(this.mReceiver, filter);
        Handler handler = this.mHandler;
        handler.sendMessageDelayed(handler.obtainMessage(1), 20000L);
        this.mAction = 0;
        this.mRetryCount = 0;
        nextStep();
        return true;
    }

    void nextStep() {
        int i = this.mAction;
        if (i == 0) {
            nextStepInit();
        } else if (i == 2) {
            nextStepConnect();
        } else {
            nextStepDisconnect();
        }
    }

    void nextStepInit() {
        int i = this.mState;
        if (i != 0) {
            if (i != 1) {
                return;
            }
        } else if (this.mA2dp == null || this.mHeadset == null || this.mInput == null) {
            this.mState = 1;
            if (!getProfileProxys()) {
                complete(DBG);
                return;
            }
            return;
        }
        this.mState = 2;
        synchronized (this.mLock) {
            if (this.mTransport == 2) {
                if (this.mInput.getConnectedDevices().contains(this.mDevice)) {
                    Log.i(TAG, "ACTION_DISCONNECT addr=" + this.mDevice + " name=" + this.mName);
                    this.mAction = 1;
                } else {
                    Log.i(TAG, "ACTION_CONNECT addr=" + this.mDevice + " name=" + this.mName);
                    this.mAction = 2;
                }
            } else {
                if (!this.mA2dp.getConnectedDevices().contains(this.mDevice) && !this.mHeadset.getConnectedDevices().contains(this.mDevice)) {
                    if (this.mHeadset.getPriority(this.mDevice) == 0) {
                        this.mIsHeadsetAvailable = DBG;
                    }
                    if (this.mA2dp.getPriority(this.mDevice) == 0) {
                        this.mIsA2dpAvailable = DBG;
                    }
                    if (!this.mIsHeadsetAvailable && !this.mIsA2dpAvailable) {
                        Log.i(TAG, "Both Headset and A2DP profiles are unavailable");
                        complete(DBG);
                        return;
                    }
                    Log.i(TAG, "ACTION_CONNECT addr=" + this.mDevice + " name=" + this.mName);
                    this.mAction = 2;
                    if (this.mIsA2dpAvailable) {
                        this.mIsMusicActive = this.mAudioManager.isMusicActive();
                    }
                }
                Log.i(TAG, "ACTION_DISCONNECT addr=" + this.mDevice + " name=" + this.mName);
                this.mAction = 1;
            }
            nextStep();
        }
    }

    void nextStepDisconnect() {
        int i;
        int i2 = this.mState;
        if (i2 == 2) {
            this.mState = 6;
            synchronized (this.mLock) {
                if (this.mTransport == 2) {
                    if (this.mInput.getConnectionState(this.mDevice) != 0) {
                        this.mHidResult = 0;
                        this.mInput.disconnect(this.mDevice);
                        toast(getToastString(R.string.disconnecting_peripheral));
                        return;
                    }
                    this.mHidResult = 2;
                } else {
                    if (this.mHeadset.getConnectionState(this.mDevice) != 0) {
                        this.mHfpResult = 0;
                        this.mHeadset.disconnect(this.mDevice);
                    } else {
                        this.mHfpResult = 2;
                    }
                    if (this.mA2dp.getConnectionState(this.mDevice) != 0) {
                        this.mA2dpResult = 0;
                        this.mA2dp.disconnect(this.mDevice);
                    } else {
                        this.mA2dpResult = 2;
                    }
                    if (this.mA2dpResult != 0) {
                        if (this.mHfpResult == 0) {
                        }
                    }
                    toast(getToastString(R.string.disconnecting_peripheral));
                    return;
                }
            }
        } else if (i2 != 6) {
            return;
        }
        if (this.mTransport == 2) {
            if (this.mHidResult == 2) {
                toast(getToastString(R.string.disconnected_peripheral));
                complete(DBG);
                return;
            }
            return;
        }
        int i3 = this.mA2dpResult;
        if (i3 != 0 && (i = this.mHfpResult) != 0) {
            if (i3 == 2 && i == 2) {
                toast(getToastString(R.string.disconnected_peripheral));
            }
            complete(DBG);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String getToastString(int resid) {
        Context context = this.mContext;
        Object[] objArr = new Object[1];
        Object obj = this.mName;
        if (obj == null) {
            obj = Integer.valueOf((int) R.string.device);
        }
        objArr[0] = obj;
        return context.getString(resid, objArr);
    }

    boolean getProfileProxys() {
        if (this.mTransport == 2) {
            if (!this.mBluetoothAdapter.getProfileProxy(this.mContext, this, 4)) {
                return DBG;
            }
        } else if (!this.mBluetoothAdapter.getProfileProxy(this.mContext, this, 1) || !this.mBluetoothAdapter.getProfileProxy(this.mContext, this, 2)) {
            return DBG;
        }
        return true;
    }

    /* JADX WARN: Removed duplicated region for block: B:59:0x00d2  */
    /* JADX WARN: Removed duplicated region for block: B:65:0x00f6  */
    /* JADX WARN: Removed duplicated region for block: B:82:0x004f A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    void nextStepConnect() {
        /*
            Method dump skipped, instructions count: 299
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.BluetoothPeripheralHandover.nextStepConnect():void");
    }

    void startBonding() {
        this.mState = 4;
        if (this.mRetryCount == 0) {
            toast(getToastString(R.string.pairing_peripheral));
        }
        OobData oobData = this.mOobData;
        if (oobData != null) {
            if (!this.mDevice.createBondOutOfBand(this.mTransport, oobData)) {
                toast(getToastString(R.string.pairing_peripheral_failed));
                complete(DBG);
            }
        } else if (!this.mDevice.createBond(this.mTransport)) {
            toast(getToastString(R.string.pairing_peripheral_failed));
            complete(DBG);
        }
    }

    void handleIntent(Intent intent) {
        int i;
        int i2;
        String action = intent.getAction();
        BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
        if (this.mDevice.equals(device)) {
            if (ACTION_ALLOW_CONNECT.equals(action)) {
                this.mHandler.removeMessages(1);
                Handler handler = this.mHandler;
                handler.sendMessageDelayed(handler.obtainMessage(1), 20000L);
                nextStepConnect();
            } else if (ACTION_DENY_CONNECT.equals(action)) {
                complete(DBG);
            } else if ("android.bluetooth.device.action.BOND_STATE_CHANGED".equals(action) && this.mState == 4) {
                int bond = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", Integer.MIN_VALUE);
                if (bond == 12) {
                    this.mRetryCount = 0;
                    nextStepConnect();
                } else if (bond == 10) {
                    if (this.mRetryCount < 3) {
                        sendRetryMessage(RETRY_PAIRING_WAIT_TIME_MS);
                        return;
                    }
                    toast(getToastString(R.string.pairing_peripheral_failed));
                    complete(DBG);
                }
            } else if ("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED".equals(action) && ((i2 = this.mState) == 5 || i2 == 6)) {
                int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", Integer.MIN_VALUE);
                if (state == 2) {
                    this.mHfpResult = 1;
                    nextStep();
                } else if (state == 0) {
                    if (this.mAction == 2 && this.mRetryCount < 3) {
                        sendRetryMessage(5000);
                        return;
                    }
                    this.mHfpResult = 2;
                    nextStep();
                }
            } else if ("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED".equals(action) && ((i = this.mState) == 5 || i == 6)) {
                int state2 = intent.getIntExtra("android.bluetooth.profile.extra.STATE", Integer.MIN_VALUE);
                if (state2 == 2) {
                    this.mA2dpResult = 1;
                    nextStep();
                } else if (state2 == 0) {
                    if (this.mAction == 2 && this.mRetryCount < 3) {
                        sendRetryMessage(5000);
                        return;
                    }
                    this.mA2dpResult = 2;
                    nextStep();
                }
            } else if ("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
                int i3 = this.mState;
                if (i3 == 5 || i3 == 6) {
                    int state3 = intent.getIntExtra("android.bluetooth.profile.extra.STATE", Integer.MIN_VALUE);
                    if (state3 == 2) {
                        this.mHidResult = 1;
                        nextStep();
                    } else if (state3 == 0) {
                        this.mHidResult = 2;
                        nextStep();
                    }
                }
            }
        }
    }

    void complete(boolean connected) {
        this.mState = 7;
        this.mContext.unregisterReceiver(this.mReceiver);
        this.mHandler.removeMessages(1);
        this.mHandler.removeMessages(3);
        synchronized (this.mLock) {
            if (this.mA2dp != null) {
                this.mBluetoothAdapter.closeProfileProxy(2, this.mA2dp);
            }
            if (this.mHeadset != null) {
                this.mBluetoothAdapter.closeProfileProxy(1, this.mHeadset);
            }
            if (this.mInput != null) {
                this.mBluetoothAdapter.closeProfileProxy(4, this.mInput);
            }
            this.mA2dp = null;
            this.mHeadset = null;
            this.mInput = null;
        }
        this.mCallback.onBluetoothPeripheralHandoverComplete(connected);
    }

    void toast(CharSequence text) {
        Toast.makeText(this.mContext, text, 0).show();
    }

    void startTheMusic() {
        if (!this.mContext.getResources().getBoolean(R.bool.enable_auto_play) && !this.mIsMusicActive) {
            return;
        }
        MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(this.mContext);
        if (helper != null) {
            KeyEvent keyEvent = new KeyEvent(0, 126);
            helper.sendMediaButtonEvent(keyEvent, (boolean) DBG);
            KeyEvent keyEvent2 = new KeyEvent(1, 126);
            helper.sendMediaButtonEvent(keyEvent2, (boolean) DBG);
            return;
        }
        Log.w(TAG, "Unable to send media key event");
    }

    void requestPairConfirmation() {
        Intent dialogIntent = new Intent(this.mContext, ConfirmConnectActivity.class);
        dialogIntent.setFlags(268468224);
        dialogIntent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
        dialogIntent.putExtra("android.bluetooth.device.extra.NAME", this.mName);
        this.mContext.startActivity(dialogIntent);
    }

    boolean hasA2dpCapability(ParcelUuid[] uuids, BluetoothClass btClass) {
        if (uuids != null) {
            for (ParcelUuid uuid : uuids) {
                if (BluetoothUuid.isAudioSink(uuid) || BluetoothUuid.isAdvAudioDist(uuid)) {
                    return true;
                }
            }
        }
        if (btClass == null || !btClass.doesClassMatch(1)) {
            return DBG;
        }
        return true;
    }

    boolean hasHeadsetCapability(ParcelUuid[] uuids, BluetoothClass btClass) {
        if (uuids != null) {
            for (ParcelUuid uuid : uuids) {
                if (BluetoothUuid.isHandsfree(uuid) || BluetoothUuid.isHeadset(uuid)) {
                    return true;
                }
            }
        }
        if (btClass == null || !btClass.doesClassMatch(0)) {
            return DBG;
        }
        return true;
    }

    static void checkMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalThreadStateException("must be called on main thread");
        }
    }

    @Override // android.bluetooth.BluetoothProfile.ServiceListener
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        synchronized (this.mLock) {
            if (profile == 1) {
                this.mHeadset = (BluetoothHeadset) proxy;
                if (this.mA2dp != null) {
                    this.mHandler.sendEmptyMessage(2);
                }
            } else if (profile == 2) {
                this.mA2dp = (BluetoothA2dp) proxy;
                if (this.mHeadset != null) {
                    this.mHandler.sendEmptyMessage(2);
                }
            } else if (profile == 4) {
                this.mInput = (BluetoothHidHost) proxy;
                if (this.mInput != null) {
                    this.mHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    @Override // android.bluetooth.BluetoothProfile.ServiceListener
    public void onServiceDisconnected(int profile) {
    }

    void sendRetryMessage(int waitTime) {
        if (!this.mHandler.hasMessages(3)) {
            Handler handler = this.mHandler;
            handler.sendMessageDelayed(handler.obtainMessage(3), waitTime);
        }
    }
}
