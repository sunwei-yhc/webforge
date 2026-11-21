//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.github.sunwei.webforge.ui.indicator;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Path.Direction;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ShiftDrawable extends DrawableWrapper {
    private final ValueAnimator mAnimator;
    private final Rect mVisibleRect;
    private Path mPath;
    private static final int MAX_LEVEL = 10000;
    private static final int DEFAULT_DURATION = 1000;

    public ShiftDrawable(@NonNull Drawable d) {
        this(d, 1000);
    }

    public ShiftDrawable(@NonNull Drawable d, int duration) {
        this(d, duration, new LinearInterpolator());
    }

    public ShiftDrawable(@NonNull Drawable d, int duration, @Nullable Interpolator interpolator) {
        super(d);
        this.mAnimator = ValueAnimator.ofFloat(new float[]{0.0F, 1.0F});
        this.mVisibleRect = new Rect();
        this.mAnimator.setDuration((long)duration);
        this.mAnimator.setRepeatCount(-1);
        this.mAnimator.setInterpolator((TimeInterpolator)(interpolator == null ? new LinearInterpolator() : interpolator));
        this.mAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (ShiftDrawable.this.isVisible()) {
                    ShiftDrawable.this.invalidateSelf();
                }

            }
        });
        this.mAnimator.start();
    }

    public Animator getAnimator() {
        return this.mAnimator;
    }

    public boolean setVisible(boolean visible, boolean restart) {
        boolean result = super.setVisible(visible, restart);
        if (this.isVisible()) {
            this.mAnimator.start();
        } else {
            this.mAnimator.end();
        }

        return result;
    }

    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        this.updateBounds();
    }

    protected boolean onLevelChange(int level) {
        boolean result = super.onLevelChange(level);
        this.updateBounds();
        return result;
    }

    public void draw(Canvas canvas) {
        Drawable d = this.getWrappedDrawable();
        float fraction = this.mAnimator.getAnimatedFraction();
        int width = this.mVisibleRect.width();
        int offset = (int)((float)width * fraction);
        int stack = canvas.save();
        canvas.clipPath(this.mPath);
        canvas.save();
        canvas.translate((float)(-offset), 0.0F);
        d.draw(canvas);
        canvas.restore();
        canvas.save();
        canvas.translate((float)(width - offset), 0.0F);
        d.draw(canvas);
        canvas.restore();
        canvas.restoreToCount(stack);
    }

    private void updateBounds() {
        Rect b = this.getBounds();
        int width = (int)((float)b.width() * (float)this.getLevel() / 10000.0F);
        float radius = (float)b.height() / 2.0F;
        this.mVisibleRect.set(b.left, b.top, b.left + width, b.height());
        this.mPath = new Path();
        this.mPath.addRect((float)b.left, (float)b.top, (float)(b.left + width) - radius, (float)b.height(), Direction.CCW);
        this.mPath.addCircle((float)(b.left + width) - radius, radius, radius, Direction.CCW);
    }
}
