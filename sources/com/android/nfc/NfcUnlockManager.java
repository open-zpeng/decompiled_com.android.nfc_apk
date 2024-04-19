package com.android.nfc;

import android.nfc.INfcUnlockHandler;
import android.nfc.Tag;
import android.os.IBinder;
import android.util.Log;
import java.util.HashMap;
import java.util.Iterator;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public class NfcUnlockManager {
    private static final String TAG = "NfcUnlockManager";
    private int mLockscreenPollMask;
    private final HashMap<IBinder, UnlockHandlerWrapper> mUnlockHandlers;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public static class UnlockHandlerWrapper {
        final int mPollMask;
        final INfcUnlockHandler mUnlockHandler;

        private UnlockHandlerWrapper(INfcUnlockHandler unlockHandler, int pollMask) {
            this.mUnlockHandler = unlockHandler;
            this.mPollMask = pollMask;
        }
    }

    public static NfcUnlockManager getInstance() {
        return Singleton.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized int addUnlockHandler(INfcUnlockHandler unlockHandler, int pollMask) {
        if (this.mUnlockHandlers.containsKey(unlockHandler.asBinder())) {
            return this.mLockscreenPollMask;
        }
        this.mUnlockHandlers.put(unlockHandler.asBinder(), new UnlockHandlerWrapper(unlockHandler, pollMask));
        int i = this.mLockscreenPollMask | pollMask;
        this.mLockscreenPollMask = i;
        return i;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized int removeUnlockHandler(IBinder unlockHandler) {
        if (this.mUnlockHandlers.containsKey(unlockHandler)) {
            this.mUnlockHandlers.remove(unlockHandler);
            this.mLockscreenPollMask = recomputePollMask();
        }
        return this.mLockscreenPollMask;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized boolean tryUnlock(Tag tag) {
        UnlockHandlerWrapper handlerWrapper;
        Iterator<IBinder> iterator = this.mUnlockHandlers.keySet().iterator();
        while (iterator.hasNext()) {
            try {
                IBinder binder = iterator.next();
                handlerWrapper = this.mUnlockHandlers.get(binder);
            } catch (Exception e) {
                Log.e(TAG, "failed to communicate with unlock handler, removing", e);
                iterator.remove();
                this.mLockscreenPollMask = recomputePollMask();
            }
            if (handlerWrapper.mUnlockHandler.onUnlockAttempted(tag)) {
                return true;
            }
        }
        return false;
    }

    private int recomputePollMask() {
        int pollMask = 0;
        for (UnlockHandlerWrapper wrapper : this.mUnlockHandlers.values()) {
            pollMask |= wrapper.mPollMask;
        }
        return pollMask;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized int getLockscreenPollMask() {
        return this.mLockscreenPollMask;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized boolean isLockscreenPollingEnabled() {
        return this.mLockscreenPollMask != 0;
    }

    /* loaded from: classes.dex */
    private static class Singleton {
        private static final NfcUnlockManager INSTANCE = new NfcUnlockManager();

        private Singleton() {
        }
    }

    private NfcUnlockManager() {
        this.mUnlockHandlers = new HashMap<>();
        this.mLockscreenPollMask = 0;
    }
}
