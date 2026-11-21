
package io.github.sunwei.webforge.ui;

import android.view.View;
import android.webkit.WebChromeClient;

/**
 */
public interface IVideo {


    void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback);


    void onHideCustomView();

    void setLandscapeScreen(boolean landscapeScreen);


    boolean isVideoState();

}
