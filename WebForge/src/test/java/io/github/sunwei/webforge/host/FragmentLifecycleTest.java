package io.github.sunwei.webforge.host;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.sunwei.webforge.WebPage;
import io.github.sunwei.webforge.WebPageOperationsTest.RecordingWebView;

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
        assertNull(fragment.getPage());
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();
        assertNotSame(first, fragment.webView);
        assertNotNull(fragment.getPage());
    }

    @Test
    public void toolbarFragmentWithoutArgumentsLoadsBlankPage() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        ToolbarWebFragment fragment = new ToolbarWebFragment();
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "toolbar").commitNow();
        assertEquals("about:blank", fragment.initialUrl());
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();
        assertNotNull(fragment.getView());
    }

    public static class Page extends BrowserFragment {
        RecordingWebView webView;
        @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                @Nullable ViewGroup container, @Nullable Bundle state) {
            return new FrameLayout(requireContext());
        }
        @NonNull @Override protected ViewGroup pageContainer(@NonNull View view) { return (ViewGroup) view; }
        @Override protected void configurePage(@NonNull WebPage.Builder builder) {
            webView = new RecordingWebView(requireContext());
            builder.webView(webView);
        }
    }
}
