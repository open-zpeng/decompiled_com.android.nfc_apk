package com.android.nfc.cardemulation;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.cardemulation.NfcFServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.StatsLog;
import com.android.nfc.NfcService;
import com.android.nfc.snep.SnepMessage;
import java.io.FileDescriptor;
import java.io.PrintWriter;
/* loaded from: classes.dex */
public class HostNfcFEmulationManager {
    static final boolean DBG = false;
    static final int MINIMUM_NFCF_PACKET_LENGTH = 10;
    static final int NFCID2_LENGTH = 8;
    static final int STATE_IDLE = 0;
    static final int STATE_W4_SERVICE = 1;
    static final int STATE_XFER = 2;
    static final String TAG = "HostNfcFEmulationManager";
    Messenger mActiveService;
    ComponentName mActiveServiceName;
    final Context mContext;
    byte[] mPendingPacket;
    Messenger mService;
    boolean mServiceBound;
    ComponentName mServiceName;
    final RegisteredT3tIdentifiersCache mT3tIdentifiersCache;
    final Messenger mMessenger = new Messenger(new MessageHandler());
    private ServiceConnection mConnection = new ServiceConnection() { // from class: com.android.nfc.cardemulation.HostNfcFEmulationManager.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (HostNfcFEmulationManager.this.mLock) {
                HostNfcFEmulationManager.this.mService = new Messenger(service);
                HostNfcFEmulationManager.this.mServiceBound = true;
                HostNfcFEmulationManager.this.mServiceName = name;
                Log.d(HostNfcFEmulationManager.TAG, "Service bound");
                HostNfcFEmulationManager.this.mState = 2;
                if (HostNfcFEmulationManager.this.mPendingPacket != null) {
                    HostNfcFEmulationManager.this.sendDataToServiceLocked(HostNfcFEmulationManager.this.mService, HostNfcFEmulationManager.this.mPendingPacket);
                    HostNfcFEmulationManager.this.mPendingPacket = null;
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            synchronized (HostNfcFEmulationManager.this.mLock) {
                Log.d(HostNfcFEmulationManager.TAG, "Service unbound");
                HostNfcFEmulationManager.this.mService = null;
                HostNfcFEmulationManager.this.mServiceBound = HostNfcFEmulationManager.DBG;
                HostNfcFEmulationManager.this.mServiceName = null;
            }
        }
    };
    final Object mLock = new Object();
    ComponentName mEnabledFgServiceName = null;
    int mState = 0;

    public HostNfcFEmulationManager(Context context, RegisteredT3tIdentifiersCache t3tIdentifiersCache) {
        this.mContext = context;
        this.mT3tIdentifiersCache = t3tIdentifiersCache;
    }

    public void onEnabledForegroundNfcFServiceChanged(ComponentName service) {
        synchronized (this.mLock) {
            this.mEnabledFgServiceName = service;
            if (service == null) {
                sendDeactivateToActiveServiceLocked(0);
                unbindServiceIfNeededLocked();
            }
        }
    }

    public void onHostEmulationActivated() {
    }

    public void onHostEmulationData(byte[] data) {
        String nfcid2 = findNfcid2(data);
        ComponentName resolvedServiceName = null;
        synchronized (this.mLock) {
            if (nfcid2 != null) {
                try {
                    NfcFServiceInfo resolvedService = this.mT3tIdentifiersCache.resolveNfcid2(nfcid2);
                    if (resolvedService != null) {
                        resolvedServiceName = resolvedService.getComponent();
                    }
                } finally {
                }
            }
            if (resolvedServiceName == null) {
                if (this.mActiveServiceName == null) {
                    return;
                }
                resolvedServiceName = this.mActiveServiceName;
            }
            if (this.mEnabledFgServiceName != null && this.mEnabledFgServiceName.equals(resolvedServiceName)) {
                int i = this.mState;
                if (i == 0) {
                    Messenger existingService = bindServiceIfNeededLocked(resolvedServiceName);
                    if (existingService != null) {
                        Log.d(TAG, "Binding to existing service");
                        this.mState = 2;
                        sendDataToServiceLocked(existingService, data);
                    } else {
                        Log.d(TAG, "Waiting for new service.");
                        this.mPendingPacket = data;
                        this.mState = 1;
                    }
                    StatsLog.write(137, 1, "HCEF");
                } else if (i == 1) {
                    Log.d(TAG, "Unexpected packet in STATE_W4_SERVICE");
                } else if (i == 2) {
                    sendDataToServiceLocked(this.mActiveService, data);
                }
            }
        }
    }

    public void onHostEmulationDeactivated() {
        synchronized (this.mLock) {
            sendDeactivateToActiveServiceLocked(0);
            this.mActiveService = null;
            this.mActiveServiceName = null;
            unbindServiceIfNeededLocked();
            this.mState = 0;
        }
    }

    public void onNfcDisabled() {
        synchronized (this.mLock) {
            sendDeactivateToActiveServiceLocked(0);
            this.mEnabledFgServiceName = null;
            this.mActiveService = null;
            this.mActiveServiceName = null;
            unbindServiceIfNeededLocked();
            this.mState = 0;
        }
    }

    public void onUserSwitched() {
        synchronized (this.mLock) {
            sendDeactivateToActiveServiceLocked(0);
            this.mEnabledFgServiceName = null;
            this.mActiveService = null;
            this.mActiveServiceName = null;
            unbindServiceIfNeededLocked();
            this.mState = 0;
        }
    }

    void sendDataToServiceLocked(Messenger service, byte[] data) {
        if (service != this.mActiveService) {
            sendDeactivateToActiveServiceLocked(0);
            this.mActiveService = service;
            this.mActiveServiceName = this.mServiceName;
        }
        Message msg = Message.obtain((Handler) null, 0);
        Bundle dataBundle = new Bundle();
        dataBundle.putByteArray("data", data);
        msg.setData(dataBundle);
        msg.replyTo = this.mMessenger;
        try {
            Log.d(TAG, "Sending data to service");
            this.mActiveService.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote service has died, dropping packet");
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

    Messenger bindServiceIfNeededLocked(ComponentName service) {
        if (this.mServiceBound && this.mServiceName.equals(service)) {
            Log.d(TAG, "Service already bound.");
            return this.mService;
        }
        Log.d(TAG, "Binding to service " + service);
        unbindServiceIfNeededLocked();
        Intent bindIntent = new Intent("android.nfc.cardemulation.action.HOST_NFCF_SERVICE");
        bindIntent.setComponent(service);
        if (!this.mContext.bindServiceAsUser(bindIntent, this.mConnection, 1, UserHandle.CURRENT)) {
            Log.e(TAG, "Could not bind service.");
            return null;
        }
        return null;
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

    String findNfcid2(byte[] data) {
        if (data == null || data.length < 10) {
            return null;
        }
        return bytesToString(data, 2, 8);
    }

    /* loaded from: classes.dex */
    class MessageHandler extends Handler {
        MessageHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            Bundle dataBundle;
            byte[] data;
            int state;
            synchronized (HostNfcFEmulationManager.this.mLock) {
                if (HostNfcFEmulationManager.this.mActiveService == null) {
                    Log.d(HostNfcFEmulationManager.TAG, "Dropping service response message; service no longer active.");
                } else if (!msg.replyTo.getBinder().equals(HostNfcFEmulationManager.this.mActiveService.getBinder())) {
                    Log.d(HostNfcFEmulationManager.TAG, "Dropping service response message; service no longer bound.");
                } else if (msg.what != 1 || (dataBundle = msg.getData()) == null || (data = dataBundle.getByteArray("data")) == null) {
                } else {
                    if (data.length == 0) {
                        Log.e(HostNfcFEmulationManager.TAG, "Invalid response packet");
                    } else if (data.length != (data[0] & SnepMessage.RESPONSE_REJECT)) {
                        Log.e(HostNfcFEmulationManager.TAG, "Invalid response packet");
                    } else {
                        synchronized (HostNfcFEmulationManager.this.mLock) {
                            state = HostNfcFEmulationManager.this.mState;
                        }
                        if (state == 2) {
                            Log.d(HostNfcFEmulationManager.TAG, "Sending data");
                            NfcService.getInstance().sendData(data);
                            return;
                        }
                        Log.d(HostNfcFEmulationManager.TAG, "Dropping data, wrong state " + Integer.toString(state));
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

    private String getByteDump(byte[] cmd) {
        StringBuffer str = new StringBuffer("");
        if (cmd == null) {
            str.append(" null\n");
            return str.toString();
        }
        for (int i = 0; i < cmd.length; i++) {
            str.append(String.format(" %02X", Byte.valueOf(cmd[i])));
            if (i % 8 == 8 - 1 || i + 1 == cmd.length) {
                str.append("\n");
            }
        }
        return str.toString();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Bound HCE-F services: ");
        if (this.mServiceBound) {
            pw.println("    service: " + this.mServiceName);
        }
    }
}
