package android.sanshisan.web

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.sunwei.webforge.WebPage

/** 在普通 Activity 中打开应用自带的 H5 能力测试页。 */
class LoadwebActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loadmain)

        WebPage.builder(this, findViewById(R.id.mFrameLayout))
            .enableBuiltInBridges()
            .open("file:///android_asset/webview_h5_test.html")
    }
}
