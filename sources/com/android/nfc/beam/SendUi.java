package com.android.nfc.beam;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeAnimator;
import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.AsyncTask;
import android.os.Binder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.SurfaceControl;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.policy.PhoneWindow;
import com.android.nfc.R;
/* loaded from: classes.dex */
public class SendUi implements Animator.AnimatorListener, View.OnTouchListener, TimeAnimator.TimeListener, TextureView.SurfaceTextureListener, Window.Callback {
    static final int FADE_IN_DURATION_MS = 250;
    static final int FADE_IN_START_DELAY_MS = 350;
    static final int FAST_SEND_DURATION_MS = 350;
    public static final int FINISH_SCALE_UP = 0;
    public static final int FINISH_SEND_SUCCESS = 1;
    static final int PRE_DURATION_MS = 350;
    static final int SCALE_UP_DURATION_MS = 300;
    static final int SLIDE_OUT_DURATION_MS = 300;
    static final int SLOW_SEND_DURATION_MS = 8000;
    static final int STATE_COMPLETE = 9;
    static final int STATE_IDLE = 0;
    static final int STATE_SENDING = 8;
    static final int STATE_W4_NFC_TAP = 7;
    static final int STATE_W4_PRESEND = 5;
    static final int STATE_W4_SCREENSHOT = 1;
    static final int STATE_W4_SCREENSHOT_PRESEND_NFC_TAP_REQUESTED = 3;
    static final int STATE_W4_SCREENSHOT_PRESEND_REQUESTED = 2;
    static final int STATE_W4_SCREENSHOT_THEN_STOP = 4;
    static final int STATE_W4_TOUCH = 6;
    static final String TAG = "SendUi";
    static final int TEXT_HINT_ALPHA_DURATION_MS = 500;
    static final int TEXT_HINT_ALPHA_START_DELAY_MS = 300;
    final ObjectAnimator mAlphaDownAnimator;
    final ObjectAnimator mAlphaUpAnimator;
    final ImageView mBlackLayer;
    final Callback mCallback;
    final Context mContext;
    View mDecor;
    final Display mDisplay;
    final ObjectAnimator mFadeInAnimator;
    final ObjectAnimator mFastSendAnimator;
    final FireflyRenderer mFireflyRenderer;
    final TimeAnimator mFrameCounterAnimator;
    final boolean mHardwareAccelerated;
    final ObjectAnimator mHintAnimator;
    final LayoutInflater mLayoutInflater;
    final ObjectAnimator mPreAnimator;
    int mRenderedFrames;
    final ObjectAnimator mScaleUpAnimator;
    Bitmap mScreenshotBitmap;
    final View mScreenshotLayout;
    final ImageView mScreenshotView;
    final ObjectAnimator mSlowSendAnimator;
    int mState;
    final StatusBarManager mStatusBarManager;
    final AnimatorSet mSuccessAnimatorSet;
    SurfaceTexture mSurface;
    int mSurfaceHeight;
    int mSurfaceWidth;
    final TextView mTextHint;
    final TextView mTextRetry;
    final TextureView mTextureView;
    String mToastString;
    final WindowManager.LayoutParams mWindowLayoutParams;
    final WindowManager mWindowManager;
    static final float INTERMEDIATE_SCALE = 0.6f;
    static final float[] PRE_SCREENSHOT_SCALE = {1.0f, INTERMEDIATE_SCALE};
    static final float[] SEND_SCREENSHOT_SCALE = {INTERMEDIATE_SCALE, 0.2f};
    static final float[] SCALE_UP_SCREENSHOT_SCALE = {INTERMEDIATE_SCALE, 1.0f};
    static final float[] BLACK_LAYER_ALPHA_DOWN_RANGE = {0.9f, 0.0f};
    static final float[] BLACK_LAYER_ALPHA_UP_RANGE = {0.0f, 0.9f};
    static final float[] TEXT_HINT_ALPHA_RANGE = {0.0f, 1.0f};
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.nfc.beam.SendUi.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.CLOSE_SYSTEM_DIALOGS".equals(intent.getAction())) {
                SendUi.this.mCallback.onCanceled();
            }
        }
    };
    final DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    final Matrix mDisplayMatrix = new Matrix();

    /* loaded from: classes.dex */
    public interface Callback {
        void onCanceled();

        void onSendConfirmed();
    }

    public SendUi(Context context, Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        this.mWindowManager = (WindowManager) context.getSystemService("window");
        this.mStatusBarManager = (StatusBarManager) context.getSystemService("statusbar");
        this.mDisplay = this.mWindowManager.getDefaultDisplay();
        this.mLayoutInflater = (LayoutInflater) context.getSystemService("layout_inflater");
        this.mScreenshotLayout = this.mLayoutInflater.inflate(R.layout.screenshot, (ViewGroup) null);
        this.mScreenshotView = (ImageView) this.mScreenshotLayout.findViewById(R.id.screenshot);
        this.mScreenshotLayout.setFocusable(true);
        this.mTextHint = (TextView) this.mScreenshotLayout.findViewById(R.id.calltoaction);
        this.mTextRetry = (TextView) this.mScreenshotLayout.findViewById(R.id.retrytext);
        this.mBlackLayer = (ImageView) this.mScreenshotLayout.findViewById(R.id.blacklayer);
        this.mTextureView = (TextureView) this.mScreenshotLayout.findViewById(R.id.fireflies);
        this.mTextureView.setSurfaceTextureListener(this);
        this.mHardwareAccelerated = ActivityManager.isHighEndGfx();
        int hwAccelerationFlags = this.mHardwareAccelerated ? 16777216 : 0;
        this.mWindowLayoutParams = new WindowManager.LayoutParams(-1, -1, 0, 0, 2003, hwAccelerationFlags | 1024 | 256, -1);
        this.mWindowLayoutParams.privateFlags |= 16;
        WindowManager.LayoutParams layoutParams = this.mWindowLayoutParams;
        layoutParams.layoutInDisplayCutoutMode = 1;
        layoutParams.token = new Binder();
        this.mFrameCounterAnimator = new TimeAnimator();
        this.mFrameCounterAnimator.setTimeListener(this);
        PropertyValuesHolder preX = PropertyValuesHolder.ofFloat("scaleX", PRE_SCREENSHOT_SCALE);
        PropertyValuesHolder preY = PropertyValuesHolder.ofFloat("scaleY", PRE_SCREENSHOT_SCALE);
        this.mPreAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mScreenshotView, preX, preY);
        this.mPreAnimator.setInterpolator(new DecelerateInterpolator());
        this.mPreAnimator.setDuration(350L);
        this.mPreAnimator.addListener(this);
        PropertyValuesHolder postX = PropertyValuesHolder.ofFloat("scaleX", SEND_SCREENSHOT_SCALE);
        PropertyValuesHolder postY = PropertyValuesHolder.ofFloat("scaleY", SEND_SCREENSHOT_SCALE);
        PropertyValuesHolder alphaDown = PropertyValuesHolder.ofFloat("alpha", 1.0f, 0.0f);
        this.mSlowSendAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mScreenshotView, postX, postY);
        this.mSlowSendAnimator.setInterpolator(new DecelerateInterpolator());
        this.mSlowSendAnimator.setDuration(8000L);
        this.mFastSendAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mScreenshotView, postX, postY, alphaDown);
        this.mFastSendAnimator.setInterpolator(new DecelerateInterpolator());
        this.mFastSendAnimator.setDuration(350L);
        this.mFastSendAnimator.addListener(this);
        PropertyValuesHolder scaleUpX = PropertyValuesHolder.ofFloat("scaleX", SCALE_UP_SCREENSHOT_SCALE);
        PropertyValuesHolder scaleUpY = PropertyValuesHolder.ofFloat("scaleY", SCALE_UP_SCREENSHOT_SCALE);
        this.mScaleUpAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mScreenshotView, scaleUpX, scaleUpY);
        this.mScaleUpAnimator.setInterpolator(new DecelerateInterpolator());
        this.mScaleUpAnimator.setDuration(300L);
        this.mScaleUpAnimator.addListener(this);
        PropertyValuesHolder fadeIn = PropertyValuesHolder.ofFloat("alpha", 1.0f);
        this.mFadeInAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mScreenshotView, fadeIn);
        this.mFadeInAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        this.mFadeInAnimator.setDuration(250L);
        this.mFadeInAnimator.setStartDelay(350L);
        this.mFadeInAnimator.addListener(this);
        PropertyValuesHolder alphaUp = PropertyValuesHolder.ofFloat("alpha", TEXT_HINT_ALPHA_RANGE);
        this.mHintAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mTextHint, alphaUp);
        this.mHintAnimator.setInterpolator(null);
        this.mHintAnimator.setDuration(500L);
        this.mHintAnimator.setStartDelay(300L);
        PropertyValuesHolder alphaDown2 = PropertyValuesHolder.ofFloat("alpha", BLACK_LAYER_ALPHA_DOWN_RANGE);
        this.mAlphaDownAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mBlackLayer, alphaDown2);
        this.mAlphaDownAnimator.setInterpolator(new DecelerateInterpolator());
        this.mAlphaDownAnimator.setDuration(400L);
        PropertyValuesHolder alphaUp2 = PropertyValuesHolder.ofFloat("alpha", BLACK_LAYER_ALPHA_UP_RANGE);
        this.mAlphaUpAnimator = ObjectAnimator.ofPropertyValuesHolder(this.mBlackLayer, alphaUp2);
        this.mAlphaUpAnimator.setInterpolator(new DecelerateInterpolator());
        this.mAlphaUpAnimator.setDuration(200L);
        this.mSuccessAnimatorSet = new AnimatorSet();
        this.mSuccessAnimatorSet.playSequentially(this.mFastSendAnimator, this.mFadeInAnimator);
        this.mContext.setTheme(16973834);
        PhoneWindow phoneWindow = new PhoneWindow(this.mContext);
        phoneWindow.setCallback(this);
        phoneWindow.requestFeature(1);
        this.mDecor = phoneWindow.getDecorView();
        phoneWindow.setContentView(this.mScreenshotLayout, this.mWindowLayoutParams);
        if (this.mHardwareAccelerated) {
            this.mFireflyRenderer = new FireflyRenderer(context);
        } else {
            this.mFireflyRenderer = null;
        }
        this.mState = 0;
    }

    public void takeScreenshot() {
        if (this.mState >= 6) {
            return;
        }
        this.mState = 1;
        new ScreenshotTask().execute(new Void[0]);
        IntentFilter filter = new IntentFilter("android.intent.action.CLOSE_SYSTEM_DIALOGS");
        this.mContext.registerReceiver(this.mReceiver, filter);
    }

    public void showPreSend(boolean promptToNfcTap) {
        int i = this.mState;
        if (i == 0) {
            Log.e(TAG, "Unexpected showPreSend() in STATE_IDLE");
        } else if (i == 1) {
            if (promptToNfcTap) {
                this.mState = 3;
            } else {
                this.mState = 2;
            }
        } else if (i == 2 || i == 3) {
            Log.e(TAG, "Unexpected showPreSend() in STATE_W4_SCREENSHOT_PRESEND_REQUESTED");
        } else if (i != 5) {
            Log.e(TAG, "Unexpected showPreSend() in state " + Integer.toString(this.mState));
        } else {
            this.mDisplay.getRealMetrics(this.mDisplayMetrics);
            int statusBarHeight = this.mContext.getResources().getDimensionPixelSize(17105438);
            this.mBlackLayer.setVisibility(8);
            this.mBlackLayer.setAlpha(0.0f);
            this.mScreenshotLayout.setOnTouchListener(this);
            this.mScreenshotView.setImageBitmap(this.mScreenshotBitmap);
            this.mScreenshotView.setTranslationX(0.0f);
            this.mScreenshotView.setAlpha(1.0f);
            this.mScreenshotView.setPadding(0, statusBarHeight, 0, 0);
            this.mScreenshotLayout.requestFocus();
            if (promptToNfcTap) {
                this.mTextHint.setText(this.mContext.getResources().getString(R.string.ask_nfc_tap));
            } else {
                this.mTextHint.setText(this.mContext.getResources().getString(R.string.tap_to_beam));
            }
            this.mTextHint.setAlpha(0.0f);
            this.mTextHint.setVisibility(0);
            this.mHintAnimator.start();
            int orientation = this.mContext.getResources().getConfiguration().orientation;
            if (orientation == 1) {
                this.mWindowLayoutParams.screenOrientation = 7;
            } else if (orientation == 2) {
                this.mWindowLayoutParams.screenOrientation = 6;
            } else {
                this.mWindowLayoutParams.screenOrientation = 7;
            }
            this.mWindowManager.addView(this.mDecor, this.mWindowLayoutParams);
            this.mStatusBarManager.disable(65536);
            this.mToastString = null;
            if (!this.mHardwareAccelerated) {
                this.mPreAnimator.start();
            }
            this.mState = promptToNfcTap ? 7 : 6;
        }
    }

    public void showStartSend() {
        if (this.mState < 8) {
            return;
        }
        this.mTextRetry.setVisibility(8);
        float currentScale = this.mScreenshotView.getScaleX();
        PropertyValuesHolder postX = PropertyValuesHolder.ofFloat("scaleX", currentScale, 0.0f);
        PropertyValuesHolder postY = PropertyValuesHolder.ofFloat("scaleY", currentScale, 0.0f);
        this.mSlowSendAnimator.setValues(postX, postY);
        float currentAlpha = this.mBlackLayer.getAlpha();
        if (this.mBlackLayer.isShown() && currentAlpha > 0.0f) {
            PropertyValuesHolder alphaDown = PropertyValuesHolder.ofFloat("alpha", currentAlpha, 0.0f);
            this.mAlphaDownAnimator.setValues(alphaDown);
            this.mAlphaDownAnimator.start();
        }
        this.mSlowSendAnimator.start();
    }

    public void finishAndToast(int finishMode, String toast) {
        this.mToastString = toast;
        finish(finishMode);
    }

    public void finish(int finishMode) {
        int i = this.mState;
        if (i != 0) {
            if (i == 1 || i == 2 || i == 3) {
                this.mState = 4;
            } else if (i == 4) {
                Log.e(TAG, "Unexpected call to finish() in STATE_W4_SCREENSHOT_THEN_STOP");
            } else if (i == 5) {
                this.mScreenshotBitmap = null;
                this.mState = 0;
            } else {
                FireflyRenderer fireflyRenderer = this.mFireflyRenderer;
                if (fireflyRenderer != null) {
                    fireflyRenderer.stop();
                }
                this.mTextHint.setVisibility(8);
                this.mTextRetry.setVisibility(8);
                float currentScale = this.mScreenshotView.getScaleX();
                float currentAlpha = this.mScreenshotView.getAlpha();
                if (finishMode == 0) {
                    this.mBlackLayer.setVisibility(8);
                    PropertyValuesHolder scaleUpX = PropertyValuesHolder.ofFloat("scaleX", currentScale, 1.0f);
                    PropertyValuesHolder scaleUpY = PropertyValuesHolder.ofFloat("scaleY", currentScale, 1.0f);
                    PropertyValuesHolder scaleUpAlpha = PropertyValuesHolder.ofFloat("alpha", currentAlpha, 1.0f);
                    this.mScaleUpAnimator.setValues(scaleUpX, scaleUpY, scaleUpAlpha);
                    this.mScaleUpAnimator.start();
                } else if (finishMode == 1) {
                    PropertyValuesHolder postX = PropertyValuesHolder.ofFloat("scaleX", currentScale, 0.0f);
                    PropertyValuesHolder postY = PropertyValuesHolder.ofFloat("scaleY", currentScale, 0.0f);
                    PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", currentAlpha, 0.0f);
                    this.mFastSendAnimator.setValues(postX, postY, alpha);
                    PropertyValuesHolder fadeIn = PropertyValuesHolder.ofFloat("alpha", 0.0f, 1.0f);
                    this.mFadeInAnimator.setValues(fadeIn);
                    this.mSlowSendAnimator.cancel();
                    this.mSuccessAnimatorSet.start();
                }
                this.mState = 9;
            }
        }
    }

    void dismiss() {
        if (this.mState < 6) {
            return;
        }
        this.mState = 0;
        this.mSurface = null;
        this.mFrameCounterAnimator.cancel();
        this.mPreAnimator.cancel();
        this.mSlowSendAnimator.cancel();
        this.mFastSendAnimator.cancel();
        this.mSuccessAnimatorSet.cancel();
        this.mScaleUpAnimator.cancel();
        this.mAlphaUpAnimator.cancel();
        this.mAlphaDownAnimator.cancel();
        this.mWindowManager.removeView(this.mDecor);
        this.mStatusBarManager.disable(0);
        this.mScreenshotBitmap = null;
        this.mContext.unregisterReceiver(this.mReceiver);
        String str = this.mToastString;
        if (str != null) {
            Toast toast = Toast.makeText(this.mContext, str, 1);
            toast.getWindowParams().privateFlags |= 16;
            toast.show();
        }
        this.mToastString = null;
    }

    static float getDegreesForRotation(int value) {
        if (value != 1) {
            if (value != 2) {
                if (value == 3) {
                    return 270.0f;
                }
                return 0.0f;
            }
            return 180.0f;
        }
        return 90.0f;
    }

    /* loaded from: classes.dex */
    final class ScreenshotTask extends AsyncTask<Void, Void, Bitmap> {
        ScreenshotTask() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Bitmap doInBackground(Void... params) {
            return SendUi.this.createScreenshot();
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Bitmap result) {
            if (SendUi.this.mState == 1) {
                SendUi sendUi = SendUi.this;
                sendUi.mScreenshotBitmap = result;
                sendUi.mState = 5;
            } else if (SendUi.this.mState == 4) {
                SendUi.this.mState = 0;
            } else if (SendUi.this.mState == 2 || SendUi.this.mState == 3) {
                SendUi sendUi2 = SendUi.this;
                sendUi2.mScreenshotBitmap = result;
                boolean requestTap = sendUi2.mState == 3;
                SendUi sendUi3 = SendUi.this;
                sendUi3.mState = 5;
                sendUi3.showPreSend(requestTap);
            } else {
                Log.e(SendUi.TAG, "Invalid state on screenshot completion: " + Integer.toString(SendUi.this.mState));
            }
        }
    }

    Bitmap createScreenshot() {
        boolean hasNavBar = this.mContext.getResources().getBoolean(17891517);
        int statusBarHeight = this.mContext.getResources().getDimensionPixelSize(17105438);
        int navBarHeight = hasNavBar ? this.mContext.getResources().getDimensionPixelSize(17105292) : 0;
        int navBarHeightLandscape = hasNavBar ? this.mContext.getResources().getDimensionPixelSize(17105294) : 0;
        int navBarWidth = hasNavBar ? this.mContext.getResources().getDimensionPixelSize(17105297) : 0;
        this.mDisplay.getRealMetrics(this.mDisplayMetrics);
        float smallestWidth = Math.min(this.mDisplayMetrics.widthPixels, this.mDisplayMetrics.heightPixels);
        float smallestWidthDp = smallestWidth / (this.mDisplayMetrics.densityDpi / 160.0f);
        int rot = this.mDisplay.getRotation();
        Rect crop = new Rect(0, statusBarHeight, this.mDisplayMetrics.widthPixels, this.mDisplayMetrics.heightPixels);
        if (this.mDisplayMetrics.widthPixels < this.mDisplayMetrics.heightPixels) {
            crop.bottom -= navBarHeight;
        } else if (smallestWidthDp > 599.0f) {
            crop.bottom -= navBarHeightLandscape;
        } else {
            crop.right -= navBarWidth;
        }
        int width = crop.width();
        int height = crop.height();
        Bitmap bitmap = SurfaceControl.screenshot(crop, width, height, rot);
        if (bitmap == null) {
            return null;
        }
        Bitmap swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        return swBitmap;
    }

    @Override // android.animation.Animator.AnimatorListener
    public void onAnimationStart(Animator animation) {
    }

    @Override // android.animation.Animator.AnimatorListener
    public void onAnimationEnd(Animator animation) {
        if (animation == this.mScaleUpAnimator || animation == this.mSuccessAnimatorSet || animation == this.mFadeInAnimator) {
            dismiss();
        } else if (animation == this.mFastSendAnimator) {
            this.mScreenshotView.setScaleX(1.0f);
            this.mScreenshotView.setScaleY(1.0f);
        } else if (animation == this.mPreAnimator && this.mHardwareAccelerated) {
            int i = this.mState;
            if (i == 6 || i == 7) {
                this.mFireflyRenderer.start(this.mSurface, this.mSurfaceWidth, this.mSurfaceHeight);
            }
        }
    }

    @Override // android.animation.Animator.AnimatorListener
    public void onAnimationCancel(Animator animation) {
    }

    @Override // android.animation.Animator.AnimatorListener
    public void onAnimationRepeat(Animator animation) {
    }

    @Override // android.animation.TimeAnimator.TimeListener
    public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
        int i = this.mRenderedFrames + 1;
        this.mRenderedFrames = i;
        if (i < 4) {
            this.mScreenshotLayout.invalidate();
            return;
        }
        this.mFrameCounterAnimator.cancel();
        this.mPreAnimator.start();
    }

    @Override // android.view.View.OnTouchListener
    public boolean onTouch(View v, MotionEvent event) {
        if (this.mState != 6) {
            return false;
        }
        this.mState = 8;
        this.mScreenshotView.setOnTouchListener(null);
        this.mFrameCounterAnimator.cancel();
        this.mPreAnimator.cancel();
        this.mCallback.onSendConfirmed();
        return true;
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (this.mHardwareAccelerated && this.mState < 9) {
            this.mRenderedFrames = 0;
            this.mFrameCounterAnimator.start();
            this.mSurface = surface;
            this.mSurfaceWidth = width;
            this.mSurfaceHeight = height;
        }
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        this.mSurface = null;
        return true;
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void showSendHint() {
        if (this.mAlphaDownAnimator.isRunning()) {
            this.mAlphaDownAnimator.cancel();
        }
        if (this.mSlowSendAnimator.isRunning()) {
            this.mSlowSendAnimator.cancel();
        }
        this.mBlackLayer.setScaleX(this.mScreenshotView.getScaleX());
        this.mBlackLayer.setScaleY(this.mScreenshotView.getScaleY());
        this.mBlackLayer.setVisibility(0);
        this.mTextHint.setVisibility(8);
        this.mTextRetry.setText(this.mContext.getResources().getString(R.string.beam_try_again));
        this.mTextRetry.setVisibility(0);
        PropertyValuesHolder alphaUp = PropertyValuesHolder.ofFloat("alpha", this.mBlackLayer.getAlpha(), 0.9f);
        this.mAlphaUpAnimator.setValues(alphaUp);
        this.mAlphaUpAnimator.start();
    }

    @Override // android.view.Window.Callback
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == 4) {
            this.mCallback.onCanceled();
            return true;
        } else if (keyCode == 25 || keyCode == 24) {
            return onTouch(this.mScreenshotView, null);
        } else {
            return false;
        }
    }

    @Override // android.view.Window.Callback
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return false;
    }

    @Override // android.view.Window.Callback
    public boolean dispatchTouchEvent(MotionEvent event) {
        return this.mScreenshotLayout.dispatchTouchEvent(event);
    }

    @Override // android.view.Window.Callback
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return false;
    }

    @Override // android.view.Window.Callback
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return false;
    }

    @Override // android.view.Window.Callback
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return false;
    }

    @Override // android.view.Window.Callback
    public View onCreatePanelView(int featureId) {
        return null;
    }

    @Override // android.view.Window.Callback
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return false;
    }

    @Override // android.view.Window.Callback
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return false;
    }

    @Override // android.view.Window.Callback
    public boolean onMenuOpened(int featureId, Menu menu) {
        return false;
    }

    @Override // android.view.Window.Callback
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return false;
    }

    @Override // android.view.Window.Callback
    public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
    }

    @Override // android.view.Window.Callback
    public void onContentChanged() {
    }

    @Override // android.view.Window.Callback
    public void onWindowFocusChanged(boolean hasFocus) {
    }

    @Override // android.view.Window.Callback
    public void onAttachedToWindow() {
    }

    @Override // android.view.Window.Callback
    public void onDetachedFromWindow() {
    }

    @Override // android.view.Window.Callback
    public void onPanelClosed(int featureId, Menu menu) {
    }

    @Override // android.view.Window.Callback
    public boolean onSearchRequested(SearchEvent searchEvent) {
        return onSearchRequested();
    }

    @Override // android.view.Window.Callback
    public boolean onSearchRequested() {
        return false;
    }

    @Override // android.view.Window.Callback
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return null;
    }

    @Override // android.view.Window.Callback
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return null;
    }

    @Override // android.view.Window.Callback
    public void onActionModeStarted(ActionMode mode) {
    }

    @Override // android.view.Window.Callback
    public void onActionModeFinished(ActionMode mode) {
    }

    public boolean isSendUiInIdleState() {
        return this.mState == 0;
    }
}
