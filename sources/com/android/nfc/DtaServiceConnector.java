package com.android.nfc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import java.util.List;
/* loaded from: classes.dex */
public class DtaServiceConnector {
    private static String sMessageService;
    boolean isBound;
    Context mContext;
    Messenger dtaMessenger = null;
    private ServiceConnection myConnection = new ServiceConnection() { // from class: com.android.nfc.DtaServiceConnector.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName className, IBinder service) {
            DtaServiceConnector.this.dtaMessenger = new Messenger(service);
            DtaServiceConnector.this.isBound = true;
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName className) {
            DtaServiceConnector dtaServiceConnector = DtaServiceConnector.this;
            dtaServiceConnector.dtaMessenger = null;
            dtaServiceConnector.isBound = false;
        }
    };

    public DtaServiceConnector(Context mContext) {
        this.mContext = mContext;
    }

    public void bindService() {
        if (!this.isBound) {
            Intent intent = new Intent(sMessageService);
            Context context = this.mContext;
            context.bindService(createExplicitFromImplicitIntent(context, intent), this.myConnection, 1);
        }
    }

    public void sendMessage(String ndefMessage) {
        if (this.isBound) {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putString("NDEF_MESSAGE", ndefMessage);
            msg.setData(bundle);
            try {
                this.dtaMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (NullPointerException e2) {
                e2.printStackTrace();
            }
        }
    }

    public static Intent createExplicitFromImplicitIntent(Context context, Intent implicitIntent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }
        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);
        Intent explicitIntent = new Intent(implicitIntent);
        explicitIntent.setComponent(component);
        return explicitIntent;
    }

    public static void setMessageService(String service) {
        sMessageService = service;
    }
}
