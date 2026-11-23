package android.sanshisan.web;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.sunwei.webforge.WebPage;

/** 在普通 Activity 中打开应用自带的 H5 能力测试页。 */
public class LoadwebActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loadmain);

        WebPage mWebPage=   WebPage.builder(this, findViewById(R.id.mFrameLayout))
                .enableBuiltInBridges()
                .open("file:///android_asset/webview_h5_test.html");

    }
}
