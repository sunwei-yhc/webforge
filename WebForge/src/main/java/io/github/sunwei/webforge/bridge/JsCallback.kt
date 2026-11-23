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
package io.github.sunwei.webforge.bridge

import android.util.Log
import android.webkit.WebView
import io.github.sunwei.webforge.util.LogUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference

open class JsCallback(view: WebView?, private val mInjectedName: String, private val mIndex: Int) {
    private var mCouldGoOn = true
    private val mWebViewRef: WeakReference<WebView?>
    private var mIsPermanent = 0

    init {
        mWebViewRef = WeakReference<WebView?>(view)
    }

    /**
     * 向网页执行js回调；
     * @param args
     * @throws JsCallbackException
     */
    @Throws(JsCallbackException::class)
    open fun apply(vararg args: Any) {
        if (mWebViewRef.get() == null) {
            throw JsCallbackException("the WebView related to the JsCallback has been recycled")
        }
        if (!mCouldGoOn) {
            throw JsCallbackException("the JsCallback isn't permanent,cannot be called more than once")
        }
        val sb = StringBuilder()
        for (arg in args) {
            sb.append(",")
            val isStrArg = arg is String
            // 有的接口将Json对象转换成了String返回，这里不能加双引号，否则网页会认为是String而不是JavaScript对象；
            val isObjArg = isJavaScriptObject(arg)
            if (isStrArg && !isObjArg) {
                sb.append("\"")
            }
            sb.append(arg.toString())
            if (isStrArg && !isObjArg) {
                sb.append("\"")
            }
        }
        val execJs: String = String.format(CALLBACK_JS_FORMAT, mInjectedName, mIndex, mIsPermanent, sb.toString())
        if (LogUtils.isDebug()) {
            Log.d("JsCallBack", execJs)
        }
        mWebViewRef.get()!!.loadUrl(execJs)
        mCouldGoOn = mIsPermanent > 0
    }

    /**
     * 是否是JSON(JavaScript Object Notation)对象；
     * @param obj
     * @return
     */
    private fun isJavaScriptObject(obj: Any): Boolean {
        if (obj is JSONObject || obj is JSONArray) {
            return true
        } else {
            val json = obj.toString()
            try {
                JSONObject(json)
            } catch (e: JSONException) {
                try {
                    JSONArray(json)
                } catch (e1: JSONException) {
                    return false
                }
            }
            return true
        }
    }

    /**
     * 一般传入到Java方法的js function是一次性使用的，即在Java层jsCallback.apply(...)之后不能再发起回调了；
     * 如果需要传入的function能够在当前页面生命周期内多次使用，请在第一次apply前setPermanent(true)；
     * @param value
     */
    open fun setPermanent(value: Boolean) {
        mIsPermanent = if (value) 1 else 0
    }

    open class JsCallbackException(msg: String?) : Exception(msg)
    companion object {
        private const val CALLBACK_JS_FORMAT = "javascript:%s.callback(%d, %d %s);"
    }
}
