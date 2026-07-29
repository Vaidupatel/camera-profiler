package com.openprofiler.domain.model

/**
 * Sealed class representing errors that can occur during profiling.
 * Every error contains a code, message, and recovery suggestion per ARCHITECTURE.md.
 *
 * @property code Unique error code.
 * @property message Human-readable error description.
 * @property recovery Suggested recovery action for the user.
 */
sealed class ProfileError(
    open val code: String,
    open val message: String,
    open val recovery: String,
) {
    /** Camera-related errors. */
    data class CameraError(
        override val code: String,
        override val message: String,
        override val recovery: String,
        val cause: Throwable? = null,
    ) : ProfileError(code, message, recovery)

    /** Calibration-related errors. */
    data class CalibrationError(
        override val code: String,
        override val message: String,
        override val recovery: String,
    ) : ProfileError(code, message, recovery)

    /** Validation-related errors. */
    data class ValidationError(
        override val code: String,
        override val message: String,
        override val recovery: String,
    ) : ProfileError(code, message, recovery)

    /** Export-related errors. */
    data class ExportError(
        override val code: String,
        override val message: String,
        override val recovery: String,
    ) : ProfileError(code, message, recovery)

    /** Native/JNI-related errors. */
    data class NativeError(
        override val code: String,
        override val message: String,
        override val recovery: String,
    ) : ProfileError(code, message, recovery)
}
