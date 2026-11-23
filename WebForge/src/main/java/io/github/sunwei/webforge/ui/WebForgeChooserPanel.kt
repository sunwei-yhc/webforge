package io.github.sunwei.webforge.ui

import android.app.Activity
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import io.github.sunwei.webforge.R

/**
 * Shared camera/file chooser content for both the Material bottom sheet and
 * the AppCompat fallback dialog.
 */
internal class WebForgeChooserPanel(
    activity: Activity, ways: Array<String>, private val mCallback: Handler.Callback?,
    private val mDismissAction: Runnable?
) {
    val contentView: View
    private var mResultDispatched = false

    init {
        this.contentView = LayoutInflater.from(activity)
            .inflate(R.layout.webforge_chooser_sheet, null, false)

        val cameraLabel: String? = getWay(
            ways, 0,
            activity.getString(R.string.webforge_camera)
        )
        val fileLabel: String? = getWay(
            ways, 1,
            activity.getString(R.string.webforge_file_chooser)
        )
        val cameraDescription =
            activity.getString(R.string.webforge_chooser_camera_desc)
        val fileDescription =
            activity.getString(R.string.webforge_chooser_file_desc)

        val cameraTitle = contentView.findViewById<TextView>(
            R.id.webforge_chooser_camera_title
        )
        val fileTitle = contentView.findViewById<TextView>(
            R.id.webforge_chooser_file_title
        )
        cameraTitle.setText(cameraLabel)
        fileTitle.setText(fileLabel)

        val cameraOption = contentView.findViewById<View>(
            R.id.webforge_chooser_camera_option
        )
        val fileOption = contentView.findViewById<View>(
            R.id.webforge_chooser_file_option
        )
        cameraOption.setContentDescription(cameraLabel + "，" + cameraDescription)
        fileOption.setContentDescription(fileLabel + "，" + fileDescription)

        cameraOption.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                select(0)
            }
        })
        fileOption.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                select(1)
            }
        })
        contentView.findViewById<View?>(R.id.webforge_chooser_cancel)
            .setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    dispatch(-1)
                    dismiss()
                }
            })
    }

    fun cancel() {
        dispatch(-1)
    }

    private fun select(which: Int) {
        dispatch(which)
        dismiss()
    }

    private fun dismiss() {
        if (mDismissAction != null) {
            mDismissAction.run()
        }
    }

    private fun dispatch(which: Int) {
        if (mResultDispatched) {
            return
        }
        mResultDispatched = true
        if (mCallback != null) {
            mCallback.handleMessage(Message.obtain(null, which))
        }
    }

    companion object {
        private fun getWay(ways: Array<String>, index: Int, fallback: String?): String? {
            if (ways.size <= index || TextUtils.isEmpty(ways[index])) {
                return fallback
            }
            return ways[index]
        }
    }
}
