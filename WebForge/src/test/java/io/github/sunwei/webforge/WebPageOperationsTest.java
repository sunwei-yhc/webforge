package io.github.sunwei.webforge;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import io.github.sunwei.webforge.bridge.FileUploadBridge;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 覆盖真实主线程队列与页面销毁之间的交互。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class WebPageOperationsTest {
    private ComponentActivity activity;
    private FrameLayout parent;
    private RecordingWebView webView;
    private WebPage page;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        parent = new FrameLayout(activity);
        activity.setContentView(parent);
        webView = new RecordingWebView(activity);
        page = WebPage.builder(activity, parent).webView(webView).build();
    }

    @Test
    public void mainThreadCallsRunImmediately() {
        page.load("https://example.com");
        page.evaluateJavaScript("window.ready()");
        assertEquals("https://example.com", webView.urls.get(0));
        assertEquals("window.ready()", webView.scripts.get(0));
    }

    @Test
    public void backgroundCallsRunOnMainThread() throws Exception {
        onWorker(() -> {
            page.load("https://example.com");
            page.evaluateJavaScript("window.ready()");
            page.reload();
            page.loadHtml("data", "https://example.com");
            page.stop();
            page.post("https://example.com", new byte[]{1});
        });
        assertTrue(webView.urls.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, webView.urls.size());
        assertEquals(1, webView.scripts.size());
        assertEquals(1, webView.reloads);
        assertEquals(1, webView.baseDataLoads);
        assertEquals(1, webView.stops);
        assertEquals(1, webView.posts);
        assertFalse(webView.wrongThread);
    }

    @Test
    public void destroyCancelsQueuedCallsAndRejectsNewCalls() throws Exception {
        onWorker(() -> {
            page.load("https://example.com");
            page.evaluateJavaScript("window.ready()");
        });
        page.close();
        page.load("https://example.org");
        page.evaluateJavaScript("window.late()");
        page.reload();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(webView.urls.isEmpty());
        assertTrue(webView.scripts.isEmpty());
        assertEquals(0, webView.reloads);
        assertEquals(1, webView.destroys);
    }

    @Test
    public void backgroundDestroyIsIdempotentAndDetachesView() throws Exception {
        onWorker(page::close);
        assertTrue(page.isClosed());
        assertEquals(0, webView.destroys);
        page.load("https://late.example");
        page.close();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, parent.getChildCount());
        assertNull(webView.getParent());
        assertEquals(1, webView.destroys);
        assertFalse(webView.wrongThread);
        assertTrue(webView.urls.isEmpty());
    }

    @Test
    public void destroyDoesNotCancelOtherPagesOrMainThreadTasks() throws Exception {
        RecordingWebView other = new RecordingWebView(activity);
        WebPage otherPage = WebPage.builder(activity, new FrameLayout(activity)).webView(other).build();
        AtomicBoolean unrelated = new AtomicBoolean();
        onWorker(() -> {
            new Handler(Looper.getMainLooper()).post(() -> unrelated.set(true));
            otherPage.load("https://other.example");
        });
        page.close();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(unrelated.get());
        assertEquals(1, other.urls.size());
        otherPage.close();
    }

    @Test
    public void lifecycleNeverChangesGlobalWebViewTimers() {
        int previousResumes = webView.resumes;
        page.pause();
        page.resume();
        page.close();
        page.pause();
        page.resume();
        page.close();
        assertEquals(1, webView.pauses);
        assertEquals(previousResumes + 1, webView.resumes);
        assertEquals(0, webView.timerChanges);
        assertEquals(1, webView.destroys);
    }

    @Test
    public void queuedPostDataAndHeadersUseSnapshots() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "before");
        byte[] body = {1, 2};
        onWorker(() -> {
            page.load("https://example.com", headers);
            page.post("https://example.com", body);
            headers.put("X-Test", "after");
            body[0] = 9;
        });
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("before", webView.headers.get("X-Test"));
        assertArrayEquals(new byte[]{1, 2}, webView.postData);
    }

    @Test
    public void configuredHeadersApplyOnlyToMatchingOriginAndUseSnapshot() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "before");
        RecordingWebView configuredView = new RecordingWebView(activity);
        WebPage configured = WebPage.builder(activity, parent).webView(configuredView)
                .headers("https://EXAMPLE.com:443/a", headers).build();
        headers.put("X-Test", "after");
        configured.load("https://example.com:8443/b");
        assertNull(configuredView.headers);
        configured.load("https://example.com/b");
        assertEquals("before", configuredView.headers.get("X-Test"));
        configured.close();
    }

    @Test
    public void callEscapesStringsAndKeepsExplicitJsonTypes() throws Exception {
        String text = "引号\"、单引号'、换行\n、反斜杠\\";
        page.callJavaScript("bridge.done", text, new JSONObject().put("ok", true),
                new JSONArray("[1,2]"), null, "[1,2]");
        String script = webView.scripts.get(0);
        JSONArray args = new JSONArray("[" + script.substring(script.indexOf('(') + 1, script.length() - 1) + "]");
        assertEquals(text, args.getString(0));
        assertTrue(args.getJSONObject(1).getBoolean("ok"));
        assertEquals(2, args.getJSONArray(2).length());
        assertTrue(args.isNull(3));
        assertEquals("[1,2]", args.getString(4));
    }

    @Test
    public void javascriptResultAfterDestroyIsIgnored() {
        AtomicBoolean called = new AtomicBoolean();
        page.evaluateJavaScript("window.ready()", value -> called.set(true));
        ValueCallback<String> pending = webView.jsResult;
        page.close();
        pending.onReceiveValue("late result");
        assertFalse(called.get());
    }

    @Test
    public void fullscreenVideoRespectsOrientationSettingAndBackClosesIt() {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        RecordingWebView videoView = new RecordingWebView(activity);
        WebPage videoPage = WebPage.builder(activity, parent).webView(videoView)
                .progress(false).landscapeVideo(false).build();
        AtomicBoolean hidden = new AtomicBoolean();
        shadowOf(videoView).getWebChromeClient()
                .onShowCustomView(new View(activity), () -> hidden.set(true));

        assertEquals(View.GONE, videoView.getVisibility());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
        assertTrue(videoPage.goBack());
        assertTrue(hidden.get());
        assertEquals(View.VISIBLE, videoView.getVisibility());
        videoPage.close();
    }

    @Test
    public void readyRegistersBuiltInAndHostJavascriptInterfaces() {
        Object bridge = new Object() {
            @JavascriptInterface
            public void notifyReady() {
            }
        };
        RecordingWebView bridgeView = new RecordingWebView(activity);
        WebPage bridged = WebPage.builder(activity, parent).webView(bridgeView)
                .enableBuiltInBridges().bridge("HostBridge", bridge).build();
        assertSame(bridge, shadowOf(bridgeView).getJavascriptInterface("HostBridge"));
        assertTrue(shadowOf(bridgeView).getJavascriptInterface("webForge") instanceof FileUploadBridge);
        assertThrows(IllegalArgumentException.class,
                () -> bridged.addJavaScriptBridge("Invalid", new Object()));
        bridged.close();
    }

    @Test
    public void fileChooserCancelsCleanlyWhenActivityIsFinishing() {
        activity.finish();
        List<android.net.Uri[]> results = new ArrayList<>();
        boolean handled = WebForgeUtils.showFileChooserCompat(activity, webView,
                results::add, null, null, null, null, null);
        assertTrue(handled);
        assertEquals(1, results.size());
        assertNull(results.get(0));
    }

    private static void onWorker(Runnable task) throws Exception {
        Thread worker = new Thread(task);
        worker.start();
        worker.join();
    }

    public static class RecordingWebView extends WebView {
        final List<String> urls = new ArrayList<>();
        final List<String> scripts = new ArrayList<>();
        Map<String, String> headers;
        byte[] postData;
        ValueCallback<String> jsResult;
        int reloads, baseDataLoads, stops, posts, destroys, pauses, resumes, timerChanges;
        boolean wrongThread;

        public RecordingWebView(Context context) { super(context); }
        public int getDestroyCount() { return destroys; }
        private void checkMain() { wrongThread |= Looper.myLooper() != Looper.getMainLooper(); }
        @Override public void loadUrl(String url) { checkMain(); urls.add(url); }
        @Override public void loadUrl(String url, Map<String, String> headers) {
            loadUrl(url); this.headers = headers;
        }
        @Override public void evaluateJavascript(String script, ValueCallback<String> callback) {
            checkMain(); scripts.add(script); jsResult = callback;
        }
        @Override public void reload() { checkMain(); reloads++; }
        @Override public void loadDataWithBaseURL(String base, String data, String mime, String encoding, String history) {
            checkMain(); baseDataLoads++;
        }
        @Override public void stopLoading() { checkMain(); stops++; }
        @Override public void postUrl(String url, byte[] data) { checkMain(); posts++; postData = data; }
        @Override public void destroy() { checkMain(); destroys++; super.destroy(); }
        @Override public void onPause() { checkMain(); pauses++; }
        @Override public void onResume() { checkMain(); resumes++; }
        @Override public void pauseTimers() { timerChanges++; }
        @Override public void resumeTimers() { timerChanges++; }
    }
}
