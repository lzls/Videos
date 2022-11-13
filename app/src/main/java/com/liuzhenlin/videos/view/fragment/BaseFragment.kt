/*
 * Created on 2022-10-22 12:10:38 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.content.res.Configuration
import android.os.Bundle
import com.liuzhenlin.common.Configs.ScreenWidthDpLevel
import com.liuzhenlin.swipeback.SwipeBackFragment

open class BaseFragment : SwipeBackFragment() {

    private var mScreenWidthDp = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mScreenWidthDp = resources.configuration.screenWidthDp
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val screenWidthDp = mScreenWidthDp
        if (screenWidthDp != 0) {
            mScreenWidthDp = newConfig.screenWidthDp
            val oldLevel = ScreenWidthDpLevel.of(screenWidthDp)
            val level = ScreenWidthDpLevel.of(newConfig.screenWidthDp)
            if (level !== oldLevel) {
                onScreenWidthDpLevelChanged(oldLevel, level)
            }
        }
    }

    protected open fun onScreenWidthDpLevelChanged(
            oldLevel: ScreenWidthDpLevel, level: ScreenWidthDpLevel) {
    }
}