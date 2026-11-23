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
import android.provider.MediaStore
import io.github.sunwei.webforge.util.LogUtils
import io.github.sunwei.webforge.util.WebForgeUtils

/**
 */
class ActionActivity : Activity() {
    private var mAction: Action? = null
    private fun cancelAction() {
        mChooserListener = null
        mPermissionListener = null
        mRationaleListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            LogUtils.i(TAG, "savedInstanceState:" + savedInstanceState)
            return
        }
        val intent = getIntent()
        @Suppress("DEPRECATION")
        mAction = intent.getParcelableExtra<Action>(KEY_ACTION)
        if (mAction == null) {
            cancelAction()
            this.finish()
            return
        }
        if (mAction!!.action == Action.ACTION_PERMISSION) {
            permission(mAction!!)
        } else if (mAction!!.action == Action.ACTION_CAMERA) {
            realOpenCamera()
        } else if (mAction!!.action == Action.ACTION_VIDEO) {
            realOpenVideo()
        } else {
            fetchFile(mAction)
        }
    }

    private fun fetchFile(action: Action?) {
        if (mChooserListener == null) {
            finish()
        }
        realOpenFileChooser()
    }

    //打开文件
    private fun realOpenFileChooser() {
        try {
            if (mChooserListener == null) {
                finish()
                return
            }
            val mIntent = mAction?.intent
            //            mIntent.setType("image/*");
            if (mIntent == null) {
                cancelAction()
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
        if (mChooserListener != null) {
            mChooserListener!!.onChoiceResult(REQUEST_CODE, resultCode, data)
            mChooserListener = null
        }
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE) {
            chooserActionCallback(resultCode, if (mUri != null) Intent().putExtra(KEY_URI, mUri) else data)
        }
    }

    private fun permission(action: Action) {
        val permissions: MutableList<String>? = action.permissions
        if (WebForgeUtils.isEmptyCollection(permissions)) {
            mPermissionListener = null
            mRationaleListener = null
            finish()
            return
        }
        if (mRationaleListener != null) {
            var rationale = false
            for (permission in permissions!!) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    rationale = shouldShowRequestPermissionRationale(permission)
                }
                if (rationale) {
                    break
                }
            }
            mRationaleListener!!.onRationaleResult(rationale, Bundle())
            mRationaleListener = null
            finish()
            return
        }
        if (mPermissionListener != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions!!.toTypedArray(), 1)
            }
        }
    }

    private var mUri: Uri? = null

    //打开相机
    private fun realOpenCamera() {
        try {
            if (mChooserListener == null) {
                finish()
            }
            val mFile = WebForgeUtils.createImageFile(this)
            if (mFile == null) {
                mChooserListener!!.onChoiceResult(REQUEST_CODE, RESULT_CANCELED, null)
                mChooserListener = null
                finish()
                return
            }
            val intent = WebForgeUtils.getIntentCaptureCompat(this, mFile)
            // 指定开启系统相机的Action
            mUri = intent.getParcelableExtra<Uri?>(MediaStore.EXTRA_OUTPUT)
            this.startActivityForResult(intent, REQUEST_CODE)
        } catch (ignore: Throwable) {
            LogUtils.e(TAG, "找不到系统相机")
            if (mChooserListener != null) {
                mChooserListener!!.onChoiceResult(REQUEST_CODE, RESULT_CANCELED, null)
            }
            mChooserListener = null
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        }
    }

    //打开录像
    private fun realOpenVideo() {
        try {
            if (mChooserListener == null) {
                finish()
            }
            val mFile = WebForgeUtils.createVideoFile(this)
            if (mFile == null) {
                mChooserListener!!.onChoiceResult(REQUEST_CODE, RESULT_CANCELED, null)
                mChooserListener = null
                finish()
                return
            }
            val intent = WebForgeUtils.getIntentVideoCompat(this, mFile)
            // 指定开启系统相机的Action
            mUri = intent.getParcelableExtra<Uri?>(MediaStore.EXTRA_OUTPUT)
            this.startActivityForResult(intent, REQUEST_CODE)
        } catch (ignore: Throwable) {
            LogUtils.e(TAG, "找不到系统相机")
            if (mChooserListener != null) {
                mChooserListener!!.onChoiceResult(REQUEST_CODE, RESULT_CANCELED, null)
            }
            mChooserListener = null
            if (LogUtils.isDebug()) {
                ignore.printStackTrace()
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (mPermissionListener != null) {
            val mBundle = Bundle()
            mBundle.putInt(KEY_FROM_INTENTION, mAction!!.fromIntention)
            mPermissionListener!!.onRequestPermissionsResult(permissions, grantResults, mBundle)
        }
        mPermissionListener = null
        finish()
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
        const val KEY_ACTION: String = "KEY_ACTION"
        const val KEY_URI: String = "KEY_URI"
        const val KEY_FROM_INTENTION: String = "KEY_FROM_INTENTION"
        const val KEY_FILE_CHOOSER_INTENT: String = "KEY_FILE_CHOOSER_INTENT"
        private var mRationaleListener: RationaleListener? = null
        private var mPermissionListener: PermissionListener? = null
        private var mChooserListener: ChooserListener? = null
        private val TAG: String = ActionActivity::class.java.getSimpleName()
        const val REQUEST_CODE: Int = 0x254

        @JvmStatic
        fun start(activity: Activity, action: Action) {
            val mIntent = Intent(activity, ActionActivity::class.java)
            mIntent.putExtra(KEY_ACTION, action)
            mIntent.setExtrasClassLoader(Action::class.java.getClassLoader())
            activity.startActivity(mIntent)
        }

        @JvmStatic
        fun setChooserListener(chooserListener: ChooserListener?) {
            mChooserListener = chooserListener
        }

        @JvmStatic
        fun setPermissionListener(permissionListener: PermissionListener?) {
            mPermissionListener = permissionListener
        }
    }
}
