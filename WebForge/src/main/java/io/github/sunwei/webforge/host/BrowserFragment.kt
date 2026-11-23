package io.github.sunwei.webforge.host

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.WebPage.Companion.builder

/** 每次创建 Fragment 视图时打开一个新的网页。  */
abstract class BrowserFragment : Fragment() {
    var page: WebPage? = null
        private set

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        val builder = builder(this, pageContainer(view))
        configurePage(builder)
        page = builder.open(initialUrl())
    }

    protected abstract fun pageContainer(view: View): ViewGroup

    protected open fun initialUrl(): String {
        return "about:blank"
    }

    protected open fun configurePage(builder: WebPage.Builder) {}

    override fun onDestroyView() {
        page = null
        super.onDestroyView()
    }
}
