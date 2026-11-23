//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
package io.github.sunwei.webforge.ui.indicator

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.IntRange
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

open class DrawableWrapper(open val wrappedDrawable: Drawable) : Drawable() {
    override fun draw(canvas: Canvas) {
        this.wrappedDrawable.draw(canvas)
    }

    override fun getChangingConfigurations(): Int {
        return this.wrappedDrawable.getChangingConfigurations()
    }

    override fun getConstantState(): ConstantState? {
        return this.wrappedDrawable.getConstantState()
    }

    override fun getCurrent(): Drawable {
        return this.wrappedDrawable.getCurrent()
    }

    override fun getIntrinsicHeight(): Int {
        return this.wrappedDrawable.getIntrinsicHeight()
    }

    override fun getIntrinsicWidth(): Int {
        return this.wrappedDrawable.getIntrinsicWidth()
    }

    override fun getMinimumHeight(): Int {
        return this.wrappedDrawable.getMinimumHeight()
    }

    override fun getMinimumWidth(): Int {
        return this.wrappedDrawable.getMinimumWidth()
    }

    override fun getOpacity(): Int {
        return this.wrappedDrawable.getOpacity()
    }

    override fun getPadding(padding: Rect): Boolean {
        return this.wrappedDrawable.getPadding(padding)
    }

    override fun getState(): IntArray {
        return this.wrappedDrawable.getState()
    }

    override fun getTransparentRegion(): Region? {
        return this.wrappedDrawable.getTransparentRegion()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet) {
        this.wrappedDrawable.inflate(r, parser, attrs)
    }

    override fun isStateful(): Boolean {
        return this.wrappedDrawable.isStateful()
    }

    override fun jumpToCurrentState() {
        this.wrappedDrawable.jumpToCurrentState()
    }

    override fun mutate(): Drawable {
        return this.wrappedDrawable.mutate()
    }

    override fun setAlpha(@IntRange(from = 0L, to = 255L) i: Int) {
        this.wrappedDrawable.setAlpha(i)
    }

    override fun scheduleSelf(what: Runnable, `when`: Long) {
        this.wrappedDrawable.scheduleSelf(what, `when`)
    }

    override fun setChangingConfigurations(configs: Int) {
        this.wrappedDrawable.setChangingConfigurations(configs)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.wrappedDrawable.setColorFilter(colorFilter)
    }

    override fun setColorFilter(color: Int, mode: PorterDuff.Mode) {
        this.wrappedDrawable.setColorFilter(color, mode)
    }

    override fun setFilterBitmap(filter: Boolean) {
        this.wrappedDrawable.setFilterBitmap(filter)
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return this.wrappedDrawable.setVisible(visible, restart)
    }

    override fun unscheduleSelf(what: Runnable) {
        this.wrappedDrawable.unscheduleSelf(what)
    }

    override fun onBoundsChange(bounds: Rect) {
        this.wrappedDrawable.setBounds(bounds)
    }

    override fun onLevelChange(level: Int): Boolean {
        return this.wrappedDrawable.setLevel(level)
    }

    override fun onStateChange(state: IntArray): Boolean {
        return this.wrappedDrawable.setState(state)
    }
}
