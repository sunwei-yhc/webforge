
package io.github.sunwei.webforge.ui.indicator;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/** 自定义页面进度条的扩展入口。 */
public abstract class BaseIndicatorView extends FrameLayout {
    public BaseIndicatorView(Context context) {
        super(context);
    }

    public BaseIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BaseIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void reset() {
    }

    public void setProgress(int newProgress) {
    }

    public void show() {
    }

    public void hide() {
    }
    public abstract LayoutParams offerLayoutParams();
}
