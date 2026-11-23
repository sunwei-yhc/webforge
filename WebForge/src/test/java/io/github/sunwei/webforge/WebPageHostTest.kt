package io.github.sunwei.webforge

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.sunwei.webforge.WebPage.Companion.builder
import io.github.sunwei.webforge.WebPage.Companion.open
import io.github.sunwei.webforge.WebPageOperationsTest.RecordingWebView
import io.github.sunwei.webforge.bridge.AndroidBridge
import io.github.sunwei.webforge.bridge.FileUploadBridge
import io.github.sunwei.webforge.client.ChromeCallbacks
import io.github.sunwei.webforge.client.NavigationCallbacks
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView
import org.junit.Assert
import org.junit.Test
import org.junit.function.ThrowingRunnable
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebPageHostTest {
    @Test
    fun activityOwnsPageFromCreationThroughDestruction() {
        val controller =
            Robolectric.buildActivity<ComponentActivity?>(ComponentActivity::class.java).create()
        val activity = controller.get()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        val view = RecordingWebView(activity)
        val page = builder(activity, parent).webView(view).open(URL)

        Assert.assertSame(view, page.view)
        Assert.assertEquals(URL, view.urls.get(0))
        Assert.assertEquals(1, view.pauses.toLong())
        Assert.assertNull(Shadows.shadowOf(view).getJavascriptInterface("Android"))
        Assert.assertNull(Shadows.shadowOf(view).getJavascriptInterface("webForge"))

        controller.start().resume().pause().stop().destroy()
        Assert.assertEquals(1, view.resumes.toLong())
        Assert.assertEquals(2, view.pauses.toLong())
        Assert.assertEquals(1, view.destroyCount.toLong())
        Assert.assertEquals(0, parent.getChildCount().toLong())
        page.load(URL + "/closed")
        page.reload()
        page.close()
        Assert.assertEquals(1, view.urls.size.toLong())
        Assert.assertEquals(0, view.reloads.toLong())
        Assert.assertEquals(1, view.destroyCount.toLong())
    }

    @Test
    fun builtInBridgesAreReadyBeforeInitialNavigation() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val view: RecordingWebView = object : RecordingWebView(activity) {
            override fun loadUrl(url: String) {
                Assert.assertTrue(Shadows.shadowOf(this).getJavascriptInterface("Android") is AndroidBridge)
                Assert.assertTrue(Shadows.shadowOf(this).getJavascriptInterface("webForge") is FileUploadBridge)
                super.loadUrl(url)
            }
        }

        val page = builder(activity, FrameLayout(activity))
            .webView(view).enableBuiltInBridges().open(URL)
        Assert.assertEquals(URL, view.urls.get(0))
        page.close()
    }

    @Test
    fun dialerBridgePrefillsNumberAndRejectsNonPhoneInput() {
        val activity = Robolectric.buildActivity<ComponentActivity?>(ComponentActivity::class.java).setup().get()
        val bridge = AndroidBridge(null, activity)

        bridge.openDialer("tel:10086")
        Assert.assertNull(Shadows.shadowOf(activity).getNextStartedActivity())

        bridge.openDialer(" +8610086 ")
        val intent = Shadows.shadowOf(activity).getNextStartedActivity()
        Assert.assertEquals(Intent.ACTION_DIAL, intent.getAction())
        Assert.assertEquals("tel", intent.getData()!!.getScheme())
        Assert.assertEquals("+8610086", intent.getData()!!.getSchemeSpecificPart())
    }

    @Test
    fun smsBridgeRejectsUriInputAndPrefillsMessage() {
        val activity = Robolectric.buildActivity<ComponentActivity?>(ComponentActivity::class.java).setup().get()
        val bridge = AndroidBridge(null, activity)
        bridge.openSms("10086?body=other", "正文")
        Assert.assertNull(Shadows.shadowOf(activity).getNextStartedActivity())
        bridge.openSms(" +8610086 ", "内容 & 中文")
        val intent = Shadows.shadowOf(activity).getNextStartedActivity()
        Assert.assertEquals(Intent.ACTION_SENDTO, intent.getAction())
        Assert.assertEquals("smsto", intent.getData()!!.getScheme())
        Assert.assertEquals("+8610086", intent.getData()!!.getSchemeSpecificPart())
        Assert.assertEquals("内容 & 中文", intent.getStringExtra("sms_body"))
    }

    @Test
    fun emailBridgeEncodesSubjectAndBodyWithoutInjectingRecipients() {
        val activity = Robolectric.buildActivity<ComponentActivity?>(ComponentActivity::class.java).setup().get()
        val bridge = AndroidBridge(null, activity)
        bridge.openEmail("user@example.com?bcc=other@example.com", "主题", "正文")
        Assert.assertNull(Shadows.shadowOf(activity).getNextStartedActivity())
        bridge.openEmail("user@example.com", "主题 &bcc=other@example.com", "中文\n内容 #1")
        val intent = Shadows.shadowOf(activity).getNextStartedActivity()
        Assert.assertEquals(Intent.ACTION_SENDTO, intent.getAction())
        Assert.assertEquals("mailto", intent.getData()!!.getScheme())
        val query = Uri.parse(
            "https://example.com/?"
                    + intent.getData()!!.getEncodedSchemeSpecificPart().split("\\?".toRegex(), limit = 2).toTypedArray()[1]
        )
        Assert.assertEquals("主题 &bcc=other@example.com", query.getQueryParameter("subject"))
        Assert.assertEquals("中文\n内容 #1", query.getQueryParameter("body"))
        Assert.assertNull(query.getQueryParameter("bcc"))
    }

    @Test
    fun shareBridgeOpensChooserWithPlainText() {
        val activity = Robolectric.buildActivity<ComponentActivity?>(ComponentActivity::class.java).setup().get()
        AndroidBridge(null, activity).shareText("WebForge 分享")

        val chooser = Shadows.shadowOf(activity).getNextStartedActivity()
        Assert.assertEquals(Intent.ACTION_CHOOSER, chooser.getAction())
        val share = chooser.getParcelableExtra<Intent?>(Intent.EXTRA_INTENT)
        Assert.assertEquals(Intent.ACTION_SEND, share!!.getAction())
        Assert.assertEquals("text/plain", share.getType())
        Assert.assertEquals("WebForge 分享", share.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    @Config(sdk = [29])
    fun mediaPermissionRequiresOnlyReadStorageOnAndroid10() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val bridge = AndroidBridge(null, activity)

        Assert.assertFalse(bridge.hasPermission("media"))
        Shadows.shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        Assert.assertTrue(bridge.hasPermission("media"))
    }

    @Test
    fun backUsesWebHistoryAndFallsThroughAfterManualDestroy() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val hostBacks = intArrayOf(0)
        activity.getOnBackPressedDispatcher().addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                hostBacks[0]++
            }
        })
        val view: RecordingWebView = object : RecordingWebView(activity) {
            private var hasHistory = true

            override fun canGoBack(): Boolean {
                return hasHistory
            }

            override fun goBack() {
                hasHistory = false
            }
        }
        val parent = FrameLayout(activity)
        val page = builder(activity, parent).webView(view).build()

        activity.getOnBackPressedDispatcher().onBackPressed()
        Assert.assertFalse(view.canGoBack())
        Assert.assertEquals(0, hostBacks[0].toLong())
        activity.getOnBackPressedDispatcher().onBackPressed()
        Assert.assertEquals(1, hostBacks[0].toLong())
        page.close()
        activity.getOnBackPressedDispatcher().onBackPressed()
        Assert.assertEquals(2, hostBacks[0].toLong())
        Assert.assertEquals(0, parent.getChildCount().toLong())
    }

    @Test
    fun failedInitialLoadDestroysThePage() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity)
        val view: RecordingWebView = object : RecordingWebView(activity) {
            override fun loadUrl(url: String) {
                throw IllegalStateException("test load failure")
            }
        }
        val builder = builder(activity, parent).webView(view)

        Assert.assertThrows<IllegalArgumentException?>(IllegalArgumentException::class.java, ThrowingRunnable { builder.open("   ") })
        Assert.assertEquals(0, parent.getChildCount().toLong())
        Assert.assertEquals(0, view.destroyCount.toLong())
        Assert.assertThrows<IllegalStateException?>(IllegalStateException::class.java, ThrowingRunnable { builder.open(URL) })
        Assert.assertEquals(0, parent.getChildCount().toLong())
        Assert.assertEquals(1, view.destroyCount.toLong())
    }

    @Test
    fun rendererExitCleansOnlyAffectedViewAndNotifiesAfterDetach() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity)
        val view = RecordingWebView(activity)
        val notifications = intArrayOf(0)
        val page = builder(activity, parent).webView(view)
            .navigationCallbacks(NavigationCallbacks())
            .onRendererGone(Runnable {
                Assert.assertEquals(1, view.destroyCount.toLong())
                Assert.assertNull(view.getParent())
                Assert.assertEquals(1, parent.getChildCount().toLong())
                notifications[0]++
            }).open(URL)
        val other = open(activity, parent, URL)
        val client = Shadows.shadowOf(view).getWebViewClient()

        Assert.assertTrue(client.onRenderProcessGone(view, null))
        Assert.assertTrue(client.onRenderProcessGone(view, null))
        Assert.assertEquals(1, notifications[0].toLong())
        Assert.assertTrue(page.isClosed)
        Assert.assertNull(page.view)
        Assert.assertEquals(0, view.stops.toLong())
        Assert.assertFalse(other.isClosed)
        other.close()
    }

    @Test
    fun failedSetupDoesNotDisconnectCallbacksOwnedByAnotherPage() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity)
        val callback = NavigationCallbacks()
        val first = RecordingWebView(activity)
        val duplicate = RecordingWebView(activity)
        val original = builder(activity, parent).webView(first)
            .blockUnknownSchemes(true).navigationCallbacks(callback).build()

        Assert.assertThrows<IllegalStateException?>(IllegalStateException::class.java, ThrowingRunnable {
            builder(activity, parent)
                .webView(duplicate).navigationCallbacks(callback).build()
        })
        Assert.assertEquals(1, duplicate.destroyCount.toLong())
        Assert.assertEquals(1, parent.getChildCount().toLong())
        Assert.assertTrue(
            Shadows.shadowOf(first).getWebViewClient()
                .shouldOverrideUrlLoading(first, "example-custom://page")
        )
        Assert.assertFalse(Shadows.shadowOf(first).getWebViewClient().shouldOverrideUrlLoading(first, URL))
        original.close()
    }

    @Test
    fun chromeCallbacksForwardProgressToCustomIndicatorInRegistrationOrder() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val events = StringBuilder()
        val indicator: BaseIndicatorView = object : BaseIndicatorView(activity) {
            public override fun setProgress(progress: Int) {
                events.append(progress)
            }

            public override fun offerLayoutParams(): LayoutParams {
                return LayoutParams(100, 3)
            }
        }
        val outer: ChromeCallbacks = object : ChromeCallbacks() {
            public override fun onProgressChanged(view: WebView?, progress: Int) {
                events.append("outer ")
                super.onProgressChanged(view, progress)
            }
        }
        val inner: ChromeCallbacks = object : ChromeCallbacks() {
            public override fun onProgressChanged(view: WebView?, progress: Int) {
                events.append("inner ")
                super.onProgressChanged(view, progress)
            }
        }
        val page = builder(activity, FrameLayout(activity))
            .progress(indicator).chromeCallbacks(outer).chromeCallbacks(inner).build()
        val view = page.view

        Shadows.shadowOf(view).getWebChromeClient().onProgressChanged(view, 35)

        Assert.assertEquals("outer inner 35", events.toString())
        page.close()
    }

    @Test
    fun defaultErrorPageStillRetriesAndHidesAfterSuccess() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity)
        val view = RecordingWebView(activity)
        val page = builder(activity, parent).webView(view).open(URL)
        val client = Shadows.shadowOf(view).getWebViewClient()

        client.onPageStarted(view, URL, null)
        client.onReceivedError(view, WebViewClient.ERROR_HOST_LOOKUP, "lookup failed", URL)
        client.onPageFinished(view, URL)
        val error = parent.findViewById<View>(R.id.mainframe_error_container_id)
        Assert.assertEquals(View.VISIBLE.toLong(), error.getVisibility().toLong())

        Assert.assertTrue(error.performClick())
        Assert.assertEquals(1, view.reloads.toLong())
        client.onPageStarted(view, URL, null)
        client.onPageFinished(view, URL)

        Assert.assertEquals(View.GONE.toLong(), error.getVisibility().toLong())
        page.close()
    }

    @Test
    fun fragmentViewCanBeRecreatedWithASeparatePage() {
        val activity = Robolectric.buildActivity<FragmentActivity>(FragmentActivity::class.java).setup().get()
        val fragment = PageFragment()
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "page").commitNow()
        val first = fragment.webView!!
        val firstParent = fragment.requireView() as FrameLayout
        val stale = builder(fragment, firstParent)

        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow()
        Assert.assertEquals(1, first.destroyCount.toLong())
        Assert.assertEquals(0, firstParent.getChildCount().toLong())
        Assert.assertThrows<IllegalStateException?>(IllegalStateException::class.java, ThrowingRunnable { stale.build() })

        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow()
        Assert.assertNotSame(first, fragment.webView)
        Assert.assertEquals(URL, fragment.webView!!.urls.get(0))
        Assert.assertEquals(0, fragment.webView!!.destroyCount.toLong())
    }

    class PageFragment : Fragment() {
        var webView: RecordingWebView? = null

        override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
            return FrameLayout(requireContext())
        }

        override fun onViewCreated(view: View, state: Bundle?) {
            webView = RecordingWebView(requireContext())
            WebPage.builder(this, view as android.widget.FrameLayout).webView(webView!!).open(URL)
        }
    }

    companion object {
        private const val URL = "https://example.com"
    }
}
