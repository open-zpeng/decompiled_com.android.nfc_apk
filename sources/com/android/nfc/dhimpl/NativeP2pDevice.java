package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost;
/* loaded from: classes.dex */
public class NativeP2pDevice implements DeviceHost.NfcDepEndpoint {
    private byte[] mGeneralBytes;
    private int mHandle;
    private byte mLlcpVersion;
    private int mMode;

    private native boolean doConnect();

    private native boolean doDisconnect();

    private native byte[] doReceive();

    private native boolean doSend(byte[] bArr);

    public native byte[] doTransceive(byte[] bArr);

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public byte[] receive() {
        return doReceive();
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public boolean send(byte[] data) {
        return doSend(data);
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public boolean connect() {
        return doConnect();
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public boolean disconnect() {
        return doDisconnect();
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public byte[] transceive(byte[] data) {
        return doTransceive(data);
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public int getHandle() {
        return this.mHandle;
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public int getMode() {
        return this.mMode;
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public byte[] getGeneralBytes() {
        return this.mGeneralBytes;
    }

    @Override // com.android.nfc.DeviceHost.NfcDepEndpoint
    public byte getLlcpVersion() {
        return this.mLlcpVersion;
    }
}
