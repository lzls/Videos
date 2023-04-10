/*
 * Created on 2020-6-18 8:34:50 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import com.liuzhenlin.common.Consts
import com.liuzhenlin.common.utils.Executors
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.dao.VideoListItemDao
import com.liuzhenlin.videos.reordered
import com.liuzhenlin.videos.set
import java.util.*

/**
 * @author 刘振林
 */

interface ILocalFoldedVideoListModel {

    public val videodir: VideoDirectory
    public val videos: List<Video>
    public val checkedVideos: List<Video>?

    fun setVideos(videos: List<Video>?)
    fun clearVideos()
    fun updateVideo(index: Int, video: Video)
    fun updateVideoProgress(video: Video)
    fun setVideoChecked(index: Int, checked: Boolean)
    fun setVideoTopped(index: Int, topped: Boolean)
    fun setAllVideosChecked()
    fun setAllVideosUnchecked()
    fun deleteVideos(vararg videos: Video)
    fun deleteVideo(video: Video)
    fun renameVideoTo(video: Video)

    interface Callback : BaseModel.Callback {
        fun onAllVideosRemoved()
        fun onAllVideosChanged()
        fun onVideoUpdated(index: Int)
        fun onVideoProgressChanged(index: Int)
        fun onVideoToppedChanged(oldIndex: Int, index: Int)
        fun onVideoCheckedChanged(index: Int)
        fun onVideoRemoved(index: Int)
        fun onVideoRenamed(oldIndex: Int, index: Int)
        fun onVideoMoved(index: Int, newIndex: Int)
    }
}

class LocalFoldedVideoListModel(override val videodir: VideoDirectory, context: Context)
    : BaseModel<Nothing, MutableList<Video>?, ILocalFoldedVideoListModel.Callback>(context),
        ILocalFoldedVideoListModel {

    private inline val mVideos get() = videodir.videos

    override val videos: List<Video> get() = mVideos

    override val checkedVideos: List<Video>?
        get() {
            var videos: MutableList<Video>? = null
            for (video in videodir.videos) {
                if (video.isChecked) {
                    if (videos == null) videos = mutableListOf()
                    videos.add(video)
                }
            }
            return videos
        }

    override fun setVideos(videos: List<Video>?) {
        if (videos == null || videos.isEmpty()) {
            clearVideos()
        } else
            if (videos.size == mVideos.size) {
                for (index in videos.indices) {
                    updateVideo(index, videos[index])
                }
            } else {
                mVideos.set(videos)
                mCallback?.onAllVideosChanged()
            }
    }

    override fun clearVideos() {
        if (mVideos.isNotEmpty()) {
            mVideos.clear()
            mCallback?.onAllVideosRemoved()
        }
    }

    override fun updateVideo(index: Int, video: Video) {
        if (!mVideos[index].allEqual(video)) {
            mVideos[index] = video
            mCallback?.onVideoUpdated(index)
        }
    }

    override fun updateVideoProgress(video: Video) {
        if (video.id == Consts.NO_ID) return

        for ((i, v) in mVideos.withIndex()) {
            if (v != video) continue
            if (v.progress != video.progress) {
                v.progress = video.progress
                mCallback?.onVideoProgressChanged(i)
            }
            break
        }
    }

    override fun setVideoChecked(index: Int, checked: Boolean) {
        val video = mVideos[index]
        if (video.isChecked != checked) {
            video.isChecked = checked
            mCallback?.onVideoCheckedChanged(index)
        }
    }

    override fun setVideoTopped(index: Int, topped: Boolean) {
        val video = mVideos[index]
        if (video.isTopped != topped) {
            video.isTopped = topped
            VideoListItemDao.getSingleton(mContext).setVideoListItemTopped(video, topped)

            val newIndex = mVideos.reordered().indexOf(video)
            if (newIndex != index) {
                mVideos.add(newIndex, mVideos.removeAt(index))
            }
            mCallback?.onVideoToppedChanged(index, newIndex)
        }
    }

    override fun setAllVideosChecked() {
        for ((index, video) in mVideos.withIndex())
            if (!video.isChecked) {
                video.isChecked = true
                mCallback?.onVideoCheckedChanged(index)
            }
    }

    override fun setAllVideosUnchecked() {
        for ((index, video) in mVideos.withIndex())
            if (video.isChecked) {
                video.isChecked = false
                mCallback?.onVideoCheckedChanged(index)
            }
    }

    override fun deleteVideos(vararg videos: Video) {
        var start = -1
        var index = 0
        val it = mVideos.iterator()
        while (it.hasNext()) {
            if (videos.contains(it.next())) {
                if (start == -1) {
                    start = index
                }
                it.remove()
                mCallback?.onVideoRemoved(index)
                index--
            }
            index++
        }
    }

    override fun deleteVideo(video: Video) {
        val index = mVideos.indexOf(video)
        if (index >= 0) {
            mVideos.removeAt(index)
            mCallback?.onVideoRemoved(index)
        }
    }

    override fun renameVideoTo(video: Video) {
        val index = mVideos.indexOf(video)
        if (index >= 0) {
            mVideos[index].name = video.name
            val newIndex = mVideos.reordered().indexOf(video)
            if (newIndex != index) {
                mVideos.add(newIndex, mVideos.removeAt(index))
            }
            mCallback?.onVideoRenamed(index, newIndex)
        }
    }

    override fun createAndStartLoader(): AsyncTask<*, *, *> {
        val loader = LoadDirectoryVideosTask()
        loader.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR)
        return loader
    }

    @SuppressLint("StaticFieldLeak")
    private inner class LoadDirectoryVideosTask : Loader<Void>() {

        override fun doInBackground(vararg params: Void?): MutableList<Video>? {
            val dao = VideoListItemDao.getSingleton(mContext)

            var videos: MutableList<Video>? = null

            val videoCursor = dao.queryAllVideosInDirectory(videodir.path) ?: return null
            while (!isCancelled && videoCursor.moveToNext()) {
                val video = dao.buildVideo(videoCursor) ?: continue
                if (videos == null)
                    videos = LinkedList()
                videos.add(video)
            }
            videoCursor.close()

            return videos?.reordered()
        }
    }
}