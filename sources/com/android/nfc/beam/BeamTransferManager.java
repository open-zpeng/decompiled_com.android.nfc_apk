package com.android.nfc.beam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import androidx.core.content.FileProvider;
import com.android.nfc.R;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
/* loaded from: classes.dex */
public class BeamTransferManager implements Handler.Callback, MediaScannerConnection.OnScanCompletedListener {
    static final String ACTION_STOP_BLUETOOTH_TRANSFER = "android.btopp.intent.action.STOP_HANDOVER_TRANSFER";
    static final String ACTION_WHITELIST_DEVICE = "android.btopp.intent.action.WHITELIST_DEVICE";
    static final int ALIVE_CHECK_MS = 20000;
    static final String BEAM_DIR = "beam";
    static final String BEAM_NOTIFICATION_CHANNEL = "beam_notification_channel";
    static final int DATA_LINK_TYPE_BLUETOOTH = 1;
    static final Boolean DBG = true;
    static final int MSG_NEXT_TRANSFER_TIMER = 0;
    static final int MSG_TRANSFER_TIMEOUT = 1;
    static final int STATE_CANCELLED = 6;
    static final int STATE_CANCELLING = 7;
    static final int STATE_FAILED = 4;
    static final int STATE_IN_PROGRESS = 1;
    static final int STATE_NEW = 0;
    static final int STATE_SUCCESS = 5;
    static final int STATE_W4_MEDIA_SCANNER = 3;
    static final int STATE_W4_NEXT_TRANSFER = 2;
    static final String TAG = "BeamTransferManager";
    static final int WAIT_FOR_NEXT_TRANSFER_MS = 4000;
    final Callback mCallback;
    boolean mCalledBack;
    final PendingIntent mCancelIntent;
    final Context mContext;
    int mCurrentCount;
    int mDataLinkType;
    final Handler mHandler;
    final boolean mIncoming;
    Long mLastUpdate;
    HashMap<String, Uri> mMediaUris;
    HashMap<String, String> mMimeTypes;
    final NotificationManager mNotificationManager;
    Uri[] mOutgoingUris;
    ArrayList<String> mPaths;
    float mProgress;
    final boolean mRemoteActivating;
    final BluetoothDevice mRemoteDevice;
    int mState;
    int mSuccessCount;
    int mTotalCount;
    final int mTransferId;
    ArrayList<String> mTransferMimeTypes;
    ArrayList<Uri> mUris;
    int mUrisScanned;
    int mBluetoothTransferId = -1;
    Long mStartTime = 0L;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public interface Callback {
        void onTransferComplete(BeamTransferManager beamTransferManager, boolean z);
    }

    public BeamTransferManager(Context context, Callback callback, BeamTransferRecord pendingTransfer, boolean incoming) {
        ArrayList<Uri> arrayList;
        this.mContext = context;
        this.mCallback = callback;
        this.mRemoteDevice = pendingTransfer.remoteDevice;
        this.mIncoming = incoming;
        this.mTransferId = pendingTransfer.id;
        this.mDataLinkType = pendingTransfer.dataLinkType;
        this.mRemoteActivating = pendingTransfer.remoteActivating;
        this.mTotalCount = pendingTransfer.uris != null ? pendingTransfer.uris.length : 0;
        this.mLastUpdate = Long.valueOf(SystemClock.elapsedRealtime());
        this.mProgress = 0.0f;
        this.mState = 0;
        if (pendingTransfer.uris == null) {
            arrayList = new ArrayList<>();
        } else {
            arrayList = new ArrayList<>(Arrays.asList(pendingTransfer.uris));
        }
        this.mUris = arrayList;
        this.mTransferMimeTypes = new ArrayList<>();
        this.mMimeTypes = new HashMap<>();
        this.mPaths = new ArrayList<>();
        this.mMediaUris = new HashMap<>();
        this.mCancelIntent = buildCancelIntent();
        this.mUrisScanned = 0;
        this.mCurrentCount = 0;
        this.mSuccessCount = 0;
        this.mOutgoingUris = pendingTransfer.uris;
        this.mHandler = new Handler(Looper.getMainLooper(), this);
        this.mHandler.sendEmptyMessageDelayed(1, 20000L);
        this.mNotificationManager = (NotificationManager) this.mContext.getSystemService("notification");
        NotificationChannel notificationChannel = new NotificationChannel(BEAM_NOTIFICATION_CHANNEL, this.mContext.getString(R.string.app_name), 4);
        this.mNotificationManager.createNotificationChannel(notificationChannel);
    }

    void whitelistOppDevice(BluetoothDevice device) {
        if (DBG.booleanValue()) {
            Log.d(TAG, "Whitelisting " + device + " for BT OPP");
        }
        Intent intent = new Intent(ACTION_WHITELIST_DEVICE);
        intent.setPackage(this.mContext.getString(R.string.bluetooth_package));
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        intent.addFlags(268435456);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    public void start() {
        if (this.mStartTime.longValue() > 0) {
            return;
        }
        this.mStartTime = Long.valueOf(System.currentTimeMillis());
        if (!this.mIncoming && this.mDataLinkType == BeamTransferRecord.DATA_LINK_TYPE_BLUETOOTH) {
            new BluetoothOppHandover(this.mContext, this.mRemoteDevice, this.mUris, this.mRemoteActivating).start();
        }
    }

    public void updateFileProgress(float progress) {
        BluetoothDevice bluetoothDevice;
        if (isRunning()) {
            this.mHandler.removeMessages(0);
            this.mProgress = progress;
            if (this.mIncoming && (bluetoothDevice = this.mRemoteDevice) != null) {
                whitelistOppDevice(bluetoothDevice);
            }
            updateStateAndNotification(1);
        }
    }

    public synchronized void setBluetoothTransferId(int id) {
        if (this.mBluetoothTransferId == -1 && id != -1) {
            this.mBluetoothTransferId = id;
            if (this.mState == 7) {
                sendBluetoothCancelIntentAndUpdateState();
            }
        }
    }

    public void finishTransfer(boolean success, Uri uri, String mimeType) {
        if (isRunning()) {
            this.mCurrentCount++;
            if (!success || uri == null) {
                Log.e(TAG, "Handover transfer failed");
            } else {
                this.mSuccessCount++;
                if (DBG.booleanValue()) {
                    Log.d(TAG, "Transfer success, uri " + uri + " mimeType " + mimeType);
                }
                this.mProgress = 0.0f;
                if (mimeType == null) {
                    mimeType = MimeTypeUtil.getMimeTypeForUri(this.mContext, uri);
                }
                if (mimeType != null) {
                    this.mUris.add(uri);
                    this.mTransferMimeTypes.add(mimeType);
                } else if (DBG.booleanValue()) {
                    Log.d(TAG, "Could not get mimeType for file.");
                }
            }
            this.mHandler.removeMessages(0);
            if (this.mCurrentCount == this.mTotalCount) {
                if (this.mIncoming) {
                    processFiles();
                    return;
                } else {
                    updateStateAndNotification(this.mSuccessCount > 0 ? 5 : 4);
                    return;
                }
            }
            this.mHandler.sendEmptyMessageDelayed(0, 4000L);
            updateStateAndNotification(2);
        }
    }

    public boolean isRunning() {
        int i = this.mState;
        return i == 0 || i == 1 || i == 2 || i == 7;
    }

    public void setObjectCount(int objectCount) {
        this.mTotalCount = objectCount;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void cancel() {
        if (isRunning()) {
            Iterator<Uri> it = this.mUris.iterator();
            while (it.hasNext()) {
                Uri uri = it.next();
                File file = new File(uri.getPath());
                if (file.exists()) {
                    file.delete();
                }
            }
            if (this.mBluetoothTransferId != -1) {
                sendBluetoothCancelIntentAndUpdateState();
            } else {
                updateStateAndNotification(7);
            }
        }
    }

    private void sendBluetoothCancelIntentAndUpdateState() {
        Intent cancelIntent = new Intent(ACTION_STOP_BLUETOOTH_TRANSFER);
        cancelIntent.setPackage(this.mContext.getString(R.string.bluetooth_package));
        cancelIntent.putExtra(BeamStatusReceiver.EXTRA_TRANSFER_ID, this.mBluetoothTransferId);
        this.mContext.sendBroadcast(cancelIntent);
        updateStateAndNotification(6);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void updateNotification() {
        String beamString;
        Notification.Builder notBuilder = new Notification.Builder(this.mContext, BEAM_NOTIFICATION_CHANNEL);
        notBuilder.setColor(this.mContext.getResources().getColor(17170460));
        notBuilder.setWhen(this.mStartTime.longValue());
        notBuilder.setVisibility(1);
        notBuilder.setOnlyAlertOnce(true);
        if (this.mIncoming) {
            beamString = this.mContext.getString(R.string.beam_progress);
        } else {
            beamString = this.mContext.getString(R.string.beam_outgoing);
        }
        int i = this.mState;
        if (i == 0 || i == 1 || i == 2 || i == 3) {
            notBuilder.setAutoCancel(false);
            notBuilder.setSmallIcon(this.mIncoming ? 17301633 : 17301640);
            notBuilder.setTicker(beamString);
            notBuilder.setContentTitle(beamString);
            notBuilder.addAction(R.drawable.ic_menu_cancel_holo_dark, this.mContext.getString(R.string.cancel), this.mCancelIntent);
            float progress = 0.0f;
            int i2 = this.mTotalCount;
            if (i2 > 0) {
                float progressUnit = 1.0f / i2;
                progress = (this.mCurrentCount * progressUnit) + (this.mProgress * progressUnit);
            }
            if (this.mTotalCount > 0 && progress > 0.0f) {
                notBuilder.setProgress(100, (int) (100.0f * progress), false);
            } else {
                notBuilder.setProgress(100, 0, true);
            }
        } else {
            if (i == 5) {
                notBuilder.setAutoCancel(true);
                notBuilder.setSmallIcon(this.mIncoming ? 17301634 : 17301641);
                notBuilder.setTicker(this.mContext.getString(R.string.beam_complete));
                notBuilder.setContentTitle(this.mContext.getString(R.string.beam_complete));
                if (this.mIncoming) {
                    notBuilder.setContentText(this.mContext.getString(R.string.beam_tap_to_view));
                    Intent viewIntent = buildViewIntent();
                    PendingIntent contentIntent = PendingIntent.getActivity(this.mContext, this.mTransferId, viewIntent, 0, null);
                    notBuilder.setContentIntent(contentIntent);
                }
            } else if (i == 4) {
                notBuilder.setAutoCancel(false);
                notBuilder.setSmallIcon(this.mIncoming ? 17301634 : 17301641);
                notBuilder.setTicker(this.mContext.getString(R.string.beam_failed));
                notBuilder.setContentTitle(this.mContext.getString(R.string.beam_failed));
            } else if (i == 6 || i == 7) {
                notBuilder.setAutoCancel(false);
                notBuilder.setSmallIcon(this.mIncoming ? 17301634 : 17301641);
                notBuilder.setTicker(this.mContext.getString(R.string.beam_canceled));
                notBuilder.setContentTitle(this.mContext.getString(R.string.beam_canceled));
            } else {
                return;
            }
        }
        this.mNotificationManager.notify(null, this.mTransferId, notBuilder.build());
    }

    void updateStateAndNotification(int newState) {
        this.mState = newState;
        this.mLastUpdate = Long.valueOf(SystemClock.elapsedRealtime());
        this.mHandler.removeMessages(1);
        if (isRunning()) {
            this.mHandler.sendEmptyMessageDelayed(1, 20000L);
        }
        updateNotification();
        int i = this.mState;
        if ((i == 5 || i == 4 || i == 6) && !this.mCalledBack) {
            this.mCalledBack = true;
            this.mCallback.onTransferComplete(this, this.mState == 5);
        }
    }

    void processFiles() {
        String extRoot = Environment.getExternalStorageDirectory().getPath();
        File beamPath = new File(extRoot + "/" + BEAM_DIR);
        if (!checkMediaStorage(beamPath) || this.mUris.size() == 0) {
            Log.e(TAG, "Media storage not valid or no uris received.");
            updateStateAndNotification(4);
            return;
        }
        if (this.mUris.size() > 1) {
            beamPath = generateMultiplePath(extRoot + "/" + BEAM_DIR + "/");
            if (!beamPath.isDirectory() && !beamPath.mkdir()) {
                Log.e(TAG, "Failed to create multiple path " + beamPath.toString());
                updateStateAndNotification(4);
                return;
            }
        }
        for (int i = 0; i < this.mUris.size(); i++) {
            Uri uri = this.mUris.get(i);
            String mimeType = this.mTransferMimeTypes.get(i);
            File srcFile = new File(uri.getPath());
            File dstFile = generateUniqueDestination(beamPath.getAbsolutePath(), uri.getLastPathSegment());
            Log.d(TAG, "Renaming from " + srcFile);
            if (!srcFile.renameTo(dstFile)) {
                if (DBG.booleanValue()) {
                    Log.d(TAG, "Failed to rename from " + srcFile + " to " + dstFile);
                }
                srcFile.delete();
                return;
            }
            this.mPaths.add(dstFile.getAbsolutePath());
            this.mMimeTypes.put(dstFile.getAbsolutePath(), mimeType);
            if (DBG.booleanValue()) {
                Log.d(TAG, "Did successful rename from " + srcFile + " to " + dstFile);
            }
        }
        String mimeType2 = this.mMimeTypes.get(this.mPaths.get(0));
        if (mimeType2.startsWith("image/") || mimeType2.startsWith("video/") || mimeType2.startsWith("audio/")) {
            String[] arrayPaths = new String[this.mPaths.size()];
            MediaScannerConnection.scanFile(this.mContext, (String[]) this.mPaths.toArray(arrayPaths), null, this);
            updateStateAndNotification(3);
            return;
        }
        updateStateAndNotification(5);
    }

    @Override // android.os.Handler.Callback
    public boolean handleMessage(Message msg) {
        if (msg.what == 0) {
            if (this.mIncoming) {
                processFiles();
            } else {
                updateStateAndNotification(this.mSuccessCount > 0 ? 5 : 4);
            }
            return true;
        } else if (msg.what == 1) {
            if (DBG.booleanValue()) {
                Log.d(TAG, "Transfer timed out for id: " + Integer.toString(this.mTransferId));
            }
            updateStateAndNotification(4);
            return false;
        } else {
            return false;
        }
    }

    @Override // android.media.MediaScannerConnection.OnScanCompletedListener
    public synchronized void onScanCompleted(String path, Uri uri) {
        if (DBG.booleanValue()) {
            Log.d(TAG, "Scan completed, path " + path + " uri " + uri);
        }
        if (uri != null) {
            this.mMediaUris.put(path, uri);
        }
        this.mUrisScanned++;
        if (this.mUrisScanned == this.mPaths.size()) {
            updateStateAndNotification(5);
        }
    }

    Intent buildViewIntent() {
        if (this.mPaths.size() == 0) {
            return null;
        }
        Intent viewIntent = new Intent("android.intent.action.VIEW");
        String filePath = this.mPaths.get(0);
        Uri mediaUri = this.mMediaUris.get(filePath);
        Uri uri = mediaUri != null ? mediaUri : FileProvider.getUriForFile(this.mContext, "com.google.android.nfc.fileprovider", new File(filePath));
        viewIntent.setDataAndTypeAndNormalize(uri, this.mMimeTypes.get(filePath));
        viewIntent.setFlags(268468227);
        return viewIntent;
    }

    PendingIntent buildCancelIntent() {
        Intent intent = new Intent(BeamStatusReceiver.ACTION_CANCEL_HANDOVER_TRANSFER);
        intent.putExtra(BeamStatusReceiver.EXTRA_ADDRESS, this.mRemoteDevice.getAddress());
        intent.putExtra(BeamStatusReceiver.EXTRA_INCOMING, this.mIncoming ? 0 : 1);
        PendingIntent pi = PendingIntent.getBroadcast(this.mContext, this.mTransferId, intent, 1073741824);
        return pi;
    }

    static boolean checkMediaStorage(File path) {
        if (Environment.getExternalStorageState().equals("mounted")) {
            if (!path.isDirectory() && !path.mkdir()) {
                Log.e(TAG, "Not dir or not mkdir " + path.getAbsolutePath());
                return false;
            }
            return true;
        }
        Log.e(TAG, "External storage not mounted, can't store file.");
        return false;
    }

    static File generateUniqueDestination(String path, String fileName) {
        String extension;
        String fileNameWithoutExtension;
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex < 0) {
            extension = "";
            fileNameWithoutExtension = fileName;
        } else {
            extension = fileName.substring(dotIndex);
            fileNameWithoutExtension = fileName.substring(0, dotIndex);
        }
        File dstFile = new File(path + File.separator + fileName);
        int count = 0;
        while (dstFile.exists()) {
            dstFile = new File(path + File.separator + fileNameWithoutExtension + "-" + Integer.toString(count) + extension);
            count++;
        }
        return dstFile;
    }

    static File generateMultiplePath(String beamRoot) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String newPath = beamRoot + "beam-" + sdf.format(new Date());
        File newFile = new File(newPath);
        int count = 0;
        while (newFile.exists()) {
            String newPath2 = beamRoot + "beam-" + sdf.format(new Date()) + "-" + Integer.toString(count);
            newFile = new File(newPath2);
            count++;
        }
        return newFile;
    }
}
