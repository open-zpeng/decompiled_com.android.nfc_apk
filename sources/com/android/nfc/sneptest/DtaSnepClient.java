package com.android.nfc.sneptest;

import android.content.Context;
import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.DtaServiceConnector;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.snep.SnepException;
import com.android.nfc.snep.SnepMessage;
import com.android.nfc.snep.SnepMessenger;
import com.android.nfc.snep.SnepServer;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
/* loaded from: classes.dex */
public final class DtaSnepClient {
    private static final int CONNECTED = 2;
    private static final int CONNECTING = 1;
    private static final boolean DBG = true;
    private static final int DEFAULT_ACCEPTABLE_LENGTH = 1024;
    private static final int DEFAULT_MIU = 128;
    private static final int DEFAULT_PORT = 63;
    private static final int DEFAULT_RWSIZE = 1;
    private static final String DEFAULT_SERVICE_NAME = "urn:nfc:sn:snep";
    private static final int DISCONNECTED = 0;
    private static final String SNEP_SERVICE_NAME = "urn:nfc:sn:snep";
    private static final String TAG = "DtaSnepClient";
    public static int mTestCaseId;
    private final int mAcceptableLength;
    private final int mFragmentLength;
    SnepMessenger mMessenger;
    private final int mMiu;
    private final int mPort;
    private final int mRwSize;
    private final String mServiceName;
    private int mState;
    private final Object mTransmissionLock;

    public DtaSnepClient() {
        this.mTransmissionLock = new Object();
        this.mState = 0;
        this.mMessenger = null;
        this.mServiceName = SnepServer.DEFAULT_SERVICE_NAME;
        this.mPort = 63;
        this.mAcceptableLength = 1024;
        this.mFragmentLength = -1;
        this.mMiu = 128;
        this.mRwSize = 1;
    }

    public DtaSnepClient(String serviceName, int miu, int rwSize, int testCaseId) {
        this.mTransmissionLock = new Object();
        this.mState = 0;
        this.mMessenger = null;
        this.mServiceName = serviceName;
        this.mPort = -1;
        this.mAcceptableLength = 1024;
        this.mFragmentLength = -1;
        this.mMiu = miu;
        this.mRwSize = rwSize;
        mTestCaseId = testCaseId;
    }

    public void DtaClientOperations(Context mContext) {
        DtaServiceConnector dtaServiceConnector = new DtaServiceConnector(mContext);
        dtaServiceConnector.bindService();
        Log.d(TAG, "Connecting remote server");
        try {
            connect();
        } catch (IOException e) {
            Log.e(TAG, "Error connecting remote server");
        }
        switch (mTestCaseId) {
            case 1:
                try {
                    Log.d(TAG, "PUT Small Ndef Data");
                    put(SnepMessage.getSmallNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getSmallNdef().toString());
                } catch (UnsupportedEncodingException e2) {
                    e2.printStackTrace();
                } catch (IOException e3) {
                    e3.printStackTrace();
                }
                close();
                return;
            case 2:
                try {
                    Log.d(TAG, "PUT Small Ndef Data");
                    put(SnepMessage.getSmallNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getSmallNdef().toString());
                } catch (UnsupportedEncodingException e4) {
                    e4.printStackTrace();
                } catch (IOException e5) {
                    e5.printStackTrace();
                }
                close();
                return;
            case 3:
                try {
                    Log.d(TAG, "PUT Small Ndef Data");
                    put(SnepMessage.getSmallNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getSmallNdef().toString());
                } catch (UnsupportedEncodingException e6) {
                    e6.printStackTrace();
                } catch (IOException e7) {
                    e7.printStackTrace();
                }
                close();
                return;
            case 4:
                try {
                    Log.d(TAG, "PUT Small Ndef Data");
                    put(SnepMessage.getSmallNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getSmallNdef().toString());
                } catch (UnsupportedEncodingException e8) {
                    e8.printStackTrace();
                } catch (IOException e9) {
                    e9.printStackTrace();
                }
                close();
                return;
            case 5:
                try {
                    Log.d(TAG, "PUT Large Ndef Data");
                    put(SnepMessage.getLargeNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getLargeNdef().toString());
                } catch (UnsupportedEncodingException e10) {
                    e10.printStackTrace();
                } catch (IOException e11) {
                    e11.printStackTrace();
                }
                close();
                return;
            case 6:
                try {
                    Log.d(TAG, "PUT Large Ndef Data");
                    put(SnepMessage.getLargeNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getLargeNdef().toString());
                } catch (UnsupportedEncodingException e12) {
                    e12.printStackTrace();
                } catch (IOException e13) {
                    e13.printStackTrace();
                }
                close();
                return;
            case 7:
                try {
                    Log.d(TAG, "GET Ndef Message");
                    get(SnepMessage.getSmallNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getSmallNdef().toString());
                } catch (UnsupportedEncodingException e14) {
                    e14.printStackTrace();
                } catch (IOException e15) {
                    e15.printStackTrace();
                }
                close();
                return;
            case 8:
                try {
                    Log.d(TAG, "GET Ndef Message");
                    get(SnepMessage.getSmallNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getSmallNdef().toString());
                } catch (UnsupportedEncodingException e16) {
                    e16.printStackTrace();
                } catch (IOException e17) {
                    e17.printStackTrace();
                }
                close();
                return;
            case 9:
                try {
                    Log.d(TAG, "GET Ndef Message");
                    get(SnepMessage.getSmallNdef());
                    dtaServiceConnector.sendMessage(SnepMessage.getSmallNdef().toString());
                } catch (UnsupportedEncodingException e18) {
                    e18.printStackTrace();
                } catch (IOException e19) {
                    e19.printStackTrace();
                }
                close();
                return;
            default:
                Log.d(TAG, "Unknown test case");
                return;
        }
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
            Log.d(TAG, "about to create socket");
            DeviceHost.LlcpSocket socket2 = NfcService.getInstance().createLlcpSocket(0, this.mMiu, this.mRwSize, 1024);
            if (socket2 == null) {
                throw new IOException("Could not connect to socket.");
            }
            if (this.mPort == -1) {
                Log.d(TAG, "about to connect to service " + this.mServiceName);
                socket2.connectToService(this.mServiceName);
            } else {
                Log.d(TAG, "about to connect to port " + this.mPort);
                socket2.connectToSap(this.mPort);
            }
            int miu = socket2.getRemoteMiu();
            int fragmentLength = this.mFragmentLength == -1 ? miu : Math.min(miu, this.mFragmentLength);
            SnepMessenger messenger = new SnepMessenger(DBG, socket2, fragmentLength);
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
