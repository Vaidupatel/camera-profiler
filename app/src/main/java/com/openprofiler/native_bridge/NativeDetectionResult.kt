package com.openprofiler.native_bridge

/**
 * Low-level JNI data transfer structure populated directly by C++ native engine.
 */
data class NativeDetectionResult(
    val boardDetected: Boolean,
    val dictionaryName: String,
    val markerCount: Int,
    val charucoCornerCount: Int,
    val markerIds: IntArray,
    val charucoIds: IntArray,
    val cornerX: FloatArray,
    val cornerY: FloatArray,
    val cornerPrecision: FloatArray,
    val rvec: DoubleArray?,
    val tvec: DoubleArray?,
    val detectionConfidence: Float,
    val processingTimeMs: Long,
    val rejectedReason: String?,
    val markerOutlineCoords: FloatArray?,
    val boardAxesCoords: FloatArray?,
    val boundingBoxCoords: FloatArray?,
    val stageTimingsMs: LongArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeDetectionResult

        if (boardDetected != other.boardDetected) return false
        if (dictionaryName != other.dictionaryName) return false
        if (markerCount != other.markerCount) return false
        if (charucoCornerCount != other.charucoCornerCount) return false
        if (!markerIds.contentEquals(other.markerIds)) return false
        if (!charucoIds.contentEquals(other.charucoIds)) return false
        if (!cornerX.contentEquals(other.cornerX)) return false
        if (!cornerY.contentEquals(other.cornerY)) return false
        if (!cornerPrecision.contentEquals(other.cornerPrecision)) return false
        if (rvec != null) {
            if (other.rvec == null) return false
            if (!rvec.contentEquals(other.rvec)) return false
        } else if (other.rvec != null) return false
        if (tvec != null) {
            if (other.tvec == null) return false
            if (!tvec.contentEquals(other.tvec)) return false
        } else if (other.tvec != null) return false
        if (detectionConfidence != other.detectionConfidence) return false
        if (processingTimeMs != other.processingTimeMs) return false
        if (rejectedReason != other.rejectedReason) return false

        return true
    }

    override fun hashCode(): Int {
        var result = boardDetected.hashCode()
        result = 31 * result + dictionaryName.hashCode()
        result = 31 * result + markerCount
        result = 31 * result + charucoCornerCount
        result = 31 * result + markerIds.contentHashCode()
        result = 31 * result + charucoIds.contentHashCode()
        result = 31 * result + cornerX.contentHashCode()
        result = 31 * result + cornerY.contentHashCode()
        result = 31 * result + cornerPrecision.contentHashCode()
        result = 31 * result + (rvec?.contentHashCode() ?: 0)
        result = 31 * result + (tvec?.contentHashCode() ?: 0)
        result = 31 * result + detectionConfidence.hashCode()
        result = 31 * result + processingTimeMs.hashCode()
        result = 31 * result + (rejectedReason?.hashCode() ?: 0)
        return result
    }
}
