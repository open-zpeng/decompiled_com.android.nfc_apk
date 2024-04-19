package com.android.nfc.ndefpush;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
/* loaded from: classes.dex */
public class NdefPushProtocol {
    public static final byte ACTION_BACKGROUND = 2;
    public static final byte ACTION_IMMEDIATE = 1;
    private static final String TAG = "NdefMessageSet";
    private static final byte VERSION = 1;
    private byte[] mActions;
    private NdefMessage[] mMessages;
    private int mNumMessages;

    public NdefPushProtocol(NdefMessage msg, byte action) {
        this.mNumMessages = 1;
        this.mActions = new byte[1];
        this.mActions[0] = action;
        this.mMessages = new NdefMessage[1];
        this.mMessages[0] = msg;
    }

    public NdefPushProtocol(byte[] actions, NdefMessage[] messages) {
        if (actions.length != messages.length || actions.length == 0) {
            throw new IllegalArgumentException("actions and messages must be the same size and non-empty");
        }
        int numMessages = actions.length;
        this.mActions = new byte[numMessages];
        System.arraycopy(actions, 0, this.mActions, 0, numMessages);
        this.mMessages = new NdefMessage[numMessages];
        System.arraycopy(messages, 0, this.mMessages, 0, numMessages);
        this.mNumMessages = numMessages;
    }

    public NdefPushProtocol(byte[] data) throws FormatException {
        ByteArrayInputStream buffer = new ByteArrayInputStream(data);
        DataInputStream input = new DataInputStream(buffer);
        try {
            byte version = input.readByte();
            if (version != 1) {
                Log.w(TAG, "Got version " + ((int) version) + ",  expected 1");
                throw new FormatException("Got version " + ((int) version) + ",  expected 1");
            }
            try {
                this.mNumMessages = input.readInt();
                int i = this.mNumMessages;
                if (i == 0) {
                    Log.w(TAG, "No NdefMessage inside NdefMessageSet packet");
                    throw new FormatException("Error while parsing NdefMessageSet");
                }
                this.mActions = new byte[i];
                this.mMessages = new NdefMessage[i];
                for (int i2 = 0; i2 < this.mNumMessages; i2++) {
                    try {
                        this.mActions[i2] = input.readByte();
                        try {
                            int length = input.readInt();
                            byte[] bytes = new byte[length];
                            try {
                                int lengthRead = input.read(bytes);
                                if (length != lengthRead) {
                                    Log.w(TAG, "Read " + lengthRead + " bytes but expected " + length);
                                    throw new FormatException("Error while parsing NdefMessageSet");
                                }
                                try {
                                    this.mMessages[i2] = new NdefMessage(bytes);
                                } catch (FormatException e) {
                                    throw e;
                                }
                            } catch (IOException e2) {
                                Log.w(TAG, "Unable to read bytes for message " + i2);
                                throw new FormatException("Error while parsing NdefMessageSet");
                            }
                        } catch (IOException e3) {
                            Log.w(TAG, "Unable to read length for message " + i2);
                            throw new FormatException("Error while parsing NdefMessageSet");
                        }
                    } catch (IOException e4) {
                        Log.w(TAG, "Unable to read action for message " + i2);
                        throw new FormatException("Error while parsing NdefMessageSet");
                    }
                }
            } catch (IOException e5) {
                Log.w(TAG, "Unable to read numMessages");
                throw new FormatException("Error while parsing NdefMessageSet");
            }
        } catch (IOException e6) {
            Log.w(TAG, "Unable to read version");
            throw new FormatException("Unable to read version");
        }
    }

    public NdefMessage getImmediate() {
        for (int i = 0; i < this.mNumMessages; i++) {
            if (this.mActions[i] == 1) {
                return this.mMessages[i];
            }
        }
        return null;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
        DataOutputStream output = new DataOutputStream(buffer);
        try {
            output.writeByte(1);
            output.writeInt(this.mNumMessages);
            for (int i = 0; i < this.mNumMessages; i++) {
                output.writeByte(this.mActions[i]);
                byte[] bytes = this.mMessages[i].toByteArray();
                output.writeInt(bytes.length);
                output.write(bytes);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
