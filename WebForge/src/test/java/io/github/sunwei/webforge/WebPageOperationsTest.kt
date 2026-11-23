package io.github.sunwei.webforge

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import io.github.sunwei.webforge.bridge.FileUploadBridge
import io.github.sunwei.webforge.util.WebForgeUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.function.ThrowingRunnable
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicBoolean

/** 覆盖真实主线程队列与页面销毁之间的交互。  */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class WebPageOperationsTest {
    private var activity: ComponentActivity? = null
    private var parent: FrameLayout? = null
    private var webView: RecordingWebView? = null
    private var page: WebPage? = null

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        parent = FrameLayout(activity!!)
        activity!!.setContentView(parent)
        webView = RecordingWebView(activity!!)
        page = WebPage.builder(activity!!, parent!!).webView(webView!!).build()
    }

    @Test
    fun mainThreadCallsRunImmediately() {
        page!!.load("https://example.com")
        page!!.evaluateJavaScript("window.ready()")
        Assert.assertEquals("https://example.com", webView!!.urls.get(0))
        Assert.assertEquals("window.ready()", webView!!.scripts.get(0))
    }

    @Test
    @Throws(Exception::class)
    fun backgroundCallsRunOnMainThread() {
        onWorker(Runnable {
            page!!.load("https://example.com")
            page!!.evaluateJavaScript("window.ready()")
            page!!.reload()
            page!!.loadHtml("data", "https://example.com")
            page!!.stop()
            page!!.post("https://example.com", byteArrayOf(1))
        })
        Assert.assertTrue(webView!!.urls.isEmpty())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Assert.assertEquals(1, webView!!.urls.size.toLong())
        Assert.assertEquals(1, webView!!.scripts.size.toLong())
        Assert.assertEquals(1, webView!!.reloads.toLong())
        Assert.assertEquals(1, webView!!.baseDataLoads.toLong())
        Assert.assertEquals(1, webView!!.stops.toLong())
        Assert.assertEquals(1, webView!!.posts.toLong())
        Assert.assertFalse(webView!!.wrongThread)
    }

    @Test
    @Throws(Exception::class)
    fun destroyCancelsQueuedCallsAndRejectsNewCalls() {
        onWorker(Runnable {
            page!!.load("https://example.com")
            page!!.evaluateJavaScript("window.ready()")
        })
        page!!.close()
        page!!.load("https://example.org")
        page!!.evaluateJavaScript("window.late()")
        page!!.reload()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Assert.assertTrue(webView!!.urls.isEmpty())
        Assert.assertTrue(webView!!.scripts.isEmpty())
        Assert.assertEquals(0, webView!!.reloads.toLong())
        Assert.assertEquals(1, webView!!.destroyCount.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun backgroundDestroyIsIdempotentAndDetachesView() {
        onWorker(Runnable { page!!.close() })
        Assert.assertTrue(page!!.isClosed)
        Assert.assertEquals(0, webView!!.destroyCount.toLong())
        page!!.load("https://late.example")
        page!!.close()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Assert.assertEquals(0, parent!!.getChildCount().toLong())
        Assert.assertNull(webView!!.getParent())
        Assert.assertEquals(1, webView!!.destroyCount.toLong())
        Assert.assertFalse(webView!!.wrongThread)
        Assert.assertTrue(webView!!.urls.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun destroyDoesNotCancelOtherPagesOrMainThreadTasks() {
        val other = RecordingWebView(activity!!)
        val otherPage = WebPage.builder(activity!!, FrameLayout(activity!!)).webView(other).build()
        val unrelated = AtomicBoolean()
        onWorker(Runnable {
            Handler(Looper.getMainLooper()).post(Runnable { unrelated.set(true) })
            otherPage.load("https://other.example")
        })
        page!!.close()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Assert.assertTrue(unrelated.get())
        Assert.assertEquals(1, other.urls.size.toLong())
        otherPage.close()
    }

    @Test
    fun lifecycleNeverChangesGlobalWebViewTimers() {
        val previousResumes = webView!!.resumes
        page!!.pause()
        page!!.resume()
        page!!.close()
        page!!.pause()
        page!!.resume()
        page!!.close()
        Assert.assertEquals(1, webView!!.pauses.toLong())
        Assert.assertEquals((previousResumes + 1).toLong(), webView!!.resumes.toLong())
        Assert.assertEquals(0, webView!!.timerChanges.toLong())
        Assert.assertEquals(1, webView!!.destroyCount.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun queuedPostDataAndHeadersUseSnapshots() {
        val headers: MutableMap<String?, String?> = HashMap<String?, String?>()
        headers.put("X-Test", "before")
        val body = byteArrayOf(1, 2)
        onWorker(Runnable {
            page!!.load("https://example.com", headers)
            page!!.post("https://example.com", body)
            headers.put("X-Test", "after")
            body[0] = 9
        })
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Assert.assertEquals("before", webView!!.headers!!.get("X-Test"))
        Assert.assertArrayEquals(byteArrayOf(1, 2), webView!!.postData)
    }

    @Test
    fun configuredHeadersApplyOnlyToMatchingOriginAndUseSnapshot() {
        val headers: MutableMap<String?, String?> = HashMap<String?, String?>()
        headers.put("X-Test", "before")
        val configuredView = RecordingWebView(activity!!)
        val configured = WebPage.builder(activity!!, parent!!).webView(configuredView)
            .headers("https://EXAMPLE.com:443/a", headers).build()
        headers.put("X-Test", "after")
        configured.load("https://example.com:8443/b")
        Assert.assertNull(configuredView.headers)
        configured.load("https://example.com/b")
        Assert.assertEquals("before", configuredView.headers!!.get("X-Test"))
        configured.close()
    }

    @Test
    @Throws(Exception::class)
    fun callEscapesStringsAndKeepsExplicitJsonTypes() {
        val text = "引号\"、单引号'、换行\n、反斜杠\\"
        page!!.callJavaScript(
            "bridge.done", text, JSONObject().put("ok", true),
            JSONArray("[1,2]"), null, "[1,2]"
        )
        val script = webView!!.scripts.get(0)
        val args = JSONArray("[" + script.substring(script.indexOf('(') + 1, script.length - 1) + "]")
        Assert.assertEquals(text, args.getString(0))
        Assert.assertTrue(args.getJSONObject(1).getBoolean("ok"))
        Assert.assertEquals(2, args.getJSONArray(2).length().toLong())
        Assert.assertTrue(args.isNull(3))
        Assert.assertEquals("[1,2]", args.getString(4))
    }

    @Test
    fun javascriptResultAfterDestroyIsIgnored() {
        val called = AtomicBoolean()
        page!!.evaluateJavaScript("window.ready()", ValueCallback { value: String? -> called.set(true) })
        val pending = webView!!.jsResult!!
        page!!.close()
        pending.onReceiveValue("late result")
        Assert.assertFalse(called.get())
    }

    @Test
    fun fullscreenVideoRespectsOrientationSettingAndBackClosesIt() {
        activity!!.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        val videoView = RecordingWebView(activity!!)
        val videoPage = WebPage.builder(activity!!, parent!!).webView(videoView)
            .progress(false).landscapeVideo(false).build()
        val hidden = AtomicBoolean()
        Shadows.shadowOf(videoView).getWebChromeClient()
            .onShowCustomView(View(activity), WebChromeClient.CustomViewCallback { hidden.set(true) })

        Assert.assertEquals(View.GONE.toLong(), videoView.getVisibility().toLong())
        Assert.assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT.toLong(), activity!!.getRequestedOrientation().toLong())
        Assert.assertTrue(videoPage.goBack())
        Assert.assertTrue(hidden.get())
        Assert.assertEquals(View.VISIBLE.toLong(), videoView.getVisibility().toLong())
        videoPage.close()
    }

    @Test
    fun readyRegistersBuiltInAndHostJavascriptInterfaces() {
        val bridge: Any = object : Any() {
            @JavascriptInterface
            fun notifyReady() {
            }
        }
        val bridgeView = RecordingWebView(activity!!)
        val bridged = WebPage.builder(activity!!, parent!!).webView(bridgeView)
            .enableBuiltInBridges().bridge("HostBridge", bridge).build()
        Assert.assertSame(bridge, Shadows.shadowOf(bridgeView).getJavascriptInterface("HostBridge"))
        Assert.assertTrue(Shadows.shadowOf(bridgeView).getJavascriptInterface("webForge") is FileUploadBridge)
        Assert.assertThrows<IllegalArgumentException?>(
            IllegalArgumentException::class.java,
            ThrowingRunnable { bridged.addJavaScriptBridge("Invalid", Any()) })
        bridged.close()
    }

    @Test
    fun fileChooserCancelsCleanlyWhenActivityIsFinishing() {
        activity!!.finish()
        val results: MutableList<Array<Uri>?> = ArrayList()
        val handled = WebForgeUtils.showFileChooserCompat(
            activity, webView,
            ValueCallback { value -> results.add(value) }, null, null, null, null, null
        )
        Assert.assertTrue(handled)
        Assert.assertEquals(1, results.size.toLong())
        Assert.assertNull(results.get(0))
    }

    open class RecordingWebView(context: Context) : WebView(context) {
        val urls: MutableList<String> = ArrayList()
        val scripts: MutableList<String> = ArrayList<String>()
        var headers: MutableMap<String, String>? = null
        var postData: ByteArray? = null
        var jsResult: ValueCallback<String?>? = null
        var reloads: Int = 0
        var baseDataLoads: Int = 0
        var stops: Int = 0
        var posts: Int = 0
        var destroyCount: Int = 0
        var pauses: Int = 0
        var resumes: Int = 0
        var timerChanges: Int = 0
        var wrongThread: Boolean = false

        private fun checkMain() {
            wrongThread = wrongThread or (Looper.myLooper() != Looper.getMainLooper())
        }

        override fun loadUrl(url: String) {
            checkMain()
            urls.add(url)
        }

        override fun loadUrl(url: String, headers: MutableMap<String, String>) {
            loadUrl(url)
            this.headers = headers
        }

        override fun evaluateJavascript(script: String, callback: ValueCallback<String?>?) {
            checkMain()
            scripts.add(script)
            jsResult = callback
        }

        override fun reload() {
            checkMain()
            reloads++
        }

        override fun loadDataWithBaseURL(base: String?, data: String, mime: String?, encoding: String?, history: String?) {
            checkMain()
            baseDataLoads++
        }

        override fun stopLoading() {
            checkMain()
            stops++
        }

        override fun postUrl(url: String, data: ByteArray) {
            checkMain()
            posts++
            postData = data
        }

        override fun destroy() {
            checkMain()
            this.destroyCount++
            super.destroy()
        }

        override fun onPause() {
            checkMain()
            pauses++
        }

        override fun onResume() {
            checkMain()
            resumes++
        }

        override fun pauseTimers() {
            timerChanges++
        }

        override fun resumeTimers() {
            timerChanges++
        }
    }

    companion object {
        @Throws(Exception::class)
        private fun onWorker(task: Runnable?) {
            val worker = Thread(task)
            worker.start()
            worker.join()
        }
    }
}
