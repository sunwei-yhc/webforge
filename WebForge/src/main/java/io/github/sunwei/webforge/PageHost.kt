package io.github.sunwei.webforge

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

internal class PageHost(private val page: WebPage, activity: ComponentActivity, private val owner: LifecycleOwner, handleBack: Boolean) : LifecycleEventObserver {
    private val dispatcher: OnBackPressedDispatcher
    private val back: OnBackPressedCallback?

    init {
        dispatcher = activity.getOnBackPressedDispatcher()
        back = if (handleBack) object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page.goBack()) {
                    return
                }
                setEnabled(false)
                try {
                    dispatcher.onBackPressed()
                } finally {
                    setEnabled(!page.isClosed)
                }
            }
        } else null
    }

    fun bind() {
        val lifecycle = owner.getLifecycle()
        check(lifecycle.getCurrentState() != Lifecycle.State.DESTROYED) { "The WebPage host has been destroyed" }
        if (!lifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            page.pause()
        }
        lifecycle.addObserver(this)
        if (back != null) {
            dispatcher.addCallback(owner, back)
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> page.resume()
            Lifecycle.Event.ON_PAUSE -> page.pause()
            Lifecycle.Event.ON_DESTROY -> page.close()
            else -> {}
        }
    }

    fun unbind() {
        owner.getLifecycle().removeObserver(this)
        if (back != null) {
            back.remove()
        }
    }
}
