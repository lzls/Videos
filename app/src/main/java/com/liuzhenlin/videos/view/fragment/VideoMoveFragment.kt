/*
 * Created on 2023-3-15 3:18:45 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.liuzhenlin.circularcheckbox.CircularCheckBox
import com.liuzhenlin.common.Configs.ScreenWidthDpLevel
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.common.windowhost.WaitingOverlayDialog
import com.liuzhenlin.videos.R
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.contextThemedFirst
import com.liuzhenlin.videos.presenter.IVideoMovePresenter
import com.liuzhenlin.videos.presenter.Presenter
import com.liuzhenlin.videos.utils.VideoUtils2
import com.liuzhenlin.videos.videoCount
import com.liuzhenlin.videos.view.IView
import com.liuzhenlin.videos.view.fragment.Payloads.PAYLOAD_REFRESH_CHECKBOX

typealias TargetDirListAdapter = ImageLoadingListAdapter<out IVideoMoveView.TargetDirListViewHolder>

interface IVideoMoveView : IView<IVideoMovePresenter> {
    fun getArguments(): Bundle?
    fun onReturnResult(resultCode: Int, data: Intent?)

    fun init(adapter: TargetDirListAdapter, videoQuantity: Int)

    fun showVideoMovePromptDialog()
    fun onVideoMoveStart()
    fun onVideoMoveFinish(moved: Boolean)

    fun setTargetDirListItemChecked(position: Int, checked: Boolean)
    fun onCheckedTargetDirListItemCountChanged(checkedItemCount: Int)

    fun newTargetDirListViewHolder(parent: ViewGroup): TargetDirListViewHolder

    abstract class TargetDirListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bindData(videodir: VideoDirectory, position: Int, payloads: List<Any>)
        abstract fun loadItemImages(video: Video)
        abstract fun cancelLoadingItemImages()
    }
}

private const val PAYLOAD_REFRESH_VIDEODIR_THUMB = Payloads.PAYLOAD_LAST shl 1

class VideoMoveFragment : FullscreenDialogFragment(R.layout.fragment_video_move), IVideoMoveView,
        View.OnClickListener {

    private var mVideoDirList: RecyclerView? = null
    private var mTitleText: TextView? = null
    private var mOkayButton: View? = null

    private var mVideoMovingDialog: Dialog? = null

    private var mPresenter: IVideoMovePresenter? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) =
                    mPresenter?.onViewStart(this@VideoMoveFragment) ?: Unit

            override fun onResume(owner: LifecycleOwner) =
                    mPresenter?.onViewResume(this@VideoMoveFragment) ?: Unit

            override fun onPause(owner: LifecycleOwner) =
                    mPresenter?.onViewPaused(this@VideoMoveFragment) ?: Unit

            override fun onStop(owner: LifecycleOwner) =
                    mPresenter?.onViewStopped(this@VideoMoveFragment) ?: Unit
        })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mPresenter = Presenter.Provider(this).get(IVideoMovePresenter.getImplClass())
        mPresenter?.attachToView(this)
    }

    override fun onDetach() {
        super.onDetach()
        mPresenter?.detachFromView(this)
    }

    override fun onReturnResult(resultCode: Int, data: Intent?) {
        targetFragment?.onActivityResult(targetRequestCode, resultCode, data)
    }

    override fun onScreenWidthDpLevelChanged(
            oldLevel: ScreenWidthDpLevel, level: ScreenWidthDpLevel) {
        val adapter = mVideoDirList?.adapter
        adapter?.notifyItemRangeChanged(0, adapter.itemCount, PAYLOAD_REFRESH_VIDEODIR_THUMB)
    }

    override fun onDialogCreated(dialog: Dialog, savedInstanceState: Bundle?) {
        super.onDialogCreated(dialog, savedInstanceState)
        mPresenter?.onViewCreated(this, savedInstanceState)
    }

    override fun init(adapter: TargetDirListAdapter, videoQuantity: Int) {
        val dialog = requireDialog()
        val context = contextThemedFirst

        mTitleText = dialog.findViewById(R.id.text_title)
        mTitleText!!.text =
                resources.getQuantityText(R.plurals.moveVideosTo, videoQuantity)
        mTitleText!!.tag = videoQuantity

        mVideoDirList = dialog.findViewById<RecyclerView?>(R.id.recycler_videoMoveTargetList)
                .apply {
                    layoutManager = LinearLayoutManager(context)
                    this.adapter = adapter
                    addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                    setHasFixedSize(true)
                }

        dialog.findViewById<View>(R.id.btn_cancel).setOnClickListener(this)
        mOkayButton = dialog.findViewById(R.id.btn_ok)
        mOkayButton!!.setOnClickListener(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        mPresenter?.restoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mPresenter?.saveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mVideoMovingDialog?.dismiss()
        mPresenter?.onViewDestroyed(this)
        mVideoDirList = null
        mTitleText = null
        mOkayButton = null
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_ok -> mPresenter?.moveVideosToCheckedDir()
            R.id.btn_cancel -> dismiss()

            R.id.btn_ok_vmpd -> {
                val promptDialog = v.tag as AppCompatDialog
                val checkbox = promptDialog.findViewById<CheckBox>(R.id.checkbox)
                val neverPromptAgain = checkbox!!.isChecked
                promptDialog.cancel()
                mPresenter?.onVideoMovePromptConfirmed(neverPromptAgain)
            }
            R.id.btn_cancel_vmpd -> {
                val promptDialog = v.tag as AppCompatDialog
                promptDialog.cancel()
            }
        }
    }

    override fun showVideoMovePromptDialog() {
        val context = contextThemedFirst
        val promptDialog = AppCompatDialog(context, R.style.DialogStyle_MinWidth_NoTitle)
        val view = View.inflate(context, R.layout.dialog_video_move_prompt, null)
                .apply {
                    findViewById<TextView>(R.id.text_message).movementMethod =
                            ScrollingMovementMethod.getInstance()
                    val cancelButton = findViewById<View>(R.id.btn_cancel_vmpd)
                    cancelButton.setOnClickListener(this@VideoMoveFragment)
                    cancelButton.tag = promptDialog
                    val okButton = findViewById<View>(R.id.btn_ok_vmpd)
                    okButton.setOnClickListener(this@VideoMoveFragment)
                    okButton.tag = promptDialog
                }
        promptDialog.setContentView(view)
        promptDialog.show()
    }

    override fun onVideoMoveStart() {
        if (mVideoMovingDialog == null) {
            val context = contextThemedFirst
            val waitingDialog = WaitingOverlayDialog(context)
            waitingDialog.message = resources.getQuantityText(R.plurals.movingVideosPleaseWait,
                    mTitleText!!.tag as Int)
            waitingDialog.show()
            waitingDialog.setOnDismissListener {
                mVideoMovingDialog = null
            }
            mVideoMovingDialog = waitingDialog
        }
    }

    override fun onVideoMoveFinish(moved: Boolean) {
        val dialog = mVideoMovingDialog
        if (dialog != null) {
            dismiss()
            dialog.dismiss()
        }
    }

    override fun setTargetDirListItemChecked(position: Int, checked: Boolean) {
        mVideoDirList?.adapter?.notifyItemChanged(position, PAYLOAD_REFRESH_CHECKBOX)
    }

    override fun onCheckedTargetDirListItemCountChanged(checkedItemCount: Int) {
        mOkayButton?.isEnabled = checkedItemCount == 1
    }

    override fun newTargetDirListViewHolder(parent: ViewGroup): IVideoMoveView.TargetDirListViewHolder {
        return TargetDirListViewHolder(
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_video_move_target_list, parent, false))
    }

    private inner class TargetDirListViewHolder(itemView: View)
        : IVideoMoveView.TargetDirListViewHolder(itemView), View.OnClickListener {
        val checkBox: CircularCheckBox = itemView.findViewById(R.id.checkbox)
        val videodirImage: ImageView = itemView.findViewById(R.id.image_videodir)
        val videodirNameText: TextView = itemView.findViewById(R.id.text_videodirName)
        val videodirPathText: TextView = itemView.findViewById(R.id.text_videodirPath)
        val videoCountText: TextView = itemView.findViewById(R.id.text_videoCount)

        init {
            itemView.setOnClickListener(this)
            checkBox.setOnClickListener(this)
        }

        override fun bindData(videodir: VideoDirectory, position: Int, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                itemView.tag = position
                checkBox.tag = position

                val videoCount = videodir.videoCount(includeDescendants = false)
                checkBox.isChecked = videodir.isChecked
                videodirNameText.text = videodir.name
                videodirPathText.text = videodir.path
                videoCountText.text =
                        resources.getQuantityString(
                                R.plurals.aTotalOfSeveralVideos, videoCount, videoCount)
            } else {
                for (payload in payloads) {
                    when (payload) {
                        PAYLOAD_REFRESH_CHECKBOX ->
                            checkBox.isChecked = videodir.isChecked
                        PAYLOAD_REFRESH_VIDEODIR_THUMB ->
                            @Suppress("UNCHECKED_CAST")
                            (bindingAdapter as ImageLoadingListAdapter<TargetDirListViewHolder>)
                                    .loadItemImagesIfNotScrolling(this)
                    }
                }
            }
        }

        override fun loadItemImages(video: Video) {
            VideoUtils2.loadVideoThumbIntoFragmentImageView(
                    this@VideoMoveFragment, videodirImage, video)
        }

        override fun cancelLoadingItemImages() {
            Glide.with(this@VideoMoveFragment).clear(videodirImage)
        }

        override fun onClick(v: View) {
            val position = v.tag as Int
            mPresenter?.toggleTargetDirChecked(position)
        }
    }
}