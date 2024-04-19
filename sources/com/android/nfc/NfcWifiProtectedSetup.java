package com.android.nfc;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.tech.Ndef;
import android.os.UserHandle;
import android.os.UserManager;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.BitSet;
/* loaded from: classes.dex */
public final class NfcWifiProtectedSetup {
    private static final short AUTH_TYPE_EXPECTED_SIZE = 2;
    private static final short AUTH_TYPE_FIELD_ID = 4099;
    private static final short AUTH_TYPE_OPEN = 1;
    private static final short AUTH_TYPE_WPA2_EAP = 16;
    private static final short AUTH_TYPE_WPA2_PSK = 32;
    private static final short AUTH_TYPE_WPA_AND_WPA2_PSK = 34;
    private static final short AUTH_TYPE_WPA_EAP = 8;
    private static final short AUTH_TYPE_WPA_PSK = 2;
    private static final short CREDENTIAL_FIELD_ID = 4110;
    public static final String EXTRA_WIFI_CONFIG = "com.android.nfc.WIFI_CONFIG_EXTRA";
    private static final int MAX_NETWORK_KEY_SIZE_BYTES = 64;
    private static final short NETWORK_KEY_FIELD_ID = 4135;
    public static final String NFC_TOKEN_MIME_TYPE = "application/vnd.wfa.wsc";
    private static final short SSID_FIELD_ID = 4165;

    private NfcWifiProtectedSetup() {
    }

    public static boolean tryNfcWifiSetup(Ndef ndef, Context context) {
        NdefMessage cachedNdefMessage;
        if (ndef == null || context == null || (cachedNdefMessage = ndef.getCachedNdefMessage()) == null) {
            return false;
        }
        try {
            WifiConfiguration wifiConfiguration = parse(cachedNdefMessage);
            if (wifiConfiguration == null || UserManager.get(context).hasUserRestriction("no_config_wifi", UserHandle.of(ActivityManager.getCurrentUser()))) {
                return false;
            }
            Intent configureNetworkIntent = new Intent().putExtra(EXTRA_WIFI_CONFIG, wifiConfiguration).setClass(context, ConfirmConnectToWifiNetworkActivity.class).setFlags(268468224);
            context.startActivityAsUser(configureNetworkIntent, UserHandle.CURRENT);
            return true;
        } catch (BufferUnderflowException e) {
            return false;
        }
    }

    private static WifiConfiguration parse(NdefMessage message) {
        NdefRecord[] records = message.getRecords();
        for (NdefRecord record : records) {
            if (new String(record.getType()).equals(NFC_TOKEN_MIME_TYPE)) {
                ByteBuffer payload = ByteBuffer.wrap(record.getPayload());
                while (payload.hasRemaining()) {
                    short fieldId = payload.getShort();
                    int fieldSize = payload.getShort() & 65535;
                    if (fieldId == 4110) {
                        return parseCredential(payload, fieldSize);
                    }
                    payload.position(payload.position() + fieldSize);
                }
                continue;
            }
        }
        return null;
    }

    private static WifiConfiguration parseCredential(ByteBuffer payload, int size) {
        int startPosition = payload.position();
        WifiConfiguration result = new WifiConfiguration();
        while (payload.position() < startPosition + size) {
            short fieldId = payload.getShort();
            int fieldSize = payload.getShort() & 65535;
            if (payload.position() + fieldSize > startPosition + size) {
                return null;
            }
            if (fieldId != 4099) {
                if (fieldId != 4135) {
                    if (fieldId == 4165) {
                        byte[] ssid = new byte[fieldSize];
                        payload.get(ssid);
                        result.SSID = "\"" + new String(ssid) + "\"";
                    } else {
                        payload.position(payload.position() + fieldSize);
                    }
                } else if (fieldSize > 64) {
                    return null;
                } else {
                    byte[] networkKey = new byte[fieldSize];
                    payload.get(networkKey);
                    if (fieldSize > 0) {
                        result.preSharedKey = getPskValidFormat(new String(networkKey));
                    }
                }
            } else if (fieldSize != 2) {
                return null;
            } else {
                short authType = payload.getShort();
                populateAllowedKeyManagement(result.allowedKeyManagement, authType);
            }
        }
        if (result.SSID != null) {
            if (result.getAuthType() == 0) {
                if (result.preSharedKey == null) {
                    return result;
                }
            } else if (result.preSharedKey != null) {
                return result;
            }
        }
        return null;
    }

    private static void populateAllowedKeyManagement(BitSet allowedKeyManagement, short authType) {
        if (authType == 2 || authType == 32 || authType == 34) {
            allowedKeyManagement.set(1);
        } else if (authType == 8 || authType == 16) {
            allowedKeyManagement.set(2);
        } else if (authType == 1) {
            allowedKeyManagement.set(0);
        }
    }

    private static String getPskValidFormat(String data) {
        if (!data.matches("[0-9A-Fa-f]{64}")) {
            return convertToQuotedString(data);
        }
        return data;
    }

    private static String convertToQuotedString(String str) {
        return '\"' + str + '\"';
    }
}
