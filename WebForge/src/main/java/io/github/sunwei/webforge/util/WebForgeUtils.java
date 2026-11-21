
package io.github.sunwei.webforge.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.AppOpsManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.os.EnvironmentCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.content.CursorLoader;

import com.google.android.material.snackbar.Snackbar;


import io.github.sunwei.webforge.R;
import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.filechooser.FileChooser;
import io.github.sunwei.webforge.permission.Action;
import io.github.sunwei.webforge.permission.PermissionInterceptor;
import io.github.sunwei.webforge.permission.WebForgeActionFragment;
import io.github.sunwei.webforge.ui.PageUiController;
import io.github.sunwei.webforge.ui.WebParentLayout;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static io.github.sunwei.webforge.config.WebForgeConfig.FILE_CACHE_PATH;
import static io.github.sunwei.webforge.config.WebForgeConfig.WEBFORGE_FILE_PATH;

/**
 */
public class WebForgeUtils {

	private static final String TAG = WebForgeUtils.class.getSimpleName();
	private static Handler mHandler = null;

	private WebForgeUtils() {
		throw new UnsupportedOperationException("u can't init me");
	}

	public static int dp2px(Context context, float dipValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		return (int) (dipValue * scale + 0.5f);
	}

	public static String getWebForgeFilePath(Context context) {
		if (!TextUtils.isEmpty(WEBFORGE_FILE_PATH)) {
			return WEBFORGE_FILE_PATH;
		}
		String dir = getDiskExternalCacheDir(context);
		File mFile = new File(dir, FILE_CACHE_PATH);
		try {
			if (!mFile.exists()) {
				mFile.mkdirs();
			}
		} catch (Throwable throwable) {
			LogUtils.i(TAG, "create dir exception");
		}
		LogUtils.i(TAG, "path:" + mFile.getAbsolutePath() + "  path:" + mFile.getPath());
		return WEBFORGE_FILE_PATH = mFile.getAbsolutePath();
	}

	public static File createFileByName(Context context, String name, boolean cover) throws IOException {
		String path = getWebForgeFilePath(context);
		if (TextUtils.isEmpty(path)) {
			return null;
		}
		File mFile = new File(path, name);
		if (mFile.exists()) {
			if (cover) {
				mFile.delete();
				mFile.createNewFile();
			}
		} else {
			mFile.createNewFile();
		}
		return mFile;
	}

	public static int checkNetworkType(Context context) {
		int netType = 0;
		//连接管理对象
		ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (manager == null) {
			return netType;
		}
		//获取NetworkInfo对象
		@SuppressLint("MissingPermission") NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		if (networkInfo == null) {
			return netType;
		}
		switch (networkInfo.getType()) {
			case ConnectivityManager.TYPE_WIFI:
			case ConnectivityManager.TYPE_WIMAX:
			case ConnectivityManager.TYPE_ETHERNET:
				return 1;
			case ConnectivityManager.TYPE_MOBILE:
				switch (networkInfo.getSubtype()) {
					case TelephonyManager.NETWORK_TYPE_LTE:  // 4G
					case TelephonyManager.NETWORK_TYPE_HSPAP:
					case TelephonyManager.NETWORK_TYPE_EHRPD:
						return 2;
					case TelephonyManager.NETWORK_TYPE_UMTS: // 3G
					case TelephonyManager.NETWORK_TYPE_CDMA:
					case TelephonyManager.NETWORK_TYPE_EVDO_0:
					case TelephonyManager.NETWORK_TYPE_EVDO_A:
					case TelephonyManager.NETWORK_TYPE_EVDO_B:
						return 3;
					case TelephonyManager.NETWORK_TYPE_GPRS: // 2G
					case TelephonyManager.NETWORK_TYPE_EDGE:
						return 4;
					default:
						return netType;
				}

			default:
				return netType;
		}
	}

	public static Uri getUriFromFile(Context context, File file) {
		Uri uri = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			uri = getUriFromFileForN(context, file);
		} else {
			uri = Uri.fromFile(file);
		}
		return uri;
	}

	static Uri getUriFromFileForN(Context context, File file) {
		Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".WebForgeFileProvider", file);
		return fileUri;
	}

	static void setIntentDataAndType(Context context,
	                                 Intent intent,
	                                 String type,
	                                 File file,
	                                 boolean writeAble) {
		if (Build.VERSION.SDK_INT >= 24) {
			intent.setDataAndType(getUriFromFile(context, file), type);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			if (writeAble) {
				intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			}
		} else {
			intent.setDataAndType(Uri.fromFile(file), type);
		}
	}

	static String getDiskExternalCacheDir(Context context) {
		File mFile = context.getExternalCacheDir();
		if (Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(mFile))) {
			return mFile.getAbsolutePath();
		}
		return null;
	}

	private static String getMIMEType(File f) {
		String type = "";
		String fName = f.getName();
		/* 取得扩展名 */
		String end = fName.substring(fName.lastIndexOf(".") + 1, fName.length()).toLowerCase();
		/* 依扩展名的类型决定MimeType */
		if (end.equals("pdf")) {
			type = "application/pdf";//
		} else if (end.equals("m4a") || end.equals("mp3") || end.equals("mid") ||
				end.equals("xmf") || end.equals("ogg") || end.equals("wav")) {
			type = "audio/*";
		} else if (end.equals("3gp") || end.equals("mp4")) {
			type = "video/*";
		} else if (end.equals("jpg") || end.equals("gif") || end.equals("png") ||
				end.equals("jpeg") || end.equals("bmp")) {
			type = "image/*";
		} else if (end.equals("apk")) {
			type = "application/vnd.android.package-archive";
		} else if (end.equals("pptx") || end.equals("ppt")) {
			type = "application/vnd.ms-powerpoint";
		} else if (end.equals("docx") || end.equals("doc")) {
			type = "application/vnd.ms-word";
		} else if (end.equals("xlsx") || end.equals("xls")) {
			type = "application/vnd.ms-excel";
		} else {
			type = "*/*";
		}
		return type;
	}

	public static void show(View parent,
	                 CharSequence text,
	                 int duration,
	                 @ColorInt int textColor,
	                 @ColorInt int bgColor,
	                 CharSequence actionText,
	                 @ColorInt int actionTextColor,
	                 View.OnClickListener listener) {
		SpannableString spannableString = new SpannableString(text);
		ForegroundColorSpan colorSpan = new ForegroundColorSpan(textColor);
		spannableString.setSpan(colorSpan, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		Snackbar snackbar = Snackbar.make(parent, spannableString, duration);
		View view = snackbar.getView();
		view.setBackgroundColor(bgColor);
		if (actionText != null && actionText.length() > 0 && listener != null) {
			snackbar.setActionTextColor(actionTextColor);
			snackbar.setAction(actionText, listener);
		}
		snackbar.show();
	}

	public static boolean checkNetwork(Context context) {
		ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity == null) {
			return false;
		}
		@SuppressLint("MissingPermission") NetworkInfo info = connectivity.getActiveNetworkInfo();
		return info != null && info.isConnected();
	}

	public static int clearCacheFolder(final File dir, final int numDays) {
		int deletedFiles = 0;
		if (dir != null) {
			Log.i("Info", "dir:" + dir.getAbsolutePath());
		}
		if (dir != null && dir.isDirectory()) {
			try {
				for (File child : dir.listFiles()) {

					//first delete subdirectories recursively
					if (child.isDirectory()) {
						deletedFiles += clearCacheFolder(child, numDays);
					}

					//then delete the files and subdirectories in this dir
					//only empty directories can be deleted, so subdirs have been done first
					if (child.lastModified() < new Date().getTime() - numDays * DateUtils.DAY_IN_MILLIS) {
						Log.i(TAG, "file name:" + child.getName());
						if (child.delete()) {
							deletedFiles++;
						}
					}
				}
			} catch (Exception e) {
				Log.e("Info", String.format("Failed to clean the cache, result %s", e.getMessage()));
			}
		}
		return deletedFiles;
	}

	static void clearCache(final Context context, final int numDays) {
		Log.i("Info", String.format("Starting cache prune, deleting files older than %d days", numDays));
		int numDeletedFiles = clearCacheFolder(context.getCacheDir(), numDays);
		Log.i("Info", String.format("Cache pruning completed, %d files deleted", numDeletedFiles));
	}

	public static String[] uriToPath(Activity activity, Uri[] uris) {
		if (activity == null || uris == null || uris.length == 0) {
			return null;
		}
		try {
			String[] paths = new String[uris.length];
			int i = 0;
			for (Uri mUri : uris) {
				paths[i++] = Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2 ? getFileAbsolutePath(activity, mUri) : getRealPathBelowVersion(activity, mUri);

			}
			return paths;
		} catch (Throwable throwable) {
			if (LogUtils.isDebug()) {
				throwable.printStackTrace();
			}
		}
		return null;

	}

	private static String getRealPathBelowVersion(Context context, Uri uri) {
		String filePath = null;
		LogUtils.i(TAG, "method -> getRealPathBelowVersion " + uri + "   path:" + uri.getPath() + "    getAuthority:" + uri.getAuthority());
		String[] projection = {MediaStore.Images.Media.DATA};
		CursorLoader loader = new CursorLoader(context, uri, projection, null,
				null, null);
		Cursor cursor = loader.loadInBackground();
		if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						filePath = cursor.getString(cursor.getColumnIndexOrThrow(projection[0]));
					}
				} finally {
					cursor.close();
				}
		}
		if (filePath == null) {
			filePath = uri.getPath();
		}
		return filePath;
	}

	public static File createImageFile(Context context) {
		File mFile = null;
		try {
			String timeStamp =
					new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
			String imageName = String.format("aw_%s.jpg", timeStamp);
			mFile = createFileByName(context, imageName, true);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return mFile;
	}

	public static File createVideoFile(Context context){
		File mFile = null;
		try {
			String timeStamp =
					new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
			String imageName = String.format("aw_%s.mp4", timeStamp);  //默认生成mp4
			mFile = createFileByName(context, imageName, true);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return mFile;
	}

	public static void closeIO(Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@TargetApi(19)
	static String getFileAbsolutePath(Activity context, Uri fileUri) {
		if (context == null || fileUri == null) {
			return null;
		}
//		LogUtils.i(TAG, "getAuthority:" + fileUri.getAuthority() + "  getHost:" + fileUri.getHost() + "   getPath:" + fileUri.getPath() + "  getScheme:" + fileUri.getScheme() + "  query:" + fileUri.getQuery());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, fileUri)) {
			if (isExternalStorageDocument(fileUri)) {
				String docId = DocumentsContract.getDocumentId(fileUri);
				String[] split = docId.split(":");
				String type = split[0];
				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}
			} else if (isDownloadsDocument(fileUri)) {
				String id = DocumentsContract.getDocumentId(fileUri);
				Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
				return getDataColumn(context, contentUri, null, null);
			} else if (isMediaDocument(fileUri)) {
				String docId = DocumentsContract.getDocumentId(fileUri);
				String[] split = docId.split(":");
				String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}
				String selection = MediaStore.Images.Media._ID + "=?";
				String[] selectionArgs = new String[]{split[1]};
				return getDataColumn(context, contentUri, selection, selectionArgs);
			} else {
			}
		} // MediaStore (and general)
		else if (fileUri.getAuthority().equalsIgnoreCase(context.getPackageName() + ".WebForgeFileProvider")) {
			String path = fileUri.getPath();
			int index = path.lastIndexOf("/");
			return getWebForgeFilePath(context) + File.separator + path.substring(index + 1, path.length());
		} else if ("content".equalsIgnoreCase(fileUri.getScheme())) {
			// Return the remote address
			if (isGooglePhotosUri(fileUri)) {
				return fileUri.getLastPathSegment();
			}
			return getDataColumn(context, fileUri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(fileUri.getScheme())) {
			return fileUri.getPath();
		}
		return null;
	}

	static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
		Cursor cursor = null;
		String[] projection = {MediaStore.Images.Media.DATA};
		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
				return cursor.getString(index);
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return null;
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	public static Intent getCommonFileIntentCompat(Context context, File file) {
		Intent mIntent = new Intent().setAction(Intent.ACTION_VIEW);
		setIntentDataAndType(context, mIntent, getMIMEType(file), file, false);
		return mIntent;
	}

	public static Intent getIntentCaptureCompat(Context context, File file) {
		Intent mIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		Uri mUri = getUriFromFile(context, file);
		mIntent.addCategory(Intent.CATEGORY_DEFAULT);
		mIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUri);
		return mIntent;
	}

	public static Intent getIntentVideoCompat(Context context, File file){
		Intent mIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
		Uri mUri = getUriFromFile(context, file);
		mIntent.addCategory(Intent.CATEGORY_DEFAULT);
		mIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUri);
		return mIntent;
	}

	public static boolean isUIThread() {
		return Looper.myLooper() == Looper.getMainLooper();
	}

	public static boolean isEmptyCollection(Collection collection) {
		return collection == null || collection.isEmpty();
	}

	private static Toast mToast = null;
	public static void toastShowShort(Context context, String msg) {
		if (mToast == null) {
			mToast = Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT);
		} else {
			mToast.setText(msg);
		}
		mToast.show();
	}

	public static boolean hasPermission(@NonNull Context context, @NonNull String... permissions) {
		return hasPermission(context, Arrays.asList(permissions));
	}

	public static boolean hasPermission(@NonNull Context context, @NonNull List<String> permissions) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return true;
		}
		for (String permission : permissions) {
			int result = ContextCompat.checkSelfPermission(context, permission);
			if (result == PackageManager.PERMISSION_DENIED) {
				return false;
			}
			String op = AppOpsManagerCompat.permissionToOp(permission);
			if (TextUtils.isEmpty(op)) {
				continue;
			}
			result = AppOpsManagerCompat.noteProxyOp(context, op, context.getPackageName());
			if (result != AppOpsManagerCompat.MODE_ALLOWED) {
				return false;
			}
		}
		return true;
	}

	public static List<String> getDeniedPermissions(Activity activity, String[] permissions) {
		if (permissions == null || permissions.length == 0) {
			return Collections.EMPTY_LIST;
		}
		List<String> deniedPermissions = new ArrayList<>(permissions.length);
		for (int i = 0; i < permissions.length; i++) {
			if (!hasPermission(activity, permissions[i])) {
				deniedPermissions.add(permissions[i]);
			}
		}
		return deniedPermissions;
	}

	/**
	 * 主动申请运行时权限（例如 H5 使用录音、通话时调用）。
	 *
	 * @param activity   发起申请的 Activity，需要是 FragmentActivity
	 * @param permissions 需要申请的权限
	 * @param listener   申请结果回调，可为 null
	 * @return true 表示已经发起申请（或者无需申请，回调会立即执行），false 表示无法发起申请
	 */
	public static boolean requestPermissions(@NonNull Activity activity, @Nullable String[] permissions,
	                                         @Nullable WebForgeActionFragment.PermissionListener listener) {
		if (activity == null || permissions == null || permissions.length == 0) {
			return false;
		}
		List<String> deniedPermissions = getDeniedPermissions(activity, permissions);
		if (deniedPermissions.isEmpty()) {
			//已经授权，直接回调，业务方可以立即执行后续逻辑
			if (listener != null) {
				listener.onRequestPermissionsResult(permissions, buildGrantResults(permissions.length), new Bundle());
			}
			return true;
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			//6.0 以下没有运行时权限，可直接使用
			if (listener != null) {
				listener.onRequestPermissionsResult(permissions, buildGrantResults(permissions.length), new Bundle());
			}
			return true;
		}
		if (!(activity instanceof FragmentActivity)) {
			//没有 FragmentActivity 无法使用 WebForge 的权限申请，交给业务方自行申请
			return false;
		}
		final String[] requestPermissions = deniedPermissions.toArray(new String[]{});
		Action action = Action.createPermissionsAction(requestPermissions);
		action.setPermissionListener(listener);
		try {
			WebForgeActionFragment.start(activity, action);
		} catch (Throwable ignore) {
			if (LogUtils.isDebug()) {
				ignore.printStackTrace();
			}
			return false;
		}
		return true;
	}

	/**
	 * 构造全部授予的授权结果，用于无需弹窗时的立即回调。
	 */
	private static int[] buildGrantResults(int length) {
		int[] grantResults = new int[length];
		for (int i = 0; i < length; i++) {
			grantResults[i] = PackageManager.PERMISSION_GRANTED;
		}
		return grantResults;
	}

	public static PageUiController getWebForgeUIControllerByWebView(WebView webView) {
		WebParentLayout mWebParentLayout = getWebParentLayoutByWebView(webView);
		return mWebParentLayout.getController();
	}

	//获取应用的名称
	public static String getApplicationName(Context context) {
		PackageManager packageManager = null;
		ApplicationInfo applicationInfo = null;
		try {
			packageManager = context.getApplicationContext().getPackageManager();
			applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
		} catch (PackageManager.NameNotFoundException e) {
			applicationInfo = null;
		}
		String applicationName =
				(String) packageManager.getApplicationLabel(applicationInfo);
		return applicationName;
	}

	static WebParentLayout getWebParentLayoutByWebView(WebView webView) {
		ViewParent parent = webView.getParent();
		while (parent instanceof ViewGroup) {
			if (parent instanceof WebParentLayout) {
				return (WebParentLayout) parent;
			}
			parent = parent.getParent();
		}
		throw new IllegalStateException("WebView is outside its WebPage layout");
	}

	public static void runInUiThread(Runnable runnable) {
		if (mHandler == null) {
			mHandler = new Handler(Looper.getMainLooper());
		}
		mHandler.post(runnable);
	}

	@android.annotation.SuppressLint("NewApi")
	public static boolean showFileChooserCompat(Activity activity,
	                                     WebView webView,
	                                     ValueCallback<Uri[]> valueCallbacks,
	                                     WebChromeClient.FileChooserParams fileChooserParams,
	                                     PermissionInterceptor permissionInterceptor,
	                                     ValueCallback valueCallback,
	                                     String mimeType,
	                                     Handler.Callback jsChannelCallback
	) {
		try {
            // 文件选择器已内置，直接调用避免反射开销和宿主混淆后的类名失效。
            FileChooser.Builder builder = FileChooser.newBuilder(activity, webView)
                    .setPermissionInterceptor(permissionInterceptor);
            if (valueCallbacks != null) {
                builder.setUriValueCallbacks(valueCallbacks);
            }
            if (fileChooserParams != null) {
                builder.setFileChooserParams(fileChooserParams);
            }
            if (valueCallback != null) {
                builder.setUriValueCallback(valueCallback);
            }
            if (!TextUtils.isEmpty(mimeType)) {
                builder.setAcceptType(mimeType);
            }
            if (jsChannelCallback != null) {
                builder.setJsChannelCallback(jsChannelCallback);
            }
            builder.build().openFileChooser();
        } catch (RuntimeException error) {
            if (LogUtils.isDebug()) {
                error.printStackTrace();
            }
            if (valueCallbacks != null) {
                return false;
            }
            if (valueCallback != null) {
                valueCallback.onReceiveValue(null);
            }
        }
        return true;
    }

}
