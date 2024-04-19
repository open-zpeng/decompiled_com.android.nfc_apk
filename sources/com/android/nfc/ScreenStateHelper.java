package com.android.nfc;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public class ScreenStateHelper {
    static final int SCREEN_POLLING_P2P_MASK = 32;
    static final int SCREEN_POLLING_READER_MASK = 64;
    static final int SCREEN_POLLING_TAG_MASK = 16;
    static final int SCREEN_STATE_OFF_LOCKED = 2;
    static final int SCREEN_STATE_OFF_UNLOCKED = 1;
    static final int SCREEN_STATE_ON_LOCKED = 4;
    static final int SCREEN_STATE_ON_UNLOCKED = 8;
    static final int SCREEN_STATE_UNKNOWN = 0;
    private final KeyguardManager mKeyguardManager;
    private final PowerManager mPowerManager;

    /* JADX INFO: Access modifiers changed from: package-private */
    public ScreenStateHelper(Context context) {
        this.mKeyguardManager = (KeyguardManager) context.getSystemService("keyguard");
        this.mPowerManager = (PowerManager) context.getSystemService("power");
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int checkScreenState() {
        if (!this.mPowerManager.isScreenOn()) {
            if (this.mKeyguardManager.isKeyguardLocked()) {
                return 2;
            }
            return 1;
        } else if (this.mKeyguardManager.isKeyguardLocked()) {
            return 4;
        } else {
            return 8;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String screenStateToString(int screenState) {
        if (screenState != 1) {
            if (screenState != 2) {
                if (screenState != 4) {
                    if (screenState == 8) {
                        return "ON_UNLOCKED";
                    }
                    return "UNKNOWN";
                }
                return "ON_LOCKED";
            }
            return "OFF_LOCKED";
        }
        return "OFF_UNLOCKED";
    }
}
