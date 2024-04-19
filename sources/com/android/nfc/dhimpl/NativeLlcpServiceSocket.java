package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost;
import java.io.IOException;
/* loaded from: classes.dex */
public class NativeLlcpServiceSocket implements DeviceHost.LlcpServerSocket {
    private int mHandle;
    private int mLocalLinearBufferLength;
    private int mLocalMiu;
    private int mLocalRw;
    private int mSap;
    private String mServiceName;

    private native NativeLlcpSocket doAccept(int i, int i2, int i3);

    private native boolean doClose();

    @Override // com.android.nfc.DeviceHost.LlcpServerSocket
    public DeviceHost.LlcpSocket accept() throws IOException {
        DeviceHost.LlcpSocket socket = doAccept(this.mLocalMiu, this.mLocalRw, this.mLocalLinearBufferLength);
        if (socket == null) {
            throw new IOException();
        }
        return socket;
    }

    @Override // com.android.nfc.DeviceHost.LlcpServerSocket
    public void close() throws IOException {
        if (!doClose()) {
            throw new IOException();
        }
    }
}
