package io.github.sunwei.webforge.client;

import androidx.activity.ComponentActivity;
import android.net.Uri;
import android.webkit.PermissionRequest;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.sunwei.webforge.WebPage;
import io.github.sunwei.webforge.ui.PageUiController;

import static org.junit.Assert.*;

/** 系统权限结果可能晚于页面取消或销毁，不能再次授权给旧请求。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PermissionLifecycleTest {
    private WebInteractionClient client;
    private CapturingController controller;
    private WebPage page;
    private Request request;

    @Before
    public void setUp() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        activity.setContentView(parent);
        controller = new CapturingController();
        page = WebPage.builder(activity, parent)
                .progress(false).uiController(controller).build();
        client = (WebInteractionClient) org.robolectric.Shadows
                .shadowOf(page.getView()).getWebChromeClient();
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
        client.release(true);
        client.release(true);
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
        client.release(true);
        assertEquals(1, request.grants);
        assertEquals(0, request.denials);
    }

    @Test
    public void destroyedClientRejectsNewRequests() {
        client.release(true);
        client.onPermissionRequest(request);
        assertEquals(1, request.denials);
        assertNull(controller.pending);
    }

    @Test
    public void pageCloseDeniesPendingChromeClientRequests() {
        WebInteractionClient chrome = (WebInteractionClient) org.robolectric.Shadows
                .shadowOf(page.getView()).getWebChromeClient();
        chrome.onPermissionRequest(request);
        page.close();
        controller.pending.grant(request.getResources());
        assertEquals(0, request.grants);
        assertEquals(1, request.denials);
    }

    public static class CapturingController extends PageUiController {
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
