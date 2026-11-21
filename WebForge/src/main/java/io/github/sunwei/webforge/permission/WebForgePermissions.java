
package io.github.sunwei.webforge.permission;

import android.Manifest;
import android.os.Build;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 */
public class WebForgePermissions {
	public static String[] CAMERA;
	public static String[] LOCATION;
	public static String[] MEDIA;
	/**
	 * 录音权限
	 */
	public static String[] AUDIO;
	/**
	 * 拨打电话权限（通话）
	 */
	public static String[] CALL;
	public static final String ACTION_CAMERA = "Camera";
	public static final String ACTION_LOCATION = "Location";
	public static final String ACTION_MEDIA = "Media";
	public static final String ACTION_AUDIO = "Audio";
	public static final String ACTION_CALL = "Call";

	static {
		CAMERA = new String[]{
				Manifest.permission.CAMERA};

		LOCATION = new String[]{
				Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.ACCESS_COARSE_LOCATION};

		//录音（H5 使用 getUserMedia / 录音 时申请）
		AUDIO = new String[]{
				Manifest.permission.RECORD_AUDIO};

		//通话（H5 使用 tel: 拨号 时申请）
		CALL = new String[]{
				Manifest.permission.CALL_PHONE};

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			// Android 13 file uploads use the Storage Access Framework or the
			// photo picker, neither of which needs a broad media permission.
			MEDIA = new String[]{};
		} else {
			MEDIA = new String[]{
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
			};
		}
	}

	/**
	 * 根据权限类型获取需要申请的权限数组，H5 可通过 android 类型字符串直接申请。
	 * 支持 Camera | Audio | Call | Location | Media （不区分大小写）
	 *
	 * @param type 权限类型，多个类型使用 "," 分隔，例如 "audio,call"
	 * @return 需要申请的权限，未识别到类型时返回空数组
	 */
	public static String[] of(String type) {
		if (TextUtils.isEmpty(type)) {
			return new String[]{};
		}
		LinkedHashSet<String> results = new LinkedHashSet<>();
		String[] types = type.split(",");
		for (String item : types) {
			String[] permissions = ofSingleType(item == null ? null : item.trim());
			if (permissions != null) {
				results.addAll(Arrays.asList(permissions));
			}
		}
		return results.toArray(new String[]{});
	}

	private static String[] ofSingleType(String type) {
		if (type == null) {
			return null;
		}
		if (ACTION_CAMERA.equalsIgnoreCase(type)) {
			return CAMERA;
		}
		if (ACTION_AUDIO.equalsIgnoreCase(type) || "record".equalsIgnoreCase(type)) {
			return AUDIO;
		}
		if (ACTION_CALL.equalsIgnoreCase(type) || "phone".equalsIgnoreCase(type)) {
			return CALL;
		}
		if (ACTION_LOCATION.equalsIgnoreCase(type)) {
			return LOCATION;
		}
		if (ACTION_MEDIA.equalsIgnoreCase(type)) {
			return MEDIA;
		}
		return null;
	}

	private static void emptyMediaPermission() {
		MEDIA = new String[]{};
	}

	private static void emptyCameraPermission() {
		CAMERA = new String[]{};
	}

	public static void dontAskUnnecessaryPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			emptyMediaPermission();
			emptyCameraPermission();
		}
	}
}
