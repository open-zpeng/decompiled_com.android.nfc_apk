package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost;
import java.io.IOException;
/* loaded from: classes.dex */
public class NativeLlcpSocket implements DeviceHost.LlcpSocket {
    private int mHandle;
    private int mLocalMiu;
    private int mLocalRw;
    private int mSap;

    private native boolean doClose();

    private native boolean doConnect(int i);

    private native boolean doConnectBy(String str);

    private native int doGetRemoteSocketMiu();

    private native int doGetRemoteSocketRw();

    private native int doReceive(byte[] bArr);

    private native boolean doSend(byte[] bArr);

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public void connectToSap(int sap) throws IOException {
        if (!doConnect(sap)) {
            throw new IOException();
        }
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public void connectToService(String serviceName) throws IOException {
        if (!doConnectBy(serviceName)) {
            throw new IOException();
        }
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public void close() throws IOException {
        if (!doClose()) {
            throw new IOException();
        }
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public void send(byte[] data) throws IOException {
        if (!doSend(data)) {
            throw new IOException();
        }
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public int receive(byte[] recvBuff) throws IOException {
        int receiveLength = doReceive(recvBuff);
        if (receiveLength == -1) {
            throw new IOException();
        }
        return receiveLength;
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public int getRemoteMiu() {
        return doGetRemoteSocketMiu();
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public int getRemoteRw() {
        return doGetRemoteSocketRw();
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public int getLocalSap() {
        return this.mSap;
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public int getLocalMiu() {
        return this.mLocalMiu;
    }

    @Override // com.android.nfc.DeviceHost.LlcpSocket
    public int getLocalRw() {
        return this.mLocalRw;
    }
}
