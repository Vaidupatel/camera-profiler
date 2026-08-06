package com.openprofiler.common.util

/**
 * Shared utilities for handling image dimensions with rotation.
 */
object ImageDimensionUtils {

    /**
     * Returns the width after applying the specified rotation.
     * 90 or 270 degrees swaps width and height.
     */
    fun rotatedWidth(width: Int, height: Int, rotationDegrees: Int): Int {
        return if (rotationDegrees == 90 || rotationDegrees == 270) height else width
    }

    /**
     * Returns the height after applying the specified rotation.
     * 90 or 270 degrees swaps width and height.
     */
    fun rotatedHeight(width: Int, height: Int, rotationDegrees: Int): Int {
        return if (rotationDegrees == 90 || rotationDegrees == 270) width else height
    }
}
