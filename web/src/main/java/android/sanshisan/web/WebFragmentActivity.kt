package android.sanshisan.web

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.sunwei.webforge.host.WebForgeFragment

class WebFragmentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_fragment)
        addFragment(R.id.llContent, FragmentLoadweb().apply {
            this.arguments=Bundle().apply {
                this.putString(WebForgeFragment.URL_KEY,"file:///android_asset/webview_h5_test.html")
            }
        },"ceshi")
    }
}