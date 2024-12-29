/*
 * Created on 2020-6-18 8:35:03 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import com.liuzhenlin.common.Consts
import com.liuzhenlin.common.Consts.EMPTY_STRING
import com.liuzhenlin.common.utils.AlgorithmUtil
import com.liuzhenlin.common.utils.Executors
import com.liuzhenlin.videos.allEqual
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.dao.VideoListItemDao
import com.liuzhenlin.videos.set
import com.liuzhenlin.videos.sortByElementName
import com.liuzhenlin.videos.view.fragment.VideoListItemDeleteOnDiskListener
import com.liuzhenlin.videos.view.fragment.VideoListItemRenameResultCallback
import com.liuzhenlin.videos.view.fragment.deleteOnDisk
import com.liuzhenlin.videos.view.fragment.renameTo

/**
 * @author 刘振林
 */

interface ILocalSearchedVideoListModel {

    public val videos: List<Video>
    public val searchText: String
    public val searchedVideos: List<Video>

    fun setVideos(videos: List<Video>?)
    fun setSearchText(searchText: String)
    fun updateSearchedVideoProgress(video: Video)
    fun deleteVideo(video: Video, listener: VideoListItemDeleteOnDiskListener<Video>?)
    fun renameVideoTo(video: Video, callback: VideoListItemRenameResultCallback<Video>?)

    interface Callback : BaseModel.Callback {
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
    private var mSearchText = EMPTY_STRING
    private val mSearchedVideos = mutableListOf<Video>()

    override val videos: List<Video> get() = mVideos
    override val searchText: String get() = mSearchText
    override val searchedVideos: List<Video> get() = mSearchedVideos

    override fun setVideos(videos: List<Video>?) {
        if (!mVideos.allEqual(videos)) {
            mVideos.set(videos)
            refreshSearchedVideos(false)
        }
    }

    override fun setSearchText(searchText: String) {
        val st = mSearchText
        mSearchText = searchText
        if (!st.equals(searchText, ignoreCase = true)) {
            refreshSearchedVideos(true)
        }
    }

    private fun refreshSearchedVideos(searchTextChanged: Boolean) {
        var searchedVideos: MutableList<Video>? = null
        if (mSearchText.isNotEmpty()) {
            for (video in mVideos) {
                if (mSearchText.length
                        == AlgorithmUtil.lcs(video.name, mSearchText, true).length) {
                    if (searchedVideos == null) searchedVideos = mutableListOf()
                    searchedVideos.add(video)
                }
            }
        }
        searchedVideos.sortByElementName()
        if (searchedVideos == null || searchedVideos.isEmpty()) {
            clearSearchedVideos()
        } else if (searchedVideos.size == mSearchedVideos.size) {
            if (searchTextChanged) {
                for (index in searchedVideos.indices) {
                    if (!updateSearchedVideo(index, searchedVideos[index])) {
                        // Still Notifies the callback that the video name display need be updated
                        // to follow the search text change.
                        mCallback?.onSearchedVideoRenamed(index, index)
                    }
                }
            } else {
                for (index in searchedVideos.indices) {
                    updateSearchedVideo(index, searchedVideos[index])
                }
            }
        } else {
            setSearchedVideos(searchedVideos)
        }
    }

    private fun setSearchedVideos(videos: List<Video>) {
        if (!mSearchedVideos.allEqual(videos)) {
            mSearchedVideos.set(videos)
            mCallback?.onAllSearchedVideosChanged()
        }
    }

    private fun clearSearchedVideos() {
        if (mSearchedVideos.isNotEmpty()) {
            mSearchedVideos.clear()
            mCallback?.onAllSearchedVideosRemoved()
        }
    }

    private fun updateSearchedVideo(index: Int, video: Video): Boolean {
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

    override fun deleteVideo(video: Video, listener: VideoListItemDeleteOnDiskListener<Video>?) {
        listener?.onItemsDeleteStart(video)
        Executors.THREAD_POOL_EXECUTOR.execute {
            arrayOf(video).deleteOnDisk()
            Executors.MAIN_EXECUTOR.execute {
                listener?.onItemsDeleteFinish(video)
            }
        }

        mVideos.remove(video)

        val index = mSearchedVideos.indexOf(video)
        if (index >= 0) {
            mSearchedVideos.removeAt(index)
            mCallback?.onSearchedVideoDeleted(index)
        }
    }

    override fun renameVideoTo(video: Video, callback: VideoListItemRenameResultCallback<Video>?) {
        val idx = mVideos.indexOf(video)
        if (idx >= 0) {
            val v = mVideos[idx]

            if (!v.renameTo(video.name, callback))
                return

            v.name = video.name
            mVideos.sortByElementName()

            // Use v for index search because its id might have been updated in the above
            // v.renameTo() method invocation
            val index = mSearchedVideos.indexOf(v)
            if (index >= 0) {
                mSearchedVideos[index].name = video.name
                if (mSearchText.length == AlgorithmUtil.lcs(video.name, mSearchText, true).length) {
                    mSearchedVideos.sortByElementName()
                    mCallback?.onSearchedVideoRenamed(index, mSearchedVideos.indexOf(v))
                } else {
                    mSearchedVideos.removeAt(index)
                    mCallback?.onSearchedVideoDeleted(index)
                }
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