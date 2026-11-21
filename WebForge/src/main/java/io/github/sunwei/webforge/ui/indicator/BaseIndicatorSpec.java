
package io.github.sunwei.webforge.ui.indicator;



/**
 */
public interface BaseIndicatorSpec {

    void show();

    void hide();

    void reset();

    void setProgress(int newProgress);

}
