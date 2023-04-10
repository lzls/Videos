/*
 * Created on 2018/08/15.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.bumptech.glide.Glide
import com.liuzhenlin.common.Configs.ScreenWidthDpLevel
import com.liuzhenlin.common.Consts.*
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.common.utils.UiUtils
import com.liuzhenlin.common.utils.Utils
import com.liuzhenlin.common.view.SwipeRefreshLayout
import com.liuzhenlin.floatingmenu.FloatingMenu
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.presenter.ILocalSearchedVideosPresenter
import com.liuzhenlin.videos.utils.VideoUtils2
import com.liuzhenlin.videos.view.IView
import com.liuzhenlin.videos.view.fragment.Payloads.*
import java.util.*

/**
 * @author 刘振林
 */

interface ILocalSearchedVideosView : IView<ILocalSearchedVideosPresenter>,
        VideoListItemOpCallback<Video> {

    public val searchText: String

    fun getArguments(): Bundle?
    fun onReturnResult(resultCode: Int, data: Intent?)

    fun onVideosLoadStart()
    fun onVideosLoadFinish()
    fun onVideosLoadCanceled()

    fun updateListVisibilityAndSearchResultText()

    fun newSearchedVideoListViewHolder(parent: ViewGroup): SearchedVideoListViewHolder

    abstract class SearchedVideoListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bindData(video: Video, position: Int, payloads: List<Any>)
        abstract fun loadItemImages(video: Video)
        abstract fun cancelLoadingItemImages()
    }
}

class LocalSearchedVideosFragment : BaseFragment(), ILocalSearchedVideosView, View.OnClickListener,
        View.OnLongClickListener, View.OnTouchListener, SwipeRefreshLayout.OnRefreshListener {

    private lateinit var mInteractionCallback: InteractionCallback
    private var mLifecycleCallback: FragmentPartLifecycleCallback? = null

    private var mVideoOpCallback: VideoListItemOpCallback<Video>? = null

    private var mSearchText = EMPTY_STRING
    private lateinit var mSearchSrcEditText: EditText
    private lateinit var mSearchResultTextView: TextView
    private lateinit var mRecyclerView: RecyclerView
    private val mAdapterWrapper by lazy(LazyThreadSafetyMode.NONE) {
        presenter.getSearchedVideoListAdapter() }
    private var mSelectedItemIndex = NO_POSITION

    internal val presenter = ILocalSearchedVideosPresenter.newInstance()

    private var mVideoOptionsMenu: FloatingMenu? = null
    private var mDownX = 0
    private var mDownY = 0

    override val searchText: String
        get() = mSearchText

    init {
        lifecycle.addObserver(object: DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) =
                    presenter.onViewStart(this@LocalSearchedVideosFragment)

            override fun onResume(owner: LifecycleOwner) =
                    presenter.onViewResume(this@LocalSearchedVideosFragment)

            override fun onPause(owner: LifecycleOwner) =
                    presenter.onViewPaused(this@LocalSearchedVideosFragment)

            override fun onStop(owner: LifecycleOwner) =
                    presenter.onViewStopped(this@LocalSearchedVideosFragment)
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
                    "has implemented LocalSearchedVideosFragment.InteractionCallback")
            else -> throw RuntimeException(
                    "$context must implement LocalSearchedVideosFragment.InteractionCallback")
        }

        if (parent is FragmentPartLifecycleCallback) {
            mLifecycleCallback = parent
        } else if (context is FragmentPartLifecycleCallback) {
            mLifecycleCallback = context
        }
        mLifecycleCallback?.onFragmentAttached(this)

        presenter.attachToView(this)
    }

    override fun onScreenWidthDpLevelChanged(
            oldLevel: ScreenWidthDpLevel, level: ScreenWidthDpLevel) {
        val headersCount = mAdapterWrapper.headersCount
        mAdapterWrapper.notifyItemRangeChanged(headersCount, mAdapterWrapper.itemCount - headersCount,
                PAYLOAD_REFRESH_VIDEO_THUMB)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_local_searched_videos, container, false)
        initViews(view)
        return view
    }

    private fun initViews(contentView: View) {
        contentView.setOnTouchListener(this)

        val actionbar = mInteractionCallback.getActionBar(this)
        actionbar.findViewById<SearchView>(R.id.searchview).run {
            UiUtils.setViewMargins(findViewById(R.id.search_edit_frame), 0, 0, 0, 0)

            findViewById<View>(R.id.search_plate)
                    .setBackgroundResource(R.drawable.bg_search_view_plate)

            mSearchSrcEditText = findViewById<SearchView.SearchAutoComplete>(R.id.search_src_text)
            mSearchSrcEditText.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.subtitle_text_size))

            onActionViewExpanded()
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                override fun onQueryTextSubmit(query: String) = true

                override fun onQueryTextChange(newText: String): Boolean {
                    mSearchText = newText.trim()
                    presenter.refreshList(true)
                    return true
                }
            })
        }
        actionbar.findViewById<View>(R.id.btn_cancelSearch).setOnClickListener(this)

        mRecyclerView = contentView.findViewById(R.id.recycler_searchedVideoList)
        mRecyclerView.layoutManager = LinearLayoutManager(contentView.context)
        mRecyclerView.adapter = mAdapterWrapper.also {
            mSearchResultTextView =
                    LayoutInflater.from(contentView.context)
                            .inflate(R.layout.text_search_result, mRecyclerView, false) as TextView
            it.addHeaderView(mSearchResultTextView)
        }
        mRecyclerView.addItemDecoration(DividerItemDecoration(contentView.context))
        mRecyclerView.setHasFixedSize(true)
//        mRecyclerView.setOnTouchListener(this) // 无效
        mInteractionCallback.setOnRefreshLayoutChildScrollUpCallback { _, _ ->
            if (mSearchText == EMPTY_STRING) {
                true
            } else {
                mRecyclerView.canScrollVertically(-1)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent) = when {
        v === view -> {
            if (event.action == MotionEvent.ACTION_UP) {
                UiUtils.hideSoftInput(mSearchSrcEditText, true)
                requireFragmentManager().popBackStackImmediate()
            }
            false
        }
        v.parent === mRecyclerView -> {
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (SDK_VERSION >= SDK_VERSION_SUPPORTS_MULTI_WINDOW) {
                    val itemLocationInWindow = IntArray(2)
                    v.getLocationInWindow(itemLocationInWindow)
                    mDownX = Utils.roundFloat(event.x + itemLocationInWindow[0])
                    mDownY = Utils.roundFloat(event.y + itemLocationInWindow[1])
                } else {
                    mDownX = Utils.roundFloat(event.rawX)
                    mDownY = Utils.roundFloat(event.rawY)
                }

                UiUtils.hideSoftInput(mSearchSrcEditText, true)
                mRecyclerView.requestFocus()
            }
            false
        }
        else -> false
    }

    override fun onClick(v: View) = when {
        v.id == R.id.btn_cancelSearch -> {
            UiUtils.hideSoftInput(mSearchSrcEditText, true)
            requireFragmentManager().popBackStackImmediate()
            Unit
        }
        v.parent === mRecyclerView -> {
            presenter.playVideoAt(v.tag as Int)
        }
        else -> Unit
    }

    override fun onLongClick(v: View) = if (v.parent === mRecyclerView) {
        val index = v.tag as Int
        val headersCount = mAdapterWrapper.headersCount
        val position = headersCount + index
        val videoWritable = presenter.isVideoWritable(index)

        mVideoOptionsMenu = FloatingMenu(mRecyclerView)
        mVideoOptionsMenu!!.inflate(R.menu.floatingmenu_video_ops)
        mVideoOptionsMenu!!.setItemEnabled(R.id.move,
                videoWritable && App.getInstance(v.context).hasAllFilesAccess())
        mVideoOptionsMenu!!.setItemEnabled(R.id.delete, videoWritable)
        mVideoOptionsMenu!!.setItemEnabled(R.id.rename, videoWritable)
        mVideoOptionsMenu!!.setOnItemClickListener { menuItem, _ ->
            when (menuItem.iconResId) {
                R.drawable.ic_file_move_menu -> presenter.moveVideoAt(index)
                R.drawable.ic_delete_24dp_menu -> presenter.deleteVideoAt(index)
                R.drawable.ic_edit_24dp_menu -> presenter.renameVideoAt(index)
                R.drawable.ic_share_24dp_menu -> presenter.shareVideoAt(index)
                R.drawable.ic_info_24dp_menu -> presenter.viewDetailsOfVideoAt(index)
            }
        }
        mVideoOptionsMenu!!.setOnDismissListener {
            mSelectedItemIndex = NO_POSITION
            mAdapterWrapper.notifyItemChanged(position, PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS)
            mVideoOptionsMenu = null
        }
        mVideoOptionsMenu!!.show(mDownX, mDownY)

        // 高亮选中的itemView
        mSelectedItemIndex = index
        mAdapterWrapper.notifyItemChanged(position, PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS)

        true
    } else false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mLifecycleCallback?.onFragmentViewCreated(this)
        presenter.onViewCreated(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mLifecycleCallback?.onFragmentViewDestroyed(this)

        mSelectedItemIndex = NO_POSITION
        mVideoOptionsMenu?.dismiss()

        if (mSearchText != EMPTY_STRING) {
            mSearchText = EMPTY_STRING
            presenter.refreshList(true)
        }
        presenter.stopLoadVideos()
        presenter.onViewDestroyed(this)

        mInteractionCallback.setOnRefreshLayoutChildScrollUpCallback(null)
    }

    override fun onDetach() {
        super.onDetach()
        mLifecycleCallback?.onFragmentDetached(this)
        presenter.detachFromView(this)
    }

    override fun onReturnResult(resultCode: Int, data: Intent?) {
        targetFragment?.onActivityResult(targetRequestCode, resultCode, data)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        presenter.onActivityResult(requestCode, resultCode, data)
    }

    override fun updateListVisibilityAndSearchResultText() {
        val videoCount = mAdapterWrapper.itemCount - mAdapterWrapper.headersCount
        if (videoCount == 0) {
            mRecyclerView.visibility = View.GONE
        } else {
            mRecyclerView.visibility = View.VISIBLE
            mSearchResultTextView.text =
                    resources.getQuantityString(R.plurals.findSomeVideos, videoCount, videoCount)
        }
    }

    override fun onRefresh() = presenter.startLoadVideos()

    override fun onVideosLoadStart() {
    }

    override fun onVideosLoadFinish() {
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

    override fun newSearchedVideoListViewHolder(parent: ViewGroup)
            : ILocalSearchedVideosView.SearchedVideoListViewHolder {
        return SearchedVideoListViewHolder(
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_searched_video_list, parent, false))
    }

    private inner class SearchedVideoListViewHolder(itemView: View)
        : ILocalSearchedVideosView.SearchedVideoListViewHolder(itemView) {

        val selectorView: View = (itemView as ViewGroup)[0]
        val videoImage: ImageView = itemView.findViewById(R.id.image_video)
        val videoNameText: TextView = itemView.findViewById(R.id.text_videoName)
        val videoProgressAndDurationText: TextView =
                itemView.findViewById(R.id.text_videoProgressAndDuration)

        init {
            itemView.setOnTouchListener(this@LocalSearchedVideosFragment)
            itemView.setOnClickListener(this@LocalSearchedVideosFragment)
            itemView.setOnLongClickListener(this@LocalSearchedVideosFragment)
        }

        override fun bindData(video: Video, position: Int, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                itemView.tag = position

                highlightSelectedItemIfExists(position)

                updateItemName(video.name)
                videoProgressAndDurationText.text =
                        VideoUtils2.concatVideoProgressAndDuration(video.progress, video.duration)
            } else {
                for (payload in payloads) {
                    if (payload !is Int) continue
                    if (payload and PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS != 0) {
                        highlightSelectedItemIfExists(position)
                    }
                    if (payload and PAYLOAD_REFRESH_VIDEO_THUMB != 0) {
                        @Suppress("UNCHECKED_CAST")
                        val adapter = mAdapterWrapper.innerAdapter
                                as ImageLoadingListAdapter<RecyclerView.ViewHolder>
                        adapter.loadItemImagesIfNotScrolling(this)
                    }
                    if (payload and PAYLOAD_REFRESH_ITEM_NAME != 0) {
                        updateItemName(video.name)
                    }
                    if (payload and PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION != 0) {
                        val (_, _, _, _, _, _, progress, duration) = video
                        videoProgressAndDurationText.text =
                                VideoUtils2.concatVideoProgressAndDuration(progress, duration)
                    }
                }
            }
        }

        override fun loadItemImages(video: Video) {
            VideoUtils2.loadVideoThumbIntoFragmentImageView(
                    this@LocalSearchedVideosFragment, videoImage, video)
        }

        override fun cancelLoadingItemImages() {
            Glide.with(this@LocalSearchedVideosFragment).clear(videoImage)
        }

        // 高亮搜索关键字
        @SuppressLint("DefaultLocale")
        fun updateItemName(name: String) {
            val text = SpannableString(name)
            var fromIndex = 0
            for (char in mSearchText.toCharArray()) {
                val start = name.lowercase().indexOf(char.lowercase(), fromIndex)
                text.setSpan(ForegroundColorSpan(COLOR_ACCENT),
                        start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                fromIndex = start + 1
            }
            videoNameText.text = text
        }

        fun highlightSelectedItemIfExists(position: Int) {
            if (position == mSelectedItemIndex) {
                if (selectorView.tag == null) {
                    selectorView.tag = selectorView.background
                }
                selectorView.setBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.selectorColor))
            } else {
                ViewCompat.setBackground(selectorView, selectorView.tag as Drawable? ?: return)
            }
        }
    }

    @SuppressLint("LongLogTag")
    private class DividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

        var mDivider: Drawable? = null

        val mBounds = Rect()

        init {
            val a = context.obtainStyledAttributes(ATTRS)
            mDivider = a.getDrawable(0)
            if (mDivider == null) {
                Log.w(TAG, "@android:attr/listDivider was not set in the theme used for this "
                        + "DividerItemDecoration. Please set that attribute all call setDivider()")
            }
            a.recycle()
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val divider = mDivider ?: return

            canvas.save()

            val left: Int
            val right: Int
            if (parent.clipToPadding) {
                left = parent.paddingLeft
                right = parent.width - parent.paddingRight
                canvas.clipRect(left, parent.paddingTop, right,
                        parent.height - parent.paddingBottom)
            } else {
                left = 0
                right = parent.width
            }

            for (i in 0 until parent.childCount) {
                val child = parent[i]
                if (parent.getChildAdapterPosition(child) >= HEADER_COUNT) {
                    parent.getDecoratedBoundsWithMargins(child, mBounds)
                    val bottom = mBounds.bottom + Utils.roundFloat(child.translationY)
                    val top = bottom - divider.intrinsicHeight
                    divider.setBounds(left, top, right, bottom)
                    divider.draw(canvas)
                }
            }

            canvas.restore()
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                                    state: RecyclerView.State) {
            val divider = mDivider
            if (divider == null) {
                outRect.set(0, 0, 0, 0)
                return
            }

            val dividerHeight = divider.intrinsicHeight
            outRect.set(0, 0, 0,
                    if (parent.getChildAdapterPosition(view) >= HEADER_COUNT) dividerHeight else 0)
        }
    }

    private companion object {
        const val PAYLOAD_HIGHLIGHT_SELECTED_ITEM_IF_EXISTS = PAYLOAD_LAST shl 1

        // Constants of LocalSearchedVideosFragment$DividerItemDecoration
        const val HEADER_COUNT = 1
        const val TAG = "LocalSearchedVideosFragment\$DividerItemDecoration"
        @JvmField
        val ATTRS = intArrayOf(android.R.attr.listDivider)
    }

    interface InteractionCallback : ActionBarCallback, RefreshLayoutCallback
}
