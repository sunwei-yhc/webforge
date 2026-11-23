package io.github.sunwei.webforge.permission

/**
 */
interface PermissionInterceptor {
    fun intercept(url: String?, permissions: Array<String>, action: String?): Boolean
}
