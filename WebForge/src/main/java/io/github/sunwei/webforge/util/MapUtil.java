package io.github.sunwei.webforge.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import static java.lang.Integer.toHexString;

/**
 * @ClassName: MapUtil
 * @Description: java类作用描述
 * @Author: 萤火虫
 * @Date: 2020/6/24 9:42
 */
public class MapUtil {
    public final static String BAIDU_PKG = "com.baidu.BaiduMap"; //百度地图的包名

    public final static String GAODE_PKG = "com.autonavi.minimap";//高德地图的包名

    /**
     * 打开高德
     *
     * @param context
     * @param longitude 经度
     * @param latitude  维度
     */
    public static void openGaoDe(@Nullable Context context, double longitude, double latitude) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent("android.intent.action.VIEW", Uri.parse("androidamap://navi?sourceApplication=叁拾叁信息技术有限公司&lat=" + latitude + "&lon=" + longitude + "&dev=0"));
        intent.setPackage("com.autonavi.minimap");
        context.startActivity(intent);
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
    public static void route(Context context, String slon, String slat, String sname, String dlon, String dlat, String dname) {
        if (context == null) {
            return;
        }
        if (checkMapAppsIsExist(context, GAODE_PKG)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setPackage("com.autonavi.minimap");
            String appName = getAppName(context);
            Uri.Builder builder = Uri.parse("androidamap://route").buildUpon()
                    .appendQueryParameter("sourceApplication",
                            TextUtils.isEmpty(appName) ? "WebForge" : appName)
                    .appendQueryParameter("dlat", dlat)
                    .appendQueryParameter("dlon", dlon)
                    .appendQueryParameter("dev", "0")
                    .appendQueryParameter("t", "0");
            //如果设置了起点
            if (!TextUtils.isEmpty(slat) && !TextUtils.isEmpty(slon)) {
                builder.appendQueryParameter("slat", slat)
                        .appendQueryParameter("slon", slon)
                        .appendQueryParameter("sname", sname == null ? "" : sname);
            }
            if (!TextUtils.isEmpty(dname)) {
                builder.appendQueryParameter("dname", dname);
            }
            intent.setData(builder.build());
            try {
                context.startActivity(intent);
                return;
            } catch (Throwable ignored) {
                // Fall back to the browser version below.
            }
        }
        Uri.Builder webBuilder = Uri.parse("https://uri.amap.com/navigation").buildUpon();
        //如果设置了起点
        if (!TextUtils.isEmpty(slat) && !TextUtils.isEmpty(slon)) {
            webBuilder.appendQueryParameter("from",
                    slon + "," + slat + "," + (TextUtils.isEmpty(sname) ? "起点" : sname));
        }
        webBuilder.appendQueryParameter("to",
                dlon + "," + dlat + "," + (TextUtils.isEmpty(dname) ? "终点" : dname))
                .appendQueryParameter("mode", "car");
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, webBuilder.build()));
        } catch (Throwable ignored) {
            // No map/browser application can handle the navigation URL.
        }
    }
    public static String getAppName(Context context) {
        if (context == null) {
            return null;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(), 0);
            int labelRes = packageInfo.applicationInfo.labelRes;
            return context.getResources().getString(labelRes);
        } catch (Throwable e) {
        }
        return null;
    }
    /**
     * 检测地图应用是否安装
     *
     * @param context
     * @param packagename
     * @return
     */
    public static boolean checkMapAppsIsExist(Context context, String packagename) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packagename, 0);
        } catch (Exception e) {
            packageInfo = null;
            e.printStackTrace();
        }
        if (packageInfo == null) {
            return false;
        } else {
            return true;
        }
    }

    public static String sHA1(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] publicKey = md.digest(cert);
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < publicKey.length; i++) {
                String appendString = toHexString(0xFF & publicKey[i])
                        .toUpperCase(Locale.US);
                if (appendString.length() == 1)
                    hexString.append("0");
                hexString.append(appendString);
                hexString.append(":");
            }

            return hexString.toString().substring(0, hexString.toString().length() - 1);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

}
