package io.github.sunwei.webforge.ui.indicator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.annotation.ColorInt
import io.github.sunwei.webforge.util.WebForgeUtils

/** 仅在 WebView 报告新进度时更新绘制。  */
class LoadingProgressBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : BaseIndicatorView(context, attrs) {
    private val paint = Paint()
    private val height: Int
    private var progress = 0

    init {
        height = WebForgeUtils.dp2px(context, 3f)
        paint.setColor(Color.rgb(26, 173, 25))
    }

    fun setColor(@ColorInt color: Int) {
        paint.setColor(color)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, getWidth() * (progress / 100f), getHeight().toFloat(), paint)
    }

    public override fun show() {
        if (getVisibility() != VISIBLE || progress == 100) {
            animate().cancel()
            setAlpha(1f)
            setVisibility(VISIBLE)
        }
    }

    public override fun setProgress(progress: Int) {
        if (this.progress != progress) {
            this.progress = progress
            invalidate()
        }
    }

    public override fun hide() {
        if (getVisibility() == VISIBLE) {
            animate().alpha(0f).setDuration(150).withEndAction(Runnable {
                setVisibility(GONE)
                setAlpha(1f)
            }).start()
        }
    }

    public override fun reset() {
        animate().cancel()
        setAlpha(1f)
        setProgress(0)
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }

    public override fun offerLayoutParams(): LayoutParams {
        return LayoutParams(LayoutParams.MATCH_PARENT, height)
    }
}
