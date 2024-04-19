package com.android.nfc;

import android.nfc.NdefMessage;
import android.os.Bundle;
import java.io.FileDescriptor;
import java.io.IOException;
/* loaded from: classes.dex */
public interface DeviceHost {

    /* loaded from: classes.dex */
    public interface DeviceHostListener {
        void onEeUpdated();

        void onHostCardEmulationActivated(int i);

        void onHostCardEmulationData(int i, byte[] bArr);

        void onHostCardEmulationDeactivated(int i);

        void onLlcpFirstPacketReceived(NfcDepEndpoint nfcDepEndpoint);

        void onLlcpLinkActivated(NfcDepEndpoint nfcDepEndpoint);

        void onLlcpLinkDeactivated(NfcDepEndpoint nfcDepEndpoint);

        void onNfcTransactionEvent(byte[] bArr, byte[] bArr2, String str);

        void onRemoteEndpointDiscovered(TagEndpoint tagEndpoint);

        void onRemoteFieldActivated();

        void onRemoteFieldDeactivated();
    }

    /* loaded from: classes.dex */
    public interface LlcpConnectionlessSocket {
        void close() throws IOException;

        int getLinkMiu();

        int getSap();

        LlcpPacket receive() throws IOException;

        void send(int i, byte[] bArr) throws IOException;
    }

    /* loaded from: classes.dex */
    public interface LlcpServerSocket {
        LlcpSocket accept() throws IOException, LlcpException;

        void close() throws IOException;
    }

    /* loaded from: classes.dex */
    public interface LlcpSocket {
        void close() throws IOException;

        void connectToSap(int i) throws IOException;

        void connectToService(String str) throws IOException;

        int getLocalMiu();

        int getLocalRw();

        int getLocalSap();

        int getRemoteMiu();

        int getRemoteRw();

        int receive(byte[] bArr) throws IOException;

        void send(byte[] bArr) throws IOException;
    }

    /* loaded from: classes.dex */
    public interface NfcDepEndpoint {
        public static final short MODE_INVALID = 255;
        public static final short MODE_P2P_INITIATOR = 1;
        public static final short MODE_P2P_TARGET = 0;

        boolean connect();

        boolean disconnect();

        byte[] getGeneralBytes();

        int getHandle();

        byte getLlcpVersion();

        int getMode();

        byte[] receive();

        boolean send(byte[] bArr);

        byte[] transceive(byte[] bArr);
    }

    /* loaded from: classes.dex */
    public interface NfceeEndpoint {
    }

    /* loaded from: classes.dex */
    public interface TagDisconnectedCallback {
        void onTagDisconnected(long j);
    }

    /* loaded from: classes.dex */
    public interface TagEndpoint {
        boolean checkNdef(int[] iArr);

        boolean connect(int i);

        boolean disconnect();

        NdefMessage findAndReadNdef();

        boolean formatNdef(byte[] bArr);

        int getConnectedTechnology();

        int getHandle();

        Bundle[] getTechExtras();

        int[] getTechList();

        byte[] getUid();

        boolean isNdefFormatable();

        boolean isPresent();

        boolean makeReadOnly();

        boolean presenceCheck();

        byte[] readNdef();

        boolean reconnect();

        void removeTechnology(int i);

        void startPresenceChecking(int i, TagDisconnectedCallback tagDisconnectedCallback);

        void stopPresenceChecking();

        byte[] transceive(byte[] bArr, boolean z, int[] iArr);

        boolean writeNdef(byte[] bArr);
    }

    boolean canMakeReadOnly(int i);

    boolean checkFirmware();

    void clearT3tIdentifiersCache();

    boolean commitRouting();

    LlcpConnectionlessSocket createLlcpConnectionlessSocket(int i, String str) throws LlcpException;

    LlcpServerSocket createLlcpServerSocket(int i, String str, int i2, int i3, int i4) throws LlcpException;

    LlcpSocket createLlcpSocket(int i, int i2, int i3, int i4) throws LlcpException;

    boolean deinitialize();

    void deregisterT3tIdentifier(byte[] bArr);

    void disableDiscovery();

    void disableDtaMode();

    boolean disableScreenOffSuspend();

    void doAbort(String str);

    boolean doActivateLlcp();

    boolean doCheckLlcp();

    void doSetScreenState(int i);

    void dump(FileDescriptor fileDescriptor);

    void enableDiscovery(NfcDiscoveryParameters nfcDiscoveryParameters, boolean z);

    void enableDtaMode();

    boolean enableScreenOffSuspend();

    void factoryReset();

    int getAidTableSize();

    int getDefaultLlcpMiu();

    int getDefaultLlcpRwSize();

    boolean getExtendedLengthApdusSupported();

    int getLfT3tMax();

    int getMaxTransceiveLength(int i);

    String getName();

    int getNciVersion();

    int getTimeout(int i);

    boolean initialize();

    void registerT3tIdentifier(byte[] bArr);

    void resetTimeouts();

    boolean routeAid(byte[] bArr, int i, int i2);

    boolean sendRawFrame(byte[] bArr);

    boolean setNfcSecure(boolean z);

    void setP2pInitiatorModes(int i);

    void setP2pTargetModes(int i);

    boolean setTimeout(int i, int i2);

    void shutdown();

    boolean unrouteAid(byte[] bArr);
}
