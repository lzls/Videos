/*
 * Created on 2018/08/15.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.*
import android.view.View.OnLayoutChangeListener
import android.widget.*
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.liuzhenlin.circularcheckbox.CircularCheckBox
import com.liuzhenlin.common.Configs.ScreenWidthDpLevel
import com.liuzhenlin.common.Consts.EMPTY_STRING
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.common.compat.ViewCompatibility
import com.liuzhenlin.common.listener.OnBackPressedListener
import com.liuzhenlin.common.utils.*
import com.liuzhenlin.common.view.OnBackPressedPreImeEventInterceptableEditText
import com.liuzhenlin.common.view.SwipeRefreshLayout
import com.liuzhenlin.common.windowhost.FocusObservableDialog
import com.liuzhenlin.common.windowhost.WaitingOverlayDialog
import com.liuzhenlin.simrv.SlidingItemMenuRecyclerView
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.presenter.ILocalVideoListPresenter
import com.liuzhenlin.videos.presenter.ILocalVideoListPresenter.VideoListAdapter.Companion.PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT
import com.liuzhenlin.videos.presenter.ILocalVideoListPresenter.VideoListAdapter.Companion.PAYLOAD_REFRESH_VIDEODIR_THUMB
import com.liuzhenlin.videos.presenter.ILocalVideoListPresenter.VideoListAdapter.Companion.VIEW_TYPE_VIDEO
import com.liuzhenlin.videos.presenter.ILocalVideoListPresenter.VideoListAdapter.Companion.VIEW_TYPE_VIDEODIR
import com.liuzhenlin.videos.utils.VideoUtils2
import com.liuzhenlin.videos.view.IView
import com.liuzhenlin.videos.view.fragment.Payloads.*
import java.util.*

/**
 * @author 刘振林
 */

interface ILocalVideoListView : IView<ILocalVideoListPresenter>, VideoListItemOpCallback<VideoListItem> {

    public val isDestroying: Boolean

    fun getArguments(): Bundle?
    fun onReturnResult(resultCode: Int, data: Intent?)

    fun goToLocalVideoSubListFragment(args: Bundle)
    fun goToVideoMoveFragment(args: Bundle)

    fun dismissItemOptionsWindow()

    fun onVideosLoadStart()
    fun onVideosLoadFinish()
    fun onVideosLoadCanceled()

    fun onItemCheckedChange(firstCheckedItem: VideoListItem?,
                            checkedItemCount: Int, hasUnwritableCheckedItem: Boolean)

    fun newVideoListViewHolder(parent: ViewGroup, viewType: Int): VideoListViewHolder

    abstract class VideoListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bindData(item: VideoListItem, position: Int, payloads: List<Any>)
        abstract fun loadItemImages(video: Video)
        abstract fun cancelLoadingItemImages()
    }
}

class LocalVideoListFragment : BaseFragment(), ILocalVideoListView,
        SwipeRefreshLayout.OnRefreshListener, View.OnClickListener, View.OnLongClickListener,
        OnBackPressedListener {

    private lateinit var mInteractionCallback: InteractionCallback
    private var mLifecycleCallback: FragmentPartLifecycleCallback? = null

    private lateinit var mRecyclerView: SlidingItemMenuRecyclerView
    private val mAdapter by lazy(LazyThreadSafetyMode.NONE) { presenter.getVideoListAdapter() }

    private var mItemOptionsWindow: PopupWindow? = null
    private var mDeleteItemsWindow: PopupWindow? = null
    private var mDeleteItemDialog: Dialog? = null
    private var mRenameItemDialog: Dialog? = null
    private var mItemDetailsDialog: Dialog? = null

    private var mTitleWindowFrame: FrameLayout? = null
    private var mSelectAllButton: TextView? = null

    private var mTitleText_IOW: TextView? = null
    private var mMoveButton_IOW: TextView? = null
    private var mDeleteButton_IOW: TextView? = null
    private var mRenameButton_IOW: TextView? = null
    private var mShareButton_IOW: TextView? = null
    private var mDetailsButton_IOW: TextView? = null

    internal val presenter = ILocalVideoListPresenter.newInstance()

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

    private val miniThumbSize
        get() = Utils.roundFloat(512f * (resources.displayMetrics.widthPixels / 1080f))

    override val isDestroying: Boolean
        get() {
            val activity = contextThemedFirst as? Activity
            return activity?.isFinishing == true || isRemoving || isDetached
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val parent = parentFragment

        mInteractionCallback = when {
            parent is InteractionCallback -> parent
            context is InteractionCallback -> context
            parent != null -> throw RuntimeException("Neither $context nor $parent " +
                    "has implemented LocalVideoListFragment.InteractionCallback")
            else -> throw RuntimeException(
                    "$context must implement LocalVideoListFragment.InteractionCallback")
        }

        if (parent is FragmentPartLifecycleCallback) {
            mLifecycleCallback = parent
        } else if (context is FragmentPartLifecycleCallback) {
            mLifecycleCallback = context
        }
        mLifecycleCallback?.onFragmentAttached(this)

        presenter.attachToView(this)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (presenter.isSublist) {
            val actionbar = mInteractionCallback.getActionBar(this)
            actionbar.findViewById<View>(R.id.btn_back).setOnClickListener(this)
            actionbar.findViewById<TextView>(R.id.text_title).text = presenter.listTitle
            actionbar.findViewById<TextView>(R.id.text_titleDesc).text = presenter.listTitleDesc
            actionbar.findViewById<HorizontalScrollView>(R.id.hsv_titleDescText).let {
                Utils.runOnLayoutValid(it) { it.fullScroll(View.FOCUS_RIGHT) }
            }
        }

        val contentView = inflater.inflate(R.layout.fragment_local_video_list, container, false)
        mRecyclerView = contentView.findViewById(R.id.simrv_videoList)
        mRecyclerView.layoutManager = LinearLayoutManager(contentView.context)
        mRecyclerView.adapter = mAdapter
        mRecyclerView.addItemDecoration(
                DividerItemDecoration(contentView.context, DividerItemDecoration.VERTICAL))
        mRecyclerView.setHasFixedSize(true)

        isSwipeBackEnabled = presenter.isSublist
        return attachViewToSwipeBackLayout(contentView)
    }

    override fun onScreenWidthDpLevelChanged(
            oldLevel: ScreenWidthDpLevel, level: ScreenWidthDpLevel) {
        mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount,
                PAYLOAD_REFRESH_VIDEO_THUMB or PAYLOAD_REFRESH_VIDEODIR_THUMB)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mLifecycleCallback?.onFragmentViewCreated(this)
        presenter.onViewCreated(this)
    }

    override fun onStart() {
        super.onStart()
        presenter.onViewStart(this)
    }

    override fun onResume() {
        super.onResume()
        presenter.onViewResume(this)
    }

    override fun onPause() {
        super.onPause()
        presenter.onViewPaused(this)
    }

    // This method can be called when a stopped activity is being recreated,
    // in which case onStop() is being called unexpectedly.
    override fun onStop() {
        super.onStop()
        presenter.onViewStopped(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mLifecycleCallback?.onFragmentViewDestroyed(this)
        dismissAllFloatingWindows()
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

    override fun onBackPressed(): Boolean {
        mItemOptionsWindow?.dismiss()
                ?: if (presenter.isSublist) {
                    swipeBackLayout.scrollToFinishActivityOrPopUpFragment()
                    return true
                } else return false
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        presenter.onActivityResult(requestCode, resultCode, data)
    }

    override fun goToLocalVideoSubListFragment(args: Bundle) =
            mInteractionCallback.goToLocalVideoSubListFragment(args)

    override fun goToVideoMoveFragment(args: Bundle) =
            mInteractionCallback.goToVideoMoveFragment(args)

    override fun onRefresh() = presenter.startLoadVideos()

    override fun dismissItemOptionsWindow() {
        // 1）自动刷新时隐藏弹出的多选窗口
        // 2) 用户长按列表时可能又在下拉刷新，多选窗口会被弹出，需要隐藏
        // 3) 视频移动完成后关闭多选窗口
        mItemOptionsWindow?.dismiss()
    }

    private fun dismissAllFloatingWindows() {
        dismissItemOptionsWindow()
        mDeleteItemsWindow?.dismiss()
        mDeleteItemDialog?.dismiss()
        mRenameItemDialog?.dismiss()
        mItemDetailsDialog?.dismiss()
    }

    override fun onVideosLoadStart() {
        dismissAllFloatingWindows()
        mRecyclerView.releaseItemView(false)
        mRecyclerView.isItemDraggable = false
        mInteractionCallback.isRefreshLayoutRefreshing = true
    }

    override fun onVideosLoadFinish() {
        dismissAllFloatingWindows()
        onVideosLoadCanceled()
    }

    override fun onVideosLoadCanceled() {
        mRecyclerView.isItemDraggable = true
        mInteractionCallback.isRefreshLayoutRefreshing = false
    }

    override fun newVideoListViewHolder(parent: ViewGroup, viewType: Int)
            : ILocalVideoListView.VideoListViewHolder {
        return when (viewType) {
            VIEW_TYPE_VIDEO -> VideoViewHolder(
                    LayoutInflater.from(parent.context)
                            .inflate(R.layout.video_list_item_video, parent, false))
            VIEW_TYPE_VIDEODIR -> VideoDirViewHolder(
                    LayoutInflater.from(parent.context)
                            .inflate(R.layout.video_list_item_videodir, parent, false))
            else -> throw IllegalArgumentException("Unknown itemView type")
        }
    }

    private open inner class VideoListViewHolder(itemView: View)
        : ILocalVideoListView.VideoListViewHolder(itemView) {

        val itemVisibleFrame: ViewGroup = itemView.findViewById(R.id.itemVisibleFrame)
        val checkBox: CircularCheckBox = itemView.findViewById(R.id.checkbox)
        val topButton: TextView = itemView.findViewById(R.id.btn_top)
        val deleteButton: TextView = itemView.findViewById(R.id.btn_delete)

        init {
            itemVisibleFrame.setOnClickListener(this@LocalVideoListFragment)
            checkBox.setOnClickListener(this@LocalVideoListFragment)
            topButton.setOnClickListener(this@LocalVideoListFragment)
            deleteButton.setOnClickListener(this@LocalVideoListFragment)

            itemVisibleFrame.setOnLongClickListener(this@LocalVideoListFragment)
        }

        override fun bindData(item: VideoListItem, position: Int, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                itemVisibleFrame.tag = position
                checkBox.tag = position
                topButton.tag = position
                deleteButton.tag = position

                separateToppedItemsFromUntoppedOnes(position)

                if (mItemOptionsWindow == null) {
                    UiUtils.setViewVisibilityAndVerify(checkBox, View.GONE)
                } else {
                    UiUtils.setViewVisibilityAndVerify(checkBox, View.VISIBLE)
                    checkBox.isChecked = item.isChecked
                }
                when (this) {
                    is VideoViewHolder -> {
                        val video = item as Video
                        videoNameText.text = item.name
                        videoSizeText.text = FileUtils.formatFileSize(item.size.toDouble())
                        videoProgressAndDurationText.text =
                                VideoUtils2.concatVideoProgressAndDuration(
                                        video.progress, video.duration)
                        UiUtils.setViewVisibilityAndVerify(deleteButton.parent as View,
                                if (video.isWritable) View.VISIBLE else View.GONE)
                    }
                    is VideoDirViewHolder -> {
                        val videodir = item as VideoDirectory
                        val firstVideo = videodir.firstVideoOrNull()!!
                        val videoCount = videodir.videoCount()

                        VideoUtils2.loadVideoThumbIntoFragmentImageView(
                                this@LocalVideoListFragment, videodirImage, firstVideo)
                        videodirNameText.text = item.name
                        videodirSizeText.text = FileUtils.formatFileSize(item.size.toDouble())
                        videoCountText.text =
                                resources.getQuantityString(
                                        R.plurals.aTotalOfSeveralVideos, videoCount, videoCount)
                        UiUtils.setViewVisibilityAndVerify(deleteButton.parent as View,
                                if (!videodir.hasUnwrittableVideo()) View.VISIBLE else View.GONE)
                    }
                }
            } else {
                for (payload in payloads) {
                    if (payload !is Int) continue
                    if (payload and PAYLOAD_CHANGE_ITEM_LPS_AND_BG != 0) {
                        separateToppedItemsFromUntoppedOnes(position)
                    }
                    if (payload and PAYLOAD_CHANGE_CHECKBOX_VISIBILITY != 0) {
                        if (mItemOptionsWindow == null) {
                            UiUtils.setViewVisibilityAndVerify(checkBox, View.GONE)
                        } else {
                            UiUtils.setViewVisibilityAndVerify(checkBox, View.VISIBLE)
                        }
                    }
                    if (payload and PAYLOAD_REFRESH_CHECKBOX != 0) {
                        checkBox.isChecked = item.isChecked
                    } else if (payload and PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR != 0) {
                        checkBox.setChecked(item.isChecked, true)
                    }
                    if (payload and PAYLOAD_REFRESH_ITEM_NAME != 0) {
                        when (this) {
                            is VideoViewHolder -> videoNameText.text = item.name
                            is VideoDirViewHolder -> videodirNameText.text = item.name
                        }
                    }
                    if (payload and PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION != 0) {
                        val (_, _, _, _, _, _, progress, duration) = item as Video
                        (this as VideoViewHolder).videoProgressAndDurationText.text =
                                VideoUtils2.concatVideoProgressAndDuration(progress, duration)
                    }
                    if (payload and (PAYLOAD_REFRESH_VIDEO_THUMB or PAYLOAD_REFRESH_VIDEODIR_THUMB)
                            != 0) {
                        @Suppress("UNCHECKED_CAST")
                        (bindingAdapter as ImageLoadingListAdapter<VideoListViewHolder>)
                                .loadItemImagesIfNotScrolling(this)
                    }
                    if (payload and PAYLOAD_REFRESH_VIDEODIR_SIZE_AND_VIDEO_COUNT != 0) {
                        val vh = this as VideoDirViewHolder
                        val videoCount = (item as VideoDirectory).videoCount()
                        vh.videodirSizeText.text = FileUtils.formatFileSize(item.size.toDouble())
                        vh.videoCountText.text =
                                resources.getQuantityString(
                                        R.plurals.aTotalOfSeveralVideos, videoCount, videoCount)
                    }
                }
            }
        }

        override fun loadItemImages(video: Video) {
            when (this) {
                is VideoViewHolder -> {
                    VideoUtils2.loadVideoThumbIntoFragmentImageView(
                            this@LocalVideoListFragment, videoImage, video)
                }
                is VideoDirViewHolder -> {
                    VideoUtils2.loadVideoThumbIntoFragmentImageView(
                            this@LocalVideoListFragment, videodirImage, video)
                }
            }
        }

        override fun cancelLoadingItemImages() {
            val requestManager = Glide.with(this@LocalVideoListFragment)
            when (this) {
                is VideoViewHolder -> requestManager.clear(videoImage)
                is VideoDirViewHolder -> requestManager.clear(videodirImage)
            }
        }

        fun separateToppedItemsFromUntoppedOnes(position: Int) {
            val context = contextThemedFirst
            if (presenter.isItemTopped(position)) {
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

    private inner class VideoViewHolder(itemView: View) : VideoListViewHolder(itemView) {
        val videoImage: ImageView = itemView.findViewById(R.id.image_video)
        val videoNameText: TextView = itemView.findViewById(R.id.text_videoName)
        val videoSizeText: TextView = itemView.findViewById(R.id.text_videoSize)
        val videoProgressAndDurationText: TextView =
                itemView.findViewById(R.id.text_videoProgressAndDuration)
    }

    private inner class VideoDirViewHolder(itemView: View) : VideoListViewHolder(itemView) {
        val videodirImage: ImageView = itemView.findViewById(R.id.image_videodir)
        val videodirNameText: TextView = itemView.findViewById(R.id.text_videodirName)
        val videodirSizeText: TextView = itemView.findViewById(R.id.text_videodirSize)
        val videoCountText: TextView = itemView.findViewById(R.id.text_videoCount)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_back -> swipeBackLayout.scrollToFinishActivityOrPopUpFragment()

            R.id.itemVisibleFrame -> {
                val position = v.tag as Int
                if (mItemOptionsWindow == null) {
                    when (mAdapter.getItemViewType(position)) {
                        VIEW_TYPE_VIDEO -> presenter.playVideoAt(position) // 播放视频
                        VIEW_TYPE_VIDEODIR -> { // 显示指定目录的视频
                            presenter.openVideoDirectoryAt(position)
                        }
                    }
                } else {
                    presenter.setItemChecked(position, !presenter.isItemChecked(position))
                }
            }

            R.id.checkbox -> {
                val position = v.tag as Int
                presenter.setItemChecked(position, !presenter.isItemChecked(position))
            }

            // 置顶或取消置顶视频（目录）
            R.id.btn_top -> {
                val position = v.tag as Int
                presenter.setItemTopped(position, !presenter.isItemTopped(position))
            }

            // 删除视频
            R.id.btn_delete -> presenter.deleteItemAt(v.tag as Int, true)
            R.id.btn_confirm_deleteVideoListItemDialog -> {
                val window = mDeleteItemDialog!!.window!!
                val decorView = window.decorView as ViewGroup
                val item = decorView.tag as VideoListItem
                @Suppress("UNCHECKED_CAST")
                val onDeleteAction = decorView[0].tag as (() -> Unit)?

                mDeleteItemDialog!!.cancel()

                deleteItems(item)

                if (onDeleteAction != null) {
                    onDeleteAction()
                } else {
                    presenter.deleteItem(item, false)
                }
            }
            R.id.btn_cancel_deleteVideoListItemDialog -> mDeleteItemDialog!!.cancel()

            // 移动（多个）视频
            R.id.btn_move -> presenter.moveCheckedItems()

            // 删除（多个）视频
            R.id.btn_delete_vlow -> presenter.deleteCheckedItems(true)
            R.id.btn_confirm_deleteItemsWindow -> {
                val view = mDeleteItemsWindow!!.contentView as ViewGroup
                @Suppress("UNCHECKED_CAST")
                val items = view.tag as Array<VideoListItem>
                @Suppress("UNCHECKED_CAST")
                val onDeleteAction = view[0].tag as (() -> Unit)?

                mDeleteItemsWindow!!.dismiss()
                mItemOptionsWindow?.dismiss()

                if (items.size == 1) {
                    val item = items[0]

                    deleteItems(item)

                    if (onDeleteAction != null) {
                        onDeleteAction()
                    } else {
                        presenter.deleteItem(item, false)
                    }
                } else {
                    deleteItems(*items)

                    if (onDeleteAction != null) {
                        onDeleteAction()
                    } else {
                        presenter.deleteItems(*items, needUserConfirm = false)
                    }
                }
            }
            R.id.btn_cancel_deleteItemsWindow -> mDeleteItemsWindow!!.dismiss()

            // 重命名视频或给视频目录取别名
            R.id.btn_rename -> {
                presenter.renameCheckedItem()
                mItemOptionsWindow!!.dismiss()
            }
            R.id.btn_complete_renameVideoListItemDialog -> {
                val editText = v.tag as EditText
                val text = editText.text.toString().trim()
                val newName =
                        if (text.isEmpty()) editText.hint.toString()
                        else text + editText.tag as String

                val window = mRenameItemDialog!!.window!!
                val decorView = window.decorView as ViewGroup
                val item = decorView.tag as VideoListItem
                @Suppress("UNCHECKED_CAST")
                val onRenameAction = decorView[0].tag as (() -> Unit)?

                mRenameItemDialog!!.cancel()

                if (renameItem(item, newName, view)) {
                    if (onRenameAction != null) {
                        onRenameAction()
                    } else {
                        presenter.renameItemTo(item)
                    }
                }
            }
            R.id.btn_cancel_renameVideoListItemDialog -> mRenameItemDialog!!.cancel()

            // 分享
            R.id.btn_share -> {
                presenter.shareCheckedVideo()
                mItemOptionsWindow!!.dismiss()
            }

            // 显示视频（目录）详情
            R.id.btn_details -> {
                presenter.viewCheckedItemDetails()
                mItemOptionsWindow!!.dismiss()
            }
            R.id.btn_ok_videoListItemDetailsDialog -> mItemDetailsDialog!!.cancel()

            R.id.btn_cancel_vlow -> mItemOptionsWindow!!.dismiss()
            // 全（不）选
            R.id.btn_selectAll -> {
                // 全选
                if (SELECT_ALL == mSelectAllButton!!.text.toString()) {
                    presenter.selectAllItems()
                    // 全不选
                } else {
                    presenter.unselectAllItems()
                }
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.itemVisibleFrame -> {
                if (mItemOptionsWindow != null || mInteractionCallback.isRefreshLayoutRefreshing) {
                    return false
                }

                mTitleWindowFrame = View.inflate(
                        v.context, R.layout.popup_window_main_title, null) as FrameLayout
                if (presenter.isSublist) {
                    mTitleWindowFrame!!.findViewById<TextView>(R.id.text_title).text =
                            presenter.listTitle
                }
                mTitleWindowFrame!!.findViewById<View>(R.id.btn_cancel_vlow)
                        .setOnClickListener(this)
                mSelectAllButton = mTitleWindowFrame!!.findViewById(R.id.btn_selectAll)
                mSelectAllButton!!.setOnClickListener(this)
                UiUtils.insertTopPaddingToActionBarIfLayoutUnderStatus(mTitleWindowFrame!!)

                val titleWindow = PopupWindow(mTitleWindowFrame,
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                titleWindow.isClippingEnabled = false
                titleWindow.animationStyle = R.style.WindowAnimations_TopPopupWindow
                titleWindow.showAtLocation(v, Gravity.TOP, 0, 0)

                val iowcv = View.inflate(v.context, R.layout.popup_window_videolist_options, null)
                mTitleText_IOW = iowcv.findViewById(R.id.text_title)
                mMoveButton_IOW = iowcv.findViewById(R.id.btn_move)
                mMoveButton_IOW!!.setOnClickListener(this)
                mDeleteButton_IOW = iowcv.findViewById(R.id.btn_delete_vlow)
                mDeleteButton_IOW!!.setOnClickListener(this)
                mRenameButton_IOW = iowcv.findViewById(R.id.btn_rename)
                mRenameButton_IOW!!.setOnClickListener(this)
                mShareButton_IOW = iowcv.findViewById(R.id.btn_share)
                mShareButton_IOW!!.setOnClickListener(this)
                mDetailsButton_IOW = iowcv.findViewById(R.id.btn_details)
                mDetailsButton_IOW!!.setOnClickListener(this)

                mItemOptionsWindow = PopupWindow(iowcv,
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                mItemOptionsWindow!!.animationStyle = R.style.WindowAnimations_BottomPopupWindow
                mItemOptionsWindow!!.showAtLocation(v, Gravity.BOTTOM, 0, 0)

                iowcv.addOnLayoutChangeListener(object : OnLayoutChangeListener, Runnable {
                    val selection = v.tag as Int
                    var firstLayout = true

                    init {
                        if (!ThemeUtils.isNightMode(contextThemedFirst)) {
                            mInteractionCallback.setLightStatus(true)
                        }
                        mInteractionCallback.setSideDrawerEnabled(false)
                        isSwipeBackEnabled = false
                        mInteractionCallback.isRefreshLayoutEnabled = false
                        mRecyclerView.isItemDraggable = false
                    }

                    override fun onLayoutChange(
                            v: View,
                            left: Int, top: Int, right: Int, bottom: Int,
                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                        val oldHeight = oldBottom - oldTop
                        val height = bottom - top
                        if (height != oldHeight) {
                            run()
                        }
                    }

                    override fun run() {
                        ViewCompatibility.removeCallbacks(iowcv, this)
                        if (!ViewCompatibility.isLayoutValid(iowcv)
                                || !ViewCompatibility.isLayoutValid(mRecyclerView)) {
                            ViewCompatibility.post(iowcv, this)
                            return
                        }

                        val location = IntArray(2)

                        iowcv.getLocationOnScreen(location)
                        val iowTop = location[1]

                        val rv = mRecyclerView
                        rv.getLocationOnScreen(location)
                        val rvBottom = location[1] + rv.height

                        if (rvBottom > iowTop) {
                            rv.setPadding(0, 0, 0, rvBottom - iowTop)
                        }

                        if (firstLayout) {
                            firstLayout = false
                            val itemBottom = (v.parent as View).bottom
                            val rvHeight = rv.height - rv.paddingBottom
                            if (itemBottom <= rvHeight) {
                                notifyItemsToShowCheckBoxes()
                            } else {
                                Utils.runOnLayoutValid(rv) {
                                    // 使长按的itemView在RecyclerView高度改变后可见
                                    rv.scrollBy(0, itemBottom - rvHeight)

                                    notifyItemsToShowCheckBoxes()
                                }
                            }
                        }
                    }

                    fun notifyItemsToShowCheckBoxes() = mAdapter.run {
                        notifyItemRangeChanged(0, selection,
                                PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)
                        notifyItemRangeChanged(selection + 1, itemCount - selection - 1,
                                PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)
                        // 勾选当前长按的itemView
                        presenter.setItemChecked(selection, true)
                        notifyItemChanged(selection,
                                PAYLOAD_CHANGE_CHECKBOX_VISIBILITY
                                        or PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR)
                    }
                })
                mItemOptionsWindow!!.setOnDismissListener {
                    mTitleWindowFrame = null
                    mSelectAllButton = null
                    titleWindow.dismiss()

                    mItemOptionsWindow = null
                    mTitleText_IOW = null
                    mMoveButton_IOW = null
                    mDeleteButton_IOW = null
                    mRenameButton_IOW = null
                    mShareButton_IOW = null
                    mDetailsButton_IOW = null

                    mRecyclerView.setPadding(0, 0, 0, 0)

                    for (index in 0 until mAdapter.itemCount) {
                        presenter.setItemChecked(index, false)
                    }
                    mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount,
                            PAYLOAD_CHANGE_CHECKBOX_VISIBILITY or PAYLOAD_REFRESH_CHECKBOX)

                    val sublist = presenter.isSublist
                    mInteractionCallback.setLightStatus(false)
                    isSwipeBackEnabled = sublist
                    mInteractionCallback.setSideDrawerEnabled(!sublist)
                    mInteractionCallback.isRefreshLayoutEnabled = true
                    mRecyclerView.isItemDraggable = true
                }
                return true
            }
        }
        return false
    }

    override fun onItemCheckedChange(firstCheckedItem: VideoListItem?,
                                     checkedItemCount: Int, hasUnwritableCheckedItem: Boolean) {
        mItemOptionsWindow ?: return

        if (checkedItemCount == mAdapter.itemCount) {
            mSelectAllButton!!.text = SELECT_NONE
        } else {
            mSelectAllButton!!.text = SELECT_ALL
        }

        var aVideoCheckedOnly = false
        mTitleText_IOW!!.text = when (checkedItemCount) {
            0 -> EMPTY_STRING
            1 -> {
                val item = firstCheckedItem!!
                aVideoCheckedOnly = item is Video

                if (aVideoCheckedOnly) item.name else getString(R.string.someDirectory, item.name)
            }
            else -> getString(R.string.severalItemsHaveBeenSelected, checkedItemCount)
        }

        mMoveButton_IOW!!.isEnabled =
                checkedItemCount > 0 && !hasUnwritableCheckedItem
                        && App.getInstance(contextRequired).hasAllFilesAccess()
        mDeleteButton_IOW!!.isEnabled = checkedItemCount > 0 && !hasUnwritableCheckedItem
        mRenameButton_IOW!!.isEnabled =
                checkedItemCount == 1
                        && (!hasUnwritableCheckedItem || firstCheckedItem is VideoDirectory)
        mShareButton_IOW!!.isEnabled = aVideoCheckedOnly
        mDetailsButton_IOW!!.isEnabled = checkedItemCount == 1
    }

    override fun showDeleteItemDialog(item: VideoListItem, onDeleteAction: (() -> Unit)?) {
        val context = contextThemedFirst
        val view = View.inflate(context, R.layout.dialog_message, null)

        val message = view.findViewById<TextView>(R.id.text_message)
        message.movementMethod = ScrollingMovementMethod.getInstance()
        message.text = when (item) {
            is Video -> getString(R.string.areYouSureToDeleteSth, item.name)
            else /* is VideoDirectory */ ->
                getString(R.string.areYouSureToDeleteSomeVideoDir, item.name)
        }

        val cancel = view.findViewById<TextView>(R.id.btn_cancel)
        cancel.id = R.id.btn_cancel_deleteVideoListItemDialog
        cancel.setOnClickListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            UiUtils.setRuleForRelativeLayoutChild(
                    cancel, RelativeLayout.START_OF, R.id.btn_confirm_deleteVideoListItemDialog)
        }
        UiUtils.setRuleForRelativeLayoutChild(
                cancel, RelativeLayout.LEFT_OF, R.id.btn_confirm_deleteVideoListItemDialog)

        val confirm = view.findViewById<TextView>(R.id.btn_ok)
        confirm.id = R.id.btn_confirm_deleteVideoListItemDialog
        confirm.setOnClickListener(this)

        mDeleteItemDialog = AppCompatDialog(context, R.style.DialogStyle_MinWidth_NoTitle)
        mDeleteItemDialog!!.setContentView(view)
        mDeleteItemDialog!!.setOnDismissListener { mDeleteItemDialog = null }
        mDeleteItemDialog!!.show()

        val window = mDeleteItemDialog!!.window!!
        val decorView = window.decorView as ViewGroup
        decorView.tag = item
        decorView[0].tag = onDeleteAction
    }

    override fun showDeleteItemsPopupWindow(vararg items: VideoListItem, onDeleteAction: (() -> Unit)?) {
        if (items.isEmpty()) return

        val view = View.inflate(
                contextThemedFirst, R.layout.popup_window_delete_video_list_items, null) as ViewGroup
        view.findViewById<TextView>(R.id.text_message).apply {
            movementMethod = ScrollingMovementMethod.getInstance()
            text = if (items.size == 1) {
                when (items[0]) {
                    is Video -> getString(R.string.areYouSureToDeleteSth, items[0].name)
                    else /* is VideoDirectory */ ->
                        getString(R.string.areYouSureToDeleteSomeVideoDir, items[0].name)
                }
            } else {
                getString(R.string.areYouSureToDelete)
            }
        }
        view.findViewById<View>(R.id.btn_confirm_deleteItemsWindow).setOnClickListener(this)
        view.findViewById<View>(R.id.btn_cancel_deleteItemsWindow).setOnClickListener(this)
        view.tag = items
        view[0].tag = onDeleteAction

        var fadedContentView =
                requireView().rootView // DecorView
                        .findViewById<FrameLayout>(android.R.id.content) // ContentFrameLayout
        fadedContentView =
                fadedContentView.parent // FitWindowsLinearLayout
                        .parent as? FrameLayout ?: fadedContentView
        var fadedContentViewFgGravity = 0

        mDeleteItemsWindow = PopupWindow(
                view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mDeleteItemsWindow!!.isTouchable = true
        mDeleteItemsWindow!!.isFocusable = true
        // 这是必须的，否则'setFocusable'将无法在Android 6.0以下运行
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mDeleteItemsWindow!!.setBackgroundDrawable(BitmapDrawable(resources, null as Bitmap?))
        }
        mDeleteItemsWindow!!.showAtLocation(fadedContentView, Gravity.BOTTOM, 0, 0)
        mDeleteItemsWindow!!.setOnDismissListener {
            mDeleteItemsWindow = null

            mTitleWindowFrame?.foreground = null
            fadedContentView.foreground = null
            fadedContentView.foregroundGravity = fadedContentViewFgGravity
        }

        mTitleWindowFrame?.foreground = ColorDrawable(0x7F000000)
        fadedContentView.foreground = ColorDrawable(0x7F000000)
        // 在设置了foreground之后获取，以免因 mForegroundInfo 还未初始化，获取不到实际的值
        fadedContentViewFgGravity = fadedContentView.foregroundGravity
        fadedContentView.foregroundGravity = Gravity.FILL
    }

    override fun showRenameItemDialog(item: VideoListItem, onRenameAction: (() -> Unit)?) {
        val name = item.name
        val postfix: String = when (item) {
            is Video -> {
                val index = name.lastIndexOf(".")
                if (index == -1) EMPTY_STRING else name.substring(index)
            }
            else /* is VideoDirectory */ -> EMPTY_STRING
        }

        val context = contextThemedFirst
        val view = View.inflate(context, R.layout.dialog_rename_video_list_item, null)

        val titleText = view.findViewById<TextView>(R.id.text_title)
        titleText.setText(
                if (item is Video) R.string.renameVideo
                else /* if (item is VideoDirectory) */ R.string.renameDirectory)

        val thumbImage = view.findViewById<ImageView>(R.id.image_videoListItem)
        val thumbSize = miniThumbSize
        thumbImage.maxWidth = thumbSize
        thumbImage.maxHeight = thumbSize
        thumbImage.adjustViewBounds = true
        val glideRequestManager = Glide.with(context.applicationContext)
        glideRequestManager
                .load((item as? Video ?: (item as VideoDirectory).firstVideoOrNull())?.path)
                .override(thumbSize)
                .fitCenter()
                .placeholder(R.drawable.ic_default_thumb)
                .into(thumbImage)

        val editText =
                view.findViewById<OnBackPressedPreImeEventInterceptableEditText>(R.id.editor_rename)
        editText.hint = name
        editText.setText(name.substring(0, name.length - postfix.length))
        editText.setSelection(editText.text!!.length)
        editText.tag = postfix

        val cancelButton = view.findViewById<TextView>(R.id.btn_cancel_renameVideoListItemDialog)
        cancelButton.setOnClickListener(this)

        val completeButton = view.findViewById<TextView>(R.id.btn_complete_renameVideoListItemDialog)
        completeButton.setOnClickListener(this)
        completeButton.tag = editText

        mRenameItemDialog = FocusObservableDialog(context, R.style.DialogStyle_MinWidth_NoTitle)
                .apply {
                    setContentView(view)
                    show()
                    setCancelable(true)
                    setCanceledOnTouchOutside(false)
                    setOnDismissListener {
                        mRenameItemDialog = null
                        glideRequestManager.clear(thumbImage)
                    }

                    UiUtils.showSoftInputForEditingViewsAccordingly(this, editText)
                }

        val window = mRenameItemDialog!!.window!!
        val decorView = window.decorView as ViewGroup
        decorView.tag = item
        decorView[0].tag = onRenameAction
    }

    override fun showItemDetailsDialog(item: VideoListItem) {
        val context = contextThemedFirst
        val view: View
        val thumbTextView: TextView
        val video: Video?

        val colon = getString(R.string.colon)
        val textColorPrimary = ContextCompat.getColor(context, R.color.textColorPrimary)
        if (item is Video) {
            view = View.inflate(context, R.layout.dialog_video_details, null)

            video = item

            thumbTextView = view.findViewById(R.id.text_videoName)
            var ss = SpannableString(getString(R.string.name, item.name))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            thumbTextView.text = ss

            val videoSizeText = view.findViewById<TextView>(R.id.text_videoSize)
            ss = SpannableString(getString(
                    R.string.size, FileUtils.formatFileSize(item.size.toDouble())))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoSizeText.text = ss

            val videoResolutionText = view.findViewById<TextView>(R.id.text_videoResolution)
            ss = SpannableString(getString(
                    R.string.resolution, VideoUtils2.formatVideoResolution(item.width, item.height)))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoResolutionText.text = ss

            val videoPathText = view.findViewById<TextView>(R.id.text_videoPath)
            ss = SpannableString(getString(R.string.path, item.path))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoPathText.text = ss
        } else /* if (item is VideoDirectory) */ {
            view = View.inflate(context, R.layout.dialog_videodir_details, null)

            video = (item as VideoDirectory).firstVideoOrNull()
            val path = item.path
            val dirname = FileUtils.getFileNameFromFilePath(path)

            thumbTextView = view.findViewById(R.id.text_videodirName)
            var ss = SpannableString(getString(
                    if (item.name.equals(dirname, ignoreCase = true)) R.string.name
                    else R.string.alias
                    , item.name))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            thumbTextView.text = ss

            val videodirSizeText = view.findViewById<TextView>(R.id.text_videodirSize)
            ss = SpannableString(getString(
                    R.string.size, FileUtils.formatFileSize(item.size.toDouble())))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videodirSizeText.text = ss

            val videoCountText = view.findViewById<TextView>(R.id.text_videoCount)
            ss = SpannableString(getString(R.string.videoCount, item.videoCount()))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videoCountText.text = ss

            val videodirPathText = view.findViewById<TextView>(R.id.text_videodirPath)
            ss = SpannableString(getString(R.string.path, path))
            ss.setSpan(ForegroundColorSpan(textColorPrimary),
                    0, ss.indexOf(colon) + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            videodirPathText.text = ss
        }

        val okButton = view.findViewById<TextView>(R.id.btn_ok_videoListItemDetailsDialog)
        okButton.setOnClickListener(this)

        val thumbSize = miniThumbSize
        val thumbTextViewTarget = object : CustomViewTarget<TextView, Drawable>(thumbTextView) {
            val placeholder = ContextCompat.getDrawable(context, R.drawable.ic_default_thumb)!!

            fun showPlaceHolderDrawable() {
                placeholder.setBounds(0, 0, thumbSize, Utils.roundFloat(thumbSize * 9f / 16f))
                thumbTextView.setCompoundDrawables(null, placeholder, null, null)
            }

            override fun onResourceLoading(placeholder: Drawable?) {
                showPlaceHolderDrawable()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                showPlaceHolderDrawable()
            }

            override fun onResourceCleared(placeholder: Drawable?) {
                showPlaceHolderDrawable()
            }

            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                thumbTextView.setCompoundDrawablesWithIntrinsicBounds(null, resource, null, null)
            }
        }
        val glideRequestManager = Glide.with(context.applicationContext)
        glideRequestManager
                .load(video?.path)
                .override(thumbSize)
                .fitCenter()
                .into(thumbTextViewTarget)

        mItemDetailsDialog = AppCompatDialog(context, R.style.DialogStyle_MinWidth_NoTitle)
        mItemDetailsDialog!!.setContentView(view)
        mItemDetailsDialog!!.show()
        mItemDetailsDialog!!.setOnDismissListener {
            mItemDetailsDialog = null
            glideRequestManager.clear(thumbTextViewTarget)
        }
    }

    override fun showVideosMovePage(vararg items: VideoListItem) = presenter.moveItems(*items)

    override fun deleteItems(vararg items: VideoListItem) {
        val dialog = WaitingOverlayDialog(contextThemedFirst)
        dialog.message = resources.getQuantityText(R.plurals.deletingVideosPleaseWait,
                if (items.size == 1
                        && (items[0] is Video || (items[0] as VideoDirectory).videoCount() == 1))
                    1
                else 1.inv())
        dialog.show()
        Executors.THREAD_POOL_EXECUTOR.execute {
            super.deleteItems(*items)
            Executors.MAIN_EXECUTOR.execute {
                dialog.dismiss()
            }
        }
    }

    interface InteractionCallback : ActionBarCallback, RefreshLayoutCallback {
        fun setLightStatus(light: Boolean)

        fun setSideDrawerEnabled(enabled: Boolean)

        fun goToLocalVideoSubListFragment(args: Bundle)

        fun goToVideoMoveFragment(args: Bundle)
    }
}