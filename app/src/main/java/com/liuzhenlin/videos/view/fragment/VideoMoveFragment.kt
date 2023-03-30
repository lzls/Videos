/*
 * Created on 2023-3-15 3:18:45 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.liuzhenlin.circularcheckbox.CircularCheckBox
import com.liuzhenlin.common.Configs.ScreenWidthDpLevel
import com.liuzhenlin.common.adapter.ImageLoadingListAdapter
import com.liuzhenlin.common.utils.Executors
import com.liuzhenlin.common.windowhost.WaitingOverlayDialog
import com.liuzhenlin.videos.R
import com.liuzhenlin.videos.RESULT_CODE_VIDEO_MOVE_FRAGMENT
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.contextThemedFirst
import com.liuzhenlin.videos.dao.AppPrefs
import com.liuzhenlin.videos.presenter.IVideoMovePresenter
import com.liuzhenlin.videos.utils.VideoUtils2
import com.liuzhenlin.videos.view.IView
import com.liuzhenlin.videos.view.fragment.PackageConsts.PAYLOAD_REFRESH_CHECKBOX

interface IVideoMoveView : IView<IVideoMovePresenter> {
    fun getArguments(): Bundle?

    fun setTargetDirListItemChecked(position: Int, checked: Boolean)

    fun newTargetDirListViewHolder(parent: ViewGroup): TargetDirListViewHolder

    abstract class TargetDirListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bindData(videodir: VideoDirectory, position: Int, payloads: List<Any>)
        abstract fun loadItemImages(video: Video)
        abstract fun cancelLoadingItemImages()
    }
}

private const val PAYLOAD_REFRESH_VIDEODIR_THUMB = PackageConsts.PAYLOAD_LAST shl 1

class VideoMoveFragment : FullscreenDialogFragment<IVideoMovePresenter>(R.layout.fragment_video_move),
        IVideoMoveView, View.OnClickListener {

    private var mVideoDirList: RecyclerView? = null
    private var mTitleText: TextView? = null
    private var mOkayButton: View? = null

    private val mPresenter = IVideoMovePresenter.newInstance()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mPresenter.attachToView(this)
    }

    override fun onDetach() {
        super.onDetach()
        mPresenter.detachFromView(this)
    }

    override fun onScreenWidthDpLevelChanged(
            oldLevel: ScreenWidthDpLevel, level: ScreenWidthDpLevel) {
        val adapter = mVideoDirList?.adapter
        adapter?.notifyItemRangeChanged(0, adapter.itemCount, PAYLOAD_REFRESH_VIDEODIR_THUMB)
    }

    override fun onDialogCreated(dialog: Dialog) {
        mTitleText = dialog.findViewById(R.id.text_title)
        mTitleText!!.text =
                resources.getQuantityText(R.plurals.moveVideosTo, mPresenter.videoQuantity)

        val context: Context = contextThemedFirst
        mVideoDirList = dialog.findViewById<RecyclerView?>(R.id.recycler_videoMoveTargetList)
                .apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = mPresenter.newTargetDirListAdapter()
                    addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                    setHasFixedSize(true)
                }

        dialog.findViewById<View>(R.id.btn_cancel).setOnClickListener(this)
        mOkayButton = dialog.findViewById(R.id.btn_ok)
        mOkayButton!!.setOnClickListener(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        mPresenter.restoreData(savedInstanceState)
        // Selected dir might not be loaded till user scrolls the list
        for (index in 0 until mVideoDirList!!.adapter!!.itemCount) {
            if (mPresenter.isTargetDirChecked(index)) {
                mOkayButton!!.isEnabled = true
                break
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mPresenter.saveData(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mVideoDirList = null
        mTitleText = null
        mOkayButton = null
    }

    override fun onClick(v: View) {
        val context = v.context
        val appPrefs = AppPrefs.getSingleton(context)
        when (v.id) {
            R.id.btn_ok -> {
                if (!appPrefs.hasUserDeclinedVideoMovePromptDialogToBeShownAgain()) {
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
                } else {
                    startToMoveVideos(context)
                }
            }
            R.id.btn_cancel -> dismiss()

            R.id.btn_ok_vmpd -> {
                val promptDialog = v.tag as AppCompatDialog
                val checkbox = promptDialog.findViewById<CheckBox>(R.id.checkbox)
                if (checkbox!!.isChecked) {
                    appPrefs.edit()
                            .setUserDeclinedVideoMovePromptDialogToBeShownAgain(true)
                            .apply()
                }
                promptDialog.cancel()

                startToMoveVideos(context)
            }
            R.id.btn_cancel_vmpd -> {
                val promptDialog = v.tag as AppCompatDialog
                promptDialog.cancel()
            }
        }
    }

    private fun startToMoveVideos(context: Context) {
        val waitingDialog = WaitingOverlayDialog(context)
        waitingDialog.message =
                resources.getQuantityText(R.plurals.movingVideosPleaseWait, mPresenter.videoQuantity)
        waitingDialog.show()
        Executors.THREAD_POOL_EXECUTOR.execute {
            if (mPresenter.moveVideos()) {
                Executors.MAIN_EXECUTOR.execute {
                    targetFragment?.onActivityResult(
                            targetRequestCode, RESULT_CODE_VIDEO_MOVE_FRAGMENT, null)
                    dismiss()
                    waitingDialog.dismiss()
                }
            }
        }
    }

    override fun setTargetDirListItemChecked(position: Int, checked: Boolean) {
        mVideoDirList?.adapter?.notifyItemChanged(position, PAYLOAD_REFRESH_CHECKBOX)
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

                val videoCount = videodir.videos.size
                if (checkBox.isChecked != videodir.isChecked) {
                    checkBox.isChecked = videodir.isChecked
                    onCheckedChange(position)
                }
                videodirNameText.text = videodir.name
                videodirPathText.text = videodir.path
                videoCountText.text =
                        resources.getQuantityString(
                                R.plurals.aTotalOfSeveralVideos, videoCount, videoCount)
            } else {
                for (payload in payloads) {
                    when (payload) {
                        PAYLOAD_REFRESH_CHECKBOX ->
                            if (checkBox.isChecked != videodir.isChecked) {
                                checkBox.isChecked = videodir.isChecked
                                onCheckedChange(position)
                            }
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
            when (v.id) {
                R.id.checkbox -> {
                    val position = v.tag as Int
                    onCheckedChange(position)
                }
                else -> { // itemView
                    val position = v.tag as Int
                    checkBox.toggle(true)
                    onCheckedChange(position)
                }
            }
        }

        fun onCheckedChange(position: Int) {
            val itemCount = bindingAdapter!!.itemCount
            if (checkBox.isChecked) {
                for (index in 0 until itemCount) {
                    if (index != position && mPresenter.isTargetDirChecked(index)) {
                        mPresenter.setTargetDirChecked(index, false)
                        break
                    }
                }
                mOkayButton!!.isEnabled = true
            } else {
                var hasCheckedDir = false
                for (index in 0 until itemCount) {
                    if (index != position && mPresenter.isTargetDirChecked(index)) {
                        hasCheckedDir = true
                        break
                    }
                }
                if (!hasCheckedDir) {
                    mOkayButton!!.isEnabled = false
                }
            }
            mPresenter.setTargetDirChecked(position, checkBox.isChecked)
        }
    }
}