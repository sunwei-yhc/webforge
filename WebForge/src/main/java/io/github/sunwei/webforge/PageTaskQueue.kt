package io.github.sunwei.webforge

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** 主线程直接执行，后台提交按页面排队，关闭时只撤销本页面任务。  */
internal class PageTaskQueue {
    @Volatile
    var isClosed: Boolean = false
        private set

    fun run(task: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!this.isClosed) {
                task.run()
            }
            return
        }
        synchronized(this) {
            if (!this.isClosed) {
                MAIN.postAtTime(Runnable { run(task) }, this, SystemClock.uptimeMillis())
            }
        }
    }

    fun close(cleanup: Runnable) {
        synchronized(this) {
            if (this.isClosed) {
                return
            }
            this.isClosed = true
            MAIN.removeCallbacksAndMessages(this)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run()
        } else {
            MAIN.post(cleanup)
        }
    }

    companion object {
        private val MAIN = Handler(Looper.getMainLooper())
    }
}
