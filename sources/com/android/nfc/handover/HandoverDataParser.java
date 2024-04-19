package com.android.nfc.handover;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.OobData;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.nfc.snep.SnepMessage;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
/* loaded from: classes.dex */
public class HandoverDataParser {
    public static final int BT_HANDOVER_LE_ROLE_CENTRAL_ONLY = 1;
    private static final int BT_HANDOVER_TYPE_128_BIT_UUIDS_COMPLETE = 7;
    private static final int BT_HANDOVER_TYPE_128_BIT_UUIDS_PARTIAL = 6;
    private static final int BT_HANDOVER_TYPE_16_BIT_UUIDS_COMPLETE = 3;
    private static final int BT_HANDOVER_TYPE_16_BIT_UUIDS_PARTIAL = 2;
    private static final int BT_HANDOVER_TYPE_32_BIT_UUIDS_COMPLETE = 5;
    private static final int BT_HANDOVER_TYPE_32_BIT_UUIDS_PARTIAL = 4;
    private static final int BT_HANDOVER_TYPE_APPEARANCE = 25;
    private static final int BT_HANDOVER_TYPE_CLASS_OF_DEVICE = 13;
    private static final int BT_HANDOVER_TYPE_LE_ROLE = 28;
    private static final int BT_HANDOVER_TYPE_LE_SC_CONFIRMATION = 34;
    private static final int BT_HANDOVER_TYPE_LE_SC_RANDOM = 35;
    private static final int BT_HANDOVER_TYPE_LONG_LOCAL_NAME = 9;
    private static final int BT_HANDOVER_TYPE_MAC = 27;
    private static final int BT_HANDOVER_TYPE_SECURITY_MANAGER_TK = 16;
    private static final int BT_HANDOVER_TYPE_SHORT_LOCAL_NAME = 8;
    private static final int CARRIER_POWER_STATE_ACTIVATING = 2;
    private static final int CARRIER_POWER_STATE_ACTIVE = 1;
    private static final int CARRIER_POWER_STATE_INACTIVE = 0;
    private static final int CARRIER_POWER_STATE_UNKNOWN = 3;
    private static final int CLASS_OF_DEVICE_SIZE = 3;
    private static final boolean DBG = false;
    public static final int SECURITY_MANAGER_LE_SC_C_SIZE = 16;
    public static final int SECURITY_MANAGER_LE_SC_R_SIZE = 16;
    public static final int SECURITY_MANAGER_TK_SIZE = 16;
    private static final String TAG = "NfcHandover";
    private String mLocalBluetoothAddress;
    private static final byte[] TYPE_BT_OOB = "application/vnd.bluetooth.ep.oob".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TYPE_BLE_OOB = "application/vnd.bluetooth.le.oob".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TYPE_NOKIA = "nokia.com:bt".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RTD_COLLISION_RESOLUTION = {99, 114};
    private final Object mLock = new Object();
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    /* loaded from: classes.dex */
    public static class BluetoothHandoverData {
        public BluetoothDevice device;
        public String name;
        public OobData oobData;
        public boolean valid = HandoverDataParser.DBG;
        public boolean carrierActivating = HandoverDataParser.DBG;
        public int transport = 0;
        public ParcelUuid[] uuids = null;
        public BluetoothClass btClass = null;
    }

    /* loaded from: classes.dex */
    public static class IncomingHandoverData {
        public final BluetoothHandoverData handoverData;
        public final NdefMessage handoverSelect;

        public IncomingHandoverData(NdefMessage handoverSelect, BluetoothHandoverData handoverData) {
            this.handoverSelect = handoverSelect;
            this.handoverData = handoverData;
        }
    }

    static NdefRecord createCollisionRecord() {
        byte[] random = new byte[2];
        new Random().nextBytes(random);
        return new NdefRecord((short) 1, RTD_COLLISION_RESOLUTION, null, random);
    }

    NdefRecord createBluetoothAlternateCarrierRecord(boolean activating) {
        int i;
        byte[] payload = new byte[4];
        if (activating) {
            i = 2;
        } else {
            i = 1;
        }
        payload[0] = (byte) i;
        payload[1] = 1;
        payload[2] = 98;
        payload[3] = 0;
        return new NdefRecord((short) 1, NdefRecord.RTD_ALTERNATIVE_CARRIER, null, payload);
    }

    NdefRecord createBluetoothOobDataRecord() {
        byte[] payload = new byte[8];
        payload[0] = (byte) (payload.length & 255);
        payload[1] = (byte) ((payload.length >> 8) & 255);
        synchronized (this.mLock) {
            if (this.mLocalBluetoothAddress == null) {
                this.mLocalBluetoothAddress = this.mBluetoothAdapter.getAddress();
            }
            byte[] addressBytes = addressToReverseBytes(this.mLocalBluetoothAddress);
            if (addressBytes != null) {
                System.arraycopy(addressBytes, 0, payload, 2, 6);
            } else {
                this.mLocalBluetoothAddress = null;
            }
        }
        return new NdefRecord((short) 2, TYPE_BT_OOB, new byte[]{98}, payload);
    }

    public boolean isHandoverSupported() {
        if (this.mBluetoothAdapter != null) {
            return true;
        }
        return DBG;
    }

    public NdefMessage createHandoverRequestMessage() {
        if (this.mBluetoothAdapter == null) {
            return null;
        }
        NdefRecord[] dataRecords = {createBluetoothOobDataRecord()};
        return new NdefMessage(createHandoverRequestRecord(), dataRecords);
    }

    NdefMessage createBluetoothHandoverSelectMessage(boolean activating) {
        return new NdefMessage(createHandoverSelectRecord(createBluetoothAlternateCarrierRecord(activating)), createBluetoothOobDataRecord());
    }

    NdefRecord createHandoverSelectRecord(NdefRecord alternateCarrier) {
        NdefMessage nestedMessage = new NdefMessage(alternateCarrier, new NdefRecord[0]);
        byte[] nestedPayload = nestedMessage.toByteArray();
        ByteBuffer payload = ByteBuffer.allocate(nestedPayload.length + 1);
        payload.put((byte) 18);
        payload.put(nestedPayload);
        byte[] payloadBytes = new byte[payload.position()];
        payload.position(0);
        payload.get(payloadBytes);
        return new NdefRecord((short) 1, NdefRecord.RTD_HANDOVER_SELECT, null, payloadBytes);
    }

    NdefRecord createHandoverRequestRecord() {
        NdefRecord[] messages = {createBluetoothAlternateCarrierRecord(DBG)};
        NdefMessage nestedMessage = new NdefMessage(createCollisionRecord(), messages);
        byte[] nestedPayload = nestedMessage.toByteArray();
        ByteBuffer payload = ByteBuffer.allocate(nestedPayload.length + 1);
        payload.put((byte) 18);
        payload.put(nestedMessage.toByteArray());
        byte[] payloadBytes = new byte[payload.position()];
        payload.position(0);
        payload.get(payloadBytes);
        return new NdefRecord((short) 1, NdefRecord.RTD_HANDOVER_REQUEST, null, payloadBytes);
    }

    public IncomingHandoverData getIncomingHandoverData(NdefMessage handoverRequest) {
        NdefRecord[] records;
        if (handoverRequest == null || this.mBluetoothAdapter == null) {
            return null;
        }
        NdefRecord handoverRequestRecord = handoverRequest.getRecords()[0];
        if (handoverRequestRecord.getTnf() != 1 || !Arrays.equals(handoverRequestRecord.getType(), NdefRecord.RTD_HANDOVER_REQUEST)) {
            return null;
        }
        BluetoothHandoverData bluetoothData = null;
        for (NdefRecord dataRecord : handoverRequest.getRecords()) {
            if (dataRecord.getTnf() == 2 && Arrays.equals(dataRecord.getType(), TYPE_BT_OOB)) {
                bluetoothData = parseBtOob(ByteBuffer.wrap(dataRecord.getPayload()));
            }
        }
        NdefMessage hs = tryBluetoothHandoverRequest(bluetoothData);
        if (hs == null) {
            return null;
        }
        return new IncomingHandoverData(hs, bluetoothData);
    }

    public BluetoothHandoverData getOutgoingHandoverData(NdefMessage handoverSelect) {
        return parseBluetooth(handoverSelect);
    }

    private NdefMessage tryBluetoothHandoverRequest(BluetoothHandoverData bluetoothData) {
        if (bluetoothData == null) {
            return null;
        }
        boolean bluetoothActivating = !this.mBluetoothAdapter.isEnabled();
        NdefMessage selectMessage = createBluetoothHandoverSelectMessage(bluetoothActivating);
        return selectMessage;
    }

    boolean isCarrierActivating(NdefRecord handoverRec, byte[] carrierId) {
        NdefRecord[] records;
        byte[] payload = handoverRec.getPayload();
        if (payload == null || payload.length <= 1) {
            return DBG;
        }
        byte[] payloadNdef = new byte[payload.length - 1];
        System.arraycopy(payload, 1, payloadNdef, 0, payload.length - 1);
        try {
            NdefMessage msg = new NdefMessage(payloadNdef);
            for (NdefRecord alt : msg.getRecords()) {
                byte[] acPayload = alt.getPayload();
                if (acPayload != null) {
                    ByteBuffer buf = ByteBuffer.wrap(acPayload);
                    int cps = buf.get() & 3;
                    int carrierRefLength = buf.get() & SnepMessage.RESPONSE_REJECT;
                    if (carrierRefLength != carrierId.length) {
                        return DBG;
                    }
                    byte[] carrierRefId = new byte[carrierRefLength];
                    buf.get(carrierRefId);
                    if (Arrays.equals(carrierRefId, carrierId)) {
                        if (cps == 2) {
                            return true;
                        }
                        return DBG;
                    }
                }
            }
            return true;
        } catch (FormatException e) {
            return DBG;
        }
    }

    BluetoothHandoverData parseBluetoothHandoverSelect(NdefMessage m) {
        NdefRecord[] records;
        for (NdefRecord oob : m.getRecords()) {
            if (oob.getTnf() == 2 && Arrays.equals(oob.getType(), TYPE_BT_OOB)) {
                BluetoothHandoverData data = parseBtOob(ByteBuffer.wrap(oob.getPayload()));
                if (data != null && isCarrierActivating(m.getRecords()[0], oob.getId())) {
                    data.carrierActivating = true;
                }
                return data;
            } else if (oob.getTnf() == 2 && Arrays.equals(oob.getType(), TYPE_BLE_OOB)) {
                return parseBleOob(ByteBuffer.wrap(oob.getPayload()));
            }
        }
        return null;
    }

    public BluetoothHandoverData parseBluetooth(NdefMessage m) {
        NdefRecord r = m.getRecords()[0];
        short tnf = r.getTnf();
        byte[] type = r.getType();
        if (r.getTnf() == 2 && Arrays.equals(r.getType(), TYPE_BT_OOB)) {
            return parseBtOob(ByteBuffer.wrap(r.getPayload()));
        }
        if (r.getTnf() == 2 && Arrays.equals(r.getType(), TYPE_BLE_OOB)) {
            return parseBleOob(ByteBuffer.wrap(r.getPayload()));
        }
        if (tnf == 1 && Arrays.equals(type, NdefRecord.RTD_HANDOVER_SELECT)) {
            return parseBluetoothHandoverSelect(m);
        }
        if (tnf == 4 && Arrays.equals(type, TYPE_NOKIA)) {
            return parseNokia(ByteBuffer.wrap(r.getPayload()));
        }
        return null;
    }

    BluetoothHandoverData parseNokia(ByteBuffer payload) {
        BluetoothHandoverData result = new BluetoothHandoverData();
        result.valid = DBG;
        try {
            payload.position(1);
            byte[] address = new byte[6];
            payload.get(address);
            result.device = this.mBluetoothAdapter.getRemoteDevice(address);
            result.valid = true;
            payload.position(14);
            int nameLength = payload.get();
            byte[] nameBytes = new byte[nameLength];
            payload.get(nameBytes);
            result.name = new String(nameBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "nokia: invalid BT address");
        } catch (BufferUnderflowException e2) {
            Log.i(TAG, "nokia: payload shorter than expected");
        }
        if (result.valid && result.name == null) {
            result.name = "";
        }
        return result;
    }

    BluetoothHandoverData parseBtOob(ByteBuffer payload) {
        BluetoothHandoverData result = new BluetoothHandoverData();
        result.valid = DBG;
        try {
            payload.position(2);
            byte[] address = parseMacFromBluetoothRecord(payload);
            result.device = this.mBluetoothAdapter.getRemoteDevice(address);
            result.valid = true;
            while (payload.remaining() > 0) {
                boolean success = DBG;
                int len = payload.get();
                int type = payload.get();
                if (type != 13) {
                    switch (type) {
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                        case 7:
                            result.uuids = parseUuidFromBluetoothRecord(payload, type, len - 1);
                            if (result.uuids != null) {
                                success = true;
                                break;
                            }
                            break;
                        case 8:
                            byte[] nameBytes = new byte[len - 1];
                            payload.get(nameBytes);
                            result.name = new String(nameBytes, StandardCharsets.UTF_8);
                            success = true;
                            break;
                        case 9:
                            if (result.name == null) {
                                byte[] nameBytes2 = new byte[len - 1];
                                payload.get(nameBytes2);
                                result.name = new String(nameBytes2, StandardCharsets.UTF_8);
                                success = true;
                                break;
                            } else {
                                break;
                            }
                    }
                } else if (len - 1 != 3) {
                    Log.i(TAG, "BT OOB: invalid size of Class of Device, should be 3 bytes.");
                } else {
                    result.btClass = parseBluetoothClassFromBluetoothRecord(payload);
                    success = true;
                }
                if (!success) {
                    payload.position((payload.position() + len) - 1);
                }
            }
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "BT OOB: invalid BT address");
        } catch (BufferUnderflowException e2) {
            Log.i(TAG, "BT OOB: payload shorter than expected");
        }
        if (result.valid && result.name == null) {
            result.name = "";
        }
        return result;
    }

    BluetoothHandoverData parseBleOob(ByteBuffer payload) {
        BluetoothHandoverData result = new BluetoothHandoverData();
        result.valid = DBG;
        result.transport = 2;
        while (payload.remaining() > 0) {
            try {
                int len = payload.get();
                int type = payload.get();
                if (type == 9) {
                    byte[] nameBytes = new byte[len - 1];
                    payload.get(nameBytes);
                    result.name = new String(nameBytes, StandardCharsets.UTF_8);
                } else if (type != 16) {
                    if (type == 27) {
                        int startpos = payload.position();
                        byte[] bdaddr = new byte[7];
                        payload.get(bdaddr);
                        if (result.oobData == null) {
                            result.oobData = new OobData();
                        }
                        result.oobData.setLeBluetoothDeviceAddress(bdaddr);
                        payload.position(startpos);
                        byte[] address = parseMacFromBluetoothRecord(payload);
                        payload.position(payload.position() + 1);
                        result.device = this.mBluetoothAdapter.getRemoteDevice(address);
                        result.valid = true;
                    } else if (type == 28) {
                        byte role = payload.get();
                        if (role == 1) {
                            result.valid = DBG;
                            return result;
                        }
                    } else if (type != 34) {
                        if (type == 35) {
                            if (len - 1 != 16) {
                                Log.i(TAG, "BT OOB: invalid size of LE SC Random, should be 16 bytes.");
                            } else {
                                byte[] leScR = new byte[len - 1];
                                payload.get(leScR);
                                if (result.oobData == null) {
                                    result.oobData = new OobData();
                                }
                                result.oobData.setLeSecureConnectionsRandom(leScR);
                            }
                        } else {
                            payload.position((payload.position() + len) - 1);
                        }
                    } else if (len - 1 != 16) {
                        Log.i(TAG, "BT OOB: invalid size of LE SC Confirmation, should be 16 bytes.");
                    } else {
                        byte[] leScC = new byte[len - 1];
                        payload.get(leScC);
                        if (result.oobData == null) {
                            result.oobData = new OobData();
                        }
                        result.oobData.setLeSecureConnectionsConfirmation(leScC);
                    }
                } else if (len - 1 != 16) {
                    Log.i(TAG, "BT OOB: invalid size of SM TK, should be 16 bytes.");
                } else {
                    byte[] securityManagerTK = new byte[len - 1];
                    payload.get(securityManagerTK);
                    if (result.oobData == null) {
                        result.oobData = new OobData();
                    }
                    result.oobData.setSecurityManagerTk(securityManagerTK);
                }
            } catch (IllegalArgumentException e) {
                Log.i(TAG, "BLE OOB: error parsing OOB data", e);
            } catch (BufferUnderflowException e2) {
                Log.i(TAG, "BT OOB: payload shorter than expected");
            }
        }
        if (result.valid && result.name == null) {
            result.name = "";
        }
        return result;
    }

    private byte[] parseMacFromBluetoothRecord(ByteBuffer payload) {
        byte[] address = new byte[6];
        payload.get(address);
        for (int i = 0; i < 3; i++) {
            byte temp = address[i];
            address[i] = address[5 - i];
            address[5 - i] = temp;
        }
        return address;
    }

    static byte[] addressToReverseBytes(String address) {
        if (address == null) {
            Log.w(TAG, "BT address is null");
            return null;
        }
        String[] split = address.split(":");
        if (split.length < 6) {
            Log.w(TAG, "BT address " + address + " is invalid");
            return null;
        }
        byte[] result = new byte[split.length];
        for (int i = 0; i < split.length; i++) {
            result[(split.length - 1) - i] = (byte) Integer.parseInt(split[i], 16);
        }
        return result;
    }

    private ParcelUuid[] parseUuidFromBluetoothRecord(ByteBuffer payload, int type, int len) {
        int uuidSize;
        switch (type) {
            case 2:
            case 3:
                uuidSize = 2;
                break;
            case 4:
            case 5:
                uuidSize = 4;
                break;
            case 6:
            case 7:
                uuidSize = 16;
                break;
            default:
                Log.i(TAG, "BT OOB: invalid size of UUID");
                return null;
        }
        if (len == 0 || len % uuidSize != 0) {
            Log.i(TAG, "BT OOB: invalid size of UUIDs, should be multiples of UUID bytes length");
            return null;
        }
        int num = len / uuidSize;
        ParcelUuid[] uuids = new ParcelUuid[num];
        byte[] data = new byte[uuidSize];
        for (int i = 0; i < num; i++) {
            payload.get(data);
            uuids[i] = BluetoothUuid.parseUuidFrom(data);
        }
        return uuids;
    }

    private BluetoothClass parseBluetoothClassFromBluetoothRecord(ByteBuffer payload) {
        byte[] btClass = new byte[3];
        payload.get(btClass);
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put(btClass);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return new BluetoothClass(buffer.getInt(0));
    }
}
