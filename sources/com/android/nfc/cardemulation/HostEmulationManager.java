package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.cardemulation.ApduServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import com.android.nfc.NfcService;
import com.android.nfc.cardemulation.RegisteredAidCache;
import com.android.nfc.snep.SnepMessage;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
/* loaded from: classes.dex */
public class HostEmulationManager {
    static final String ANDROID_HCE_AID = "A000000476416E64726F6964484345";
    static final boolean DBG = false;
    static final byte INSTR_SELECT = -92;
    static final int MINIMUM_AID_LENGTH = 5;
    static final int SELECT_APDU_HDR_LENGTH = 5;
    static final int STATE_IDLE = 0;
    static final int STATE_W4_DEACTIVATE = 3;
    static final int STATE_W4_SELECT = 1;
    static final int STATE_W4_SERVICE = 2;
    static final int STATE_XFER = 4;
    static final String TAG = "HostEmulationManager";
    Messenger mActiveService;
    ComponentName mActiveServiceName;
    final RegisteredAidCache mAidCache;
    final Context mContext;
    final KeyguardManager mKeyguard;
    ComponentName mLastBoundPaymentServiceName;
    String mLastSelectedAid;
    Messenger mPaymentService;
    byte[] mSelectApdu;
    Messenger mService;
    static final byte[] ANDROID_HCE_RESPONSE = {20, SnepMessage.RESPONSE_SUCCESS, 0, 0, -112, 0};
    static final byte[] AID_NOT_FOUND = {106, -126};
    static final byte[] UNKNOWN_ERROR = {111, 0};
    final Messenger mMessenger = new Messenger(new MessageHandler());
    boolean mServiceBound = DBG;
    ComponentName mServiceName = null;
    boolean mPaymentServiceBound = DBG;
    ComponentName mPaymentServiceName = null;
    private ServiceConnection mPaymentConnection = new ServiceConnection() { // from class: com.android.nfc.cardemulation.HostEmulationManager.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (HostEmulationManager.this.mLock) {
                if (HostEmulationManager.this.mLastBoundPaymentServiceName.equals(name)) {
                    HostEmulationManager.this.mPaymentServiceName = name;
                    HostEmulationManager.this.mPaymentService = new Messenger(service);
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            synchronized (HostEmulationManager.this.mLock) {
                HostEmulationManager.this.mPaymentService = null;
                HostEmulationManager.this.mPaymentServiceBound = HostEmulationManager.DBG;
                HostEmulationManager.this.mPaymentServiceName = null;
            }
        }
    };
    private ServiceConnection mConnection = new ServiceConnection() { // from class: com.android.nfc.cardemulation.HostEmulationManager.2
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (HostEmulationManager.this.mLock) {
                if (HostEmulationManager.this.mState == 0) {
                    return;
                }
                HostEmulationManager.this.mService = new Messenger(service);
                HostEmulationManager.this.mServiceName = name;
                Log.d(HostEmulationManager.TAG, "Service bound");
                HostEmulationManager.this.mState = 4;
                if (HostEmulationManager.this.mSelectApdu != null) {
                    HostEmulationManager.this.sendDataToServiceLocked(HostEmulationManager.this.mService, HostEmulationManager.this.mSelectApdu);
                    HostEmulationManager.this.mSelectApdu = null;
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            synchronized (HostEmulationManager.this.mLock) {
                Log.d(HostEmulationManager.TAG, "Service unbound");
                HostEmulationManager.this.mService = null;
                HostEmulationManager.this.mServiceBound = HostEmulationManager.DBG;
            }
        }
    };
    final Object mLock = new Object();
    int mState = 0;

    public HostEmulationManager(Context context, RegisteredAidCache aidCache) {
        this.mContext = context;
        this.mAidCache = aidCache;
        this.mKeyguard = (KeyguardManager) context.getSystemService("keyguard");
    }

    public void onPreferredPaymentServiceChanged(final ComponentName service) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.android.nfc.cardemulation.-$$Lambda$HostEmulationManager$l3zf4PM7t_oqnJtgTLUmF3VO_MU
            @Override // java.lang.Runnable
            public final void run() {
                HostEmulationManager.this.lambda$onPreferredPaymentServiceChanged$0$HostEmulationManager(service);
            }
        });
    }

    public /* synthetic */ void lambda$onPreferredPaymentServiceChanged$0$HostEmulationManager(ComponentName service) {
        synchronized (this.mLock) {
            if (service != null) {
                bindPaymentServiceLocked(ActivityManager.getCurrentUser(), service);
            } else {
                unbindPaymentServiceLocked();
            }
        }
    }

    public void onPreferredForegroundServiceChanged(ComponentName service) {
        synchronized (this.mLock) {
            if (service != null) {
                bindServiceIfNeededLocked(service);
            } else {
                unbindServiceIfNeededLocked();
            }
        }
    }

    public void onHostEmulationActivated() {
        Log.d(TAG, "notifyHostEmulationActivated");
        synchronized (this.mLock) {
            Intent intent = new Intent(TapAgainDialog.ACTION_CLOSE);
            intent.setPackage("com.android.nfc");
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            if (this.mState != 0) {
                Log.e(TAG, "Got activation event in non-idle state");
            }
            this.mState = 1;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:53:0x00c3 A[Catch: all -> 0x0161, TryCatch #0 {, blocks: (B:4:0x0010, B:6:0x0014, B:7:0x001b, B:9:0x001d, B:11:0x0022, B:12:0x0029, B:15:0x002d, B:17:0x0035, B:18:0x003e, B:20:0x0040, B:22:0x0049, B:25:0x0053, B:27:0x0059, B:29:0x0061, B:31:0x0069, B:33:0x0071, B:34:0x007a, B:36:0x007c, B:38:0x0082, B:39:0x0092, B:41:0x0094, B:53:0x00c3, B:54:0x00cf, B:43:0x009a, B:45:0x009e, B:46:0x00a4, B:48:0x00aa, B:50:0x00bc, B:56:0x00d1, B:57:0x00da, B:59:0x00dc, B:86:0x015f, B:65:0x00eb, B:67:0x00f1, B:68:0x00f7, B:70:0x00fc, B:72:0x0100, B:73:0x0106, B:74:0x010e, B:76:0x0118, B:78:0x011e, B:80:0x0136, B:82:0x0142, B:83:0x0148, B:79:0x012b, B:85:0x014e), top: B:91:0x0010 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void onHostEmulationData(byte[] r11) {
        /*
            Method dump skipped, instructions count: 356
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.cardemulation.HostEmulationManager.onHostEmulationData(byte[]):void");
    }

    public void onHostEmulationDeactivated() {
        Log.d(TAG, "notifyHostEmulationDeactivated");
        synchronized (this.mLock) {
            if (this.mState == 0) {
                Log.e(TAG, "Got deactivation event while in idle state");
            }
            sendDeactivateToActiveServiceLocked(0);
            this.mActiveService = null;
            this.mActiveServiceName = null;
            unbindServiceIfNeededLocked();
            this.mState = 0;
        }
    }

    public void onOffHostAidSelected() {
        Log.d(TAG, "notifyOffHostAidSelected");
        synchronized (this.mLock) {
            if (this.mState == 4 && this.mActiveService != null) {
                sendDeactivateToActiveServiceLocked(1);
            }
            this.mActiveService = null;
            this.mActiveServiceName = null;
            unbindServiceIfNeededLocked();
            this.mState = 1;
            Intent intent = new Intent(TapAgainDialog.ACTION_CLOSE);
            intent.setPackage("com.android.nfc");
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    Messenger bindServiceIfNeededLocked(ComponentName service) {
        ComponentName componentName = this.mPaymentServiceName;
        if (componentName != null && componentName.equals(service)) {
            Log.d(TAG, "Service already bound as payment service.");
            return this.mPaymentService;
        }
        ComponentName componentName2 = this.mServiceName;
        if (componentName2 != null && componentName2.equals(service)) {
            Log.d(TAG, "Service already bound as regular service.");
            return this.mService;
        }
        Log.d(TAG, "Binding to service " + service);
        unbindServiceIfNeededLocked();
        Intent aidIntent = new Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE");
        aidIntent.setComponent(service);
        if (this.mContext.bindServiceAsUser(aidIntent, this.mConnection, 1048577, UserHandle.CURRENT)) {
            this.mServiceBound = true;
            return null;
        }
        Log.e(TAG, "Could not bind service.");
        return null;
    }

    void sendDataToServiceLocked(Messenger service, byte[] data) {
        if (service != this.mActiveService) {
            sendDeactivateToActiveServiceLocked(1);
            this.mActiveService = service;
            if (service.equals(this.mPaymentService)) {
                this.mActiveServiceName = this.mPaymentServiceName;
            } else {
                this.mActiveServiceName = this.mServiceName;
            }
        }
        Message msg = Message.obtain((Handler) null, 0);
        Bundle dataBundle = new Bundle();
        dataBundle.putByteArray("data", data);
        msg.setData(dataBundle);
        msg.replyTo = this.mMessenger;
        try {
            this.mActiveService.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote service has died, dropping APDU");
        }
    }

    void sendDeactivateToActiveServiceLocked(int reason) {
        if (this.mActiveService == null) {
            return;
        }
        Message msg = Message.obtain((Handler) null, 2);
        msg.arg1 = reason;
        try {
            this.mActiveService.send(msg);
        } catch (RemoteException e) {
        }
    }

    void unbindPaymentServiceLocked() {
        if (this.mPaymentServiceBound) {
            this.mContext.unbindService(this.mPaymentConnection);
            this.mPaymentServiceBound = DBG;
            this.mPaymentService = null;
            this.mPaymentServiceName = null;
        }
    }

    void bindPaymentServiceLocked(int userId, ComponentName service) {
        unbindPaymentServiceLocked();
        Intent intent = new Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE");
        intent.setComponent(service);
        this.mLastBoundPaymentServiceName = service;
        if (this.mContext.bindServiceAsUser(intent, this.mPaymentConnection, 1048577, new UserHandle(userId))) {
            this.mPaymentServiceBound = true;
        } else {
            Log.e(TAG, "Could not bind (persistent) payment service.");
        }
    }

    void unbindServiceIfNeededLocked() {
        if (this.mServiceBound) {
            Log.d(TAG, "Unbinding from service " + this.mServiceName);
            this.mContext.unbindService(this.mConnection);
            this.mServiceBound = DBG;
            this.mService = null;
            this.mServiceName = null;
        }
    }

    void launchTapAgain(ApduServiceInfo service, String category) {
        Intent dialogIntent = new Intent(this.mContext, TapAgainDialog.class);
        dialogIntent.putExtra("category", category);
        dialogIntent.putExtra(TapAgainDialog.EXTRA_APDU_SERVICE, (Parcelable) service);
        dialogIntent.setFlags(268468224);
        this.mContext.startActivityAsUser(dialogIntent, UserHandle.CURRENT);
    }

    void launchResolver(ArrayList<ApduServiceInfo> services, ComponentName failedComponent, String category) {
        Intent intent = new Intent(this.mContext, AppChooserActivity.class);
        intent.setFlags(268468224);
        intent.putParcelableArrayListExtra(AppChooserActivity.EXTRA_APDU_SERVICES, services);
        intent.putExtra("category", category);
        if (failedComponent != null) {
            intent.putExtra(AppChooserActivity.EXTRA_FAILED_COMPONENT, failedComponent);
        }
        this.mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    String findSelectAid(byte[] data) {
        if (data == null || data.length < 10 || data[0] != 0 || data[1] != -92 || data[2] != 4) {
            return null;
        }
        if (data[3] != 0) {
            Log.d(TAG, "Selecting next, last or previous AID occurrence is not supported");
        }
        int aidLength = data[4];
        if (data.length < aidLength + 5) {
            return null;
        }
        return bytesToString(data, 5, aidLength);
    }

    /* loaded from: classes.dex */
    class MessageHandler extends Handler {
        MessageHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int state;
            synchronized (HostEmulationManager.this.mLock) {
                if (HostEmulationManager.this.mActiveService == null) {
                    Log.d(HostEmulationManager.TAG, "Dropping service response message; service no longer active.");
                } else if (!msg.replyTo.getBinder().equals(HostEmulationManager.this.mActiveService.getBinder())) {
                    Log.d(HostEmulationManager.TAG, "Dropping service response message; service no longer bound.");
                } else if (msg.what == 1) {
                    Bundle dataBundle = msg.getData();
                    if (dataBundle == null) {
                        return;
                    }
                    byte[] data = dataBundle.getByteArray("data");
                    if (data == null || data.length == 0) {
                        Log.e(HostEmulationManager.TAG, "Dropping empty R-APDU");
                        return;
                    }
                    synchronized (HostEmulationManager.this.mLock) {
                        state = HostEmulationManager.this.mState;
                    }
                    if (state == 4) {
                        Log.d(HostEmulationManager.TAG, "Sending data");
                        NfcService.getInstance().sendData(data);
                        return;
                    }
                    Log.d(HostEmulationManager.TAG, "Dropping data, wrong state " + Integer.toString(state));
                } else if (msg.what == 3) {
                    synchronized (HostEmulationManager.this.mLock) {
                        RegisteredAidCache.AidResolveInfo resolveInfo = HostEmulationManager.this.mAidCache.resolveAid(HostEmulationManager.this.mLastSelectedAid);
                        if (resolveInfo.services.size() > 0) {
                            HostEmulationManager.this.launchResolver((ArrayList) resolveInfo.services, HostEmulationManager.this.mActiveServiceName, resolveInfo.category);
                        }
                    }
                }
            }
        }
    }

    static String bytesToString(byte[] bytes, int offset, int length) {
        char[] hexChars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] chars = new char[length * 2];
        for (int j = 0; j < length; j++) {
            int byteValue = bytes[offset + j] & SnepMessage.RESPONSE_REJECT;
            chars[j * 2] = hexChars[byteValue >>> 4];
            chars[(j * 2) + 1] = hexChars[byteValue & 15];
        }
        return new String(chars);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Bound HCE-A/HCE-B services: ");
        if (this.mPaymentServiceBound) {
            pw.println("    payment: " + this.mPaymentServiceName);
        }
        if (this.mServiceBound) {
            pw.println("    other: " + this.mServiceName);
        }
    }
}
