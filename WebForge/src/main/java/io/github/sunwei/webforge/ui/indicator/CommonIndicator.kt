package io.github.sunwei.webforge.ui.indicator

import android.content.Context
import android.util.AttributeSet

/**
 */
open class CommonIndicator : BaseIndicatorView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)


    public override fun show() {
        this.setVisibility(VISIBLE)
    }

    public override fun hide() {
        this.setVisibility(GONE)
    }


    public override fun offerLayoutParams(): LayoutParams? {
        return LayoutParams(-1, -1)
    }
}
