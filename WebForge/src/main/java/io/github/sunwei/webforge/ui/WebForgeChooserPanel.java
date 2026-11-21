
package io.github.sunwei.webforge.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import io.github.sunwei.webforge.R;

/**
 * Shared camera/file chooser content for both the Material bottom sheet and
 * the AppCompat fallback dialog.
 */
final class WebForgeChooserPanel {

    private final Handler.Callback mCallback;
    private final Runnable mDismissAction;
    private final View mContentView;
    private boolean mResultDispatched;

    WebForgeChooserPanel(Activity activity, String[] ways, Handler.Callback callback,
                         Runnable dismissAction) {
        mCallback = callback;
        mDismissAction = dismissAction;
        mContentView = LayoutInflater.from(activity)
                .inflate(R.layout.webforge_chooser_sheet, null, false);

        String cameraLabel = getWay(ways, 0,
                activity.getString(R.string.webforge_camera));
        String fileLabel = getWay(ways, 1,
                activity.getString(R.string.webforge_file_chooser));
        String cameraDescription =
                activity.getString(R.string.webforge_chooser_camera_desc);
        String fileDescription =
                activity.getString(R.string.webforge_chooser_file_desc);

        TextView cameraTitle = mContentView.findViewById(
                R.id.webforge_chooser_camera_title);
        TextView fileTitle = mContentView.findViewById(
                R.id.webforge_chooser_file_title);
        cameraTitle.setText(cameraLabel);
        fileTitle.setText(fileLabel);

        View cameraOption = mContentView.findViewById(
                R.id.webforge_chooser_camera_option);
        View fileOption = mContentView.findViewById(
                R.id.webforge_chooser_file_option);
        cameraOption.setContentDescription(cameraLabel + "，" + cameraDescription);
        fileOption.setContentDescription(fileLabel + "，" + fileDescription);

        cameraOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(0);
            }
        });
        fileOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(1);
            }
        });
        mContentView.findViewById(R.id.webforge_chooser_cancel)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dispatch(-1);
                        dismiss();
                    }
                });
    }

    View getContentView() {
        return mContentView;
    }

    void cancel() {
        dispatch(-1);
    }

    private void select(int which) {
        dispatch(which);
        dismiss();
    }

    private void dismiss() {
        if (mDismissAction != null) {
            mDismissAction.run();
        }
    }

    private void dispatch(int which) {
        if (mResultDispatched) {
            return;
        }
        mResultDispatched = true;
        if (mCallback != null) {
            mCallback.handleMessage(Message.obtain(null, which));
        }
    }

    private static String getWay(String[] ways, int index, String fallback) {
        if (ways == null || ways.length <= index || TextUtils.isEmpty(ways[index])) {
            return fallback;
        }
        return ways[index];
    }
}
