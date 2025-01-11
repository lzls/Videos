/*
 * Created on 2023-3-16 4:22:46 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.videos.KEY_MOVED
import com.liuzhenlin.videos.KEY_SELECTION
import com.liuzhenlin.videos.KEY_VIDEODIRS
import com.liuzhenlin.videos.KEY_VIDEOS
import com.liuzhenlin.videos.RESULT_CODE_VIDEO_MOVE_FRAGMENT
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.firstVideoOrNull
import com.liuzhenlin.videos.model.VideoMoveRepository
import com.liuzhenlin.videos.view.fragment.IVideoMoveView

interface IVideoMovePresenter : IPresenter<IVideoMoveView> {

    fun restoreInstanceState(savedInstanceState: Bundle?)
    fun saveInstanceState(outState: Bundle)

    fun moveVideosToCheckedDir()

    fun onVideoMovePromptConfirmed(neverPromptAgain: Boolean)

    fun toggleTargetDirChecked(index: Int)

    companion object {
        @JvmStatic
        fun <T> getImplClass(): Class<T> where T : Presenter<IVideoMoveView>, T : IVideoMovePresenter {
            @Suppress("UNCHECKED_CAST")
            return VideoMovePresenter::class.java as Class<T>
        }
    }
}

class VideoMovePresenter : Presenter<IVideoMoveView>(), IVideoMovePresenter,
        VideoMoveRepository.Callback {

    private var mVideoMoveRepository: VideoMoveRepository? = null

    override fun attachToView(view: IVideoMoveView) {
        super.attachToView(view)
        val args = view.getArguments()
        val targetDirs: Array<VideoDirectory?>
        val parcels = args?.getParcelableArray(KEY_VIDEODIRS)
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
        val videos: Array<VideoListItem> =
                args?.getParcelableArrayList<VideoListItem>(KEY_VIDEOS)?.toTypedArray() ?: arrayOf()
        @Suppress("UNCHECKED_CAST")
        mVideoMoveRepository = VideoMoveRepository.create(
                mContext, videos, targetDirs as Array<VideoDirectory>)
        mVideoMoveRepository!!.setCallback(this)
    }

    override fun detachFromView(view: IVideoMoveView) {
        mVideoMoveRepository?.dispose()
        mVideoMoveRepository = null
        super.detachFromView(view)
    }

    override fun onViewCreated(view: IVideoMoveView) {
        super<Presenter>.onViewCreated(view)
        mView?.init(TargetDirListAdapter(), mVideoMoveRepository?.videoQuantity ?: 0)
    }

    override fun restoreInstanceState(savedInstanceState: Bundle?) {
        mVideoMoveRepository?.let {
            if (savedInstanceState != null) {
                val checkedPosition = savedInstanceState.getInt(KEY_SELECTION, -1)
                if (checkedPosition >= 0) {
                    it.setTargetDirChecked(checkedPosition, true)
                }
            }
        }
    }

    override fun saveInstanceState(outState: Bundle) {
        val repository = mVideoMoveRepository
        if (repository != null) {
            for (index in repository.targetDirs.indices) {
                if (repository.isTargetDirChecked(index)) {
                    outState.putInt(KEY_SELECTION, index)
                    return
                }
            }
            outState.remove(KEY_SELECTION)
        }
    }

    override fun moveVideosToCheckedDir() {
        val repository = mVideoMoveRepository
        if (repository != null && repository.needShowVideoMovePromptDialog()) {
            mView?.showVideoMovePromptDialog()
        } else {
            repository?.moveVideosToCheckedDir()
        }
    }

    override fun onVideoMovePromptConfirmed(neverPromptAgain: Boolean) {
        if (neverPromptAgain) {
            mVideoMoveRepository?.setVideoMovePromptDialogNeedBeShown(false)
        }
        mVideoMoveRepository?.moveVideosToCheckedDir()
    }

    override fun onVideoMoveStart() {
        mView?.onVideoMoveStart()
    }

    override fun onVideoMoveFinish(moved: Boolean) {
        mView?.run {
            onReturnResult(RESULT_CODE_VIDEO_MOVE_FRAGMENT, Intent().putExtra(KEY_MOVED, moved))
            onVideoMoveFinish(moved)
        }
    }

    override fun toggleTargetDirChecked(index: Int) {
        val repository = mVideoMoveRepository
        repository?.setTargetDirChecked(index, !repository.isTargetDirChecked(index))
    }

    override fun onTargetDirCheckedChanged(index: Int, checked: Boolean) {
        mView?.setTargetDirListItemChecked(index, checked)
    }

    override fun onCheckedTargetDirCountChanged(index: Int) {
        mView?.onCheckedTargetDirListItemCountChanged(index)
    }

    private inner class TargetDirListAdapter
        : ImageLoadingListAdapter<IVideoMoveView.TargetDirListViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
                : IVideoMoveView.TargetDirListViewHolder {
            return mView.newTargetDirListViewHolder(parent)
        }

        override fun getItemCount() = mVideoMoveRepository?.targetDirs?.size ?: 0

        override fun onBindViewHolder(holder: IVideoMoveView.TargetDirListViewHolder, position: Int,
                                      payloads: MutableList<Any>) {
            super.onBindViewHolder(holder, position, payloads)
            holder.bindData(mVideoMoveRepository!!.targetDirs[position], position, payloads)
        }

        override fun cancelLoadingItemImages(holder: IVideoMoveView.TargetDirListViewHolder) {
            holder.cancelLoadingItemImages()
        }

        override fun loadItemImages(holder: IVideoMoveView.TargetDirListViewHolder) {
            val videodir = mVideoMoveRepository!!.targetDirs[holder.bindingAdapterPosition]
            holder.loadItemImages(videodir.firstVideoOrNull()!!)
        }
    }
}