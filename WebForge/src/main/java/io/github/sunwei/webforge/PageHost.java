package io.github.sunwei.webforge;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

final class PageHost implements LifecycleEventObserver {
    private final WebPage page;
    private final LifecycleOwner owner;
    private final OnBackPressedDispatcher dispatcher;
    private final OnBackPressedCallback back;

    PageHost(WebPage page, ComponentActivity activity, LifecycleOwner owner, boolean handleBack) {
        this.page = page;
        this.owner = owner;
        dispatcher = activity.getOnBackPressedDispatcher();
        back = handleBack ? new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (page.goBack()) {
                    return;
                }
                setEnabled(false);
                try {
                    dispatcher.onBackPressed();
                } finally {
                    setEnabled(!page.isClosed());
                }
            }
        } : null;
    }

    void bind() {
        Lifecycle lifecycle = owner.getLifecycle();
        if (lifecycle.getCurrentState() == Lifecycle.State.DESTROYED) {
            throw new IllegalStateException("The WebPage host has been destroyed");
        }
        if (!lifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            page.pause();
        }
        lifecycle.addObserver(this);
        if (back != null) {
            dispatcher.addCallback(owner, back);
        }
    }

    @Override
    public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
        switch (event) {
            case ON_RESUME:
                page.resume();
                break;
            case ON_PAUSE:
                page.pause();
                break;
            case ON_DESTROY:
                page.close();
                break;
            default:
                break;
        }
    }

    void unbind() {
        owner.getLifecycle().removeObserver(this);
        if (back != null) {
            back.remove();
        }
    }
}
