package com.android.nfc;

import android.app.ActivityManager;
import android.app.Application;
import android.app.BroadcastOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.backup.BackupManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.nfc.BeamShareData;
import android.nfc.FormatException;
import android.nfc.IAppCallback;
import android.nfc.INfcAdapter;
import android.nfc.INfcAdapterExtras;
import android.nfc.INfcCardEmulation;
import android.nfc.INfcDta;
import android.nfc.INfcFCardEmulation;
import android.nfc.INfcTag;
import android.nfc.INfcUnlockHandler;
import android.nfc.ITagRemovedCallback;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.TechListParcel;
import android.nfc.TransceiveResult;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.se.omapi.ISecureElementService;
import android.service.vr.IVrManager;
import android.service.vr.IVrStateCallbacks;
import android.text.TextUtils;
import android.util.Log;
import android.util.StatsLog;
import android.widget.Toast;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.util.ArrayUtils;
import com.android.nfc.DeviceHost;
import com.android.nfc.NfcDiscoveryParameters;
import com.android.nfc.cardemulation.CardEmulationManager;
import com.android.nfc.dhimpl.NativeNfcManager;
import com.android.nfc.handover.HandoverDataParser;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
/* loaded from: classes.dex */
public class NfcService implements DeviceHost.DeviceHostListener {
    public static final String ACTION_LLCP_DOWN = "com.android.nfc.action.LLCP_DOWN";
    public static final String ACTION_LLCP_UP = "com.android.nfc.action.LLCP_UP";
    public static final String ACTION_RF_FIELD_OFF_DETECTED = "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED";
    public static final String ACTION_RF_FIELD_ON_DETECTED = "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED";
    static final boolean ANTENNA_BLOCKED_MESSAGE_SHOWN_DEFAULT = false;
    private static final int APPLY_ROUTING_RETRY_TIMEOUT_MS = 5000;
    static final boolean DBG = false;
    static final int DEFAULT_PRESENCE_CHECK_DELAY = 125;
    static final int INIT_WATCHDOG_MS = 90000;
    static final int INVALID_NATIVE_HANDLE = -1;
    static final int INVOKE_BEAM_DELAY_MS = 1000;
    static final long MAX_POLLING_PAUSE_TIMEOUT = 40000;
    static final int MSG_APPLY_SCREEN_STATE = 16;
    static final int MSG_COMMIT_ROUTING = 7;
    static final int MSG_DEREGISTER_T3T_IDENTIFIER = 13;
    static final int MSG_INVOKE_BEAM = 8;
    static final int MSG_LLCP_LINK_ACTIVATION = 1;
    static final int MSG_LLCP_LINK_DEACTIVATED = 2;
    static final int MSG_LLCP_LINK_FIRST_PACKET = 4;
    static final int MSG_MOCK_NDEF = 3;
    static final int MSG_NDEF_TAG = 0;
    static final int MSG_REGISTER_T3T_IDENTIFIER = 12;
    static final int MSG_RESUME_POLLING = 11;
    static final int MSG_RF_FIELD_ACTIVATED = 9;
    static final int MSG_RF_FIELD_DEACTIVATED = 10;
    static final int MSG_ROUTE_AID = 5;
    static final int MSG_TAG_DEBOUNCE = 14;
    static final int MSG_TRANSACTION_EVENT = 17;
    static final int MSG_UNROUTE_AID = 6;
    static final int MSG_UPDATE_STATS = 15;
    static final String NATIVE_LOG_FILE_NAME = "native_logs";
    public static final int NCI_VERSION_1_0 = 16;
    public static final int NCI_VERSION_2_0 = 32;
    static final boolean NDEF_PUSH_ON_DEFAULT = false;
    static final boolean NFC_ON_DEFAULT = true;
    static final int NFC_POLLING_MODE = 8;
    static final int NFC_POLL_A = 1;
    static final int NFC_POLL_B = 2;
    static final int NFC_POLL_B_PRIME = 16;
    static final int NFC_POLL_F = 4;
    static final int NFC_POLL_KOVIO = 32;
    static final int NFC_POLL_V = 8;
    static final int NO_POLL_DELAY = -1;
    public static final String PREF = "NfcServicePrefs";
    static final String PREF_ANTENNA_BLOCKED_MESSAGE_SHOWN = "antenna_blocked_message_shown";
    static final String PREF_FIRST_BEAM = "first_beam";
    static final String PREF_FIRST_BOOT = "first_boot";
    static final String PREF_NDEF_PUSH_ON = "ndef_push_on";
    static final String PREF_NFC_ON = "nfc_on";
    static final String PREF_SECURE_NFC_ON = "secure_nfc_on";
    static final int ROUTING_WATCHDOG_MS = 10000;
    static final boolean SECURE_NFC_ON_DEFAULT = false;
    public static final String SERVICE_NAME = "nfc";
    public static final int SOUND_END = 1;
    public static final int SOUND_ERROR = 2;
    public static final int SOUND_START = 0;
    static final long STATS_UPDATE_INTERVAL_MS = 14400000;
    static final String TAG = "NfcService";
    static final int TASK_BOOT = 3;
    static final int TASK_DISABLE = 2;
    static final int TASK_ENABLE = 1;
    static final String TRON_NFC_CE = "nfc_ce";
    static final String TRON_NFC_P2P = "nfc_p2p";
    static final String TRON_NFC_TAG = "nfc_tag";
    private static int mDispatchFailedCount;
    private static int mDispatchFailedMax;
    private static Toast mToast;
    private static NfcService sService;
    boolean mAntennaBlockedMessageShown;
    private final BackupManager mBackupManager;
    private CardEmulationManager mCardEmulationManager;
    private ContentResolver mContentResolver;
    Context mContext;
    int mDebounceTagDebounceMs;
    ITagRemovedCallback mDebounceTagRemovedCallback;
    byte[] mDebounceTagUid;
    private DeviceHost mDeviceHost;
    int mEndSound;
    int mErrorSound;
    private ForegroundUtils mForegroundUtils;
    private HandoverDataParser mHandoverDataParser;
    boolean mInProvisionMode;
    boolean mIsBeamCapable;
    boolean mIsDebugBuild;
    boolean mIsHceCapable;
    boolean mIsHceFCapable;
    boolean mIsNdefPushEnabled;
    boolean mIsSecureNfcCapable;
    boolean mIsSecureNfcEnabled;
    boolean mIsVrModeEnabled;
    private KeyguardManager mKeyguard;
    NdefMessage mLastReadNdefMessage;
    private NfcDispatcher mNfcDispatcher;
    NfcDtaService mNfcDtaService;
    private final NfcUnlockManager mNfcUnlockManager;
    boolean mNotifyDispatchFailed;
    AtomicInteger mNumHceDetected;
    AtomicInteger mNumP2pDetected;
    AtomicInteger mNumTagsDetected;
    P2pLinkManager mP2pLinkManager;
    int mPollDelay;
    boolean mPollingPaused;
    private PowerManager mPowerManager;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEditor;
    ReaderModeParams mReaderModeParams;
    private PowerManager.WakeLock mRoutingWakeLock;
    private ISecureElementService mSEService;
    int mScreenState;
    private ScreenStateHelper mScreenStateHelper;
    SoundPool mSoundPool;
    int mStartSound;
    int mState;
    private final UserManager mUserManager;
    private VibrationEffect mVibrationEffect;
    private Vibrator mVibrator;
    private IVrManager vrManager;
    public static boolean sIsShortRecordLayout = false;
    private static int nci_version = 16;
    public static boolean sIsDtaMode = false;
    private final ReaderModeDeathRecipient mReaderModeDeathRecipient = new ReaderModeDeathRecipient();
    List<String> mNfcEventInstalledPackages = new ArrayList();
    final HashMap<Integer, Object> mObjectMap = new HashMap<>();
    NfcDiscoveryParameters mCurrentDiscoveryParameters = NfcDiscoveryParameters.getNfcOffParameters();
    int mDebounceTagNativeHandle = -1;
    private NfcServiceHandler mHandler = new NfcServiceHandler();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.nfc.NfcService.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            int i;
            String action = intent.getAction();
            if (action.equals("android.intent.action.SCREEN_ON") || action.equals("android.intent.action.SCREEN_OFF") || action.equals("android.intent.action.USER_PRESENT")) {
                int screenState = NfcService.this.mScreenStateHelper.checkScreenState();
                if (action.equals("android.intent.action.SCREEN_OFF")) {
                    if (NfcService.this.mScreenState != 2) {
                        screenState = NfcService.this.mKeyguard.isKeyguardLocked() ? 2 : 1;
                    }
                } else if (action.equals("android.intent.action.SCREEN_ON")) {
                    if (NfcService.this.mKeyguard.isKeyguardLocked()) {
                        i = 4;
                    } else {
                        i = 8;
                    }
                    screenState = i;
                } else if (action.equals("android.intent.action.USER_PRESENT")) {
                    screenState = 8;
                }
                if (NfcService.nci_version != 32) {
                    new ApplyRoutingTask().execute(Integer.valueOf(screenState));
                }
                NfcService.this.sendMessage(16, Integer.valueOf(screenState));
            } else if (action.equals("android.intent.action.USER_SWITCHED")) {
                int userId = intent.getIntExtra("android.intent.extra.user_handle", 0);
                if (NfcService.this.mIsBeamCapable) {
                    int beamSetting = 0;
                    try {
                        IPackageManager mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
                        beamSetting = mIpm.getComponentEnabledSetting(new ComponentName(BeamShareActivity.class.getPackageName$(), BeamShareActivity.class.getName()), userId);
                    } catch (RemoteException e) {
                        Log.e(NfcService.TAG, "Error int getComponentEnabledSetting for BeamShareActivity");
                    }
                    synchronized (this) {
                        NfcService.this.mUserId = userId;
                        if (beamSetting == 2) {
                            NfcService.this.mIsNdefPushEnabled = false;
                        } else {
                            NfcService.this.mIsNdefPushEnabled = NfcService.NFC_ON_DEFAULT;
                        }
                        UserManager um = (UserManager) NfcService.this.mContext.getSystemService("user");
                        List<UserHandle> luh = um.getUserProfiles();
                        for (UserHandle uh : luh) {
                            NfcService.this.enforceBeamShareActivityPolicy(NfcService.this.mContext, uh);
                        }
                        NfcService.this.enforceBeamShareActivityPolicy(NfcService.this.mContext, new UserHandle(NfcService.this.mUserId));
                    }
                    NfcService.this.mP2pLinkManager.onUserSwitched(NfcService.this.getUserId());
                }
                if (NfcService.this.mIsHceCapable) {
                    NfcService.this.mCardEmulationManager.onUserSwitched(NfcService.this.getUserId());
                }
                int screenState2 = NfcService.this.mScreenStateHelper.checkScreenState();
                if (screenState2 != NfcService.this.mScreenState) {
                    new ApplyRoutingTask().execute(Integer.valueOf(screenState2));
                }
            }
        }
    };
    private final BroadcastReceiver mOwnerReceiver = new BroadcastReceiver() { // from class: com.android.nfc.NfcService.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.PACKAGE_REMOVED") || action.equals("android.intent.action.PACKAGE_ADDED") || action.equals("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE") || action.equals("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE")) {
                NfcService.this.updatePackageCache();
            } else if (action.equals("android.intent.action.ACTION_SHUTDOWN") && NfcService.this.isNfcEnabled()) {
                NfcService.this.mDeviceHost.shutdown();
            }
        }
    };
    private final BroadcastReceiver mPolicyReceiver = new BroadcastReceiver() { // from class: com.android.nfc.NfcService.3
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED".equals(action)) {
                NfcService.this.enforceBeamShareActivityPolicy(context, new UserHandle(getSendingUserId()));
            }
        }
    };
    private final IVrStateCallbacks mVrStateCallbacks = new IVrStateCallbacks.Stub() { // from class: com.android.nfc.NfcService.4
        public void onVrStateChanged(boolean enabled) {
            synchronized (this) {
                NfcService.this.mIsVrModeEnabled = enabled;
            }
        }
    };
    private int mUserId = ActivityManager.getCurrentUser();
    TagService mNfcTagService = new TagService();
    NfcAdapterService mNfcAdapter = new NfcAdapterService();

    static /* synthetic */ int access$2508() {
        int i = mDispatchFailedCount;
        mDispatchFailedCount = i + 1;
        return i;
    }

    public static NfcService getInstance() {
        return sService;
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onRemoteEndpointDiscovered(DeviceHost.TagEndpoint tag) {
        sendMessage(0, tag);
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onHostCardEmulationActivated(int technology) {
        CardEmulationManager cardEmulationManager = this.mCardEmulationManager;
        if (cardEmulationManager != null) {
            cardEmulationManager.onHostCardEmulationActivated(technology);
        }
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onHostCardEmulationData(int technology, byte[] data) {
        CardEmulationManager cardEmulationManager = this.mCardEmulationManager;
        if (cardEmulationManager != null) {
            cardEmulationManager.onHostCardEmulationData(technology, data);
        }
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onHostCardEmulationDeactivated(int technology) {
        if (this.mCardEmulationManager != null) {
            this.mNumHceDetected.incrementAndGet();
            this.mCardEmulationManager.onHostCardEmulationDeactivated(technology);
        }
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onLlcpLinkActivated(DeviceHost.NfcDepEndpoint device) {
        if (this.mIsBeamCapable) {
            sendMessage(1, device);
        }
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onLlcpLinkDeactivated(DeviceHost.NfcDepEndpoint device) {
        if (this.mIsBeamCapable) {
            sendMessage(2, device);
        }
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onLlcpFirstPacketReceived(DeviceHost.NfcDepEndpoint device) {
        if (this.mIsBeamCapable) {
            this.mNumP2pDetected.incrementAndGet();
            sendMessage(4, device);
        }
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onRemoteFieldActivated() {
        sendMessage(9, null);
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onRemoteFieldDeactivated() {
        sendMessage(10, null);
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onNfcTransactionEvent(byte[] aid, byte[] data, String seName) {
        byte[][] dataObj = {aid, data, seName.getBytes()};
        sendMessage(17, dataObj);
        StatsLog.write(137, 3, seName);
    }

    @Override // com.android.nfc.DeviceHost.DeviceHostListener
    public void onEeUpdated() {
        new ApplyRoutingTask().execute(new Integer[0]);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class ReaderModeParams {
        public IAppCallback callback;
        public int flags;
        public int presenceCheckDelay;

        ReaderModeParams() {
        }
    }

    public NfcService(Application nfcApplication) {
        this.mContext = nfcApplication;
        Log.i(TAG, "Starting NFC service");
        sService = this;
        this.mScreenStateHelper = new ScreenStateHelper(this.mContext);
        this.mContentResolver = this.mContext.getContentResolver();
        this.mDeviceHost = new NativeNfcManager(this.mContext, this);
        this.mNfcUnlockManager = NfcUnlockManager.getInstance();
        this.mHandoverDataParser = new HandoverDataParser();
        boolean isNfcProvisioningEnabled = false;
        try {
            isNfcProvisioningEnabled = this.mContext.getResources().getBoolean(R.bool.enable_nfc_provisioning);
        } catch (Resources.NotFoundException e) {
        }
        if (isNfcProvisioningEnabled) {
            this.mInProvisionMode = Settings.Global.getInt(this.mContentResolver, "device_provisioned", 0) == 0;
        } else {
            this.mInProvisionMode = false;
        }
        this.mNfcDispatcher = new NfcDispatcher(this.mContext, this.mHandoverDataParser, this.mInProvisionMode);
        this.mPrefs = this.mContext.getSharedPreferences(PREF, 0);
        this.mPrefsEditor = this.mPrefs.edit();
        this.mState = 1;
        this.mIsDebugBuild = "userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE);
        this.mPowerManager = (PowerManager) this.mContext.getSystemService("power");
        this.mRoutingWakeLock = this.mPowerManager.newWakeLock(1, "NfcService:mRoutingWakeLock");
        this.mKeyguard = (KeyguardManager) this.mContext.getSystemService("keyguard");
        this.mUserManager = (UserManager) this.mContext.getSystemService("user");
        this.mVibrator = (Vibrator) this.mContext.getSystemService("vibrator");
        this.mVibrationEffect = VibrationEffect.createOneShot(200L, -1);
        this.mScreenState = this.mScreenStateHelper.checkScreenState();
        this.mNumTagsDetected = new AtomicInteger();
        this.mNumP2pDetected = new AtomicInteger();
        this.mNumHceDetected = new AtomicInteger();
        this.mBackupManager = new BackupManager(this.mContext);
        IntentFilter filter = new IntentFilter("android.intent.action.SCREEN_OFF");
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.USER_PRESENT");
        filter.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, filter, null, null);
        IntentFilter ownerFilter = new IntentFilter("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
        ownerFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
        ownerFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        this.mContext.registerReceiver(this.mOwnerReceiver, ownerFilter);
        IntentFilter ownerFilter2 = new IntentFilter();
        ownerFilter2.addAction("android.intent.action.PACKAGE_ADDED");
        ownerFilter2.addAction("android.intent.action.PACKAGE_REMOVED");
        ownerFilter2.addDataScheme("package");
        this.mContext.registerReceiver(this.mOwnerReceiver, ownerFilter2);
        IntentFilter policyFilter = new IntentFilter("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED");
        this.mContext.registerReceiverAsUser(this.mPolicyReceiver, UserHandle.ALL, policyFilter, null, null);
        updatePackageCache();
        PackageManager pm = this.mContext.getPackageManager();
        this.mIsBeamCapable = pm.hasSystemFeature("android.sofware.nfc.beam");
        this.mIsNdefPushEnabled = this.mPrefs.getBoolean(PREF_NDEF_PUSH_ON, false) && this.mIsBeamCapable;
        if (this.mIsBeamCapable) {
            this.mP2pLinkManager = new P2pLinkManager(this.mContext, this.mHandoverDataParser, this.mDeviceHost.getDefaultLlcpMiu(), this.mDeviceHost.getDefaultLlcpRwSize());
        }
        enforceBeamShareActivityPolicy(this.mContext, new UserHandle(this.mUserId));
        this.mIsHceCapable = pm.hasSystemFeature("android.hardware.nfc.hce") || pm.hasSystemFeature("android.hardware.nfc.hcef");
        this.mIsHceFCapable = pm.hasSystemFeature("android.hardware.nfc.hcef");
        if (this.mIsHceCapable) {
            this.mCardEmulationManager = new CardEmulationManager(this.mContext);
        }
        this.mForegroundUtils = ForegroundUtils.getInstance();
        this.mIsSecureNfcCapable = this.mNfcAdapter.deviceSupportsNfcSecure();
        this.mIsSecureNfcEnabled = this.mPrefs.getBoolean(PREF_SECURE_NFC_ON, false) && this.mIsSecureNfcCapable;
        this.mDeviceHost.setNfcSecure(this.mIsSecureNfcEnabled);
        mDispatchFailedCount = 0;
        if (this.mContext.getResources().getBoolean(R.bool.enable_antenna_blocked_alert) && !this.mPrefs.getBoolean(PREF_ANTENNA_BLOCKED_MESSAGE_SHOWN, false)) {
            this.mAntennaBlockedMessageShown = false;
            mDispatchFailedMax = this.mContext.getResources().getInteger(R.integer.max_antenna_blocked_failure_count);
        } else {
            this.mAntennaBlockedMessageShown = NFC_ON_DEFAULT;
        }
        this.mPollDelay = this.mContext.getResources().getInteger(R.integer.unknown_tag_polling_delay);
        this.mNotifyDispatchFailed = this.mContext.getResources().getBoolean(R.bool.enable_notify_dispatch_failed);
        ServiceManager.addService(SERVICE_NAME, this.mNfcAdapter);
        new EnableDisableTask().execute(3);
        this.mHandler.sendEmptyMessageDelayed(15, STATS_UPDATE_INTERVAL_MS);
        Context context = this.mContext;
        IVrManager mVrManager = IVrManager.Stub.asInterface(ServiceManager.getService("vrmanager"));
        if (mVrManager != null) {
            try {
                mVrManager.registerListener(this.mVrStateCallbacks);
                this.mIsVrModeEnabled = mVrManager.getVrModeState();
            } catch (RemoteException e2) {
                Log.e(TAG, "Failed to register VR mode state listener: " + e2);
            }
        }
        this.mSEService = ISecureElementService.Stub.asInterface(ServiceManager.getService("secure_element"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isSEServiceAvailable() {
        if (this.mSEService == null) {
            this.mSEService = ISecureElementService.Stub.asInterface(ServiceManager.getService("secure_element"));
        }
        if (this.mSEService != null) {
            return NFC_ON_DEFAULT;
        }
        return false;
    }

    void initSoundPool() {
        synchronized (this) {
            if (this.mSoundPool == null) {
                this.mSoundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder().setUsage(13).setContentType(4).build()).build();
                this.mStartSound = this.mSoundPool.load(this.mContext, R.raw.start, 1);
                this.mEndSound = this.mSoundPool.load(this.mContext, R.raw.end, 1);
                this.mErrorSound = this.mSoundPool.load(this.mContext, R.raw.error, 1);
            }
        }
    }

    void releaseSoundPool() {
        synchronized (this) {
            if (this.mSoundPool != null) {
                this.mSoundPool.release();
                this.mSoundPool = null;
            }
        }
    }

    void updatePackageCache() {
        PackageManager pm = this.mContext.getPackageManager();
        List<PackageInfo> packagesNfcEvents = pm.getPackagesHoldingPermissions(new String[]{"android.permission.NFC_TRANSACTION_EVENT"}, 1);
        synchronized (this) {
            this.mNfcEventInstalledPackages.clear();
            for (int i = 0; i < packagesNfcEvents.size(); i++) {
                this.mNfcEventInstalledPackages.add(packagesNfcEvents.get(i).packageName);
            }
        }
    }

    /* loaded from: classes.dex */
    class EnableDisableTask extends AsyncTask<Integer, Void, Void> {
        EnableDisableTask() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Void doInBackground(Integer... params) {
            boolean initialized;
            int i = NfcService.this.mState;
            if (i == 2 || i == 4) {
                Log.e(NfcService.TAG, "Processing EnableDisable task " + params[0] + " from bad state " + NfcService.this.mState);
                return null;
            }
            Process.setThreadPriority(0);
            int intValue = params[0].intValue();
            if (intValue == 1) {
                enableInternal();
            } else if (intValue == 2) {
                disableInternal();
            } else if (intValue == 3) {
                if (NfcService.this.mPrefs.getBoolean(NfcService.PREF_FIRST_BOOT, NfcService.NFC_ON_DEFAULT)) {
                    Log.i(NfcService.TAG, "First Boot");
                    NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_FIRST_BOOT, false);
                    NfcService.this.mPrefsEditor.apply();
                    NfcService.this.mDeviceHost.factoryReset();
                }
                Log.d(NfcService.TAG, "checking on firmware download");
                if (NfcService.this.mPrefs.getBoolean(NfcService.PREF_NFC_ON, NfcService.NFC_ON_DEFAULT)) {
                    Log.d(NfcService.TAG, "NFC is on. Doing normal stuff");
                    initialized = enableInternal();
                } else {
                    Log.d(NfcService.TAG, "NFC is off.  Checking firmware version");
                    initialized = NfcService.this.mDeviceHost.checkFirmware();
                }
                if (initialized) {
                    SystemProperties.set("nfc.initialized", "true");
                }
            }
            Process.setThreadPriority(10);
            return null;
        }

        boolean enableInternal() {
            if (NfcService.this.mState == 3) {
                return NfcService.NFC_ON_DEFAULT;
            }
            Log.i(NfcService.TAG, "Enabling NFC");
            StatsLog.write(135, 2);
            updateState(2);
            WatchDogThread watchDog = new WatchDogThread("enableInternal", NfcService.INIT_WATCHDOG_MS);
            watchDog.start();
            try {
                NfcService.this.mRoutingWakeLock.acquire();
                if (NfcService.this.mDeviceHost.initialize()) {
                    NfcService.this.mRoutingWakeLock.release();
                    watchDog.cancel();
                    if (NfcService.this.mIsHceCapable) {
                        NfcService.this.mCardEmulationManager.onNfcEnabled();
                    }
                    int unused = NfcService.nci_version = NfcService.this.getNciVersion();
                    Log.d(NfcService.TAG, "NCI_Version: " + NfcService.nci_version);
                    synchronized (NfcService.this) {
                        NfcService.this.mObjectMap.clear();
                        if (NfcService.this.mIsBeamCapable) {
                            NfcService.this.mP2pLinkManager.enableDisable(NfcService.this.mIsNdefPushEnabled, NfcService.NFC_ON_DEFAULT);
                        }
                        updateState(3);
                    }
                    NfcService.this.initSoundPool();
                    NfcService nfcService = NfcService.this;
                    nfcService.mScreenState = nfcService.mScreenStateHelper.checkScreenState();
                    int screen_state_mask = NfcService.this.mNfcUnlockManager.isLockscreenPollingEnabled() ? NfcService.this.mScreenState | 16 : NfcService.this.mScreenState;
                    if (NfcService.this.mNfcUnlockManager.isLockscreenPollingEnabled()) {
                        NfcService.this.applyRouting(false);
                    }
                    NfcService.this.mDeviceHost.doSetScreenState(screen_state_mask);
                    NfcService.this.applyRouting(NfcService.NFC_ON_DEFAULT);
                    return NfcService.NFC_ON_DEFAULT;
                }
                Log.w(NfcService.TAG, "Error enabling NFC");
                updateState(1);
                NfcService.this.mRoutingWakeLock.release();
                return false;
            } finally {
                watchDog.cancel();
            }
        }

        boolean disableInternal() {
            if (NfcService.this.mState == 1) {
                return NfcService.NFC_ON_DEFAULT;
            }
            Log.i(NfcService.TAG, "Disabling NFC");
            StatsLog.write(135, 1);
            updateState(4);
            WatchDogThread watchDog = new WatchDogThread("disableInternal", NfcService.ROUTING_WATCHDOG_MS);
            watchDog.start();
            if (NfcService.this.mIsHceCapable) {
                NfcService.this.mCardEmulationManager.onNfcDisabled();
            }
            if (NfcService.this.mIsBeamCapable) {
                NfcService.this.mP2pLinkManager.enableDisable(false, false);
            }
            NfcService.this.maybeDisconnectTarget();
            NfcService.this.mNfcDispatcher.setForegroundDispatch(null, null, null);
            boolean result = NfcService.this.mDeviceHost.deinitialize();
            watchDog.cancel();
            synchronized (NfcService.this) {
                NfcService.this.mCurrentDiscoveryParameters = NfcDiscoveryParameters.getNfcOffParameters();
                updateState(1);
            }
            NfcService.this.releaseSoundPool();
            return result;
        }

        void updateState(int newState) {
            synchronized (NfcService.this) {
                if (newState == NfcService.this.mState) {
                    return;
                }
                NfcService.this.mState = newState;
                Intent intent = new Intent("android.nfc.action.ADAPTER_STATE_CHANGED");
                intent.setFlags(67108864);
                intent.putExtra("android.nfc.extra.ADAPTER_STATE", NfcService.this.mState);
                NfcService.this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
            }
        }
    }

    void saveNfcOnSetting(boolean on) {
        synchronized (this) {
            this.mPrefsEditor.putBoolean(PREF_NFC_ON, on);
            this.mPrefsEditor.apply();
            this.mBackupManager.dataChanged();
        }
    }

    public void playSound(int sound) {
        synchronized (this) {
            if (this.mSoundPool == null) {
                Log.w(TAG, "Not playing sound when NFC is disabled");
            } else if (this.mIsVrModeEnabled) {
                Log.d(TAG, "Not playing NFC sound when Vr Mode is enabled");
            } else {
                if (sound == 0) {
                    this.mSoundPool.play(this.mStartSound, 1.0f, 1.0f, 0, 0, 1.0f);
                } else if (sound == 1) {
                    this.mSoundPool.play(this.mEndSound, 1.0f, 1.0f, 0, 0, 1.0f);
                } else if (sound == 2) {
                    this.mSoundPool.play(this.mErrorSound, 1.0f, 1.0f, 0, 0, 1.0f);
                }
            }
        }
    }

    synchronized int getUserId() {
        return this.mUserId;
    }

    void enforceBeamShareActivityPolicy(Context context, UserHandle uh) {
        int i;
        UserManager um = (UserManager) context.getSystemService("user");
        IPackageManager mIpm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        boolean isGlobalEnabled = this.mIsNdefPushEnabled;
        boolean isActiveForUser = !um.hasUserRestriction("no_outgoing_beam", uh) && isGlobalEnabled && this.mIsBeamCapable;
        try {
            ComponentName componentName = new ComponentName(BeamShareActivity.class.getPackageName$(), BeamShareActivity.class.getName());
            if (isActiveForUser) {
                i = 1;
            } else {
                i = 2;
            }
            mIpm.setComponentEnabledSetting(componentName, i, 1, uh.getIdentifier());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to change Beam status for user " + uh);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class NfcAdapterService extends INfcAdapter.Stub {
        NfcAdapterService() {
        }

        public boolean enable() throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            NfcService.this.saveNfcOnSetting(NfcService.NFC_ON_DEFAULT);
            new EnableDisableTask().execute(1);
            return NfcService.NFC_ON_DEFAULT;
        }

        public boolean disable(boolean saveState) throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (saveState) {
                NfcService.this.saveNfcOnSetting(false);
            }
            new EnableDisableTask().execute(2);
            return NfcService.NFC_ON_DEFAULT;
        }

        public void pausePolling(int timeoutInMs) {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (timeoutInMs <= 0 || timeoutInMs > NfcService.MAX_POLLING_PAUSE_TIMEOUT) {
                Log.e(NfcService.TAG, "Refusing to pause polling for " + timeoutInMs + "ms.");
                return;
            }
            synchronized (NfcService.this) {
                NfcService.this.mPollingPaused = NfcService.NFC_ON_DEFAULT;
                NfcService.this.mDeviceHost.disableDiscovery();
                NfcService.this.mHandler.sendMessageDelayed(NfcService.this.mHandler.obtainMessage(11), timeoutInMs);
            }
        }

        public void resumePolling() {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            synchronized (NfcService.this) {
                if (NfcService.this.mPollingPaused) {
                    NfcService.this.mHandler.removeMessages(11);
                    NfcService.this.mPollingPaused = false;
                    new ApplyRoutingTask().execute(new Integer[0]);
                }
            }
        }

        public boolean isNdefPushEnabled() throws RemoteException {
            boolean z;
            synchronized (NfcService.this) {
                z = (NfcService.this.mState == 3 && NfcService.this.mIsNdefPushEnabled) ? NfcService.NFC_ON_DEFAULT : false;
            }
            return z;
        }

        public boolean enableNdefPush() throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            synchronized (NfcService.this) {
                if (!NfcService.this.mIsNdefPushEnabled && NfcService.this.mIsBeamCapable) {
                    Log.i(NfcService.TAG, "enabling NDEF Push");
                    NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_NDEF_PUSH_ON, NfcService.NFC_ON_DEFAULT);
                    NfcService.this.mPrefsEditor.apply();
                    NfcService.this.mIsNdefPushEnabled = NfcService.NFC_ON_DEFAULT;
                    UserManager um = (UserManager) NfcService.this.mContext.getSystemService("user");
                    List<UserHandle> luh = um.getUserProfiles();
                    for (UserHandle uh : luh) {
                        NfcService.this.enforceBeamShareActivityPolicy(NfcService.this.mContext, uh);
                    }
                    NfcService.this.enforceBeamShareActivityPolicy(NfcService.this.mContext, new UserHandle(NfcService.this.mUserId));
                    if (NfcService.this.isNfcEnabled()) {
                        NfcService.this.mP2pLinkManager.enableDisable(NfcService.NFC_ON_DEFAULT, NfcService.NFC_ON_DEFAULT);
                    }
                    NfcService.this.mBackupManager.dataChanged();
                    return NfcService.NFC_ON_DEFAULT;
                }
                return NfcService.NFC_ON_DEFAULT;
            }
        }

        public boolean isNfcSecureEnabled() throws RemoteException {
            boolean z;
            synchronized (NfcService.this) {
                z = NfcService.this.mIsSecureNfcEnabled;
            }
            return z;
        }

        public boolean setNfcSecure(boolean enable) {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (NfcService.this.mKeyguard.isKeyguardLocked() && !enable) {
                Log.i(NfcService.TAG, "KeyGuard need to be unlocked before setting Secure NFC OFF");
                return false;
            }
            synchronized (NfcService.this) {
                Log.i(NfcService.TAG, "setting Secure NFC " + enable);
                NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_SECURE_NFC_ON, enable);
                NfcService.this.mPrefsEditor.apply();
                NfcService.this.mIsSecureNfcEnabled = enable;
                NfcService.this.mBackupManager.dataChanged();
                NfcService.this.mDeviceHost.setNfcSecure(enable);
            }
            if (NfcService.this.mIsHceCapable) {
                NfcService.this.mCardEmulationManager.onSecureNfcToggled();
            }
            if (enable) {
                StatsLog.write(135, 3);
                return NfcService.NFC_ON_DEFAULT;
            }
            StatsLog.write(135, 2);
            return NfcService.NFC_ON_DEFAULT;
        }

        public boolean disableNdefPush() throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            synchronized (NfcService.this) {
                if (NfcService.this.mIsNdefPushEnabled && NfcService.this.mIsBeamCapable) {
                    Log.i(NfcService.TAG, "disabling NDEF Push");
                    NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_NDEF_PUSH_ON, false);
                    NfcService.this.mPrefsEditor.apply();
                    NfcService.this.mIsNdefPushEnabled = false;
                    UserManager um = (UserManager) NfcService.this.mContext.getSystemService("user");
                    List<UserHandle> luh = um.getUserProfiles();
                    for (UserHandle uh : luh) {
                        NfcService.this.enforceBeamShareActivityPolicy(NfcService.this.mContext, uh);
                    }
                    NfcService.this.enforceBeamShareActivityPolicy(NfcService.this.mContext, new UserHandle(NfcService.this.mUserId));
                    if (NfcService.this.isNfcEnabled()) {
                        NfcService.this.mP2pLinkManager.enableDisable(false, NfcService.NFC_ON_DEFAULT);
                    }
                    NfcService.this.mBackupManager.dataChanged();
                    return NfcService.NFC_ON_DEFAULT;
                }
                return NfcService.NFC_ON_DEFAULT;
            }
        }

        public void setForegroundDispatch(PendingIntent intent, IntentFilter[] filters, TechListParcel techListsParcel) {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (!NfcService.this.mForegroundUtils.isInForeground(Binder.getCallingUid())) {
                Log.e(NfcService.TAG, "setForegroundDispatch: Caller not in foreground.");
            } else if (intent == null && filters == null && techListsParcel == null) {
                NfcService.this.mNfcDispatcher.setForegroundDispatch(null, null, null);
            } else {
                if (filters != null) {
                    if (filters.length == 0) {
                        filters = null;
                    } else {
                        for (IntentFilter filter : filters) {
                            if (filter == null) {
                                throw new IllegalArgumentException("null IntentFilter");
                            }
                        }
                    }
                }
                String[][] techLists = null;
                if (techListsParcel != null) {
                    techLists = techListsParcel.getTechLists();
                }
                NfcService.this.mNfcDispatcher.setForegroundDispatch(intent, filters, techLists);
            }
        }

        public void setAppCallback(IAppCallback callback) {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            UserInfo userInfo = NfcService.this.mUserManager.getUserInfo(UserHandle.getCallingUserId());
            if (!NfcService.this.mUserManager.hasUserRestriction("no_outgoing_beam", userInfo.getUserHandle()) && NfcService.this.mIsBeamCapable) {
                NfcService.this.mP2pLinkManager.setNdefCallback(callback, Binder.getCallingUid());
            }
        }

        public boolean ignore(int nativeHandle, int debounceMs, ITagRemovedCallback callback) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (debounceMs == 0 && NfcService.this.mDebounceTagNativeHandle != -1 && nativeHandle == NfcService.this.mDebounceTagNativeHandle) {
                NfcService.this.mHandler.removeMessages(14);
                NfcService.this.mHandler.sendEmptyMessage(14);
                return NfcService.NFC_ON_DEFAULT;
            }
            DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) NfcService.this.findAndRemoveObject(nativeHandle);
            if (tag == null) {
                return false;
            }
            int uidLength = tag.getUid().length;
            synchronized (NfcService.this) {
                NfcService.this.mDebounceTagDebounceMs = debounceMs;
                NfcService.this.mDebounceTagNativeHandle = nativeHandle;
                NfcService.this.mDebounceTagUid = new byte[uidLength];
                NfcService.this.mDebounceTagRemovedCallback = callback;
                System.arraycopy(tag.getUid(), 0, NfcService.this.mDebounceTagUid, 0, uidLength);
            }
            tag.disconnect();
            NfcService.this.mHandler.sendEmptyMessageDelayed(14, debounceMs);
            return NfcService.NFC_ON_DEFAULT;
        }

        public void verifyNfcPermission() {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
        }

        public void invokeBeam() {
            if (!NfcService.this.mIsBeamCapable) {
                return;
            }
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (NfcService.this.mForegroundUtils.isInForeground(Binder.getCallingUid())) {
                NfcService.this.mP2pLinkManager.onManualBeamInvoke(null);
            } else {
                Log.e(NfcService.TAG, "Calling activity not in foreground.");
            }
        }

        public void invokeBeamInternal(BeamShareData shareData) {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            Message msg = Message.obtain();
            msg.what = 8;
            msg.obj = shareData;
            NfcService.this.mHandler.sendMessageDelayed(msg, 1000L);
        }

        public INfcTag getNfcTagInterface() throws RemoteException {
            return NfcService.this.mNfcTagService;
        }

        public INfcCardEmulation getNfcCardEmulationInterface() {
            if (NfcService.this.mIsHceCapable) {
                return NfcService.this.mCardEmulationManager.getNfcCardEmulationInterface();
            }
            return null;
        }

        public INfcFCardEmulation getNfcFCardEmulationInterface() {
            if (NfcService.this.mIsHceFCapable) {
                return NfcService.this.mCardEmulationManager.getNfcFCardEmulationInterface();
            }
            return null;
        }

        public int getState() throws RemoteException {
            int i;
            synchronized (NfcService.this) {
                i = NfcService.this.mState;
            }
            return i;
        }

        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            NfcService.this.dump(fd, pw, args);
        }

        public void dispatch(Tag tag) throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            NfcService.this.mNfcDispatcher.dispatchTag(tag);
        }

        public void setP2pModes(int initiatorModes, int targetModes) throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            NfcService.this.mDeviceHost.setP2pInitiatorModes(initiatorModes);
            NfcService.this.mDeviceHost.setP2pTargetModes(targetModes);
            NfcService.this.applyRouting(NfcService.NFC_ON_DEFAULT);
        }

        public void setReaderMode(IBinder binder, IAppCallback callback, int flags, Bundle extras) throws RemoteException {
            int callingUid = Binder.getCallingUid();
            if (callingUid != 1000 && !NfcService.this.mForegroundUtils.isInForeground(callingUid)) {
                Log.e(NfcService.TAG, "setReaderMode: Caller is not in foreground and is not system process.");
                return;
            }
            synchronized (NfcService.this) {
                if (!NfcService.this.isNfcEnabled()) {
                    Log.e(NfcService.TAG, "setReaderMode() called while NFC is not enabled.");
                    return;
                }
                if (flags != 0) {
                    try {
                        NfcService.this.mReaderModeParams = new ReaderModeParams();
                        NfcService.this.mReaderModeParams.callback = callback;
                        NfcService.this.mReaderModeParams.flags = flags;
                        ReaderModeParams readerModeParams = NfcService.this.mReaderModeParams;
                        int i = NfcService.DEFAULT_PRESENCE_CHECK_DELAY;
                        if (extras != null) {
                            i = extras.getInt("presence", NfcService.DEFAULT_PRESENCE_CHECK_DELAY);
                        }
                        readerModeParams.presenceCheckDelay = i;
                        binder.linkToDeath(NfcService.this.mReaderModeDeathRecipient, 0);
                        NfcService.this.applyRouting(false);
                        return;
                    } catch (RemoteException e) {
                        Log.e(NfcService.TAG, "Remote binder has already died.");
                        return;
                    }
                }
                try {
                    NfcService.this.mReaderModeParams = null;
                    NfcService.this.StopPresenceChecking();
                    binder.unlinkToDeath(NfcService.this.mReaderModeDeathRecipient, 0);
                } catch (NoSuchElementException e2) {
                    Log.e(NfcService.TAG, "Reader mode Binder was never registered.");
                }
                NfcService.this.applyRouting(false);
                return;
            }
        }

        public INfcAdapterExtras getNfcAdapterExtrasInterface(String pkg) throws RemoteException {
            return null;
        }

        public INfcDta getNfcDtaInterface(String pkg) throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (NfcService.this.mNfcDtaService == null) {
                NfcService nfcService = NfcService.this;
                nfcService.mNfcDtaService = new NfcDtaService();
            }
            return NfcService.this.mNfcDtaService;
        }

        public void addNfcUnlockHandler(INfcUnlockHandler unlockHandler, int[] techList) {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            int lockscreenPollMask = computeLockscreenPollMask(techList);
            synchronized (NfcService.this) {
                NfcService.this.mNfcUnlockManager.addUnlockHandler(unlockHandler, lockscreenPollMask);
            }
            NfcService.this.applyRouting(false);
        }

        public void removeNfcUnlockHandler(INfcUnlockHandler token) throws RemoteException {
            synchronized (NfcService.this) {
                NfcService.this.mNfcUnlockManager.removeUnlockHandler(token.asBinder());
            }
            NfcService.this.applyRouting(false);
        }

        public boolean deviceSupportsNfcSecure() {
            String[] skuList = NfcService.this.mContext.getResources().getStringArray(R.array.config_skuSupportsSecureNfc);
            String sku = SystemProperties.get("ro.boot.hardware.sku");
            if (TextUtils.isEmpty(sku) || !ArrayUtils.contains(skuList, sku)) {
                return false;
            }
            return NfcService.NFC_ON_DEFAULT;
        }

        private int computeLockscreenPollMask(int[] techList) {
            Map<Integer, Integer> techCodeToMask = new HashMap<>();
            techCodeToMask.put(1, 1);
            techCodeToMask.put(2, 2);
            techCodeToMask.put(5, 8);
            techCodeToMask.put(4, 4);
            techCodeToMask.put(10, 32);
            int mask = 0;
            for (int i = 0; i < techList.length; i++) {
                if (techCodeToMask.containsKey(Integer.valueOf(techList[i]))) {
                    mask |= techCodeToMask.get(Integer.valueOf(techList[i])).intValue();
                }
            }
            return mask;
        }
    }

    /* loaded from: classes.dex */
    final class ReaderModeDeathRecipient implements IBinder.DeathRecipient {
        ReaderModeDeathRecipient() {
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (NfcService.this) {
                if (NfcService.this.mReaderModeParams != null) {
                    NfcService.this.mReaderModeParams = null;
                    NfcService.this.applyRouting(false);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class TagService extends INfcTag.Stub {
        TagService() {
        }

        public int connect(int nativeHandle, int technology) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null || !tag.isPresent() || !tag.connect(technology)) {
                return -5;
            }
            return 0;
        }

        public int reconnect(int nativeHandle) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null || !tag.reconnect()) {
                return -5;
            }
            return 0;
        }

        public int[] getTechList(int nativeHandle) throws RemoteException {
            DeviceHost.TagEndpoint tag;
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (NfcService.this.isNfcEnabled() && (tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle)) != null) {
                return tag.getTechList();
            }
            return null;
        }

        public boolean isPresent(int nativeHandle) throws RemoteException {
            DeviceHost.TagEndpoint tag;
            if (NfcService.this.isNfcEnabled() && (tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle)) != null) {
                return tag.isPresent();
            }
            return false;
        }

        public boolean isNdef(int nativeHandle) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (NfcService.this.isNfcEnabled()) {
                DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle);
                int[] ndefInfo = new int[2];
                if (tag == null) {
                    return false;
                }
                return tag.checkNdef(ndefInfo);
            }
            return false;
        }

        public TransceiveResult transceive(int nativeHandle, byte[] data, boolean raw) throws RemoteException {
            DeviceHost.TagEndpoint tag;
            int result;
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (NfcService.this.isNfcEnabled() && (tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle)) != null) {
                if (data.length > getMaxTransceiveLength(tag.getConnectedTechnology())) {
                    return new TransceiveResult(3, (byte[]) null);
                }
                int[] targetLost = new int[1];
                byte[] response = tag.transceive(data, raw, targetLost);
                if (response == null) {
                    if (targetLost[0] == 1) {
                        result = 2;
                    } else {
                        result = 1;
                    }
                } else {
                    result = 0;
                }
                return new TransceiveResult(result, response);
            }
            return null;
        }

        public NdefMessage ndefRead(int nativeHandle) throws RemoteException {
            DeviceHost.TagEndpoint tag;
            byte[] buf;
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (!NfcService.this.isNfcEnabled() || (tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle)) == null || (buf = tag.readNdef()) == null) {
                return null;
            }
            try {
                return new NdefMessage(buf);
            } catch (FormatException e) {
                return null;
            }
        }

        public int ndefWrite(int nativeHandle, NdefMessage msg) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null) {
                return -1;
            }
            if (msg == null) {
                return -8;
            }
            if (!tag.writeNdef(msg.toByteArray())) {
                return -1;
            }
            return 0;
        }

        public boolean ndefIsWritable(int nativeHandle) throws RemoteException {
            throw new UnsupportedOperationException();
        }

        public int ndefMakeReadOnly(int nativeHandle) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null || !tag.makeReadOnly()) {
                return -1;
            }
            return 0;
        }

        public int formatNdef(int nativeHandle, byte[] key) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null || !tag.formatNdef(key)) {
                return -1;
            }
            return 0;
        }

        public Tag rediscover(int nativeHandle) throws RemoteException {
            DeviceHost.TagEndpoint tag;
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            if (NfcService.this.isNfcEnabled() && (tag = (DeviceHost.TagEndpoint) NfcService.this.findObject(nativeHandle)) != null) {
                tag.removeTechnology(6);
                tag.removeTechnology(7);
                tag.findAndReadNdef();
                try {
                    Tag newTag = new Tag(tag.getUid(), tag.getTechList(), tag.getTechExtras(), tag.getHandle(), this);
                    return newTag;
                } catch (Exception e) {
                    Log.e(NfcService.TAG, "Tag creation exception.", e);
                    return null;
                }
            }
            return null;
        }

        public int setTimeout(int tech, int timeout) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            boolean success = NfcService.this.mDeviceHost.setTimeout(tech, timeout);
            if (success) {
                return 0;
            }
            return -8;
        }

        public int getTimeout(int tech) throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            return NfcService.this.mDeviceHost.getTimeout(tech);
        }

        public void resetTimeouts() throws RemoteException {
            NfcPermissions.enforceUserPermissions(NfcService.this.mContext);
            NfcService.this.mDeviceHost.resetTimeouts();
        }

        public boolean canMakeReadOnly(int ndefType) throws RemoteException {
            return NfcService.this.mDeviceHost.canMakeReadOnly(ndefType);
        }

        public int getMaxTransceiveLength(int tech) throws RemoteException {
            return NfcService.this.mDeviceHost.getMaxTransceiveLength(tech);
        }

        public boolean getExtendedLengthApdusSupported() throws RemoteException {
            return NfcService.this.mDeviceHost.getExtendedLengthApdusSupported();
        }
    }

    /* loaded from: classes.dex */
    final class NfcDtaService extends INfcDta.Stub {
        NfcDtaService() {
        }

        public void enableDta() throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (!NfcService.sIsDtaMode) {
                NfcService.this.mDeviceHost.enableDtaMode();
                NfcService.sIsDtaMode = NfcService.NFC_ON_DEFAULT;
                Log.d(NfcService.TAG, "DTA Mode is Enabled ");
            }
        }

        public void disableDta() throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (NfcService.sIsDtaMode) {
                NfcService.this.mDeviceHost.disableDtaMode();
                NfcService.sIsDtaMode = false;
            }
        }

        public boolean enableServer(String serviceName, int serviceSap, int miu, int rwSize, int testCaseId) throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (serviceName.equals(null) || !NfcService.this.mIsBeamCapable) {
                return false;
            }
            NfcService.this.mP2pLinkManager.enableExtDtaSnepServer(serviceName, serviceSap, miu, rwSize, testCaseId);
            return NfcService.NFC_ON_DEFAULT;
        }

        public void disableServer() throws RemoteException {
            if (!NfcService.this.mIsBeamCapable) {
                return;
            }
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            NfcService.this.mP2pLinkManager.disableExtDtaSnepServer();
        }

        public boolean enableClient(String serviceName, int miu, int rwSize, int testCaseId) throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (testCaseId == 0 || !NfcService.this.mIsBeamCapable) {
                return false;
            }
            if (testCaseId > 20) {
                NfcService.sIsShortRecordLayout = NfcService.NFC_ON_DEFAULT;
                testCaseId -= 20;
            } else {
                NfcService.sIsShortRecordLayout = false;
            }
            Log.d("testCaseId", "" + testCaseId);
            NfcService.this.mP2pLinkManager.enableDtaSnepClient(serviceName, miu, rwSize, testCaseId);
            return NfcService.NFC_ON_DEFAULT;
        }

        public void disableClient() throws RemoteException {
            if (!NfcService.this.mIsBeamCapable) {
                return;
            }
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            NfcService.this.mP2pLinkManager.disableDtaSnepClient();
        }

        public boolean registerMessageService(String msgServiceName) throws RemoteException {
            NfcPermissions.enforceAdminPermissions(NfcService.this.mContext);
            if (msgServiceName.equals(null)) {
                return false;
            }
            DtaServiceConnector.setMessageService(msgServiceName);
            return NfcService.NFC_ON_DEFAULT;
        }
    }

    boolean isNfcEnabledOrShuttingDown() {
        boolean z;
        synchronized (this) {
            if (this.mState != 3 && this.mState != 4) {
                z = false;
            }
            z = NFC_ON_DEFAULT;
        }
        return z;
    }

    boolean isNfcEnabled() {
        boolean z;
        synchronized (this) {
            z = this.mState == 3 ? NFC_ON_DEFAULT : false;
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public class WatchDogThread extends Thread {
        final Object mCancelWaiter;
        boolean mCanceled;
        final int mTimeout;

        public WatchDogThread(String threadName, int timeout) {
            super(threadName);
            this.mCancelWaiter = new Object();
            this.mCanceled = false;
            this.mTimeout = timeout;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            try {
            } catch (InterruptedException e) {
                Log.w(NfcService.TAG, "Watchdog thread interruped.");
                interrupt();
            }
            synchronized (this.mCancelWaiter) {
                this.mCancelWaiter.wait(this.mTimeout);
                if (this.mCanceled) {
                    return;
                }
                if (NfcService.this.mRoutingWakeLock.isHeld()) {
                    Log.e(NfcService.TAG, "Watchdog triggered, release lock before aborting.");
                    NfcService.this.mRoutingWakeLock.release();
                }
                Log.e(NfcService.TAG, "Watchdog triggered, aborting.");
                StatsLog.write(135, 4);
                NfcService.this.storeNativeCrashLogs();
                NfcService.this.mDeviceHost.doAbort(getName());
            }
        }

        public synchronized void cancel() {
            synchronized (this.mCancelWaiter) {
                this.mCanceled = NfcService.NFC_ON_DEFAULT;
                this.mCancelWaiter.notify();
            }
        }
    }

    static byte[] hexStringToBytes(String s) {
        if (s == null || s.length() == 0) {
            return null;
        }
        int len = s.length();
        if (len % 2 != 0) {
            s = '0' + s;
            len++;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    void applyRouting(boolean force) {
        synchronized (this) {
            if (isNfcEnabledOrShuttingDown()) {
                WatchDogThread watchDog = new WatchDogThread("applyRouting", ROUTING_WATCHDOG_MS);
                if (this.mInProvisionMode) {
                    this.mInProvisionMode = Settings.Global.getInt(this.mContentResolver, "device_provisioned", 0) == 0 ? NFC_ON_DEFAULT : false;
                    if (!this.mInProvisionMode) {
                        this.mNfcDispatcher.disableProvisioningMode();
                    }
                }
                if (this.mScreenState == 8 && isTagPresent()) {
                    Log.d(TAG, "Not updating discovery parameters, tag connected.");
                    this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(11), 5000L);
                    return;
                }
                watchDog.start();
                NfcDiscoveryParameters newParams = computeDiscoveryParameters(this.mScreenState);
                if (!force && newParams.equals(this.mCurrentDiscoveryParameters)) {
                    Log.d(TAG, "Discovery configuration equal, not updating.");
                    watchDog.cancel();
                }
                if (newParams.shouldEnableDiscovery()) {
                    boolean shouldRestart = this.mCurrentDiscoveryParameters.shouldEnableDiscovery();
                    this.mDeviceHost.enableDiscovery(newParams, shouldRestart);
                } else {
                    this.mDeviceHost.disableDiscovery();
                }
                this.mCurrentDiscoveryParameters = newParams;
                watchDog.cancel();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public NfcDiscoveryParameters computeDiscoveryParameters(int screenState) {
        NfcDiscoveryParameters.Builder paramsBuilder = NfcDiscoveryParameters.newBuilder();
        if (screenState >= 8) {
            ReaderModeParams readerModeParams = this.mReaderModeParams;
            if (readerModeParams != null) {
                int techMask = 0;
                if ((readerModeParams.flags & 1) != 0) {
                    techMask = 0 | 1;
                }
                if ((this.mReaderModeParams.flags & 2) != 0) {
                    techMask |= 2;
                }
                if ((this.mReaderModeParams.flags & 4) != 0) {
                    techMask |= 4;
                }
                if ((8 & this.mReaderModeParams.flags) != 0) {
                    techMask |= 8;
                }
                if ((this.mReaderModeParams.flags & 16) != 0) {
                    techMask |= 32;
                }
                paramsBuilder.setTechMask(techMask);
                paramsBuilder.setEnableReaderMode(NFC_ON_DEFAULT);
            } else {
                paramsBuilder.setTechMask(-1);
                paramsBuilder.setEnableP2p(this.mIsBeamCapable);
            }
        } else if (screenState == 4 && this.mInProvisionMode) {
            paramsBuilder.setTechMask(-1);
            paramsBuilder.setEnableP2p(this.mIsBeamCapable);
        } else if (screenState == 4 && this.mNfcUnlockManager.isLockscreenPollingEnabled()) {
            int techMask2 = this.mNfcUnlockManager.isLockscreenPollingEnabled() ? 0 | this.mNfcUnlockManager.getLockscreenPollMask() : 0;
            paramsBuilder.setTechMask(techMask2);
            paramsBuilder.setEnableLowPowerDiscovery(false);
            paramsBuilder.setEnableP2p(false);
        }
        if (this.mIsHceCapable && this.mScreenState >= 4 && this.mReaderModeParams == null) {
            paramsBuilder.setEnableHostRouting(NFC_ON_DEFAULT);
        }
        return paramsBuilder.build();
    }

    private boolean isTagPresent() {
        for (Object object : this.mObjectMap.values()) {
            if (object instanceof DeviceHost.TagEndpoint) {
                return ((DeviceHost.TagEndpoint) object).isPresent();
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void StopPresenceChecking() {
        Object[] objectValues = this.mObjectMap.values().toArray();
        for (Object object : objectValues) {
            if (object instanceof DeviceHost.TagEndpoint) {
                DeviceHost.TagEndpoint tagEndpoint = (DeviceHost.TagEndpoint) object;
                ((DeviceHost.TagEndpoint) object).stopPresenceChecking();
            }
        }
    }

    void maybeDisconnectTarget() {
        Object[] objectsToDisconnect;
        if (!isNfcEnabledOrShuttingDown()) {
            return;
        }
        synchronized (this) {
            Object[] objectValues = this.mObjectMap.values().toArray();
            objectsToDisconnect = Arrays.copyOf(objectValues, objectValues.length);
            this.mObjectMap.clear();
        }
        for (Object o : objectsToDisconnect) {
            if (o instanceof DeviceHost.TagEndpoint) {
                DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) o;
                tag.disconnect();
            } else if (o instanceof DeviceHost.NfcDepEndpoint) {
                DeviceHost.NfcDepEndpoint device = (DeviceHost.NfcDepEndpoint) o;
                if (device.getMode() == 0) {
                    device.disconnect();
                }
            }
        }
    }

    Object findObject(int key) {
        Object device;
        synchronized (this) {
            device = this.mObjectMap.get(Integer.valueOf(key));
            if (device == null) {
                Log.w(TAG, "Handle not found");
            }
        }
        return device;
    }

    Object findAndRemoveObject(int handle) {
        Object device;
        synchronized (this) {
            device = this.mObjectMap.get(Integer.valueOf(handle));
            if (device == null) {
                Log.w(TAG, "Handle not found");
            } else {
                this.mObjectMap.remove(Integer.valueOf(handle));
            }
        }
        return device;
    }

    void registerTagObject(DeviceHost.TagEndpoint tag) {
        synchronized (this) {
            this.mObjectMap.put(Integer.valueOf(tag.getHandle()), tag);
        }
    }

    void unregisterObject(int handle) {
        synchronized (this) {
            this.mObjectMap.remove(Integer.valueOf(handle));
        }
    }

    public DeviceHost.LlcpSocket createLlcpSocket(int sap, int miu, int rw, int linearBufferLength) throws LlcpException {
        return this.mDeviceHost.createLlcpSocket(sap, miu, rw, linearBufferLength);
    }

    public DeviceHost.LlcpConnectionlessSocket createLlcpConnectionLessSocket(int sap, String sn) throws LlcpException {
        return this.mDeviceHost.createLlcpConnectionlessSocket(sap, sn);
    }

    public DeviceHost.LlcpServerSocket createLlcpServerSocket(int sap, String sn, int miu, int rw, int linearBufferLength) throws LlcpException {
        return this.mDeviceHost.createLlcpServerSocket(sap, sn, miu, rw, linearBufferLength);
    }

    public int getAidRoutingTableSize() {
        int aidTableSize = this.mDeviceHost.getAidTableSize();
        return aidTableSize;
    }

    public void sendMockNdefTag(NdefMessage msg) {
        sendMessage(3, msg);
    }

    public void routeAids(String aid, int route, int aidInfo) {
        Message msg = this.mHandler.obtainMessage();
        msg.what = 5;
        msg.arg1 = route;
        msg.obj = aid;
        msg.arg2 = aidInfo;
        this.mHandler.sendMessage(msg);
    }

    public void unrouteAids(String aid) {
        sendMessage(6, aid);
    }

    public int getNciVersion() {
        return this.mDeviceHost.getNciVersion();
    }

    private byte[] getT3tIdentifierBytes(String systemCode, String nfcId2, String t3tPmm) {
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.put(hexStringToBytes(systemCode));
        buffer.put(hexStringToBytes(nfcId2));
        buffer.put(hexStringToBytes(t3tPmm));
        byte[] t3tIdBytes = new byte[buffer.position()];
        buffer.position(0);
        buffer.get(t3tIdBytes);
        return t3tIdBytes;
    }

    public void registerT3tIdentifier(String systemCode, String nfcId2, String t3tPmm) {
        Log.d(TAG, "request to register LF_T3T_IDENTIFIER");
        byte[] t3tIdentifier = getT3tIdentifierBytes(systemCode, nfcId2, t3tPmm);
        sendMessage(12, t3tIdentifier);
    }

    public void deregisterT3tIdentifier(String systemCode, String nfcId2, String t3tPmm) {
        Log.d(TAG, "request to deregister LF_T3T_IDENTIFIER");
        byte[] t3tIdentifier = getT3tIdentifierBytes(systemCode, nfcId2, t3tPmm);
        sendMessage(13, t3tIdentifier);
    }

    public void clearT3tIdentifiersCache() {
        Log.d(TAG, "clear T3t Identifiers Cache");
        this.mDeviceHost.clearT3tIdentifiersCache();
    }

    public int getLfT3tMax() {
        return this.mDeviceHost.getLfT3tMax();
    }

    public void commitRouting() {
        this.mHandler.sendEmptyMessage(7);
    }

    public boolean sendData(byte[] data) {
        return this.mDeviceHost.sendRawFrame(data);
    }

    void sendMessage(int what, Object obj) {
        Message msg = this.mHandler.obtainMessage();
        msg.what = what;
        msg.obj = obj;
        this.mHandler.sendMessage(msg);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class NfcServiceHandler extends Handler {
        NfcServiceHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            byte[] debounceTagUid;
            int debounceTagMs;
            ITagRemovedCallback debounceTagRemovedCallback;
            ReaderModeParams readerParams;
            ITagRemovedCallback tagRemovedCallback;
            switch (msg.what) {
                case 0:
                    if (NfcService.this.mScreenState == 8) {
                        NfcService.this.mPowerManager.userActivity(SystemClock.uptimeMillis(), 0, 0);
                    }
                    NfcService.this.mNumTagsDetected.incrementAndGet();
                    DeviceHost.TagEndpoint tag = (DeviceHost.TagEndpoint) msg.obj;
                    synchronized (NfcService.this) {
                        debounceTagUid = NfcService.this.mDebounceTagUid;
                        debounceTagMs = NfcService.this.mDebounceTagDebounceMs;
                        debounceTagRemovedCallback = NfcService.this.mDebounceTagRemovedCallback;
                    }
                    int presenceCheckDelay = NfcService.DEFAULT_PRESENCE_CHECK_DELAY;
                    DeviceHost.TagDisconnectedCallback callback = new DeviceHost.TagDisconnectedCallback() { // from class: com.android.nfc.NfcService.NfcServiceHandler.1
                        @Override // com.android.nfc.DeviceHost.TagDisconnectedCallback
                        public void onTagDisconnected(long handle) {
                            NfcService.this.applyRouting(false);
                        }
                    };
                    synchronized (NfcService.this) {
                        readerParams = NfcService.this.mReaderModeParams;
                    }
                    if (readerParams != null) {
                        presenceCheckDelay = readerParams.presenceCheckDelay;
                        if ((readerParams.flags & 128) != 0) {
                            tag.startPresenceChecking(presenceCheckDelay, callback);
                            dispatchTagEndpoint(tag, readerParams);
                            return;
                        }
                    }
                    if (tag.getConnectedTechnology() == 10) {
                        tag.startPresenceChecking(presenceCheckDelay, callback);
                        dispatchTagEndpoint(tag, readerParams);
                        return;
                    }
                    NdefMessage ndefMsg = tag.findAndReadNdef();
                    if (ndefMsg == null && !tag.reconnect()) {
                        tag.disconnect();
                        if (NfcService.this.mScreenState == 8) {
                            if (NfcService.mToast != null && NfcService.mToast.getView().isShown()) {
                                NfcService.mToast.cancel();
                            }
                            Toast unused = NfcService.mToast = Toast.makeText(NfcService.this.mContext, (int) R.string.tag_read_error, 0);
                            NfcService.mToast.show();
                            return;
                        }
                        return;
                    }
                    if (debounceTagUid != null) {
                        if (Arrays.equals(debounceTagUid, tag.getUid()) || (ndefMsg != null && ndefMsg.equals(NfcService.this.mLastReadNdefMessage))) {
                            NfcService.this.mHandler.removeMessages(14);
                            NfcService.this.mHandler.sendEmptyMessageDelayed(14, debounceTagMs);
                            tag.disconnect();
                            return;
                        }
                        synchronized (NfcService.this) {
                            NfcService.this.mDebounceTagUid = null;
                            NfcService.this.mDebounceTagRemovedCallback = null;
                            NfcService.this.mDebounceTagNativeHandle = -1;
                        }
                        if (debounceTagRemovedCallback != null) {
                            try {
                                debounceTagRemovedCallback.onTagRemoved();
                            } catch (RemoteException e) {
                            }
                        }
                    }
                    NfcService.this.mLastReadNdefMessage = ndefMsg;
                    tag.startPresenceChecking(presenceCheckDelay, callback);
                    dispatchTagEndpoint(tag, readerParams);
                    return;
                case 1:
                    NfcService.this.mPowerManager.userActivity(SystemClock.uptimeMillis(), 0, 0);
                    if (NfcService.this.mIsDebugBuild) {
                        Intent actIntent = new Intent(NfcService.ACTION_LLCP_UP);
                        NfcService.this.mContext.sendBroadcast(actIntent);
                    }
                    llcpActivated((DeviceHost.NfcDepEndpoint) msg.obj);
                    return;
                case 2:
                    if (NfcService.this.mIsDebugBuild) {
                        Intent deactIntent = new Intent(NfcService.ACTION_LLCP_DOWN);
                        NfcService.this.mContext.sendBroadcast(deactIntent);
                    }
                    DeviceHost.NfcDepEndpoint device = (DeviceHost.NfcDepEndpoint) msg.obj;
                    boolean needsDisconnect = false;
                    Log.d(NfcService.TAG, "LLCP Link Deactivated message. Restart polling loop.");
                    synchronized (NfcService.this) {
                        if (NfcService.this.mObjectMap.remove(Integer.valueOf(device.getHandle())) != null && device.getMode() == 0) {
                            needsDisconnect = NfcService.NFC_ON_DEFAULT;
                        }
                    }
                    if (needsDisconnect) {
                        device.disconnect();
                    }
                    NfcService.this.mP2pLinkManager.onLlcpDeactivated();
                    return;
                case 3:
                    Bundle extras = new Bundle();
                    extras.putParcelable("ndefmsg", (NdefMessage) msg.obj);
                    extras.putInt("ndefmaxlength", 0);
                    extras.putInt("ndefcardstate", 1);
                    extras.putInt("ndeftype", -1);
                    Tag tag2 = Tag.createMockTag(new byte[]{0}, new int[]{6}, new Bundle[]{extras});
                    Log.d(NfcService.TAG, "mock NDEF tag, starting corresponding activity");
                    Log.d(NfcService.TAG, tag2.toString());
                    int dispatchStatus = NfcService.this.mNfcDispatcher.dispatchTag(tag2);
                    if (dispatchStatus == 1) {
                        NfcService.this.playSound(1);
                        return;
                    } else if (dispatchStatus == 2) {
                        NfcService.this.playSound(2);
                        return;
                    } else {
                        return;
                    }
                case 4:
                    NfcService.this.mP2pLinkManager.onLlcpFirstPacketReceived();
                    return;
                case 5:
                    int route = msg.arg1;
                    int aidInfo = msg.arg2;
                    String aid = (String) msg.obj;
                    NfcService.this.mDeviceHost.routeAid(NfcService.hexStringToBytes(aid), route, aidInfo);
                    return;
                case 6:
                    String aid2 = (String) msg.obj;
                    NfcService.this.mDeviceHost.unrouteAid(NfcService.hexStringToBytes(aid2));
                    return;
                case 7:
                    boolean commit = false;
                    synchronized (NfcService.this) {
                        if (NfcService.this.mCurrentDiscoveryParameters.shouldEnableDiscovery()) {
                            commit = NfcService.NFC_ON_DEFAULT;
                        } else {
                            Log.d(NfcService.TAG, "Not committing routing because discovery is disabled.");
                        }
                    }
                    if (commit) {
                        NfcService.this.mDeviceHost.commitRouting();
                        return;
                    }
                    return;
                case 8:
                    NfcService.this.mP2pLinkManager.onManualBeamInvoke((BeamShareData) msg.obj);
                    return;
                case 9:
                    Intent fieldOnIntent = new Intent(NfcService.ACTION_RF_FIELD_ON_DETECTED);
                    sendNfcEeAccessProtectedBroadcast(fieldOnIntent);
                    return;
                case 10:
                    Intent fieldOffIntent = new Intent(NfcService.ACTION_RF_FIELD_OFF_DETECTED);
                    sendNfcEeAccessProtectedBroadcast(fieldOffIntent);
                    return;
                case 11:
                    NfcService.this.mNfcAdapter.resumePolling();
                    return;
                case 12:
                    Log.d(NfcService.TAG, "message to register LF_T3T_IDENTIFIER");
                    NfcService.this.mDeviceHost.disableDiscovery();
                    byte[] t3tIdentifier = (byte[]) msg.obj;
                    NfcService.this.mDeviceHost.registerT3tIdentifier(t3tIdentifier);
                    NfcService nfcService = NfcService.this;
                    NfcDiscoveryParameters params = nfcService.computeDiscoveryParameters(nfcService.mScreenState);
                    boolean shouldRestart = NfcService.this.mCurrentDiscoveryParameters.shouldEnableDiscovery();
                    NfcService.this.mDeviceHost.enableDiscovery(params, shouldRestart);
                    return;
                case 13:
                    Log.d(NfcService.TAG, "message to deregister LF_T3T_IDENTIFIER");
                    NfcService.this.mDeviceHost.disableDiscovery();
                    byte[] t3tIdentifier2 = (byte[]) msg.obj;
                    NfcService.this.mDeviceHost.deregisterT3tIdentifier(t3tIdentifier2);
                    NfcService nfcService2 = NfcService.this;
                    NfcDiscoveryParameters params2 = nfcService2.computeDiscoveryParameters(nfcService2.mScreenState);
                    boolean shouldRestart2 = NfcService.this.mCurrentDiscoveryParameters.shouldEnableDiscovery();
                    NfcService.this.mDeviceHost.enableDiscovery(params2, shouldRestart2);
                    return;
                case 14:
                    synchronized (NfcService.this) {
                        NfcService.this.mDebounceTagUid = null;
                        tagRemovedCallback = NfcService.this.mDebounceTagRemovedCallback;
                        NfcService.this.mDebounceTagRemovedCallback = null;
                        NfcService.this.mDebounceTagNativeHandle = -1;
                    }
                    if (tagRemovedCallback != null) {
                        try {
                            tagRemovedCallback.onTagRemoved();
                            return;
                        } catch (RemoteException e2) {
                            return;
                        }
                    }
                    return;
                case 15:
                    if (NfcService.this.mNumTagsDetected.get() > 0) {
                        MetricsLogger.count(NfcService.this.mContext, NfcService.TRON_NFC_TAG, NfcService.this.mNumTagsDetected.get());
                        NfcService.this.mNumTagsDetected.set(0);
                    }
                    if (NfcService.this.mNumHceDetected.get() > 0) {
                        MetricsLogger.count(NfcService.this.mContext, NfcService.TRON_NFC_CE, NfcService.this.mNumHceDetected.get());
                        NfcService.this.mNumHceDetected.set(0);
                    }
                    if (NfcService.this.mNumP2pDetected.get() > 0) {
                        MetricsLogger.count(NfcService.this.mContext, NfcService.TRON_NFC_P2P, NfcService.this.mNumP2pDetected.get());
                        NfcService.this.mNumP2pDetected.set(0);
                    }
                    removeMessages(15);
                    sendEmptyMessageDelayed(15, NfcService.STATS_UPDATE_INTERVAL_MS);
                    return;
                case 16:
                    NfcService.this.mScreenState = ((Integer) msg.obj).intValue();
                    Log.d(NfcService.TAG, "MSG_APPLY_SCREEN_STATE " + NfcService.this.mScreenState);
                    synchronized (NfcService.this) {
                        if (NfcService.this.mState == 4) {
                            return;
                        }
                        if (NfcService.this.mScreenState == 8) {
                            NfcService.this.applyRouting(false);
                        }
                        int screen_state_mask = NfcService.this.mNfcUnlockManager.isLockscreenPollingEnabled() ? NfcService.this.mScreenState | 16 : NfcService.this.mScreenState;
                        if (NfcService.this.mNfcUnlockManager.isLockscreenPollingEnabled()) {
                            NfcService.this.applyRouting(false);
                        }
                        NfcService.this.mDeviceHost.doSetScreenState(screen_state_mask);
                        return;
                    }
                case 17:
                    if (NfcService.this.mCardEmulationManager != null) {
                        NfcService.this.mCardEmulationManager.onOffHostAidSelected();
                    }
                    byte[][] data = (byte[][]) msg.obj;
                    sendOffHostTransactionEvent(data[0], data[1], data[2]);
                    return;
                default:
                    Log.e(NfcService.TAG, "Unknown message received");
                    return;
            }
        }

        private void sendOffHostTransactionEvent(byte[] aid, byte[] data, byte[] readerByteArray) {
            String reader;
            if (NfcService.this.isSEServiceAvailable() && !NfcService.this.mNfcEventInstalledPackages.isEmpty()) {
                try {
                    try {
                        String reader2 = new String(readerByteArray, "UTF-8");
                        String[] installedPackages = new String[NfcService.this.mNfcEventInstalledPackages.size()];
                        boolean[] nfcAccess = NfcService.this.mSEService.isNFCEventAllowed(reader2, aid, (String[]) NfcService.this.mNfcEventInstalledPackages.toArray(installedPackages));
                        if (nfcAccess == null) {
                            return;
                        }
                        new ArrayList();
                        Intent intent = new Intent("android.nfc.action.TRANSACTION_DETECTED");
                        intent.addFlags(32);
                        intent.addFlags(268435456);
                        intent.putExtra("android.nfc.extra.AID", aid);
                        try {
                            intent.putExtra("android.nfc.extra.DATA", data);
                            intent.putExtra("android.nfc.extra.SECURE_ELEMENT_NAME", reader2);
                            StringBuilder aidString = new StringBuilder(aid.length);
                            for (byte b : aid) {
                                aidString.append(String.format("%02X", Byte.valueOf(b)));
                            }
                            String url = new String("nfc://secure:0/" + reader2 + "/" + aidString.toString());
                            intent.setData(Uri.parse(url));
                            BroadcastOptions options = BroadcastOptions.makeBasic();
                            options.setBackgroundActivityStartsAllowed((boolean) NfcService.NFC_ON_DEFAULT);
                            int i = 0;
                            while (i < nfcAccess.length) {
                                if (!nfcAccess[i]) {
                                    reader = reader2;
                                } else {
                                    intent.setPackage(NfcService.this.mNfcEventInstalledPackages.get(i));
                                    reader = reader2;
                                    NfcService.this.mContext.sendBroadcast(intent, null, options.toBundle());
                                }
                                i++;
                                reader2 = reader;
                            }
                        } catch (RemoteException e) {
                            e = e;
                            Log.e(NfcService.TAG, "Error in isNFCEventAllowed() " + e);
                        } catch (UnsupportedEncodingException e2) {
                            e = e2;
                            Log.e(NfcService.TAG, "Incorrect format for Secure Element name" + e);
                        }
                    } catch (RemoteException e3) {
                        e = e3;
                    } catch (UnsupportedEncodingException e4) {
                        e = e4;
                    }
                } catch (RemoteException e5) {
                    e = e5;
                } catch (UnsupportedEncodingException e6) {
                    e = e6;
                }
            }
        }

        private ArrayList<String> getSEAccessAllowedPackages() {
            if (!NfcService.this.isSEServiceAvailable() || NfcService.this.mNfcEventInstalledPackages.isEmpty()) {
                return null;
            }
            try {
                String[] readers = NfcService.this.mSEService.getReaders();
                if (readers == null || readers.length == 0) {
                    return null;
                }
                String[] installedPackages = new String[NfcService.this.mNfcEventInstalledPackages.size()];
                boolean[] nfcAccessFinal = null;
                for (String reader : readers) {
                    try {
                        boolean[] accessList = NfcService.this.mSEService.isNFCEventAllowed(reader, (byte[]) null, (String[]) NfcService.this.mNfcEventInstalledPackages.toArray(installedPackages));
                        if (accessList != null) {
                            if (nfcAccessFinal == null) {
                                nfcAccessFinal = accessList;
                            }
                            for (int i = 0; i < accessList.length; i++) {
                                if (accessList[i]) {
                                    nfcAccessFinal[i] = NfcService.NFC_ON_DEFAULT;
                                }
                            }
                        }
                    } catch (RemoteException e) {
                        Log.e(NfcService.TAG, "Error in isNFCEventAllowed() " + e);
                    }
                }
                if (nfcAccessFinal == null) {
                    return null;
                }
                ArrayList<String> packages = new ArrayList<>();
                for (int i2 = 0; i2 < nfcAccessFinal.length; i2++) {
                    if (nfcAccessFinal[i2]) {
                        packages.add(NfcService.this.mNfcEventInstalledPackages.get(i2));
                    }
                }
                return packages;
            } catch (RemoteException e2) {
                Log.e(NfcService.TAG, "Error in getReaders() " + e2);
                return null;
            }
        }

        private void sendNfcEeAccessProtectedBroadcast(Intent intent) {
            PackageInfo info;
            intent.addFlags(32);
            NfcService.this.mNfcDispatcher.resumeAppSwitches();
            synchronized (this) {
                ArrayList<String> SEPackages = getSEAccessAllowedPackages();
                if (SEPackages != null && !SEPackages.isEmpty()) {
                    Iterator<String> it = SEPackages.iterator();
                    while (it.hasNext()) {
                        intent.setPackage(it.next());
                        NfcService.this.mContext.sendBroadcast(intent);
                    }
                }
                PackageManager pm = NfcService.this.mContext.getPackageManager();
                for (String packageName : NfcService.this.mNfcEventInstalledPackages) {
                    try {
                        info = pm.getPackageInfo(packageName, 0);
                    } catch (Exception e) {
                        Log.e(NfcService.TAG, "Exception in getPackageInfo " + e);
                    }
                    if (SEPackages == null || !SEPackages.contains(packageName)) {
                        if (info.applicationInfo != null && ((info.applicationInfo.flags & 1) != 0 || (info.applicationInfo.privateFlags & 8) != 0)) {
                            intent.setPackage(packageName);
                            NfcService.this.mContext.sendBroadcast(intent);
                        }
                    }
                }
            }
        }

        private boolean llcpActivated(DeviceHost.NfcDepEndpoint device) {
            Log.d(NfcService.TAG, "LLCP Activation message");
            if (device.getMode() == 0) {
                if (device.connect()) {
                    if (NfcService.this.mDeviceHost.doCheckLlcp()) {
                        if (NfcService.this.mDeviceHost.doActivateLlcp()) {
                            synchronized (NfcService.this) {
                                NfcService.this.mObjectMap.put(Integer.valueOf(device.getHandle()), device);
                            }
                            NfcService.this.mP2pLinkManager.onLlcpActivated(device.getLlcpVersion());
                            return NfcService.NFC_ON_DEFAULT;
                        }
                        Log.w(NfcService.TAG, "Initiator LLCP activation failed. Disconnect.");
                        device.disconnect();
                        return false;
                    }
                    device.disconnect();
                    return false;
                }
                return false;
            } else if (device.getMode() == 1) {
                if (NfcService.this.mDeviceHost.doCheckLlcp()) {
                    if (NfcService.this.mDeviceHost.doActivateLlcp()) {
                        synchronized (NfcService.this) {
                            NfcService.this.mObjectMap.put(Integer.valueOf(device.getHandle()), device);
                        }
                        NfcService.this.mP2pLinkManager.onLlcpActivated(device.getLlcpVersion());
                        return NfcService.NFC_ON_DEFAULT;
                    }
                    return false;
                }
                Log.w(NfcService.TAG, "checkLlcp failed");
                return false;
            } else {
                return false;
            }
        }

        private void dispatchTagEndpoint(DeviceHost.TagEndpoint tagEndpoint, ReaderModeParams readerParams) {
            try {
                Tag tag = new Tag(tagEndpoint.getUid(), tagEndpoint.getTechList(), tagEndpoint.getTechExtras(), tagEndpoint.getHandle(), NfcService.this.mNfcTagService);
                NfcService.this.registerTagObject(tagEndpoint);
                if (readerParams != null) {
                    try {
                        if ((readerParams.flags & 256) == 0) {
                            NfcService.this.mVibrator.vibrate(NfcService.this.mVibrationEffect);
                            NfcService.this.playSound(1);
                        }
                        if (readerParams.callback != null) {
                            readerParams.callback.onTagDiscovered(tag);
                            return;
                        }
                    } catch (RemoteException e) {
                        Log.e(NfcService.TAG, "Reader mode remote has died, falling back.", e);
                    } catch (Exception e2) {
                        Log.e(NfcService.TAG, "App exception, not dispatching.", e2);
                        return;
                    }
                }
                int dispatchResult = NfcService.this.mNfcDispatcher.dispatchTag(tag);
                if (dispatchResult == 2 && !NfcService.this.mInProvisionMode) {
                    NfcService.this.unregisterObject(tagEndpoint.getHandle());
                    if (NfcService.this.mPollDelay <= -1) {
                        Log.e(NfcService.TAG, "Keep presence checking.");
                    } else {
                        tagEndpoint.stopPresenceChecking();
                        NfcService.this.mNfcAdapter.pausePolling(NfcService.this.mPollDelay);
                    }
                    if (NfcService.this.mScreenState == 8 && NfcService.this.mContext.getResources().getBoolean(R.bool.enable_notify_dispatch_failed)) {
                        if (NfcService.mToast != null && NfcService.mToast.getView().isShown()) {
                            NfcService.mToast.cancel();
                        }
                        Toast unused = NfcService.mToast = Toast.makeText(NfcService.this.mContext, (int) R.string.tag_dispatch_failed, 0);
                        NfcService.mToast.show();
                        NfcService.this.playSound(2);
                    }
                    if (!NfcService.this.mAntennaBlockedMessageShown && NfcService.access$2508() > NfcService.mDispatchFailedMax) {
                        Intent dialogIntent = new Intent(NfcService.this.mContext, NfcBlockedNotification.class);
                        dialogIntent.setFlags(268468224);
                        NfcService.this.mContext.startActivity(dialogIntent);
                        NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_ANTENNA_BLOCKED_MESSAGE_SHOWN, NfcService.NFC_ON_DEFAULT);
                        NfcService.this.mPrefsEditor.apply();
                        NfcService.this.mBackupManager.dataChanged();
                        NfcService.this.mAntennaBlockedMessageShown = NfcService.NFC_ON_DEFAULT;
                        int unused2 = NfcService.mDispatchFailedCount = 0;
                    }
                } else if (dispatchResult == 1) {
                    int unused3 = NfcService.mDispatchFailedCount = 0;
                    NfcService.this.mVibrator.vibrate(NfcService.this.mVibrationEffect);
                    NfcService.this.playSound(1);
                }
            } catch (Exception e3) {
                Log.e(NfcService.TAG, "Tag creation exception, not dispatching.", e3);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public class ApplyRoutingTask extends AsyncTask<Integer, Void, Void> {
        ApplyRoutingTask() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Void doInBackground(Integer... params) {
            synchronized (NfcService.this) {
                if (params != null) {
                    if (params.length == 1) {
                        NfcService.this.mScreenState = params[0].intValue();
                        NfcService.this.mRoutingWakeLock.acquire();
                        NfcService.this.applyRouting(false);
                        NfcService.this.mRoutingWakeLock.release();
                        return null;
                    }
                }
                NfcService.this.applyRouting(NfcService.NFC_ON_DEFAULT);
                return null;
            }
        }
    }

    static String stateToString(int state) {
        if (state != 1) {
            if (state != 2) {
                if (state != 3) {
                    if (state == 4) {
                        return "turning off";
                    }
                    return "<error>";
                }
                return "on";
            }
            return "turning on";
        }
        return "off";
    }

    private void copyNativeCrashLogsIfAny(PrintWriter pw) {
        try {
            File file = new File(this.mContext.getFilesDir(), NATIVE_LOG_FILE_NAME);
            if (!file.exists()) {
                return;
            }
            pw.println("---BEGIN: NATIVE CRASH LOG----");
            Scanner sc = new Scanner(file);
            while (sc.hasNextLine()) {
                String s = sc.nextLine();
                pw.println(s);
            }
            pw.println("---END: NATIVE CRASH LOG----");
            sc.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception in copyNativeCrashLogsIfAny " + e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void storeNativeCrashLogs() {
        try {
            File file = new File(this.mContext.getFilesDir(), NATIVE_LOG_FILE_NAME);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file);
            this.mDeviceHost.dump(fos.getFD());
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception in storeNativeCrashLogs " + e);
        }
    }

    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump nfc from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid() + " without permission android.permission.DUMP");
            return;
        }
        synchronized (this) {
            pw.println("mState=" + stateToString(this.mState));
            pw.println("mIsZeroClickRequested=" + this.mIsNdefPushEnabled);
            pw.println("mScreenState=" + ScreenStateHelper.screenStateToString(this.mScreenState));
            pw.println(this.mCurrentDiscoveryParameters);
            if (this.mIsBeamCapable) {
                this.mP2pLinkManager.dump(fd, pw, args);
            }
            if (this.mIsHceCapable) {
                this.mCardEmulationManager.dump(fd, pw, args);
            }
            this.mNfcDispatcher.dump(fd, pw, args);
            copyNativeCrashLogsIfAny(pw);
            pw.flush();
            this.mDeviceHost.dump(fd);
        }
    }
}
