package com.android.nfc.handover;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
/* loaded from: classes.dex */
public final class HandoverClient {
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final boolean DBG = false;
    private static final int DISCONNECTED = 0;
    private static final int MIU = 128;
    private static final String TAG = "HandoverClient";
    private static final Object mLock = new Object();
    DeviceHost.LlcpSocket mSocket;
    int mState;

    public void connect() throws IOException {
        synchronized (mLock) {
            if (this.mState != 0) {
                throw new IOException("Socket in use.");
            }
            this.mState = 1;
        }
        NfcService service = NfcService.getInstance();
        try {
            DeviceHost.LlcpSocket sock = service.createLlcpSocket(0, 128, 1, 1024);
            try {
                sock.connectToService("urn:nfc:sn:handover");
                synchronized (mLock) {
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
                synchronized (mLock) {
                    this.mState = 0;
                    throw new IOException("Could not connect to handover service");
                }
            }
        } catch (LlcpException e3) {
            synchronized (mLock) {
                this.mState = 0;
                throw new IOException("Could not create socket");
            }
        }
    }

    public void close() {
        synchronized (mLock) {
            if (this.mSocket != null) {
                try {
                    this.mSocket.close();
                } catch (IOException e) {
                }
                this.mSocket = null;
            }
            this.mState = 0;
        }
    }

    public NdefMessage sendHandoverRequest(NdefMessage msg) throws IOException {
        DeviceHost.LlcpSocket sock;
        if (msg == null) {
            return null;
        }
        synchronized (mLock) {
            if (this.mState != 2) {
                throw new IOException("Socket not connected");
            }
            sock = this.mSocket;
        }
        int offset = 0;
        byte[] buffer = msg.toByteArray();
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            int remoteMiu = sock.getRemoteMiu();
            while (offset < buffer.length) {
                int length = Math.min(buffer.length - offset, remoteMiu);
                byte[] tmpBuffer = Arrays.copyOfRange(buffer, offset, offset + length);
                sock.send(tmpBuffer);
                offset += length;
            }
            byte[] partial = new byte[sock.getLocalMiu()];
            NdefMessage handoverSelectMsg = null;
            while (true) {
                int size = sock.receive(partial);
                if (size < 0) {
                    break;
                }
                byteStream.write(partial, 0, size);
                try {
                    handoverSelectMsg = new NdefMessage(byteStream.toByteArray());
                    break;
                } catch (FormatException e) {
                }
            }
            try {
                sock.close();
            } catch (IOException e2) {
            }
            try {
                byteStream.close();
            } catch (IOException e3) {
            }
            return handoverSelectMsg;
        } catch (IOException e4) {
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException e5) {
                }
            }
            try {
                byteStream.close();
            } catch (IOException e6) {
            }
            return null;
        } catch (Throwable th) {
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException e7) {
                }
            }
            try {
                byteStream.close();
            } catch (IOException e8) {
            }
            throw th;
        }
    }
}
