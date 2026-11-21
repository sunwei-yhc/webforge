package io.github.sunwei.webforge.ui.indicator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import io.github.sunwei.webforge.util.WebForgeUtils;

/** 仅在 WebView 报告新进度时更新绘制。 */
public final class LoadingProgressBar extends BaseIndicatorView {
    private final Paint paint = new Paint();
    private final int height;
    private int progress;

    public LoadingProgressBar(Context context) {
        this(context, null);
    }

    public LoadingProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        height = WebForgeUtils.dp2px(context, 3);
        paint.setColor(Color.rgb(26, 173, 25));
    }

    public void setColor(@ColorInt int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth() * (progress / 100f), getHeight(), paint);
    }

    @Override
    public void show() {
        if (getVisibility() != VISIBLE || progress == 100) {
            animate().cancel();
            setAlpha(1f);
            setVisibility(VISIBLE);
        }
    }

    @Override
    public void setProgress(int progress) {
        if (this.progress != progress) {
            this.progress = progress;
            invalidate();
        }
    }

    @Override
    public void hide() {
        if (getVisibility() == VISIBLE) {
            animate().alpha(0f).setDuration(150).withEndAction(() -> {
                setVisibility(GONE);
                setAlpha(1f);
            }).start();
        }
    }

    @Override
    public void reset() {
        animate().cancel();
        setAlpha(1f);
        setProgress(0);
    }

    @Override
    protected void onDetachedFromWindow() {
        animate().cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public LayoutParams offerLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }
}
