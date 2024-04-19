package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.core.os.EnvironmentCompat;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.nfc.R;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
/* loaded from: classes.dex */
public class AppChooserActivity extends AlertActivity implements AdapterView.OnItemClickListener {
    public static final String EXTRA_APDU_SERVICES = "services";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_FAILED_COMPONENT = "failed_component";
    static final String TAG = "AppChooserActivity";
    private CardEmulation mCardEmuManager;
    private String mCategory;
    private int mIconSize;
    private ListAdapter mListAdapter;
    private ListView mListView;
    final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.nfc.cardemulation.AppChooserActivity.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            AppChooserActivity.this.finish();
        }
    };

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    /* JADX WARN: Multi-variable type inference failed */
    protected void onCreate(Bundle savedInstanceState, String category, ArrayList<ApduServiceInfo> options, ComponentName failedComponent) {
        super.onCreate(savedInstanceState);
        setTheme(16974546);
        IntentFilter filter = new IntentFilter("android.intent.action.SCREEN_OFF");
        registerReceiver(this.mReceiver, filter);
        if ((options == null || options.size() == 0) && failedComponent == null) {
            Log.e(TAG, "No components passed in.");
            finish();
            return;
        }
        this.mCategory = category;
        boolean isPayment = "payment".equals(this.mCategory);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        this.mCardEmuManager = CardEmulation.getInstance(adapter);
        AlertController.AlertParams ap = this.mAlertParams;
        ActivityManager am = (ActivityManager) getSystemService("activity");
        this.mIconSize = am.getLauncherLargeIconSize();
        PackageManager pm = getPackageManager();
        CharSequence applicationLabel = EnvironmentCompat.MEDIA_UNKNOWN;
        if (failedComponent != null) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(failedComponent.getPackageName(), 0);
                applicationLabel = info.loadLabel(pm);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        if (options.size() == 0 && failedComponent != null) {
            String formatString = getString(R.string.transaction_failure);
            ap.mTitle = "";
            ap.mMessage = String.format(formatString, applicationLabel);
            ap.mPositiveButtonText = getString(17039370);
            setupAlert();
        } else {
            this.mListAdapter = new ListAdapter(this, options);
            if (failedComponent != null) {
                String formatString2 = getString(R.string.could_not_use_app);
                ap.mTitle = String.format(formatString2, applicationLabel);
                ap.mNegativeButtonText = getString(17039360);
            } else if ("payment".equals(category)) {
                ap.mTitle = getString(R.string.pay_with);
            } else {
                ap.mTitle = getString(R.string.complete_with);
            }
            ap.mView = getLayoutInflater().inflate(R.layout.cardemu_resolver, (ViewGroup) null);
            this.mListView = (ListView) ap.mView.findViewById(R.id.resolver_list);
            if (!isPayment) {
                this.mListView.setPadding(0, 0, 0, 0);
            } else {
                this.mListView.setDivider(getResources().getDrawable(17170445));
                int height = (int) (getResources().getDisplayMetrics().density * 16.0f);
                this.mListView.setDividerHeight(height);
            }
            this.mListView.setAdapter((android.widget.ListAdapter) this.mListAdapter);
            this.mListView.setOnItemClickListener(this);
            setupAlert();
        }
        Window window = getWindow();
        window.addFlags(4194304);
    }

    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        ArrayList<ApduServiceInfo> services = intent.getParcelableArrayListExtra(EXTRA_APDU_SERVICES);
        String category = intent.getStringExtra("category");
        ComponentName failedComponent = (ComponentName) intent.getParcelableExtra(EXTRA_FAILED_COMPONENT);
        onCreate(savedInstanceState, category, services, failedComponent);
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // android.widget.AdapterView.OnItemClickListener
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        DisplayAppInfo info = (DisplayAppInfo) this.mListAdapter.getItem(position);
        this.mCardEmuManager.setDefaultForNextTap(info.serviceInfo.getComponent());
        Intent dialogIntent = new Intent((Context) this, (Class<?>) TapAgainDialog.class);
        dialogIntent.putExtra("category", this.mCategory);
        dialogIntent.putExtra(TapAgainDialog.EXTRA_APDU_SERVICE, (Parcelable) info.serviceInfo);
        startActivity(dialogIntent);
        finish();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class DisplayAppInfo {
        Drawable displayBanner;
        Drawable displayIcon;
        CharSequence displayLabel;
        ApduServiceInfo serviceInfo;

        public DisplayAppInfo(ApduServiceInfo serviceInfo, CharSequence label, Drawable icon, Drawable banner) {
            this.serviceInfo = serviceInfo;
            this.displayIcon = icon;
            this.displayLabel = label;
            this.displayBanner = banner;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class ListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final boolean mIsPayment;
        private List<DisplayAppInfo> mList;

        public ListAdapter(Context context, ArrayList<ApduServiceInfo> services) {
            this.mInflater = (LayoutInflater) context.getSystemService("layout_inflater");
            PackageManager pm = AppChooserActivity.this.getPackageManager();
            this.mList = new ArrayList();
            this.mIsPayment = "payment".equals(AppChooserActivity.this.mCategory);
            Iterator<ApduServiceInfo> it = services.iterator();
            while (it.hasNext()) {
                ApduServiceInfo service = it.next();
                CharSequence label = service.getDescription();
                CharSequence label2 = label == null ? service.loadLabel(pm) : label;
                Drawable icon = service.loadIcon(pm);
                Drawable banner = null;
                if (this.mIsPayment && (banner = service.loadBanner(pm)) == null) {
                    Log.e(AppChooserActivity.TAG, "Not showing " + ((Object) label2) + " because no banner specified.");
                } else {
                    DisplayAppInfo info = new DisplayAppInfo(service, label2, icon, banner);
                    this.mList.add(info);
                }
            }
        }

        @Override // android.widget.Adapter
        public int getCount() {
            return this.mList.size();
        }

        @Override // android.widget.Adapter
        public Object getItem(int position) {
            return this.mList.get(position);
        }

        @Override // android.widget.Adapter
        public long getItemId(int position) {
            return position;
        }

        @Override // android.widget.Adapter
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                if (this.mIsPayment) {
                    view = this.mInflater.inflate(R.layout.cardemu_payment_item, parent, false);
                } else {
                    view = this.mInflater.inflate(R.layout.cardemu_item, parent, false);
                }
                view.setTag(new ViewHolder(view));
            } else {
                view = convertView;
            }
            ViewHolder holder = (ViewHolder) view.getTag();
            DisplayAppInfo appInfo = this.mList.get(position);
            if (this.mIsPayment) {
                holder.banner.setImageDrawable(appInfo.displayBanner);
            } else {
                ViewGroup.LayoutParams lp = holder.icon.getLayoutParams();
                int i = AppChooserActivity.this.mIconSize;
                lp.height = i;
                lp.width = i;
                holder.icon.setImageDrawable(appInfo.displayIcon);
                holder.text.setText(appInfo.displayLabel);
            }
            return view;
        }
    }

    /* loaded from: classes.dex */
    static class ViewHolder {
        public ImageView banner;
        public ImageView icon;
        public TextView text;

        public ViewHolder(View view) {
            this.text = (TextView) view.findViewById(R.id.applabel);
            this.icon = (ImageView) view.findViewById(R.id.appicon);
            this.banner = (ImageView) view.findViewById(R.id.banner);
        }
    }
}
