package com.android.nfc;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
/* loaded from: classes.dex */
public class NfcRootActivity extends Activity {
    static final String EXTRA_LAUNCH_INTENT = "launchIntent";

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        Intent launchIntent;
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_LAUNCH_INTENT) && (launchIntent = (Intent) intent.getParcelableExtra(EXTRA_LAUNCH_INTENT)) != null) {
            try {
                startActivityAsUser(launchIntent, UserHandle.CURRENT);
            } catch (ActivityNotFoundException e) {
            }
        }
        finish();
    }
}
