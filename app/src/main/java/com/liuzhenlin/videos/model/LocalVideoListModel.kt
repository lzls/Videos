/*
 * Created on 2020-6-18 8:34:30 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.os.AsyncTask
import android.os.Environment
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

    public val parentVideoDir: VideoDirectory?
    public val videoListItems: List<VideoListItem>
    public val checkedItems: List<VideoListItem>?
    public val videoDirs: List<VideoDirectory>?
    public val videos: ArrayList<Video>?

    fun setVideoListItems(items: List<VideoListItem>?)
    fun updateVideoProgress(video: Video)
    fun updateVideoDirectory(videodir: VideoDirectory)
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

class LocalVideoListModel(context: Context, override val parentVideoDir: VideoDirectory? = null)
    : BaseModel<Nothing, MutableList<VideoListItem>?, ILocalVideoListModel.Callback>(context),
        ILocalVideoListModel {

    private val mVideoListItems: MutableList<VideoListItem> =
            parentVideoDir?.videoListItems ?: mutableListOf()

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
        get() = collectVideoDirectoryRecursively(mVideoListItems, true)

    private fun collectVideoDirectoryRecursively(items: List<VideoListItem>, inBaseDir: Boolean)
            : MutableList<VideoDirectory>? {
        var baseDir: VideoDirectory? = null
        var dirs: MutableList<VideoDirectory>? = null
        var createdNonbaseDirs: MutableList<VideoDirectory>? = null
        for (item in items) {
            if (dirs == null) {
                dirs = mutableListOf()
            }
            if (item is VideoDirectory) {
                if (inBaseDir) {
                    if (baseDir == null) {
                        baseDir = VideoDirectory()
                        baseDir.path =
                            parentVideoDir?.path ?: Environment.getExternalStorageDirectory().path
                        baseDir.name = FileUtils.getFileNameFromFilePath(baseDir.path)
                    }
                    baseDir.videoListItems.add(item)
                }
                dirs.addAll(collectVideoDirectoryRecursively(item.videoListItems, false) ?: continue)
                dirs.add(item)
            } else if (inBaseDir) {
                val itemPath = item.path.substring(0, item.path.lastIndexOf(File.separatorChar))
                var dir = createdNonbaseDirs?.find { it.path == itemPath }
                if (dir == null) {
                    if (itemPath == baseDir?.path) {
                        dir = baseDir
                    } else {
                        dir = VideoDirectory()
                        dir.path = itemPath
                        dir.name = FileUtils.getFileNameFromFilePath(dir.path)
                        dirs.add(dir)
                        if (createdNonbaseDirs == null)
                            createdNonbaseDirs = mutableListOf()
                        createdNonbaseDirs.add(dir)
                    }
                }
                dir.videoListItems.add(item)
            }
        }
        if (baseDir != null) {
            baseDir.computeSize()
            dirs!!.add(baseDir)
        }
        if (createdNonbaseDirs != null) {
            for (dir in createdNonbaseDirs) dir.computeSize()
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
                    is VideoDirectory -> videos.addAll(item.videos())
                }
            }
            return videos?.apply { sortByElementName() }
        }

    private var mNeedReloadVideos = false
    private var mVideoObserver: VideoObserver? = null

    private var mOnVideoItemsLoadListeners: MutableList<OnVideoItemsLoadListener<*>>? = null

    fun addOnVideoItemsLoadListener(listener: OnVideoItemsLoadListener<*>) {
        if (mOnVideoItemsLoadListeners == null)
            mOnVideoItemsLoadListeners = mutableListOf()
        if (!mOnVideoItemsLoadListeners!!.contains(listener))
            mOnVideoItemsLoadListeners!!.add(listener)
    }

    fun removeOnVideoItemsLoadListener(listener: OnVideoItemsLoadListener<*>) {
        mOnVideoItemsLoadListeners?.remove(listener)
    }

    private fun notifyOnVideoItemsLoadListeners(items: List<VideoListItem>?, videos: List<Video>?) =
            mOnVideoItemsLoadListeners?.let {
                if (it.isEmpty()) return@let

                for (i in it.size - 1 downTo 0) {
                    when (it[i]) {
                        is OnVideoListItemsLoadListener -> {
                            notifyOnVideoItemsLoadListener(
                                    it[i] as OnVideoListItemsLoadListener, items)
                        }
                        is OnVideosLoadListener -> {
                            notifyOnVideoItemsLoadListener(it[i] as OnVideosLoadListener, videos)
                        }
                    }
                }
            }

    private inline fun <reified T : VideoListItem> notifyOnVideoItemsLoadListener(
            listener: OnVideoItemsLoadListener<T>, items: List<T>?) {
        val copy = if (items == null) null else ArrayList(items)
        copy?.deepCopy(items!!)
        listener.onVideoItemsLoadFinish(copy)
    }

    override fun onLoadStart() {
        super.onLoadStart()
        mOnVideoItemsLoadListeners?.let {
            for (i in it.size - 1 downTo 0) {
                it[i].onVideoItemsLoadStart()
            }
        }
    }

    override fun onLoadCanceled() {
        super.onLoadCanceled()
        mOnVideoItemsLoadListeners?.let {
            for (i in it.size - 1 downTo 0) {
                it[i].onVideoItemsLoadCanceled()
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
            var videos: MutableList<Video>? = null
            val vdir = parentVideoDir?.path

            val dao = VideoListItemDao.getSingleton(mContext)
            val videoCursor =
                (if (vdir == null) dao.queryAllVideos() else dao.queryAllVideosInDirectory(vdir, true))
                    ?: return null
            while (!isCancelled && videoCursor.moveToNext()) {
                val video = dao.buildVideo(videoCursor)
                if (video != null) {
                    if (videos == null)
                        videos = LinkedList()
                    videos.add(video)
                }
            }
            videos.sortByElementName()
            videoCursor.close()

            val items = videos.toVideoListItems(vdir) ?: return null
            return arrayOf(items, videos)
        }

        @Suppress("UNCHECKED_CAST")
        override fun onPostExecute(result: Array<*>?) {
            mNeedReloadVideos = false
            val videoItems = result?.get(0) as MutableList<VideoListItem>?
            val videos = result?.get(1) as List<Video>?
            onLoadFinish(videoItems)
            notifyOnVideoItemsLoadListeners(videoItems, videos)
        }
    }

    override fun setVideoListItems(items: List<VideoListItem>?) {
        if (items == null || items.isEmpty()) {
            if (mVideoListItems.isNotEmpty()) {
                mVideoListItems.clear()
                parentVideoDir?.computeSize()
                mCallback?.onAllItemsRemoved()
            }
        } else if (items.size == mVideoListItems.size) {
            var changed = false
            for (index in items.indices) {
                if (!items[index].allEqual(mVideoListItems[index])) {
                    changed = true
                    mVideoListItems[index] = items[index]
                    mCallback?.onItemUpdated(index)
                }
            }
            if (changed) {
                parentVideoDir?.computeSize()
            }
        } else {
            mVideoListItems.set(items)
            parentVideoDir?.computeSize()
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

    override fun updateVideoDirectory(videodir: VideoDirectory) {
        loop@ for ((i, item) in mVideoListItems.withIndex()) {
            if (item.path != videodir.path) continue@loop

            val dao = VideoListItemDao.getSingleton(mContext)
            when (videodir.videoCount()) {
                0 -> {
                    if (item is VideoDirectory) {
                        dao.deleteVideoDir(item.path)
                    }

                    mVideoListItems.removeAt(i)
                    parentVideoDir?.computeSize()
                    mCallback?.onItemRemoved(i)
                }

                else -> {
                    val compactVideoDir = videodir.videoListItems.size == 1
                            && videodir.videoListItems[0] is VideoDirectory
                    if (item is Video || compactVideoDir) {
                        if (compactVideoDir) {
                            val vd = videodir.videoListItems[0]
                            if (vd.isTopped) {
                                vd.isTopped = false
                                dao.setVideoListItemTopped(vd, false)
                            }
                            dao.deleteVideoDir(videodir.path)
                        } else {
                            if (videodir.isTopped) {
                                videodir.isTopped = false
                                dao.setVideoListItemTopped(videodir, false)
                            }
                        }

                        val newItem = if (compactVideoDir) videodir.videoListItems[0] else videodir
                        mVideoListItems[i] = newItem
                        parentVideoDir?.computeSize()
                        val newIndex = mVideoListItems.reordered().indexOf(newItem)
                        if (newIndex == i) {
                            mCallback?.onItemUpdated(i)
                        } else {
                            mVideoListItems.add(newIndex, mVideoListItems.removeAt(i))
                            mCallback?.onItemMoved(i, newIndex)
                        }
                    } else if (item is VideoDirectory) {
                        val oldFirstVideo = item.firstVideoOrNull()
                        val oldSize = item.size
                        val oldVideoCount = item.videoCount()

                        item.videoListItems = videodir.videoListItems
                        item.size = videodir.size
                        if (oldFirstVideo?.allEqual(videodir.firstVideoOrNull()) == false) {
                            mCallback?.onVideoDirThumbChanged(i)
                        }
                        if (oldSize != videodir.size || oldVideoCount != videodir.videoCount()) {
                            parentVideoDir?.computeSize()
                            mCallback?.onVideoDirVideoSizeOrCountChanged(i)
                        }
                    }
                }
            }
            // Necessary. Used to prevent ConcurrentModificationException from occurring in
            // the later element iteration of mVideoListItems after any its element is moved.
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
            parentVideoDir?.computeSize()
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
        if (start >= 0) {
            parentVideoDir?.computeSize()
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