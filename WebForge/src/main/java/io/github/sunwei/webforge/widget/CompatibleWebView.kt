package io.github.sunwei.webforge.widget

import android.annotation.TargetApi
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.AttributeSet
import android.webkit.WebView

/**
 * 修复 Android 5.0 & 5.1 打开 WebView 闪退问题：
 */
@Suppress("unused")
open class CompatibleWebView : WebView {
    constructor(context: Context) : super(getFixedContext(context)!!)

    constructor(context: Context, attrs: AttributeSet?) : super(getFixedContext(context)!!, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(getFixedContext(context)!!, attrs, defStyleAttr)

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(getFixedContext(context)!!, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, privateBrowsing: Boolean) : super(getFixedContext(context)!!, attrs, defStyleAttr, privateBrowsing)

    companion object {
        @JvmStatic
        fun getFixedContext(context: Context): Context? {
            if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 23) {
                // Avoid crashing on Android 5 and 6 (API level 21 to 23)
                return context.createConfigurationContext(Configuration())
            }
            return context
        }
    }
}
