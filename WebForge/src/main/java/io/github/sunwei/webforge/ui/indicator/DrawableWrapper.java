//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.github.sunwei.webforge.ui.indicator;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class DrawableWrapper extends Drawable {
    private final Drawable mWrapped;

    public DrawableWrapper(@NonNull Drawable drawable) {
        this.mWrapped = drawable;
    }

    public Drawable getWrappedDrawable() {
        return this.mWrapped;
    }

    public void draw(@NonNull Canvas canvas) {
        this.mWrapped.draw(canvas);
    }

    public int getChangingConfigurations() {
        return this.mWrapped.getChangingConfigurations();
    }

    public ConstantState getConstantState() {
        return this.mWrapped.getConstantState();
    }

    public Drawable getCurrent() {
        return this.mWrapped.getCurrent();
    }

    public int getIntrinsicHeight() {
        return this.mWrapped.getIntrinsicHeight();
    }

    public int getIntrinsicWidth() {
        return this.mWrapped.getIntrinsicWidth();
    }

    public int getMinimumHeight() {
        return this.mWrapped.getMinimumHeight();
    }

    public int getMinimumWidth() {
        return this.mWrapped.getMinimumWidth();
    }

    public int getOpacity() {
        return this.mWrapped.getOpacity();
    }

    public boolean getPadding(Rect padding) {
        return this.mWrapped.getPadding(padding);
    }

    public int[] getState() {
        return this.mWrapped.getState();
    }

    public Region getTransparentRegion() {
        return this.mWrapped.getTransparentRegion();
    }

    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {
        this.mWrapped.inflate(r, parser, attrs);
    }

    public boolean isStateful() {
        return this.mWrapped.isStateful();
    }

    public void jumpToCurrentState() {
        this.mWrapped.jumpToCurrentState();
    }

    public Drawable mutate() {
        return this.mWrapped.mutate();
    }

    public void setAlpha(@IntRange(from = 0L,to = 255L) int i) {
        this.mWrapped.setAlpha(i);
    }

    public void scheduleSelf(Runnable what, long when) {
        this.mWrapped.scheduleSelf(what, when);
    }

    public void setChangingConfigurations(int configs) {
        this.mWrapped.setChangingConfigurations(configs);
    }

    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.mWrapped.setColorFilter(colorFilter);
    }

    public void setColorFilter(int color, PorterDuff.Mode mode) {
        this.mWrapped.setColorFilter(color, mode);
    }

    public void setFilterBitmap(boolean filter) {
        this.mWrapped.setFilterBitmap(filter);
    }

    public boolean setVisible(boolean visible, boolean restart) {
        return this.mWrapped.setVisible(visible, restart);
    }

    public void unscheduleSelf(Runnable what) {
        this.mWrapped.unscheduleSelf(what);
    }

    protected void onBoundsChange(Rect bounds) {
        this.mWrapped.setBounds(bounds);
    }

    protected boolean onLevelChange(int level) {
        return this.mWrapped.setLevel(level);
    }

    protected boolean onStateChange(int[] state) {
        return this.mWrapped.setState(state);
    }
}
