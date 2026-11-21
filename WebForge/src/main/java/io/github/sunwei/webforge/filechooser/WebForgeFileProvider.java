
package io.github.sunwei.webforge.filechooser;

import android.content.Context;
import android.content.pm.ProviderInfo;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

/**
 */
public class WebForgeFileProvider extends FileProvider {

    @Override
    public void attachInfo(@NonNull Context context, @NonNull ProviderInfo info) {
        super.attachInfo(context, info);
    }
}
