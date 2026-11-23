//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
package io.github.sunwei.webforge.ui.indicator

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import io.github.sunwei.webforge.R
import kotlin.math.max
import kotlin.math.min

open class CoolIndicator : ProgressBar {
    private var mPrimaryAnimator: ValueAnimator? = null
    private val mShrinkAnimator: ValueAnimator? = ValueAnimator.ofFloat(*floatArrayOf(0.0f, 1.0f))
    private val mAlphaAnimator: ValueAnimator? = ValueAnimator.ofFloat(*floatArrayOf(1.0f, 0.25f))
    private var mClipRegion = 0.0f
    private var mExpectedProgress = 0
    private var mTempRect: Rect? = null
    private var mIsRtl = false
    private var mIsRunning = false
    private var mIsRunningCompleteAnimation = false
    private val mAccelerateDecelerateInterpolator = AccelerateDecelerateInterpolator()
    private val mLinearInterpolator = LinearInterpolator()
    private var mWrap = false
    private var mDuration = 0
    private var mResID = 0
    private var mClosingAnimatorSet: AnimatorSet? = null
    private val mListener: ValueAnimator.AnimatorUpdateListener?

    constructor(context: Context) : super(context, null as AttributeSet?) {
        this.mListener = NamelessClass_1()
        this.init(context, null as AttributeSet?)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        this.mListener = NamelessClass_1()
        this.init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        this.mListener = NamelessClass_1()
        this.init(context, attrs)
    }

    @RequiresApi(21)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        this.mListener = NamelessClass_1()
        this.init(context, attrs)
    }

    internal inner class NamelessClass_1 : ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            this@CoolIndicator.setProgressImmediately((this@CoolIndicator.mPrimaryAnimator!!.getAnimatedValue() as kotlin.Int?)!!)
        }
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        this.mTempRect = Rect()
        val a = context.obtainStyledAttributes(attrs, R.styleable.CoolIndicator)
        this.mDuration = a.getInteger(R.styleable.CoolIndicator_shiftDuration, 1000)
        this.mResID = a.getResourceId(R.styleable.CoolIndicator_shiftInterpolator, 0)
        this.mWrap = a.getBoolean(R.styleable.CoolIndicator_wrapShiftDrawable, true)
        this.mPrimaryAnimator = ValueAnimator.ofInt(*intArrayOf(this.getProgress(), this.getMax()))
        this.mPrimaryAnimator!!.setInterpolator(LinearInterpolator())
        this.mPrimaryAnimator!!.setDuration(5520L)
        this.mPrimaryAnimator!!.addUpdateListener(this.mListener)
        this.mPrimaryAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                if (this@CoolIndicator.getProgress() == this@CoolIndicator.getMax()) {
                    Log.i(TAG, "progress:" + this@CoolIndicator.getProgress() + "  max:" + this@CoolIndicator.getMax())
                    this@CoolIndicator.animateClosing()
                }
            }
        })
        this.mClosingAnimatorSet = AnimatorSet()
        this.mAlphaAnimator!!.setDuration(600L)
        this.mAlphaAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            override fun onAnimationUpdate(animation: ValueAnimator) {
                this@CoolIndicator.setAlpha((animation.getAnimatedValue() as kotlin.Float?)!!)
            }
        })
        this.mAlphaAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                super.onAnimationCancel(animation)
                this@CoolIndicator.setAlpha(1.0f)
            }

            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                this@CoolIndicator.setAlpha(1.0f)
            }
        })
        this.mShrinkAnimator!!.setDuration(600L)
        this.mShrinkAnimator.setInterpolator(AccelerateDecelerateInterpolator())
        this.mShrinkAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
                val region = valueAnimator.getAnimatedValue() as Float
                if (this@CoolIndicator.mClipRegion != region) {
                    this@CoolIndicator.mClipRegion = region
                    this@CoolIndicator.invalidate()
                }
            }
        })
        this.mShrinkAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {
                this@CoolIndicator.mClipRegion = 0.0f
            }

            override fun onAnimationEnd(animator: Animator) {
                this@CoolIndicator.setVisibilityImmediately(8)
                this@CoolIndicator.mIsRunning = false
                this@CoolIndicator.mIsRunningCompleteAnimation = false
            }

            override fun onAnimationCancel(animator: Animator) {
                this@CoolIndicator.mClipRegion = 0.0f
                this@CoolIndicator.setVisibilityImmediately(8)
                this@CoolIndicator.mIsRunning = false
                this@CoolIndicator.mIsRunningCompleteAnimation = false
            }

            override fun onAnimationRepeat(animator: Animator) {
            }
        })
        this.mClosingAnimatorSet!!.playTogether(*arrayOf<Animator?>(this.mShrinkAnimator, this.mAlphaAnimator))
        if (this.getProgressDrawable() != null) {
            this.setProgressDrawableImmediately(this.buildWrapDrawable(this.getProgressDrawable(), this.mWrap, this.mDuration, this.mResID))
        }

        a.recycle()
        this.setMax(100)
    }

    @Deprecated("")
    override fun setProgress(nextProgress: Int) {
    }

    private fun setProgressInternal(nextProgress: Int) {
        var nextProgress = nextProgress
        nextProgress = min(nextProgress, this.getMax())
        nextProgress = max(0, nextProgress)
        this.mExpectedProgress = nextProgress
        if (this.mExpectedProgress < this.getProgress() && this.getProgress() == this.getMax()) {
            this.setProgressImmediately(0)
        }

        if (this.mPrimaryAnimator != null) {
            if (nextProgress == this.getMax()) {
                Log.i(TAG, "finished duration:" + 300.0f * (1.0f - this.getProgress().toFloat() / this.getMax().toFloat()))
                this.mPrimaryAnimator!!.setDuration((300.0f * (1.0f - this.getProgress().toFloat() / this.getMax().toFloat())).toLong())
                this.mPrimaryAnimator!!.setInterpolator(this.mAccelerateDecelerateInterpolator)
            } else {
                this.mPrimaryAnimator!!.setDuration((6000.0 * (1.0 - this.getProgress().toFloat().toDouble() / (this.getMax().toDouble() * 0.92))).toLong())
                this.mPrimaryAnimator!!.setInterpolator(this.mLinearInterpolator)
            }

            this.mPrimaryAnimator!!.cancel()
            this.mPrimaryAnimator!!.setIntValues(*intArrayOf(this.getProgress(), nextProgress))
            this.mPrimaryAnimator!!.start()
        } else {
            this.setProgressImmediately(nextProgress)
        }

        if (this.mShrinkAnimator != null && nextProgress != this.getMax()) {
            this.mShrinkAnimator.cancel()
            this.mClipRegion = 0.0f
        }
    }

    private fun setProgressDrawableImmediately(drawable: Drawable?) {
        super.setProgressDrawable(drawable)
    }

    override fun setProgressDrawable(d: Drawable) {
        super.setProgressDrawable(this.buildWrapDrawable(d, this.mWrap, this.mDuration, this.mResID))
    }

    open fun start() {
        Log.i(TAG, "start:" + this.mIsRunning)
        if (!this.mIsRunning) {
            this.mIsRunning = true
            this.setVisibility(VISIBLE)
            this.setProgressImmediately(0)
            this.setProgressInternal((this.getMax().toFloat() * 0.92f).toInt())
        }
    }

    open fun complete() {
        if (!this.mIsRunningCompleteAnimation) {
            if (this.mIsRunning) {
                this.mIsRunningCompleteAnimation = true
                this.setProgressInternal((this.getMax().toFloat() * 1.0f).toInt())
            }
        }
    }

    @Synchronized
    override fun setMax(max: Int) {
        super.setMax(max * 100)
    }

    public override fun onDraw(canvas: Canvas) {
        if (this.mClipRegion == 0.0f) {
            super.onDraw(canvas)
        } else {
            canvas.getClipBounds(this.mTempRect!!)
            val clipWidth = this.mTempRect!!.width().toFloat() * this.mClipRegion
            val saveCount = canvas.save()
            if (this.mIsRtl) {
                canvas.clipRect(this.mTempRect!!.left.toFloat(), this.mTempRect!!.top.toFloat(), this.mTempRect!!.right.toFloat() - clipWidth, this.mTempRect!!.bottom.toFloat())
            } else {
                canvas.clipRect(this.mTempRect!!.left.toFloat() + clipWidth, this.mTempRect!!.top.toFloat(), this.mTempRect!!.right.toFloat(), this.mTempRect!!.bottom.toFloat())
            }

            super.onDraw(canvas)
            canvas.restoreToCount(saveCount)
        }
    }

    @SuppressLint("WrongConstant")
    override fun setVisibility(value: Int) {
        if (value == GONE) {
            if (this.mExpectedProgress != this.getMax()) {
                this.setVisibilityImmediately(value)
            }
        } else {
            this.setVisibilityImmediately(value)
        }
    }

    private fun setVisibilityImmediately(value: Int) {
        super.setVisibility(value)
    }

    private fun animateClosing() {
        this.mIsRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
        this.mClosingAnimatorSet!!.cancel()
        val handler = this.getHandler()
        if (handler != null) {
            handler.postDelayed(object : Runnable {
                override fun run() {
                    this@CoolIndicator.mClosingAnimatorSet!!.start()
                }
            }, 200L)
        }
    }

    private fun setProgressImmediately(progress: Int) {
        super.setProgress(progress)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (this.mPrimaryAnimator != null) {
            this.mPrimaryAnimator!!.cancel()
        }

        if (this.mClosingAnimatorSet != null) {
            this.mClosingAnimatorSet!!.cancel()
        }

        if (this.mShrinkAnimator != null) {
            this.mShrinkAnimator.cancel()
        }

        if (this.mAlphaAnimator != null) {
            this.mAlphaAnimator.cancel()
        }
    }

    private fun buildWrapDrawable(original: Drawable, isWrap: Boolean, duration: Int, resID: Int): Drawable {
        if (isWrap) {
            val interpolator = if (resID > 0) AnimationUtils.loadInterpolator(this.getContext(), resID) else null
            val wrappedDrawable = ShiftDrawable(original, duration, interpolator)
            Log.i(TAG, "wrappedDrawable:" + (wrappedDrawable != null) + "   orgin:" + (original != null))
            return wrappedDrawable
        } else {
            return original
        }
    }

    companion object {
        private const val PROGRESS_DURATION = 6000
        private const val CLOSING_DELAY = 200
        private const val CLOSING_DURATION = 600
        private const val FINISHED_DURATION = 300
        private val TAG: String = CoolIndicator::class.java.getSimpleName()
        private const val LINEAR_MAX_RADIX_SEGMENT = 0.92f
        private const val ACCELERATE_DECELERATE_MAX_RADIX_SEGMENT = 1.0f
        private const val RADIX = 100
        @JvmStatic
        fun create(activity: Activity): CoolIndicator {
            return CoolIndicator(activity, null as AttributeSet?, 16974455)
        }
    }
}
