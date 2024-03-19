/*
 * Created on 2018/09/07.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import com.liuzhenlin.videos.bean.Video

/**
 * @author 刘振林
 */
interface OnVideosLoadListener {
    fun onVideosLoadStart()
    fun onVideosLoadFinish(videos: MutableList<Video>?)
    fun onVideosLoadCanceled()
}