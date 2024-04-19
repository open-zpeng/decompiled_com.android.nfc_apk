package com.android.nfc;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcBarcode;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.StatsLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.nfc.NfcDispatcher;
import com.android.nfc.RegisteredComponentCache;
import com.android.nfc.handover.HandoverDataParser;
import com.android.nfc.handover.PeripheralHandoverService;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public class NfcDispatcher {
    private static final boolean DBG = false;
    static final int DISPATCH_FAIL = 2;
    static final int DISPATCH_SUCCESS = 1;
    static final int DISPATCH_UNLOCK = 3;
    private static final String TAG = "NfcDispatcher";
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final boolean mDeviceSupportsBluetooth;
    private final HandoverDataParser mHandoverDataParser;
    private IntentFilter[] mOverrideFilters;
    private PendingIntent mOverrideIntent;
    private String[][] mOverrideTechLists;
    private final String[] mProvisioningMimes;
    private boolean mProvisioningOnly;
    private final ScreenStateHelper mScreenStateHelper;
    private final RegisteredComponentCache mTechListFilters;
    private final Handler mMessageHandler = new MessageHandler();
    private final Messenger mMessenger = new Messenger(this.mMessageHandler);
    private AtomicBoolean mBluetoothEnabledByNfc = new AtomicBoolean();
    final BroadcastReceiver mBluetoothStatusReceiver = new BroadcastReceiver() { // from class: com.android.nfc.NfcDispatcher.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                handleBluetoothStateChanged(intent);
            }
        }

        private void handleBluetoothStateChanged(Intent intent) {
            int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
            if (state == 10) {
                NfcDispatcher.this.mBluetoothEnabledByNfc.set(NfcDispatcher.DBG);
            }
        }
    };
    private final IActivityManager mIActivityManager = ActivityManager.getService();
    private final NfcUnlockManager mNfcUnlockManager = NfcUnlockManager.getInstance();

    /* JADX INFO: Access modifiers changed from: package-private */
    public NfcDispatcher(Context context, HandoverDataParser handoverDataParser, boolean provisionOnly) {
        this.mContext = context;
        this.mTechListFilters = new RegisteredComponentCache(this.mContext, "android.nfc.action.TECH_DISCOVERED", "android.nfc.action.TECH_DISCOVERED");
        this.mContentResolver = context.getContentResolver();
        this.mHandoverDataParser = handoverDataParser;
        this.mScreenStateHelper = new ScreenStateHelper(context);
        this.mDeviceSupportsBluetooth = BluetoothAdapter.getDefaultAdapter() != null ? true : DBG;
        synchronized (this) {
            this.mProvisioningOnly = provisionOnly;
        }
        String[] provisionMimes = null;
        if (provisionOnly) {
            try {
                provisionMimes = context.getResources().getStringArray(R.array.provisioning_mime_types);
            } catch (Resources.NotFoundException e) {
                provisionMimes = null;
            }
        }
        this.mProvisioningMimes = provisionMimes;
        IntentFilter filter = new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED");
        this.mContext.registerReceiver(this.mBluetoothStatusReceiver, filter);
    }

    protected void finalize() throws Throwable {
        this.mContext.unregisterReceiver(this.mBluetoothStatusReceiver);
        super.finalize();
    }

    public synchronized void setForegroundDispatch(PendingIntent intent, IntentFilter[] filters, String[][] techLists) {
        this.mOverrideIntent = intent;
        this.mOverrideFilters = filters;
        this.mOverrideTechLists = techLists;
    }

    public synchronized void disableProvisioningMode() {
        this.mProvisioningOnly = DBG;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public static class DispatchInfo {
        final Context context;
        public final Intent intent = new Intent();
        final String ndefMimeType;
        final Uri ndefUri;
        final PackageManager packageManager;
        final Intent rootIntent;

        public DispatchInfo(Context context, Tag tag, NdefMessage message) {
            this.intent.putExtra("android.nfc.extra.TAG", tag);
            this.intent.putExtra("android.nfc.extra.ID", tag.getId());
            if (message != null) {
                this.intent.putExtra("android.nfc.extra.NDEF_MESSAGES", new NdefMessage[]{message});
                this.ndefUri = message.getRecords()[0].toUri();
                this.ndefMimeType = message.getRecords()[0].toMimeType();
            } else {
                this.ndefUri = null;
                this.ndefMimeType = null;
            }
            this.rootIntent = new Intent(context, NfcRootActivity.class);
            this.rootIntent.putExtra("launchIntent", this.intent);
            this.rootIntent.setFlags(268468224);
            this.context = context;
            this.packageManager = context.getPackageManager();
        }

        public Intent setNdefIntent() {
            this.intent.setAction("android.nfc.action.NDEF_DISCOVERED");
            Uri uri = this.ndefUri;
            if (uri != null) {
                this.intent.setData(uri);
                return this.intent;
            }
            String str = this.ndefMimeType;
            if (str != null) {
                this.intent.setType(str);
                return this.intent;
            }
            return null;
        }

        public Intent setTechIntent() {
            this.intent.setData(null);
            this.intent.setType(null);
            this.intent.setAction("android.nfc.action.TECH_DISCOVERED");
            return this.intent;
        }

        public Intent setTagIntent() {
            this.intent.setData(null);
            this.intent.setType(null);
            this.intent.setAction("android.nfc.action.TAG_DISCOVERED");
            return this.intent;
        }

        public boolean hasIntentReceiver() {
            if (this.packageManager.queryIntentActivitiesAsUser(this.intent, 0, ActivityManager.getCurrentUser()).size() > 0) {
                return true;
            }
            return NfcDispatcher.DBG;
        }

        public boolean isWebIntent() {
            Uri uri = this.ndefUri;
            if (uri == null || uri.normalizeScheme().getScheme() == null || !this.ndefUri.normalizeScheme().getScheme().startsWith("http")) {
                return NfcDispatcher.DBG;
            }
            return true;
        }

        public String getUri() {
            return this.ndefUri.toString();
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public boolean tryStartActivity() {
            List<ResolveInfo> activities = this.packageManager.queryIntentActivitiesAsUser(this.intent, 0, ActivityManager.getCurrentUser());
            if (activities.size() <= 0) {
                return NfcDispatcher.DBG;
            }
            this.context.startActivityAsUser(this.rootIntent, UserHandle.CURRENT);
            StatsLog.write(138, 5);
            return true;
        }

        boolean tryStartActivity(Intent intentToStart) {
            List<ResolveInfo> activities = this.packageManager.queryIntentActivitiesAsUser(intentToStart, 0, ActivityManager.getCurrentUser());
            if (activities.size() <= 0) {
                return NfcDispatcher.DBG;
            }
            this.rootIntent.putExtra("launchIntent", intentToStart);
            this.context.startActivityAsUser(this.rootIntent, UserHandle.CURRENT);
            StatsLog.write(138, 5);
            return true;
        }
    }

    public int dispatchTag(Tag tag) {
        IntentFilter[] overrideFilters;
        PendingIntent overrideIntent;
        String[][] overrideTechLists;
        boolean provisioningOnly;
        String[] provisioningMimes;
        boolean screenUnlocked;
        NdefMessage message;
        Ndef ndef = Ndef.get(tag);
        synchronized (this) {
            overrideFilters = this.mOverrideFilters;
            overrideIntent = this.mOverrideIntent;
            overrideTechLists = this.mOverrideTechLists;
            provisioningOnly = this.mProvisioningOnly;
            provisioningMimes = this.mProvisioningMimes;
        }
        if (!provisioningOnly && this.mScreenStateHelper.checkScreenState() == 4) {
            boolean screenUnlocked2 = handleNfcUnlock(tag);
            if (!screenUnlocked2) {
                return 2;
            }
            screenUnlocked = screenUnlocked2;
        } else {
            screenUnlocked = false;
        }
        if (ndef != null) {
            NdefMessage message2 = ndef.getCachedNdefMessage();
            message = message2;
        } else {
            NfcBarcode nfcBarcode = NfcBarcode.get(tag);
            if (nfcBarcode != null && nfcBarcode.getType() == 1) {
                NdefMessage message3 = decodeNfcBarcodeUri(nfcBarcode);
                message = message3;
            } else {
                message = null;
            }
        }
        DispatchInfo dispatch = new DispatchInfo(this.mContext, tag, message);
        resumeAppSwitches();
        NdefMessage message4 = message;
        if (!tryOverrides(dispatch, tag, message, overrideIntent, overrideFilters, overrideTechLists)) {
            if (tryPeripheralHandover(message4)) {
                StatsLog.write(138, 2);
                return screenUnlocked ? 3 : 1;
            } else if (NfcWifiProtectedSetup.tryNfcWifiSetup(ndef, this.mContext)) {
                StatsLog.write(138, 4);
                return screenUnlocked ? 3 : 1;
            } else {
                if (provisioningOnly) {
                    StatsLog.write(138, 3);
                    if (message4 == null) {
                        return 2;
                    }
                    String ndefMimeType = message4.getRecords()[0].toMimeType();
                    if (provisioningMimes == null || !Arrays.asList(provisioningMimes).contains(ndefMimeType)) {
                        Log.e(TAG, "Dropping NFC intent in provisioning mode.");
                        return 2;
                    }
                }
                if (tryNdef(dispatch, message4)) {
                    return screenUnlocked ? 3 : 1;
                } else if (screenUnlocked) {
                    return 3;
                } else {
                    if (tryTech(dispatch, tag)) {
                        return 1;
                    }
                    dispatch.setTagIntent();
                    if (dispatch.tryStartActivity()) {
                        return 1;
                    }
                    StatsLog.write(138, 6);
                    return 2;
                }
            }
        }
        StatsLog.write(138, 5);
        return screenUnlocked ? 3 : 1;
    }

    private boolean handleNfcUnlock(Tag tag) {
        return this.mNfcUnlockManager.tryUnlock(tag);
    }

    private NdefMessage decodeNfcBarcodeUri(NfcBarcode nfcBarcode) {
        byte[] tagId = nfcBarcode.getTag().getId();
        if (tagId.length >= 4) {
            if (tagId[1] != 1 && tagId[1] != 2 && tagId[1] != 3 && tagId[1] != 4) {
                return null;
            }
            int end = 2;
            while (end < tagId.length - 2 && tagId[end] != -2) {
                end++;
            }
            byte[] payload = new byte[end - 1];
            System.arraycopy(tagId, 1, payload, 0, payload.length);
            NdefRecord uriRecord = new NdefRecord((short) 1, NdefRecord.RTD_URI, tagId, payload);
            NdefMessage message = new NdefMessage(uriRecord, new NdefRecord[0]);
            return message;
        }
        return null;
    }

    boolean tryOverrides(DispatchInfo dispatch, Tag tag, NdefMessage message, PendingIntent overrideIntent, IntentFilter[] overrideFilters, String[][] overrideTechLists) {
        Intent intent;
        if (overrideIntent == null) {
            return DBG;
        }
        if (message != null && (intent = dispatch.setNdefIntent()) != null) {
            if (isFilterMatch(intent, overrideFilters, overrideTechLists != null)) {
                try {
                    overrideIntent.send(this.mContext, -1, intent);
                    return true;
                } catch (PendingIntent.CanceledException e) {
                    return DBG;
                }
            }
        }
        Intent intent2 = dispatch.setTechIntent();
        if (isTechMatch(tag, overrideTechLists)) {
            try {
                overrideIntent.send(this.mContext, -1, intent2);
                return true;
            } catch (PendingIntent.CanceledException e2) {
                return DBG;
            }
        }
        Intent intent3 = dispatch.setTagIntent();
        if (!isFilterMatch(intent3, overrideFilters, overrideTechLists != null)) {
            return DBG;
        }
        try {
            overrideIntent.send(this.mContext, -1, intent3);
            return true;
        } catch (PendingIntent.CanceledException e3) {
            return DBG;
        }
    }

    boolean isFilterMatch(Intent intent, IntentFilter[] filters, boolean hasTechFilter) {
        if (filters != null) {
            for (IntentFilter filter : filters) {
                if (filter.match(this.mContentResolver, intent, DBG, TAG) >= 0) {
                    return true;
                }
            }
        } else if (!hasTechFilter) {
            return true;
        }
        return DBG;
    }

    boolean isTechMatch(Tag tag, String[][] techLists) {
        if (techLists == null) {
            return DBG;
        }
        String[] tagTechs = tag.getTechList();
        Arrays.sort(tagTechs);
        for (String[] filterTechs : techLists) {
            if (filterMatch(tagTechs, filterTechs)) {
                return true;
            }
        }
        return DBG;
    }

    boolean tryNdef(DispatchInfo dispatch, NdefMessage message) {
        Intent intent;
        ResolveInfo ri;
        if (message == null || (intent = dispatch.setNdefIntent()) == null) {
            return DBG;
        }
        List<String> aarPackages = extractAarPackages(message);
        for (String pkg : aarPackages) {
            dispatch.intent.setPackage(pkg);
            if (dispatch.tryStartActivity()) {
                return true;
            }
        }
        if (aarPackages.size() > 0) {
            String firstPackage = aarPackages.get(0);
            try {
                UserHandle currentUser = new UserHandle(ActivityManager.getCurrentUser());
                PackageManager pm = this.mContext.createPackageContextAsUser("android", 0, currentUser).getPackageManager();
                Intent appLaunchIntent = pm.getLaunchIntentForPackage(firstPackage);
                if (appLaunchIntent != null && (ri = pm.resolveActivity(appLaunchIntent, 0)) != null && ri.activityInfo != null && ri.activityInfo.exported && dispatch.tryStartActivity(appLaunchIntent)) {
                    return true;
                }
                Intent marketIntent = getAppSearchIntent(firstPackage);
                if (marketIntent != null && dispatch.tryStartActivity(marketIntent)) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Could not create user package context");
                return DBG;
            }
        }
        dispatch.intent.setPackage(null);
        if (dispatch.isWebIntent() && dispatch.hasIntentReceiver()) {
            showWebLinkConfirmation(dispatch);
            StatsLog.write(138, 1);
            return true;
        }
        try {
            UserHandle currentUser2 = new UserHandle(ActivityManager.getCurrentUser());
            ResolveInfo ri2 = this.mContext.createPackageContextAsUser("android", 0, currentUser2).getPackageManager().resolveActivity(intent, 0);
            if (ri2 != null && ri2.activityInfo != null && ri2.activityInfo.exported) {
                if (dispatch.tryStartActivity()) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e2) {
            Log.e(TAG, "Could not create user package context");
        }
        return DBG;
    }

    static List<String> extractAarPackages(NdefMessage message) {
        NdefRecord[] records;
        List<String> aarPackages = new LinkedList<>();
        for (NdefRecord record : message.getRecords()) {
            String pkg = checkForAar(record);
            if (pkg != null) {
                aarPackages.add(pkg);
            }
        }
        return aarPackages;
    }

    boolean tryTech(DispatchInfo dispatch, Tag tag) {
        dispatch.setTechIntent();
        String[] tagTechs = tag.getTechList();
        Arrays.sort(tagTechs);
        ArrayList<ResolveInfo> matches = new ArrayList<>();
        List<RegisteredComponentCache.ComponentInfo> registered = this.mTechListFilters.getComponents();
        try {
            UserHandle currentUser = new UserHandle(ActivityManager.getCurrentUser());
            PackageManager pm = this.mContext.createPackageContextAsUser("android", 0, currentUser).getPackageManager();
            for (RegisteredComponentCache.ComponentInfo info : registered) {
                if (filterMatch(tagTechs, info.techs) && isComponentEnabled(pm, info.resolveInfo) && !matches.contains(info.resolveInfo) && info.resolveInfo.activityInfo.exported) {
                    matches.add(info.resolveInfo);
                }
            }
            if (matches.size() == 1) {
                ResolveInfo info2 = matches.get(0);
                dispatch.intent.setClassName(info2.activityInfo.packageName, info2.activityInfo.name);
                if (dispatch.tryStartActivity()) {
                    return true;
                }
                dispatch.intent.setComponent(null);
            } else if (matches.size() > 1) {
                Intent intent = new Intent(this.mContext, TechListChooserActivity.class);
                intent.putExtra("android.intent.extra.INTENT", dispatch.intent);
                intent.putParcelableArrayListExtra(TechListChooserActivity.EXTRA_RESOLVE_INFOS, matches);
                if (dispatch.tryStartActivity(intent)) {
                    return true;
                }
            }
            return DBG;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not create user package context");
            return DBG;
        }
    }

    public boolean tryPeripheralHandover(NdefMessage m) {
        HandoverDataParser.BluetoothHandoverData handover;
        if (m == null || !this.mDeviceSupportsBluetooth || (handover = this.mHandoverDataParser.parseBluetooth(m)) == null || !handover.valid || UserManager.get(this.mContext).hasUserRestriction("no_config_bluetooth", UserHandle.of(ActivityManager.getCurrentUser()))) {
            return DBG;
        }
        Intent intent = new Intent(this.mContext, PeripheralHandoverService.class);
        intent.putExtra(PeripheralHandoverService.EXTRA_PERIPHERAL_DEVICE, handover.device);
        intent.putExtra(PeripheralHandoverService.EXTRA_PERIPHERAL_NAME, handover.name);
        intent.putExtra(PeripheralHandoverService.EXTRA_PERIPHERAL_TRANSPORT, handover.transport);
        if (handover.oobData != null) {
            intent.putExtra(PeripheralHandoverService.EXTRA_PERIPHERAL_OOB_DATA, (Parcelable) handover.oobData);
        }
        if (handover.uuids != null) {
            intent.putExtra(PeripheralHandoverService.EXTRA_PERIPHERAL_UUIDS, handover.uuids);
        }
        if (handover.btClass != null) {
            intent.putExtra(PeripheralHandoverService.EXTRA_PERIPHERAL_CLASS, handover.btClass);
        }
        intent.putExtra(PeripheralHandoverService.EXTRA_BT_ENABLED, this.mBluetoothEnabledByNfc.get());
        intent.putExtra(PeripheralHandoverService.EXTRA_CLIENT, this.mMessenger);
        this.mContext.startServiceAsUser(intent, UserHandle.CURRENT);
        return true;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void resumeAppSwitches() {
        try {
            this.mIActivityManager.resumeAppSwitches();
        } catch (RemoteException e) {
        }
    }

    boolean filterMatch(String[] tagTechs, String[] filterTechs) {
        if (filterTechs == null || filterTechs.length == 0) {
            return DBG;
        }
        for (String tech : filterTechs) {
            if (Arrays.binarySearch(tagTechs, tech) < 0) {
                return DBG;
            }
        }
        return true;
    }

    static String checkForAar(NdefRecord record) {
        if (record.getTnf() == 4 && Arrays.equals(record.getType(), NdefRecord.RTD_ANDROID_APP)) {
            return new String(record.getPayload(), StandardCharsets.US_ASCII);
        }
        return null;
    }

    static Intent getAppSearchIntent(String pkg) {
        Intent market = new Intent("android.intent.action.VIEW");
        market.setData(Uri.parse("market://details?id=" + pkg));
        return market;
    }

    static boolean isComponentEnabled(PackageManager pm, ResolveInfo info) {
        boolean enabled = DBG;
        ComponentName compname = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        try {
            if (pm.getActivityInfo(compname, 0) != null) {
                enabled = true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            enabled = DBG;
        }
        if (!enabled) {
            Log.d(TAG, "Component not enabled: " + compname);
        }
        return enabled;
    }

    void showWebLinkConfirmation(final DispatchInfo dispatch) {
        if (!this.mContext.getResources().getBoolean(R.bool.enable_nfc_url_open_dialog)) {
            dispatch.tryStartActivity();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mContext.getApplicationContext(), 16974546);
        builder.setTitle(R.string.title_confirm_url_open);
        LayoutInflater inflater = LayoutInflater.from(this.mContext);
        View view = inflater.inflate(R.layout.url_open_confirmation, (ViewGroup) null);
        if (view != null) {
            TextView url = (TextView) view.findViewById(R.id.url_open_confirmation_link);
            if (url != null) {
                url.setText(dispatch.getUri());
            }
            builder.setView(view);
        }
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() { // from class: com.android.nfc.-$$Lambda$NfcDispatcher$IE5hy6kTVRlYKFXvWsHjAQmY8Tw
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                NfcDispatcher.lambda$showWebLinkConfirmation$0(dialogInterface, i);
            }
        });
        builder.setPositiveButton(R.string.action_confirm_url_open, new DialogInterface.OnClickListener() { // from class: com.android.nfc.-$$Lambda$NfcDispatcher$r1sTxVZBVEU7jV6UFtSDqCtzioM
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                NfcDispatcher.DispatchInfo.this.tryStartActivity();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(2003);
        dialog.show();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$showWebLinkConfirmation$0(DialogInterface dialog, int which) {
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (this) {
            pw.println("mOverrideIntent=" + this.mOverrideIntent);
            pw.println("mOverrideFilters=" + this.mOverrideFilters);
            pw.println("mOverrideTechLists=" + this.mOverrideTechLists);
        }
    }

    /* loaded from: classes.dex */
    private class MessageHandler extends Handler {
        private MessageHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 0 || i == 1) {
                NfcDispatcher.this.mBluetoothEnabledByNfc.set(msg.arg1 == 0 ? NfcDispatcher.DBG : true);
            }
        }
    }
}
