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
import java.io.IOException;
/* loaded from: classes.dex */
public final class ExtDtaSnepServer {
    private static final boolean DBG = true;
    public static final int DEFAULT_PORT = 5;
    public static final String DEFAULT_SERVICE_NAME = "urn:nfc:sn:sneptest";
    public static final String EXTENDED_SNEP_DTA_SERVICE_NAME = "urn:nfc:sn:sneptest";
    private static final String TAG = "ExtDtaSnepServer";
    static DtaServiceConnector dtaServiceConnector;
    public static Context mContext;
    public static int mTestCaseId;
    final int mDtaMiu;
    final int mDtaRwSize;
    final String mDtaServiceName;
    final int mDtaServiceSap;
    final Callback mExtDtaSnepServerCallback;
    ServerThread mServerThread = null;
    boolean mServerRunning = false;
    final int mDtaFragmentLength = -1;

    /* loaded from: classes.dex */
    public interface Callback {
        SnepMessage doGet(int i, NdefMessage ndefMessage);

        SnepMessage doPut(NdefMessage ndefMessage);
    }

    public ExtDtaSnepServer(String serviceName, int serviceSap, int miu, int rwSize, Callback callback, Context mContext2, int testCaseId) {
        this.mExtDtaSnepServerCallback = callback;
        this.mDtaServiceName = serviceName;
        this.mDtaServiceSap = serviceSap;
        this.mDtaMiu = miu;
        this.mDtaRwSize = rwSize;
        mTestCaseId = testCaseId;
        dtaServiceConnector = new DtaServiceConnector(mContext2);
        dtaServiceConnector.bindService();
    }

    /* loaded from: classes.dex */
    private class ConnectionThread extends Thread {
        private final SnepMessenger mMessager;
        private final DeviceHost.LlcpSocket mSock;

        ConnectionThread(DeviceHost.LlcpSocket socket, int fragmentLength) {
            super(ExtDtaSnepServer.TAG);
            this.mSock = socket;
            this.mMessager = new SnepMessenger(false, socket, fragmentLength);
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            boolean running;
            Log.d(ExtDtaSnepServer.TAG, "starting connection thread");
            try {
                try {
                    try {
                        synchronized (ExtDtaSnepServer.this) {
                            running = ExtDtaSnepServer.this.mServerRunning;
                        }
                        while (running && ExtDtaSnepServer.handleRequest(this.mMessager, ExtDtaSnepServer.this.mExtDtaSnepServerCallback)) {
                            synchronized (ExtDtaSnepServer.this) {
                                running = ExtDtaSnepServer.this.mServerRunning;
                            }
                        }
                        Log.d(ExtDtaSnepServer.TAG, "about to close");
                        this.mSock.close();
                    } catch (IOException e) {
                    }
                } catch (IOException e2) {
                    Log.e(ExtDtaSnepServer.TAG, "Closing from IOException");
                    Log.d(ExtDtaSnepServer.TAG, "about to close");
                    this.mSock.close();
                }
                Log.d(ExtDtaSnepServer.TAG, "finished connection thread");
            } catch (Throwable th) {
                try {
                    Log.d(ExtDtaSnepServer.TAG, "about to close");
                    this.mSock.close();
                } catch (IOException e3) {
                }
                throw th;
            }
        }
    }

    static boolean handleRequest(SnepMessenger messenger, Callback callback) throws IOException {
        try {
            SnepMessage request = messenger.getMessage();
            if (((request.getVersion() & 240) >> 4) != 1) {
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_UNSUPPORTED_VERSION));
            } else if (request.getLength() > 1024 || request.getLength() == -1) {
                Log.d(TAG, "Bad requested length");
                messenger.sendMessage(SnepMessage.getMessage((byte) -1));
            } else if (request.getField() == 1) {
                Log.d(TAG, "getting message " + request.toString());
                messenger.sendMessage(callback.doGet(request.getAcceptableLength(), request.getNdefMessage()));
                if (request.getNdefMessage() != null) {
                    dtaServiceConnector.sendMessage(request.getNdefMessage().toString());
                }
            } else if (request.getField() == 2) {
                Log.d(TAG, "putting message " + request.toString());
                messenger.sendMessage(callback.doPut(request.getNdefMessage()));
                if (request.getNdefMessage() != null) {
                    dtaServiceConnector.sendMessage(request.getNdefMessage().toString());
                }
            } else {
                Log.d(TAG, "Unknown request (" + ((int) request.getField()) + ")");
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_BAD_REQUEST));
            }
            return DBG;
        } catch (SnepException e) {
            Log.w(TAG, "Bad snep message", e);
            try {
                messenger.sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_BAD_REQUEST));
                return false;
            } catch (IOException e2) {
                return false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public class ServerThread extends Thread {
        DeviceHost.LlcpServerSocket mServerSocket;
        private boolean mThreadRunning = ExtDtaSnepServer.DBG;

        ServerThread() {
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            boolean threadRunning;
            boolean threadRunning2;
            DeviceHost.LlcpServerSocket serverSocket;
            synchronized (ExtDtaSnepServer.this) {
                threadRunning = this.mThreadRunning;
            }
            while (threadRunning) {
                Log.d(ExtDtaSnepServer.TAG, "about create LLCP service socket");
                try {
                    try {
                        synchronized (ExtDtaSnepServer.this) {
                            this.mServerSocket = NfcService.getInstance().createLlcpServerSocket(ExtDtaSnepServer.this.mDtaServiceSap, ExtDtaSnepServer.this.mDtaServiceName, ExtDtaSnepServer.this.mDtaMiu, ExtDtaSnepServer.this.mDtaRwSize, 1024);
                        }
                    } catch (Throwable th) {
                        synchronized (ExtDtaSnepServer.this) {
                            if (this.mServerSocket != null) {
                                Log.d(ExtDtaSnepServer.TAG, "about to close");
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
                    Log.e(ExtDtaSnepServer.TAG, "llcp error", e2);
                    synchronized (ExtDtaSnepServer.this) {
                        if (this.mServerSocket != null) {
                            Log.d(ExtDtaSnepServer.TAG, "about to close");
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e3) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                } catch (IOException e4) {
                    Log.e(ExtDtaSnepServer.TAG, "IO error", e4);
                    synchronized (ExtDtaSnepServer.this) {
                        if (this.mServerSocket != null) {
                            Log.d(ExtDtaSnepServer.TAG, "about to close");
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e5) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                }
                if (this.mServerSocket == null) {
                    Log.d(ExtDtaSnepServer.TAG, "failed to create LLCP service socket");
                    synchronized (ExtDtaSnepServer.this) {
                        if (this.mServerSocket != null) {
                            Log.d(ExtDtaSnepServer.TAG, "about to close");
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e6) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                    return;
                }
                Log.d(ExtDtaSnepServer.TAG, "created LLCP service socket");
                synchronized (ExtDtaSnepServer.this) {
                    threadRunning2 = this.mThreadRunning;
                }
                while (threadRunning2) {
                    synchronized (ExtDtaSnepServer.this) {
                        serverSocket = this.mServerSocket;
                    }
                    if (serverSocket == null) {
                        Log.d(ExtDtaSnepServer.TAG, "Server socket shut down.");
                        synchronized (ExtDtaSnepServer.this) {
                            if (this.mServerSocket != null) {
                                Log.d(ExtDtaSnepServer.TAG, "about to close");
                                try {
                                    this.mServerSocket.close();
                                } catch (IOException e7) {
                                }
                                this.mServerSocket = null;
                            }
                        }
                        return;
                    }
                    Log.d(ExtDtaSnepServer.TAG, "about to accept");
                    DeviceHost.LlcpSocket communicationSocket = serverSocket.accept();
                    Log.d(ExtDtaSnepServer.TAG, "accept returned " + communicationSocket);
                    if (communicationSocket != null) {
                        int miu = communicationSocket.getRemoteMiu();
                        int fragmentLength = ExtDtaSnepServer.this.mDtaFragmentLength == -1 ? miu : Math.min(miu, ExtDtaSnepServer.this.mDtaFragmentLength);
                        new ConnectionThread(communicationSocket, fragmentLength).start();
                    }
                    synchronized (ExtDtaSnepServer.this) {
                        threadRunning2 = this.mThreadRunning;
                    }
                }
                Log.d(ExtDtaSnepServer.TAG, "stop running");
                synchronized (ExtDtaSnepServer.this) {
                    if (this.mServerSocket != null) {
                        Log.d(ExtDtaSnepServer.TAG, "about to close");
                        try {
                            this.mServerSocket.close();
                        } catch (IOException e8) {
                        }
                        this.mServerSocket = null;
                    }
                }
                synchronized (ExtDtaSnepServer.this) {
                    threadRunning = this.mThreadRunning;
                }
            }
        }

        public void shutdown() {
            synchronized (ExtDtaSnepServer.this) {
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

    public void start() {
        synchronized (this) {
            Log.d(TAG, "start, thread = " + this.mServerThread);
            if (this.mServerThread == null) {
                Log.d(TAG, "starting new server thread");
                this.mServerThread = new ServerThread();
                this.mServerThread.start();
                this.mServerRunning = DBG;
            }
        }
    }

    public void stop() {
        synchronized (this) {
            Log.d(TAG, "stop, thread = " + this.mServerThread);
            if (this.mServerThread != null) {
                Log.d(TAG, "shuting down server thread");
                this.mServerThread.shutdown();
                this.mServerThread = null;
                this.mServerRunning = false;
            }
        }
    }
}
