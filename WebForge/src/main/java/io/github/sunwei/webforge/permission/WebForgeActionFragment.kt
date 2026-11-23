/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.sunwei.webforge.permission

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.View
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.sunwei.webforge.util.LogUtils
import io.github.sunwei.webforge.util.WebForgeUtils

/**
 */
class WebForgeActionFragment : Fragment() {
    private var mAction: Action? = null
    private fun resetAction() {
//        mAction = null;
    }

    private var isViewCreated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            LogUtils.i(TAG, "savedInstanceState:" + savedInstanceState)
            return
        }
        isViewCreated = true
        runAction()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun runAction() {
        if (mAction == null) {
            resetAction()
            return
        }
        if (mAction!!.action == Action.ACTION_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermission(mAction!!)
            } else {
                resetAction()
            }
        } else if (mAction!!.action == Action.ACTION_CAMERA) {
            captureCamera()
        } else if (mAction!!.action == Action.ACTION_VIDEO) {
            recordVideo()
        } else {
            choose()
        }
    }


    private fun choose() {
        try {
            if (mAction!!.chooserListener == null) {
                return
            }
            val mIntent = mAction!!.intent
            if (mIntent == null) {
                resetAction()
                return
            }
            this.startActivityForResult(mIntent, REQUEST_CODE)
        } catch (throwable: Throwable) {
            LogUtils.i(TAG, "找不到文件选择器")
            chooserActionCallback(-1, null)
            if (LogUtils.isDebug()) {
                throwable.printStackTrace()
            }
        }
    }

    private fun chooserActionCallback(resultCode: Int, data: Intent?) {
        if (mAction!!.chooserListener != null) {
            mAction!!.chooserListener!!.onChoiceResult(REQUEST_CODE, resultCode, data)
        }
        resetAction()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (mAction == null) {
            return
        }
        if (requestCode == REQUEST_CODE) {
            if (mAction!!.uri != null) {
                chooserActionCallback(resultCode, Intent().putExtra(KEY_URI, mAction!!.uri))
            } else {
                chooserActionCallback(resultCode, data)
            }
        }
        resetAction()
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun requestPermission(action: Action) {
        val permissions: MutableList<String>? = action.permissions
        if (WebForgeUtils.isEmptyCollection(permissions)) {
            resetAction()
            return
        }
        if (mAction!!.rationaleListener != null) {
            var rationale = false
            for (permission in permissions!!) {
                rationale = shouldShowRequestPermissionRationale(permission)
                if (rationale) {
                    break
                }
            }
            mAction!!.rationaleListener!!.onRationaleResult(rationale, Bundle())
            resetAction()
            return
        }
        if (mAction!!.permissionListener != null) {
            requestPermissions(permissions!!.toTypedArray(), 1)
        }
    }


    private fun captureCamera() {
        try {
            if (mAction!!.chooserListener == null) {
                resetAction()
                return
            }
            val host = activity ?: return
            val mFile = WebForgeUtils.createImageFile(host)
            if (mFile == null) {
                mAction!!.chooserListener!!.onChoiceResult(REQUEST_CODE, Activity.RESULT_CANCELED, null)
                resetAction()
                return
            }
            val intent = WebForgeUtils.getIntentCaptureCompat(host, mFile)
            // 指定开启系统相机的Action
            mAction!!.uri = intent.getParcelableExtra<Parcelable?>(MediaStore.EXTRA_OUTPUT) as Uri?
            this.startActivityForResult(intent, REQUEST_CODE)
        } catch (ignore: Throwable) {
            LogUtils.e(TAG, "找不到系统相机")
            if (mAction!!.chooserListener != null) {
                mAction!!.chooserListener!!.onChoiceResult(REQUEST_CODE, Activity.RESULT_CANCELED, null)
            }
            resetAction()
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        }
    }

    private fun recordVideo() {
        try {
            if (mAction!!.chooserListener == null) {
                resetAction()
                return
            }
            val host = activity ?: return
            val mFile = WebForgeUtils.createVideoFile(host)
            if (mFile == null) {
                mAction!!.chooserListener!!.onChoiceResult(REQUEST_CODE, Activity.RESULT_CANCELED, null)
                resetAction()
                return
            }
            val intent = WebForgeUtils.getIntentVideoCompat(host, mFile)
            // 指定开启系统相机的Action
            mAction!!.uri = intent.getParcelableExtra<Parcelable?>(MediaStore.EXTRA_OUTPUT) as Uri?
            this.startActivityForResult(intent, REQUEST_CODE)
        } catch (ignore: Throwable) {
            LogUtils.e(TAG, "找不到系统相机")
            if (mAction!!.chooserListener != null) {
                mAction!!.chooserListener!!.onChoiceResult(REQUEST_CODE, Activity.RESULT_CANCELED, null)
            }
            resetAction()
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (mAction!!.permissionListener != null) {
            val mBundle = Bundle()
            mBundle.putInt(KEY_FROM_INTENTION, mAction!!.fromIntention)
            mAction!!.permissionListener!!.onRequestPermissionsResult(permissions, grantResults, mBundle)
        }
        resetAction()
    }

    fun interface RationaleListener {
        fun onRationaleResult(showRationale: Boolean, extras: Bundle?)
    }

    fun interface PermissionListener {
        fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray, extras: Bundle?)
    }

    fun interface ChooserListener {
        fun onChoiceResult(requestCode: Int, resultCode: Int, data: Intent?)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val KEY_URI: String = "KEY_URI"
        const val KEY_FROM_INTENTION: String = "KEY_FROM_INTENTION"
        private val TAG: String = WebForgeActionFragment::class.java.getSimpleName()
        const val REQUEST_CODE: Int = 0x254
        const val FRAGMENT_TAG: String = "WebForgeActionFragment"

        @JvmStatic
        fun start(activity: Activity?, action: Action?) {
            val fragmentActivity = activity as FragmentActivity
            val fragmentManager = fragmentActivity.getSupportFragmentManager()
            var fragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG) as WebForgeActionFragment?
            if (fragment == null) {
                fragment = WebForgeActionFragment()
                fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG).commitAllowingStateLoss()
            }
            fragment.mAction = action
            if (fragment.isViewCreated) {
                fragment.runAction()
            }
        }
    }
}
