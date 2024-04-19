package com.android.nfc;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.nfc.BeamShareData;
import android.nfc.IAppCallback;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.StatsLog;
import com.android.nfc.P2pEventListener;
import com.android.nfc.beam.BeamManager;
import com.android.nfc.echoserver.EchoServer;
import com.android.nfc.handover.HandoverClient;
import com.android.nfc.handover.HandoverDataParser;
import com.android.nfc.handover.HandoverServer;
import com.android.nfc.ndefpush.NdefPushClient;
import com.android.nfc.ndefpush.NdefPushServer;
import com.android.nfc.snep.SnepClient;
import com.android.nfc.snep.SnepMessage;
import com.android.nfc.snep.SnepServer;
import com.android.nfc.sneptest.DtaSnepClient;
import com.android.nfc.sneptest.ExtDtaSnepServer;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public class P2pLinkManager implements Handler.Callback, P2pEventListener.Callback {
    static final boolean DBG = true;
    static final String DISABLE_BEAM_DEFAULT = "android.nfc.disable_beam_default";
    static final boolean ECHOSERVER_ENABLED = false;
    static final int HANDOVER_BUSY = 3;
    static final int HANDOVER_FAILURE = 1;
    static final int HANDOVER_SAP = 20;
    static final int HANDOVER_SUCCESS = 0;
    static final int HANDOVER_UNSUPPORTED = 2;
    static final int LINK_SEND_CANCELED_DEBOUNCE_MS = 250;
    static final int LINK_SEND_COMPLETE_DEBOUNCE_MS = 500;
    static final int LINK_SEND_CONFIRMED_DEBOUNCE_MS = 5000;
    static final int LINK_SEND_PENDING_DEBOUNCE_MS = 3000;
    static final int LINK_STATE_DEBOUNCE = 3;
    static final int LINK_STATE_DOWN = 1;
    static final int LINK_STATE_UP = 2;
    static final int MSG_DEBOUNCE_TIMEOUT = 1;
    static final int MSG_HANDOVER_BUSY = 10;
    static final int MSG_HANDOVER_NOT_SUPPORTED = 7;
    static final int MSG_RECEIVE_COMPLETE = 2;
    static final int MSG_RECEIVE_HANDOVER = 3;
    static final int MSG_SEND_COMPLETE = 4;
    static final int MSG_SHOW_CONFIRMATION_UI = 8;
    static final int MSG_START_ECHOSERVER = 5;
    static final int MSG_STOP_ECHOSERVER = 6;
    static final int MSG_WAIT_FOR_LINK_TIMEOUT = 9;
    static final int NDEFPUSH_SAP = 16;
    static final int SEND_STATE_CANCELED = 6;
    static final int SEND_STATE_COMPLETE = 5;
    static final int SEND_STATE_NEED_CONFIRMATION = 2;
    static final int SEND_STATE_NOTHING_TO_SEND = 1;
    static final int SEND_STATE_PENDING = 3;
    static final int SEND_STATE_SENDING = 4;
    static final int SNEP_FAILURE = 1;
    static final int SNEP_SUCCESS = 0;
    static final String TAG = "NfcP2pLinkManager";
    static final int WAIT_FOR_LINK_TIMEOUT_MS = 10000;
    IAppCallback mCallbackNdef;
    ConnectTask mConnectTask;
    final Context mContext;
    final int mDefaultMiu;
    final int mDefaultRwSize;
    final SnepServer mDefaultSnepServer;
    private int mDtaMiu;
    private int mDtaRwSize;
    final P2pEventListener mEventListener;
    HandoverClient mHandoverClient;
    final HandoverDataParser mHandoverDataParser;
    final HandoverServer mHandoverServer;
    long mLastLlcpActivationTime;
    NdefMessage mMessageToSend;
    NdefPushClient mNdefPushClient;
    PackageManager mPackageManager;
    byte mPeerLlcpVersion;
    SharedPreferences mPrefs;
    int mSendFlags;
    SendTask mSendTask;
    private String mServiceName;
    private int mServiceSap;
    SnepClient mSnepClient;
    private int mTestCaseID;
    Uri[] mUrisToSend;
    UserHandle mUserHandle;
    private ExtDtaSnepServer mExtDtaSnepServer = null;
    private DtaSnepClient mDtaSnepClient = null;
    private boolean mClientEnabled = ECHOSERVER_ENABLED;
    private boolean mServerEnabled = ECHOSERVER_ENABLED;
    private boolean mExtDtaSnepServerRunning = ECHOSERVER_ENABLED;
    private boolean mPutBeforeGet = ECHOSERVER_ENABLED;
    final HandoverServer.Callback mHandoverCallback = new HandoverServer.Callback() { // from class: com.android.nfc.P2pLinkManager.1
        @Override // com.android.nfc.handover.HandoverServer.Callback
        public void onHandoverRequestReceived() {
            P2pLinkManager.this.onReceiveHandover();
        }

        @Override // com.android.nfc.handover.HandoverServer.Callback
        public void onHandoverBusy() {
            P2pLinkManager.this.onHandoverBusy();
        }
    };
    final NdefPushServer.Callback mNppCallback = new NdefPushServer.Callback() { // from class: com.android.nfc.P2pLinkManager.2
        @Override // com.android.nfc.ndefpush.NdefPushServer.Callback
        public void onMessageReceived(NdefMessage msg) {
            P2pLinkManager.this.onReceiveComplete(msg);
        }
    };
    final SnepServer.Callback mDefaultSnepCallback = new SnepServer.Callback() { // from class: com.android.nfc.P2pLinkManager.3
        @Override // com.android.nfc.snep.SnepServer.Callback
        public SnepMessage doPut(NdefMessage msg) {
            if (NfcService.sIsDtaMode) {
                Log.d(P2pLinkManager.TAG, "DTA mode enabled, dont dispatch the tag");
            } else {
                P2pLinkManager.this.onReceiveComplete(msg);
            }
            return SnepMessage.getMessage(SnepMessage.RESPONSE_SUCCESS);
        }

        @Override // com.android.nfc.snep.SnepServer.Callback
        public SnepMessage doGet(int acceptableLength, NdefMessage msg) {
            NdefMessage response = null;
            if (NfcService.sIsDtaMode) {
                if (msg != null && P2pLinkManager.this.mHandoverDataParser.getIncomingHandoverData(msg) != null) {
                    response = P2pLinkManager.this.mHandoverDataParser.getIncomingHandoverData(msg).handoverSelect;
                }
            } else {
                response = P2pLinkManager.this.mHandoverDataParser.getIncomingHandoverData(msg).handoverSelect;
            }
            if (response != null) {
                P2pLinkManager.this.onReceiveHandover();
                return SnepMessage.getSuccessResponse(response);
            }
            return SnepMessage.getMessage(SnepMessage.RESPONSE_NOT_IMPLEMENTED);
        }
    };
    final ExtDtaSnepServer.Callback mExtDtaSnepServerCallback = new ExtDtaSnepServer.Callback() { // from class: com.android.nfc.P2pLinkManager.4
        @Override // com.android.nfc.sneptest.ExtDtaSnepServer.Callback
        public SnepMessage doPut(NdefMessage msg) {
            P2pLinkManager.this.mPutBeforeGet = P2pLinkManager.DBG;
            return SnepMessage.getMessage(SnepMessage.RESPONSE_SUCCESS);
        }

        @Override // com.android.nfc.sneptest.ExtDtaSnepServer.Callback
        public SnepMessage doGet(int acceptableLength, NdefMessage msg) {
            if (!P2pLinkManager.this.mPutBeforeGet) {
                return SnepMessage.getMessage(SnepMessage.RESPONSE_NOT_FOUND);
            }
            if (acceptableLength == 501) {
                P2pLinkManager.this.mPutBeforeGet = P2pLinkManager.ECHOSERVER_ENABLED;
                return SnepMessage.getMessage(SnepMessage.RESPONSE_EXCESS_DATA);
            } else if (!P2pLinkManager.this.mPutBeforeGet || acceptableLength != 1024) {
                P2pLinkManager.this.mPutBeforeGet = P2pLinkManager.ECHOSERVER_ENABLED;
                return SnepMessage.getMessage(SnepMessage.RESPONSE_NOT_IMPLEMENTED);
            } else {
                try {
                    P2pLinkManager.this.mPutBeforeGet = P2pLinkManager.ECHOSERVER_ENABLED;
                    return SnepMessage.getSuccessResponse(SnepMessage.getLargeNdef());
                } catch (UnsupportedEncodingException e) {
                    P2pLinkManager.this.mPutBeforeGet = P2pLinkManager.ECHOSERVER_ENABLED;
                    return null;
                }
            }
        }
    };
    final NdefPushServer mNdefPushServer = new NdefPushServer(16, this.mNppCallback);
    final EchoServer mEchoServer = null;
    final Handler mHandler = new Handler(this);
    int mLinkState = 1;
    int mSendState = 1;
    boolean mIsSendEnabled = ECHOSERVER_ENABLED;
    boolean mIsReceiveEnabled = ECHOSERVER_ENABLED;
    boolean mLlcpServicesConnected = ECHOSERVER_ENABLED;
    int mNdefCallbackUid = -1;
    final ForegroundUtils mForegroundUtils = ForegroundUtils.getInstance();

    public P2pLinkManager(Context context, HandoverDataParser handoverDataParser, int defaultMiu, int defaultRwSize) {
        this.mDefaultSnepServer = new SnepServer(this.mDefaultSnepCallback, defaultMiu, defaultRwSize);
        this.mHandoverServer = new HandoverServer(context, 20, handoverDataParser, this.mHandoverCallback);
        this.mPackageManager = context.getPackageManager();
        this.mContext = context;
        this.mEventListener = new P2pEventManager(context, this);
        this.mPrefs = context.getSharedPreferences(NfcService.PREF, 0);
        this.mHandoverDataParser = handoverDataParser;
        this.mDefaultMiu = defaultMiu;
        this.mDefaultRwSize = defaultRwSize;
    }

    public void enableDisable(boolean sendEnable, boolean receiveEnable) {
        synchronized (this) {
            if (!this.mIsReceiveEnabled && receiveEnable) {
                this.mDefaultSnepServer.start();
                this.mNdefPushServer.start();
                this.mHandoverServer.start();
                if (this.mEchoServer != null) {
                    this.mHandler.sendEmptyMessage(5);
                }
            } else if (this.mIsReceiveEnabled && !receiveEnable) {
                Log.d(TAG, "enableDisable: llcp deactivate");
                onLlcpDeactivated();
                this.mDefaultSnepServer.stop();
                this.mNdefPushServer.stop();
                this.mHandoverServer.stop();
                if (this.mEchoServer != null) {
                    this.mHandler.sendEmptyMessage(6);
                }
                if (this.mExtDtaSnepServerRunning) {
                    disableExtDtaSnepServer();
                }
            }
            this.mIsSendEnabled = sendEnable;
            this.mIsReceiveEnabled = receiveEnable;
        }
    }

    public void enableExtDtaSnepServer(String serviceName, int serviceSap, int miu, int rwSize, int testCaseId) {
        Log.d(TAG, "Enabling Extended DTA Server");
        this.mServiceName = serviceName;
        this.mServiceSap = serviceSap;
        this.mDtaMiu = miu;
        this.mDtaRwSize = rwSize;
        this.mTestCaseID = testCaseId;
        synchronized (this) {
            if (this.mExtDtaSnepServer == null) {
                this.mExtDtaSnepServer = new ExtDtaSnepServer(this.mServiceName, this.mServiceSap, this.mDtaMiu, this.mDtaRwSize, this.mExtDtaSnepServerCallback, this.mContext, this.mTestCaseID);
            }
            this.mExtDtaSnepServer.start();
            this.mExtDtaSnepServerRunning = DBG;
        }
        this.mServerEnabled = DBG;
    }

    public void disableExtDtaSnepServer() {
        Log.d(TAG, "Disabling Extended DTA Server");
        if (!this.mExtDtaSnepServerRunning) {
            return;
        }
        synchronized (this) {
            this.mExtDtaSnepServer.stop();
            this.mExtDtaSnepServer = null;
            this.mExtDtaSnepServerRunning = ECHOSERVER_ENABLED;
        }
        this.mServerEnabled = ECHOSERVER_ENABLED;
    }

    public void enableDtaSnepClient(String serviceName, int miu, int rwSize, int testCaseId) {
        Log.d(TAG, "enableDtaSnepClient");
        this.mClientEnabled = DBG;
        this.mServiceName = serviceName;
        this.mServiceSap = -1;
        this.mDtaMiu = miu;
        this.mDtaRwSize = rwSize;
        this.mTestCaseID = testCaseId;
    }

    public void disableDtaSnepClient() {
        Log.d(TAG, "disableDtaSnepClient");
        this.mDtaSnepClient = null;
        this.mClientEnabled = ECHOSERVER_ENABLED;
    }

    public boolean isLlcpActive() {
        boolean z;
        synchronized (this) {
            int i = this.mLinkState;
            z = DBG;
            if (i == 1) {
                z = ECHOSERVER_ENABLED;
            }
        }
        return z;
    }

    public void setNdefCallback(IAppCallback callbackNdef, int callingUid) {
        synchronized (this) {
            this.mCallbackNdef = callbackNdef;
            this.mNdefCallbackUid = callingUid;
        }
    }

    public void onManualBeamInvoke(BeamShareData shareData) {
        synchronized (this) {
            if (this.mLinkState != 1) {
                return;
            }
            if (this.mForegroundUtils.getForegroundUids().contains(Integer.valueOf(this.mNdefCallbackUid))) {
                prepareMessageToSend(ECHOSERVER_ENABLED);
            } else {
                this.mMessageToSend = null;
                this.mUrisToSend = null;
            }
            if (this.mMessageToSend == null && this.mUrisToSend == null && shareData != null) {
                if (shareData.uris != null) {
                    this.mUrisToSend = shareData.uris;
                } else if (shareData.ndefMessage != null) {
                    this.mMessageToSend = shareData.ndefMessage;
                }
                this.mUserHandle = shareData.userHandle;
            }
            if (this.mMessageToSend != null || (this.mUrisToSend != null && this.mHandoverDataParser.isHandoverSupported())) {
                this.mSendState = 3;
                this.mEventListener.onP2pNfcTapRequested();
                scheduleTimeoutLocked(9, WAIT_FOR_LINK_TIMEOUT_MS);
            }
        }
    }

    public void onLlcpActivated(byte peerLlcpVersion) {
        Log.i(TAG, "LLCP activated");
        synchronized (this) {
            if (this.mEchoServer != null) {
                this.mEchoServer.onLlcpActivated();
            }
            this.mLastLlcpActivationTime = SystemClock.elapsedRealtime();
            this.mPeerLlcpVersion = peerLlcpVersion;
            int i = this.mLinkState;
            if (i != 1) {
                if (i == 2) {
                    Log.d(TAG, "Duplicate onLlcpActivated()");
                } else if (i == 3) {
                    this.mLinkState = 2;
                    if (this.mSendState == 4 || this.mSendState == 2) {
                        connectLlcpServices();
                    }
                    this.mHandler.removeMessages(1);
                }
            } else if (this.mEventListener.isP2pIdle() || this.mSendState == 3) {
                Log.d(TAG, "onP2pInRange()");
                this.mEventListener.onP2pInRange();
                this.mLinkState = 2;
                if (this.mSendState == 3) {
                    this.mSendState = 4;
                    this.mHandler.removeMessages(9);
                    connectLlcpServices();
                } else {
                    this.mSendState = 1;
                    prepareMessageToSend(DBG);
                    if (this.mMessageToSend != null || (this.mUrisToSend != null && this.mHandoverDataParser.isHandoverSupported())) {
                        connectLlcpServices();
                        if ((this.mSendFlags & 1) != 0) {
                            this.mSendState = 4;
                        } else {
                            this.mSendState = 2;
                        }
                    }
                }
            }
        }
    }

    public void onLlcpFirstPacketReceived() {
        synchronized (this) {
            long totalTime = SystemClock.elapsedRealtime() - this.mLastLlcpActivationTime;
            Log.d(TAG, "Took " + Long.toString(totalTime) + " to get first LLCP PDU");
        }
    }

    public void onUserSwitched(int userId) {
        synchronized (this) {
            try {
                this.mPackageManager = this.mContext.createPackageContextAsUser("android", 0, new UserHandle(userId)).getPackageManager();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to retrieve PackageManager for user");
            }
        }
    }

    void prepareMessageToSend(boolean generatePlayLink) {
        synchronized (this) {
            this.mMessageToSend = null;
            this.mUrisToSend = null;
            if (this.mIsSendEnabled) {
                List<Integer> foregroundUids = this.mForegroundUtils.getForegroundUids();
                if (foregroundUids.isEmpty()) {
                    Log.e(TAG, "Could not determine foreground UID.");
                } else if (isBeamDisabled(foregroundUids.get(0).intValue())) {
                    Log.d(TAG, "Beam is disabled by policy.");
                } else {
                    if (this.mCallbackNdef != null) {
                        if (foregroundUids.contains(Integer.valueOf(this.mNdefCallbackUid))) {
                            try {
                                BeamShareData shareData = this.mCallbackNdef.createBeamShareData(this.mPeerLlcpVersion);
                                this.mMessageToSend = shareData.ndefMessage;
                                this.mUrisToSend = shareData.uris;
                                this.mUserHandle = shareData.userHandle;
                                this.mSendFlags = shareData.flags;
                                return;
                            } catch (Exception e) {
                                Log.e(TAG, "Failed NDEF callback: ", e);
                            }
                        } else {
                            Log.d(TAG, "Last registered callback is not running in the foreground.");
                        }
                    }
                    String[] pkgs = this.mPackageManager.getPackagesForUid(foregroundUids.get(0).intValue());
                    if (pkgs != null && pkgs.length >= 1) {
                        if (generatePlayLink && !beamDefaultDisabled(pkgs[0])) {
                            this.mMessageToSend = createDefaultNdef(pkgs[0]);
                            this.mUrisToSend = null;
                            this.mSendFlags = 0;
                        }
                        Log.d(TAG, "Disabling default Beam behavior");
                        this.mMessageToSend = null;
                        this.mUrisToSend = null;
                    }
                    Log.d(TAG, "mMessageToSend = " + this.mMessageToSend);
                    Log.d(TAG, "mUrisToSend = " + this.mUrisToSend);
                }
            }
        }
    }

    private boolean isBeamDisabled(int uid) {
        UserManager userManager = (UserManager) this.mContext.getSystemService("user");
        UserInfo userInfo = userManager.getUserInfo(UserHandle.getUserId(uid));
        return userManager.hasUserRestriction("no_outgoing_beam", userInfo.getUserHandle());
    }

    boolean beamDefaultDisabled(String pkgName) {
        try {
            ApplicationInfo ai = this.mPackageManager.getApplicationInfo(pkgName, 128);
            if (ai != null && ai.metaData != null) {
                return ai.metaData.getBoolean(DISABLE_BEAM_DEFAULT);
            }
            return ECHOSERVER_ENABLED;
        } catch (PackageManager.NameNotFoundException e) {
            return ECHOSERVER_ENABLED;
        }
    }

    NdefMessage createDefaultNdef(String pkgName) {
        NdefRecord appUri = NdefRecord.createUri(Uri.parse("http://play.google.com/store/apps/details?id=" + pkgName + "&feature=beam"));
        NdefRecord appRecord = NdefRecord.createApplicationRecord(pkgName);
        return new NdefMessage(new NdefRecord[]{appUri, appRecord});
    }

    void disconnectLlcpServices() {
        synchronized (this) {
            if (this.mConnectTask != null) {
                this.mConnectTask.cancel(DBG);
                this.mConnectTask = null;
            }
            if (this.mNdefPushClient != null) {
                this.mNdefPushClient.close();
                this.mNdefPushClient = null;
            }
            if (this.mSnepClient != null) {
                this.mSnepClient.close();
                this.mSnepClient = null;
            }
            if (this.mHandoverClient != null) {
                this.mHandoverClient.close();
                this.mHandoverClient = null;
            }
            this.mLlcpServicesConnected = ECHOSERVER_ENABLED;
        }
    }

    public void onLlcpDeactivated() {
        Log.i(TAG, "LLCP deactivated.");
        synchronized (this) {
            if (this.mEchoServer != null) {
                this.mEchoServer.onLlcpDeactivated();
            }
            int i = this.mLinkState;
            if (i != 1) {
                if (i == 2) {
                    this.mLinkState = 3;
                    int debounceTimeout = 0;
                    int i2 = this.mSendState;
                    if (i2 == 1) {
                        debounceTimeout = 0;
                    } else if (i2 == 2) {
                        debounceTimeout = LINK_SEND_PENDING_DEBOUNCE_MS;
                    } else if (i2 == 4) {
                        debounceTimeout = 5000;
                    } else if (i2 == 5) {
                        debounceTimeout = LINK_SEND_COMPLETE_DEBOUNCE_MS;
                    } else if (i2 == 6) {
                        debounceTimeout = LINK_SEND_CANCELED_DEBOUNCE_MS;
                    }
                    scheduleTimeoutLocked(1, debounceTimeout);
                    if (this.mSendState == 4) {
                        Log.e(TAG, "onP2pSendDebounce()");
                        this.mEventListener.onP2pSendDebounce();
                    }
                    cancelSendNdefMessage();
                    disconnectLlcpServices();
                } else if (i != 3) {
                }
            }
            Log.i(TAG, "Duplicate onLlcpDectivated()");
        }
    }

    void onHandoverUnsupported() {
        this.mHandler.sendEmptyMessage(7);
    }

    void onHandoverBusy() {
        this.mHandler.sendEmptyMessage(10);
    }

    void onSendComplete(NdefMessage msg, long elapsedRealtime) {
        this.mHandler.sendEmptyMessage(4);
    }

    void sendNdefMessage() {
        synchronized (this) {
            cancelSendNdefMessage();
            this.mSendTask = new SendTask();
            this.mSendTask.execute(new Void[0]);
        }
    }

    void cancelSendNdefMessage() {
        synchronized (this) {
            if (this.mSendTask != null) {
                this.mSendTask.cancel(DBG);
            }
        }
    }

    void connectLlcpServices() {
        synchronized (this) {
            if (this.mConnectTask != null) {
                Log.e(TAG, "Still had a reference to mConnectTask!");
            }
            this.mConnectTask = new ConnectTask();
            this.mConnectTask.execute(new Void[0]);
        }
    }

    void onLlcpServicesConnected() {
        Log.d(TAG, "onLlcpServicesConnected");
        synchronized (this) {
            if (this.mLinkState != 2) {
                return;
            }
            this.mLlcpServicesConnected = DBG;
            if (this.mSendState == 2) {
                Log.d(TAG, "onP2pSendConfirmationRequested()");
                this.mEventListener.onP2pSendConfirmationRequested();
            } else if (this.mSendState == 4) {
                this.mEventListener.onP2pResumeSend();
                sendNdefMessage();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class ConnectTask extends AsyncTask<Void, Void, Boolean> {
        ConnectTask() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Boolean result) {
            if (isCancelled()) {
                Log.d(P2pLinkManager.TAG, "ConnectTask was cancelled");
            } else if (result.booleanValue()) {
                P2pLinkManager.this.onLlcpServicesConnected();
            } else {
                Log.e(P2pLinkManager.TAG, "Could not connect required NFC transports");
            }
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Boolean doInBackground(Void... params) {
            HandoverClient handoverClient;
            boolean success;
            SnepClient snepClient;
            NdefPushClient nppClient;
            boolean needsHandover = P2pLinkManager.ECHOSERVER_ENABLED;
            boolean needsNdef = P2pLinkManager.ECHOSERVER_ENABLED;
            boolean success2 = P2pLinkManager.ECHOSERVER_ENABLED;
            SnepClient snepClient2 = null;
            synchronized (P2pLinkManager.this) {
                if (P2pLinkManager.this.mUrisToSend != null) {
                    needsHandover = P2pLinkManager.DBG;
                }
                if (P2pLinkManager.this.mMessageToSend != null) {
                    needsNdef = P2pLinkManager.DBG;
                }
            }
            if (!needsHandover) {
                handoverClient = null;
            } else {
                HandoverClient handoverClient2 = new HandoverClient();
                try {
                    handoverClient2.connect();
                    success2 = P2pLinkManager.DBG;
                    handoverClient = handoverClient2;
                } catch (IOException e) {
                    handoverClient = null;
                }
            }
            if (needsNdef || (needsHandover && handoverClient == null)) {
                if (NfcService.sIsDtaMode) {
                    if (P2pLinkManager.this.mClientEnabled && P2pLinkManager.this.mDtaSnepClient == null) {
                        Log.d(P2pLinkManager.TAG, "Creating DTA Snep Client");
                        P2pLinkManager p2pLinkManager = P2pLinkManager.this;
                        p2pLinkManager.mDtaSnepClient = new DtaSnepClient(p2pLinkManager.mServiceName, P2pLinkManager.this.mDtaMiu, P2pLinkManager.this.mDtaRwSize, P2pLinkManager.this.mTestCaseID);
                    }
                } else {
                    snepClient2 = new SnepClient();
                }
                try {
                    if (NfcService.sIsDtaMode) {
                        if (P2pLinkManager.this.mDtaSnepClient != null) {
                            P2pLinkManager.this.mDtaSnepClient.DtaClientOperations(P2pLinkManager.this.mContext);
                        }
                    } else {
                        snepClient2.connect();
                    }
                    success2 = P2pLinkManager.DBG;
                    P2pLinkManager.this.mDtaSnepClient = null;
                } catch (IOException e2) {
                    snepClient2 = null;
                }
                if (success2) {
                    success = success2;
                    snepClient = snepClient2;
                    nppClient = null;
                } else {
                    NdefPushClient nppClient2 = new NdefPushClient();
                    try {
                        nppClient2.connect();
                        success = true;
                        snepClient = snepClient2;
                        nppClient = nppClient2;
                    } catch (IOException e3) {
                        success = success2;
                        snepClient = snepClient2;
                        nppClient = null;
                    }
                }
            } else {
                success = success2;
                snepClient = null;
                nppClient = null;
            }
            synchronized (P2pLinkManager.this) {
                if (isCancelled()) {
                    if (handoverClient != null) {
                        handoverClient.close();
                    }
                    if (snepClient != null) {
                        snepClient.close();
                    }
                    if (nppClient != null) {
                        nppClient.close();
                    }
                    if (P2pLinkManager.this.mDtaSnepClient != null) {
                        P2pLinkManager.this.mDtaSnepClient.close();
                    }
                    return Boolean.valueOf((boolean) P2pLinkManager.ECHOSERVER_ENABLED);
                }
                P2pLinkManager.this.mHandoverClient = handoverClient;
                P2pLinkManager.this.mSnepClient = snepClient;
                P2pLinkManager.this.mNdefPushClient = nppClient;
                return Boolean.valueOf(success);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class SendTask extends AsyncTask<Void, Void, Void> {
        HandoverClient handoverClient;
        NdefPushClient nppClient;
        SnepClient snepClient;

        SendTask() {
        }

        int doHandover(Uri[] uris, UserHandle userHandle) throws IOException {
            SnepClient snepClient;
            NdefMessage response = null;
            BeamManager beamManager = BeamManager.getInstance();
            if (beamManager.isBeamInProgress()) {
                return 3;
            }
            NdefMessage request = P2pLinkManager.this.mHandoverDataParser.createHandoverRequestMessage();
            if (request == null) {
                return 2;
            }
            HandoverClient handoverClient = this.handoverClient;
            if (handoverClient != null) {
                response = handoverClient.sendHandoverRequest(request);
            }
            if (response == null && (snepClient = this.snepClient) != null) {
                SnepMessage snepResponse = snepClient.get(request);
                response = snepResponse.getNdefMessage();
            }
            if (response != null) {
                return !beamManager.startBeamSend(P2pLinkManager.this.mContext, P2pLinkManager.this.mHandoverDataParser.getOutgoingHandoverData(response), uris, userHandle) ? 3 : 0;
            } else if (this.snepClient != null) {
                return 2;
            } else {
                return 1;
            }
        }

        int doSnepProtocol(NdefMessage msg) throws IOException {
            if (msg != null) {
                this.snepClient.put(msg);
                return 0;
            }
            return 1;
        }

        @Override // android.os.AsyncTask
        public Void doInBackground(Void... args) {
            NdefPushClient ndefPushClient;
            boolean result = P2pLinkManager.ECHOSERVER_ENABLED;
            synchronized (P2pLinkManager.this) {
                if (P2pLinkManager.this.mLinkState == 2 && P2pLinkManager.this.mSendState == 4) {
                    NdefMessage m = P2pLinkManager.this.mMessageToSend;
                    Uri[] uris = P2pLinkManager.this.mUrisToSend;
                    UserHandle userHandle = P2pLinkManager.this.mUserHandle;
                    this.snepClient = P2pLinkManager.this.mSnepClient;
                    this.handoverClient = P2pLinkManager.this.mHandoverClient;
                    this.nppClient = P2pLinkManager.this.mNdefPushClient;
                    long time = SystemClock.elapsedRealtime();
                    if (uris != null) {
                        Log.d(P2pLinkManager.TAG, "Trying handover request");
                        try {
                            int handoverResult = doHandover(uris, userHandle);
                            if (handoverResult != 0) {
                                if (handoverResult == 1) {
                                    result = P2pLinkManager.ECHOSERVER_ENABLED;
                                } else if (handoverResult == 2) {
                                    result = P2pLinkManager.ECHOSERVER_ENABLED;
                                    P2pLinkManager.this.onHandoverUnsupported();
                                } else if (handoverResult == 3) {
                                    result = P2pLinkManager.ECHOSERVER_ENABLED;
                                    P2pLinkManager.this.onHandoverBusy();
                                }
                            } else {
                                result = P2pLinkManager.DBG;
                            }
                        } catch (IOException e) {
                            result = P2pLinkManager.ECHOSERVER_ENABLED;
                        }
                    }
                    if (!result && m != null && this.snepClient != null) {
                        Log.d(P2pLinkManager.TAG, "Sending ndef via SNEP");
                        try {
                            int snepResult = doSnepProtocol(m);
                            if (snepResult != 0) {
                                if (snepResult == 1) {
                                    result = P2pLinkManager.ECHOSERVER_ENABLED;
                                } else {
                                    result = P2pLinkManager.ECHOSERVER_ENABLED;
                                }
                            } else {
                                result = P2pLinkManager.DBG;
                            }
                        } catch (IOException e2) {
                            result = P2pLinkManager.ECHOSERVER_ENABLED;
                        }
                    }
                    if (!result && m != null && (ndefPushClient = this.nppClient) != null) {
                        result = ndefPushClient.push(m);
                    }
                    long time2 = SystemClock.elapsedRealtime() - time;
                    Log.d(P2pLinkManager.TAG, "SendTask result=" + result + ", time ms=" + time2);
                    if (result) {
                        P2pLinkManager.this.onSendComplete(m, time2);
                    }
                    return null;
                }
                return null;
            }
        }
    }

    void onReceiveHandover() {
        this.mHandler.obtainMessage(3).sendToTarget();
    }

    void onReceiveComplete(NdefMessage msg) {
        this.mHandler.obtainMessage(2, msg).sendToTarget();
    }

    @Override // android.os.Handler.Callback
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                synchronized (this) {
                    if (this.mLinkState == 3) {
                        Log.d(TAG, "Debounce timeout");
                        this.mLinkState = 1;
                        this.mSendState = 1;
                        this.mMessageToSend = null;
                        this.mUrisToSend = null;
                        Log.d(TAG, "onP2pOutOfRange()");
                        this.mEventListener.onP2pOutOfRange();
                    }
                }
                break;
            case 2:
                NdefMessage m = (NdefMessage) msg.obj;
                synchronized (this) {
                    if (this.mLinkState != 1) {
                        if (this.mSendState == 4) {
                            cancelSendNdefMessage();
                        }
                        this.mSendState = 1;
                        Log.d(TAG, "onP2pReceiveComplete()");
                        this.mEventListener.onP2pReceiveComplete(DBG);
                        NfcService.getInstance().sendMockNdefTag(m);
                        StatsLog.write(136, 2);
                        break;
                    } else {
                        break;
                    }
                }
            case 3:
                synchronized (this) {
                    if (this.mLinkState != 1) {
                        if (this.mSendState == 4) {
                            cancelSendNdefMessage();
                        }
                        this.mSendState = 1;
                        Log.d(TAG, "onP2pReceiveComplete()");
                        this.mEventListener.onP2pReceiveComplete(ECHOSERVER_ENABLED);
                        StatsLog.write(136, 2);
                        break;
                    } else {
                        break;
                    }
                }
            case 4:
                synchronized (this) {
                    this.mSendTask = null;
                    if (this.mLinkState != 1 && this.mSendState == 4) {
                        this.mSendState = 5;
                        this.mHandler.removeMessages(1);
                        Log.d(TAG, "onP2pSendComplete()");
                        this.mEventListener.onP2pSendComplete();
                        if (this.mCallbackNdef != null) {
                            try {
                                this.mCallbackNdef.onNdefPushComplete(this.mPeerLlcpVersion);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed NDEF completed callback: " + e.getMessage());
                            }
                        }
                        StatsLog.write(136, 1);
                        break;
                    }
                    break;
                }
            case 5:
                synchronized (this) {
                    this.mEchoServer.start();
                }
                break;
            case 6:
                synchronized (this) {
                    this.mEchoServer.stop();
                }
                break;
            case 7:
                synchronized (this) {
                    this.mSendTask = null;
                    if (this.mLinkState != 1 && this.mSendState == 4) {
                        this.mSendState = 1;
                        Log.d(TAG, "onP2pHandoverNotSupported()");
                        this.mEventListener.onP2pHandoverNotSupported();
                    }
                }
                break;
            case 9:
                synchronized (this) {
                    this.mSendState = 1;
                    this.mEventListener.onP2pTimeoutWaitingForLink();
                }
                break;
            case 10:
                synchronized (this) {
                    this.mSendTask = null;
                    if (this.mLinkState != 1 && this.mSendState == 4) {
                        this.mSendState = 1;
                        Log.d(TAG, "onP2pHandoverBusy()");
                        this.mEventListener.onP2pHandoverBusy();
                    }
                }
                break;
        }
        return DBG;
    }

    @Override // com.android.nfc.P2pEventListener.Callback
    public void onP2pSendConfirmed() {
        onP2pSendConfirmed(DBG);
    }

    private void onP2pSendConfirmed(boolean requireConfirmation) {
        Log.d(TAG, "onP2pSendConfirmed()");
        synchronized (this) {
            if (this.mLinkState != 1 && (!requireConfirmation || this.mSendState == 2)) {
                this.mSendState = 4;
                if (this.mLinkState == 2) {
                    if (this.mLlcpServicesConnected) {
                        sendNdefMessage();
                    }
                } else if (this.mLinkState == 3) {
                    scheduleTimeoutLocked(1, 5000);
                    this.mEventListener.onP2pSendDebounce();
                }
            }
        }
    }

    @Override // com.android.nfc.P2pEventListener.Callback
    public void onP2pCanceled() {
        synchronized (this) {
            this.mSendState = 6;
            if (this.mLinkState == 1) {
                this.mHandler.removeMessages(9);
            } else if (this.mLinkState == 3) {
                scheduleTimeoutLocked(1, LINK_SEND_CANCELED_DEBOUNCE_MS);
            }
        }
    }

    void scheduleTimeoutLocked(int what, int timeout) {
        this.mHandler.removeMessages(what);
        this.mHandler.sendEmptyMessageDelayed(what, timeout);
    }

    static String sendStateToString(int state) {
        if (state != 1) {
            if (state != 2) {
                if (state != 4) {
                    if (state != 5) {
                        if (state == 6) {
                            return "SEND_STATE_CANCELED";
                        }
                        return "<error>";
                    }
                    return "SEND_STATE_COMPLETE";
                }
                return "SEND_STATE_SENDING";
            }
            return "SEND_STATE_NEED_CONFIRMATION";
        }
        return "SEND_STATE_NOTHING_TO_SEND";
    }

    static String linkStateToString(int state) {
        if (state != 1) {
            if (state != 2) {
                if (state == 3) {
                    return "LINK_STATE_DEBOUNCE";
                }
                return "<error>";
            }
            return "LINK_STATE_UP";
        }
        return "LINK_STATE_DOWN";
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (this) {
            pw.println("mIsSendEnabled=" + this.mIsSendEnabled);
            pw.println("mIsReceiveEnabled=" + this.mIsReceiveEnabled);
            pw.println("mLinkState=" + linkStateToString(this.mLinkState));
            pw.println("mSendState=" + sendStateToString(this.mSendState));
            pw.println("mCallbackNdef=" + this.mCallbackNdef);
            pw.println("mMessageToSend=" + this.mMessageToSend);
            pw.println("mUrisToSend=" + this.mUrisToSend);
        }
    }
}
