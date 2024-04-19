package com.android.nfc;
/* compiled from: P2pLinkManager.java */
/* loaded from: classes.dex */
interface P2pEventListener {

    /* compiled from: P2pLinkManager.java */
    /* loaded from: classes.dex */
    public interface Callback {
        void onP2pCanceled();

        void onP2pSendConfirmed();
    }

    boolean isP2pIdle();

    void onP2pHandoverBusy();

    void onP2pHandoverNotSupported();

    void onP2pInRange();

    void onP2pNfcTapRequested();

    void onP2pOutOfRange();

    void onP2pReceiveComplete(boolean z);

    void onP2pResumeSend();

    void onP2pSendComplete();

    void onP2pSendConfirmationRequested();

    void onP2pSendDebounce();

    void onP2pTimeoutWaitingForLink();
}
