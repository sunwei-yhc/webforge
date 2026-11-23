package io.github.sunwei.webforge.host

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import io.github.sunwei.webforge.R
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.client.ChromeCallbacks
import io.github.sunwei.webforge.client.NavigationCallbacks
import io.github.sunwei.webforge.config.ExternalLinks

/** 带工具栏的通用网页容器，生命周期与设置复用基础 Fragment。  */
open class ToolbarWebFragment : BrowserFragment() {
    private var backView: View? = null
    private var lineView: View? = null
    private var titleView: TextView? = null
    private var popupMenu: PopupMenu? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webforge, container, false)
    }

    public override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 先绑定工具栏，首次加载的同步回调也能安全更新视图。
        initView(view)
        super.onViewCreated(view, savedInstanceState)
        val webView = page!!.view
        if (webView != null) {
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER)
        }
    }

    override fun pageContainer(view: View): ViewGroup {
        return view.findViewById<ViewGroup>(R.id.webforge_page_container)
    }

    public override fun initialUrl(): String {
        val arguments = getArguments()
        val url = if (arguments == null) null else arguments.getString(URL_KEY)
        return (if (android.text.TextUtils.isEmpty(url)) "about:blank" else url)!!
    }

    override fun configurePage(builder: WebPage.Builder) {
        builder.externalLinks(ExternalLinks.BLOCK)
            .chromeCallbacks(object : ChromeCallbacks() {
                public override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    titleView!!.setText(title)
                }
            })
            .navigationCallbacks(object : NavigationCallbacks() {
                public override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val visibility = if (TextUtils.equals(url, initialUrl())) View.GONE else View.VISIBLE
                    backView!!.setVisibility(visibility)
                    lineView!!.setVisibility(visibility)
                }
            })
    }

    protected fun initView(view: View) {
        backView = view.findViewById<View?>(R.id.iv_back)
        lineView = view.findViewById<View?>(R.id.view_line)
        titleView = view.findViewById<TextView?>(R.id.toolbar_title)
        backView!!.setVisibility(View.GONE)
        lineView!!.setVisibility(View.GONE)
        backView!!.setOnClickListener(View.OnClickListener { v: View? ->
            val page = page
            if (page != null && !page.goBack()) {
                requireActivity().finish()
            }
        })
        view.findViewById<View?>(R.id.iv_finish).setOnClickListener(View.OnClickListener { v: View? -> requireActivity().finish() })
        val menuAnchor = view.findViewById<View>(R.id.iv_more)
        menuAnchor.setOnClickListener(View.OnClickListener { anchor: View? -> this.showMenu(anchor) })
        menuAnchor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}

            override fun onViewDetachedFromWindow(view: View) {
                // 此时锚点仍能访问原窗口的 ViewTreeObserver，PopupWindow 可移除滚动监听。
                dismissMenu()
            }
        })
    }

    private fun showMenu(anchor: View?) {
        if (popupMenu == null) {
            popupMenu = PopupMenu(requireContext(), anchor)
            popupMenu!!.inflate(R.menu.toolbar_menu)
            popupMenu!!.setOnMenuItemClickListener { item -> handleMenuItem(item) }
        }
        popupMenu!!.show()
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        val page = page ?: return false
        val webView = page.view ?: return false
        val url = webView.getUrl()
        return when (item.getItemId()) {
            R.id.refresh -> {
                page.reload()
                true
            }
            R.id.copy -> {
                val clipboard = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                if (clipboard != null && url != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, url))
                }
                true
            }
            R.id.default_browser -> {
                openBrowser(url)
                true
            }
            R.id.default_clean -> {
                page.clearBrowserData()
                Toast.makeText(requireContext(), "已清理缓存", Toast.LENGTH_SHORT).show()
                true
            }
            else -> false
        }
    }

    private fun openBrowser(url: String?) {
        if (TextUtils.isEmpty(url)) {
            return
        }
        val uri = Uri.parse(url)
        if (!"http".equals(uri.getScheme(), ignoreCase = true)
            && !"https".equals(uri.getScheme(), ignoreCase = true)
        ) {
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onDestroyView() {
        dismissMenu()
        super.onDestroyView()
        backView = null
        lineView = null
        titleView = null
    }

    private fun dismissMenu() {
        val menu = popupMenu
        popupMenu = null
        if (menu != null) {
            menu.dismiss()
        }
    }

    companion object {
        const val URL_KEY: String = "url_key"
        @JvmStatic
        fun newInstance(url: String): ToolbarWebFragment {
            val fragment = ToolbarWebFragment()
            val args = Bundle()
            args.putString(URL_KEY, url)
            fragment.setArguments(args)
            return fragment
        }
    }
}
