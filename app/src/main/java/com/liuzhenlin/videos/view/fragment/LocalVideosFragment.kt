/*
 * Created on 2019/10/19 7:08 PM.
 * Copyright © 2019–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.liuzhenlin.common.Configs
import com.liuzhenlin.common.view.SwipeRefreshLayout
import com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout
import com.liuzhenlin.swipeback.SwipeBackLayout
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.presenter.LocalVideoListPresenter
import java.util.LinkedList

/**
 * @author 刘振林
 */
class LocalVideosFragment : Fragment(), ILocalVideosFragment, FragmentPartLifecycleCallback,
        LocalSearchedVideosFragment.InteractionCallback, SwipeBackLayout.SwipeListener,
        SlidingDrawerLayout.OnDrawerScrollListener {

    private lateinit var mInteractionCallback: InteractionCallback

    private lateinit var mLocalVideoListFragment: LocalVideoListFragment
    private var mLocalVideoSubListFragments: MutableList<LocalVideoListFragment>? = null
    private var mLocalSearchedVideosFragment: LocalSearchedVideosFragment? = null
    private var mVideoMoveFragment: VideoMoveFragment? = null

    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout

    private var mSwipeBackScrollPercent = 0.0f

    private fun getOrCreateLocalVideoSubListFragments(): MutableList<LocalVideoListFragment> {
        if (mLocalVideoSubListFragments == null) {
            mLocalVideoSubListFragments = LinkedList()
        }
        return mLocalVideoSubListFragments!!
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val parent = parentFragment
        mInteractionCallback = when {
            parent is InteractionCallback -> parent
            context is InteractionCallback -> context
            parent != null -> throw RuntimeException("Neither $context nor $parent " +
                    "has implemented LocalVideosFragment.InteractionCallback")
            else -> throw RuntimeException(
                    "$context must implement LocalVideosFragment.InteractionCallback")
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contentView = inflater.inflate(R.layout.fragment_local_videos, container, false)
        initViews(contentView)
        return contentView
    }

    private fun initViews(contentView: View) {
        mSwipeRefreshLayout = contentView.findViewById(R.id.swipeRefreshLayout)
        mSwipeRefreshLayout.setColorSchemeResources(*Configs.SWIPE_REFRESH_WIDGET_COLOR_SCHEME)
        mSwipeRefreshLayout.setOnRequestDisallowInterceptTouchEventCallback { true }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            mLocalVideoListFragment = LocalVideoListFragment()
            childFragmentManager.beginTransaction()
                    .add(R.id.container_child_fragments, mLocalVideoListFragment,
                            TAG_LOCAL_VIDEO_LIST_FRAGMENT)
                    .commitNow()
        } else {
            val fm = childFragmentManager

            mLocalVideoListFragment = fm.findFragmentByTag(TAG_LOCAL_VIDEO_LIST_FRAGMENT)
                    as LocalVideoListFragment
            onFragmentAttached(mLocalVideoListFragment)

            for (i in 1..savedInstanceState.getInt(KEY_LOCAL_VIDEO_SUBLIST_FRAGMENT_COUNT)) {
                val f = fm.findFragmentByTag(PREFIX_LOCAL_VIDEO_SUBLIST_FRAGMENT_TAG + i)
                        as LocalVideoListFragment
                getOrCreateLocalVideoSubListFragments().add(f)
                onFragmentAttached(f)
            }

            mLocalSearchedVideosFragment = fm.findFragmentByTag(TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT)
                    as LocalSearchedVideosFragment?
            if (mLocalSearchedVideosFragment != null) {
                onFragmentAttached(mLocalSearchedVideosFragment!!)
            }

            mVideoMoveFragment = fm.findFragmentByTag(TAG_VIDEO_MOVE_FRAGMENT) as VideoMoveFragment?
            if (mVideoMoveFragment != null) {
                onFragmentAttached(mVideoMoveFragment!!)
            }
        }
    }

    override fun onFragmentAttached(childFragment: Fragment) {
        // this::mLocalVideoListFragment.isInitialized用于判断我们是否已经初始化了子Fragment属性
        // 在Activity被系统销毁后自动创建时，子Fragment属性还未在此类的onViewCreated()方法中被初始化，
        // 但子Fragments已经attach到此Fragment了
        if (!this::mLocalVideoListFragment.isInitialized) return

        when {
            childFragment is LocalVideoListFragment -> {
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)

                val sublistFragmentIndex = mLocalVideoSubListFragments?.indexOf(childFragment) ?: -1
                if (sublistFragmentIndex >= 0) {
                    mLocalVideoListFragment.presenter
                            .addOnVideoItemsLoadListener(childFragment.presenter)
                    if (childFragment.presenter is LocalVideoListPresenter) {
                        val lastFragment =
                                if (sublistFragmentIndex > 0)
                                    mLocalVideoSubListFragments!![sublistFragmentIndex - 1]
                                else
                                    mLocalVideoListFragment
                        val parentPresenter = lastFragment.presenter
                        if (parentPresenter is LocalVideoListPresenter)
                            childFragment.presenter.setParentPresenter(parentPresenter)
                    }

                    mInteractionCallback.setSideDrawerEnabled(false)
                    mInteractionCallback.onLocalVideoSublistFragmentAttached(childFragment)
                    mInteractionCallback.showTabItems(false)
                    mInteractionCallback.setTabItemsEnabled(false)
                }
            }

            childFragment === mLocalSearchedVideosFragment -> {
                childFragment.setVideoOpCallback(mLocalVideoListFragment)
                mLocalVideoListFragment.presenter.addOnVideoItemsLoadListener(childFragment.presenter)
                mSwipeRefreshLayout.setOnRefreshListener(childFragment)

                mInteractionCallback.setSideDrawerEnabled(false)
                mInteractionCallback.onLocalSearchedVideosFragmentAttached(childFragment)
                mInteractionCallback.showTabItems(false)
            }
        }
    }

    override fun onFragmentViewCreated(childFragment: Fragment) {
        if (mLocalVideoSubListFragments?.contains(childFragment) == true) {
            (childFragment as LocalVideoListFragment).swipeBackLayout.addSwipeListener(this)
        }
    }

    override fun onFragmentViewDestroyed(childFragment: Fragment) {}

    override fun onFragmentDetached(childFragment: Fragment) {
        when {
            childFragment is LocalVideoListFragment -> {
                val sublistFragmentIndex = mLocalVideoSubListFragments?.indexOf(childFragment) ?: -1
                if (sublistFragmentIndex >= 0) {
                    mLocalVideoSubListFragments!!.removeAt(sublistFragmentIndex)
                    mLocalVideoListFragment.presenter
                            .removeOnVideoItemsLoadListener(childFragment.presenter)
                    if (sublistFragmentIndex == 0) {
                        mSwipeRefreshLayout.setOnRefreshListener(mLocalVideoListFragment)

                        mInteractionCallback.setSideDrawerEnabled(true)
                        mInteractionCallback.setTabItemsEnabled(true)
                    } else {
                        mSwipeRefreshLayout.setOnRefreshListener(
                                mLocalVideoSubListFragments!![sublistFragmentIndex - 1])
                    }
                    mInteractionCallback.onLocalVideoSublistFragmentDetached(childFragment)
                }
            }
            childFragment === mLocalSearchedVideosFragment -> {
                mLocalVideoListFragment.presenter
                        .removeOnVideoItemsLoadListener(childFragment.presenter)
                mLocalSearchedVideosFragment = null
                mSwipeRefreshLayout.setOnRefreshListener(mLocalVideoListFragment)

                mInteractionCallback.setSideDrawerEnabled(true)
                mInteractionCallback.onLocalSearchedVideosFragmentDetached(childFragment)
                mInteractionCallback.showTabItems(true)
            }
            childFragment === mVideoMoveFragment -> {
                mVideoMoveFragment = null
            }
        }
    }

    override fun goToLocalVideoSubListFragment(args: Bundle) {
        getOrCreateLocalVideoSubListFragments().let {
            val fragment = LocalVideoListFragment()
            val lastFragment = if (it.size > 0) it[it.size - 1] else mLocalVideoListFragment
            fragment.arguments = args
            fragment.setTargetFragment(lastFragment, RESULT_CODE_LOCAL_VIDEO_SUBLIST_FRAGMENT)
            it.add(fragment)

            childFragmentManager.beginTransaction()
                    .setCustomAnimations(
                            R.anim.anim_open_enter, R.anim.anim_open_exit,
                            R.anim.anim_close_enter, R.anim.anim_close_exit)
                    .hide(lastFragment)
                    .add(R.id.container_child_fragments, fragment,
                            PREFIX_LOCAL_VIDEO_SUBLIST_FRAGMENT_TAG + it.size)
                    .addToBackStack(PREFIX_LOCAL_VIDEO_SUBLIST_FRAGMENT_TAG + it.size)
                    .commit()
            childFragmentManager.executePendingTransactions()
        }
    }

    override fun goToLocalSearchedVideosFragment() {
        mLocalSearchedVideosFragment = LocalSearchedVideosFragment()
        mLocalVideoListFragment.presenter.setArgsForLocalSearchedVideosFragment(
                mLocalSearchedVideosFragment!!)
        mLocalSearchedVideosFragment!!.setTargetFragment(
                mLocalVideoListFragment, REQUEST_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT)

        childFragmentManager.beginTransaction()
                .add(R.id.container_child_fragments, mLocalSearchedVideosFragment!!,
                        TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT)
                .addToBackStack(TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT)
                .commit()
        childFragmentManager.executePendingTransactions()
    }

    override fun goToVideoMoveFragment(args: Bundle) {
        val it = mLocalVideoSubListFragments
        val topVideoListFragment =
                if (it != null && it.size > 0) {
                    it[it.size - 1]
                } else {
                    mLocalVideoListFragment
                }
        mVideoMoveFragment = VideoMoveFragment()
        mVideoMoveFragment!!.arguments = args
        mVideoMoveFragment!!.setTargetFragment(
                topVideoListFragment, REQUEST_CODE_VIDEO_MOVE_FRAGMENT)
        mVideoMoveFragment!!.showNow(childFragmentManager, TAG_VIDEO_MOVE_FRAGMENT)
    }

    override fun onBackPressed(): Boolean {
        if (mLocalSearchedVideosFragment != null) {
            childFragmentManager.popBackStackImmediate()
            return true
        }
        mLocalVideoSubListFragments?.let {
            if (it.isNotEmpty()) {
                val last = it.last()
                if (last.onBackPressed())
                    return true
            }
        }
        return mLocalVideoListFragment.onBackPressed()
    }

    override fun getActionBar(fragment: Fragment): View =
            mInteractionCallback.getActionBar(fragment)

    override fun isRefreshLayoutEnabled() = mSwipeRefreshLayout.isEnabled

    override fun setRefreshLayoutEnabled(enabled: Boolean) {
        mSwipeRefreshLayout.isEnabled = enabled
    }

    override fun isRefreshLayoutRefreshing() = mSwipeRefreshLayout.isRefreshing

    override fun setRefreshLayoutRefreshing(refreshing: Boolean) {
        mSwipeRefreshLayout.isRefreshing = refreshing
    }

    override fun setOnRefreshLayoutChildScrollUpCallback(
            callback: SwipeRefreshLayout.OnChildScrollUpCallback?) {
        mSwipeRefreshLayout.setOnChildScrollUpCallback(callback)
    }

    override fun onScrollStateChange(edge: Int, state: Int) {
        when (state) {
            SwipeBackLayout.STATE_DRAGGING,
            SwipeBackLayout.STATE_SETTLING -> {
                mInteractionCallback.showPreviousActionBar(true)
                if (mLocalVideoSubListFragments?.size == 1) {
                    mInteractionCallback.showTabItems(true)
                }
            }
            SwipeBackLayout.STATE_IDLE ->
                if (mSwipeBackScrollPercent == 0.0f) {
                    mInteractionCallback.showPreviousActionBar(false)
                    mInteractionCallback.showTabItems(false)
                }
        }
    }

    override fun onScrollPercentChange(edge: Int, percent: Float) {
        mSwipeBackScrollPercent = percent
        mInteractionCallback.setPreviousActionBarAlpha(percent)
        mInteractionCallback.setActionBarAlpha(1 - percent)
    }

    override fun onDrawerOpened(parent: SlidingDrawerLayout, drawer: View) {
    }

    override fun onDrawerClosed(parent: SlidingDrawerLayout, drawer: View) {
    }

    override fun onScrollPercentChange(parent: SlidingDrawerLayout, drawer: View, percent: Float) {
        if (view == null) return
    }

    override fun onScrollStateChange(parent: SlidingDrawerLayout, drawer: View, state: Int) {
        if (view == null) return

        when (state) {
            SlidingDrawerLayout.SCROLL_STATE_TOUCH_SCROLL,
            SlidingDrawerLayout.SCROLL_STATE_AUTO_SCROLL -> {
                mSwipeRefreshLayout[0] /* fragment container */
                        .setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            SlidingDrawerLayout.SCROLL_STATE_IDLE -> {
                mSwipeRefreshLayout[0] /* fragment container */
                        .setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_LOCAL_VIDEO_SUBLIST_FRAGMENT_COUNT,
                mLocalVideoSubListFragments?.size ?: 0)
    }

    private companion object {
        const val PREFIX_LOCAL_VIDEO_SUBLIST_FRAGMENT_TAG = "LocalVideoSublistFragment$"
        const val TAG_LOCAL_VIDEO_LIST_FRAGMENT = "LocalVideoListFragment"
        const val TAG_LOCAL_SEARCHED_VIDEOS_FRAGMENT = "LocalSearchedVideosFragment"
        const val TAG_VIDEO_MOVE_FRAGMENT = "VideoMoveFragment"

        const val KEY_LOCAL_VIDEO_SUBLIST_FRAGMENT_COUNT = "localVideoSublistFragmentCount"
    }

    interface InteractionCallback : LocalVideoListFragment.InteractionCallback {
        fun showPreviousActionBar(show: Boolean)
        fun setPreviousActionBarAlpha(alpha: Float)
        fun setActionBarAlpha(alpha: Float)

        fun showTabItems(show: Boolean)
        fun setTabItemsEnabled(enabled: Boolean)

        fun onLocalVideoSublistFragmentAttached(fragment: Fragment)
        fun onLocalVideoSublistFragmentDetached(fragment: Fragment)

        fun onLocalSearchedVideosFragmentAttached(fragment: Fragment)
        fun onLocalSearchedVideosFragmentDetached(fragment: Fragment)
    }
}
