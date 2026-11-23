//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
package io.github.sunwei.webforge.ui.indicator

import android.animation.TimeInterpolator
import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator

open class ShiftDrawable @JvmOverloads constructor(d: Drawable, duration: Int = 1000, interpolator: Interpolator? = LinearInterpolator()) : DrawableWrapper(d) {
    private val mAnimator: ValueAnimator
    private val mVisibleRect: Rect
    private var mPath: Path? = null

    init {
        this.mAnimator = ValueAnimator.ofFloat(*floatArrayOf(0.0f, 1.0f))
        this.mVisibleRect = Rect()
        this.mAnimator.setDuration(duration.toLong())
        this.mAnimator.setRepeatCount(-1)
        this.mAnimator.setInterpolator((if (interpolator == null) LinearInterpolator() else interpolator) as TimeInterpolator)
        this.mAnimator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            override fun onAnimationUpdate(animation: ValueAnimator) {
                if (this@ShiftDrawable.isVisible()) {
                    this@ShiftDrawable.invalidateSelf()
                }
            }
        })
        this.mAnimator.start()
    }

    open val animator: Animator
        get() = this.mAnimator

    public override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val result = super.setVisible(visible, restart)
        if (this.isVisible()) {
            this.mAnimator.start()
        } else {
            this.mAnimator.end()
        }

        return result
    }

    protected override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        this.updateBounds()
    }

    protected override fun onLevelChange(level: Int): Boolean {
        val result = super.onLevelChange(level)
        this.updateBounds()
        return result
    }

    public override fun draw(canvas: Canvas) {
        val d = this.wrappedDrawable
        val fraction = this.mAnimator.getAnimatedFraction()
        val width = this.mVisibleRect.width()
        val offset = (width.toFloat() * fraction).toInt()
        val stack = canvas.save()
        canvas.clipPath(this.mPath!!)
        canvas.save()
        canvas.translate((-offset).toFloat(), 0.0f)
        d.draw(canvas)
        canvas.restore()
        canvas.save()
        canvas.translate((width - offset).toFloat(), 0.0f)
        d.draw(canvas)
        canvas.restore()
        canvas.restoreToCount(stack)
    }

    private fun updateBounds() {
        val b = this.getBounds()
        val width = (b.width().toFloat() * this.getLevel().toFloat() / 10000.0f).toInt()
        val radius = b.height().toFloat() / 2.0f
        this.mVisibleRect.set(b.left, b.top, b.left + width, b.height())
        this.mPath = Path()
        this.mPath!!.addRect(b.left.toFloat(), b.top.toFloat(), (b.left + width).toFloat() - radius, b.height().toFloat(), Path.Direction.CCW)
        this.mPath!!.addCircle((b.left + width).toFloat() - radius, radius, radius, Path.Direction.CCW)
    }

    companion object {
        private const val MAX_LEVEL = 10000
        private const val DEFAULT_DURATION = 1000
    }
}
