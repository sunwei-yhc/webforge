package io.github.sunwei.webforge.permission

import android.Manifest
import android.os.Build
import android.text.TextUtils
import java.util.Arrays

/**
 */
open class WebForgePermissions {
    companion object {
    @JvmField
    var CAMERA: Array<String>
    @JvmField
    var LOCATION: Array<String>
    @JvmField
    var MEDIA: Array<String>

    /**
     * 录音权限
     */
    @JvmField
    var AUDIO: Array<String>

    /**
     * 拨打电话权限（通话）
     */
    @JvmField
    var CALL: Array<String>
    const val ACTION_CAMERA: String = "Camera"
    const val ACTION_LOCATION: String = "Location"
    const val ACTION_MEDIA: String = "Media"
    const val ACTION_AUDIO: String = "Audio"
    const val ACTION_CALL: String = "Call"

    init {
        CAMERA = arrayOf(Manifest.permission.CAMERA)

        LOCATION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        //录音（H5 使用 getUserMedia / 录音 时申请）
        AUDIO = arrayOf(Manifest.permission.RECORD_AUDIO)

        //通话（H5 使用 tel: 拨号 时申请）
        CALL = arrayOf(Manifest.permission.CALL_PHONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 起由系统文件选择器授予所选文件的访问权限。
            MEDIA = emptyArray()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 与 Manifest 保持一致，写入权限只声明到 API 28。
            MEDIA = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            MEDIA = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * 根据权限类型获取需要申请的权限数组，H5 可通过 android 类型字符串直接申请。
     * 支持 Camera | Audio | Call | Location | Media （不区分大小写）
     *
     * @param type 权限类型，多个类型使用 "," 分隔，例如 "audio,call"
     * @return 需要申请的权限，未识别到类型时返回空数组
     */
    @JvmStatic
    fun of(type: String?): Array<String> {
        if (TextUtils.isEmpty(type)) {
            return emptyArray()
        }
        val results = LinkedHashSet<String>()
        val types = type!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }
        for (item in types) {
            results.addAll(ofSingleType(item.trim { it <= ' ' }))
        }
        return results.toTypedArray()
    }

    private fun ofSingleType(type: String): Array<String> {
        if (ACTION_CAMERA.equals(type, ignoreCase = true)) {
            return CAMERA
        }
        if (ACTION_AUDIO.equals(type, ignoreCase = true) || "record".equals(type, ignoreCase = true)) {
            return AUDIO
        }
        if (ACTION_CALL.equals(type, ignoreCase = true) || "phone".equals(type, ignoreCase = true)) {
            return CALL
        }
        if (ACTION_LOCATION.equals(type, ignoreCase = true)) {
            return LOCATION
        }
        if (ACTION_MEDIA.equals(type, ignoreCase = true)) {
            return MEDIA
        }
        return emptyArray()
    }

    private fun emptyMediaPermission() {
        MEDIA = emptyArray()
    }

    private fun emptyCameraPermission() {
        CAMERA = emptyArray()
    }

    @JvmStatic
    fun dontAskUnnecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            emptyMediaPermission()
            emptyCameraPermission()
        }
    }
    }
}
