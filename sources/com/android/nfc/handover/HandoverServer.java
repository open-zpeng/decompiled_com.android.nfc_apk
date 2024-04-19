package com.android.nfc.handover;

import android.content.Context;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.beam.BeamManager;
import com.android.nfc.handover.HandoverDataParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
/* loaded from: classes.dex */
public final class HandoverServer {
    static final Boolean DBG = false;
    static final String HANDOVER_SERVICE_NAME = "urn:nfc:sn:handover";
    static final int MIU = 128;
    static final String TAG = "HandoverServer";
    final Callback mCallback;
    private final Context mContext;
    final HandoverDataParser mHandoverDataParser;
    final int mSap;
    ServerThread mServerThread = null;
    boolean mServerRunning = false;

    /* loaded from: classes.dex */
    public interface Callback {
        void onHandoverBusy();

        void onHandoverRequestReceived();
    }

    public HandoverServer(Context context, int sap, HandoverDataParser manager, Callback callback) {
        this.mContext = context;
        this.mSap = sap;
        this.mHandoverDataParser = manager;
        this.mCallback = callback;
    }

    public synchronized void start() {
        if (this.mServerThread == null) {
            this.mServerThread = new ServerThread();
            this.mServerThread.start();
            this.mServerRunning = true;
        }
    }

    public synchronized void stop() {
        if (this.mServerThread != null) {
            this.mServerThread.shutdown();
            this.mServerThread = null;
            this.mServerRunning = false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public class ServerThread extends Thread {
        DeviceHost.LlcpServerSocket mServerSocket;
        private boolean mThreadRunning;

        private ServerThread() {
            this.mThreadRunning = true;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            boolean threadRunning;
            boolean threadRunning2;
            DeviceHost.LlcpServerSocket serverSocket;
            synchronized (HandoverServer.this) {
                threadRunning = this.mThreadRunning;
            }
            while (threadRunning) {
                try {
                    try {
                        synchronized (HandoverServer.this) {
                            this.mServerSocket = NfcService.getInstance().createLlcpServerSocket(HandoverServer.this.mSap, HandoverServer.HANDOVER_SERVICE_NAME, 128, 1, 1024);
                        }
                    } catch (Throwable th) {
                        synchronized (HandoverServer.this) {
                            if (this.mServerSocket != null) {
                                if (HandoverServer.DBG.booleanValue()) {
                                    Log.d(HandoverServer.TAG, "about to close");
                                }
                                try {
                                    this.mServerSocket.close();
                                } catch (IOException e) {
                                }
                                this.mServerSocket = null;
                            }
                            throw th;
                        }
                    }
                } catch (LlcpException e2) {
                    Log.e(HandoverServer.TAG, "llcp error", e2);
                    synchronized (HandoverServer.this) {
                        if (this.mServerSocket != null) {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e3) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                } catch (IOException e4) {
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "IO error");
                    }
                    synchronized (HandoverServer.this) {
                        if (this.mServerSocket != null) {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e5) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                }
                if (this.mServerSocket == null) {
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "failed to create LLCP service socket");
                    }
                    synchronized (HandoverServer.this) {
                        if (this.mServerSocket != null) {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "about to close");
                            }
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e6) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                    return;
                }
                if (HandoverServer.DBG.booleanValue()) {
                    Log.d(HandoverServer.TAG, "created LLCP service socket");
                }
                synchronized (HandoverServer.this) {
                    threadRunning2 = this.mThreadRunning;
                }
                while (threadRunning2) {
                    synchronized (HandoverServer.this) {
                        serverSocket = this.mServerSocket;
                    }
                    if (serverSocket == null) {
                        if (HandoverServer.DBG.booleanValue()) {
                            Log.d(HandoverServer.TAG, "Server socket shut down.");
                        }
                        synchronized (HandoverServer.this) {
                            if (this.mServerSocket != null) {
                                if (HandoverServer.DBG.booleanValue()) {
                                    Log.d(HandoverServer.TAG, "about to close");
                                }
                                try {
                                    this.mServerSocket.close();
                                } catch (IOException e7) {
                                }
                                this.mServerSocket = null;
                            }
                        }
                        return;
                    }
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "about to accept");
                    }
                    DeviceHost.LlcpSocket communicationSocket = serverSocket.accept();
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "accept returned " + communicationSocket);
                    }
                    if (communicationSocket != null) {
                        new ConnectionThread(communicationSocket).start();
                    }
                    synchronized (HandoverServer.this) {
                        threadRunning2 = this.mThreadRunning;
                    }
                }
                if (HandoverServer.DBG.booleanValue()) {
                    Log.d(HandoverServer.TAG, "stop running");
                }
                synchronized (HandoverServer.this) {
                    if (this.mServerSocket != null) {
                        if (HandoverServer.DBG.booleanValue()) {
                            Log.d(HandoverServer.TAG, "about to close");
                        }
                        try {
                            this.mServerSocket.close();
                        } catch (IOException e8) {
                        }
                        this.mServerSocket = null;
                    }
                }
                synchronized (HandoverServer.this) {
                    threadRunning = this.mThreadRunning;
                }
            }
        }

        public void shutdown() {
            synchronized (HandoverServer.this) {
                this.mThreadRunning = false;
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

    /* loaded from: classes.dex */
    private class ConnectionThread extends Thread {
        private final DeviceHost.LlcpSocket mSock;

        ConnectionThread(DeviceHost.LlcpSocket socket) {
            super(HandoverServer.TAG);
            this.mSock = socket;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            boolean running;
            if (HandoverServer.DBG.booleanValue()) {
                Log.d(HandoverServer.TAG, "starting connection thread");
            }
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try {
                try {
                    try {
                        synchronized (HandoverServer.this) {
                            running = HandoverServer.this.mServerRunning;
                        }
                        byte[] partial = new byte[this.mSock.getLocalMiu()];
                        NdefMessage handoverRequestMsg = null;
                        while (running) {
                            int size = this.mSock.receive(partial);
                            if (size < 0) {
                                break;
                            }
                            byteStream.write(partial, 0, size);
                            try {
                                handoverRequestMsg = new NdefMessage(byteStream.toByteArray());
                            } catch (FormatException e) {
                            }
                            if (handoverRequestMsg != null) {
                                BeamManager beamManager = BeamManager.getInstance();
                                if (beamManager.isBeamInProgress()) {
                                    HandoverServer.this.mCallback.onHandoverBusy();
                                    break;
                                }
                                HandoverDataParser.IncomingHandoverData handoverData = HandoverServer.this.mHandoverDataParser.getIncomingHandoverData(handoverRequestMsg);
                                if (handoverData == null) {
                                    Log.e(HandoverServer.TAG, "Failed to create handover response");
                                    break;
                                }
                                int offset = 0;
                                byte[] buffer = handoverData.handoverSelect.toByteArray();
                                int remoteMiu = this.mSock.getRemoteMiu();
                                while (offset < buffer.length) {
                                    int length = Math.min(buffer.length - offset, remoteMiu);
                                    byte[] tmpBuffer = Arrays.copyOfRange(buffer, offset, offset + length);
                                    this.mSock.send(tmpBuffer);
                                    offset += length;
                                }
                                HandoverServer.this.mCallback.onHandoverRequestReceived();
                                if (!beamManager.startBeamReceive(HandoverServer.this.mContext, handoverData.handoverData)) {
                                    HandoverServer.this.mCallback.onHandoverBusy();
                                    break;
                                }
                                byteStream = new ByteArrayOutputStream();
                            }
                            synchronized (HandoverServer.this) {
                                running = HandoverServer.this.mServerRunning;
                            }
                        }
                        try {
                            if (HandoverServer.DBG.booleanValue()) {
                                Log.d(HandoverServer.TAG, "about to close");
                            }
                            this.mSock.close();
                        } catch (IOException e2) {
                        }
                        byteStream.close();
                    } catch (IOException e3) {
                    }
                } catch (IOException e4) {
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "IOException");
                    }
                    try {
                        if (HandoverServer.DBG.booleanValue()) {
                            Log.d(HandoverServer.TAG, "about to close");
                        }
                        this.mSock.close();
                    } catch (IOException e5) {
                    }
                    byteStream.close();
                }
                if (HandoverServer.DBG.booleanValue()) {
                    Log.d(HandoverServer.TAG, "finished connection thread");
                }
            } catch (Throwable th) {
                try {
                    if (HandoverServer.DBG.booleanValue()) {
                        Log.d(HandoverServer.TAG, "about to close");
                    }
                    this.mSock.close();
                } catch (IOException e6) {
                }
                try {
                    byteStream.close();
                } catch (IOException e7) {
                }
                throw th;
            }
        }
    }
}
