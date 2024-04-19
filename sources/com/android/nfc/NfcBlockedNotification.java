package com.android.nfc;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
/* loaded from: classes.dex */
public class NfcBlockedNotification extends Activity {
    private static final String NFC_NOTIFICATION_CHANNEL = "nfc_notification_channel";
    public static final int NOTIFICATION_ID_NFC = -1000001;
    private NotificationChannel mNotificationChannel;

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        Intent infoIntent;
        super.onCreate(savedInstanceState);
        if (TextUtils.isEmpty(getString(R.string.antenna_blocked_alert_link))) {
            infoIntent = new Intent();
        } else {
            Intent infoIntent2 = new Intent("android.intent.action.VIEW");
            infoIntent2.setData(Uri.parse(getString(R.string.antenna_blocked_alert_link)));
            infoIntent = infoIntent2;
        }
        Notification.Builder builder = new Notification.Builder(this, NFC_NOTIFICATION_CHANNEL);
        builder.setContentTitle(getString(R.string.nfc_blocking_alert_title)).setContentText(getString(R.string.nfc_blocking_alert_message)).setSmallIcon(17301642).setPriority(3).setAutoCancel(true).setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, infoIntent, 0));
        this.mNotificationChannel = new NotificationChannel(NFC_NOTIFICATION_CHANNEL, getString(R.string.nfcUserLabel), 3);
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(this.mNotificationChannel);
        notificationManager.notify(NOTIFICATION_ID_NFC, builder.build());
    }
}
