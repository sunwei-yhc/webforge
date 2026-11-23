package io.github.sunwei.webforge.filechooser

import android.net.Uri
import android.webkit.ValueCallback
import java.io.Serializable

/**
 */
open class FileCompressor internal constructor() : Serializable {
    private var mFileCompressEngine: FileCompressEngine? = null

    fun registerFileCompressEngine(valueCallback: FileCompressEngine?) {
        this.mFileCompressEngine = valueCallback
    }

    fun unregisterFileCompressEngine(valueCallback: FileCompressEngine?) {
        this.mFileCompressEngine = null
    }

    fun fileCompress(type: String?, uri: Array<Uri>?, callback: ValueCallback<Array<Uri>>) {
        if (mFileCompressEngine == null) {
            callback.onReceiveValue(uri)
        } else {
            mFileCompressEngine!!.compressFile(type, uri, callback)
        }
    }

    interface FileCompressEngine {
        fun compressFile(type: String?, uri: Array<Uri>?, callback: ValueCallback<Array<Uri>>?)
    }


    companion object {
        private var sInstance: FileCompressor? = null
        @JvmStatic
        val instance: FileCompressor
            get() {
                if (sInstance == null) {
                    synchronized(FileCompressor::class.java) {
                        if (sInstance == null) {
                            sInstance = FileCompressor()
                        }
                    }
                }
                return sInstance!!
            }
    }
}
