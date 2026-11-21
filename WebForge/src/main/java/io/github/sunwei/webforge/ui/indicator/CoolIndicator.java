//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.github.sunwei.webforge.ui.indicator;

import android.animation.Animator.AnimatorListener;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;

import io.github.sunwei.webforge.R.styleable;

public class CoolIndicator extends ProgressBar {
    private static final int PROGRESS_DURATION = 6000;
    private static final int CLOSING_DELAY = 200;
    private static final int CLOSING_DURATION = 600;
    private static final int FINISHED_DURATION = 300;
    private ValueAnimator mPrimaryAnimator;
    private ValueAnimator mShrinkAnimator = ValueAnimator.ofFloat(new float[]{0.0F, 1.0F});
    private ValueAnimator mAlphaAnimator = ValueAnimator.ofFloat(new float[]{1.0F, 0.25F});
    private float mClipRegion = 0.0F;
    private int mExpectedProgress = 0;
    private Rect mTempRect;
    private boolean mIsRtl;
    private static final String TAG = CoolIndicator.class.getSimpleName();
    private boolean mIsRunning = false;
    private boolean mIsRunningCompleteAnimation = false;
    private AccelerateDecelerateInterpolator mAccelerateDecelerateInterpolator = new AccelerateDecelerateInterpolator();
    private LinearInterpolator mLinearInterpolator = new LinearInterpolator();
    private static final float LINEAR_MAX_RADIX_SEGMENT = 0.92F;
    private static final float ACCELERATE_DECELERATE_MAX_RADIX_SEGMENT = 1.0F;
    private boolean mWrap;
    private int mDuration;
    private int mResID;
    private static final int RADIX = 100;
    private AnimatorSet mClosingAnimatorSet;
    private AnimatorUpdateListener mListener;

    public static CoolIndicator create(Activity activity) {
        return new CoolIndicator(activity, (AttributeSet) null, 16974455);
    }

    public CoolIndicator(@NonNull Context context) {
        super(context, (AttributeSet) null);
        this.mListener = new NamelessClass_1();
        this.init(context, (AttributeSet) null);
    }

    public CoolIndicator(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.mListener = new NamelessClass_1();
        this.init(context, attrs);
    }

    public CoolIndicator(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mListener = new NamelessClass_1();
        this.init(context, attrs);
    }

    @RequiresApi(21)
    public CoolIndicator(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);


        this.mListener = new NamelessClass_1();
        this.init(context, attrs);
    }

    class NamelessClass_1 implements AnimatorUpdateListener {
        NamelessClass_1() {
        }

        public void onAnimationUpdate(ValueAnimator animation) {
            CoolIndicator.this.setProgressImmediately((Integer) CoolIndicator.this.mPrimaryAnimator.getAnimatedValue());
        }
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        this.mTempRect = new Rect();
        TypedArray a = context.obtainStyledAttributes(attrs, styleable.CoolIndicator);
        this.mDuration = a.getInteger(styleable.CoolIndicator_shiftDuration, 1000);
        this.mResID = a.getResourceId(styleable.CoolIndicator_shiftInterpolator, 0);
        this.mWrap = a.getBoolean(styleable.CoolIndicator_wrapShiftDrawable, true);
        this.mPrimaryAnimator = ValueAnimator.ofInt(new int[]{this.getProgress(), this.getMax()});
        this.mPrimaryAnimator.setInterpolator(new LinearInterpolator());
        this.mPrimaryAnimator.setDuration(5520L);
        this.mPrimaryAnimator.addUpdateListener(this.mListener);
        this.mPrimaryAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (CoolIndicator.this.getProgress() == CoolIndicator.this.getMax()) {
                    Log.i(CoolIndicator.TAG, "progress:" + CoolIndicator.this.getProgress() + "  max:" + CoolIndicator.this.getMax());
                    CoolIndicator.this.animateClosing();
                }

            }
        });
        this.mClosingAnimatorSet = new AnimatorSet();
        this.mAlphaAnimator.setDuration(600L);
        this.mAlphaAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                CoolIndicator.this.setAlpha((Float) animation.getAnimatedValue());
            }
        });
        this.mAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                CoolIndicator.this.setAlpha(1.0F);
            }

            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                CoolIndicator.this.setAlpha(1.0F);
            }
        });
        this.mShrinkAnimator.setDuration(600L);
        this.mShrinkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        this.mShrinkAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float region = (Float) valueAnimator.getAnimatedValue();
                if (CoolIndicator.this.mClipRegion != region) {
                    CoolIndicator.this.mClipRegion = region;
                    CoolIndicator.this.invalidate();
                }

            }
        });
        this.mShrinkAnimator.addListener(new AnimatorListener() {
            public void onAnimationStart(Animator animator) {
                CoolIndicator.this.mClipRegion = 0.0F;
            }

            public void onAnimationEnd(Animator animator) {
                CoolIndicator.this.setVisibilityImmediately(8);
                CoolIndicator.this.mIsRunning = false;
                CoolIndicator.this.mIsRunningCompleteAnimation = false;
            }

            public void onAnimationCancel(Animator animator) {
                CoolIndicator.this.mClipRegion = 0.0F;
                CoolIndicator.this.setVisibilityImmediately(8);
                CoolIndicator.this.mIsRunning = false;
                CoolIndicator.this.mIsRunningCompleteAnimation = false;
            }

            public void onAnimationRepeat(Animator animator) {
            }
        });
        this.mClosingAnimatorSet.playTogether(new Animator[]{this.mShrinkAnimator, this.mAlphaAnimator});
        if (this.getProgressDrawable() != null) {
            this.setProgressDrawableImmediately(this.buildWrapDrawable(this.getProgressDrawable(), this.mWrap, this.mDuration, this.mResID));
        }

        a.recycle();
        this.setMax(100);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void setProgress(int nextProgress) {
    }

    private void setProgressInternal(int nextProgress) {
        nextProgress = Math.min(nextProgress, this.getMax());
        nextProgress = Math.max(0, nextProgress);
        this.mExpectedProgress = nextProgress;
        if (this.mExpectedProgress < this.getProgress() && this.getProgress() == this.getMax()) {
            this.setProgressImmediately(0);
        }

        if (this.mPrimaryAnimator != null) {
            if (nextProgress == this.getMax()) {
                Log.i(TAG, "finished duration:" + 300.0F * (1.0F - Float.valueOf((float) this.getProgress()) / (float) this.getMax()));
                this.mPrimaryAnimator.setDuration((long) (300.0F * (1.0F - Float.valueOf((float) this.getProgress()) / (float) this.getMax())));
                this.mPrimaryAnimator.setInterpolator(this.mAccelerateDecelerateInterpolator);
            } else {
                this.mPrimaryAnimator.setDuration((long) (6000.0D * (1.0D - (double) Float.valueOf((float) this.getProgress()) / ((double) this.getMax() * 0.92D))));
                this.mPrimaryAnimator.setInterpolator(this.mLinearInterpolator);
            }

            this.mPrimaryAnimator.cancel();
            this.mPrimaryAnimator.setIntValues(new int[]{this.getProgress(), nextProgress});
            this.mPrimaryAnimator.start();
        } else {
            this.setProgressImmediately(nextProgress);
        }

        if (this.mShrinkAnimator != null && nextProgress != this.getMax()) {
            this.mShrinkAnimator.cancel();
            this.mClipRegion = 0.0F;
        }

    }

    private void setProgressDrawableImmediately(Drawable drawable) {
        super.setProgressDrawable(drawable);
    }

    public void setProgressDrawable(Drawable d) {
        super.setProgressDrawable(this.buildWrapDrawable(d, this.mWrap, this.mDuration, this.mResID));
    }

    public void start() {
        Log.i(TAG, "start:" + this.mIsRunning);
        if (!this.mIsRunning) {
            this.mIsRunning = true;
            this.setVisibility(View.VISIBLE);
            this.setProgressImmediately(0);
            this.setProgressInternal((int) ((float) this.getMax() * 0.92F));
        }
    }

    public void complete() {
        if (!this.mIsRunningCompleteAnimation) {
            if (this.mIsRunning) {
                this.mIsRunningCompleteAnimation = true;
                this.setProgressInternal((int) ((float) this.getMax() * 1.0F));
            }

        }
    }

    public synchronized void setMax(int max) {
        super.setMax(max * 100);
    }

    public void onDraw(Canvas canvas) {
        if (this.mClipRegion == 0.0F) {
            super.onDraw(canvas);
        } else {
            canvas.getClipBounds(this.mTempRect);
            float clipWidth = (float) this.mTempRect.width() * this.mClipRegion;
            int saveCount = canvas.save();
            if (this.mIsRtl) {
                canvas.clipRect((float) this.mTempRect.left, (float) this.mTempRect.top, (float) this.mTempRect.right - clipWidth, (float) this.mTempRect.bottom);
            } else {
                canvas.clipRect((float) this.mTempRect.left + clipWidth, (float) this.mTempRect.top, (float) this.mTempRect.right, (float) this.mTempRect.bottom);
            }

            super.onDraw(canvas);
            canvas.restoreToCount(saveCount);
        }

    }

    @android.annotation.SuppressLint("WrongConstant")
    public void setVisibility(int value) {
        if (value == View.GONE) {
            if (this.mExpectedProgress != this.getMax()) {
                this.setVisibilityImmediately(value);
            }
        } else {
            this.setVisibilityImmediately(value);
        }

    }

    private void setVisibilityImmediately(int value) {
        super.setVisibility(value);
    }

    private void animateClosing() {
        this.mIsRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
        this.mClosingAnimatorSet.cancel();
        Handler handler = this.getHandler();
        if (handler != null) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    CoolIndicator.this.mClosingAnimatorSet.start();
                }
            }, 200L);
        }

    }

    private void setProgressImmediately(int progress) {
        super.setProgress(progress);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (this.mPrimaryAnimator != null) {
            this.mPrimaryAnimator.cancel();
        }

        if (this.mClosingAnimatorSet != null) {
            this.mClosingAnimatorSet.cancel();
        }

        if (this.mShrinkAnimator != null) {
            this.mShrinkAnimator.cancel();
        }

        if (this.mAlphaAnimator != null) {
            this.mAlphaAnimator.cancel();
        }

    }

    private Drawable buildWrapDrawable(Drawable original, boolean isWrap, int duration, int resID) {
        if (isWrap) {
            Interpolator interpolator = resID > 0 ? AnimationUtils.loadInterpolator(this.getContext(), resID) : null;
            ShiftDrawable wrappedDrawable = new ShiftDrawable(original, duration, interpolator);
            Log.i(TAG, "wrappedDrawable:" + (wrappedDrawable != null) + "   orgin:" + (original != null));
            return wrappedDrawable;
        } else {
            return original;
        }
    }
}
