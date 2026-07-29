package com.openprofiler.native_bridge

/**
 * JNI bridge to the native OpenCV-based engine.
 * Phase 0: Placeholder with library loading and version check.
 */
class NativeBridge {

    companion object {
        init {
            System.loadLibrary("camera_profiler_native")
        }
    }

    /**
     * Returns the native library version string.
     * Used to verify JNI bridge is operational.
     */
    external fun getNativeVersion(): String
}
