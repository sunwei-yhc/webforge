package android.sanshisan.web

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.host.BrowserFragment
import io.github.sunwei.webforge.host.ToolbarWebFragment

/** 使用系统 WebView 的基础页面示例。 */
class FragmentLoadweb : BrowserFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_web, container, false)
    }

    override fun pageContainer(view: View): ViewGroup {
        return view.findViewById(R.id.container)
    }

    override fun configurePage(builder: WebPage.Builder) {
        builder.enableBuiltInBridges()
    }

    override fun initialUrl(): String {
        return arguments?.getString(ToolbarWebFragment.URL_KEY)
            ?: "file:///android_asset/webview_h5_test.html"
    }
}
