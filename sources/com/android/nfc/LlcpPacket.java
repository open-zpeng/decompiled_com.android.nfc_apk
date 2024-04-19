package com.android.nfc;
/* loaded from: classes.dex */
public class LlcpPacket {
    private byte[] mDataBuffer;
    private int mRemoteSap;

    public int getRemoteSap() {
        return this.mRemoteSap;
    }

    public byte[] getDataBuffer() {
        return this.mDataBuffer;
    }
}
