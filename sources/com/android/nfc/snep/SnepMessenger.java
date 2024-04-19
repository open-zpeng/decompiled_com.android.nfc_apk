package com.android.nfc.snep;

import android.nfc.FormatException;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.NfcService;
import com.android.nfc.sneptest.DtaSnepClient;
import com.android.nfc.sneptest.ExtDtaSnepServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
/* loaded from: classes.dex */
public class SnepMessenger {
    private static final boolean DBG = false;
    private static final int HEADER_LENGTH = 6;
    private static final String TAG = "SnepMessager";
    final int mFragmentLength;
    final boolean mIsClient;
    final DeviceHost.LlcpSocket mSocket;

    public SnepMessenger(boolean isClient, DeviceHost.LlcpSocket socket, int fragmentLength) {
        this.mSocket = socket;
        this.mFragmentLength = fragmentLength;
        this.mIsClient = isClient;
    }

    public void sendMessage(SnepMessage msg) throws IOException {
        byte remoteContinue;
        byte[] buffer = msg.toByteArray();
        if (this.mIsClient) {
            remoteContinue = SnepMessage.RESPONSE_CONTINUE;
        } else {
            remoteContinue = 0;
        }
        int length = Math.min(buffer.length, this.mFragmentLength);
        byte[] tmpBuffer = Arrays.copyOfRange(buffer, 0, length);
        this.mSocket.send(tmpBuffer);
        if (length == buffer.length) {
            return;
        }
        int offset = length;
        byte[] responseBytes = new byte[6];
        this.mSocket.receive(responseBytes);
        try {
            SnepMessage snepResponse = SnepMessage.fromByteArray(responseBytes);
            if (snepResponse.getField() != remoteContinue) {
                throw new IOException("Invalid response from server (" + ((int) snepResponse.getField()) + ")");
            }
            if (NfcService.sIsDtaMode && this.mIsClient && DtaSnepClient.mTestCaseId == 6) {
                int length2 = Math.min(buffer.length - offset, this.mFragmentLength);
                byte[] tmpBuffer2 = Arrays.copyOfRange(buffer, offset, offset + length2);
                this.mSocket.send(tmpBuffer2);
                offset += length2;
                this.mSocket.receive(responseBytes);
                try {
                    if (SnepMessage.fromByteArray(responseBytes).getField() == remoteContinue) {
                        close();
                        return;
                    }
                } catch (FormatException e) {
                    throw new IOException("Invalid SNEP message", e);
                }
            }
            while (offset < buffer.length) {
                int length3 = Math.min(buffer.length - offset, this.mFragmentLength);
                byte[] tmpBuffer3 = Arrays.copyOfRange(buffer, offset, offset + length3);
                this.mSocket.send(tmpBuffer3);
                if (NfcService.sIsDtaMode && !this.mIsClient && ExtDtaSnepServer.mTestCaseId == 1) {
                    this.mSocket.receive(responseBytes);
                    try {
                        if (SnepMessage.fromByteArray(responseBytes).getField() == remoteContinue) {
                            close();
                            return;
                        }
                    } catch (FormatException e2) {
                        throw new IOException("Invalid SNEP message", e2);
                    }
                }
                offset += length3;
            }
        } catch (FormatException e3) {
            throw new IOException("Invalid SNEP message", e3);
        }
    }

    public SnepMessage getMessage() throws IOException, SnepException {
        byte fieldReject;
        byte fieldContinue;
        int readSize;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(this.mFragmentLength);
        byte[] partial = new byte[this.mFragmentLength];
        boolean doneReading = DBG;
        if (this.mIsClient) {
            fieldReject = SnepMessage.REQUEST_REJECT;
            fieldContinue = 0;
        } else {
            fieldReject = -1;
            fieldContinue = Byte.MIN_VALUE;
        }
        int size = this.mSocket.receive(partial);
        if (size < 0) {
            try {
                this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
            } catch (IOException e) {
            }
            throw new IOException("Error reading SNEP message.");
        } else if (size < 6) {
            try {
                if (NfcService.sIsDtaMode && this.mIsClient) {
                    close();
                } else {
                    this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
                }
                this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
            } catch (IOException e2) {
            }
            throw new IOException("Invalid fragment from sender.");
        } else {
            int readSize2 = size - 6;
            buffer.write(partial, 0, size);
            DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(partial));
            byte requestVersion = dataIn.readByte();
            byte requestField = dataIn.readByte();
            int requestSize = dataIn.readInt();
            if (((requestVersion & 240) >> 4) != 1) {
                if (NfcService.sIsDtaMode) {
                    sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_UNSUPPORTED_VERSION));
                    close();
                } else if (NfcService.sIsDtaMode) {
                    sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_UNSUPPORTED_VERSION));
                    close();
                } else {
                    return new SnepMessage(requestVersion, requestField, 0, 0, null);
                }
            }
            if (NfcService.sIsDtaMode) {
                if ((!this.mIsClient && requestField == Byte.MIN_VALUE) || requestField == -127 || requestField == -64) {
                    close();
                }
                if (!this.mIsClient && requestField == 3) {
                    sendMessage(SnepMessage.getMessage(SnepMessage.RESPONSE_BAD_REQUEST));
                    close();
                }
                if (this.mIsClient && requestField == 2) {
                    close();
                }
                if (this.mIsClient && requestSize > 1024) {
                    return new SnepMessage(requestVersion, requestField, requestSize, 0, null);
                }
                if (!this.mIsClient && (requestSize > 1024 || requestSize == -1)) {
                    return new SnepMessage(requestVersion, requestField, requestSize, 0, null);
                }
            }
            if (requestSize > readSize2) {
                this.mSocket.send(SnepMessage.getMessage(fieldContinue).toByteArray());
                readSize = readSize2;
            } else {
                doneReading = true;
                readSize = readSize2;
            }
            while (!doneReading) {
                try {
                    int size2 = this.mSocket.receive(partial);
                    if (size2 < 0) {
                        try {
                            this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
                        } catch (IOException e3) {
                        }
                        throw new IOException();
                    }
                    readSize += size2;
                    buffer.write(partial, 0, size2);
                    if (readSize == requestSize) {
                        doneReading = true;
                    }
                } catch (IOException e4) {
                    try {
                        this.mSocket.send(SnepMessage.getMessage(fieldReject).toByteArray());
                    } catch (IOException e5) {
                    }
                    throw e4;
                }
            }
            try {
                return SnepMessage.fromByteArray(buffer.toByteArray());
            } catch (FormatException e6) {
                Log.e(TAG, "Badly formatted NDEF message, ignoring", e6);
                throw new SnepException(e6);
            }
        }
    }

    public void close() throws IOException {
        this.mSocket.close();
    }
}
