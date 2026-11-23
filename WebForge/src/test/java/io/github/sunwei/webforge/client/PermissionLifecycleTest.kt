package io.github.sunwei.webforge.client

import android.net.Uri
import android.webkit.PermissionRequest
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import io.github.sunwei.webforge.WebPage
import io.github.sunwei.webforge.WebPage.Companion.builder
import io.github.sunwei.webforge.ui.PageUiController
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** 系统权限结果可能晚于页面取消或销毁，不能再次授权给旧请求。  */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PermissionLifecycleTest {
    private var client: WebInteractionClient? = null
    private var controller: CapturingController? = null
    private var page: WebPage? = null
    private var request: Request? = null

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity<ComponentActivity>(ComponentActivity::class.java).setup().get()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        controller = CapturingController()
        page = builder(activity, parent)
            .progress(false).uiController(controller!!).build()
        client = Shadows
            .shadowOf(page!!.view).getWebChromeClient() as WebInteractionClient
        request = Request()
    }

    @Test
    fun cancelIgnoresLateGrant() {
        client!!.onPermissionRequest(request)
        Assert.assertNotNull(controller!!.pending)
        client!!.onPermissionRequestCanceled(request)
        controller!!.pending!!.grant(request!!.getResources())
        Assert.assertEquals(0, request!!.grants.toLong())
        Assert.assertEquals(0, request!!.denials.toLong())
    }

    @Test
    fun destroyDeniesPendingRequestOnlyOnce() {
        client!!.onPermissionRequest(request)
        client!!.release(true)
        client!!.release(true)
        controller!!.pending!!.grant(request!!.getResources())
        controller!!.pending!!.deny()
        Assert.assertEquals(0, request!!.grants.toLong())
        Assert.assertEquals(1, request!!.denials.toLong())
    }

    @Test
    fun activeRequestCompletesOnlyOnce() {
        client!!.onPermissionRequest(request)
        controller!!.pending!!.grant(request!!.getResources())
        controller!!.pending!!.grant(request!!.getResources())
        controller!!.pending!!.deny()
        client!!.release(true)
        Assert.assertEquals(1, request!!.grants.toLong())
        Assert.assertEquals(0, request!!.denials.toLong())
    }

    @Test
    fun destroyedClientRejectsNewRequests() {
        client!!.release(true)
        client!!.onPermissionRequest(request)
        Assert.assertEquals(1, request!!.denials.toLong())
        Assert.assertNull(controller!!.pending)
    }

    @Test
    fun pageCloseDeniesPendingChromeClientRequests() {
        val chrome = Shadows
            .shadowOf(page!!.view).getWebChromeClient() as WebInteractionClient
        chrome.onPermissionRequest(request)
        page!!.close()
        controller!!.pending!!.grant(request!!.getResources())
        Assert.assertEquals(0, request!!.grants.toLong())
        Assert.assertEquals(1, request!!.denials.toLong())
    }

    class CapturingController : PageUiController() {
        var pending: PermissionRequest? = null
        public override fun onPermissionRequest(request: PermissionRequest) {
            pending = request
        }
    }

    private class Request : PermissionRequest() {
        var grants: Int = 0
        var denials: Int = 0
        override fun getOrigin(): Uri? {
            return Uri.parse("https://example.com")
        }

        override fun getResources(): Array<String> {
            return arrayOf(RESOURCE_AUDIO_CAPTURE)
        }

        override fun grant(resources: Array<String>?) {
            grants++
        }

        override fun deny() {
            denials++
        }
    }
}
