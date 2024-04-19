package com.android.nfc.echoserver;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.LlcpPacket;
import com.android.nfc.NfcService;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
/* loaded from: classes.dex */
public class EchoServer {
    static final String CONNECTIONLESS_SERVICE_NAME = "urn:nfc:sn:cl-echo";
    static final String CONNECTION_SERVICE_NAME = "urn:nfc:sn:co-echo";
    static boolean DBG = true;
    static final int DEFAULT_CL_SAP = 18;
    static final int DEFAULT_CO_SAP = 17;
    static final int MIU = 128;
    static final String TAG = "EchoServer";
    ConnectionlessServerThread mConnectionlessServerThread;
    ServerThread mServerThread;
    NfcService mService = NfcService.getInstance();

    /* loaded from: classes.dex */
    public interface WriteCallback {
        void write(byte[] bArr);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public static class EchoMachine implements Handler.Callback {
        static final int ECHO_DELAY_IN_MS = 2000;
        static final int ECHO_MIU = 128;
        static final int QUEUE_SIZE = 2;
        final WriteCallback callback;
        final boolean dumpWhenFull;
        boolean shutdown = false;
        final BlockingQueue<byte[]> dataQueue = new LinkedBlockingQueue(2);
        final Handler handler = new Handler(this);

        EchoMachine(WriteCallback callback, boolean dumpWhenFull) {
            this.callback = callback;
            this.dumpWhenFull = dumpWhenFull;
        }

        public void pushUnit(byte[] unit, int size) {
            if (this.dumpWhenFull && this.dataQueue.remainingCapacity() == 0) {
                if (EchoServer.DBG) {
                    Log.d(EchoServer.TAG, "Dumping data unit");
                    return;
                }
                return;
            }
            int sizeLeft = size;
            int offset = 0;
            try {
                if (this.dataQueue.isEmpty()) {
                    this.handler.sendMessageDelayed(this.handler.obtainMessage(), 2000L);
                }
                if (sizeLeft == 0) {
                    this.dataQueue.put(new byte[0]);
                }
                while (sizeLeft > 0) {
                    int minSize = Math.min(size, 128);
                    byte[] data = new byte[minSize];
                    System.arraycopy(unit, offset, data, 0, minSize);
                    this.dataQueue.put(data);
                    sizeLeft -= minSize;
                    offset += minSize;
                }
            } catch (InterruptedException e) {
            }
        }

        public synchronized void shutdown() {
            this.dataQueue.clear();
            this.shutdown = true;
        }

        @Override // android.os.Handler.Callback
        public synchronized boolean handleMessage(Message msg) {
            if (this.shutdown) {
                return true;
            }
            while (!this.dataQueue.isEmpty()) {
                this.callback.write(this.dataQueue.remove());
            }
            return true;
        }
    }

    /* loaded from: classes.dex */
    public class ServerThread extends Thread implements WriteCallback {
        DeviceHost.LlcpSocket clientSocket;
        DeviceHost.LlcpServerSocket serverSocket;
        boolean running = true;
        final EchoMachine echoMachine = new EchoMachine(this, false);

        public ServerThread() {
        }

        private void handleClient(DeviceHost.LlcpSocket socket) {
            int size;
            boolean connectionBroken = false;
            byte[] dataUnit = new byte[1024];
            while (!connectionBroken) {
                try {
                    size = socket.receive(dataUnit);
                    if (EchoServer.DBG) {
                        Log.d(EchoServer.TAG, "read " + size + " bytes");
                    }
                } catch (IOException e) {
                    connectionBroken = true;
                    if (EchoServer.DBG) {
                        Log.d(EchoServer.TAG, "connection broken by IOException", e);
                    }
                }
                if (size >= 0) {
                    this.echoMachine.pushUnit(dataUnit, size);
                } else {
                    return;
                }
            }
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            if (EchoServer.DBG) {
                Log.d(EchoServer.TAG, "about create LLCP service socket");
            }
            try {
                this.serverSocket = EchoServer.this.mService.createLlcpServerSocket(17, EchoServer.CONNECTION_SERVICE_NAME, 128, 1, 1024);
                if (this.serverSocket == null) {
                    if (EchoServer.DBG) {
                        Log.d(EchoServer.TAG, "failed to create LLCP service socket");
                        return;
                    }
                    return;
                }
                if (EchoServer.DBG) {
                    Log.d(EchoServer.TAG, "created LLCP service socket");
                }
                while (this.running) {
                    try {
                        if (EchoServer.DBG) {
                            Log.d(EchoServer.TAG, "about to accept");
                        }
                        this.clientSocket = this.serverSocket.accept();
                        if (EchoServer.DBG) {
                            Log.d(EchoServer.TAG, "accept returned " + this.clientSocket);
                        }
                        handleClient(this.clientSocket);
                    } catch (LlcpException e) {
                        Log.e(EchoServer.TAG, "llcp error", e);
                        this.running = false;
                    } catch (IOException e2) {
                        Log.e(EchoServer.TAG, "IO error", e2);
                        this.running = false;
                    }
                }
                this.echoMachine.shutdown();
                try {
                    this.clientSocket.close();
                } catch (IOException e3) {
                }
                this.clientSocket = null;
                try {
                    this.serverSocket.close();
                } catch (IOException e4) {
                }
                this.serverSocket = null;
            } catch (LlcpException e5) {
            }
        }

        @Override // com.android.nfc.echoserver.EchoServer.WriteCallback
        public void write(byte[] data) {
            DeviceHost.LlcpSocket llcpSocket = this.clientSocket;
            if (llcpSocket != null) {
                try {
                    llcpSocket.send(data);
                    Log.e(EchoServer.TAG, "Send success!");
                } catch (IOException e) {
                    Log.e(EchoServer.TAG, "Send failed.");
                }
            }
        }

        public void shutdown() {
            this.running = false;
            DeviceHost.LlcpServerSocket llcpServerSocket = this.serverSocket;
            if (llcpServerSocket != null) {
                try {
                    llcpServerSocket.close();
                } catch (IOException e) {
                }
                this.serverSocket = null;
            }
        }
    }

    /* loaded from: classes.dex */
    public class ConnectionlessServerThread extends Thread implements WriteCallback {
        int mRemoteSap;
        DeviceHost.LlcpConnectionlessSocket socket;
        boolean mRunning = true;
        final EchoMachine echoMachine = new EchoMachine(this, true);

        public ConnectionlessServerThread() {
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            LlcpPacket packet;
            boolean connectionBroken = false;
            if (EchoServer.DBG) {
                Log.d(EchoServer.TAG, "about create LLCP connectionless socket");
            }
            try {
                try {
                    try {
                        this.socket = EchoServer.this.mService.createLlcpConnectionLessSocket(18, EchoServer.CONNECTIONLESS_SERVICE_NAME);
                    } catch (LlcpException e) {
                        Log.e(EchoServer.TAG, "llcp error", e);
                        this.echoMachine.shutdown();
                        DeviceHost.LlcpConnectionlessSocket llcpConnectionlessSocket = this.socket;
                        if (llcpConnectionlessSocket == null) {
                            return;
                        }
                        llcpConnectionlessSocket.close();
                    }
                    if (this.socket == null) {
                        if (EchoServer.DBG) {
                            Log.d(EchoServer.TAG, "failed to create LLCP connectionless socket");
                        }
                        this.echoMachine.shutdown();
                        DeviceHost.LlcpConnectionlessSocket llcpConnectionlessSocket2 = this.socket;
                        if (llcpConnectionlessSocket2 != null) {
                            try {
                                llcpConnectionlessSocket2.close();
                                return;
                            } catch (IOException e2) {
                                return;
                            }
                        }
                        return;
                    }
                    while (this.mRunning && !connectionBroken) {
                        try {
                            packet = this.socket.receive();
                        } catch (IOException e3) {
                            connectionBroken = true;
                            if (EchoServer.DBG) {
                                Log.d(EchoServer.TAG, "connection broken by IOException", e3);
                            }
                        }
                        if (packet == null || packet.getDataBuffer() == null) {
                            break;
                        }
                        byte[] dataUnit = packet.getDataBuffer();
                        int size = dataUnit.length;
                        if (EchoServer.DBG) {
                            Log.d(EchoServer.TAG, "read " + packet.getDataBuffer().length + " bytes");
                        }
                        if (size < 0) {
                            break;
                        }
                        this.mRemoteSap = packet.getRemoteSap();
                        this.echoMachine.pushUnit(dataUnit, size);
                    }
                    this.echoMachine.shutdown();
                    DeviceHost.LlcpConnectionlessSocket llcpConnectionlessSocket3 = this.socket;
                    if (llcpConnectionlessSocket3 != null) {
                        llcpConnectionlessSocket3.close();
                    }
                } catch (Throwable th) {
                    this.echoMachine.shutdown();
                    DeviceHost.LlcpConnectionlessSocket llcpConnectionlessSocket4 = this.socket;
                    if (llcpConnectionlessSocket4 != null) {
                        try {
                            llcpConnectionlessSocket4.close();
                        } catch (IOException e4) {
                        }
                    }
                    throw th;
                }
            } catch (IOException e5) {
            }
        }

        public void shutdown() {
            this.mRunning = false;
        }

        @Override // com.android.nfc.echoserver.EchoServer.WriteCallback
        public void write(byte[] data) {
            try {
                this.socket.send(this.mRemoteSap, data);
            } catch (IOException e) {
                if (EchoServer.DBG) {
                    Log.d(EchoServer.TAG, "Error writing data.");
                }
            }
        }
    }

    public void onLlcpActivated() {
        synchronized (this) {
            if (this.mConnectionlessServerThread == null) {
                this.mConnectionlessServerThread = new ConnectionlessServerThread();
                this.mConnectionlessServerThread.start();
            }
        }
    }

    public void onLlcpDeactivated() {
        synchronized (this) {
            if (this.mConnectionlessServerThread != null) {
                this.mConnectionlessServerThread.shutdown();
                this.mConnectionlessServerThread = null;
            }
        }
    }

    public void start() {
        synchronized (this) {
            if (this.mServerThread == null) {
                this.mServerThread = new ServerThread();
                this.mServerThread.start();
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (this.mServerThread != null) {
                this.mServerThread.shutdown();
                this.mServerThread = null;
            }
        }
    }
}
