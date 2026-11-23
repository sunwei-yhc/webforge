package io.github.sunwei.webforge.ui.indicator

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** 自定义页面进度条的扩展入口。  */
abstract class BaseIndicatorView : FrameLayout {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    open fun reset() {
    }

    open fun setProgress(newProgress: Int) {
    }

    open fun show() {
    }

    open fun hide() {
    }

    abstract fun offerLayoutParams(): LayoutParams?
}
