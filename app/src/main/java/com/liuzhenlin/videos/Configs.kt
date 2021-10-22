/*
 * Created on 2021-10-22 10:13:39 AM.
 * Copyright © 2021 刘振林. All rights reserved.
 */
@file:JvmName("Configs")

package com.liuzhenlin.videos

/**
 * @author 刘振林
 */

@Suppress("SimplifyBooleanWithConstants")
@JvmField
internal val DEBUG_APP_UPDATE = BuildConfig.DEBUG && false

internal const val TOLERANCE_VIDEO_DURATION = 100 // ms

internal const val DELAY_SEND_NOTIFICATION_WITH_JUST_STOPPED_FOREGROUND_SERVICE_ID = 200 //ms