/*
 * Created on 2025-3-17 7:48 PM.
 * Copyright © 2025 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils

import androidx.collection.SimpleArrayMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A special Coroutine launcher that tracks previous active jobs and can automatically cancel
 * the replaceable jobs when launching such a new.
 */
class ReplacingCoroutineLauncher(private val mCoroutineScope: CoroutineScope) {

    @Volatile
    private var mCanceled: Boolean = false

    private val mJobs: SimpleArrayMap<Job, Boolean> = SimpleArrayMap()

    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        replaceable: Boolean = true,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return if (replaceable) {
            synchronized(mJobs) {
                for (i in mJobs.size() - 1 downTo 0) {
                    if (mJobs.valueAt(i)) {
                        mJobs.keyAt(i).cancel()
                        mJobs.removeAt(i)
                    }
                }
                launchIfNotCanceled(context, start, true, block)
            }
        } else {
            launchIfNotCanceled(context, start, false, block)
        }.also { job ->
            job.invokeOnCompletion {
                synchronized(mJobs) { mJobs.remove(job) }
            }
        }
    }

    private fun launchIfNotCanceled(
        context: CoroutineContext,
        start: CoroutineStart,
        replaceable: Boolean,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return mCoroutineScope.launch(context, start, block)
                .also {
                    synchronized(mJobs) {
                        if (!mCanceled) {
                            mJobs.put(it, replaceable)
                        }
                    }
                    if (mCanceled) {
                        it.cancel()
                    }
                }
    }

    fun cancel(job: Job? = null) {
        if (job == null) {
            mCanceled = true
            synchronized(mJobs) {
                for (i in 0 until mJobs.size()) {
                    mJobs.keyAt(i).cancel()
                }
                mJobs.clear()
            }
        } else {
            job.cancel()
            synchronized(mJobs) { mJobs.remove(job) }
        }
    }
}