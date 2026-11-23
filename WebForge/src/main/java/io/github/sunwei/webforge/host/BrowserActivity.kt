package io.github.sunwei.webforge.host

import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.WebPage.Companion.builder

/** 需要在同一 Activity 内替换页面时可使用此基类。  */
abstract class BrowserActivity : AppCompatActivity() {
    protected var page: WebPage? = null
        private set

    @MainThread
    protected fun showPage(container: ViewGroup, url: String): WebPage {
        if (page != null) {
            page!!.close()
            page = null
        }
        val builder = builder(this, container)
        configurePage(builder)
        page = builder.open(url)
        return page!!
    }

    protected fun configurePage(builder: WebPage.Builder) {}

    override fun onDestroy() {
        try {
            super.onDestroy()
        } finally {
            page = null
        }
    }
}
