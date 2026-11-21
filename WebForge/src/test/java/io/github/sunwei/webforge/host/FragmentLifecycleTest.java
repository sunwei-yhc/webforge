package io.github.sunwei.webforge.host;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.sunwei.webforge.core.WebViewLifecycleTest.RecordingWebView;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FragmentLifecycleTest {
    @Test
    public void detachedFragmentReleasesWebViewAndRecreatesItOnAttach() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        Page fragment = new Page();
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "page").commitNow();
        RecordingWebView first = fragment.webView;
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
        assertEquals(1, first.getDestroyCount());
        assertNull(fragment.mWebForge);
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();
        assertNotSame(first, fragment.webView);
        assertNotNull(fragment.mWebForge);
    }

    @Test
    public void toolbarFragmentWithoutArgumentsLoadsBlankPage() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        WebForgeFragment fragment = new WebForgeFragment();
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "toolbar").commitNow();
        assertEquals("about:blank", fragment.getUrl());
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();
        assertNotNull(fragment.getView());
    }

    public static class Page extends BaseWebForgeFragment {
        RecordingWebView webView;
        @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                @Nullable ViewGroup container, @Nullable Bundle state) {
            return new FrameLayout(requireContext());
        }
        @NonNull @Override protected ViewGroup getWebForgeParent() { return (ViewGroup) requireView(); }
        @Override protected WebView getWebView() {
            webView = new RecordingWebView(requireContext());
            return webView;
        }
        @Override protected Object getAndroidInterface() { return null; }
    }
}
