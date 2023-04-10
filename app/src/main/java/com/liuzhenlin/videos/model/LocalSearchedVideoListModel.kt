/*
 * Created on 2020-6-18 8:35:03 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import com.liuzhenlin.common.Consts
import com.liuzhenlin.common.utils.Executors
import com.liuzhenlin.videos.allEqual
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.dao.VideoListItemDao
import com.liuzhenlin.videos.set
import com.liuzhenlin.videos.sortByElementName

/**
 * @author 刘振林
 */

interface ILocalSearchedVideoListModel {

    public val videos: List<Video>
    public val searchedVideos: List<Video>

    fun setVideos(videos: List<Video>?)
    fun setSearchedVideos(videos: List<Video>)
    fun clearSearchedVideos()
    fun updateSearchedVideo(index: Int, video: Video): Boolean
    fun updateSearchedVideoProgress(video: Video)
    fun deleteVideo(video: Video)
    fun renameVideoTo(video: Video, videoNameMatchesSearchKeywords: Boolean)

    interface Callback : BaseModel.Callback {
        fun onAllVideosChanged()
        fun onAllSearchedVideosRemoved()
        fun onAllSearchedVideosChanged()
        fun onSearchedVideoUpdated(index: Int)
        fun onSearchedVideoProgressChanged(index: Int)
        fun onSearchedVideoDeleted(index: Int)
        fun onSearchedVideoRenamed(oldIndex: Int, index: Int)
        fun onSearchedVideoMoved(index: Int, newIndex: Int)
    }
}

class LocalSearchedVideoListModel(context: Context)
    : BaseModel<Nothing, MutableList<Video>?, ILocalSearchedVideoListModel.Callback>(context),
        ILocalSearchedVideoListModel {

    private val mVideos = arrayListOf<Video>()
    private val mSearchedVideos = mutableListOf<Video>()

    override val videos: List<Video> get() = mVideos
    override val searchedVideos: List<Video> get() = mSearchedVideos

    override fun setVideos(videos: List<Video>?) {
        if (!mVideos.allEqual(videos)) {
            mVideos.set(videos)
            mCallback?.onAllVideosChanged()
        }
    }

    override fun setSearchedVideos(videos: List<Video>) {
        if (!mSearchedVideos.allEqual(videos)) {
            mSearchedVideos.set(videos)
            mCallback?.onAllSearchedVideosChanged()
        }
    }

    override fun clearSearchedVideos() {
        if (mSearchedVideos.isNotEmpty()) {
            mSearchedVideos.clear()
            mCallback?.onAllSearchedVideosRemoved()
        }
    }

    override fun updateSearchedVideo(index: Int, video: Video): Boolean {
        if (!mSearchedVideos[index].allEqual(video)) {
            mSearchedVideos[index] = video
            mCallback?.onSearchedVideoUpdated(index)
            return true
        }
        return false
    }

    override fun updateSearchedVideoProgress(video: Video) {
        if (video.id == Consts.NO_ID) return

        for ((i, v) in mSearchedVideos.withIndex()) {
            if (v != video) continue
            if (v.progress != video.progress) {
                v.progress = video.progress
                mCallback?.onSearchedVideoProgressChanged(i)
            }
            break
        }
    }

    override fun deleteVideo(video: Video) {
        mVideos.remove(video)

        val index = mSearchedVideos.indexOf(video)
        if (index >= 0) {
            mSearchedVideos.removeAt(index)
            mCallback?.onSearchedVideoDeleted(index)
        }
    }

    override fun renameVideoTo(video: Video, videoNameMatchesSearchKeywords: Boolean) {
        val idx = mVideos.indexOf(video)
        if (idx >= 0) {
            mVideos[idx].name = video.name
            mVideos.sortByElementName()
        }

        val index = mSearchedVideos.indexOf(video)
        if (index >= 0) {
            mSearchedVideos[index].name = video.name
            if (videoNameMatchesSearchKeywords) {
                mSearchedVideos.sortByElementName()
                mCallback?.onSearchedVideoRenamed(index, mSearchedVideos.indexOf(video))
            } else {
                mSearchedVideos.removeAt(index)
                mCallback?.onSearchedVideoDeleted(index)
            }
        }
    }

    override fun createAndStartLoader(): AsyncTask<*, *, *> {
        val loader = LoadVideosTask()
        loader.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR)
        return loader
    }

    @SuppressLint("StaticFieldLeak")
    private inner class LoadVideosTask : Loader<Void>() {

        override fun doInBackground(vararg voids: Void): MutableList<Video>? {
            val dao = VideoListItemDao.getSingleton(mContext)

            var videos: MutableList<Video>? = null

            val videoCursor = dao.queryAllVideos() ?: return null
            while (!isCancelled && videoCursor.moveToNext()) {
                val video = dao.buildVideo(videoCursor)
                if (video != null) {
                    if (videos == null)
                        videos = mutableListOf()
                    videos.add(video)
                }
            }
            videoCursor.close()

            videos.sortByElementName()
            return videos
        }
    }
}