package com.android.nfc.ndefpush;

import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import java.io.IOException;
import java.util.Arrays;
/* loaded from: classes.dex */
public class NdefPushClient {
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final boolean DBG = true;
    private static final int DISCONNECTED = 0;
    private static final int MIU = 128;
    private static final String TAG = "NdefPushClient";
    private DeviceHost.LlcpSocket mSocket;
    final Object mLock = new Object();
    private int mState = 0;

    public void connect() throws IOException {
        synchronized (this.mLock) {
            if (this.mState != 0) {
                throw new IOException("Socket still in use.");
            }
            this.mState = 1;
        }
        NfcService service = NfcService.getInstance();
        Log.d(TAG, "about to create socket");
        try {
            DeviceHost.LlcpSocket sock = service.createLlcpSocket(0, 128, 1, 1024);
            try {
                Log.d(TAG, "about to connect to service com.android.npp");
                sock.connectToService("com.android.npp");
                synchronized (this.mLock) {
                    this.mSocket = sock;
                    this.mState = 2;
                }
            } catch (IOException e) {
                if (sock != null) {
                    try {
                        sock.close();
                    } catch (IOException e2) {
                    }
                }
                synchronized (this.mLock) {
                    this.mState = 0;
                    throw new IOException("Could not connect service.");
                }
            }
        } catch (LlcpException e3) {
            synchronized (this.mLock) {
                this.mState = 0;
                throw new IOException("Could not create socket.");
            }
        }
    }

    public boolean push(NdefMessage msg) {
        synchronized (this.mLock) {
            if (this.mState != 2) {
                Log.e(TAG, "Not connected to NPP.");
                return false;
            }
            DeviceHost.LlcpSocket sock = this.mSocket;
            NdefPushProtocol proto = new NdefPushProtocol(msg, (byte) 1);
            byte[] buffer = proto.toByteArray();
            int offset = 0;
            try {
                try {
                    int remoteMiu = sock.getRemoteMiu();
                    Log.d(TAG, "about to send a " + buffer.length + " byte message");
                    while (offset < buffer.length) {
                        int length = Math.min(buffer.length - offset, remoteMiu);
                        byte[] tmpBuffer = Arrays.copyOfRange(buffer, offset, offset + length);
                        Log.d(TAG, "about to send a " + length + " byte packet");
                        sock.send(tmpBuffer);
                        offset += length;
                    }
                    try {
                        Log.d(TAG, "about to close");
                        sock.close();
                    } catch (IOException e) {
                    }
                    return DBG;
                } catch (IOException e2) {
                    Log.e(TAG, "couldn't send tag");
                    Log.d(TAG, "exception:", e2);
                    if (sock != null) {
                        try {
                            Log.d(TAG, "about to close");
                            sock.close();
                        } catch (IOException e3) {
                        }
                    }
                    return false;
                }
            } catch (Throwable th) {
                if (sock != null) {
                    try {
                        Log.d(TAG, "about to close");
                        sock.close();
                    } catch (IOException e4) {
                    }
                }
                throw th;
            }
        }
    }

    public void close() {
        synchronized (this.mLock) {
            if (this.mSocket != null) {
                try {
                    Log.d(TAG, "About to close NPP socket.");
                    this.mSocket.close();
                } catch (IOException e) {
                }
                this.mSocket = null;
            }
            this.mState = 0;
        }
    }
}
