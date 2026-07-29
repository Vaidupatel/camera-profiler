package com.openprofiler.common.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Abstraction over coroutine dispatchers for testability.
 * Inject this instead of using Dispatchers directly.
 */
interface DispatcherProvider {
    /** Main (UI) thread dispatcher. */
    val main: CoroutineDispatcher

    /** IO dispatcher for blocking operations. */
    val io: CoroutineDispatcher

    /** Default dispatcher for CPU-intensive work. */
    val default: CoroutineDispatcher

    /** Unconfined dispatcher. */
    val unconfined: CoroutineDispatcher
}

/**
 * Production implementation using standard Android dispatchers.
 */
class StandardDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
