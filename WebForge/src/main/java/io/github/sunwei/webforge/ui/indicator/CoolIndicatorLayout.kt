/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.sunwei.webforge.ui.indicator

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import io.github.sunwei.webforge.R
import io.github.sunwei.webforge.util.WebForgeUtils

/**
 */
open class CoolIndicatorLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = -1) : BaseIndicatorView(context, attrs, defStyleAttr) {
    private var mCoolIndicator: CoolIndicator? = null


    init {
        mCoolIndicator = CoolIndicator.create(context as Activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCoolIndicator!!.setProgressDrawable(context.getResources().getDrawable(R.drawable.default_drawable_indicator, context.getTheme()))
        } else {
            mCoolIndicator!!.setProgressDrawable(context.getResources().getDrawable(R.drawable.default_drawable_indicator))
        }

        this.addView(mCoolIndicator, offerLayoutParams())
    }

    public override fun show() {
        this.setVisibility(VISIBLE)
        mCoolIndicator!!.start()
    }

    public override fun setProgress(newProgress: Int) {
    }

    public override fun hide() {
        mCoolIndicator!!.complete()
    }

    public override fun offerLayoutParams(): LayoutParams? {
        return LayoutParams(-1, WebForgeUtils.dp2px(getContext(), 3f))
    }

    companion object {
        private val TAG: String = CoolIndicatorLayout::class.java.getSimpleName()
    }
}
