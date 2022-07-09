/*
 * Created on 2019/3/18 8:45 PM.
 * Copyright © 2019–2020 刘振林. All rights reserved.
 */
@file:JvmName("Consts")

package com.liuzhenlin.videos

import androidx.core.content.ContextCompat
import com.liuzhenlin.common.Consts

/**
 * @author 刘振林
 */

internal const val PROCESS_NAME_MAIN = Consts.APPLICATION_ID
internal const val PROCESS_NAME_WEB = Consts.APPLICATION_ID + ":web"

internal const val KEY_DIRECTORY_PATH = "directoryPath"
internal const val KEY_VIDEODIR = "videodir"
internal const val KEY_VIDEO = "video"
internal const val KEY_VIDEOS = "videos"
internal const val KEY_VIDEO_TITLE = "videoTitle"
internal const val KEY_VIDEO_TITLES = "videoTitles"
internal const val KEY_VIDEO_URIS = "videoURIs"
internal const val KEY_SELECTION = "index"

internal const val REQUEST_CODE_PLAY_VIDEO = 1
internal const val RESULT_CODE_PLAY_VIDEO = 1

internal const val REQUEST_CODE_PLAY_VIDEOS = 2
internal const val RESULT_CODE_PLAY_VIDEOS = 2

internal const val REQUEST_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT = 3
internal const val RESULT_CODE_LOCAL_SEARCHED_VIDEOS_FRAGMENT = 3

internal const val REQUEST_CODE_LOCAL_FOLDED_VIDEOS_FRAGMENT = 4
internal const val RESULT_CODE_LOCAL_FOLDED_VIDEOS_FRAGMENT = 4

internal const val REQUEST_CODE_GET_PICTURE = 5
internal const val RESULT_CODE_GET_PICTURE = 5

private val _COLOR_ACCENT by lazy(LazyThreadSafetyMode.NONE) {
    ContextCompat.getColor(App.getInstanceUnsafe()!!, R.color.colorAccent) }
@JvmField
internal val COLOR_ACCENT = _COLOR_ACCENT

private val _TEXT_COLOR_PRIMARY_LIGHT by lazy(LazyThreadSafetyMode.NONE) {
    ContextCompat.getColor(App.getInstanceUnsafe()!!, R.color.primary_text_default_material_dark) }
@JvmField
internal val TEXT_COLOR_PRIMARY_LIGHT = _TEXT_COLOR_PRIMARY_LIGHT

private val _TEXT_COLOR_PRIMARY_DARK by lazy(LazyThreadSafetyMode.NONE) {
    ContextCompat.getColor(App.getInstanceUnsafe()!!, R.color.primary_text_default_material_light) }
@JvmField
internal val TEXT_COLOR_PRIMARY_DARK = _TEXT_COLOR_PRIMARY_DARK