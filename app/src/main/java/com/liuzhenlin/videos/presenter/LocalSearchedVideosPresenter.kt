/*
 * Created on 2023-4-4 2:14:00 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import com.liuzhenlin.common.adapter.HeaderAndFooterWrapper
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.common.utils.AlgorithmUtil
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.model.ILocalSearchedVideoListModel
import com.liuzhenlin.videos.model.LocalSearchedVideoListModel
import com.liuzhenlin.videos.model.OnLoadListener
import com.liuzhenlin.videos.model.OnVideosLoadListener
import com.liuzhenlin.videos.view.fragment.*
import java.util.*
import kotlin.math.abs
import kotlin.math.min

interface ILocalSearchedVideosPresenter : IPresenter<ILocalSearchedVideosView>,
        OnVideosLoadListener, ILocalSearchedVideoListModel.Callback {

    fun startLoadVideos()
    fun stopLoadVideos()

    fun refreshList(searchTextChanged: Boolean)

    fun isVideoWritable(index: Int): Boolean
    fun playVideoAt(index: Int)
    fun moveVideoAt(index: Int)
    fun deleteVideoAt(index: Int)
    fun renameVideoAt(index: Int)
    fun shareVideoAt(index: Int)
    fun viewDetailsOfVideoAt(index: Int)

    fun getSearchedVideoListAdapter()
            : HeaderAndFooterWrapper<out ILocalSearchedVideosView.SearchedVideoListViewHolder>

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)

    companion object {
        @JvmStatic
        fun newInstance(): ILocalSearchedVideosPresenter {
            return LocalSearchedVideosPresenter()
        }
    }
}

class LocalSearchedVideosPresenter : Presenter<ILocalSearchedVideosView>(),
        ILocalSearchedVideosPresenter {

    private val mAdapterWrapper = HeaderAndFooterWrapper(SearchedVideoListAdapter())

    private var mModel: LocalSearchedVideoListModel? = null

    override fun attachToView(view: ILocalSearchedVideosView) {
        super.attachToView(view)
        var model = mModel
        if (model == null) {
            model = LocalSearchedVideoListModel(mContext)
            model.addOnLoadListener(object : OnLoadListener<Nothing, MutableList<Video>?> {
                override fun onLoadStart() = onVideoItemsLoadStart()

                override fun onLoadFinish(result: MutableList<Video>?) =
                        onVideoItemsLoadFinish(result)

                override fun onLoadCanceled() = onVideoItemsLoadCanceled()
            })
            mModel = model
        }
        model.setCallback(this)
        model.setVideos(view.getArguments()?.getParcelableArrayList(KEY_VIDEOS) ?: model.videos)
    }

    override fun detachFromView(view: ILocalSearchedVideosView) {
        super.detachFromView(view)
        val videos = mModel?.videos ?: arrayListOf()
        view.onReturnResult(RESULT_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT,
                Intent().putParcelableArrayListExtra(
                        KEY_VIDEOS, videos as? ArrayList<Video> ?: ArrayList(videos)))
        mModel?.setCallback(null)
    }

    override fun startLoadVideos() {
        mModel?.startLoader()
    }

    override fun stopLoadVideos() {
        mModel?.stopLoader()
    }

    override fun onVideoItemsLoadStart() {
        mView?.onVideosLoadStart()
    }

    override fun onVideoItemsLoadFinish(videoItems: MutableList<Video>?) {
        mModel?.setVideos(videoItems)
        mView?.onVideosLoadFinish()
    }

    override fun onVideoItemsLoadCanceled() {
        mView?.onVideosLoadCanceled()
    }

    override fun onAllVideosChanged() = refreshList(false)

    override fun refreshList(searchTextChanged: Boolean) {
        val model = mModel ?: return

        var searchedVideos: MutableList<Video>? = null
        if (mView?.searchText?.isNotEmpty() == true) {
            for (video in model.videos) {
                if (mView?.searchText?.length
                        == AlgorithmUtil.lcs(video.name, mView?.searchText ?: "", true).length) {
                    if (searchedVideos == null) searchedVideos = mutableListOf()
                    searchedVideos.add(video)
                }
            }
        }
        if (searchedVideos == null || searchedVideos.isEmpty()) {
            model.clearSearchedVideos()
        } else if (searchedVideos.size == model.searchedVideos.size) {
            if (searchTextChanged) {
                val headersCount = mAdapterWrapper.headersCount
                for (index in searchedVideos.indices) {
                    if (!model.updateSearchedVideo(index, searchedVideos[index])) {
                        mAdapterWrapper.notifyItemChanged(
                                headersCount + index, Payloads.PAYLOAD_REFRESH_ITEM_NAME)
                    }
                }
            } else {
                for (index in searchedVideos.indices) {
                    model.updateSearchedVideo(index, searchedVideos[index])
                }
            }
        } else {
            model.setSearchedVideos(searchedVideos)
        }
    }

    override fun onAllSearchedVideosRemoved() = onAllSearchedVideosChanged()

    @SuppressLint("NotifyDataSetChanged")
    override fun onAllSearchedVideosChanged() {
        mAdapterWrapper.notifyDataSetChanged()
        mView?.updateListVisibilityAndSearchResultText()
    }

    override fun onSearchedVideoUpdated(index: Int) =
            mAdapterWrapper.notifyItemChanged(mAdapterWrapper.headersCount + index)

    override fun onSearchedVideoProgressChanged(index: Int) =
            mAdapterWrapper.notifyItemChanged(mAdapterWrapper.headersCount + index,
                    Payloads.PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION)

    override fun onSearchedVideoDeleted(index: Int) {
        val position = mAdapterWrapper.headersCount + index
        mAdapterWrapper.notifyItemRemoved(position)
        mAdapterWrapper.notifyItemRangeChanged(position, mAdapterWrapper.itemCount - 1)
        mView?.updateListVisibilityAndSearchResultText()
    }

    override fun onSearchedVideoRenamed(oldIndex: Int, index: Int) {
        if (index == oldIndex) {
            mAdapterWrapper.notifyItemChanged(mAdapterWrapper.headersCount + oldIndex,
                    Payloads.PAYLOAD_REFRESH_ITEM_NAME)
        } else {
            onSearchedVideoMoved(oldIndex, index)
        }
    }

    override fun onSearchedVideoMoved(index: Int, newIndex: Int) {
        val headersCount = mAdapterWrapper.headersCount
        val from = headersCount + index
        val to = headersCount + newIndex
        mAdapterWrapper.notifyItemRemoved(from)
        mAdapterWrapper.notifyItemInserted(to)
        mAdapterWrapper.notifyItemRangeChanged(min(from, to), abs(to - from) + 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_PLAY_VIDEO -> if (resultCode == RESULT_CODE_PLAY_VIDEO) {
                val video = data?.getParcelableExtra<Video>(KEY_VIDEO) ?: return
                mModel?.updateSearchedVideoProgress(video)
            }
        }
    }

    override fun isVideoWritable(index: Int) =
            mModel?.searchedVideos?.get(index)?.isWritable == true

    override fun playVideoAt(index: Int) {
        mView.playVideo(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun moveVideoAt(index: Int) {
        mView?.showVideosMovePage(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun deleteVideoAt(index: Int) {
        val video = mModel?.searchedVideos?.get(index) ?: return
        mView?.showDeleteItemDialog(video) {
            mModel?.deleteVideo(video)
        }
    }

    override fun renameVideoAt(index: Int) {
        val video = mModel?.searchedVideos?.get(index) ?: return
        mView?.showRenameItemDialog(video) {
            mModel?.renameVideoTo(video,
                    mView?.searchText?.length
                            == AlgorithmUtil.lcs(video.name, mView?.searchText ?: "", true).length)
        }
    }

    override fun shareVideoAt(index: Int) {
        mView.shareVideo(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun viewDetailsOfVideoAt(index: Int) {
        mView?.showItemDetailsDialog(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun getSearchedVideoListAdapter() = mAdapterWrapper

    private inner class SearchedVideoListAdapter
        : ImageLoadingListAdapter<ILocalSearchedVideosView.SearchedVideoListViewHolder>() {

        override fun getItemCount() = mModel?.searchedVideos?.size ?: 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                mView.newSearchedVideoListViewHolder(parent)

        override fun onBindViewHolder(holder: ILocalSearchedVideosView.SearchedVideoListViewHolder,
                                      position: Int, payloads: MutableList<Any>) {
            super.onBindViewHolder(holder, position, payloads)
            holder.bindData(mModel?.searchedVideos?.get(position) ?: return, position, payloads)
        }

        override fun cancelLoadingItemImages(
                holder: ILocalSearchedVideosView.SearchedVideoListViewHolder) {
            holder.cancelLoadingItemImages()
        }

        override fun loadItemImages(holder: ILocalSearchedVideosView.SearchedVideoListViewHolder) {
            val searchedVideos = mModel?.searchedVideos ?: return
            holder.loadItemImages(
                    searchedVideos[holder.bindingAdapterPosition - mAdapterWrapper.headersCount])
        }
    }
}
