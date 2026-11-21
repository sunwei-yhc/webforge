
package io.github.sunwei.webforge.core;

import android.view.KeyEvent;

/**
 */
public interface IEventHandler {

    boolean onKeyDown(int keyCode, KeyEvent event);

    boolean back();
}
