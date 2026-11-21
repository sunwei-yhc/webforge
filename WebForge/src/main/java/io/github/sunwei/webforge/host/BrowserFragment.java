package io.github.sunwei.webforge.host;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.sunwei.webforge.WebPage;

/** 每次创建 Fragment 视图时打开一个新的网页。 */
public abstract class BrowserFragment extends Fragment {
    private WebPage page;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        WebPage.Builder builder = WebPage.builder(this, pageContainer(view));
        configurePage(builder);
        page = builder.open(initialUrl());
    }

    @NonNull
    protected abstract ViewGroup pageContainer(@NonNull View view);

    @NonNull
    protected String initialUrl() {
        return "about:blank";
    }

    protected void configurePage(@NonNull WebPage.Builder builder) {}

    @Nullable
    protected final WebPage getPage() {
        return page;
    }

    @Override
    public void onDestroyView() {
        page = null;
        super.onDestroyView();
    }
}
