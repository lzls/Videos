/*
 * Created on 2025-1-6 9:04:11 PM.
 * Copyright © 2025 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

class CloseableCoroutineScope(context: CoroutineContext) : Closeable, CoroutineScope {

    override val coroutineContext: CoroutineContext = context

    override fun close() {
        coroutineContext.cancel()
    }
}