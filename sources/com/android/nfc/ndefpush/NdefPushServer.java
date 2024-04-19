package com.android.nfc.ndefpush;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
/* loaded from: classes.dex */
public class NdefPushServer {
    private static final boolean DBG = true;
    private static final int MIU = 248;
    static final String SERVICE_NAME = "com.android.npp";
    private static final String TAG = "NdefPushServer";
    final Callback mCallback;
    int mSap;
    NfcService mService = NfcService.getInstance();
    ServerThread mServerThread = null;

    /* loaded from: classes.dex */
    public interface Callback {
        void onMessageReceived(NdefMessage ndefMessage);
    }

    public NdefPushServer(int sap, Callback callback) {
        this.mSap = sap;
        this.mCallback = callback;
    }

    /* loaded from: classes.dex */
    private class ConnectionThread extends Thread {
        private DeviceHost.LlcpSocket mSock;

        ConnectionThread(DeviceHost.LlcpSocket sock) {
            super(NdefPushServer.TAG);
            this.mSock = sock;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            int size;
            Log.d(NdefPushServer.TAG, "starting connection thread");
            try {
                try {
                    try {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
                        byte[] partial = new byte[1024];
                        boolean connectionBroken = false;
                        while (!connectionBroken) {
                            try {
                                size = this.mSock.receive(partial);
                                Log.d(NdefPushServer.TAG, "read " + size + " bytes");
                            } catch (IOException e) {
                                connectionBroken = NdefPushServer.DBG;
                                Log.d(NdefPushServer.TAG, "connection broken by IOException", e);
                            }
                            if (size < 0) {
                                break;
                            }
                            buffer.write(partial, 0, size);
                        }
                        NdefPushProtocol msg = new NdefPushProtocol(buffer.toByteArray());
                        Log.d(NdefPushServer.TAG, "got message " + msg.toString());
                        NdefPushServer.this.mCallback.onMessageReceived(msg.getImmediate());
                        Log.d(NdefPushServer.TAG, "about to close");
                        this.mSock.close();
                    } catch (FormatException e2) {
                        Log.e(NdefPushServer.TAG, "badly formatted NDEF message, ignoring", e2);
                        Log.d(NdefPushServer.TAG, "about to close");
                        this.mSock.close();
                    }
                } catch (Throwable th) {
                    try {
                        Log.d(NdefPushServer.TAG, "about to close");
                        this.mSock.close();
                    } catch (IOException e3) {
                    }
                    throw th;
                }
            } catch (IOException e4) {
            }
            Log.d(NdefPushServer.TAG, "finished connection thread");
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public class ServerThread extends Thread {
        boolean mRunning = NdefPushServer.DBG;
        DeviceHost.LlcpServerSocket mServerSocket;

        ServerThread() {
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            boolean threadRunning;
            boolean threadRunning2;
            DeviceHost.LlcpServerSocket serverSocket;
            synchronized (NdefPushServer.this) {
                threadRunning = this.mRunning;
            }
            while (threadRunning) {
                Log.d(NdefPushServer.TAG, "about create LLCP service socket");
                try {
                    try {
                        try {
                            synchronized (NdefPushServer.this) {
                                this.mServerSocket = NdefPushServer.this.mService.createLlcpServerSocket(NdefPushServer.this.mSap, NdefPushServer.SERVICE_NAME, NdefPushServer.MIU, 1, 1024);
                            }
                        } catch (Throwable th) {
                            synchronized (NdefPushServer.this) {
                                if (this.mServerSocket != null) {
                                    Log.d(NdefPushServer.TAG, "about to close");
                                    try {
                                        this.mServerSocket.close();
                                    } catch (IOException e) {
                                    }
                                    this.mServerSocket = null;
                                }
                                throw th;
                            }
                        }
                    } catch (IOException e2) {
                        Log.d(NdefPushServer.TAG, "IO error");
                        synchronized (NdefPushServer.this) {
                            if (this.mServerSocket != null) {
                                Log.d(NdefPushServer.TAG, "about to close");
                                try {
                                    this.mServerSocket.close();
                                } catch (IOException e3) {
                                }
                                this.mServerSocket = null;
                            }
                        }
                    }
                } catch (LlcpException e4) {
                    Log.e(NdefPushServer.TAG, "llcp error", e4);
                    synchronized (NdefPushServer.this) {
                        if (this.mServerSocket != null) {
                            Log.d(NdefPushServer.TAG, "about to close");
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e5) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                }
                if (this.mServerSocket == null) {
                    Log.d(NdefPushServer.TAG, "failed to create LLCP service socket");
                    synchronized (NdefPushServer.this) {
                        if (this.mServerSocket != null) {
                            Log.d(NdefPushServer.TAG, "about to close");
                            try {
                                this.mServerSocket.close();
                            } catch (IOException e6) {
                            }
                            this.mServerSocket = null;
                        }
                    }
                    return;
                }
                Log.d(NdefPushServer.TAG, "created LLCP service socket");
                synchronized (NdefPushServer.this) {
                    threadRunning2 = this.mRunning;
                }
                while (threadRunning2) {
                    synchronized (NdefPushServer.this) {
                        serverSocket = this.mServerSocket;
                    }
                    if (serverSocket == null) {
                        synchronized (NdefPushServer.this) {
                            if (this.mServerSocket != null) {
                                Log.d(NdefPushServer.TAG, "about to close");
                                try {
                                    this.mServerSocket.close();
                                } catch (IOException e7) {
                                }
                                this.mServerSocket = null;
                            }
                        }
                        return;
                    }
                    Log.d(NdefPushServer.TAG, "about to accept");
                    DeviceHost.LlcpSocket communicationSocket = serverSocket.accept();
                    Log.d(NdefPushServer.TAG, "accept returned " + communicationSocket);
                    if (communicationSocket != null) {
                        new ConnectionThread(communicationSocket).start();
                    }
                    synchronized (NdefPushServer.this) {
                        threadRunning2 = this.mRunning;
                    }
                }
                Log.d(NdefPushServer.TAG, "stop running");
                synchronized (NdefPushServer.this) {
                    if (this.mServerSocket != null) {
                        Log.d(NdefPushServer.TAG, "about to close");
                        try {
                            this.mServerSocket.close();
                        } catch (IOException e8) {
                        }
                        this.mServerSocket = null;
                    }
                }
                synchronized (NdefPushServer.this) {
                    threadRunning = this.mRunning;
                }
            }
        }

        public void shutdown() {
            synchronized (NdefPushServer.this) {
                this.mRunning = false;
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
            }
        }
    }
}
