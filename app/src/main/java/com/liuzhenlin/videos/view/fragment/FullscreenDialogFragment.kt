/*
 * Created on 2020-10-12 9:30:47 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */
package com.liuzhenlin.videos.view.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.liuzhenlin.common.Configs.ScreenWidthDpLevel
import com.liuzhenlin.common.utils.SystemBarUtils
import com.liuzhenlin.common.utils.ThemeUtils
import com.liuzhenlin.common.utils.UiUtils
import com.liuzhenlin.videos.R
import com.liuzhenlin.videos.contextThemedFirst

/**
 * @author 刘振林
 */
abstract class FullscreenDialogFragment(@LayoutRes protected val contentLayoutId: Int)
    : AppCompatDialogFragment() {

    private var mLifecycleCallback: FragmentPartLifecycleCallback? = null

    private var mScreenWidthDp = 0

    private var mTmpBundle: Bundle? = null

    init {
        @Suppress("LeakingThis")
        setStyle(STYLE_NORMAL, R.style.Theme_AppCompat_DayNight_Dialog_Fullscreen)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val parent = parentFragment
        if (parent is FragmentPartLifecycleCallback) {
            mLifecycleCallback = parent
        } else if (context is FragmentPartLifecycleCallback) {
            mLifecycleCallback = context
        }
        mLifecycleCallback?.onFragmentAttached(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mScreenWidthDp = resources.configuration.screenWidthDp
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val screenWidthDp = mScreenWidthDp
        if (screenWidthDp != 0) {
            mScreenWidthDp = newConfig.screenWidthDp
            val oldLevel = ScreenWidthDpLevel.of(screenWidthDp)
            val level = ScreenWidthDpLevel.of(newConfig.screenWidthDp)
            if (level !== oldLevel) {
                onScreenWidthDpLevelChanged(oldLevel, level)
            }
        }
    }

    protected open fun onScreenWidthDpLevelChanged(
            oldLevel: ScreenWidthDpLevel, level: ScreenWidthDpLevel) {
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context: Context = contextThemedFirst
        val dialog: Dialog = AppCompatDialog(context, theme)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(true)
        mTmpBundle = savedInstanceState
        return dialog
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val context: Context = contextThemedFirst
        val contentParent =
                dialog.window!!.decorView.findViewById<ViewGroup>(Window.ID_ANDROID_CONTENT)
        setupContentView(
                dialog, LayoutInflater.from(context).inflate(contentLayoutId, contentParent, false))
        onDialogCreated(dialog, mTmpBundle)
        mTmpBundle = null
        mLifecycleCallback?.onFragmentViewCreated(this)
    }

    protected open fun setupContentView(dialog: Dialog, contentView: View) {
        val context: Context = contextThemedFirst
        val window = dialog.window!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    && SystemBarUtils.setLightStatusCompat(window, isStatusBarBackgroundLight)) {
                SystemBarUtils.setTransparentStatus(window)
            } else {
                SystemBarUtils.setTranslucentStatus(window, true)
            }
            UiUtils.insertTopPaddingToActionBarIfLayoutUnderStatus(
                    contentView.findViewById(actionbarId))
        }
        window.setContentView(contentView)
        window.setBackgroundDrawableResource(
                ThemeUtils.getThemeAttrRes(context, android.R.attr.windowBackground))
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT)
    }

    @get:IdRes
    protected open val actionbarId: Int = R.id.actionbar

    protected open val isStatusBarBackgroundLight: Boolean
            get() = !ThemeUtils.isNightMode(contextThemedFirst)

    protected open fun onDialogCreated(dialog: Dialog, savedInstanceState: Bundle?) {}

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        onRestoreInstanceState(savedInstanceState)
    }

    protected open fun onRestoreInstanceState(savedInstanceState: Bundle?) {}

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mLifecycleCallback?.onFragmentViewDestroyed(this)
    }

    override fun onDetach() {
        super.onDetach()
        mLifecycleCallback?.onFragmentDetached(this)
    }
}