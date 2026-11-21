package io.github.sunwei.webforge;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** 主线程直接执行，后台提交按页面排队，关闭时只撤销本页面任务。 */
final class PageTaskQueue {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    boolean isClosed() {
        return closed;
    }

    void run(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!closed) {
                task.run();
            }
            return;
        }
        synchronized (this) {
            if (!closed) {
                MAIN.postAtTime(() -> run(task), this, SystemClock.uptimeMillis());
            }
        }
    }

    void close(Runnable cleanup) {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            MAIN.removeCallbacksAndMessages(this);
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run();
        } else {
            MAIN.post(cleanup);
        }
    }
}
