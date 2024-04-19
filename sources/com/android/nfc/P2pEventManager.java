package com.android.nfc;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Vibrator;
import com.android.nfc.P2pEventListener;
import com.android.nfc.beam.SendUi;
/* loaded from: classes.dex */
public class P2pEventManager implements P2pEventListener, SendUi.Callback {
    static final boolean DBG = true;
    static final String TAG = "NfcP2pEventManager";
    static final long[] VIBRATION_PATTERN = {0, 100, 10000};
    final P2pEventListener.Callback mCallback;
    final Context mContext;
    boolean mInDebounce;
    boolean mNdefReceived;
    boolean mNdefSent;
    final NotificationManager mNotificationManager;
    final SendUi mSendUi;
    final Vibrator mVibrator;
    final NfcService mNfcService = NfcService.getInstance();
    boolean mSending = false;

    public P2pEventManager(Context context, P2pEventListener.Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        this.mVibrator = (Vibrator) context.getSystemService("vibrator");
        this.mNotificationManager = (NotificationManager) this.mContext.getSystemService("notification");
        int uiModeType = this.mContext.getResources().getConfiguration().uiMode & 15;
        if (uiModeType == 5) {
            this.mSendUi = null;
        } else {
            this.mSendUi = new SendUi(context, this);
        }
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pInRange() {
        this.mNdefSent = false;
        this.mNdefReceived = false;
        this.mInDebounce = false;
        SendUi sendUi = this.mSendUi;
        if (sendUi != null) {
            sendUi.takeScreenshot();
        }
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pNfcTapRequested() {
        this.mNfcService.playSound(0);
        this.mNdefSent = false;
        this.mNdefReceived = false;
        this.mInDebounce = false;
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        SendUi sendUi = this.mSendUi;
        if (sendUi != null) {
            sendUi.takeScreenshot();
            this.mSendUi.showPreSend(DBG);
        }
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pTimeoutWaitingForLink() {
        SendUi sendUi = this.mSendUi;
        if (sendUi != null) {
            sendUi.finish(0);
        }
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pSendConfirmationRequested() {
        this.mNfcService.playSound(0);
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        SendUi sendUi = this.mSendUi;
        if (sendUi != null) {
            sendUi.showPreSend(false);
        } else {
            this.mCallback.onP2pSendConfirmed();
        }
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pSendComplete() {
        this.mNfcService.playSound(1);
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        SendUi sendUi = this.mSendUi;
        if (sendUi != null) {
            sendUi.finish(1);
        }
        this.mSending = false;
        this.mNdefSent = DBG;
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pHandoverNotSupported() {
        this.mNfcService.playSound(2);
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        this.mSendUi.finishAndToast(0, this.mContext.getString(R.string.beam_handover_not_supported));
        this.mSending = false;
        this.mNdefSent = false;
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pHandoverBusy() {
        this.mNfcService.playSound(2);
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        this.mSendUi.finishAndToast(0, this.mContext.getString(R.string.beam_busy));
        this.mSending = false;
        this.mNdefSent = false;
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pReceiveComplete(boolean playSound) {
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        if (playSound) {
            this.mNfcService.playSound(1);
        }
        SendUi sendUi = this.mSendUi;
        if (sendUi != null) {
            sendUi.finish(0);
        }
        this.mNdefReceived = DBG;
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pOutOfRange() {
        SendUi sendUi;
        if (this.mSending) {
            this.mNfcService.playSound(2);
            this.mSending = false;
        }
        if (!this.mNdefSent && !this.mNdefReceived && (sendUi = this.mSendUi) != null) {
            sendUi.finish(0);
        }
        this.mInDebounce = false;
    }

    @Override // com.android.nfc.beam.SendUi.Callback
    public void onSendConfirmed() {
        if (!this.mSending) {
            SendUi sendUi = this.mSendUi;
            if (sendUi != null) {
                sendUi.showStartSend();
            }
            this.mCallback.onP2pSendConfirmed();
        }
        this.mSending = DBG;
    }

    @Override // com.android.nfc.beam.SendUi.Callback
    public void onCanceled() {
        this.mSendUi.finish(0);
        this.mCallback.onP2pCanceled();
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pSendDebounce() {
        this.mInDebounce = DBG;
        this.mNfcService.playSound(2);
        SendUi sendUi = this.mSendUi;
        if (sendUi != null) {
            sendUi.showSendHint();
        }
    }

    @Override // com.android.nfc.P2pEventListener
    public void onP2pResumeSend() {
        SendUi sendUi;
        this.mVibrator.vibrate(VIBRATION_PATTERN, -1);
        this.mNfcService.playSound(0);
        if (this.mInDebounce && (sendUi = this.mSendUi) != null) {
            sendUi.showStartSend();
        }
        this.mInDebounce = false;
    }

    @Override // com.android.nfc.P2pEventListener
    public boolean isP2pIdle() {
        SendUi sendUi = this.mSendUi;
        if (sendUi != null && !sendUi.isSendUiInIdleState()) {
            return false;
        }
        return DBG;
    }
}
