/*
 * Created on 2022-2-22 7:08:19 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity

import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.textfield.TextInputEditText
import com.liuzhenlin.common.utils.URLUtils
import com.liuzhenlin.common.utils.UiUtils
import com.liuzhenlin.common.view.OnBackPressedPreImeEventInterceptableTextInputEditText
import com.liuzhenlin.common.windowhost.FocusObservableDialog
import com.liuzhenlin.videos.R
import com.liuzhenlin.videos.view.fragment.ILocalVideosFragment
import com.liuzhenlin.videos.view.fragment.playVideo
import com.liuzhenlin.videos.web.youtube.YoutubePlaybackService

open class MainActivityToolbarActions(private val mActivity: MainActivity) {

    init {
        mActivity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    mOpenVideoLinkDialog?.dismiss()
                }
            }
        })
    }

    private var mOpenVideoLinkDialog: Dialog? = null
    private val mOnClickListener = View.OnClickListener { v ->
        when (v.id) {
            R.id.btn_cancel_openVideoLinkDialog -> mOpenVideoLinkDialog!!.cancel()
            R.id.btn_ok_openVideoLinkDialog -> {
                val linkTiet = mOpenVideoLinkDialog!!.window!!.decorView.tag as TextInputEditText
                val link = linkTiet.text!!.trim().toString()
                if (link.isEmpty()) {
                    Toast.makeText(v.context,
                            R.string.pleaseInputVideoLinkFirst, Toast.LENGTH_SHORT).show()
                    return@OnClickListener
                }
                if (URLUtils.REGEX_WEB_URL.matches(link)) {
                    if (!YoutubePlaybackService.startPlaybackIfUrlIsWatchUrl(v.context, link)) {
                        v.context.playVideo(link,
                                (linkTiet.tag as TextInputEditText).text!!.trim().toString()
                                        .run {
                                            if (isEmpty()) null else this
                                        })
                    }
                } else {
                    Toast.makeText(v.context, R.string.illegalInputLink, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun showOpenVideoLinkDialog(context: Context) {
        val dialog = FocusObservableDialog(context, R.style.DialogStyle_MinWidth_NoTitle)
        dialog.setContentView(R.layout.dialog_open_video_link)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        val tiet_videoTitle =
            dialog.findViewById<OnBackPressedPreImeEventInterceptableTextInputEditText>(
                R.id.textinput_videoTitle)!!

        val tiet_videoLink = dialog.findViewById<
                OnBackPressedPreImeEventInterceptableTextInputEditText>(R.id.textinput_videoLink)!!
        tiet_videoLink.tag = tiet_videoTitle

        UiUtils.showSoftInputForEditingViewsAccordingly(dialog, tiet_videoTitle, tiet_videoLink)

        dialog.findViewById<View>(R.id.btn_cancel_openVideoLinkDialog)!!
                .setOnClickListener(mOnClickListener)
        dialog.findViewById<View>(R.id.btn_ok_openVideoLinkDialog)!!
                .setOnClickListener(mOnClickListener)
        dialog.window!!.decorView.tag = tiet_videoLink
        dialog.setOnDismissListener { mOpenVideoLinkDialog = null }
        mOpenVideoLinkDialog = dialog
    }

    fun goToLocalSearchedVideosFragment(fragment: ILocalVideosFragment) {
        fragment.goToLocalSearchedVideosFragment()
    }
}
