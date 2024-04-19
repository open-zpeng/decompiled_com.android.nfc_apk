package com.android.nfc.dhimpl;

import android.content.Context;
import android.nfc.ErrorCodes;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;
import com.android.nfc.NfcDiscoveryParameters;
import java.io.FileDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
/* loaded from: classes.dex */
public class NativeNfcManager implements DeviceHost {
    static final int DEFAULT_LLCP_MIU = 1980;
    static final int DEFAULT_LLCP_RWSIZE = 2;
    static final String DRIVER_NAME = "android-nci";
    static final String PREF = "NciDeviceHost";
    private static final String TAG = "NativeNfcManager";
    private final Context mContext;
    private int mIsoDepMaxTransceiveLength;
    private final DeviceHost.DeviceHostListener mListener;
    private long mNative;
    private final Object mLock = new Object();
    private final HashMap<Integer, byte[]> mT3tIdentifiers = new HashMap<>();

    private native NativeLlcpConnectionlessSocket doCreateLlcpConnectionlessSocket(int i, String str);

    private native NativeLlcpServiceSocket doCreateLlcpServiceSocket(int i, String str, int i2, int i3, int i4);

    private native NativeLlcpSocket doCreateLlcpSocket(int i, int i2, int i3, int i4);

    private native boolean doDeinitialize();

    private native void doDisableDtaMode();

    private native void doDisableScreenOffSuspend();

    private native boolean doDownload();

    private native void doDump(FileDescriptor fileDescriptor);

    private native void doEnableDiscovery(int i, boolean z, boolean z2, boolean z3, boolean z4, boolean z5);

    private native void doEnableDtaMode();

    private native void doEnableScreenOffSuspend();

    private native void doFactoryReset();

    private native int doGetTimeout(int i);

    private native boolean doInitialize();

    private native void doResetTimeouts();

    private native boolean doSetNfcSecure(boolean z);

    private native void doSetP2pInitiatorModes(int i);

    private native void doSetP2pTargetModes(int i);

    private native boolean doSetTimeout(int i, int i2);

    private native void doShutdown();

    private native int getIsoDepMaxTransceiveLength();

    @Override // com.android.nfc.DeviceHost
    public native boolean commitRouting();

    @Override // com.android.nfc.DeviceHost
    public native void disableDiscovery();

    @Override // com.android.nfc.DeviceHost
    public native void doAbort(String str);

    @Override // com.android.nfc.DeviceHost
    public native boolean doActivateLlcp();

    @Override // com.android.nfc.DeviceHost
    public native boolean doCheckLlcp();

    public native void doDeregisterT3tIdentifier(int i);

    public native int doGetLastError();

    public native int doRegisterT3tIdentifier(byte[] bArr);

    @Override // com.android.nfc.DeviceHost
    public native void doSetScreenState(int i);

    @Override // com.android.nfc.DeviceHost
    public native int getAidTableSize();

    @Override // com.android.nfc.DeviceHost
    public native int getLfT3tMax();

    @Override // com.android.nfc.DeviceHost
    public native int getNciVersion();

    public native boolean initializeNativeStructure();

    @Override // com.android.nfc.DeviceHost
    public native boolean routeAid(byte[] bArr, int i, int i2);

    @Override // com.android.nfc.DeviceHost
    public native boolean sendRawFrame(byte[] bArr);

    @Override // com.android.nfc.DeviceHost
    public native boolean unrouteAid(byte[] bArr);

    static {
        System.loadLibrary("nfc_nci_jni");
    }

    public NativeNfcManager(Context context, DeviceHost.DeviceHostListener listener) {
        this.mListener = listener;
        initializeNativeStructure();
        this.mContext = context;
    }

    @Override // com.android.nfc.DeviceHost
    public boolean checkFirmware() {
        return doDownload();
    }

    @Override // com.android.nfc.DeviceHost
    public boolean initialize() {
        boolean ret = doInitialize();
        this.mIsoDepMaxTransceiveLength = getIsoDepMaxTransceiveLength();
        return ret;
    }

    @Override // com.android.nfc.DeviceHost
    public void enableDtaMode() {
        doEnableDtaMode();
    }

    @Override // com.android.nfc.DeviceHost
    public void disableDtaMode() {
        Log.d(TAG, "disableDtaMode : entry");
        doDisableDtaMode();
    }

    @Override // com.android.nfc.DeviceHost
    public void factoryReset() {
        doFactoryReset();
    }

    @Override // com.android.nfc.DeviceHost
    public void shutdown() {
        doShutdown();
    }

    @Override // com.android.nfc.DeviceHost
    public boolean deinitialize() {
        return doDeinitialize();
    }

    @Override // com.android.nfc.DeviceHost
    public String getName() {
        return DRIVER_NAME;
    }

    @Override // com.android.nfc.DeviceHost
    public void registerT3tIdentifier(byte[] t3tIdentifier) {
        synchronized (this.mLock) {
            int handle = doRegisterT3tIdentifier(t3tIdentifier);
            if (handle != 65535) {
                this.mT3tIdentifiers.put(Integer.valueOf(handle), t3tIdentifier);
            }
        }
    }

    @Override // com.android.nfc.DeviceHost
    public void deregisterT3tIdentifier(byte[] t3tIdentifier) {
        synchronized (this.mLock) {
            Iterator<Integer> it = this.mT3tIdentifiers.keySet().iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                int handle = it.next().intValue();
                byte[] value = this.mT3tIdentifiers.get(Integer.valueOf(handle));
                if (Arrays.equals(value, t3tIdentifier)) {
                    doDeregisterT3tIdentifier(handle);
                    this.mT3tIdentifiers.remove(Integer.valueOf(handle));
                    break;
                }
            }
        }
    }

    @Override // com.android.nfc.DeviceHost
    public void clearT3tIdentifiersCache() {
        synchronized (this.mLock) {
            this.mT3tIdentifiers.clear();
        }
    }

    @Override // com.android.nfc.DeviceHost
    public void enableDiscovery(NfcDiscoveryParameters params, boolean restart) {
        doEnableDiscovery(params.getTechMask(), params.shouldEnableLowPowerDiscovery(), params.shouldEnableReaderMode(), params.shouldEnableHostRouting(), params.shouldEnableP2p(), restart);
    }

    @Override // com.android.nfc.DeviceHost
    public DeviceHost.LlcpConnectionlessSocket createLlcpConnectionlessSocket(int nSap, String sn) throws LlcpException {
        DeviceHost.LlcpConnectionlessSocket socket = doCreateLlcpConnectionlessSocket(nSap, sn);
        if (socket != null) {
            return socket;
        }
        int error = doGetLastError();
        Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));
        if (error == -12 || error == -9) {
            throw new LlcpException(error);
        }
        throw new LlcpException(-10);
    }

    @Override // com.android.nfc.DeviceHost
    public DeviceHost.LlcpServerSocket createLlcpServerSocket(int nSap, String sn, int miu, int rw, int linearBufferLength) throws LlcpException {
        DeviceHost.LlcpServerSocket socket = doCreateLlcpServiceSocket(nSap, sn, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        }
        int error = doGetLastError();
        Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));
        if (error == -12 || error == -9) {
            throw new LlcpException(error);
        }
        throw new LlcpException(-10);
    }

    @Override // com.android.nfc.DeviceHost
    public DeviceHost.LlcpSocket createLlcpSocket(int sap, int miu, int rw, int linearBufferLength) throws LlcpException {
        DeviceHost.LlcpSocket socket = doCreateLlcpSocket(sap, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        }
        int error = doGetLastError();
        Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));
        if (error == -12 || error == -9) {
            throw new LlcpException(error);
        }
        throw new LlcpException(-10);
    }

    @Override // com.android.nfc.DeviceHost
    public void resetTimeouts() {
        doResetTimeouts();
    }

    @Override // com.android.nfc.DeviceHost
    public boolean setTimeout(int tech, int timeout) {
        return doSetTimeout(tech, timeout);
    }

    @Override // com.android.nfc.DeviceHost
    public int getTimeout(int tech) {
        return doGetTimeout(tech);
    }

    @Override // com.android.nfc.DeviceHost
    public boolean canMakeReadOnly(int ndefType) {
        return ndefType == 1 || ndefType == 2;
    }

    @Override // com.android.nfc.DeviceHost
    public int getMaxTransceiveLength(int technology) {
        if (technology == 1 || technology == 2) {
            return 253;
        }
        if (technology != 3) {
            if (technology != 4) {
                return (technology == 5 || technology == 8 || technology == 9) ? 253 : 0;
            }
            return 255;
        }
        return this.mIsoDepMaxTransceiveLength;
    }

    @Override // com.android.nfc.DeviceHost
    public void setP2pInitiatorModes(int modes) {
        doSetP2pInitiatorModes(modes);
    }

    @Override // com.android.nfc.DeviceHost
    public void setP2pTargetModes(int modes) {
        doSetP2pTargetModes(modes);
    }

    @Override // com.android.nfc.DeviceHost
    public boolean getExtendedLengthApdusSupported() {
        if (getMaxTransceiveLength(3) > 261) {
            return true;
        }
        return false;
    }

    @Override // com.android.nfc.DeviceHost
    public int getDefaultLlcpMiu() {
        return DEFAULT_LLCP_MIU;
    }

    @Override // com.android.nfc.DeviceHost
    public int getDefaultLlcpRwSize() {
        return 2;
    }

    @Override // com.android.nfc.DeviceHost
    public void dump(FileDescriptor fd) {
        doDump(fd);
    }

    @Override // com.android.nfc.DeviceHost
    public boolean enableScreenOffSuspend() {
        doEnableScreenOffSuspend();
        return true;
    }

    @Override // com.android.nfc.DeviceHost
    public boolean disableScreenOffSuspend() {
        doDisableScreenOffSuspend();
        return true;
    }

    @Override // com.android.nfc.DeviceHost
    public boolean setNfcSecure(boolean enable) {
        return doSetNfcSecure(enable);
    }

    private void notifyNdefMessageListeners(NativeNfcTag tag) {
        this.mListener.onRemoteEndpointDiscovered(tag);
    }

    private void notifyLlcpLinkActivation(NativeP2pDevice device) {
        this.mListener.onLlcpLinkActivated(device);
    }

    private void notifyLlcpLinkDeactivated(NativeP2pDevice device) {
        this.mListener.onLlcpLinkDeactivated(device);
    }

    private void notifyLlcpLinkFirstPacketReceived(NativeP2pDevice device) {
        this.mListener.onLlcpFirstPacketReceived(device);
    }

    private void notifyHostEmuActivated(int technology) {
        this.mListener.onHostCardEmulationActivated(technology);
    }

    private void notifyHostEmuData(int technology, byte[] data) {
        this.mListener.onHostCardEmulationData(technology, data);
    }

    private void notifyHostEmuDeactivated(int technology) {
        this.mListener.onHostCardEmulationDeactivated(technology);
    }

    private void notifyRfFieldActivated() {
        this.mListener.onRemoteFieldActivated();
    }

    private void notifyRfFieldDeactivated() {
        this.mListener.onRemoteFieldDeactivated();
    }

    private void notifyTransactionListeners(byte[] aid, byte[] data, String evtSrc) {
        this.mListener.onNfcTransactionEvent(aid, data, evtSrc);
    }

    private void notifyEeUpdated() {
        this.mListener.onEeUpdated();
    }
}
