
package io.github.sunwei.webforge.client;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.permission.WebForgeActionFragment;
import io.github.sunwei.webforge.permission.WebForgePermissions;
import io.github.sunwei.webforge.ui.AbsWebForgeUIController;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 */
public class DefaultWebClient extends MiddlewareWebClientBase {
	/**
	 * Activity's WeakReference
	 */
	private WeakReference<Activity> mWeakReference = null;
	/**
	 * 缩放
	 */
	private static final int CONSTANTS_ABNORMAL_BIG = 7;
	/**
	 * WebViewClient
	 */
	private WebViewClient mWebViewClient;
	/**
	 * mWebClientHelper
	 */
	private boolean webClientHelper = true;
	/**
	 * 通话是否需要用户二次确认，true 表示授权后只打开系统拨号盘，由用户点击拨打。
	 * 详见 {@link #setCallNeedConfirm(boolean)}
	 */
	private static boolean sCallNeedConfirm = false;
	/**
	 * intent ' s scheme
	 */
	public static final String INTENT_SCHEME = "intent://";
	/**
	 * Wechat pay scheme ，用于唤醒微信支付
	 */
	public static final String WEBCHAT_PAY_SCHEME = "weixin://wap/pay?";
	/**
	 * 支付宝
	 */
	public static final String ALIPAYS_SCHEME = "alipays://";
	/**
	 * http scheme
	 */
	public static final String HTTP_SCHEME = "http://";
	/**
	 * https scheme
	 */
	public static final String HTTPS_SCHEME = "https://";
	/**
	 * WebViewClient's tag 用于打印
	 */
	private static final String TAG = DefaultWebClient.class.getSimpleName();
	/**
	 * 直接打开其他页面
	 */
	public static final int DERECT_OPEN_OTHER_PAGE = 1001;
	/**
	 * 弹窗咨询用户是否前往其他页面
	 */
	public static final int ASK_USER_OPEN_OTHER_PAGE = DERECT_OPEN_OTHER_PAGE >> 2;
	/**
	 * 不允许打开其他页面
	 */
	public static final int DISALLOW_OPEN_OTHER_APP = DERECT_OPEN_OTHER_PAGE >> 4;
	/**
	 * 默认为咨询用户
	 */
	private int mUrlHandleWays = ASK_USER_OPEN_OTHER_PAGE;
	/**
	 * 是否拦截找不到相应页面的Url，默认拦截
	 */
	private boolean mInterceptUnknownUrl = true;
	/**
	 * AbsWebForgeUIController
	 */
	private WeakReference<AbsWebForgeUIController> mWebForgeUIController = null;
	/**
	 * WebView
	 */
	private WebView mWebView;
	/**
	 * MainFrameErrorMethod
	 */
	private Method onMainFrameErrorMethod = null;
	/**
	 * SMS scheme
	 */
	public static final String SCHEME_SMS = "sms:";
	/**
	 * 缓存当前出现错误的页面
	 */
	private Set<String> mErrorUrlsSet = new HashSet<>();
	/**
	 * 缓存等待加载完成的页面 onPageStart()执行之后 ，onPageFinished()执行之前
	 */
	private Set<String> mWaittingFinishSet = new HashSet<>();

	DefaultWebClient(Builder builder) {
		super(builder.mClient);
		this.mWebView = builder.mWebView;
		this.mWebViewClient = builder.mClient;
		mWeakReference = new WeakReference<Activity>(builder.mActivity);
		this.webClientHelper = builder.mWebClientHelper;
		mWebForgeUIController = new WeakReference<AbsWebForgeUIController>(WebForgeUtils.getWebForgeUIControllerByWebView(builder.mWebView));
		mInterceptUnknownUrl = builder.mInterceptUnknownUrl;
		if (builder.mUrlHandleWays <= 0) {
			mUrlHandleWays = ASK_USER_OPEN_OTHER_PAGE;
		} else {
			mUrlHandleWays = builder.mUrlHandleWays;
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.N)
	@Override
	public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
		return handleUrl(request.getUrl().toString()) || super.shouldOverrideUrlLoading(view, request);
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		return handleUrl(url) || super.shouldOverrideUrlLoading(view, url);
	}

	private boolean handleUrl(String url) {
		if (handleCommonLink(url)) {
			return true;
		}
		if (url.startsWith(INTENT_SCHEME)) {
			lookup(url);
			return true;
		}
		if (url.startsWith(WEBCHAT_PAY_SCHEME)) {
			startActivity(url);
			return true;
		}
		if (url.startsWith(ALIPAYS_SCHEME) && lookup(url)) {
			return true;
		}
		if (url.startsWith("market://") && queryActivitiesNumber(url) > 0 && deepLink(url)) {
			return true;
		}
		return mInterceptUnknownUrl;
	}

	private boolean deepLink(String url) {
		switch (mUrlHandleWays) {
			// 直接打开其他App
			case DERECT_OPEN_OTHER_PAGE:
				lookup(url);
				return true;
			// 咨询用户是否打开其他App
			case ASK_USER_OPEN_OTHER_PAGE:
				Activity mActivity = null;
				if ((mActivity = mWeakReference.get()) == null) {
					return false;
				}
				ResolveInfo resolveInfo = lookupResolveInfo(url);
				if (null == resolveInfo) {
					return false;
				}
				ActivityInfo activityInfo = resolveInfo.activityInfo;
				LogUtils.e(TAG, "resolve package:" + resolveInfo.activityInfo.packageName + " app package:" + mActivity.getPackageName());
				if (activityInfo != null
						&& !TextUtils.isEmpty(activityInfo.packageName)
						&& activityInfo.packageName.equals(mActivity.getPackageName())) {
					return lookup(url);
				}
				if (mWebForgeUIController.get() != null) {
					mWebForgeUIController.get()
							.onOpenPagePrompt(this.mWebView,
									mWebView.getUrl(),
									getOpenPageCallback(url));
				}
				return true;
			// 默认不打开
			default:
				return false;
		}
	}

	private int queryActivitiesNumber(String url) {
		try {
			if (mWeakReference.get() == null) {
				return 0;
			}
			Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
			PackageManager mPackageManager = mWeakReference.get().getPackageManager();
			List<ResolveInfo> mResolveInfos = mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
			return mResolveInfos == null ? 0 : mResolveInfos.size();
		} catch (URISyntaxException ignore) {
			if (LogUtils.isDebug()) {
				ignore.printStackTrace();
			}
			return 0;
		}
	}

	private ResolveInfo lookupResolveInfo(String url) {
		try {
			Intent intent;
			Activity mActivity = null;
			if ((mActivity = mWeakReference.get()) == null) {
				return null;
			}
			PackageManager packageManager = mActivity.getPackageManager();
			intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
			ResolveInfo info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
			return info;
		} catch (Throwable ignore) {
			if (LogUtils.isDebug()) {
				ignore.printStackTrace();
			}
		}
		return null;
	}

	private boolean lookup(String url) {
		try {
			Intent intent;
			Activity mActivity = null;
			if ((mActivity = mWeakReference.get()) == null) {
				return true;
			}
			PackageManager packageManager = mActivity.getPackageManager();
			intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
			ResolveInfo info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
			// 跳到该应用
			if (info != null) {
				mActivity.startActivity(intent);
				return true;
			}
		} catch (Throwable ignore) {
			if (LogUtils.isDebug()) {
				ignore.printStackTrace();
			}
		}
		return false;
	}

	private boolean handleCommonLink(String url) {
		//拨打电话 ， 需要通话权限 ， 未授权时主动申请
		if (url.startsWith(WebView.SCHEME_TEL)) {
			return handleTelLink(url);
		}
		//邮件 ， 短信 ， 地图
		if (url.startsWith(SCHEME_SMS)
				|| url.startsWith(WebView.SCHEME_MAILTO)
				|| url.startsWith(WebView.SCHEME_GEO)) {
			openUri(url, Intent.ACTION_VIEW);
			return true;
		}
		return false;
	}

	/**
	 * H5 使用 tel: 链接拨打电话时，先检查通话权限，没有授权则主动申请。
	 */
	private boolean handleTelLink(final String url) {
		final Activity activity = mWeakReference.get();
		if (activity == null) {
			return false;
		}
		if (!WebForgeUtils.hasPermission(activity, WebForgePermissions.CALL)) {
			boolean requesting = WebForgeUtils.requestPermissions(activity, WebForgePermissions.CALL,
					new WebForgeActionFragment.PermissionListener() {
						@Override
						public void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults, Bundle extras) {
							call(url);
						}
					});
			if (!requesting) {
				//无法申请权限（例如不是 FragmentActivity），退化为打开系统拨号盘
				openUri(url, Intent.ACTION_DIAL);
			}
			return true;
		}
		call(url);
		return true;
	}

	/**
	 * 拨号，授权成功后默认直接拨出，可通过 {@link #setCallNeedConfirm(boolean)} 改为只打开系统拨号盘。
	 */
	private void call(String url) {
		final Activity activity = mWeakReference.get();
		if (activity == null) {
			return;
		}
		if (!sCallNeedConfirm && WebForgeUtils.hasPermission(activity, WebForgePermissions.CALL)
				&& openUri(url, Intent.ACTION_CALL)) {
			return;
		}
		openUri(url, Intent.ACTION_DIAL);
	}

	private boolean openUri(String url, String action) {
		try {
			Activity mActivity = null;
			if ((mActivity = mWeakReference.get()) == null) {
				return false;
			}
			Intent intent = new Intent(action);
			intent.setData(Uri.parse(url));
			mActivity.startActivity(intent);
			return true;
		} catch (Throwable ignored) {
			if (WebForgeConfig.DEBUG) {
				ignored.printStackTrace();
			}
			return false;
		}
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (!mWaittingFinishSet.contains(url)) {
			mWaittingFinishSet.add(url);
		}
		super.onPageStarted(view, url, favicon);

	}

	@Override
	@android.annotation.SuppressLint("WebViewClientOnReceivedSslError")
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		if (WebForgeConfig.isAcceptSslCertificateErrors()) {
			handler.proceed();
			return;
		}
		handler.cancel();
	}


	/**
	 * MainFrame Error
	 *
	 * @param view
	 * @param errorCode
	 * @param description
	 * @param failingUrl
	 */
	@Override
	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
		LogUtils.i(TAG, "onReceivedError：" + description + "  CODE:" + errorCode);
		if (failingUrl == null && errorCode != -12) {
			return;
		}
		if (errorCode == -1) {
			return;
		}
		if (errorCode != ERROR_HOST_LOOKUP && (failingUrl != null && !failingUrl.equals(view.getUrl()) && !failingUrl.equals(view.getOriginalUrl()))) {
			return;
		}
		onMainFrameError(view, errorCode, description, failingUrl);
	}

	@Override
	public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
		if (!mWaittingFinishSet.contains(url)) {
			mWaittingFinishSet.add(url);
		}
		super.doUpdateVisitedHistory(view, url, isReload);
	}

	@TargetApi(Build.VERSION_CODES.M)
	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
		String failingUrl = request.getUrl().toString();
		int errorCode = error.getErrorCode();
		if (!request.isForMainFrame()) {
			return;
		}
		if (failingUrl == null && errorCode != ERROR_BAD_URL) {
			return;
		}
		if (errorCode == ERROR_UNKNOWN) {
			return;
		}
		LogUtils.i(TAG, "onReceivedError:" + error.getDescription() + " code:" + error.getErrorCode() + " failingUrl:" + failingUrl + " getUrl:" + view.getUrl() + " getOriginalUrl:" + view.getOriginalUrl());
		if (errorCode != ERROR_HOST_LOOKUP &&
				(failingUrl != null && !failingUrl.equals(view.getUrl()) && !failingUrl.equals(view.getOriginalUrl()))) {
			return;
		}
		onMainFrameError(view,
				error.getErrorCode(), error.getDescription().toString(),
				request.getUrl().toString());

	}

	private void onMainFrameError(WebView view, int errorCode, String description, String failingUrl) {
		mErrorUrlsSet.add(failingUrl);
		// 下面逻辑判断开发者是否重写了 onMainFrameError 方法 ， 优先交给开发者处理
		if (this.mWebViewClient != null && webClientHelper) {
			Method mMethod = this.onMainFrameErrorMethod;
			if (mMethod != null || (this.onMainFrameErrorMethod = mMethod = WebForgeUtils.isExistMethod(mWebViewClient, "onMainFrameError", AbsWebForgeUIController.class, WebView.class, int.class, String.class, String.class)) != null) {
				try {
					mMethod.invoke(this.mWebViewClient, mWebForgeUIController.get(), view, errorCode, description, failingUrl);
				} catch (Throwable ignore) {
					if (LogUtils.isDebug()) {
						ignore.printStackTrace();
					}
				}
				return;
			}
		}
		if (mWebForgeUIController.get() != null) {
			mWebForgeUIController.get().onMainFrameError(view, errorCode, description, failingUrl);
		}
//        this.mWebView.setVisibility(View.GONE);
	}


	@Override
	public void onPageFinished(WebView view, String url) {
		if (!mErrorUrlsSet.contains(url) && mWaittingFinishSet.contains(url)) {
			if (mWebForgeUIController.get() != null) {
				mWebForgeUIController.get().onShowMainFrame();
			}
		} else {
			view.setVisibility(View.VISIBLE);
		}
		if (mWaittingFinishSet.contains(url)) {
			mWaittingFinishSet.remove(url);
		}
		if (!mErrorUrlsSet.isEmpty()) {
			mErrorUrlsSet.clear();
		}
		super.onPageFinished(view, url);
	}


	private void startActivity(String url) {
		try {
			if (mWeakReference.get() == null) {
				return;
			}
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_VIEW);
			intent.setData(Uri.parse(url));
			mWeakReference.get().startActivity(intent);

		} catch (Exception e) {
			if (LogUtils.isDebug()) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 设置 H5 使用 tel: 拨打电话时是否需要用户二次确认。
	 *
	 * @param callNeedConfirm true 授权后只打开系统拨号盘（用户手动点击拨打）；false（默认）授权后直接拨出
	 */
	public static void setCallNeedConfirm(boolean callNeedConfirm) {
		sCallNeedConfirm = callNeedConfirm;
	}

	@Override
	public void onScaleChanged(WebView view, float oldScale, float newScale) {
		LogUtils.i(TAG, "onScaleChanged:" + oldScale + "   n:" + newScale);
		if (newScale - oldScale > CONSTANTS_ABNORMAL_BIG) {
			view.setInitialScale((int) (oldScale / newScale * 100));
		}
	}

	private Handler.Callback getOpenPageCallback(String url) {
		return message -> {
			if (message.what == 1) {
				lookup(url);
			}
			return true;
		};
	}

	public static Builder createBuilder() {
		return new Builder();
	}

	public static class Builder {
		private Activity mActivity;
		private WebViewClient mClient;
		private boolean mWebClientHelper;
		private WebView mWebView;
		private boolean mInterceptUnknownUrl = true;
		private int mUrlHandleWays;

		public Builder setActivity(Activity activity) {
			this.mActivity = activity;
			return this;
		}

		public Builder setClient(WebViewClient client) {
			this.mClient = client;
			return this;
		}

		public Builder setWebClientHelper(boolean webClientHelper) {
			this.mWebClientHelper = webClientHelper;
			return this;
		}

		public Builder setWebView(WebView webView) {
			this.mWebView = webView;
			return this;
		}

		public Builder setInterceptUnknownUrl(boolean enabled) {
			this.mInterceptUnknownUrl = enabled;
			return this;
		}

		/** @deprecated Use {@link #setInterceptUnknownUrl(boolean)}. */
		@Deprecated
		public Builder setInterceptUnkownUrl(boolean enabled) {
			return setInterceptUnknownUrl(enabled);
		}

		public Builder setUrlHandleWays(int urlHandleWays) {
			this.mUrlHandleWays = urlHandleWays;
			return this;
		}

		public DefaultWebClient build() {
			return new DefaultWebClient(this);
		}
	}

	public static enum OpenOtherPageWays {
		/**
		 * 直接打开跳转页
		 */
		DERECT(DefaultWebClient.DERECT_OPEN_OTHER_PAGE),
		/**
		 * 咨询用户是否打开
		 */
		ASK(DefaultWebClient.ASK_USER_OPEN_OTHER_PAGE),
		/**
		 * 禁止打开其他页面
		 */
		DISALLOW(DefaultWebClient.DISALLOW_OPEN_OTHER_APP);
		private final int code;

		public int getCode() {
			return code;
		}

		OpenOtherPageWays(int code) {
			this.code = code;
		}
	}
}
