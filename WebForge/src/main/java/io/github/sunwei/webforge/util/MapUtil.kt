package io.github.sunwei.webforge.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.text.TextUtils
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @ClassName: MapUtil
 * @Description: java类作用描述
 */
open class MapUtil {
    companion object {
    const val BAIDU_PKG: String = "com.baidu.BaiduMap" //百度地图的包名

    const val GAODE_PKG: String = "com.autonavi.minimap" //高德地图的包名

    /**
     * 打开高德
     *
     * @param context
     * @param longitude 经度
     * @param latitude  维度
     */
    @JvmStatic
    fun openGaoDe(context: Context?, longitude: Double, latitude: Double) {
        if (context == null) {
            return
        }
        val intent = Intent("android.intent.action.VIEW", Uri.parse("androidamap://navi?sourceApplication=叁拾叁信息技术有限公司&lat=" + latitude + "&lon=" + longitude + "&dev=0"))
        intent.setPackage("com.autonavi.minimap")
        context.startActivity(intent)
    }


    //    public static void openBaidu(Context context, MarkerOptions markerOption) {
    //        Intent i1 = new Intent();
    //        double[] position = GPSUtil.gcj02_To_Bd09(markerOption.getPosition().latitude, markerOption.getPosition().longitude);
    //        i1.setData(Uri.parse("baidumap://map/geocoder?location=" + position[0] + "," + position[1]));
    //        context.startActivity(i1);
    //    }
    /**
     * 路线规划
     *
     * @param context
     * @param slon    开始点-经度
     * @param slat    开始点-维度
     * @param sname   地址名
     * @param dlon    终点-经度
     * @param dlat    终点-维度
     * @param dname   终点名字
     */
    @JvmStatic
    fun route(context: Context?, slon: String?, slat: String?, sname: String?, dlon: String?, dlat: String?, dname: String?) {
        if (context == null) {
            return
        }
        if (checkMapAppsIsExist(context, GAODE_PKG)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.setPackage("com.autonavi.minimap")
            val appName = getAppName(context)
            val builder = Uri.parse("androidamap://route").buildUpon()
                .appendQueryParameter(
                    "sourceApplication",
                    if (TextUtils.isEmpty(appName)) "WebForge" else appName
                )
                .appendQueryParameter("dlat", dlat)
                .appendQueryParameter("dlon", dlon)
                .appendQueryParameter("dev", "0")
                .appendQueryParameter("t", "0")
            //如果设置了起点
            if (!TextUtils.isEmpty(slat) && !TextUtils.isEmpty(slon)) {
                builder.appendQueryParameter("slat", slat)
                    .appendQueryParameter("slon", slon)
                    .appendQueryParameter("sname", if (sname == null) "" else sname)
            }
            if (!TextUtils.isEmpty(dname)) {
                builder.appendQueryParameter("dname", dname)
            }
            intent.setData(builder.build())
            try {
                context.startActivity(intent)
                return
            } catch (ignored: Throwable) {
                // Fall back to the browser version below.
            }
        }
        val webBuilder = Uri.parse("https://uri.amap.com/navigation").buildUpon()
        //如果设置了起点
        if (!TextUtils.isEmpty(slat) && !TextUtils.isEmpty(slon)) {
            webBuilder.appendQueryParameter(
                "from",
                slon + "," + slat + "," + (if (TextUtils.isEmpty(sname)) "起点" else sname)
            )
        }
        webBuilder.appendQueryParameter(
            "to",
            dlon + "," + dlat + "," + (if (TextUtils.isEmpty(dname)) "终点" else dname)
        )
            .appendQueryParameter("mode", "car")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, webBuilder.build()))
        } catch (ignored: Throwable) {
            // No map/browser application can handle the navigation URL.
        }
    }

    @JvmStatic
    fun getAppName(context: Context?): String? {
        if (context == null) {
            return null
        }
        try {
            val packageManager = context.getPackageManager()
            val packageInfo = packageManager.getPackageInfo(
                context.getPackageName(), 0
            )
            val labelRes = packageInfo.applicationInfo.labelRes
            return context.getResources().getString(labelRes)
        } catch (e: Throwable) {
        }
        return null
    }

    /**
     * 检测地图应用是否安装
     *
     * @param context
     * @param packagename
     * @return
     */
    @JvmStatic
    fun checkMapAppsIsExist(context: Context, packagename: String): Boolean {
        var packageInfo: PackageInfo?
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packagename, 0)
        } catch (e: Exception) {
            packageInfo = null
            e.printStackTrace()
        }
        if (packageInfo == null) {
            return false
        } else {
            return true
        }
    }

    @JvmStatic
    fun sHA1(context: Context): String? {
        try {
            val info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNATURES
            )
            val cert = info.signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA1")
            val publicKey = md.digest(cert)
            val hexString = StringBuffer()
            for (i in publicKey.indices) {
                val appendString = Integer.toHexString(0xFF and publicKey[i].toInt())
                    .uppercase()
                if (appendString.length == 1) hexString.append("0")
                hexString.append(appendString)
                hexString.append(":")
            }

            return hexString.toString().substring(0, hexString.toString().length - 1)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return null
    }
    }
}
