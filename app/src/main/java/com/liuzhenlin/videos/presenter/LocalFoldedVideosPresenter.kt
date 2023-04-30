/*
 * Created on 2023-4-3 10:19:24 AM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.model.*
import com.liuzhenlin.videos.view.fragment.*
import com.liuzhenlin.videos.view.fragment.Payloads.*
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.min

interface ILocalFoldedVideosPresenter : IPresenter<ILocalFoldedVideosView>, OnReloadVideosListener,
        ILocalFoldedVideoListModel.Callback {

    public val videoDirName: String?

    fun startLoadVideos()
    fun stopLoadVideos()

    fun isVideoChecked(index: Int): Boolean
    fun setVideoChecked(index: Int, checked: Boolean)

    fun isVideoTopped(index: Int): Boolean
    fun setVideoTopped(index: Int, topped: Boolean)

    fun selectAllVideos()
    fun unselectAllVideos()
    fun moveCheckedVideos()
    fun deleteVideo(index: Int)
    fun deleteCheckedVideos()
    fun renameCheckedVideo()
    fun shareCheckedVideo()
    fun viewCheckedVideoDetails()
    fun playAllVideos(selection: Int)

    fun getVideoListAdapter(): ImageLoadingListAdapter<out ILocalFoldedVideosView.VideoListViewHolder>

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)

    companion object {
        @JvmStatic
        fun newInstance(): ILocalFoldedVideosPresenter {
            return LocalFoldedVideosPresenter()
        }
    }
}

class LocalFoldedVideosPresenter : Presenter<ILocalFoldedVideosView>(), ILocalFoldedVideosPresenter {

    private var mModel: LocalFoldedVideoListModel? = null

    private val mAdapter = VideoListAdapter()

    override val videoDirName: String?
        get() = mModel?.videodir?.name

    override fun attachToView(view: ILocalFoldedVideosView) {
        super.attachToView(view)
        val videodir = view.getArguments()?.get(KEY_VIDEODIR) as? VideoDirectory ?: VideoDirectory()
        mModel = LocalFoldedVideoListModel(videodir, mContext)
        mModel!!.addOnLoadListener(object : OnLoadListener<Nothing, MutableList<Video>?> {
            override fun onLoadStart() {
                mView?.onVideosLoadStart()
            }

            override fun onLoadFinish(result: MutableList<Video>?) {
                onReloadDirectoryVideos(result)
                mView?.onVideosLoadFinish()
            }

            override fun onLoadCanceled() {
                mView?.onVideosLoadCanceled()
            }
        })
        mModel!!.setCallback(this)
    }

    override fun detachFromView(view: ILocalFoldedVideosView) {
        super.detachFromView(view)
        val videos = mModel?.videos ?: arrayListOf()
        view.onReturnResult(RESULT_CODE_LOCAL_FOLDED_VIDEOS_FRAGMENT,
                Intent().putExtra(KEY_DIRECTORY_PATH, mModel?.videodir?.path)
                        .putParcelableArrayListExtra(
                                KEY_VIDEOS, videos as? ArrayList<Video> ?: ArrayList(videos)))
        mModel?.setCallback(null)
    }

    override fun startLoadVideos() {
        mModel?.startLoader()
    }

    override fun stopLoadVideos() {
        mModel?.stopLoader()
    }

    override fun onReloadVideos(videos: MutableList<Video>?) =
            if (videos == null || videos.isEmpty()) {
                onReloadDirectoryVideos(null)
            } else {
                onReloadDirectoryVideos(
                        videos.filter {
                            it.path.substring(0, it.path.lastIndexOf(File.separatorChar))
                                    .equals(mModel?.videodir?.path, ignoreCase = true)
                        }.reordered())
            }

    private fun onReloadDirectoryVideos(videos: List<Video>?) {
        mModel?.setVideos(videos)
        if (mView?.isVideoSelectControlsShown() == true) {
            mView?.hideVideoSelectControls()
        }
    }

    override fun onAllVideosRemoved() = onAllVideosChanged()

    @SuppressLint("NotifyDataSetChanged")
    override fun onAllVideosChanged() = mAdapter.notifyDataSetChanged()

    override fun onVideoUpdated(index: Int) = mAdapter.notifyItemChanged(index)

    override fun onVideoProgressChanged(index: Int) =
            mAdapter.notifyItemChanged(index, PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION)

    override fun onVideoToppedChanged(oldIndex: Int, index: Int) {
        if (index == oldIndex) {
            mAdapter.notifyItemChanged(oldIndex, PAYLOAD_CHANGE_ITEM_LPS_AND_BG)
        } else {
            onVideoMoved(oldIndex, index)
        }
    }

    override fun onVideoCheckedChanged(index: Int) {
        mAdapter.notifyItemChanged(index, PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)

        var checkedVideosCount = 0
        var hasUnwritableCheckedVideo = false
        for (video in mModel?.videos ?: return) {
            if (video.isChecked) {
                checkedVideosCount++
                if (!hasUnwritableCheckedVideo && !video.isWritable) {
                    hasUnwritableCheckedVideo = true
                }
            }
        }
        mView?.onVideoCheckedChange(checkedVideosCount, hasUnwritableCheckedVideo)
    }

    override fun onVideoRemoved(index: Int) {
        mAdapter.notifyItemRemoved(index)
        mAdapter.notifyItemRangeChanged(index, mAdapter.itemCount - index)
    }

    override fun onVideoRenamed(oldIndex: Int, index: Int) {
        if (index == oldIndex) {
            mAdapter.notifyItemChanged(oldIndex, PAYLOAD_REFRESH_ITEM_NAME)
        } else {
            onVideoMoved(oldIndex, index)
        }
    }

    override fun onVideoMoved(index: Int, newIndex: Int) {
        mAdapter.notifyItemRemoved(index)
        mAdapter.notifyItemInserted(newIndex)
        mAdapter.notifyItemRangeChanged(min(index, newIndex), abs(newIndex - index) + 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_PLAY_VIDEO -> if (resultCode == RESULT_CODE_PLAY_VIDEO) {
                val video = data?.getParcelableExtra<Video>(KEY_VIDEO) ?: return
                mModel?.updateVideoProgress(video)
            }
            REQUEST_CODE_PLAY_VIDEOS -> if (resultCode == RESULT_CODE_PLAY_VIDEOS) {
                val parcelables = data?.getParcelableArrayExtra(KEY_VIDEOS) ?: return
                val videos = Array(parcelables.size) { parcelables[it] as Video }
                for (video in videos) {
                    mModel?.updateVideoProgress(video)
                }
            }
        }
    }

    override fun isVideoChecked(index: Int): Boolean =
            mModel?.videos?.get(index)?.isChecked == true

    override fun setVideoChecked(index: Int, checked: Boolean) {
        mModel?.setVideoChecked(index, checked)
    }

    override fun isVideoTopped(index: Int): Boolean =
            mModel?.videos?.get(index)?.isTopped == true

    override fun setVideoTopped(index: Int, topped: Boolean) {
        mModel?.setVideoTopped(index, topped)
    }

    override fun selectAllVideos() {
        mModel?.setAllVideosChecked()
    }

    override fun unselectAllVideos() {
        mModel?.setAllVideosUnchecked()
    }

    override fun moveCheckedVideos() {
        val videos = mModel?.checkedVideos ?: return
        mView?.showVideosMovePage(*videos.toTypedArray())
    }

    override fun deleteVideo(index: Int) {
        val video = mModel?.videos?.get(index) ?: return
        mView?.showDeleteItemDialog(video) {
            mModel?.deleteVideo(video)
        }
    }

    override fun deleteCheckedVideos() {
        val videos = mModel?.checkedVideos ?: return
        if (videos.size == 1) {
            mView?.showDeleteItemsPopupWindow(videos[0]) {
                mView?.hideVideoSelectControls()
                mModel?.deleteVideo(videos[0])
            }
        } else {
            val videosArray = videos.toTypedArray()
            mView?.showDeleteItemsPopupWindow(*videosArray) {
                mView?.hideVideoSelectControls()
                mModel?.deleteVideos(*videosArray)
            }
        }
    }

    override fun renameCheckedVideo() {
        val video = mModel?.checkedVideos?.get(0) ?: return
        mView?.hideVideoSelectControls()
        mView?.showRenameItemDialog(video) {
            mModel?.renameVideoTo(video)
        }
    }

    override fun shareCheckedVideo() {
        val video = mModel?.checkedVideos?.get(0) ?: return
        mView?.hideVideoSelectControls()
        mView.shareVideo(video)
    }

    override fun viewCheckedVideoDetails() {
        val video = mModel?.checkedVideos?.get(0) ?: return
        mView?.hideVideoSelectControls()
        mView?.showItemDetailsDialog(video)
    }

    override fun playAllVideos(selection: Int) {
        val model = mModel ?: return
        if (model.videos.size == 1) {
            mView.playVideo(model.videos[0])
        } else {
            mView.playVideos(*model.videos.toTypedArray(), selection = selection)
        }
    }

    override fun getVideoListAdapter()
            : ImageLoadingListAdapter<out ILocalFoldedVideosView.VideoListViewHolder> = mAdapter

    private inner class VideoListAdapter
        : ImageLoadingListAdapter<ILocalFoldedVideosView.VideoListViewHolder>() {

        override fun getItemCount() = mModel?.videos?.size ?: 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                mView.newVideoListViewHolder(parent)

        override fun onBindViewHolder(holder: ILocalFoldedVideosView.VideoListViewHolder,
                                      position: Int, payloads: MutableList<Any>) {
            super.onBindViewHolder(holder, position, payloads)
            holder.bindData(mModel?.videos?.get(position) ?: return, position, payloads)
        }

        override fun cancelLoadingItemImages(holder: ILocalFoldedVideosView.VideoListViewHolder) {
            holder.cancelLoadingItemImages()
        }

        override fun loadItemImages(holder: ILocalFoldedVideosView.VideoListViewHolder) {
            val videos = mModel?.videos ?: return
            holder.loadItemImages(videos[holder.bindingAdapterPosition])
        }
    }
}