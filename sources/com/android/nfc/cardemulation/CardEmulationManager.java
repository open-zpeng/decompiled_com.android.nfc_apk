package com.android.nfc.cardemulation;

import android.content.ComponentName;
import android.content.Context;
import android.nfc.INfcCardEmulation;
import android.nfc.INfcFCardEmulation;
import android.nfc.cardemulation.AidGroup;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.NfcFServiceInfo;
import android.os.Binder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import com.android.nfc.NfcPermissions;
import com.android.nfc.NfcService;
import com.android.nfc.cardemulation.EnabledNfcFServices;
import com.android.nfc.cardemulation.PreferredServices;
import com.android.nfc.cardemulation.RegisteredNfcFServicesCache;
import com.android.nfc.cardemulation.RegisteredServicesCache;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
/* loaded from: classes.dex */
public class CardEmulationManager implements RegisteredServicesCache.Callback, RegisteredNfcFServicesCache.Callback, PreferredServices.Callback, EnabledNfcFServices.Callback {
    static final boolean DBG = false;
    static final int NFC_HCE_APDU = 1;
    static final int NFC_HCE_NFCF = 4;
    static final String TAG = "CardEmulationManager";
    final RegisteredAidCache mAidCache;
    final Context mContext;
    final EnabledNfcFServices mEnabledNfcFServices;
    final HostEmulationManager mHostEmulationManager;
    final HostNfcFEmulationManager mHostNfcFEmulationManager;
    final RegisteredNfcFServicesCache mNfcFServicesCache;
    final PowerManager mPowerManager;
    final PreferredServices mPreferredServices;
    final RegisteredServicesCache mServiceCache;
    final RegisteredT3tIdentifiersCache mT3tIdentifiersCache;
    final CardEmulationInterface mCardEmulationInterface = new CardEmulationInterface();
    final NfcFCardEmulationInterface mNfcFCardEmulationInterface = new NfcFCardEmulationInterface();

    public CardEmulationManager(Context context) {
        this.mContext = context;
        this.mAidCache = new RegisteredAidCache(context);
        this.mT3tIdentifiersCache = new RegisteredT3tIdentifiersCache(context);
        this.mHostEmulationManager = new HostEmulationManager(context, this.mAidCache);
        this.mHostNfcFEmulationManager = new HostNfcFEmulationManager(context, this.mT3tIdentifiersCache);
        this.mServiceCache = new RegisteredServicesCache(context, this);
        this.mNfcFServicesCache = new RegisteredNfcFServicesCache(context, this);
        this.mPreferredServices = new PreferredServices(context, this.mServiceCache, this.mAidCache, this);
        this.mEnabledNfcFServices = new EnabledNfcFServices(context, this.mNfcFServicesCache, this.mT3tIdentifiersCache, this);
        this.mServiceCache.initialize();
        this.mNfcFServicesCache.initialize();
        this.mPowerManager = (PowerManager) context.getSystemService("power");
    }

    public INfcCardEmulation getNfcCardEmulationInterface() {
        return this.mCardEmulationInterface;
    }

    public INfcFCardEmulation getNfcFCardEmulationInterface() {
        return this.mNfcFCardEmulationInterface;
    }

    public void onHostCardEmulationActivated(int technology) {
        PowerManager powerManager = this.mPowerManager;
        if (powerManager != null) {
            powerManager.userActivity(SystemClock.uptimeMillis(), 2, 0);
        }
        if (technology == 1) {
            this.mHostEmulationManager.onHostEmulationActivated();
            this.mPreferredServices.onHostEmulationActivated();
        } else if (technology == 4) {
            this.mHostNfcFEmulationManager.onHostEmulationActivated();
            this.mNfcFServicesCache.onHostEmulationActivated();
            this.mEnabledNfcFServices.onHostEmulationActivated();
        }
    }

    public void onHostCardEmulationData(int technology, byte[] data) {
        PowerManager powerManager = this.mPowerManager;
        if (powerManager != null) {
            powerManager.userActivity(SystemClock.uptimeMillis(), 2, 0);
        }
        if (technology == 1) {
            this.mHostEmulationManager.onHostEmulationData(data);
        } else if (technology == 4) {
            this.mHostNfcFEmulationManager.onHostEmulationData(data);
        }
    }

    public void onHostCardEmulationDeactivated(int technology) {
        if (technology == 1) {
            this.mHostEmulationManager.onHostEmulationDeactivated();
            this.mPreferredServices.onHostEmulationDeactivated();
        } else if (technology == 4) {
            this.mHostNfcFEmulationManager.onHostEmulationDeactivated();
            this.mNfcFServicesCache.onHostEmulationDeactivated();
            this.mEnabledNfcFServices.onHostEmulationDeactivated();
        }
    }

    public void onOffHostAidSelected() {
        this.mHostEmulationManager.onOffHostAidSelected();
    }

    public void onUserSwitched(int userId) {
        this.mServiceCache.invalidateCache(userId);
        this.mPreferredServices.onUserSwitched(userId);
        this.mHostNfcFEmulationManager.onUserSwitched();
        this.mT3tIdentifiersCache.onUserSwitched();
        this.mEnabledNfcFServices.onUserSwitched(userId);
        this.mNfcFServicesCache.onUserSwitched();
        this.mNfcFServicesCache.invalidateCache(userId);
    }

    public void onNfcEnabled() {
        this.mAidCache.onNfcEnabled();
        this.mT3tIdentifiersCache.onNfcEnabled();
    }

    public void onNfcDisabled() {
        this.mAidCache.onNfcDisabled();
        this.mHostNfcFEmulationManager.onNfcDisabled();
        this.mNfcFServicesCache.onNfcDisabled();
        this.mT3tIdentifiersCache.onNfcDisabled();
        this.mEnabledNfcFServices.onNfcDisabled();
    }

    public void onSecureNfcToggled() {
        this.mAidCache.onSecureNfcToggled();
        this.mT3tIdentifiersCache.onSecureNfcToggled();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mServiceCache.dump(fd, pw, args);
        this.mNfcFServicesCache.dump(fd, pw, args);
        this.mPreferredServices.dump(fd, pw, args);
        this.mEnabledNfcFServices.dump(fd, pw, args);
        this.mAidCache.dump(fd, pw, args);
        this.mT3tIdentifiersCache.dump(fd, pw, args);
        this.mHostEmulationManager.dump(fd, pw, args);
        this.mHostNfcFEmulationManager.dump(fd, pw, args);
    }

    @Override // com.android.nfc.cardemulation.RegisteredServicesCache.Callback
    public void onServicesUpdated(int userId, List<ApduServiceInfo> services) {
        verifyDefaults(userId, services);
        this.mAidCache.onServicesUpdated(userId, services);
        this.mPreferredServices.onServicesUpdated();
    }

    @Override // com.android.nfc.cardemulation.RegisteredNfcFServicesCache.Callback
    public void onNfcFServicesUpdated(int userId, List<NfcFServiceInfo> services) {
        this.mT3tIdentifiersCache.onServicesUpdated(userId, services);
        this.mEnabledNfcFServices.onServicesUpdated();
    }

    void verifyDefaults(int userId, List<ApduServiceInfo> services) {
        ComponentName defaultPaymentService = getDefaultServiceForCategory(userId, "payment", DBG);
        if (defaultPaymentService == null) {
            int numPaymentServices = 0;
            ComponentName lastFoundPaymentService = null;
            for (ApduServiceInfo service : services) {
                if (service.hasCategory("payment")) {
                    numPaymentServices++;
                    lastFoundPaymentService = service.getComponent();
                }
            }
            if (numPaymentServices <= 1 && numPaymentServices == 1) {
                setDefaultServiceForCategoryChecked(userId, lastFoundPaymentService, "payment");
            }
        }
    }

    ComponentName getDefaultServiceForCategory(int userId, String category, boolean validateInstalled) {
        if (!"payment".equals(category)) {
            Log.e(TAG, "Not allowing defaults for category " + category);
            return null;
        }
        String name = Settings.Secure.getStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", userId);
        if (name != null) {
            ComponentName service = ComponentName.unflattenFromString(name);
            if (!validateInstalled || service == null) {
                return service;
            }
            if (this.mServiceCache.hasService(userId, service)) {
                return service;
            }
            return null;
        }
        return null;
    }

    boolean setDefaultServiceForCategoryChecked(int userId, ComponentName service, String category) {
        if (!"payment".equals(category)) {
            Log.e(TAG, "Not allowing defaults for category " + category);
            return DBG;
        } else if (service == null || this.mServiceCache.hasService(userId, service)) {
            Settings.Secure.putStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", service != null ? service.flattenToString() : null, userId);
            return true;
        } else {
            Log.e(TAG, "Could not find default service to make default: " + service);
            return true;
        }
    }

    boolean isServiceRegistered(int userId, ComponentName service) {
        boolean serviceFound = this.mServiceCache.hasService(userId, service);
        if (!serviceFound) {
            this.mServiceCache.invalidateCache(userId);
        }
        return this.mServiceCache.hasService(userId, service);
    }

    boolean isNfcFServiceInstalled(int userId, ComponentName service) {
        boolean serviceFound = this.mNfcFServicesCache.hasService(userId, service);
        if (!serviceFound) {
            this.mNfcFServicesCache.invalidateCache(userId);
        }
        return this.mNfcFServicesCache.hasService(userId, service);
    }

    public boolean packageHasPreferredService(String packageName) {
        return this.mPreferredServices.packageHasPreferredService(packageName);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class CardEmulationInterface extends INfcCardEmulation.Stub {
        CardEmulationInterface() {
        }

        public boolean isDefaultServiceForCategory(int userId, ComponentName service, String category) {
            ComponentName defaultService;
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            NfcPermissions.validateUserId(userId);
            if (CardEmulationManager.this.isServiceRegistered(userId, service) && (defaultService = CardEmulationManager.this.getDefaultServiceForCategory(userId, category, true)) != null && defaultService.equals(service)) {
                return true;
            }
            return CardEmulationManager.DBG;
        }

        public boolean isDefaultServiceForAid(int userId, ComponentName service, String aid) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mAidCache.isDefaultServiceForAid(userId, service, aid);
        }

        public boolean setDefaultServiceForCategory(int userId, ComponentName service, String category) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceAdminPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.setDefaultServiceForCategoryChecked(userId, service, category);
        }

        public boolean setDefaultForNextTap(int userId, ComponentName service) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceAdminPermissions(CardEmulationManager.this.mContext);
            if (service != null && !CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mPreferredServices.setDefaultForNextTap(service);
        }

        public boolean registerAidGroupForService(int userId, ComponentName service, AidGroup aidGroup) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mServiceCache.registerAidGroupForService(userId, Binder.getCallingUid(), service, aidGroup);
        }

        public boolean setOffHostForService(int userId, ComponentName service, String offHostSE) {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mServiceCache.setOffHostSecureElement(userId, Binder.getCallingUid(), service, offHostSE);
        }

        public boolean unsetOffHostForService(int userId, ComponentName service) {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mServiceCache.unsetOffHostSecureElement(userId, Binder.getCallingUid(), service);
        }

        public AidGroup getAidGroupForService(int userId, ComponentName service, String category) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return null;
            }
            return CardEmulationManager.this.mServiceCache.getAidGroupForService(userId, Binder.getCallingUid(), service, category);
        }

        public boolean removeAidGroupForService(int userId, ComponentName service, String category) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mServiceCache.removeAidGroupForService(userId, Binder.getCallingUid(), service, category);
        }

        public List<ApduServiceInfo> getServices(int userId, String category) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceAdminPermissions(CardEmulationManager.this.mContext);
            return CardEmulationManager.this.mServiceCache.getServicesForCategory(userId, category);
        }

        public boolean setPreferredService(ComponentName service) throws RemoteException {
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isServiceRegistered(UserHandle.getCallingUserId(), service)) {
                Log.e(CardEmulationManager.TAG, "setPreferredService: unknown component.");
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mPreferredServices.registerPreferredForegroundService(service, Binder.getCallingUid());
        }

        public boolean unsetPreferredService() throws RemoteException {
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            return CardEmulationManager.this.mPreferredServices.unregisteredPreferredForegroundService(Binder.getCallingUid());
        }

        public boolean supportsAidPrefixRegistration() throws RemoteException {
            return CardEmulationManager.this.mAidCache.supportsAidPrefixRegistration();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes.dex */
    public final class NfcFCardEmulationInterface extends INfcFCardEmulation.Stub {
        NfcFCardEmulationInterface() {
        }

        public String getSystemCodeForService(int userId, ComponentName service) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isNfcFServiceInstalled(userId, service)) {
                return null;
            }
            return CardEmulationManager.this.mNfcFServicesCache.getSystemCodeForService(userId, Binder.getCallingUid(), service);
        }

        public boolean registerSystemCodeForService(int userId, ComponentName service, String systemCode) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isNfcFServiceInstalled(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mNfcFServicesCache.registerSystemCodeForService(userId, Binder.getCallingUid(), service, systemCode);
        }

        public boolean removeSystemCodeForService(int userId, ComponentName service) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isNfcFServiceInstalled(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mNfcFServicesCache.removeSystemCodeForService(userId, Binder.getCallingUid(), service);
        }

        public String getNfcid2ForService(int userId, ComponentName service) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isNfcFServiceInstalled(userId, service)) {
                return null;
            }
            return CardEmulationManager.this.mNfcFServicesCache.getNfcid2ForService(userId, Binder.getCallingUid(), service);
        }

        public boolean setNfcid2ForService(int userId, ComponentName service, String nfcid2) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (!CardEmulationManager.this.isNfcFServiceInstalled(userId, service)) {
                return CardEmulationManager.DBG;
            }
            return CardEmulationManager.this.mNfcFServicesCache.setNfcid2ForService(userId, Binder.getCallingUid(), service, nfcid2);
        }

        public boolean enableNfcFForegroundService(ComponentName service) throws RemoteException {
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            if (CardEmulationManager.this.isNfcFServiceInstalled(UserHandle.getCallingUserId(), service)) {
                return CardEmulationManager.this.mEnabledNfcFServices.registerEnabledForegroundService(service, Binder.getCallingUid());
            }
            return CardEmulationManager.DBG;
        }

        public boolean disableNfcFForegroundService() throws RemoteException {
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            return CardEmulationManager.this.mEnabledNfcFServices.unregisteredEnabledForegroundService(Binder.getCallingUid());
        }

        public List<NfcFServiceInfo> getNfcFServices(int userId) throws RemoteException {
            NfcPermissions.validateUserId(userId);
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            return CardEmulationManager.this.mNfcFServicesCache.getServices(userId);
        }

        public int getMaxNumOfRegisterableSystemCodes() throws RemoteException {
            NfcPermissions.enforceUserPermissions(CardEmulationManager.this.mContext);
            return NfcService.getInstance().getLfT3tMax();
        }
    }

    @Override // com.android.nfc.cardemulation.PreferredServices.Callback
    public void onPreferredPaymentServiceChanged(ComponentName service) {
        this.mAidCache.onPreferredPaymentServiceChanged(service);
        this.mHostEmulationManager.onPreferredPaymentServiceChanged(service);
    }

    @Override // com.android.nfc.cardemulation.PreferredServices.Callback
    public void onPreferredForegroundServiceChanged(ComponentName service) {
        this.mAidCache.onPreferredForegroundServiceChanged(service);
        this.mHostEmulationManager.onPreferredForegroundServiceChanged(service);
    }

    @Override // com.android.nfc.cardemulation.EnabledNfcFServices.Callback
    public void onEnabledForegroundNfcFServiceChanged(ComponentName service) {
        this.mT3tIdentifiersCache.onEnabledForegroundNfcFServiceChanged(service);
        this.mHostNfcFEmulationManager.onEnabledForegroundNfcFServiceChanged(service);
    }
}
