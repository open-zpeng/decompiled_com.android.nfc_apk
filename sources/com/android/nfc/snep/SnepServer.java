package com.android.nfc.snep;

import android.nfc.NdefMessage;
import com.android.nfc.DeviceHost;
import com.android.nfc.NfcService;
import java.io.IOException;
/* loaded from: classes.dex */
public final class SnepServer {
    private static final boolean DBG = false;
    private static final int DEFAULT_MIU = 248;
    public static final int DEFAULT_PORT = 4;
    private static final int DEFAULT_RW_SIZE = 1;
    public static final String DEFAULT_SERVICE_NAME = "urn:nfc:sn:snep";
    private static final String TAG = "SnepServer";
    final Callback mCallback;
    final int mFragmentLength;
    final int mMiu;
    final int mRwSize;
    boolean mServerRunning;
    ServerThread mServerThread;
    final String mServiceName;
    final int mServiceSap;

    /* loaded from: classes.dex */
    public interface Callback {
        SnepMessage doGet(int i, NdefMessage ndefMessage);

        SnepMessage doPut(NdefMessage ndefMessage);
    }

    public SnepServer(Callback callback) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = DEFAULT_SERVICE_NAME;
        this.mServiceSap = 4;
        this.mFragmentLength = -1;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = 1;
    }

    public SnepServer(String serviceName, int serviceSap, Callback callback) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = serviceName;
        this.mServiceSap = serviceSap;
        this.mFragmentLength = -1;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = 1;
    }

    public SnepServer(Callback callback, int miu, int rwSize) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = DEFAULT_SERVICE_NAME;
        this.mServiceSap = 4;
        this.mFragmentLength = -1;
        this.mMiu = miu;
        this.mRwSize = rwSize;
    }

    SnepServer(String serviceName, int serviceSap, int fragmentLength, Callback callback) {
        this.mServerThread = null;
        this.mServerRunning = DBG;
        this.mCallback = callback;
        this.mServiceName = serviceName;
        this.mServiceSap = serviceSap;
        this.mFragmentLength = fragmentLength;
        this.mMiu = DEFAULT_MIU;
        this.mRwSize = 1;
    }

    /* loaded from: classes.dex */
    private class ConnectionThread extends Thread {
        private final SnepMessenger mMessager;
        private final DeviceHost.LlcpSocket mSock;

        ConnectionThread(DeviceHost.LlcpSocket socket, int fragmentLength) {
            super(SnepServer.TAG);
            this.mSock = socket;
            this.mMessager = new SnepMessenger(SnepServer.DBG, socket, fragmentLength);
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            boolean running;
            try {
                try {
                    synchronized (SnepServer.this) {
                        running = SnepServer.this.mServerRunning;
                    }
                    while (running && SnepServer.handleRequest(this.mMessager, SnepServer.this.mCallback)) {
                        synchronized (SnepServer.this) {
                            running = SnepServer.this.mServerRunning;
                        }
                    }
                    this.mSock.close();
                } catch (IOException e) {
                    this.mSock.close();
                } catch (Throwable th) {
                    try {
                        this.mSock.close();
                    } catch (IOException e2) {
                    }
                    throw th;
                }
            } catch (IOException e3) {
            }
        }
    }

    static boolean handleRequest(SnepMessenger messenger, Callback callback) throws IOException {
        try {
            SnepMessage request = messenger.getMessage();
            if (((request.getVersion() & 240) >> 4) != 1) {
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_UNSUPPORTED_VERSION));
            } else if (NfcService.sIsDtaMode && (request.getLength() > 1024 || request.getLength() == -1)) {
                messenger.sendMessage(SnepMessage.getMessage((byte) -1));
            } else if (request.getField() == 1) {
                messenger.sendMessage(callback.doGet(request.getAcceptableLength(), request.getNdefMessage()));
            } else if (request.getField() == 2) {
                messenger.sendMessage(callback.doPut(request.getNdefMessage()));
            } else {
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_BAD_REQUEST));
            }
            return true;
        } catch (SnepException e) {
            try {
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_BAD_REQUEST));
                return DBG;
            } catch (IOException e2) {
                return DBG;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public class ServerThread extends Thread {
        DeviceHost.LlcpServerSocket mServerSocket;
        private boolean mThreadRunning = true;

        ServerThread() {
        }

        /* JADX WARN: Removed duplicated region for block: B:171:0x00f9 A[EXC_TOP_SPLITTER, SYNTHETIC] */
        /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:129:0x0101 -> B:130:0x0102). Please submit an issue!!! */
        @Override // java.lang.Thread, java.lang.Runnable
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        public void run() {
            /*
                Method dump skipped, instructions count: 289
                To view this dump add '--comments-level debug' option
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.snep.SnepServer.ServerThread.run():void");
        }

        public void shutdown() {
            synchronized (SnepServer.this) {
                this.mThreadRunning = SnepServer.DBG;
                if (this.mServerSocket != null) {
                    try {
                        this.mServerSocket.close();
                    } catch (IOException e) {
                    }
                    this.mServerSocket = null;
                }
            }
        }
    }

    public void start() {
        synchronized (this) {
            if (this.mServerThread == null) {
                this.mServerThread = new ServerThread();
                this.mServerThread.start();
                this.mServerRunning = true;
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (this.mServerThread != null) {
                this.mServerThread.shutdown();
                this.mServerThread = null;
                this.mServerRunning = DBG;
            }
        }
    }
}
