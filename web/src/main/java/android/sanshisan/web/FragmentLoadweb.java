package android.sanshisan.web;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sunwei.webforge.host.BaseWebForgeFragment;
import io.github.sunwei.webforge.host.WebForgeFragment;

/** 使用系统 WebView 的基础页面示例。 */
public class FragmentLoadweb extends BaseWebForgeFragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_web, container, false);
    }

    @NonNull
    @Override
    protected ViewGroup getWebForgeParent() {
        return requireView().findViewById(R.id.container);
    }

    @Override
    public String getUrl() {
        Bundle arguments = getArguments();
        String url = arguments == null ? null : arguments.getString(WebForgeFragment.URL_KEY);
        return url == null ? "file:///android_asset/webview_h5_test.html" : url;
    }
}
