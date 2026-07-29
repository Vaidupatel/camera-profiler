package com.openprofiler.common.util

import timber.log.Timber

/**
 * Structured logger wrapping Timber for categorized logging.
 * Each module should use a specific tag for its log messages.
 */
object Logger {

    /** Log a debug message with the given tag. */
    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    /** Log an info message with the given tag. */
    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    /** Log a warning message with the given tag. */
    fun w(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }

    /** Log an error message with the given tag and optional throwable. */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).e(throwable, message)
    }
}
