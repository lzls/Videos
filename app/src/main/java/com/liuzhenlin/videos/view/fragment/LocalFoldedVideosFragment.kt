/*
 * Created on 2018/08/15.
 * Copyright © 2018–2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.liuzhenlin.circularcheckbox.CircularCheckBox
import com.liuzhenlin.common.Configs.ScreenWidthDpLevel
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.common.listener.OnBackPressedListener
import com.liuzhenlin.common.utils.FileUtils
import com.liuzhenlin.common.utils.UiUtils
import com.liuzhenlin.common.utils.Utils
import com.liuzhenlin.common.view.SwipeRefreshLayout
import com.liuzhenlin.simrv.SlidingItemMenuRecyclerView
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.presenter.ILocalFoldedVideosPresenter
import com.liuzhenlin.videos.utils.VideoUtils2
import com.liuzhenlin.videos.view.IView
import com.liuzhenlin.videos.view.fragment.Payloads.*
import java.util.*

/**
 * @author 刘振林
 */

interface ILocalFoldedVideosView : IView<ILocalFoldedVideosPresenter>, VideoListItemOpCallback<Video> {

    fun getArguments(): Bundle?
    fun onReturnResult(resultCode: Int, data: Intent?)

    fun onVideosLoadStart()
    fun onVideosLoadFinish()
    fun onVideosLoadCanceled()

    fun onVideoCheckedChange(checkedVideosCount: Int, hasUnwritableCheckedVideo: Boolean)

    fun isVideoSelectControlsShown(): Boolean
    fun hideVideoSelectControls()

    fun newVideoListViewHolder(parent: ViewGroup): VideoListViewHolder

    abstract class VideoListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bindData(video: Video, position: Int, payloads: List<Any>)
        abstract fun loadItemImages(video: Video)
        abstract fun cancelLoadingItemImages()
    }
}

class LocalFoldedVideosFragment : BaseFragment(), ILocalFoldedVideosView, View.OnClickListener,
        View.OnLongClickListener, SwipeRefreshLayout.OnRefreshListener, OnBackPressedListener {

    private lateinit var mInteractionCallback: InteractionCallback
    private var mLifecycleCallback: FragmentPartLifecycleCallback? = null

    private var mVideoOpCallback: VideoListItemOpCallback<Video>? = null

    internal val presenter = ILocalFoldedVideosPresenter.newInstance()

    private lateinit var mRecyclerView: SlidingItemMenuRecyclerView
    private val mAdapter by lazy(LazyThreadSafetyMode.NONE) { presenter.getVideoListAdapter() }

    private lateinit var mBackButton: ImageButton
    private lateinit var mCancelButton: Button
    private lateinit var mSelectAllButton: Button
    private lateinit var mOptionsFrameTopDivider: View
    private lateinit var mVideoOptionsFrame: ViewGroup
    private lateinit var mMoveButton: TextView
    private lateinit var mDeleteButton: TextView
    private lateinit var mRenameButton: TextView
    private lateinit var mShareButton: TextView
    private lateinit var mDetailsButton: TextView

    private var _TOP: String? = null
    private val TOP: String
        get() {
            if (_TOP == null) {
                _TOP = getString(R.string.top)
            }
            return _TOP!!
        }
    private var _CANCEL_TOP: String? = null
    private val CANCEL_TOP: String
        get() {
            if (_CANCEL_TOP == null) {
                _CANCEL_TOP = getString(R.string.cancelTop)
            }
            return _CANCEL_TOP!!
        }
    private var _SELECT_ALL: String? = null
    private val SELECT_ALL: String
        get() {
            if (_SELECT_ALL == null) {
                _SELECT_ALL = getString(R.string.selectAll)
            }
            return _SELECT_ALL!!
        }
    private var _SELECT_NONE: String? = null
    private val SELECT_NONE: String
        get() {
            if (_SELECT_NONE == null) {
                _SELECT_NONE = getString(R.string.selectNone)
            }
            return _SELECT_NONE!!
        }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) =
                    presenter.onViewStart(this@LocalFoldedVideosFragment)

            override fun onResume(owner: LifecycleOwner) =
                    presenter.onViewResume(this@LocalFoldedVideosFragment)

            override fun onPause(owner: LifecycleOwner) =
                    presenter.onViewPaused(this@LocalFoldedVideosFragment)

            override fun onStop(owner: LifecycleOwner) =
                    presenter.onViewStopped(this@LocalFoldedVideosFragment)
        })
    }

    fun setVideoOpCallback(callback: VideoListItemOpCallback<Video>?) {
        mVideoOpCallback = callback
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val parent = parentFragment

        mInteractionCallback = when {
            parent is InteractionCallback -> parent
            context is InteractionCallback -> context
            parent != null -> throw RuntimeException("Neither $parent nor $context " +
                    "has implemented LocalFoldedVideosFragment.InteractionCallback")
            else -> throw RuntimeException(
                    "$context must implement LocalFoldedVideosFragment.InteractionCallback")
        }

        if (parent is FragmentPartLifecycleCallback) {
            mLifecycleCallback = parent
        } else if (context is FragmentPartLifecycleCallback) {
            mLifecycleCallback = context
        }
        mLifecycleCallback?.onFragmentAttached(this)

        presenter.attachToView(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mLifecycleCallback?.onFragmentViewCreated(this)
        presenter.onViewCreated(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mLifecycleCallback?.onFragmentViewDestroyed(this)

        presenter.stopLoadVideos()
        if (isVideoSelectControlsShown()) {
            for (i in 0 until mAdapter.itemCount) {
                presenter.setVideoChecked(i, false)
            }
            mInteractionCallback.isRefreshLayoutEnabled = true
        }
        presenter.onViewDestroyed(this)
    }

    override fun onDetach() {
        super.onDetach()
        mLifecycleCallback?.onFragmentDetached(this)
        presenter.detachFromView(this)
    }

    override fun onReturnResult(resultCode: Int, data: Intent?) {
        targetFragment?.onActivityResult(targetRequestCode, resultCode, data)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_local_folded_videos, container, false)
        initViews(view)
        return attachViewToSwipeBackLayout(view)
    }

    private fun initViews(contentView: View) {
        val actionbar = mInteractionCallback.getActionBar(this)

        val titleText = actionbar.findViewById<TextView>(R.id.text_title)
        titleText.text = presenter.videoDirName

        mRecyclerView = contentView.findViewById(R.id.simrv_foldedVideoList)
        mRecyclerView.layoutManager = LinearLayoutManager(contentView.context)
        mRecyclerView.adapter = mAdapter
        mRecyclerView.addItemDecoration(
                DividerItemDecoration(contentView.context, DividerItemDecoration.VERTICAL))
        mRecyclerView.setHasFixedSize(true)

        mBackButton = actionbar.findViewById(R.id.btn_back)
        mCancelButton = actionbar.findViewById(R.id.btn_cancel)
        mSelectAllButton = actionbar.findViewById(R.id.btn_selectAll)
        mOptionsFrameTopDivider = contentView.findViewById(R.id.divider_videoOptionsFrame)
        mVideoOptionsFrame = contentView.findViewById(R.id.frame_videoOptions)
        mMoveButton = contentView.findViewById(R.id.btn_move)
        mDeleteButton = contentView.findViewById(R.id.btn_delete_videoListOptions)
        mRenameButton = contentView.findViewById(R.id.btn_rename)
        mShareButton = contentView.findViewById(R.id.btn_share)
        mDetailsButton = contentView.findViewById(R.id.btn_details)

        mBackButton.setOnClickListener(this)
        mCancelButton.setOnClickListener(this)
        mSelectAllButton.setOnClickListener(this)
        mMoveButton.setOnClickListener(this)
        mDeleteButton.setOnClickListener(this)
        mRenameButton.setOnClickListener(this)
        mShareButton.setOnClickListener(this)
        mDetailsButton.setOnClickListener(this)
    }

    override fun onScreenWidthDpLevelChanged(
            oldLevel: ScreenWidthDpLevel, level: ScreenWidthDpLevel) {
        mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount, PAYLOAD_REFRESH_VIDEO_THUMB)
    }

    override fun onBackPressed() =
            if (isVideoSelectControlsShown()) {
                hideVideoSelectControls()
                true
            } else false

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        presenter.onActivityResult(requestCode, resultCode, data)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_back -> swipeBackLayout.scrollToFinishActivityOrPopUpFragment()

            R.id.itemVisibleFrame -> {
                val position = v.tag as Int
                if (isVideoSelectControlsShown()) {
                    presenter.setVideoChecked(position, !presenter.isVideoChecked(position))
                } else {
                    presenter.playAllVideos(position)
                }
            }
            R.id.checkbox -> {
                val position = v.tag as Int
                presenter.setVideoChecked(position, !presenter.isVideoChecked(position))
            }
            R.id.btn_top -> {
                val position = v.tag as Int
                presenter.setVideoTopped(position, !presenter.isVideoTopped(position))
            }
            R.id.btn_delete -> {
                presenter.deleteVideo(v.tag as Int)
            }

            R.id.btn_cancel -> hideVideoSelectControls()
            R.id.btn_selectAll -> {
                if (mSelectAllButton.text == SELECT_ALL) {
                    presenter.selectAllVideos()
                } else {
                    presenter.unselectAllVideos()
                }
            }
            R.id.btn_move -> {
                presenter.moveCheckedVideos()
            }
            R.id.btn_delete_videoListOptions -> {
                presenter.deleteCheckedVideos()
            }
            R.id.btn_rename -> {
                presenter.renameCheckedVideo()
            }
            R.id.btn_share -> {
                presenter.shareCheckedVideo()
            }
            R.id.btn_details -> {
                presenter.viewCheckedVideoDetails()
            }
        }
    }

    override fun onLongClick(v: View) = when (v.id) {
        R.id.itemVisibleFrame ->
            if (isVideoSelectControlsShown()
                    || mInteractionCallback.isRefreshLayoutRefreshing) {
                false
            } else {
                UiUtils.setRuleForRelativeLayoutChild(
                        mRecyclerView, RelativeLayout.ABOVE, mOptionsFrameTopDivider.id)
                mRecyclerView.isItemDraggable = false
                mInteractionCallback.isRefreshLayoutEnabled = false
                mBackButton.visibility = View.GONE
                mCancelButton.visibility = View.VISIBLE
                mSelectAllButton.visibility = View.VISIBLE
                mOptionsFrameTopDivider.visibility = View.VISIBLE
                mVideoOptionsFrame.visibility = View.VISIBLE

                Utils.runOnLayoutValid(mRecyclerView.parent as View) {
                    val itemBottom = (v.parent as View).bottom
                    val listHeight = mRecyclerView.height
                    if (itemBottom > listHeight) {
                        mRecyclerView.scrollBy(0, itemBottom - listHeight)
                    }
                }

                val selection = v.tag as Int
                mAdapter.run {
                    notifyItemRangeChanged(0, selection,
                            PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)
                    notifyItemRangeChanged(selection + 1, itemCount - selection - 1,
                            PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)

                    presenter.setVideoChecked(selection, true)
                    notifyItemChanged(selection,
                            PAYLOAD_CHANGE_CHECKBOX_VISIBILITY
                                    or PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)
                }

                true
            }
        else -> false
    }

    override fun onVideoCheckedChange(checkedVideosCount: Int, hasUnwritableCheckedVideo: Boolean) {
        when (checkedVideosCount) {
            mAdapter.itemCount -> mSelectAllButton.text = SELECT_NONE
            else -> mSelectAllButton.text = SELECT_ALL
        }
        mMoveButton.isEnabled =
                checkedVideosCount > 0 && !hasUnwritableCheckedVideo
                        && App.getInstance(contextRequired).hasAllFilesAccess()
        mDeleteButton.isEnabled = checkedVideosCount > 0 && !hasUnwritableCheckedVideo
        mRenameButton.isEnabled = checkedVideosCount == 1 && !hasUnwritableCheckedVideo
        mShareButton.isEnabled = checkedVideosCount == 1
        mDetailsButton.isEnabled = checkedVideosCount == 1
    }

    override fun isVideoSelectControlsShown() =
            mVideoOptionsFrame.visibility == View.VISIBLE

    override fun hideVideoSelectControls() {
        UiUtils.setRuleForRelativeLayoutChild(mRecyclerView, RelativeLayout.ABOVE,  /* false */ 0)
        mRecyclerView.isItemDraggable = true
        mInteractionCallback.isRefreshLayoutEnabled = true
        mBackButton.visibility = View.VISIBLE
        mCancelButton.visibility = View.GONE
        mSelectAllButton.visibility = View.GONE
        mOptionsFrameTopDivider.visibility = View.GONE
        mVideoOptionsFrame.visibility = View.GONE

        for (index in 0 until mAdapter.itemCount) {
            presenter.setVideoChecked(index, false)
        }
        mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount,
                PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)
    }

    override fun onRefresh() {
        // 用户长按列表时可能又在下拉刷新，多选窗口会被弹出，需要隐藏
        if (isVideoSelectControlsShown()) {
            hideVideoSelectControls()
        }

        presenter.startLoadVideos()
    }

    override fun onVideosLoadStart() {
        mRecyclerView.isItemDraggable = false
        mRecyclerView.releaseItemView(false)
    }

    override fun onVideosLoadFinish() {
        mRecyclerView.isItemDraggable = true
        mInteractionCallback.isRefreshLayoutRefreshing = false
    }

    override fun onVideosLoadCanceled() = onVideosLoadFinish()

    override fun showDeleteItemDialog(video: Video, onDeleteAction: (() -> Unit)?) {
        mVideoOpCallback?.showDeleteItemDialog(video, onDeleteAction)
    }

    override fun showDeleteItemsPopupWindow(vararg videos: Video, onDeleteAction: (() -> Unit)?) {
        mVideoOpCallback?.showDeleteItemsPopupWindow(*videos, onDeleteAction = onDeleteAction)
    }

    override fun showRenameItemDialog(video: Video, onRenameAction: (() -> Unit)?) {
        mVideoOpCallback?.showRenameItemDialog(video, onRenameAction)
    }

    override fun showItemDetailsDialog(video: Video) {
        mVideoOpCallback?.showItemDetailsDialog(video)
    }

    override fun showVideosMovePage(vararg videos: Video) {
        mVideoOpCallback?.showVideosMovePage(*videos)
    }

    override fun newVideoListViewHolder(parent: ViewGroup): ILocalFoldedVideosView.VideoListViewHolder {
        return VideoListViewHolder(
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.video_list_item_video, parent, false))
    }

    private inner class VideoListViewHolder(itemView: View)
        : ILocalFoldedVideosView.VideoListViewHolder(itemView) {

        val itemVisibleFrame: ViewGroup = itemView.findViewById(R.id.itemVisibleFrame)
        val checkBox: CircularCheckBox = itemView.findViewById(R.id.checkbox)
        val videoImage: ImageView = itemView.findViewById(R.id.image_video)
        val videoNameText: TextView = itemView.findViewById(R.id.text_videoName)
        val videoSizeText: TextView = itemView.findViewById(R.id.text_videoSize)
        val videoProgressAndDurationText: TextView =
                itemView.findViewById(R.id.text_videoProgressAndDuration)
        val topButton: TextView = itemView.findViewById(R.id.btn_top)
        val deleteButton: TextView = itemView.findViewById(R.id.btn_delete)

        init {
            itemVisibleFrame.setOnClickListener(this@LocalFoldedVideosFragment)
            checkBox.setOnClickListener(this@LocalFoldedVideosFragment)
            topButton.setOnClickListener(this@LocalFoldedVideosFragment)
            deleteButton.setOnClickListener(this@LocalFoldedVideosFragment)

            itemVisibleFrame.setOnLongClickListener(this@LocalFoldedVideosFragment)
        }

        override fun bindData(video: Video, position: Int, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                itemVisibleFrame.tag = position
                checkBox.tag = position
                topButton.tag = position
                deleteButton.tag = position

                separateToppedItemsFromUntoppedOnes(video)

                checkBox.run {
                    UiUtils.setViewVisibilityAndVerify(checkBox, mVideoOptionsFrame.visibility)
                    isChecked = video.isChecked
                }
                videoNameText.text = video.name
                videoSizeText.text = FileUtils.formatFileSize(video.size.toDouble())
                videoProgressAndDurationText.text =
                        VideoUtils2.concatVideoProgressAndDuration(video.progress, video.duration)
                UiUtils.setViewVisibilityAndVerify(deleteButton.parent as View,
                        if (video.isWritable) View.VISIBLE else View.GONE)
            } else {
                for (payload in payloads) {
                    if (payload !is Int) continue
                    if (payload and PAYLOAD_CHANGE_ITEM_LPS_AND_BG != 0) {
                        separateToppedItemsFromUntoppedOnes(video)
                    }
                    if (payload and PAYLOAD_CHANGE_CHECKBOX_VISIBILITY != 0) {
                        UiUtils.setViewVisibilityAndVerify(checkBox, mVideoOptionsFrame.visibility)
                    }
                    if (payload and PAYLOAD_REFRESH_CHECKBOX != 0) {
                        checkBox.isChecked = video.isChecked

                    } else if (payload and PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR != 0) {
                        checkBox.setChecked(video.isChecked, true)
                    }
                    if (payload and PAYLOAD_REFRESH_VIDEO_THUMB != 0) {
                        @Suppress("UNCHECKED_CAST")
                        (bindingAdapter as ImageLoadingListAdapter<VideoListViewHolder>)
                                .loadItemImagesIfNotScrolling(this)
                    }
                    if (payload and PAYLOAD_REFRESH_ITEM_NAME != 0) {
                        videoNameText.text = video.name
                    }
                    if (payload and PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION != 0) {
                        videoProgressAndDurationText.text =
                                VideoUtils2.concatVideoProgressAndDuration(
                                        video.progress, video.duration)
                    }
                }
            }
        }

        override fun loadItemImages(video: Video) {
            VideoUtils2.loadVideoThumbIntoFragmentImageView(
                    this@LocalFoldedVideosFragment, videoImage, video)
        }

        override fun cancelLoadingItemImages() {
            Glide.with(this@LocalFoldedVideosFragment).clear(videoImage)
        }

        fun separateToppedItemsFromUntoppedOnes(video: Video) {
            val context = contextThemedFirst
            if (video.isTopped) {
                ViewCompat.setBackground(itemVisibleFrame,
                        ContextCompat.getDrawable(context, R.drawable.selector_topped_recycler_item))
                topButton.text = CANCEL_TOP
            } else {
                ViewCompat.setBackground(itemVisibleFrame,
                        ContextCompat.getDrawable(context, R.drawable.default_selector_recycler_item))
                topButton.text = TOP
            }
        }
    }

    interface InteractionCallback : ActionBarCallback, RefreshLayoutCallback
}
