
package io.github.sunwei.webforge.ui;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import io.github.sunwei.webforge.R;
import io.github.sunwei.webforge.permission.WebForgeActionFragment;
import io.github.sunwei.webforge.util.LogUtils;
import io.github.sunwei.webforge.util.WebForgeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 */
public class DefaultUIController extends AbsWebForgeUIController {

	private AlertDialog mAlertDialog;
	protected AlertDialog mConfirmDialog;
	private JsPromptResult mJsPromptResult = null;
	private JsResult mJsResult = null;
	private AlertDialog mPromptDialog = null;
	private Activity mActivity;
	private WebParentLayout mWebParentLayout;
	private AlertDialog mAskOpenOtherAppDialog = null;
	private ProgressDialog mProgressDialog;
	private Resources mResources = null;

	@Override
	public void onJsAlert(WebView view, String url, String message) {
		WebForgeUtils.toastShowShort(view.getContext().getApplicationContext(), message);
	}

	@Override
	public void onOpenPagePrompt(WebView view, String url, final Handler.Callback callback) {
		LogUtils.i(TAG, "onOpenPagePrompt");
		Activity mActivity;
		if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mActivity.isDestroyed()) {
				return;
			}
		}
		if (mAskOpenOtherAppDialog == null) {
			mAskOpenOtherAppDialog = new AlertDialog
					.Builder(mActivity)
					.setMessage(mResources.getString(R.string.webforge_leave_app_and_go_other_page,
							WebForgeUtils.getApplicationName(mActivity)))
					.setTitle(mResources.getString(R.string.webforge_tips))
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (callback != null) {
								callback.handleMessage(Message.obtain(null, -1));
							}
						}
					})//
					.setPositiveButton(mResources.getString(R.string.webforge_leave), new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (callback != null) {
								callback.handleMessage(Message.obtain(null, 1));
							}
						}
					})
					.create();
		}
		mAskOpenOtherAppDialog.show();
	}

	@Override
	public void onJsConfirm(WebView view, String url, String message, JsResult jsResult) {
		onJsConfirmInternal(message, jsResult);
	}

	@Override
	public void onSelectItemsPrompt(WebView view, String url, final String[] ways, final Handler.Callback callback) {
		showChooserInternal(ways, callback);
	}

	@Override
	public void onForceDownloadAlert(String url, final Handler.Callback callback) {
		onForceDownloadAlertInternal(callback);
	}

	private void onForceDownloadAlertInternal(final Handler.Callback callback) {
		Activity mActivity;
		if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mActivity.isDestroyed()) {
				return;
			}
		}
		AlertDialog mAlertDialog = null;
		mAlertDialog = new AlertDialog.Builder(mActivity)
				.setTitle(mResources.getString(R.string.webforge_tips))
				.setMessage(mResources.getString(R.string.webforge_honeycomblow))
				.setNegativeButton(mResources.getString(R.string.webforge_download), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (dialog != null) {
							dialog.dismiss();
						}
						if (callback != null) {
							callback.handleMessage(Message.obtain());
						}
					}
				})//
				.setPositiveButton(mResources.getString(R.string.webforge_cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						if (dialog != null) {
							dialog.dismiss();
						}
					}
				}).create();
		mAlertDialog.show();
	}

	private void showChooserInternal(String[] ways, final Handler.Callback callback) {
		Activity mActivity;
		if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mActivity.isDestroyed()) {
				return;
			}
		}
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.cancel();
		}
		final AlertDialog dialog = new AlertDialog.Builder(mActivity).create();
		final WebForgeChooserPanel chooserPanel = new WebForgeChooserPanel(
				mActivity,
				ways,
				callback,
				new Runnable() {
					@Override
					public void run() {
						dialog.dismiss();
					}
				});
		dialog.setView(chooserPanel.getContentView());
		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						chooserPanel.cancel();
					}
				});
		mAlertDialog = dialog;
		dialog.show();
	}

	private void onJsConfirmInternal(String message, JsResult jsResult) {
		LogUtils.i(TAG, "activity:" + mActivity.hashCode() + "  ");
		Activity mActivity = this.mActivity;
		if (mActivity == null || mActivity.isFinishing()) {
			toCancelJsresult(jsResult);
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mActivity.isDestroyed()) {
				toCancelJsresult(jsResult);
				return;
			}
		}

		if (mConfirmDialog == null) {
			mConfirmDialog = new AlertDialog.Builder(mActivity)
					.setMessage(message)
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							toDismissDialog(mConfirmDialog);
							toCancelJsresult(mJsResult);
						}
					})//
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							toDismissDialog(mConfirmDialog);
							if (mJsResult != null) {
								mJsResult.confirm();
							}

						}
					})
					.setOnCancelListener(new DialogInterface.OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							dialog.dismiss();
							toCancelJsresult(mJsResult);
						}
					})
					.create();

		}
		mConfirmDialog.setMessage(message);
		this.mJsResult = jsResult;
		mConfirmDialog.show();
	}


	private void onJsPromptInternal(String message, String defaultValue, JsPromptResult jsPromptResult) {
		Activity mActivity = this.mActivity;
		if (mActivity == null || mActivity.isFinishing()) {
			jsPromptResult.cancel();
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mActivity.isDestroyed()) {
				jsPromptResult.cancel();
				return;
			}
		}
		if (mPromptDialog == null) {
			final EditText et = new EditText(mActivity);
			et.setText(defaultValue);
			mPromptDialog = new AlertDialog.Builder(mActivity)
					.setView(et)
					.setTitle(message)
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							toDismissDialog(mPromptDialog);
							toCancelJsresult(mJsPromptResult);
						}
					})//
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							toDismissDialog(mPromptDialog);

							if (mJsPromptResult != null) {
								mJsPromptResult.confirm(et.getText().toString());
							}

						}
					})
					.setOnCancelListener(new DialogInterface.OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							dialog.dismiss();
							toCancelJsresult(mJsPromptResult);
						}
					})
					.create();
		}
		this.mJsPromptResult = jsPromptResult;
		mPromptDialog.show();
	}

	@Override
	public void onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult jsPromptResult) {
		onJsPromptInternal(message, defaultValue, jsPromptResult);
	}

	@Override
	public void onMainFrameError(WebView view, int errorCode, String description, String failingUrl) {

		LogUtils.i(TAG, "mWebParentLayout onMainFrameError:" + mWebParentLayout);
		if (mWebParentLayout != null) {
			mWebParentLayout.showPageMainFrameError();
		}
	}

	@Override
	public void onShowMainFrame() {
		if (mWebParentLayout != null) {
			mWebParentLayout.hideErrorLayout();
		}
	}

	@Override
	public void onLoading(String msg) {
		Activity mActivity;
		if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mActivity.isDestroyed()) {
				return;
			}
		}
		if (mProgressDialog == null) {
			mProgressDialog = new ProgressDialog(mActivity);
		}
		mProgressDialog.setCancelable(false);
		mProgressDialog.setCanceledOnTouchOutside(false);
		mProgressDialog.setMessage(msg);
		mProgressDialog.show();

	}

	@Override
	public void onCancelLoading() {
		Activity mActivity;
		if ((mActivity = this.mActivity) == null || mActivity.isFinishing()) {
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			if (mActivity.isDestroyed()) {
				return;
			}
		}
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
		mProgressDialog = null;
	}

	@Override
	public void onShowMessage(String message, String from) {
		if (!TextUtils.isEmpty(from) && from.contains("performDownload")) {
			return;
		}
		WebForgeUtils.toastShowShort(mActivity.getApplicationContext(), message);
	}

	@Override
	public void onPermissionsDeny(String[] permissions, String permissionType, String action) {
//		WebForgeUtils.toastShowShort(mActivity.getApplicationContext(), "权限被冻结");
	}

	@Override
	public void onShowSslCertificateErrorDialog(final WebView view, final SslErrorHandler handler, final SslError error) {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(mActivity);
		String sslErrorMessage;
		switch (error.getPrimaryError()) {
			case SslError.SSL_UNTRUSTED:
				sslErrorMessage = mActivity.getString(R.string.webforge_message_show_ssl_untrusted);
				break;
			case SslError.SSL_EXPIRED:
				sslErrorMessage = mActivity.getString(R.string.webforge_message_show_ssl_expired);
				break;
			case SslError.SSL_IDMISMATCH:
				sslErrorMessage = mActivity.getString(R.string.webforge_message_show_ssl_hostname_mismatch);
				break;
			case SslError.SSL_NOTYETVALID:
				sslErrorMessage = mActivity.getString(R.string.webforge_message_show_ssl_not_yet_valid);
				break;
			default:
				sslErrorMessage = mActivity.getString(R.string.webforge_message_show_ssl_error);
		}
		sslErrorMessage += mActivity.getString(R.string.webforge_message_show_continue);
		alertDialog.setTitle(mActivity.getString(R.string.webforge_title_ssl_error));
		alertDialog.setMessage(sslErrorMessage);
		alertDialog.setPositiveButton(R.string.webforge_continue, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Ignore SSL certificate errors
				handler.proceed();
			}
		});

		alertDialog.setNegativeButton(R.string.webforge_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				handler.cancel();
			}
		});
		alertDialog.show();


	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onPermissionRequest(final PermissionRequest request) {
		if (request == null) {
			return;
		}
		final String[] resources = request.getResources();
		if (resources == null || resources.length <= 0) {
			request.deny();
			return;
		}
		final Activity activity = mActivity;
		if (activity == null || activity.isFinishing()) {
			request.deny();
			return;
		}
		Set<String> resourcesSet = new HashSet<>(Arrays.asList(resources));
		ArrayList<String> allowedResources = new ArrayList<>(2);
		ArrayList<String> permissions = new ArrayList<>(2);
		if (resourcesSet.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
			allowedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
			permissions.add(Manifest.permission.CAMERA);
		}
		if (resourcesSet.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
			allowedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
			permissions.add(Manifest.permission.RECORD_AUDIO);
		}
		if (allowedResources.isEmpty()) {
			request.deny();
			return;
		}

		final String[] grantedResources = allowedResources.toArray(new String[]{});
		final String[] requestPermissions = permissions.toArray(new String[]{});
		//已经授权 或者 6.0 以下无需授权时，requestPermissions 会立即回调，直接放行
		boolean requesting = WebForgeUtils.requestPermissions(activity, requestPermissions,
				new WebForgeActionFragment.PermissionListener() {
					@Override
					public void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults, Bundle extras) {
						if (WebForgeUtils.getDeniedPermissions(activity, permissions).isEmpty()) {
							request.grant(grantedResources);
						} else {
							request.deny();
						}
					}
				});
		if (requesting) {
			return;
		}
		//无法发起权限申请（例如不是 FragmentActivity），按当前授权状态处理
		if (WebForgeUtils.getDeniedPermissions(activity, requestPermissions).isEmpty()) {
			request.grant(grantedResources);
		} else {
			request.deny();
		}
	}

	private void toCancelJsresult(JsResult result) {
		if (result != null) {
			result.cancel();
		}
	}


	@Override
	protected void bindSupportWebParent(WebParentLayout webParentLayout, Activity activity) {
		this.mActivity = activity;
		this.mWebParentLayout = webParentLayout;
		mResources = this.mActivity.getResources();

	}
}
