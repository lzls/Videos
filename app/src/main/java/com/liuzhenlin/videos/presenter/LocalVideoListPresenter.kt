/*
 * Created on 2023-4-5 5:01:38 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.model.ILocalVideoListModel
import com.liuzhenlin.videos.model.LocalVideoListModel
import com.liuzhenlin.videos.model.OnLoadListener
import com.liuzhenlin.videos.model.OnVideoItemsLoadListener
import com.liuzhenlin.videos.model.OnVideoListItemsLoadListener
import com.liuzhenlin.videos.presenter.ILocalVideoListPresenter.VideoListAdapter.Companion.PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT
import com.liuzhenlin.videos.presenter.ILocalVideoListPresenter.VideoListAdapter.Companion.PAYLOAD_REFRESH_VIDEODIR_THUMB
import com.liuzhenlin.videos.view.fragment.*
import com.liuzhenlin.videos.view.fragment.Payloads.*
import java.io.File
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.min

interface ILocalVideoListPresenter : IPresenter<ILocalVideoListView>,
        OnVideoListItemsLoadListener {

    fun setArgsForLocalSearchedVideosFragment(localSearchedVideosFragment: Fragment)

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)

    fun addOnVideoItemsLoadListener(listener: OnVideoItemsLoadListener<*>)
    fun removeOnVideoItemsLoadListener(listener: OnVideoItemsLoadListener<*>)

    fun startLoadVideos()
    fun stopLoadVideos()

    fun setItemChecked(index: Int, checked: Boolean)
    fun toggleItemChecked(index: Int)

    fun toggleItemTopped(index: Int)

    fun playVideoAt(index: Int)
    fun openVideoDirectoryAt(index: Int)
    fun deleteItemAt(index: Int, needUserConfirm: Boolean)
    fun deleteItem(item: VideoListItem, needUserConfirm: Boolean)
    fun moveCheckedItems()
    fun moveItems(vararg items: VideoListItem)
    fun deleteCheckedItems(needUserConfirm: Boolean)
    fun deleteItems(vararg items: VideoListItem, needUserConfirm: Boolean)
    fun renameCheckedItem()
    fun renameItem(item: VideoListItem)
    fun renameItemTo(item: VideoListItem)
    fun shareCheckedVideo()
    fun viewCheckedItemDetails()

    fun selectAllItems()
    fun unselectAllItems()

    abstract class VideoListAdapter<VH : ILocalVideoListView.VideoListViewHolder>
        : ImageLoadingListAdapter<VH>() {

        companion object {
            const val PAYLOAD_REFRESH_VIDEODIR_THUMB = PAYLOAD_LAST shl 1
            const val PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT = PAYLOAD_LAST shl 2

            const val VIEW_TYPE_VIDEODIR = 1
            const val VIEW_TYPE_VIDEO = 2
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): ILocalVideoListPresenter {
            return LocalVideoListPresenter()
        }
    }
}

class LocalVideoListPresenter : Presenter<ILocalVideoListView>(), ILocalVideoListPresenter,
        ILocalVideoListModel.Callback {

    private inline val mSublist: Boolean
        get() = mModel.parentVideoDir != null

    private var mViewsCreated = false

    private var _mModel: LocalVideoListModel? = null
    private val mModel: LocalVideoListModel
        get() {
            if (_mModel == null) {
                _mModel = LocalVideoListModel(App.getInstanceUnsafe()!!)
            }
            return _mModel!!
        }

    private var mVideosLoadListener: OnLoadListener<Nothing, MutableList<VideoListItem>?>? = null

    private val mAdapter = VideoListAdapter()

    private var mParent: LocalVideoListPresenter? = null

    internal fun setParentPresenter(parent: LocalVideoListPresenter?) {
        mParent = parent
    }

    override fun attachToView(view: ILocalVideoListView) {
        super.attachToView(view)
        if (view.getArguments()?.containsKey(KEY_VIDEODIR) == true) {
            _mModel = LocalVideoListModel(mContext, view.getArguments()?.getParcelable(KEY_VIDEODIR))
        }
        mModel.setCallback(this)
        mModel.addOnLoadListener(object : OnLoadListener<Nothing, MutableList<VideoListItem>?> {
            init {
                mVideosLoadListener = this
            }

            override fun onLoadStart() {
                mView?.onVideosLoadStart()
            }

            override fun onLoadFinish(result: MutableList<VideoListItem>?) {
                mModel.setVideoListItems(result)
                mView?.onVideosLoadFinish()
            }

            override fun onLoadCanceled() {
                mView?.onVideosLoadCanceled()
            }
        })
    }

    override fun detachFromView(view: ILocalVideoListView) {
        super.detachFromView(view)
        if (mSublist) {
            view.onReturnResult(
                    RESULT_CODE_LOCAL_VIDEO_SUBLIST_FRAGMENT,
                    Intent().putExtra(KEY_VIDEODIR, mModel.parentVideoDir))
        }
        mModel.setCallback(null)
        mVideosLoadListener?.let {
            mModel.removeOnLoadListener(it)
            mVideosLoadListener = null
        }
    }

    override fun onViewCreated(view: ILocalVideoListView) {
        super<Presenter>.onViewCreated(view)
        view.init(mSublist, mModel.parentVideoDir?.name, mModel.parentVideoDir?.path, mAdapter)
    }

    override fun onViewStart(view: ILocalVideoListView) {
        super.onViewStart(view)
        if (!mSublist) {
            mModel.stopWatchingVideos(true)
            // Make sure to load the videos after all restored Fragments have created their views,
            // otherwise the application will crash when the video loading callback is sent to one
            // of the upper Fragments because its UI controls will probably not have been initialized.
            if (!mViewsCreated) {
                startLoadVideos()
            }
        }
        mViewsCreated = true
    }

    override fun onViewStopped(view: ILocalVideoListView) {
        super.onViewStopped(view)
        if (!mSublist && !view.isDestroying) {
            mModel.startWatchingVideos()
        }
    }

    override fun onViewDestroyed(view: ILocalVideoListView) {
        super.onViewDestroyed(view)
        mViewsCreated = false
        if (!mSublist)
            mModel.stopWatchingVideos(false)
        stopLoadVideos()
    }

    override fun startLoadVideos() = mModel.startLoader()

    override fun stopLoadVideos() = mModel.stopLoader()

    override fun addOnVideoItemsLoadListener(listener: OnVideoItemsLoadListener<*>) =
            mModel.addOnVideoItemsLoadListener(listener)

    override fun removeOnVideoItemsLoadListener(listener: OnVideoItemsLoadListener<*>) =
            mModel.removeOnVideoItemsLoadListener(listener)

    override fun onVideoItemsLoadStart() {
        mView?.onVideosLoadStart()
    }

    override fun onVideoItemsLoadFinish(videoItems: MutableList<VideoListItem>?) {
        val parentVideoDir = mModel.parentVideoDir
        val items: List<VideoListItem>? =
                if (parentVideoDir == null) {
                    videoItems
                } else {
                    var items: List<VideoListItem>? = null
                    val predicate: ((VideoListItem) -> Boolean) = {
                        var ret = false
                        if (it is VideoDirectory) {
                            if (it.path == parentVideoDir.path) {
                                items = it.videoListItems
                                ret = true
                            } else if (it.path.startsWith(parentVideoDir.path + File.separator)) {
                                items = listOf(it)
                                ret = true
                            }
                        }
                        ret
                    }
                    videoItems.search(predicate)
                    items
                }
        mModel.setVideoListItems(items)
        mView?.onVideosLoadFinish()
    }

    override fun onVideoItemsLoadCanceled() {
        mView?.onVideosLoadCanceled()
    }

    override fun setArgsForLocalSearchedVideosFragment(localSearchedVideosFragment: Fragment) {
        val args = Bundle()
        args.putParcelableArrayList(KEY_VIDEOS, mModel.videos.also { it?.deepCopy(it) })
        localSearchedVideosFragment.arguments = args
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_PLAY_VIDEO -> if (resultCode == RESULT_CODE_PLAY_VIDEO) {
                val video = data?.getParcelableExtra<Video>(KEY_VIDEO) ?: return
                mModel.updateVideoProgress(video)
            }
            REQUEST_CODE_PLAY_VIDEOS -> if (resultCode == RESULT_CODE_PLAY_VIDEOS) {
                val parcelables = data?.getParcelableArrayExtra(KEY_VIDEOS) ?: return
                val videos = Array(parcelables.size) { parcelables[it] as Video }
                for (video in videos) {
                    mModel.updateVideoProgress(video)
                }
            }
            REQUEST_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT ->
                if (resultCode == RESULT_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT) {
                    val videos = data?.getParcelableArrayListExtra<Video>(KEY_VIDEOS) ?: return
                    if (!videos.allEqual(mModel.videos)) {
                        startLoadVideos()
                    }
                }
            REQUEST_CODE_LOCAL_VIDEO_SUBLIST_FRAGMENT ->
                if (resultCode == RESULT_CODE_LOCAL_VIDEO_SUBLIST_FRAGMENT) {
                    val videodir: VideoDirectory = data?.getParcelableExtra(KEY_VIDEODIR) ?: return
                    mModel.updateVideoDirectory(videodir)
                }
            REQUEST_CODE_VIDEO_MOVE_FRAGMENT -> {
                if (resultCode == RESULT_CODE_VIDEO_MOVE_FRAGMENT) {
                    val moved = data?.getBooleanExtra(KEY_MOVED, false) ?: return
                    if (moved) {
                        var parent: LocalVideoListPresenter? = this
                        while (parent != null) {
                            if (parent.mParent == null) {
                                parent.startLoadVideos()
                                break
                            }
                            parent = parent.mParent
                        }
                    } else {
                        mView?.dismissItemOptionsWindow()
                    }
                }
            }
        }
    }

    override fun setItemChecked(index: Int, checked: Boolean) =
            mModel.setItemChecked(index, checked)

    override fun toggleItemChecked(index: Int) =
            mModel.setItemChecked(index, !mModel.videoListItems[index].isChecked)

    override fun toggleItemTopped(index: Int) =
            mModel.setItemTopped(index, !mModel.videoListItems[index].isTopped)

    override fun playVideoAt(index: Int) {
        val videoListItems = mModel.videoListItems
        val dirPath = videoListItems[index].path.substring(
                0, videoListItems[index].path.lastIndexOf(File.separatorChar))
        val videos = LinkedList<Video>()
        var selection = -1
        for ((i, item) in videoListItems.withIndex()) {
            if (i == index)
                selection = videos.size
            if (item is Video) {
                if (mSublist
                        || dirPath == item.path.substring(0, item.path.lastIndexOf(File.separatorChar))) {
                    videos.add(item)
                }
            }
        }
        if (videos.size == 1) {
            mView?.playVideo(videos[0])
        } else {
            mView?.playVideos(*videos.toTypedArray(), selection = selection)
        }
    }

    override fun openVideoDirectoryAt(index: Int) {
        val args = Bundle()
        args.putParcelable(KEY_VIDEODIR, mModel.videoListItems[index].deepCopy())
        mView?.goToLocalVideoSubListFragment(args)
    }

    override fun deleteItemAt(index: Int, needUserConfirm: Boolean) =
            deleteItem(mModel.videoListItems[index], needUserConfirm)

    override fun deleteItem(item: VideoListItem, needUserConfirm: Boolean) {
        if (needUserConfirm) {
            mView?.showDeleteItemDialog(item)
        } else {
            mModel.deleteItem(item, mView)
        }
    }

    override fun moveCheckedItems() {
        moveItems(*mModel.checkedItems?.toTypedArray() ?: return)
    }

    override fun moveItems(vararg items: VideoListItem) {
        var videoDirs: MutableSet<VideoDirectory>? = null
        var parent: LocalVideoListPresenter? = this
        while (parent != null) {
            val vds = parent.mModel.videoDirs
            if (vds != null) {
                if (videoDirs == null) videoDirs = linkedSetOf()
                videoDirs.addAll(vds)
            }
            parent = parent.mParent
        }
        if (videoDirs == null) {
            return
        }
        val args = Bundle()
        args.putParcelableArray(KEY_VIDEODIRS,
                arrayListOf<VideoDirectory>().apply { deepCopy(videoDirs.toList()) }.toTypedArray())
        args.putParcelableArrayList(KEY_VIDEOS,
                arrayListOf<VideoListItem>().apply { deepCopy(listOf(*items)) })
        mView?.goToVideoMoveFragment(args)
    }

    override fun deleteCheckedItems(needUserConfirm: Boolean) {
        val items = mModel.checkedItems ?: return
        deleteItems(*items.toTypedArray(), needUserConfirm = needUserConfirm)
    }

    override fun deleteItems(vararg items: VideoListItem, needUserConfirm: Boolean) {
        if (needUserConfirm) {
            mView?.showDeleteItemsPopupWindow(*items)
        } else {
            mModel.deleteItems(*items, listener = mView)
        }
    }

    override fun renameCheckedItem() {
        renameItem(mModel.checkedItems?.get(0) ?: return)
    }

    override fun renameItem(item: VideoListItem) {
        mView?.showRenameItemDialog(item)
    }

    override fun renameItemTo(item: VideoListItem) = mModel.renameItemTo(item, mView)

    override fun shareCheckedVideo() {
        mView?.shareVideo(mModel.checkedItems?.get(0) as? Video ?: return)
    }

    override fun viewCheckedItemDetails() {
        mView?.showItemDetailsDialog(mModel.checkedItems?.get(0) ?: return)
    }

    override fun selectAllItems() = mModel.setAllItemsChecked()

    override fun unselectAllItems() = mModel.setAllItemsUnchecked()

    override fun onAllItemsRemoved() = onAllItemsChanged()

    @SuppressLint("NotifyDataSetChanged")
    override fun onAllItemsChanged() = mAdapter.notifyDataSetChanged()

    override fun onVideoDirThumbChanged(index: Int) =
            mAdapter.notifyItemChanged(index, PAYLOAD_REFRESH_VIDEODIR_THUMB)

    override fun onVideoDirVideoSizeOrCountChanged(index: Int) =
            mAdapter.notifyItemChanged(index, PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT)

    override fun onVideoProgressChanged(index: Int) =
            mAdapter.notifyItemChanged(index, PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION)

    override fun onItemUpdated(index: Int) = mAdapter.notifyItemChanged(index)

    override fun onItemRemoved(index: Int) {
        mAdapter.notifyItemRemoved(index)
        mAdapter.notifyItemRangeChanged(index, mAdapter.itemCount - index)
    }

    override fun onItemMoved(fromIndex: Int, toIndex: Int) {
        mAdapter.notifyItemRemoved(fromIndex)
        mAdapter.notifyItemInserted(toIndex)
        mAdapter.notifyItemRangeChanged(min(fromIndex, toIndex), abs(toIndex - fromIndex) + 1)
    }

    override fun onItemRenamed(oldIndex: Int, index: Int) {
        if (index == oldIndex) {
            mAdapter.notifyItemChanged(oldIndex, PAYLOAD_REFRESH_ITEM_NAME)
        } else {
            onItemMoved(oldIndex, index)
        }
    }

    override fun onItemCheckedChanged(index: Int) {
        mAdapter.notifyItemChanged(index, PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)

        var firstCheckedItem: VideoListItem? = null
        var checkedItemCount = 0
        var hasUnwritableCheckedItem = false
        for (item in mModel.videoListItems) {
            if (item.isChecked) {
                checkedItemCount++
                if (checkedItemCount == 1) {
                    firstCheckedItem = item
                }
                if (item is VideoDirectory) {
                    if (!hasUnwritableCheckedItem) {
                        hasUnwritableCheckedItem = item.hasUnwrittableVideo()
                    }
                } else /* if (item is Video) */ {
                    if (!hasUnwritableCheckedItem && !(item as Video).isWritable) {
                        hasUnwritableCheckedItem = true
                    }
                }
            }
        }
        mView?.onItemCheckedChange(firstCheckedItem, checkedItemCount, hasUnwritableCheckedItem)
    }

    override fun onItemToppedChanged(oldIndex: Int, index: Int) {
        if (index == oldIndex) {
            mAdapter.notifyItemChanged(oldIndex, PAYLOAD_CHANGE_ITEM_LPS_AND_BG)
        } else {
            onItemMoved(oldIndex, index)
        }
    }

    private inner class VideoListAdapter
        : ILocalVideoListPresenter.VideoListAdapter<ILocalVideoListView.VideoListViewHolder>() {

        override fun getItemCount() = mModel.videoListItems.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                mView.newVideoListViewHolder(parent, viewType)

        override fun onBindViewHolder(holder: ILocalVideoListView.VideoListViewHolder,
                                      position: Int, payloads: MutableList<Any>) {
            super.onBindViewHolder(holder, position, payloads)
            holder.bindData(mModel.videoListItems[position], position, payloads)
        }

        override fun getItemViewType(position: Int) =
                when (mModel.videoListItems[position]) {
                    is Video -> VIEW_TYPE_VIDEO
                    is VideoDirectory -> VIEW_TYPE_VIDEODIR
                    else -> throw IllegalArgumentException("Unknown itemView type")
                }

        override fun loadItemImages(holder: ILocalVideoListView.VideoListViewHolder) {
            val position = holder.bindingAdapterPosition
            when (getItemViewType(position)) {
                VIEW_TYPE_VIDEO ->
                    holder.loadItemImages(mModel.videoListItems[position] as Video)
                VIEW_TYPE_VIDEODIR ->
                    holder.loadItemImages(
                            (mModel.videoListItems[position] as VideoDirectory).firstVideoOrNull()!!)
            }
        }

        override fun cancelLoadingItemImages(holder: ILocalVideoListView.VideoListViewHolder) {
            holder.cancelLoadingItemImages()
        }
    }
}
