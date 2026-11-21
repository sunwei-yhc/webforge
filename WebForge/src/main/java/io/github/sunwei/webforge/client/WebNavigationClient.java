
package io.github.sunwei.webforge.client;

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
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import io.github.sunwei.webforge.config.ExternalLinks;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.github.sunwei.webforge.config.WebForgeConfig;
import io.github.sunwei.webforge.permission.WebForgeActionFragment;
import io.github.sunwei.webforge.permission.WebForgePermissions;
import io.github.sunwei.webforge.ui.PageUiController;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.lang.ref.WeakReference;
import java.net.URISyntaxException;

/** 处理系统链接、主框架错误和渲染器退出。 */
public final class WebNavigationClient extends WebViewClient {
    private static final String TAG = "WebForgeNavigation";
    private static volatile boolean sCallNeedConfirm;

    private final WeakReference<Activity> mWeakReference;
    private final PageUiController ui;
    private final ExternalLinks externalLinks;
    private final boolean blockUnknownSchemes;
    private final Runnable rendererGone;
    private String activeUrl;
    private boolean failed;

    public WebNavigationClient(Activity activity, WebView view, ExternalLinks externalLinks,
                            boolean blockUnknownSchemes, Runnable rendererGone) {
        mWeakReference = new WeakReference<>(activity);
        ui = WebForgeUtils.getWebForgeUIControllerByWebView(view);
        this.externalLinks = externalLinks;
        this.blockUnknownSchemes = blockUnknownSchemes;
        this.rendererGone = rendererGone;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.O)
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        rendererGone.run();
        return true;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.N)
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return request.isForMainFrame() && handleUrl(view, request.getUrl().toString());
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrl(view, url);
    }

    private boolean handleUrl(WebView view, String url) {
        String scheme = Uri.parse(url).getScheme();
        if (scheme == null || "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)
                || "file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)
                || "about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)
                || "blob".equalsIgnoreCase(scheme) || "javascript".equalsIgnoreCase(scheme)) {
            return false;
        }
        if (externalLinks == ExternalLinks.BLOCK) {
            return true;
        }
        if (handleCommonLink(url)) {
            return true;
        }
        if (url.startsWith("intent://")) {
            lookup(url);
            return true;
        }
        if (url.startsWith("weixin://wap/pay?")) {
            return openUri(url, Intent.ACTION_VIEW) || blockUnknownSchemes;
        }
        if (url.startsWith("alipays://") && lookup(url)) {
            return true;
        }
        if (url.startsWith("market://")) {
            if (externalLinks == ExternalLinks.OPEN) {
                return lookup(url) || blockUnknownSchemes;
            }
            ResolveInfo info = lookupResolveInfo(url);
            if (info != null && ui != null) {
                Activity activity = mWeakReference.get();
                ActivityInfo target = info.activityInfo;
                if (activity != null && target != null
                        && activity.getPackageName().equals(target.packageName)) {
                    return lookup(url);
                }
                ui.onOpenPagePrompt(view, view.getUrl(), getOpenPageCallback(url));
                return true;
            }
        }
        return blockUnknownSchemes;
    }

    private static Intent externalIntent(String url) throws URISyntaxException {
        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        intent.setComponent(null);
        intent.setSelector(null);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        return intent;
    }

	private ResolveInfo lookupResolveInfo(String url) {
		try {
			Intent intent;
			Activity mActivity = null;
			if ((mActivity = mWeakReference.get()) == null) {
				return null;
			}
			PackageManager packageManager = mActivity.getPackageManager();
			intent = externalIntent(url);
			ResolveInfo info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
			return info;
		} catch (RuntimeException | URISyntaxException ignore) {
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
			intent = externalIntent(url);
			ResolveInfo info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
			// 跳到该应用
			if (info != null) {
				mActivity.startActivity(intent);
				return true;
			}
		} catch (RuntimeException | URISyntaxException ignore) {
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
		if (url.startsWith("sms:")
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
		} catch (RuntimeException ignored) {
			if (WebForgeConfig.DEBUG) {
				ignored.printStackTrace();
			}
			return false;
		}
	}

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        activeUrl = url;
        failed = false;
    }

    @Override
    @android.annotation.SuppressLint("WebViewClientOnReceivedSslError")
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        if (WebForgeConfig.isAcceptSslCertificateErrors()) {
            handler.proceed();
        } else {
            handler.cancel();
        }
    }

    @Override
    public void onReceivedError(WebView view, int code, String description, String url) {
        showError(view, code, description, url);
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.M)
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request.isForMainFrame()) {
            showError(view, error.getErrorCode(), error.getDescription().toString(),
                    request.getUrl().toString());
        }
    }

    private void showError(WebView view, int code, String description, String url) {
        if (activeUrl != null && !TextUtils.equals(activeUrl, url)
                && !TextUtils.equals(view.getUrl(), url)) {
            return;
        }
        failed = true;
        if (ui != null) {
            ui.onMainFrameError(view, code, description, url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (!failed && TextUtils.equals(activeUrl, url) && ui != null) {
            ui.onShowMainFrame();
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

	private Handler.Callback getOpenPageCallback(String url) {
		return message -> {
			if (message.what == 1) {
				lookup(url);
			}
			return true;
		};
	}

}
