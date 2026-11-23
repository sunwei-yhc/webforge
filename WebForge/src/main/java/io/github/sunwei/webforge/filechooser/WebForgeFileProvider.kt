package io.github.sunwei.webforge.filechooser

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.core.content.FileProvider

/**
 */
open class WebForgeFileProvider : FileProvider() {
    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
    }
}
