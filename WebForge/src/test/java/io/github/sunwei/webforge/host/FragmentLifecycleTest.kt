package io.github.sunwei.webforge.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.WebPageOperationsTest.RecordingWebView
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FragmentLifecycleTest {
    @Test
    fun detachedFragmentReleasesWebViewAndRecreatesItOnAttach() {
        val activity = Robolectric.buildActivity<FragmentActivity>(FragmentActivity::class.java).setup().get()
        val fragment = Page()
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "page").commitNow()
        val first = fragment.webView!!
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow()
        Assert.assertEquals(1, first.destroyCount.toLong())
        Assert.assertNull(fragment.page)
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow()
        Assert.assertNotSame(first, fragment.webView)
        Assert.assertNotNull(fragment.page)
    }

    @Test
    fun toolbarFragmentWithoutArgumentsLoadsBlankPage() {
        val activity = Robolectric.buildActivity<FragmentActivity>(FragmentActivity::class.java).setup().get()
        val fragment = ToolbarWebFragment()
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "toolbar").commitNow()
        Assert.assertEquals("about:blank", fragment.initialUrl())
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow()
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow()
        Assert.assertNotNull(fragment.getView())
    }

    class Page : BrowserFragment() {
        var webView: RecordingWebView? = null
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?, state: Bundle?
        ): View? {
            return FrameLayout(requireContext())
        }

        override fun pageContainer(view: View): ViewGroup {
            return view as ViewGroup
        }

        override fun configurePage(builder: WebPage.Builder) {
            webView = RecordingWebView(requireContext())
            builder.webView(webView!!)
        }
    }
}
