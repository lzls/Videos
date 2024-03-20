/*
 * Created on 2020-6-18 8:34:30 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.os.AsyncTask
import android.os.Handler
import com.liuzhenlin.common.Consts
import com.liuzhenlin.common.utils.Executors
import com.liuzhenlin.common.utils.FileUtils
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.dao.IVideoDao
import com.liuzhenlin.videos.dao.VideoListItemDao
import java.io.File
import java.util.*

/**
 * @author 刘振林
 */

interface ILocalVideoListModel {

    public val videoListItems: List<VideoListItem>
    public val checkedItems: List<VideoListItem>?
    public val videoDirs: List<VideoDirectory>?
    public val videos: ArrayList<Video>?

    fun setVideoListItems(items: List<VideoListItem>?)
    fun updateVideoProgress(video: Video)
    fun updateVideoDirectory(dirPath: String, videos: List<Video>)
    fun setItemChecked(index: Int, checked: Boolean)
    fun setItemTopped(index: Int, topped: Boolean)
    fun deleteItem(item: VideoListItem)
    fun deleteItems(vararg items: VideoListItem)
    fun renameItemTo(item: VideoListItem)
    fun setAllItemsChecked()
    fun setAllItemsUnchecked()

    fun startWatchingVideos()
    fun stopWatchingVideos(reloadVideosIfNeeded: Boolean)

    interface Callback : BaseModel.Callback {
        fun onAllItemsRemoved()
        fun onAllItemsChanged()
        fun onVideoDirThumbChanged(index: Int)
        fun onVideoDirVideoSizeOrCountChanged(index: Int)
        fun onVideoProgressChanged(index: Int)
        fun onItemUpdated(index: Int)
        fun onItemRemoved(index: Int)
        fun onItemMoved(fromIndex: Int, toIndex: Int)
        fun onItemRenamed(oldIndex: Int, index: Int)
        fun onItemCheckedChanged(index: Int)
        fun onItemToppedChanged(oldIndex: Int, index: Int)
    }
}

class LocalVideoListModel(context: Context)
    : BaseModel<Nothing, MutableList<VideoListItem>?, ILocalVideoListModel.Callback>(context),
        ILocalVideoListModel {

    private val mVideoListItems = mutableListOf<VideoListItem>()

    override val videoListItems: List<VideoListItem> get() = mVideoListItems

    override val checkedItems: List<VideoListItem>?
        get() {
            var checkedItems: MutableList<VideoListItem>? = null
            for (item in mVideoListItems) {
                if (item.isChecked) {
                    if (checkedItems == null) {
                        checkedItems = mutableListOf()
                    }
                    checkedItems.add(item)
                }
            }
            return checkedItems
        }

    override val videoDirs: List<VideoDirectory>?
        get() {
            var dirs: MutableList<VideoDirectory>? = null
            for (item in mVideoListItems) {
                if (dirs == null) {
                    dirs = mutableListOf()
                }
                if (item is VideoDirectory) {
                    dirs.add(item)
                } else {
                    val video = item as Video
                    val dir = VideoDirectory()
                    dir.path = video.path.substring(0, video.path.lastIndexOf(File.separatorChar))
                    dir.name = FileUtils.getFileNameFromFilePath(dir.path)
                    dir.videos = mutableListOf(video)
                    dir.size = video.size
                    dirs.add(dir)
                }
            }
            return dirs
        }

    override val videos: ArrayList<Video>?
        get() {
            var videos: ArrayList<Video>? = null
            for (item in mVideoListItems) {
                if (videos == null) videos = ArrayList()
                when (item) {
                    is Video -> videos.add(item)
                    is VideoDirectory -> videos.addAll(item.videos)
                }
            }
            return videos?.apply { sortByElementName() }
        }

    private var mNeedReloadVideos = false
    private var mVideoObserver: VideoObserver? = null

    private var mOnVideosLoadListeners: MutableList<OnVideosLoadListener>? = null

    fun addOnVideosLoadListener(listener: OnVideosLoadListener) {
        if (mOnVideosLoadListeners == null)
            mOnVideosLoadListeners = mutableListOf()
        if (!mOnVideosLoadListeners!!.contains(listener))
            mOnVideosLoadListeners!!.add(listener)
    }

    fun removeOnVideosLoadListener(listener: OnVideosLoadListener) {
        mOnVideosLoadListeners?.remove(listener)
    }

    private fun notifyListenersOnVideosLoaded(videos: List<Video>?) =
            mOnVideosLoadListeners?.let {
                if (it.isEmpty()) return@let

                for (i in it.size - 1 downTo 0) {
                    val copy = if (videos == null) null else ArrayList(videos)
                    copy?.deepCopy(videos as List<Video>)
                    it[i].onVideosLoadFinish(copy)
                }
            }

    override fun onLoadStart() {
        super.onLoadStart()
        mOnVideosLoadListeners?.let {
            for (i in it.size - 1 downTo 0) {
                it[i].onVideosLoadStart()
            }
        }
    }

    override fun onLoadCanceled() {
        super.onLoadCanceled()
        mOnVideosLoadListeners?.let {
            for (i in it.size - 1 downTo 0) {
                it[i].onVideosLoadCanceled()
            }
        }
    }

    override fun createAndStartLoader(): AsyncTask<*, *, *> {
        val loader = LoadVideosTask()
        loader.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR)
        return loader
    }

    @SuppressLint("StaticFieldLeak")
    private inner class LoadVideosTask : AsyncTask<Void, Void, Array<*>?>() {

        override fun onPreExecute() = onLoadStart()

        override fun doInBackground(vararg voids: Void): Array<*>? {
            val dao = VideoListItemDao.getSingleton(mContext)

            var videos: MutableList<Video>? = null

            val videoCursor = dao.queryAllVideos() ?: return null
            while (!isCancelled && videoCursor.moveToNext()) {
                val video = dao.buildVideo(videoCursor)
                if (video != null) {
                    if (videos == null)
                        videos = LinkedList()
                    videos.add(video)
                }
            }
            videoCursor.close()

            videos.sortByElementName()
            val items = videos.toVideoListItems() ?: return null

            val videodirCursor = dao.queryAllVideoDirs()
            if (videodirCursor != null) {
                while (!isCancelled && videodirCursor.moveToNext()) {
                    val videodir = dao.buildVideoDir(videodirCursor)
                    if (!items.contains(videodir)) {
                        dao.deleteVideoDir(videodir.path)
                    }
                }
                videodirCursor.close()
            }

            return arrayOf(items, videos)
        }

        override fun onPostExecute(result: Array<*>?) {
            mNeedReloadVideos = false
            @Suppress("UNCHECKED_CAST")
            onLoadFinish(result?.get(0) as MutableList<VideoListItem>?)

            @Suppress("UNCHECKED_CAST")
            notifyListenersOnVideosLoaded(result?.get(1) as List<Video>?)
        }
    }

    override fun setVideoListItems(items: List<VideoListItem>?) {
        if (items == null || items.isEmpty()) {
            if (mVideoListItems.isNotEmpty()) {
                mVideoListItems.clear()
                mCallback?.onAllItemsRemoved()
            }
        } else if (items.size == mVideoListItems.size) {
            for (index in items.indices) {
                if (!items[index].allEqual(mVideoListItems[index])) {
                    mVideoListItems[index] = items[index]
                    mCallback?.onItemUpdated(index)
                }
            }
        } else {
            mVideoListItems.set(items)
            mCallback?.onAllItemsChanged()
        }
    }

    override fun updateVideoProgress(video: Video) {
        if (video.id == Consts.NO_ID) return

        for (i in mVideoListItems.indices) {
            val item = mVideoListItems[i] as? Video ?: continue
            if (item != video) continue

            if (item.progress != video.progress) {
                item.progress = video.progress
                mCallback?.onVideoProgressChanged(i)
            }
            break
        }
    }

    override fun updateVideoDirectory(dirPath: String, videos: List<Video>) {
        loop@ for ((i, item) in mVideoListItems.withIndex()) {
            if (item.path != dirPath) continue@loop

            val dao = VideoListItemDao.getSingleton(mContext)
            when (videos.size) {
                0 -> {
                    if (item is VideoDirectory) {
                        dao.deleteVideoDir(item.path)
                    }

                    mVideoListItems.removeAt(i)
                    mCallback?.onItemRemoved(i)
                }
                1 -> {
                    if (item is VideoDirectory) {
                        dao.deleteVideoDir(item.path)
                    }

                    val video = videos[0]
                    if (video.isTopped) {
                        video.isTopped = false
                        dao.setVideoListItemTopped(video, false)
                    }

                    mVideoListItems[i] = video
                    val newIndex = mVideoListItems.reordered().indexOf(video)
                    if (newIndex == i) {
                        mCallback?.onItemUpdated(i)
                    } else {
                        mVideoListItems.add(newIndex, mVideoListItems.removeAt(i))
                        mCallback?.onItemMoved(i, newIndex)
                    }
                }
                else -> {
                    if (item is Video) {
                        var videodir = dao.queryVideoDirByPath(dirPath)
                        if (videodir == null) {
                            videodir = dao.insertVideoDir(dirPath)
                        } else
                            if (videodir.isTopped) {
                                videodir.isTopped = false
                                dao.setVideoListItemTopped(videodir, false)
                            }
                        videodir.videos = videos as? MutableList<Video>? ?: videos.toMutableList()
                        videodir.size = videos.allVideoSize()

                        mVideoListItems[i] = videodir
                        val newIndex = mVideoListItems.reordered().indexOf(videodir)
                        if (newIndex == i) {
                            mCallback?.onItemUpdated(i)
                        } else {
                            mVideoListItems.add(newIndex, mVideoListItems.removeAt(i))
                            mCallback?.onItemMoved(i, newIndex)
                        }
                    } else if (item is VideoDirectory) {
                        val oldVideos = item.videos
                        val oldSize = item.size
                        val oldVideoCount = oldVideos.size
                        if (!oldVideos.allEqual(videos)) {
                            item.videos = videos as? MutableList<Video>? ?: videos.toMutableList()
                            item.size = videos.allVideoSize()

                            if (!oldVideos[0].allEqual(videos[0])) {
                                mCallback?.onVideoDirThumbChanged(i)
                            }
                            if (oldSize != item.size || oldVideoCount != videos.size) {
                                mCallback?.onVideoDirVideoSizeOrCountChanged(i)
                            }
                        }
                    }
                }
            }
            break@loop
        }
    }

    override fun setItemChecked(index: Int, checked: Boolean) {
        val item = mVideoListItems[index]
        if (item.isChecked != checked) {
            item.isChecked = checked
            mCallback?.onItemCheckedChanged(index)
        }
    }

    override fun setItemTopped(index: Int, topped: Boolean) {
        val item = mVideoListItems[index]
        if (item.isTopped != topped) {
            item.isTopped = topped
            VideoListItemDao.getSingleton(mContext).setVideoListItemTopped(item, topped)

            val newIndex = mVideoListItems.reordered().indexOf(item)
            if (newIndex != index) {
                mVideoListItems.add(newIndex, mVideoListItems.removeAt(index))
            }
            mCallback?.onItemToppedChanged(index, newIndex)
        }
    }

    override fun deleteItem(item: VideoListItem) {
        val index = mVideoListItems.indexOf(item)
        if (index >= 0) {
            mVideoListItems.removeAt(index)
            mCallback?.onItemRemoved(index)
        }
    }

    override fun deleteItems(vararg items: VideoListItem) {
        var start = -1
        var index = 0
        val it = mVideoListItems.iterator()
        while (it.hasNext()) {
            if (items.contains(it.next())) {
                if (start == -1) {
                    start = index
                }
                it.remove()
                mCallback?.onItemRemoved(index)
                index--
            }
            index++
        }
    }

    override fun renameItemTo(item: VideoListItem) {
        val index = mVideoListItems.indexOf(item)
        if (index >= 0) {
            mVideoListItems[index].name = item.name
            val newIndex = mVideoListItems.reordered().indexOf(item)
            if (newIndex != index) {
                mVideoListItems.add(newIndex, mVideoListItems.removeAt(index))
            }
            mCallback?.onItemRenamed(index, newIndex)
        }
    }

    override fun setAllItemsChecked() {
        for (i in mVideoListItems.indices) {
            val item = mVideoListItems[i]
            if (!item.isChecked) {
                item.isChecked = true
                mCallback?.onItemCheckedChanged(i)
            }
        }
    }

    override fun setAllItemsUnchecked() {
        for (i in mVideoListItems.indices) {
            val item = mVideoListItems[i]
            if (item.isChecked) {
                item.isChecked = false
                mCallback?.onItemCheckedChanged(i)
            }
        }
    }

    override fun startWatchingVideos() {
        var observer = mVideoObserver
        if (observer == null) {
            observer = VideoObserver(Consts.getMainThreadHandler())
        }
        observer.startWatching()
    }

    override fun stopWatchingVideos(reloadVideosIfNeeded: Boolean) {
        mVideoObserver?.stopWatching()
        if (mNeedReloadVideos) {
            mNeedReloadVideos = false
            if (reloadVideosIfNeeded) {
                startLoader()
            }
        }
    }

    private inner class VideoObserver(handler: Handler) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            // 此页面放入后台且数据有变化，标记为需要刷新数据
            // 以在此页面重新显示在前台时刷新列表
            mNeedReloadVideos = true
        }

        fun startWatching() {
            mVideoObserver = this
            // notifyForDescendants should be true here, for fear of Uris like
            // content://media/external/video/media/83 not notifying us.
            mContext.contentResolver.registerContentObserver(IVideoDao.VIDEO_URI, true, this)
        }

        fun stopWatching() {
            mVideoObserver = null
            mContext.contentResolver.unregisterContentObserver(this)
        }
    }
}