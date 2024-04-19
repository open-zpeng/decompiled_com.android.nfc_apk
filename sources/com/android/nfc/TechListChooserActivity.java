package com.android.nfc;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import com.android.internal.app.ResolverActivity;
import java.util.ArrayList;
/* loaded from: classes.dex */
public class TechListChooserActivity extends ResolverActivity {
    public static final String EXTRA_RESOLVE_INFOS = "rlist";

    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        Parcelable targetParcelable = intent.getParcelableExtra("android.intent.extra.INTENT");
        if (!(targetParcelable instanceof Intent)) {
            Log.w("TechListChooserActivity", "Target is not an intent: " + targetParcelable);
            finish();
            return;
        }
        Intent target = (Intent) targetParcelable;
        ArrayList<ResolveInfo> rList = intent.getParcelableArrayListExtra(EXTRA_RESOLVE_INFOS);
        CharSequence title = getResources().getText(17039652);
        super.onCreate(savedInstanceState, target, title, (Intent[]) null, rList, false);
    }
}
