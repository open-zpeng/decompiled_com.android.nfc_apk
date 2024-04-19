package com.android.nfc.beam;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
/* loaded from: classes.dex */
public final class MimeTypeUtil {
    private static final String TAG = "MimeTypeUtil";

    private MimeTypeUtil() {
    }

    public static String getMimeTypeForUri(Context context, Uri uri) {
        if (uri.getScheme() == null) {
            return null;
        }
        if (uri.getScheme().equals("content")) {
            ContentResolver cr = context.getContentResolver();
            return cr.getType(uri);
        } else if (uri.getScheme().equals("file")) {
            String extension = null;
            String filePath = uri.getPath().toLowerCase();
            int index = filePath.lastIndexOf(".");
            if (index > 0 && index + 1 < filePath.length()) {
                extension = filePath.substring(index + 1);
            }
            if (extension != null) {
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return null;
        } else {
            Log.d(TAG, "Could not determine mime type for Uri " + uri);
            return null;
        }
    }
}
