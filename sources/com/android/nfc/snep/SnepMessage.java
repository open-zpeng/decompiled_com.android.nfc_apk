package com.android.nfc.snep;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import com.android.nfc.NfcService;
import com.android.nfc.sneptest.DtaSnepClient;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
/* loaded from: classes.dex */
public final class SnepMessage {
    private static final int HEADER_LENGTH = 6;
    public static final int MAL = -1;
    public static final int MAL_IUT = 1024;
    public static final byte REQUEST_CONTINUE = 0;
    public static final byte REQUEST_GET = 1;
    public static final byte REQUEST_PUT = 2;
    public static final byte REQUEST_REJECT = Byte.MAX_VALUE;
    public static final byte REQUEST_RFU = 3;
    public static final byte RESPONSE_BAD_REQUEST = -62;
    public static final byte RESPONSE_CONTINUE = Byte.MIN_VALUE;
    public static final byte RESPONSE_NOT_FOUND = -64;
    public static final byte RESPONSE_NOT_IMPLEMENTED = -32;
    public static final byte RESPONSE_REJECT = -1;
    public static final byte RESPONSE_SUCCESS = -127;
    public static final byte RESPONSE_UNSUPPORTED_VERSION = -31;
    public static final byte VERSION = 16;
    public static final byte VERSION_MAJOR = 1;
    public static final byte VERSION_MINOR = 0;
    private final int mAcceptableLength;
    private final byte mField;
    private final int mLength;
    private final NdefMessage mNdefMessage;
    private final byte mVersion;
    private static final byte[] NDEF_SHORT_TEST_RECORD = {-47, 1, 30, 84, 2, 108, 97, 76, 111, 114, 101, 109, 32, 105, 112, 115, 117, 109, 32, 100, 111, 108, 111, 114, 32, 115, 105, 116, 32, 97, 109, 101, 116, 46};
    public static final byte RESPONSE_EXCESS_DATA = -63;
    private static final byte[] NDEF_TEST_RECORD = {RESPONSE_EXCESS_DATA, 1, 0, 0, 0, 30, 84, 2, 108, 97, 76, 111, 114, 101, 109, 32, 105, 112, 115, 117, 109, 32, 100, 111, 108, 111, 114, 32, 115, 105, 116, 32, 97, 109, 101, 116, 46};

    public static SnepMessage getGetRequest(int acceptableLength, NdefMessage ndef) {
        return new SnepMessage(VERSION, (byte) 1, ndef.toByteArray().length + 4, acceptableLength, ndef);
    }

    public static SnepMessage getPutRequest(NdefMessage ndef) {
        return new SnepMessage(VERSION, (byte) 2, ndef.toByteArray().length, 0, ndef);
    }

    public static SnepMessage getMessage(byte field) {
        return new SnepMessage(VERSION, field, 0, 0, null);
    }

    public static SnepMessage getSuccessResponse(NdefMessage ndef) {
        if (ndef == null) {
            return new SnepMessage(VERSION, RESPONSE_SUCCESS, 0, 0, null);
        }
        return new SnepMessage(VERSION, RESPONSE_SUCCESS, ndef.toByteArray().length, 0, ndef);
    }

    public static SnepMessage fromByteArray(byte[] data) throws FormatException {
        return new SnepMessage(data);
    }

    public static NdefMessage getLargeNdef() throws UnsupportedEncodingException {
        byte[] textBytes = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus at lorem nunc, ut venenatis quam. Etiam id dolor quam, at viverra dolor. Phasellus eu lacus ligula, quis euismod erat. Sed feugiat, ligula at mollis aliquet, justo lacus condimentum eros, non tincidunt neque ipsum eu risus. Sed adipiscing dui euismod tellus ullamcorper ornare. Phasellus mattis risus et lectus euismod eu fermentum sem cursus. Phasellus tristique consectetur mauris eu porttitor. Sed lobortis porttitor orci.".getBytes();
        byte[] langBytes = "la".getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[langLength + 1 + textLength];
        payload[0] = (byte) langLength;
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, langLength + 1, textLength);
        NdefRecord data2 = new NdefRecord((short) 1, NdefRecord.RTD_TEXT, new byte[0], payload);
        return new NdefMessage(new NdefRecord[]{data2});
    }

    public static NdefMessage getSmallNdef() throws UnsupportedEncodingException {
        byte[] textBytes = "Lorem ipsum dolor sit amet.".getBytes();
        byte[] langBytes = "la".getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[langLength + 1 + textLength];
        payload[0] = (byte) langLength;
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, langLength + 1, textLength);
        NdefRecord data1 = new NdefRecord((short) 1, NdefRecord.RTD_TEXT, new byte[0], payload);
        return new NdefMessage(new NdefRecord[]{data1});
    }

    private SnepMessage(byte[] data) throws FormatException {
        int ndefOffset;
        int ndefLength;
        ByteBuffer input = ByteBuffer.wrap(data);
        this.mVersion = input.get();
        this.mField = input.get();
        this.mLength = input.getInt();
        if (this.mField == 1) {
            this.mAcceptableLength = input.getInt();
            ndefOffset = 10;
            ndefLength = this.mLength - 4;
        } else {
            this.mAcceptableLength = -1;
            ndefOffset = 6;
            ndefLength = this.mLength;
        }
        if (ndefLength > 0) {
            byte[] bytes = new byte[ndefLength];
            System.arraycopy(data, ndefOffset, bytes, 0, ndefLength);
            this.mNdefMessage = new NdefMessage(bytes);
            return;
        }
        this.mNdefMessage = null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public SnepMessage(byte version, byte field, int length, int acceptableLength, NdefMessage ndefMessage) {
        this.mVersion = version;
        this.mField = field;
        this.mLength = length;
        this.mAcceptableLength = acceptableLength;
        this.mNdefMessage = ndefMessage;
    }

    public byte[] toByteArray() {
        byte[] bytes;
        ByteArrayOutputStream buffer;
        if (this.mNdefMessage != null) {
            if (NfcService.sIsDtaMode && DtaSnepClient.mTestCaseId != 0) {
                if (DtaSnepClient.mTestCaseId == 5 || DtaSnepClient.mTestCaseId == 6) {
                    bytes = this.mNdefMessage.toByteArray();
                } else if (NfcService.sIsShortRecordLayout) {
                    bytes = NDEF_SHORT_TEST_RECORD;
                } else {
                    bytes = NDEF_TEST_RECORD;
                }
            } else {
                bytes = this.mNdefMessage.toByteArray();
            }
        } else {
            bytes = new byte[0];
        }
        try {
            if (this.mField == 1) {
                buffer = new ByteArrayOutputStream(bytes.length + 6 + 4);
            } else {
                buffer = new ByteArrayOutputStream(bytes.length + 6);
            }
            DataOutputStream output = new DataOutputStream(buffer);
            output.writeByte(this.mVersion);
            output.writeByte(this.mField);
            if (this.mField == 1) {
                output.writeInt(bytes.length + 4);
                output.writeInt(this.mAcceptableLength);
            } else {
                output.writeInt(bytes.length);
            }
            output.write(bytes);
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public NdefMessage getNdefMessage() {
        return this.mNdefMessage;
    }

    public byte getField() {
        return this.mField;
    }

    public byte getVersion() {
        return this.mVersion;
    }

    public int getLength() {
        return this.mLength;
    }

    public int getAcceptableLength() {
        if (this.mField != 1) {
            throw new UnsupportedOperationException("Acceptable Length only available on get request messages.");
        }
        return this.mAcceptableLength;
    }
}
