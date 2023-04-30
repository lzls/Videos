/*
 * Created on 2023-3-16 4:22:46 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter

import android.os.Bundle
import android.view.ViewGroup
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.common.utils.LateinitProperty
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.dao.VideoListItemDao
import com.liuzhenlin.videos.view.fragment.IVideoMoveView
import java.io.File

interface IVideoMovePresenter : IPresenter<IVideoMoveView> {

    fun restoreData(savedInstanceState: Bundle?)
    fun saveData(outState: Bundle)

    public val videoQuantity: Int

    fun moveVideos(): Boolean

    fun isTargetDirChecked(index: Int): Boolean
    fun setTargetDirChecked(index: Int, checked: Boolean)

    fun newTargetDirListAdapter(): ImageLoadingListAdapter<out IVideoMoveView.TargetDirListViewHolder>

    companion object {
        @JvmStatic
        fun newInstance(): IVideoMovePresenter {
            return VideoMovePresenter()
        }
    }
}

class VideoMovePresenter : Presenter<IVideoMoveView>(), IVideoMovePresenter {

    private val mTargetDirs = object : LateinitProperty<Array<VideoDirectory>>() {
        override fun initialize(): Array<VideoDirectory> {
            val targetDirs: Array<VideoDirectory?>
            val parcels = mView?.getArguments()?.getParcelableArray(KEY_VIDEODIRS)
            if (parcels != null) {
                targetDirs = arrayOfNulls(parcels.size)
                for (i in parcels.indices) {
                    val videodir = parcels[i] as VideoDirectory
                    videodir.isChecked = false
                    targetDirs[i] = videodir
                }
            } else {
                targetDirs = arrayOf()
            }
            @Suppress("UNCHECKED_CAST")
            return targetDirs as Array<VideoDirectory>
        }
    }

    private val mVideos = object : LateinitProperty<List<VideoListItem>>() {
        override fun initialize(): List<VideoListItem> {
            return mView?.getArguments()?.getParcelableArrayList(KEY_VIDEOS) ?: listOf()
        }
    }

    override fun attachToView(view: IVideoMoveView) {
        super.attachToView(view)
        mTargetDirs.reset()
        mVideos.reset()
    }

    override fun restoreData(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val checkedPosition = savedInstanceState.getInt(KEY_SELECTION, -1)
            if (checkedPosition >= 0) {
                setTargetDirChecked(checkedPosition, true)
            }
        }
    }

    override fun saveData(outState: Bundle) {
        val videodirs = mTargetDirs.getNoInitialize()
        if (videodirs != null) {
            for (index in videodirs.indices) {
                if (videodirs[index].isChecked) {
                    outState.putInt(KEY_SELECTION, index)
                    return
                }
            }
            outState.remove(KEY_SELECTION)
        }
    }

    override val videoQuantity: Int
        get() {
            val videos = mVideos.get()
            return if (videos.size == 1
                    && (videos[0] is Video || (videos[0] as VideoDirectory).videos.size == 1)) {
                1
            } else {
                1.inv()
            }
        }

    override fun moveVideos(): Boolean {
        for (videodir in mTargetDirs.get()) {
            if (videodir.isChecked) {
                for (video in mVideos.get()) {
                    if (video is VideoDirectory) {
                        for (v in video.videos) {
                            moveVideoTo(v, videodir)
                        }
                    } else {
                        val v = video as Video
                        moveVideoTo(v, videodir)
                    }
                }
                return true
            }
        }
        return false
    }

    private fun moveVideoTo(video: Video, videodir: VideoDirectory) {
        val videoPath = video.path
        for (v in videodir.videos) {
            if (v.name == video.name) {
                if (v.path == videoPath)
                    return

                var i = 1
                var videoName: String
                outer@
                while (true) {
                    for (v2 in videodir.videos) {
                        videoName = v.title + '-' + i + v.suffix
                        if (v2.name == videoName) {
                            i++
                            break
                        }
                        break@outer
                    }
                }
                video.name = videoName
                video.path = video.path.replaceAfterLast(File.separatorChar, video.name)
                break
            }
        }
        video.path = video.path.replaceBeforeLast(File.separatorChar, videodir.path)
        File(videoPath).renameTo(File(video.path))
        VideoListItemDao.getSingleton(mContext).updateVideo(video)
    }

    override fun isTargetDirChecked(index: Int): Boolean =
        mTargetDirs.get()[index].isChecked

    override fun setTargetDirChecked(index: Int, checked: Boolean) {
        val targetDirs = mTargetDirs.get()
        if (targetDirs[index].isChecked != checked) {
            targetDirs[index].isChecked = checked
            mView?.setTargetDirListItemChecked(index, checked)
        }
    }

    override fun newTargetDirListAdapter()
            : ImageLoadingListAdapter<out IVideoMoveView.TargetDirListViewHolder> {
        return TargetDirListAdapter()
    }

    private inner class TargetDirListAdapter
        : ImageLoadingListAdapter<IVideoMoveView.TargetDirListViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
                : IVideoMoveView.TargetDirListViewHolder {
            return mView.newTargetDirListViewHolder(parent)
        }

        override fun getItemCount() = mTargetDirs.get().size

        override fun onBindViewHolder(holder: IVideoMoveView.TargetDirListViewHolder, position: Int,
                                      payloads: MutableList<Any>) {
            super.onBindViewHolder(holder, position, payloads)
            holder.bindData(mTargetDirs.get()[position], position, payloads)
        }

        override fun cancelLoadingItemImages(holder: IVideoMoveView.TargetDirListViewHolder) {
            holder.cancelLoadingItemImages()
        }

        override fun loadItemImages(holder: IVideoMoveView.TargetDirListViewHolder) {
            holder.loadItemImages(mTargetDirs.get()[holder.bindingAdapterPosition].videos[0])
        }
    }
}