package io.github.sunwei.webforge.client;

import android.app.Activity;
import android.net.Uri;
import android.webkit.PermissionRequest;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.ui.WebForgeUIControllerImplBase;

import static org.junit.Assert.*;

/** 系统权限结果可能晚于页面取消或销毁，不能再次授权给旧请求。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PermissionLifecycleTest {
    private DefaultChromeClient client;
    private CapturingController controller;
    private WebForge agent;
    private Request request;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        activity.setContentView(parent);
        controller = new CapturingController();
        agent = WebForge.with(activity).setWebForgeParent(parent, new FrameLayout.LayoutParams(-1, -1))
                .useDefaultIndicator().setWebForgeUIController(controller).createWebForge().ready().get();
        client = new DefaultChromeClient(activity, null, null, null, null,
                agent.getWebCreator().getWebView());
        request = new Request();
    }

    @Test
    public void cancelIgnoresLateGrant() {
        client.onPermissionRequest(request);
        assertNotNull(controller.pending);
        client.onPermissionRequestCanceled(request);
        controller.pending.grant(request.getResources());
        assertEquals(0, request.grants);
        assertEquals(0, request.denials);
    }

    @Test
    public void destroyDeniesPendingRequestOnlyOnce() {
        client.onPermissionRequest(request);
        client.destroy();
        client.destroy();
        controller.pending.grant(request.getResources());
        controller.pending.deny();
        assertEquals(0, request.grants);
        assertEquals(1, request.denials);
    }

    @Test
    public void activeRequestCompletesOnlyOnce() {
        client.onPermissionRequest(request);
        controller.pending.grant(request.getResources());
        controller.pending.grant(request.getResources());
        controller.pending.deny();
        client.destroy();
        assertEquals(1, request.grants);
        assertEquals(0, request.denials);
    }

    @Test
    public void destroyedClientRejectsNewRequests() {
        client.destroy();
        client.onPermissionRequest(request);
        assertEquals(1, request.denials);
        assertNull(controller.pending);
    }

    @Test
    public void agentDestroyClosesItsChromeClientRequests() {
        DefaultChromeClient chrome = (DefaultChromeClient) org.robolectric.Shadows
                .shadowOf(agent.getWebCreator().getWebView()).getWebChromeClient();
        chrome.onPermissionRequest(request);
        agent.destroy();
        controller.pending.grant(request.getResources());
        assertEquals(0, request.grants);
        assertEquals(1, request.denials);
    }

    public static class CapturingController extends WebForgeUIControllerImplBase {
        PermissionRequest pending;
        @Override public void onPermissionRequest(PermissionRequest request) { pending = request; }
    }

    private static class Request extends PermissionRequest {
        int grants, denials;
        @Override public Uri getOrigin() { return Uri.parse("https://example.com"); }
        @Override public String[] getResources() { return new String[]{RESOURCE_AUDIO_CAPTURE}; }
        @Override public void grant(String[] resources) { grants++; }
        @Override public void deny() { denials++; }
    }
}
