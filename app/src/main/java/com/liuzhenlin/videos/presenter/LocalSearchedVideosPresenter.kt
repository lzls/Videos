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
        OnVideosLoadListener {

    fun startLoadVideos()
    fun stopLoadVideos()

    fun refreshList(searchText: String)

    fun showVideoOptionsMenu(index: Int, callback: ((Video) -> Unit)?)
    fun playVideoAt(index: Int)
    fun moveVideoAt(index: Int)
    fun deleteVideoAt(index: Int)
    fun renameVideoAt(index: Int)
    fun shareVideoAt(index: Int)
    fun viewDetailsOfVideoAt(index: Int)

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)

    companion object {
        @JvmStatic
        fun <T> getImplClass(): Class<T> where T : Presenter<ILocalSearchedVideosView>,
                                               T : ILocalSearchedVideosPresenter {
            @Suppress("UNCHECKED_CAST")
            return LocalSearchedVideosPresenter::class.java as Class<T>
        }
    }
}

class LocalSearchedVideosPresenter : Presenter<ILocalSearchedVideosView>(),
        ILocalSearchedVideosPresenter, ILocalSearchedVideoListModel.Callback {

    // Must be set to null when the associated view is destroyed, or memory leak of
    // the view instance would occur due to ViewModel lifecycle mechanism.
    private var mAdapterWrapper: SearchedVideoListAdapterWrapper? = null

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
            model.setCallback(this)
            mModel = model
        }
        model.setVideos(view.getArguments()?.getParcelableArrayList(KEY_VIDEOS) ?: emptyList())
    }

    override fun detachFromView(view: ILocalSearchedVideosView) {
        stopLoadVideos()
        mModel?.run {
            view.getArguments()?.putParcelableArrayList(
                    KEY_VIDEOS, videos as? ArrayList<Video> ?: ArrayList(videos))
        }
        super.detachFromView(view)
    }

    override fun onCleared() {
        super.onCleared()
        val videos = mModel?.videos ?: arrayListOf()
        mView?.onReturnResult(RESULT_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT,
                Intent().putParcelableArrayListExtra(
                        KEY_VIDEOS, videos as? ArrayList<Video> ?: ArrayList(videos)))
        mModel?.dispose()
        mModel = null
    }

    override fun onViewCreated(view: ILocalSearchedVideosView) {
        super<Presenter>.onViewCreated(view)
        mAdapterWrapper = HeaderAndFooterWrapper(SearchedVideoListAdapter()).also { view.init(it) }
    }

    override fun onViewDestroyed(view: ILocalSearchedVideosView) {
        super.onViewDestroyed(view)
        mAdapterWrapper = null
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

    override fun refreshList(searchText: String) {
        mModel?.setSearchText(searchText)
    }

    override fun onAllSearchedVideosRemoved() = onAllSearchedVideosChanged()

    @SuppressLint("NotifyDataSetChanged")
    override fun onAllSearchedVideosChanged() {
        mAdapterWrapper?.notifyDataSetChanged()
        mView?.updateListVisibilityAndSearchResultText()
    }

    override fun onSearchedVideoUpdated(index: Int) {
        mAdapterWrapper?.let { adapter ->
            adapter.notifyItemChanged(adapter.headersCount + index)
        }
    }

    override fun onSearchedVideoProgressChanged(index: Int) {
        mAdapterWrapper?.let { adapter ->
            adapter.notifyItemChanged(
                    adapter.headersCount + index, Payloads.PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION)
        }
    }

    override fun onSearchedVideoDeleted(index: Int) {
        mAdapterWrapper?.let { adapter ->
            val position = adapter.headersCount + index
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, adapter.itemCount - 1)
            mView?.updateListVisibilityAndSearchResultText()
        }
    }

    override fun onSearchedVideoRenamed(oldIndex: Int, index: Int) {
        if (index == oldIndex) {
            mAdapterWrapper?.let { adapter ->
                adapter.notifyItemChanged(
                        adapter.headersCount + oldIndex, Payloads.PAYLOAD_REFRESH_ITEM_NAME)
            }
        } else {
            onSearchedVideoMoved(oldIndex, index)
        }
    }

    override fun onSearchedVideoMoved(index: Int, newIndex: Int) {
        mAdapterWrapper?.let { adapter ->
            val headersCount = adapter.headersCount
            val from = headersCount + index
            val to = headersCount + newIndex
            adapter.notifyItemRemoved(from)
            adapter.notifyItemInserted(to)
            adapter.notifyItemRangeChanged(min(from, to), abs(to - from) + 1)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_PLAY_VIDEO -> if (resultCode == RESULT_CODE_PLAY_VIDEO) {
                val video = data?.getParcelableExtra<Video>(KEY_VIDEO) ?: return
                mModel?.updateSearchedVideoProgress(video)
            }
        }
    }

    override fun showVideoOptionsMenu(index: Int, callback: ((Video) -> Unit)?) {
        callback?.invoke(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun playVideoAt(index: Int) {
        mView?.playVideo(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun moveVideoAt(index: Int) {
        mView?.showVideosMovePage(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun deleteVideoAt(index: Int) {
        val video = mModel?.searchedVideos?.get(index) ?: return
        val view = mView
        view?.showDeleteItemDialog(video) {
            mModel?.deleteVideo(video, view)
        }
    }

    override fun renameVideoAt(index: Int) {
        var video = mModel?.searchedVideos?.get(index) ?: return
        val view = mView
        view?.showRenameItemDialog(video) { newName ->
            video = video.shallowCopy()
            video.name = newName
            mModel?.renameVideoTo(video, view)
        }
    }

    override fun shareVideoAt(index: Int) {
        mView?.shareVideo(mModel?.searchedVideos?.get(index) ?: return)
    }

    override fun viewDetailsOfVideoAt(index: Int) {
        mView?.showItemDetailsDialog(mModel?.searchedVideos?.get(index) ?: return)
    }

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
            val position = holder.bindingAdapterPosition - (mAdapterWrapper?.headersCount ?: return)
            holder.loadItemImages(searchedVideos[position])
        }
    }
}
