package com.android.nfc;

import android.nfc.ErrorCodes;
/* loaded from: classes.dex */
public class LlcpException extends Exception {
    public LlcpException(String s) {
        super(s);
    }

    public LlcpException(int error) {
        super(ErrorCodes.asString(error));
    }
}
