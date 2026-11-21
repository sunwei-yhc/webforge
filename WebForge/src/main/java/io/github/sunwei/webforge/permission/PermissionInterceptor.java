
package io.github.sunwei.webforge.permission;

/**
 */
public interface PermissionInterceptor {
    boolean intercept(String url, String[] permissions, String action);
}
