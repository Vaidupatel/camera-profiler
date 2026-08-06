package com.openprofiler.domain.model

/**
 * Deterministic real-time guidance instructions for optimal calibration frame capture.
 */
enum class CoverageGuidance(val text: String) {
    MOVE_LEFT("Move Left"),
    MOVE_RIGHT("Move Right"),
    MOVE_UP("Move Up"),
    MOVE_DOWN("Move Down"),
    MOVE_CLOSER("Move Closer"),
    MOVE_FARTHER("Move Farther"),
    ROTATE_LEFT("Rotate Left"),
    ROTATE_RIGHT("Rotate Right"),
    TILT_UP("Tilt Up"),
    TILT_DOWN("Tilt Down"),
    EXCELLENT("Excellent")
}
