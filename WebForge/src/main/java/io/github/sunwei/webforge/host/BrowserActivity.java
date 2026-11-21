package io.github.sunwei.webforge.host;

import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.sunwei.webforge.WebPage;

/** 需要在同一 Activity 内替换页面时可使用此基类。 */
public abstract class BrowserActivity extends AppCompatActivity {
    private WebPage page;

    @MainThread
    protected final WebPage showPage(@NonNull ViewGroup container, @NonNull String url) {
        if (page != null) {
            page.close();
            page = null;
        }
        WebPage.Builder builder = WebPage.builder(this, container);
        configurePage(builder);
        page = builder.open(url);
        return page;
    }

    protected void configurePage(@NonNull WebPage.Builder builder) {}

    @Nullable
    protected final WebPage getPage() {
        return page;
    }

    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
        } finally {
            page = null;
        }
    }
}
