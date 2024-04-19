package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpPacket;
import java.io.IOException;
/* loaded from: classes.dex */
public class NativeLlcpConnectionlessSocket implements DeviceHost.LlcpConnectionlessSocket {
    private int mHandle;
    private int mLinkMiu;
    private int mSap;

    public native boolean doClose();

    public native LlcpPacket doReceiveFrom(int i);

    public native boolean doSendTo(int i, byte[] bArr);

    @Override // com.android.nfc.DeviceHost.LlcpConnectionlessSocket
    public int getLinkMiu() {
        return this.mLinkMiu;
    }

    @Override // com.android.nfc.DeviceHost.LlcpConnectionlessSocket
    public int getSap() {
        return this.mSap;
    }

    @Override // com.android.nfc.DeviceHost.LlcpConnectionlessSocket
    public void send(int sap, byte[] data) throws IOException {
        if (!doSendTo(sap, data)) {
            throw new IOException();
        }
    }

    @Override // com.android.nfc.DeviceHost.LlcpConnectionlessSocket
    public LlcpPacket receive() throws IOException {
        LlcpPacket packet = doReceiveFrom(this.mLinkMiu);
        if (packet == null) {
            throw new IOException();
        }
        return packet;
    }

    public int getHandle() {
        return this.mHandle;
    }

    @Override // com.android.nfc.DeviceHost.LlcpConnectionlessSocket
    public void close() throws IOException {
        if (!doClose()) {
            throw new IOException();
        }
    }
}
