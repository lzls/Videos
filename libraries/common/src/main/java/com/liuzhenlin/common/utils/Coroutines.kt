/*
 * Created on 2025-1-6 9:56:52 PM.
 * Copyright © 2025 刘振林. All rights reserved.
 */
@file:JvmName("Coroutines")

package com.liuzhenlin.common.utils

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Binder
import android.os.Process
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleCoroutineScopeImplAccessor
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelAccessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import kotlin.coroutines.CoroutineContext

private const val TAG = "Coroutines"

private const val KEY_VIEW_MODEL_SCOPE = "com.liuzhenlin.common.utils.Coroutines.viewModelScope"

object AppScope : CoroutineScope {
    override val coroutineContext: CoroutineContext =
            newCoroutineContext("AppScope", Dispatchers.CPU)
}

@Suppress("FunctionName")
fun ServiceScope() = CloseableCoroutineScope(newCoroutineContext("ServiceScope", Dispatchers.IO))

@Suppress("FunctionName")
fun ModelScope() = CloseableCoroutineScope(newCoroutineContext("ModelScope", Dispatchers.IO))

public val ViewModel.viewModelScope: CoroutineScope
    get() {
        val scope: CoroutineScope? = ViewModelAccessor.getTag(this, KEY_VIEW_MODEL_SCOPE)
        if (scope != null) {
            return scope
        }
        return ViewModelAccessor.setTagIfAbsent(this, KEY_VIEW_MODEL_SCOPE,
                CloseableCoroutineScope(newCoroutineContext("ViewModelScope", Dispatchers.CPU)))
    }

public inline val LifecycleOwner.lifecycleScope: LifecycleCoroutineScope
    get() = lifecycle.lifecycleScope

public val Lifecycle.lifecycleScope: LifecycleCoroutineScope
    @SuppressLint("RestrictedApi")
    get() {
        while (true) {
            val existing = internalScopeRef.get() as LifecycleCoroutineScope?
            if (existing != null) {
                return existing
            }
            val newScope =
                    LifecycleCoroutineScopeImplAccessor.newLifecycleCoroutineScopeImpl(
                            this, newCoroutineContext("LifecycleScope", Dispatchers.Main.immediate))
            if (internalScopeRef.compareAndSet(null, newScope)) {
                LifecycleCoroutineScopeImplAccessor.register(newScope)
                return newScope
            }
        }
    }

private fun newCoroutineContext(name: String, dispatcher: CoroutineDispatcher): CoroutineContext {
    return CoroutineName(name) + SupervisorJob() + dispatcher + loggingExceptionHandler
}

private val loggingExceptionHandler = CoroutineExceptionHandler { coroutineScope, throwable ->
    Log.w(TAG, coroutineScope[CoroutineName.Key]?.name, throwable)
}

public inline val Dispatchers.CPU get() = Dispatchers.Default

public val Dispatchers.Single by lazy { Executors.SERIAL_EXECUTOR.asCoroutineDispatcher() }

@JvmName("executeAsyncTask")
fun <Result> AsyncTask<*, *, Result>.executeOnCoroutine(
        scope: CoroutineScope, context: CoroutineContext, vararg params: Any)
: Deferred<Result?> {
    val status = status
    if (status != AsyncTask.Status.PENDING) {
        when (status) {
            AsyncTask.Status.RUNNING ->
                throw IllegalStateException("Cannot execute task: the task is already running.")
            AsyncTask.Status.FINISHED ->
                throw IllegalStateException(
                        "Cannot execute task: the task has already been executed " +
                                "(a task can be executed only once)")
            else -> {}
        }
    }
    statusField?.set(this, AsyncTask.Status.RUNNING)

    onPreExecuteMethod?.invoke(this)

    @Suppress("UNCHECKED_CAST")
    val futureTask: FutureTask<Result?>? = futureField?.get(this) as FutureTask<Result?>?
    if (futureTask != null && params.isNotEmpty()) {
        val worker = workerField?.get(this)
        if (worker == null || workerParamsField?.set(worker, params) == null) {
            val workerRunnable = object : WorkerRunnable<Any, Result>() {
                override fun call(): Result? {
                    taskInvokedField?.set(this@executeOnCoroutine, true)
                    var result: Result? = null
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                        @Suppress("UNCHECKED_CAST")
                        result = doInBackgroundMethod?.invoke(this@executeOnCoroutine, mParams)
                                as Result?
                        Binder.flushPendingCommands()
                    } catch (tr: Throwable) {
                        cancel(false)
                        throw tr
                    } finally {
                        postResult(result)
                    }
                    return result
                }
            }
            workerRunnable.mParams = arrayOf(params)
            futureTask.callableField?.set(futureTask, workerRunnable)
        }
    }
    return scope.async(context) {
        if (futureTask != null) {
            futureTask.run()
            return@async futureTask.get()
        }
        return@async null
    }
}

private val AsyncTask<*, *, *>.statusField by lazy {
    ReflectionUtils.getDeclaredField(AsyncTask::class.java, "mStatus")
}
private val AsyncTask<*, *, *>.taskInvokedField by lazy {
    ReflectionUtils.getDeclaredField(AsyncTask::class.java, "mTaskInvoked")
}
private val AsyncTask<*, *, *>.workerField by lazy {
    ReflectionUtils.getDeclaredField(AsyncTask::class.java, "mWorker")
}
private val AsyncTask<*, *, *>.workerParamsField by lazy {
    val workerRunnableClass = ReflectionUtils.getDeclaredClass(AsyncTask::class.java,
            "android.os.AsyncTask\$WorkerRunnable")
    if (workerRunnableClass != null) {
        return@lazy ReflectionUtils.getDeclaredField(workerRunnableClass, "mParams")
    }
    return@lazy null
}
private val AsyncTask<*, *, *>.futureField by lazy {
    ReflectionUtils.getDeclaredField(AsyncTask::class.java, "mFuture")
}
private val FutureTask<*>.callableField by lazy {
    ReflectionUtils.getDeclaredField(FutureTask::class.java, "callable")
}
private val AsyncTask<*, *, *>.onPreExecuteMethod by lazy {
    ReflectionUtils.getDeclaredMethod(AsyncTask::class.java, "onPreExecute")
}
private val AsyncTask<*, *, *>.doInBackgroundMethod by lazy {
    ReflectionUtils.getDeclaredMethod(AsyncTask::class.java, "doInBackground",
            Array<Any>::class.java)
}
private val AsyncTask<*, *, *>.onCancelledMethod by lazy {
    ReflectionUtils.getDeclaredMethod(AsyncTask::class.java, "onCancelled", Any::class.java)
}
private val AsyncTask<*, *, *>.onPostExecuteMethod by lazy {
    ReflectionUtils.getDeclaredMethod(AsyncTask::class.java, "onPostExecute", Any::class.java)
}

private fun <Result> AsyncTask<*, *, Result>.postResult(result: Result?) {
    AppScope.launch(Dispatchers.Main) {
        if (isCancelled) {
            onCancelledMethod?.invoke(this@postResult, result)
        } else {
            onPostExecuteMethod?.invoke(this@postResult, result)
        }
        statusField?.set(this@postResult, AsyncTask.Status.FINISHED)
    }
}

private abstract class WorkerRunnable<Params, Result> : Callable<Result> {
    var mParams: Array<Params>? = null
}