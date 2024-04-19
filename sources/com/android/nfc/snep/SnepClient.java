package com.android.nfc.snep;

import android.nfc.NdefMessage;
import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import java.io.IOException;
/* loaded from: classes.dex */
public final class SnepClient {
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final boolean DBG = false;
    private static final int DEFAULT_ACCEPTABLE_LENGTH = 102400;
    private static final int DEFAULT_MIU = 128;
    private static final int DEFAULT_RWSIZE = 1;
    private static final int DISCONNECTED = 0;
    private static final String TAG = "SnepClient";
    private final int mAcceptableLength;
    private final int mFragmentLength;
    SnepMessenger mMessenger;
    private final int mMiu;
    private final int mPort;
    private final int mRwSize;
    private final String mServiceName;
    private int mState;
    private final Object mTransmissionLock;

    public SnepClient() {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = 0;
        this.mServiceName = SnepServer.DEFAULT_SERVICE_NAME;
        this.mPort = 4;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        this.mFragmentLength = -1;
        this.mMiu = 128;
        this.mRwSize = 1;
    }

    public SnepClient(String serviceName) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = 0;
        this.mServiceName = serviceName;
        this.mPort = -1;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        this.mFragmentLength = -1;
        this.mMiu = 128;
        this.mRwSize = 1;
    }

    public SnepClient(int miu, int rwSize) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = 0;
        this.mServiceName = SnepServer.DEFAULT_SERVICE_NAME;
        this.mPort = 4;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        this.mFragmentLength = -1;
        this.mMiu = miu;
        this.mRwSize = rwSize;
    }

    SnepClient(String serviceName, int fragmentLength) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = 0;
        this.mServiceName = serviceName;
        this.mPort = -1;
        this.mAcceptableLength = DEFAULT_ACCEPTABLE_LENGTH;
        this.mFragmentLength = fragmentLength;
        this.mMiu = 128;
        this.mRwSize = 1;
    }

    SnepClient(String serviceName, int acceptableLength, int fragmentLength) {
        this.mMessenger = null;
        this.mTransmissionLock = new Object();
        this.mState = 0;
        this.mServiceName = serviceName;
        this.mPort = -1;
        this.mAcceptableLength = acceptableLength;
        this.mFragmentLength = fragmentLength;
        this.mMiu = 128;
        this.mRwSize = 1;
    }

    public void put(NdefMessage msg) throws IOException {
        SnepMessenger messenger;
        synchronized (this) {
            if (this.mState != 2) {
                throw new IOException("Socket not connected.");
            }
            messenger = this.mMessenger;
        }
        synchronized (this.mTransmissionLock) {
            try {
                try {
                    messenger.sendMessage(SnepMessage.getPutRequest(msg));
                    messenger.getMessage();
                } catch (SnepException e) {
                    throw new IOException(e);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public SnepMessage get(NdefMessage msg) throws IOException {
        SnepMessenger messenger;
        SnepMessage message;
        synchronized (this) {
            if (this.mState != 2) {
                throw new IOException("Socket not connected.");
            }
            messenger = this.mMessenger;
        }
        synchronized (this.mTransmissionLock) {
            try {
                try {
                    messenger.sendMessage(SnepMessage.getGetRequest(this.mAcceptableLength, msg));
                    message = messenger.getMessage();
                } catch (SnepException e) {
                    throw new IOException(e);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return message;
    }

    public void connect() throws IOException {
        synchronized (this) {
            if (this.mState != 0) {
                throw new IOException("Socket already in use.");
            }
            this.mState = 1;
        }
        DeviceHost.LlcpSocket socket = null;
        try {
            DeviceHost.LlcpSocket socket2 = NfcService.getInstance().createLlcpSocket(0, this.mMiu, this.mRwSize, 1024);
            if (socket2 == null) {
                throw new IOException("Could not connect to socket.");
            }
            if (this.mPort == -1) {
                socket2.connectToService(this.mServiceName);
            } else {
                socket2.connectToSap(this.mPort);
            }
            int miu = socket2.getRemoteMiu();
            int fragmentLength = this.mFragmentLength == -1 ? miu : Math.min(miu, this.mFragmentLength);
            SnepMessenger messenger = new SnepMessenger(true, socket2, fragmentLength);
            synchronized (this) {
                this.mMessenger = messenger;
                this.mState = 2;
            }
        } catch (LlcpException e) {
            synchronized (this) {
                this.mState = 0;
                throw new IOException("Could not connect to socket");
            }
        } catch (IOException e2) {
            if (0 != 0) {
                try {
                    socket.close();
                } catch (IOException e3) {
                }
            }
            synchronized (this) {
                this.mState = 0;
                throw new IOException("Failed to connect to socket");
            }
        }
    }

    public void close() {
        synchronized (this) {
            if (this.mMessenger != null) {
                try {
                    this.mMessenger.close();
                    this.mMessenger = null;
                } catch (IOException e) {
                    this.mMessenger = null;
                } catch (Throwable th) {
                    this.mMessenger = null;
                    this.mState = 0;
                    throw th;
                }
                this.mState = 0;
            }
        }
    }
}
