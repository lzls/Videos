/*
 * Created on 2018/09/07.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoListItem

/**
 * @author 刘振林
 */

interface OnVideoItemsLoadListener<T : VideoListItem> {
    fun onVideoItemsLoadStart()
    fun onVideoItemsLoadFinish(videoItems: MutableList<T>?)
    fun onVideoItemsLoadCanceled()
}

interface OnVideoListItemsLoadListener : OnVideoItemsLoadListener<VideoListItem>
interface OnVideosLoadListener : OnVideoItemsLoadListener<Video>
