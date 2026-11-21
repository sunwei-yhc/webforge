package android.sanshisan.web

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.lang.Exception

/**
 * 添加fragment
 */
fun FragmentActivity.addFragment(res: Int, fragment: Fragment, string: String? = fragment.javaClass.simpleName) {
    supportFragmentManager.beginTransaction().replace(res, fragment, string).commitAllowingStateLoss()
}

/**
 * 显示fragment
 * @param res 布局容器
 * @param fragments fragment集合
 * @param selectIndex 选中的下标
 * @param string tag集合标签用于查找fragment
 *
 */

var fragment: Fragment? = null

/**
 * 显示fragment
 * @param res 布局容器
 * @param mFragment fragmet
 * @param string tag集合标签用于查找fragment
 */

fun FragmentActivity.showFragment(res: Int, mFragment: Fragment?, simpleName: String? = null) {
    try {
        if (mFragment == null) {
            return
        }
        var mTransaction = supportFragmentManager.beginTransaction()
        var list = supportFragmentManager.fragments
        if (fragment != null) {
            mTransaction.hide(fragment!!)
        }
        //判断是否被添加过
        fragment = if (list.contains(mFragment) && mFragment.isAdded) {
            mTransaction.show(mFragment)
            mFragment
        } else {
            mTransaction.add(res, mFragment, simpleName ?: mFragment.javaClass.simpleName)
            mFragment
        }
        mTransaction.commit()
    } catch (e: Exception) {
        e.printStackTrace()
        Log.d(TAG, "showFragment: ${e.message}")
    }
}


const val TAG = "frag_showFragment"

/**
 * 清除fargment
 */
fun FragmentActivity.clearFragment() {
    try {
        supportFragmentManager.fragments.forEach {
            supportFragmentManager.beginTransaction().hide(it)
            supportFragmentManager.beginTransaction().remove(it)
        }
        supportFragmentManager.beginTransaction().commit()
    } catch (e: Exception) {
    }
}

/**
 * 移除fragment
 */
fun FragmentActivity.removeFragment(fragment: Fragment?) {
    if (fragment == null) {
        return
    }
    supportFragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
}

/**
 * 防止内存重启
 */
fun FragmentActivity.retryFragment(savedInstanceState: Bundle?, hashMap: HashMap<String, Fragment>?, mCallBack: CallBack) {
    if (savedInstanceState == null) {
        mCallBack.isNullCanBack()
    } else {
        mCallBack.notNulCallBack(hashMap)
        supportFragmentManager.fragments.forEach {
            hashMap?.put("${it.javaClass.simpleName}", it)
        }
    }
}

interface CallBack {
    //savedInstanceState==null的回调
    fun isNullCanBack()

    //savedInstanceState不等于null的时候回调（内存重启的情况下）
    fun notNulCallBack(hashMap: HashMap<String, Fragment>?)
}