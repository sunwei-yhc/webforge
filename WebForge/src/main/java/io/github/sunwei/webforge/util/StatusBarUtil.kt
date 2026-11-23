package io.github.sunwei.webforge.util

import android.R
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import java.util.regex.Pattern

open class StatusBarUtil {
    companion object {
    private const val DEFAULT_ALPHA = 0

    /**
     * 设置状态栏颜色（自定义颜色)
     *
     * @param activity 目标activity
     * @param color    状态栏颜色值
     */
    @JvmStatic
    fun setColor(activity: Activity, @ColorInt color: Int) {
        setColor(activity, color, DEFAULT_ALPHA)
    }

    /**
     * 设置纯色状态栏（自定义颜色，alpha）
     *
     * @param activity 目标activity
     * @param color    状态栏颜色值
     * @param alpha    状态栏透明度
     */
    @JvmStatic
    fun setColor(activity: Activity, @ColorInt color: Int, @IntRange(from = 0, to = 255) alpha: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            activity.getWindow().setStatusBarColor(cipherColor(color, alpha))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            setTranslucentView(activity.getWindow().getDecorView() as ViewGroup, color, alpha)
            setRootView(activity, true)
        }
    }

    /**
     * 设置状态栏渐变颜色
     *
     * @param activity 目标activity
     * @param view     目标View
     */
    @JvmStatic
    fun setGradientColor(activity: Activity, view: View) {
        val decorView = activity.getWindow().getDecorView() as ViewGroup
        val fakeStatusBarView = decorView.findViewById<View?>(R.id.custom)
        if (fakeStatusBarView != null) {
            decorView.removeView(fakeStatusBarView)
        }
        setRootView(activity, false)
        setTransparentForWindow(activity)
        setPaddingTop(activity, view)
    }

    /**
     * 设置状态栏渐变颜色（适用于Android4.4版本）
     * 如果是初次使用setGradientColor()，则可以直接调用5.0版本方法
     * 如果是先使用setColor()方法，则需要调用此方法
     * (已弃用)
     * @param activity
     * @param drawable
     */
    @Deprecated("")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @JvmStatic
    fun setGradientColor(activity: Activity, drawable: Drawable?) {
        setTransparentForWindow(activity)
        //获取顶级视图
        val decorView = activity.getWindow().getDecorView() as ViewGroup
        //获取顶部的StatusBarView,自定义id
        var fakeStatusBarView = decorView.findViewById<View?>(R.id.custom)
        if (fakeStatusBarView != null) {
            if (fakeStatusBarView.getVisibility() == View.GONE) {
                fakeStatusBarView.setVisibility(View.VISIBLE)
            }
            //设置顶层颜色
            fakeStatusBarView.setBackground(drawable)
        } else {
            //上述不符合，则创建一个View添加到顶级视图中
            fakeStatusBarView = View(activity)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getStatusBarHeight(activity))
            fakeStatusBarView.setLayoutParams(params)
            fakeStatusBarView.setBackground(drawable)
            fakeStatusBarView.setId(R.id.custom)
            decorView.addView(fakeStatusBarView)
        }
        setRootView(activity, false)
    }


    /**
     * 设置透明状态栏
     *
     * @param activity 目标界面
     */
    @JvmStatic
    fun setTransparentForWindow(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT)
            activity.getWindow()
                .getDecorView()
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.getWindow()
                .setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
    }

    /**
     * 清除透明状态栏
     *
     * @param activity 目标界面
     */
    @JvmStatic
    fun clearFlag(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(Color.WHITE)
            activity.getWindow()
                .clearFlags(View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
    }

    /**
     * 增加View的paddingTop,增加的值为状态栏高度 (智能判断，并设置高度)
     *
     * @param context 目标Context
     * @param view    需要增高的View
     */
    @JvmStatic
    fun setPaddingTop(context: Context, view: View) {
        if (view == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val lp = view.getLayoutParams()
            if (lp != null && view.getPaddingTop() == 0) {
                lp.height += getStatusBarHeight(context)
                view.setPadding(
                    view.getPaddingLeft(), view.getPaddingTop() + getStatusBarHeight(context),
                    view.getPaddingRight(), view.getPaddingBottom()
                )
            }
        }
    }


    /**
     * 设置状态栏darkMode,字体颜色及icon变黑(目前支持MIUI6以上,Flyme4以上,Android M以上)
     *
     * @param activity 目标activity
     */
    @JvmStatic
    fun setDarkMode(activity: Activity) {
        darkMode(activity.getWindow(), true)
    }

    /**
     * 设置状态栏darkMode,字体颜色及icon变亮(目前支持MIUI6以上,Flyme4以上,Android M以上)
     *
     * @param activity 目标activity
     */
    @JvmStatic
    fun setLightMode(activity: Activity) {
        darkMode(activity.getWindow(), false)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun darkMode(window: Window, dark: Boolean) {
        if (isFlyme4) {
            setModeForFlyme4(window, dark)
        } else if (isMIUI6) {
            setModeForMIUI6(window, dark)
        }
        darkModeForM(window, dark)
    }


    /**
     * android 6.0设置字体颜色
     *
     * @param window 目标window
     * @param dark   亮色 or 暗色
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun darkModeForM(window: Window, dark: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var systemUiVisibility = window.getDecorView().getSystemUiVisibility()
            if (dark) {
                systemUiVisibility = systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                systemUiVisibility = systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            window.getDecorView().setSystemUiVisibility(systemUiVisibility)
        }
    }


    /**
     * 设置MIUI6+的状态栏的darkMode,darkMode时候字体颜色及icon
     *
     * @param window 目标window
     * @param dark   亮色 or 暗色
     */
    private fun setModeForMIUI6(window: Window, dark: Boolean) {
        val clazz: Class<out Window?> = window.javaClass
        try {
            val layoutParams = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
            val field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
            val darkModeFlag = field.getInt(layoutParams)
            val extraFlagField = clazz.getMethod("setExtraFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            extraFlagField.invoke(window, if (dark) darkModeFlag else 0, darkModeFlag)
        } catch (e: Exception) {
            Log.e("StatusBar", "darkIcon: failed")
        }
    }

    /**
     * 设置Flyme4+的状态栏的darkMode,darkMode时候字体颜色及icon
     *
     * @param window 目标window
     * @param dark   亮色 or 暗色
     */
    private fun setModeForFlyme4(window: Window, dark: Boolean) {
        try {
            val lp = window.getAttributes()
            val darkFlag = WindowManager.LayoutParams::class.java.getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON")
            val meizuFlags = WindowManager.LayoutParams::class.java.getDeclaredField("meizuFlags")
            darkFlag.setAccessible(true)
            meizuFlags.setAccessible(true)
            val bit = darkFlag.getInt(null)
            var value = meizuFlags.getInt(lp)
            if (dark) {
                value = value or bit
            } else {
                value = value and bit.inv()
            }
            meizuFlags.setInt(lp, value)
            window.setAttributes(lp)
        } catch (e: Exception) {
            Log.e("StatusBar", "darkIcon: failed")
        }
    }


    private val isFlyme4: Boolean
        /**
         * 判断是否Flyme4以上
         */
        get() = Build.FINGERPRINT.contains("Flyme_OS_4") || Build.VERSION.INCREMENTAL.contains("Flyme_OS_4")
                || Pattern.compile("Flyme OS [4|5]", Pattern.CASE_INSENSITIVE).matcher(Build.DISPLAY).find()

    private val isMIUI6: Boolean
        /**
         * 判断是否MIUI6以上
         */
        get() {
            try {
                val clz = Class.forName("android.os.SystemProperties")
                val mtd = clz.getMethod("get", String::class.java)
                var `val` = mtd.invoke(null, "ro.miui.ui.version.name") as String?
                `val` = `val`!!.replace("[vV]".toRegex(), "")
                val version = `val`.toInt()
                return version >= 6
            } catch (e: Exception) {
                return false
            }
        }

    /**
     * 计算alpha色值
     *
     * @param color 状态栏颜色值
     * @param alpha 状态栏透明度
     */
    private fun cipherColor(@ColorInt color: Int, alpha: Int): Int {
        if (alpha == 0) {
            return color
        }
        val a = 1 - alpha / 255f
        var red = color shr 16 and 0xff
        var green = color shr 8 and 0xff
        var blue = color and 0xff
        red = (red * a + 0.5).toInt()
        green = (green * a + 0.5).toInt()
        blue = (blue * a + 0.5).toInt()
        return 0xff shl 24 or (red shl 16) or (green shl 8) or blue
    }


    /**
     * 创建透明View
     *
     * @param viewGroup 目标视图
     * @param color     状态栏颜色值
     * @param alpha     状态栏透明度
     */
    private fun setTranslucentView(viewGroup: ViewGroup, @ColorInt color: Int, @IntRange(from = 0, to = 255) alpha: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val cipherColor = cipherColor(color, alpha)
            var translucentView = viewGroup.findViewById<View?>(R.id.custom)
            if (translucentView == null && cipherColor != 0) {
                translucentView = View(viewGroup.getContext())
                translucentView.setId(R.id.custom)
                val params =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getStatusBarHeight(viewGroup.getContext()))
                viewGroup.addView(translucentView, params)
            }
            if (translucentView != null) {
                translucentView.setBackgroundColor(cipherColor)
            }
        }
    }

    /**
     * 设置根布局参数
     *
     * @param activity         目标activity
     * @param fitSystemWindows 是否预留toolbar的高度
     */
    private fun setRootView(activity: Activity, fitSystemWindows: Boolean) {
        val parent = activity.findViewById<ViewGroup>(R.id.content)
        var i = 0
        val count = parent.getChildCount()
        while (i < count) {
            val childView = parent.getChildAt(i)
            if (childView is ViewGroup) {
                childView.setFitsSystemWindows(fitSystemWindows)
                childView.setClipToPadding(fitSystemWindows)
            }
            i++
        }
    }

    /**
     * 获取状态栏高度
     *
     * @param context 目标Context
     */
    @JvmStatic
    fun getStatusBarHeight(context: Context): Int {
        // 获得状态栏高度
        val resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android")
        return context.getResources().getDimensionPixelSize(resourceId)
    }
    }
}
