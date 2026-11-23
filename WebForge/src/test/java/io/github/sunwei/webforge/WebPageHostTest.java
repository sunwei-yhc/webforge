package io.github.sunwei.webforge;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import io.github.sunwei.webforge.bridge.AndroidBridge;
import io.github.sunwei.webforge.bridge.FileUploadBridge;
import io.github.sunwei.webforge.client.ChromeCallbacks;
import io.github.sunwei.webforge.client.NavigationCallbacks;
import io.github.sunwei.webforge.ui.indicator.BaseIndicatorView;
import io.github.sunwei.webforge.WebPageOperationsTest.RecordingWebView;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WebPageHostTest {
    private static final String URL = "https://example.com";

    @Test
    public void activityOwnsPageFromCreationThroughDestruction() {
        ActivityController<ComponentActivity> controller =
                Robolectric.buildActivity(ComponentActivity.class).create();
        ComponentActivity activity = controller.get();
        FrameLayout parent = new FrameLayout(activity);
        activity.setContentView(parent);
        RecordingWebView view = new RecordingWebView(activity);
        WebPage page = WebPage.builder(activity, parent).webView(view).open(URL);

        assertSame(view, page.getView());
        assertEquals(URL, view.urls.get(0));
        assertEquals(1, view.pauses);
        assertNull(shadowOf(view).getJavascriptInterface("Android"));
        assertNull(shadowOf(view).getJavascriptInterface("webForge"));

        controller.start().resume().pause().stop().destroy();
        assertEquals(1, view.resumes);
        assertEquals(2, view.pauses);
        assertEquals(1, view.destroys);
        assertEquals(0, parent.getChildCount());
        page.load(URL + "/closed");
        page.reload();
        page.close();
        assertEquals(1, view.urls.size());
        assertEquals(0, view.reloads);
        assertEquals(1, view.destroys);
    }

    @Test
    public void builtInBridgesAreReadyBeforeInitialNavigation() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        RecordingWebView view = new RecordingWebView(activity) {
            @Override
            public void loadUrl(String url) {
                assertTrue(shadowOf(this).getJavascriptInterface("Android") instanceof AndroidBridge);
                assertTrue(shadowOf(this).getJavascriptInterface("webForge") instanceof FileUploadBridge);
                super.loadUrl(url);
            }
        };

        WebPage page = WebPage.builder(activity, new FrameLayout(activity))
                .webView(view).enableBuiltInBridges().open(URL);
        assertEquals(URL, view.urls.get(0));
        page.close();
    }

    @Test
    public void dialerBridgePrefillsNumberAndRejectsNonPhoneInput() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        AndroidBridge bridge = new AndroidBridge(null, activity);

        bridge.openDialer("tel:10086");
        assertNull(shadowOf(activity).getNextStartedActivity());

        bridge.openDialer(" +8610086 ");
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_DIAL, intent.getAction());
        assertEquals("tel", intent.getData().getScheme());
        assertEquals("+8610086", intent.getData().getSchemeSpecificPart());
    }

    @Test
    public void smsBridgeRejectsUriInputAndPrefillsMessage() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        AndroidBridge bridge = new AndroidBridge(null, activity);
        bridge.openSms("10086?body=other", "正文");
        assertNull(shadowOf(activity).getNextStartedActivity());
        bridge.openSms(" +8610086 ", "内容 & 中文");
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_SENDTO, intent.getAction());
        assertEquals("smsto", intent.getData().getScheme());
        assertEquals("+8610086", intent.getData().getSchemeSpecificPart());
        assertEquals("内容 & 中文", intent.getStringExtra("sms_body"));
    }

    @Test
    public void emailBridgeEncodesSubjectAndBodyWithoutInjectingRecipients() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        AndroidBridge bridge = new AndroidBridge(null, activity);
        bridge.openEmail("user@example.com?bcc=other@example.com", "主题", "正文");
        assertNull(shadowOf(activity).getNextStartedActivity());
        bridge.openEmail("user@example.com", "主题 &bcc=other@example.com", "中文\n内容 #1");
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_SENDTO, intent.getAction());
        assertEquals("mailto", intent.getData().getScheme());
        android.net.Uri query = android.net.Uri.parse("https://example.com/?"
                + intent.getData().getEncodedSchemeSpecificPart().split("\\?", 2)[1]);
        assertEquals("主题 &bcc=other@example.com", query.getQueryParameter("subject"));
        assertEquals("中文\n内容 #1", query.getQueryParameter("body"));
        assertNull(query.getQueryParameter("bcc"));
    }

    @Test
    public void shareBridgeOpensChooserWithPlainText() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        new AndroidBridge(null, activity).shareText("WebForge 分享");

        Intent chooser = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        Intent share = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND, share.getAction());
        assertEquals("text/plain", share.getType());
        assertEquals("WebForge 分享", share.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    @Config(sdk = 29)
    public void mediaPermissionRequiresOnlyReadStorageOnAndroid10() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        AndroidBridge bridge = new AndroidBridge(null, activity);

        assertFalse(bridge.hasPermission("media"));
        shadowOf(activity.getApplication()).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        assertTrue(bridge.hasPermission("media"));
    }

    @Test
    public void backUsesWebHistoryAndFallsThroughAfterManualDestroy() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        int[] hostBacks = {0};
        activity.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                hostBacks[0]++;
            }
        });
        RecordingWebView view = new RecordingWebView(activity) {
            private boolean hasHistory = true;

            @Override public boolean canGoBack() { return hasHistory; }
            @Override public void goBack() { hasHistory = false; }
        };
        FrameLayout parent = new FrameLayout(activity);
        WebPage page = WebPage.builder(activity, parent).webView(view).build();

        activity.getOnBackPressedDispatcher().onBackPressed();
        assertFalse(view.canGoBack());
        assertEquals(0, hostBacks[0]);
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertEquals(1, hostBacks[0]);
        page.close();
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertEquals(2, hostBacks[0]);
        assertEquals(0, parent.getChildCount());
    }

    @Test
    public void failedInitialLoadDestroysThePage() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        RecordingWebView view = new RecordingWebView(activity) {
            @Override public void loadUrl(String url) {
                throw new IllegalStateException("test load failure");
            }
        };
        WebPage.Builder builder = WebPage.builder(activity, parent).webView(view);

        assertThrows(IllegalArgumentException.class, () -> builder.open("   "));
        assertEquals(0, parent.getChildCount());
        assertEquals(0, view.destroys);
        assertThrows(IllegalStateException.class, () -> builder.open(URL));
        assertEquals(0, parent.getChildCount());
        assertEquals(1, view.destroys);
    }

    @Test
    public void rendererExitCleansOnlyAffectedViewAndNotifiesAfterDetach() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        RecordingWebView view = new RecordingWebView(activity);
        int[] notifications = {0};
        WebPage page = WebPage.builder(activity, parent).webView(view)
                .navigationCallbacks(new NavigationCallbacks())
                .onRendererGone(() -> {
                    assertEquals(1, view.destroys);
                    assertNull(view.getParent());
                    assertEquals(1, parent.getChildCount());
                    notifications[0]++;
                }).open(URL);
        WebPage other = WebPage.open(activity, parent, URL);
        WebViewClient client = shadowOf(view).getWebViewClient();

        assertTrue(client.onRenderProcessGone(view, null));
        assertTrue(client.onRenderProcessGone(view, null));
        assertEquals(1, notifications[0]);
        assertTrue(page.isClosed());
        assertNull(page.getView());
        assertEquals(0, view.stops);
        assertFalse(other.isClosed());
        other.close();
    }

    @Test
    public void failedSetupDoesNotDisconnectCallbacksOwnedByAnotherPage() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        NavigationCallbacks callback = new NavigationCallbacks();
        RecordingWebView first = new RecordingWebView(activity);
        RecordingWebView duplicate = new RecordingWebView(activity);
        WebPage original = WebPage.builder(activity, parent).webView(first)
                .blockUnknownSchemes(true).navigationCallbacks(callback).build();

        assertThrows(IllegalStateException.class, () -> WebPage.builder(activity, parent)
                .webView(duplicate).navigationCallbacks(callback).build());
        assertEquals(1, duplicate.destroys);
        assertEquals(1, parent.getChildCount());
        assertTrue(shadowOf(first).getWebViewClient()
                .shouldOverrideUrlLoading(first, "example-custom://page"));
        assertFalse(shadowOf(first).getWebViewClient().shouldOverrideUrlLoading(first, URL));
        original.close();
    }

    @Test
    public void chromeCallbacksForwardProgressToCustomIndicatorInRegistrationOrder() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        StringBuilder events = new StringBuilder();
        BaseIndicatorView indicator = new BaseIndicatorView(activity) {
            @Override
            public void setProgress(int progress) {
                events.append(progress);
            }

            @Override
            public LayoutParams offerLayoutParams() {
                return new LayoutParams(100, 3);
            }
        };
        ChromeCallbacks outer = new ChromeCallbacks() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                events.append("outer ");
                super.onProgressChanged(view, progress);
            }
        };
        ChromeCallbacks inner = new ChromeCallbacks() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                events.append("inner ");
                super.onProgressChanged(view, progress);
            }
        };
        WebPage page = WebPage.builder(activity, new FrameLayout(activity))
                .progress(indicator).chromeCallbacks(outer).chromeCallbacks(inner).build();
        WebView view = page.getView();

        shadowOf(view).getWebChromeClient().onProgressChanged(view, 35);

        assertEquals("outer inner 35", events.toString());
        page.close();
    }

    @Test
    public void defaultErrorPageStillRetriesAndHidesAfterSuccess() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        RecordingWebView view = new RecordingWebView(activity);
        WebPage page = WebPage.builder(activity, parent).webView(view).open(URL);
        WebViewClient client = shadowOf(view).getWebViewClient();

        client.onPageStarted(view, URL, null);
        client.onReceivedError(view, WebViewClient.ERROR_HOST_LOOKUP, "lookup failed", URL);
        client.onPageFinished(view, URL);
        View error = parent.findViewById(R.id.mainframe_error_container_id);
        assertEquals(View.VISIBLE, error.getVisibility());

        assertTrue(error.performClick());
        assertEquals(1, view.reloads);
        client.onPageStarted(view, URL, null);
        client.onPageFinished(view, URL);

        assertEquals(View.GONE, error.getVisibility());
        page.close();
    }

    @Test
    public void fragmentViewCanBeRecreatedWithASeparatePage() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        PageFragment fragment = new PageFragment();
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "page").commitNow();
        RecordingWebView first = fragment.webView;
        FrameLayout firstParent = (FrameLayout) fragment.requireView();
        WebPage.Builder stale = WebPage.builder(fragment, firstParent);

        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
        assertEquals(1, first.destroys);
        assertEquals(0, firstParent.getChildCount());
        assertThrows(IllegalStateException.class, stale::build);

        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();
        assertNotSame(first, fragment.webView);
        assertEquals(URL, fragment.webView.urls.get(0));
        assertEquals(0, fragment.webView.destroys);
    }

    public static class PageFragment extends Fragment {
        RecordingWebView webView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
            return new FrameLayout(requireContext());
        }

        @Override
        public void onViewCreated(View view, Bundle state) {
            webView = new RecordingWebView(requireContext());
            WebPage.builder(this, (FrameLayout) view).webView(webView).open(URL);
        }
    }
}
