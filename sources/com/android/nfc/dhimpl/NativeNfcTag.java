package com.android.nfc.dhimpl;

import android.nfc.NdefMessage;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.snep.SnepMessage;
/* loaded from: classes.dex */
public class NativeNfcTag implements DeviceHost.TagEndpoint {
    static final boolean DBG = true;
    static final int STATUS_CODE_TARGET_LOST = 146;
    private final String TAG = "NativeNfcTag";
    private int mConnectedHandle;
    private int mConnectedTechIndex;
    private boolean mIsPresent;
    private byte[][] mTechActBytes;
    private Bundle[] mTechExtras;
    private int[] mTechHandles;
    private int[] mTechLibNfcTypes;
    private int[] mTechList;
    private byte[][] mTechPollBytes;
    private byte[] mUid;
    private PresenceCheckWatchdog mWatchdog;

    private native int doCheckNdef(int[] iArr);

    private native int doConnect(int i);

    private native byte[] doRead();

    private native byte[] doTransceive(byte[] bArr, boolean z, int[] iArr);

    private native boolean doWrite(byte[] bArr);

    native boolean doDisconnect();

    native int doGetNdefType(int i, int i2);

    native int doHandleReconnect(int i);

    native boolean doIsIsoDepNdefFormatable(byte[] bArr, byte[] bArr2);

    native boolean doMakeReadonly(byte[] bArr);

    native boolean doNdefFormat(byte[] bArr);

    native boolean doPresenceCheck();

    native int doReconnect();

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public class PresenceCheckWatchdog extends Thread {
        private DeviceHost.TagDisconnectedCallback tagDisconnectedCallback;
        private final int watchdogTimeout;
        private boolean isPresent = NativeNfcTag.DBG;
        private boolean isStopped = false;
        private boolean isPaused = false;
        private boolean doCheck = NativeNfcTag.DBG;

        public PresenceCheckWatchdog(int presenceCheckDelay, DeviceHost.TagDisconnectedCallback callback) {
            this.watchdogTimeout = presenceCheckDelay;
            this.tagDisconnectedCallback = callback;
        }

        public synchronized void pause() {
            this.isPaused = NativeNfcTag.DBG;
            this.doCheck = false;
            notifyAll();
        }

        public synchronized void doResume() {
            this.isPaused = false;
            this.doCheck = false;
            notifyAll();
        }

        public synchronized void end(boolean disableCallback) {
            this.isStopped = NativeNfcTag.DBG;
            this.doCheck = false;
            if (disableCallback) {
                this.tagDisconnectedCallback = null;
            }
            notifyAll();
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            synchronized (this) {
                Log.d("NativeNfcTag", "Starting background presence check");
                while (this.isPresent && !this.isStopped) {
                    try {
                        if (!this.isPaused) {
                            this.doCheck = NativeNfcTag.DBG;
                        }
                        wait(this.watchdogTimeout);
                        if (this.doCheck) {
                            this.isPresent = NativeNfcTag.this.doPresenceCheck();
                        }
                    } catch (InterruptedException e) {
                    }
                }
            }
            synchronized (NativeNfcTag.this) {
                NativeNfcTag.this.mIsPresent = false;
            }
            Log.d("NativeNfcTag", "Tag lost, restarting polling loop");
            NativeNfcTag.this.doDisconnect();
            DeviceHost.TagDisconnectedCallback tagDisconnectedCallback = this.tagDisconnectedCallback;
            if (tagDisconnectedCallback != null) {
                tagDisconnectedCallback.onTagDisconnected(NativeNfcTag.this.mConnectedHandle);
            }
            Log.d("NativeNfcTag", "Stopping background presence check");
        }
    }

    public synchronized int connectWithStatus(int technology) {
        int status;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        status = -1;
        int i = 0;
        while (true) {
            if (i >= this.mTechList.length) {
                break;
            } else if (this.mTechList[i] != technology) {
                i++;
            } else if (this.mConnectedHandle != this.mTechHandles[i]) {
                if (this.mConnectedHandle == -1) {
                    status = doConnect(i);
                } else {
                    Log.d("NativeNfcTag", "Connect to a tech with a different handle");
                    status = reconnectWithStatus(i);
                }
                if (status == 0) {
                    this.mConnectedHandle = this.mTechHandles[i];
                    this.mConnectedTechIndex = i;
                }
            } else {
                i = (technology == 6 || technology == 7) ? 0 : 0;
                status = reconnectWithStatus(i);
                if (status == 0) {
                    this.mConnectedTechIndex = i;
                }
            }
        }
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return status;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean connect(int technology) {
        return connectWithStatus(technology) == 0 ? DBG : false;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized void stopPresenceChecking() {
        this.mIsPresent = false;
        if (this.mWatchdog != null) {
            this.mWatchdog.end(DBG);
        }
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized void startPresenceChecking(int presenceCheckDelay, DeviceHost.TagDisconnectedCallback callback) {
        this.mIsPresent = DBG;
        if (this.mWatchdog == null) {
            this.mWatchdog = new PresenceCheckWatchdog(presenceCheckDelay, callback);
            this.mWatchdog.start();
        }
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean isPresent() {
        return this.mIsPresent;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public boolean disconnect() {
        PresenceCheckWatchdog watchdog;
        boolean result;
        synchronized (this) {
            this.mIsPresent = false;
            watchdog = this.mWatchdog;
        }
        if (watchdog != null) {
            watchdog.end(false);
            try {
                watchdog.join();
            } catch (InterruptedException e) {
            }
            synchronized (this) {
                this.mWatchdog = null;
            }
            result = DBG;
        } else {
            result = doDisconnect();
        }
        this.mConnectedTechIndex = -1;
        this.mConnectedHandle = -1;
        return result;
    }

    public synchronized int reconnectWithStatus() {
        int status;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        status = doReconnect();
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return status;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean reconnect() {
        return reconnectWithStatus() == 0 ? DBG : false;
    }

    public synchronized int reconnectWithStatus(int handle) {
        int status;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        status = doHandleReconnect(handle);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return status;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized byte[] transceive(byte[] data, boolean raw, int[] returnCode) {
        byte[] result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doTransceive(data, raw, returnCode);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    private synchronized int checkNdefWithStatus(int[] ndefinfo) {
        int status;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        status = doCheckNdef(ndefinfo);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return status;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean checkNdef(int[] ndefinfo) {
        return checkNdefWithStatus(ndefinfo) == 0 ? DBG : false;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized byte[] readNdef() {
        byte[] result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doRead();
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean writeNdef(byte[] buf) {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doWrite(buf);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean presenceCheck() {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doPresenceCheck();
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean formatNdef(byte[] key) {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        result = doNdefFormat(key);
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean makeReadOnly() {
        boolean result;
        if (this.mWatchdog != null) {
            this.mWatchdog.pause();
        }
        if (hasTech(8)) {
            result = doMakeReadonly(MifareClassic.KEY_DEFAULT);
        } else {
            result = doMakeReadonly(new byte[0]);
        }
        if (this.mWatchdog != null) {
            this.mWatchdog.doResume();
        }
        return result;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public synchronized boolean isNdefFormatable() {
        return doIsIsoDepNdefFormatable(this.mTechPollBytes[0], this.mTechActBytes[0]);
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public int getHandle() {
        int[] iArr = this.mTechHandles;
        if (iArr.length > 0) {
            return iArr[0];
        }
        return 0;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public byte[] getUid() {
        return this.mUid;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public int[] getTechList() {
        return this.mTechList;
    }

    private int getConnectedHandle() {
        return this.mConnectedHandle;
    }

    private int getConnectedLibNfcType() {
        int i = this.mConnectedTechIndex;
        if (i != -1) {
            int[] iArr = this.mTechLibNfcTypes;
            if (i < iArr.length) {
                return iArr[i];
            }
            return 0;
        }
        return 0;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public int getConnectedTechnology() {
        int i = this.mConnectedTechIndex;
        if (i != -1) {
            int[] iArr = this.mTechList;
            if (i < iArr.length) {
                return iArr[i];
            }
            return 0;
        }
        return 0;
    }

    private int getNdefType(int libnfctype, int javatype) {
        return doGetNdefType(libnfctype, javatype);
    }

    private void addTechnology(int tech, int handle, int libnfctype) {
        int[] iArr = this.mTechList;
        int[] mNewTechList = new int[iArr.length + 1];
        System.arraycopy(iArr, 0, mNewTechList, 0, iArr.length);
        mNewTechList[this.mTechList.length] = tech;
        this.mTechList = mNewTechList;
        int[] iArr2 = this.mTechHandles;
        int[] mNewHandleList = new int[iArr2.length + 1];
        System.arraycopy(iArr2, 0, mNewHandleList, 0, iArr2.length);
        mNewHandleList[this.mTechHandles.length] = handle;
        this.mTechHandles = mNewHandleList;
        int[] iArr3 = this.mTechLibNfcTypes;
        int[] mNewTypeList = new int[iArr3.length + 1];
        System.arraycopy(iArr3, 0, mNewTypeList, 0, iArr3.length);
        mNewTypeList[this.mTechLibNfcTypes.length] = libnfctype;
        this.mTechLibNfcTypes = mNewTypeList;
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public void removeTechnology(int tech) {
        synchronized (this) {
            int techIndex = getTechIndex(tech);
            if (techIndex != -1) {
                int[] mNewTechList = new int[this.mTechList.length - 1];
                System.arraycopy(this.mTechList, 0, mNewTechList, 0, techIndex);
                System.arraycopy(this.mTechList, techIndex + 1, mNewTechList, techIndex, (this.mTechList.length - techIndex) - 1);
                this.mTechList = mNewTechList;
                int[] mNewHandleList = new int[this.mTechHandles.length - 1];
                System.arraycopy(this.mTechHandles, 0, mNewHandleList, 0, techIndex);
                System.arraycopy(this.mTechHandles, techIndex + 1, mNewTechList, techIndex, (this.mTechHandles.length - techIndex) - 1);
                this.mTechHandles = mNewHandleList;
                int[] mNewTypeList = new int[this.mTechLibNfcTypes.length - 1];
                System.arraycopy(this.mTechLibNfcTypes, 0, mNewTypeList, 0, techIndex);
                System.arraycopy(this.mTechLibNfcTypes, techIndex + 1, mNewTypeList, techIndex, (this.mTechLibNfcTypes.length - techIndex) - 1);
                this.mTechLibNfcTypes = mNewTypeList;
                if (this.mTechExtras != null) {
                    Bundle[] mNewTechExtras = new Bundle[this.mTechExtras.length - 1];
                    System.arraycopy(this.mTechExtras, 0, mNewTechExtras, 0, techIndex);
                    System.arraycopy(this.mTechExtras, techIndex + 1, mNewTechExtras, techIndex, (this.mTechExtras.length - techIndex) - 1);
                    this.mTechExtras = mNewTechExtras;
                }
            }
        }
    }

    public void addNdefFormatableTechnology(int handle, int libnfcType) {
        synchronized (this) {
            addTechnology(7, handle, libnfcType);
        }
    }

    public void addNdefTechnology(NdefMessage msg, int handle, int libnfcType, int javaType, int maxLength, int cardState) {
        synchronized (this) {
            addTechnology(6, handle, libnfcType);
            Bundle extras = new Bundle();
            extras.putParcelable("ndefmsg", msg);
            extras.putInt("ndefmaxlength", maxLength);
            extras.putInt("ndefcardstate", cardState);
            extras.putInt("ndeftype", getNdefType(libnfcType, javaType));
            if (this.mTechExtras == null) {
                Bundle[] builtTechExtras = getTechExtras();
                builtTechExtras[builtTechExtras.length - 1] = extras;
            } else {
                Bundle[] oldTechExtras = getTechExtras();
                Bundle[] newTechExtras = new Bundle[oldTechExtras.length + 1];
                System.arraycopy(oldTechExtras, 0, newTechExtras, 0, oldTechExtras.length);
                newTechExtras[oldTechExtras.length] = extras;
                this.mTechExtras = newTechExtras;
            }
        }
    }

    private int getTechIndex(int tech) {
        int i = 0;
        while (true) {
            int[] iArr = this.mTechList;
            if (i >= iArr.length) {
                return -1;
            }
            if (iArr[i] != tech) {
                i++;
            } else {
                int techIndex = i;
                return techIndex;
            }
        }
    }

    private boolean hasTech(int tech) {
        int i = 0;
        while (true) {
            int[] iArr = this.mTechList;
            if (i >= iArr.length) {
                return false;
            }
            if (iArr[i] != tech) {
                i++;
            } else {
                return DBG;
            }
        }
    }

    private boolean hasTechOnHandle(int tech, int handle) {
        int i = 0;
        while (true) {
            int[] iArr = this.mTechList;
            if (i >= iArr.length) {
                return false;
            }
            if (iArr[i] != tech || this.mTechHandles[i] != handle) {
                i++;
            } else {
                return DBG;
            }
        }
    }

    private boolean isUltralightC() {
        byte[] readCmd = {48, 2};
        int[] retCode = new int[2];
        byte[] respData = transceive(readCmd, false, retCode);
        if (respData == null || respData.length != 16) {
            return false;
        }
        if (respData[2] == 0 && respData[3] == 0 && respData[4] == 0 && respData[5] == 0 && respData[6] == 0 && respData[7] == 0) {
            if (respData[8] == 2 && respData[9] == 0) {
                return DBG;
            }
            return false;
        } else if (respData[4] != -31 || (respData[5] & SnepMessage.RESPONSE_REJECT) >= 32 || (respData[6] & SnepMessage.RESPONSE_REJECT) <= 6) {
            return false;
        } else {
            return DBG;
        }
    }

    @Override // com.android.nfc.DeviceHost.TagEndpoint
    public Bundle[] getTechExtras() {
        synchronized (this) {
            if (this.mTechExtras != null) {
                return this.mTechExtras;
            }
            this.mTechExtras = new Bundle[this.mTechList.length];
            for (int i = 0; i < this.mTechList.length; i++) {
                Bundle extras = new Bundle();
                int i2 = this.mTechList[i];
                if (i2 == 1) {
                    byte[] actBytes = this.mTechActBytes[i];
                    if (actBytes != null && actBytes.length > 0) {
                        extras.putShort("sak", (short) (actBytes[0] & SnepMessage.RESPONSE_REJECT));
                    }
                    extras.putByteArray("atqa", this.mTechPollBytes[i]);
                } else if (i2 == 2) {
                    byte[] appData = new byte[4];
                    byte[] protInfo = new byte[3];
                    if (this.mTechPollBytes[i].length >= 7) {
                        System.arraycopy(this.mTechPollBytes[i], 0, appData, 0, 4);
                        System.arraycopy(this.mTechPollBytes[i], 4, protInfo, 0, 3);
                        extras.putByteArray("appdata", appData);
                        extras.putByteArray("protinfo", protInfo);
                    }
                } else if (i2 != 3) {
                    if (i2 == 4) {
                        byte[] pmm = new byte[8];
                        byte[] sc = new byte[2];
                        if (this.mTechPollBytes[i].length >= 8) {
                            System.arraycopy(this.mTechPollBytes[i], 0, pmm, 0, 8);
                            extras.putByteArray("pmm", pmm);
                        }
                        if (this.mTechPollBytes[i].length == 10) {
                            System.arraycopy(this.mTechPollBytes[i], 8, sc, 0, 2);
                            extras.putByteArray("systemcode", sc);
                        }
                    } else if (i2 != 5) {
                        if (i2 == 9) {
                            boolean isUlc = isUltralightC();
                            extras.putBoolean("isulc", isUlc);
                        } else if (i2 == 10) {
                            extras.putInt("barcodetype", 1);
                        }
                    } else if (this.mTechPollBytes[i] != null && this.mTechPollBytes[i].length >= 2) {
                        extras.putByte("respflags", this.mTechPollBytes[i][0]);
                        extras.putByte("dsfid", this.mTechPollBytes[i][1]);
                    }
                } else if (hasTech(1)) {
                    extras.putByteArray("histbytes", this.mTechActBytes[i]);
                } else {
                    extras.putByteArray("hiresp", this.mTechActBytes[i]);
                }
                this.mTechExtras[i] = extras;
            }
            return this.mTechExtras;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:47:0x00d2  */
    @Override // com.android.nfc.DeviceHost.TagEndpoint
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public android.nfc.NdefMessage findAndReadNdef() {
        /*
            Method dump skipped, instructions count: 251
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.dhimpl.NativeNfcTag.findAndReadNdef():android.nfc.NdefMessage");
    }
}
