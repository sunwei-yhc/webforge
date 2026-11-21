package io.github.sunwei.webforge.core;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import io.github.sunwei.webforge.WebForge;
import io.github.sunwei.webforge.bridge.FileUploadJsBridge;
import io.github.sunwei.webforge.bridge.JsAccessEntrace;
import io.github.sunwei.webforge.bridge.WebViewJsExecutor;
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
public class WebViewLifecycleTest {
    private Activity activity;
    private RecordingWebView webView;
    private WebViewTaskDispatcher dispatcher;
    private WebViewUrlLoader loader;
    private JsAccessEntrace js;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        webView = new RecordingWebView(activity);
        dispatcher = new WebViewTaskDispatcher(webView);
        loader = new WebViewUrlLoader(dispatcher, null);
        js = new WebViewJsExecutor(dispatcher);
    }

    @Test
    public void mainThreadCallsRunImmediately() {
        loader.loadUrl("https://example.com");
        js.callJs("window.ready()");
        assertEquals("https://example.com", webView.urls.get(0));
        assertEquals("window.ready()", webView.scripts.get(0));
    }

    @Test
    public void backgroundCallsRunOnMainThread() throws Exception {
        onWorker(() -> {
            loader.loadUrl("https://example.com");
            js.callJs("window.ready()");
            loader.reload();
            loader.loadData("data", "text/html", "UTF-8");
            loader.loadDataWithBaseURL("https://example.com", "data", "text/html", "UTF-8", null);
            loader.stopLoading();
            loader.postUrl("https://example.com", new byte[]{1});
        });
        assertTrue(webView.urls.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, webView.urls.size());
        assertEquals(1, webView.scripts.size());
        assertEquals(1, webView.reloads);
        assertEquals(1, webView.dataLoads);
        assertEquals(1, webView.baseDataLoads);
        assertEquals(1, webView.stops);
        assertEquals(1, webView.posts);
        assertFalse(webView.wrongThread);
    }

    @Test
    public void destroyCancelsQueuedCallsAndRejectsNewCalls() throws Exception {
        onWorker(() -> {
            loader.loadUrl("https://example.com");
            js.callJs("window.ready()");
        });
        dispatcher.destroy(null);
        loader.loadUrl("https://example.org");
        js.callJs("window.late()");
        loader.reload();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(webView.urls.isEmpty());
        assertTrue(webView.scripts.isEmpty());
        assertEquals(0, webView.reloads);
        assertEquals(1, webView.destroys);
    }

    @Test
    public void backgroundDestroyIsIdempotentAndDetachesView() throws Exception {
        FrameLayout parent = new FrameLayout(activity);
        parent.addView(webView);
        AtomicBoolean cleanup = new AtomicBoolean();
        onWorker(() -> dispatcher.destroy(() -> cleanup.set(true)));
        loader.loadUrl("https://late.example");
        dispatcher.destroy(null);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(cleanup.get());
        assertNull(webView.getParent());
        assertEquals(1, webView.destroys);
        assertFalse(webView.wrongThread);
        assertTrue(webView.urls.isEmpty());
    }

    @Test
    public void destroyDoesNotCancelOtherPagesOrMainThreadTasks() throws Exception {
        RecordingWebView other = new RecordingWebView(activity);
        WebViewTaskDispatcher otherDispatcher = new WebViewTaskDispatcher(other);
        AtomicBoolean unrelated = new AtomicBoolean();
        onWorker(() -> {
            new Handler(Looper.getMainLooper()).post(() -> unrelated.set(true));
            otherDispatcher.dispatch(view -> view.loadUrl("https://other.example"));
        });
        dispatcher.destroy(null);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(unrelated.get());
        assertEquals(1, other.urls.size());
    }

    @Test
    public void lifecycleNeverChangesGlobalWebViewTimers() {
        WebLifeCycle lifecycle = new WebViewLifecycle(dispatcher, null);
        lifecycle.onPause();
        lifecycle.onResume();
        lifecycle.onDestroy();
        lifecycle.onPause();
        lifecycle.onResume();
        lifecycle.onDestroy();
        assertEquals(1, webView.pauses);
        assertEquals(1, webView.resumes);
        assertEquals(0, webView.timerChanges);
        assertEquals(1, webView.destroys);
    }

    @Test
    public void queuedPostDataAndHeadersUseSnapshots() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "before");
        byte[] body = {1, 2};
        onWorker(() -> {
            loader.loadUrl("https://example.com", headers);
            loader.postUrl("https://example.com", body);
            headers.put("X-Test", "after");
            body[0] = 9;
        });
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("before", webView.headers.get("X-Test"));
        assertArrayEquals(new byte[]{1, 2}, webView.postData);
    }

    @Test
    public void ordinaryNavigationDoesNotGrowHeaderRegistry() {
        for (int i = 0; i < 100; i++) {
            loader.loadUrl("https://host" + i + ".example/page");
        }
        assertTrue(loader.getHttpHeaders().getHeaders().isEmpty());
        loader.getHttpHeaders().additionalHttpHeader("https://example.com/a", "X-Test", "value");
        loader.loadUrl("https://example.com/b");
        assertEquals("value", webView.headers.get("X-Test"));
    }

    @Test
    public void quickCallEscapesStringsAndPreservesJsonAndNull() throws Exception {
        String text = "引号\"、单引号'、换行\n、反斜杠\\";
        js.quickCallJs("bridge.done", text, "{\"ok\":true}", "[1,2]", null);
        String script = webView.scripts.get(0);
        JSONArray args = new JSONArray("[" + script.substring(script.indexOf('(') + 1, script.length() - 1) + "]");
        assertEquals(text, args.getString(0));
        assertTrue(args.getJSONObject(1).getBoolean("ok"));
        assertEquals(2, args.getJSONArray(2).length());
        assertTrue(args.isNull(3));
    }

    @Test
    public void javascriptResultAfterDestroyIsIgnored() {
        AtomicBoolean called = new AtomicBoolean();
        js.callJs("window.ready()", value -> called.set(true));
        ValueCallback<String> pending = webView.jsResult;
        dispatcher.destroy(null);
        pending.onReceiveValue("late result");
        assertFalse(called.get());
    }

    @Test
    public void webForgeDestroyAndLifecycleDestroyShareTheSameState() {
        WebForge agent = WebForge.with(activity)
                .setWebForgeParent(new FrameLayout(activity), new FrameLayout.LayoutParams(-1, -1))
                .useDefaultIndicator().setWebView(webView).createWebForge().ready().get();
        agent.getWebLifeCycle().onDestroy();
        agent.destroy();
        agent.getUrlLoader().loadUrl("https://late.example");
        agent.getJsAccessEntrace().callJs("window.late()");
        assertEquals(1, webView.destroys);
        assertTrue(webView.urls.isEmpty());
        assertTrue(webView.scripts.isEmpty());
        assertFalse(agent.back());
    }

    @Test
    public void fullscreenVideoRespectsOrientationSettingAndBackClosesIt() {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        WebForge page = WebForge.with(activity)
                .setWebForgeParent(new FrameLayout(activity), new FrameLayout.LayoutParams(-1, -1))
                .closeIndicator().setWebView(webView).setLandscapeFullscreenVideo(false)
                .createWebForge().get();
        AtomicBoolean hidden = new AtomicBoolean();
        shadowOf(webView).getWebChromeClient()
                .onShowCustomView(new View(activity), () -> hidden.set(true));

        assertTrue(page.getIVideo().isVideoState());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
        assertTrue(page.back());
        assertTrue(hidden.get());
        assertFalse(page.getIVideo().isVideoState());
        assertEquals(View.VISIBLE, webView.getVisibility());
    }

    @Test
    public void readyRegistersBuiltInAndHostJavascriptInterfaces() {
        Object bridge = new Object() {
            @JavascriptInterface
            public void notifyReady() {
            }
        };
        WebForge page = WebForge.with(activity)
                .setWebForgeParent(new FrameLayout(activity), new FrameLayout.LayoutParams(-1, -1))
                .closeIndicator().setWebView(webView)
                .setSecurityType(WebForge.SecurityType.STRICT_CHECK)
                .addJavascriptInterface("HostBridge", bridge).createWebForge().get();

        assertSame(bridge, shadowOf(webView).getJavascriptInterface("HostBridge"));
        assertTrue(shadowOf(webView).getJavascriptInterface("webForge") instanceof FileUploadJsBridge);
        assertFalse(page.getJsInterfaceHolder().checkObject(new Object()));
    }

    @Test
    public void fileChooserCancelsCleanlyWhenActivityIsFinishing() {
        WebForge.with(activity)
                .setWebForgeParent(new FrameLayout(activity), new FrameLayout.LayoutParams(-1, -1))
                .useDefaultIndicator().setWebView(webView).createWebForge().ready();
        activity.finish();
        List<android.net.Uri[]> results = new ArrayList<>();
        boolean handled = WebForgeUtils.showFileChooserCompat(activity, webView,
                results::add, null, null, null, null, null);
        assertTrue(handled);
        assertEquals(1, results.size());
        assertNull(results.get(0));
    }

    @Test(expected = NullPointerException.class)
    public void nullWebViewFailsAtConstruction() {
        new WebViewUrlLoader((WebView) null, null);
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
        int reloads, dataLoads, baseDataLoads, stops, posts, destroys, pauses, resumes, timerChanges;
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
        @Override public void loadData(String data, String mime, String encoding) { checkMain(); dataLoads++; }
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
