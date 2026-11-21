package android.sanshisan.web;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sunwei.webforge.WebPage;
import io.github.sunwei.webforge.host.BrowserFragment;
import io.github.sunwei.webforge.host.ToolbarWebFragment;

/** 使用系统 WebView 的基础页面示例。 */
public class FragmentLoadweb extends BrowserFragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_web, container, false);
    }

    @NonNull
    @Override
    public ViewGroup pageContainer(@NonNull View view) {
        return view.findViewById(R.id.container);
    }

    @Override
    protected void configurePage(@NonNull WebPage.Builder builder) {
        builder.enableBuiltInBridges();
    }

    @NonNull
    @Override
    public String initialUrl() {
        Bundle arguments = getArguments();
        String url = arguments == null ? null : arguments.getString(ToolbarWebFragment.URL_KEY);
        return url == null ? "file:///android_asset/webview_h5_test.html" : url;
    }
}
