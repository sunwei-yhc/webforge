package android.sanshisan.web;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sunwei.webforge.host.BaseWebForgeActivity;

import java.lang.reflect.Method;

public class LoadwebActivity extends BaseWebForgeActivity {


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loadmain);
        getWebForge().getWebForgeSettings().getWebSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    }

    @NonNull
    @Override
    protected ViewGroup getWebForgeParent() {
        return findViewById(R.id.mFrameLayout);
    }
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d("VideoImpl", "onConfigurationChanged: " + newConfig.orientation );
    }


    @Nullable
    @Override
    protected String getUrl() {
        return "file:///android_asset/webview_h5_test.html";
    }

}
