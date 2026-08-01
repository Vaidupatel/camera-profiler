package com.openprofiler.domain.repository

/**
 * Opaque ownership token for a single camera session on the shared [CameraRepository].
 *
 * Issued by [CameraRepository.startCamera]. Callers must pass the same token to
 * [CameraRepository.stopCamera]; a stale token is ignored so a disposed screen cannot
 * tear down a newer session that has already taken ownership.
 */
@JvmInline
value class CameraSessionToken(val id: Long) {
    companion object {
        /** Sentinel meaning "no session". Never matches an issued token (ids start at 1). */
        val None: CameraSessionToken = CameraSessionToken(0L)
    }

    val isNone: Boolean get() = id == 0L
}
