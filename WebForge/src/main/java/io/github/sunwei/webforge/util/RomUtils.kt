package io.github.sunwei.webforge.util

import android.annotation.SuppressLint
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale
import java.util.Properties

/**
 * <pre>
 * author: Blankj
 * time  : 2018/07/04
 * desc  : utils about rom
</pre> *
 */
class RomUtils private constructor() {
    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }

    open class RomInfo {
        internal var name: String? = null
        open var version: String? = null
            internal set

        open fun getName(): String {
            return name!!
        }

        override fun toString(): String {
            return "RomInfo{name=" + name +
                    ", version=" + version + "}"
        }
    }

    companion object {
        private val ROM_HUAWEI = arrayOf<String>("huawei")
        private const val VERSION_PROPERTY_HUAWEI = "ro.build.version.emui"
        private const val UNKNOWN = "unknown"

        private var bean: RomInfo? = null

        @get:JvmStatic
        val isHuawei: Boolean
            /**
             * Return whether the rom is made by huawei.
             *
             * @return `true`: yes<br></br>`false`: no
             */
        get() = ROM_HUAWEI[0] == romInfo?.getName()

        @get:JvmStatic
        val romInfo: RomInfo?
            /**
             * Return the rom's information.
             *
             * @return the rom's information
             */
            get() {
                val cached = bean
                if (cached != null) return cached
                val created = RomInfo()
                bean = created
                val brand: String = brand
                val manufacturer: String = manufacturer
                if (isRightRom(brand, manufacturer, *ROM_HUAWEI)) {
                    created.name = ROM_HUAWEI[0]
                    val version: String = getRomVersion(VERSION_PROPERTY_HUAWEI)
                    val temp: Array<String?> = version.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (temp.size > 1) {
                        created.version = temp[1]
                    } else {
                        created.version = version
                    }
                    return created
                } else {
                    created.name = manufacturer
                }
                created.version = getRomVersion("")
                return created
            }

        private fun isRightRom(brand: String, manufacturer: String, vararg names: String): Boolean {
            for (name in names) {
                if (brand.contains(name) || manufacturer.contains(name)) {
                    return true
                }
            }
            return false
        }

        @get:JvmStatic
        val manufacturer: String
            get() {
                try {
                    val manufacturer = Build.MANUFACTURER
                    if (!TextUtils.isEmpty(manufacturer)) {
                        return manufacturer!!.lowercase(Locale.getDefault())
                    }
                } catch (ignore: Throwable) { /**/
                }
                return UNKNOWN
            }

        @get:JvmStatic
        val brand: String
            get() {
                try {
                    val brand = Build.BRAND
                    if (!TextUtils.isEmpty(brand)) {
                        return brand!!.lowercase(Locale.getDefault())
                    }
                } catch (ignore: Throwable) { /**/
                }
                return UNKNOWN
            }

        private fun getRomVersion(propertyName: String?): String {
            var ret = ""
            if (!TextUtils.isEmpty(propertyName)) {
                ret = getSystemProperty(propertyName)
            }
            if (TextUtils.isEmpty(ret) || ret == UNKNOWN) {
                try {
                    val display = Build.DISPLAY
                    if (!TextUtils.isEmpty(display)) {
                        ret = display!!.lowercase(Locale.getDefault())
                    }
                } catch (ignore: Throwable) { /**/
                }
            }
            if (TextUtils.isEmpty(ret)) {
                return UNKNOWN
            }
            return ret
        }

        private fun getSystemProperty(name: String?): String {
            var prop: String = getSystemPropertyByShell(name)
            if (!TextUtils.isEmpty(prop)) return prop
            prop = getSystemPropertyByStream(name)
            if (!TextUtils.isEmpty(prop)) return prop
            if (Build.VERSION.SDK_INT < 28) {
                return getSystemPropertyByReflect(name)
            }
            return prop
        }

        private fun getSystemPropertyByShell(propName: String?): String {
            var line: String?
            var input: BufferedReader? = null
            try {
                val p = Runtime.getRuntime().exec("getprop " + propName)
                input = BufferedReader(InputStreamReader(p.getInputStream()), 1024)
                val ret = input.readLine()
                if (ret != null) {
                    return ret
                }
            } catch (ignore: IOException) {
            } finally {
                if (input != null) {
                    try {
                        input.close()
                    } catch (ignore: IOException) { /**/
                    }
                }
            }
            return ""
        }

        private fun getSystemPropertyByStream(key: String?): String {
            try {
                val prop = Properties()
                val `is` = FileInputStream(
                    File(Environment.getRootDirectory(), "build.prop")
                )
                prop.load(`is`)
                return prop.getProperty(key, "")
            } catch (ignore: Exception) { /**/
            }
            return ""
        }

        private fun getSystemPropertyByReflect(key: String?): String {
            try {
                @SuppressLint("PrivateApi") val clz = Class.forName("android.os.SystemProperties")
                val getMethod = clz.getMethod("get", String::class.java, String::class.java)
                return getMethod.invoke(clz, key, "") as String
            } catch (e: Exception) { /**/
            }
            return ""
        }
    }
}
