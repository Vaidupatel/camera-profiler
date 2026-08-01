package com.openprofiler.quality

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityThresholds
import org.junit.Test

class QualityMetricRulesResolutionCoverageTest {

    private val thresholds = QualityThresholds(
        minWidthPx = 640,
        minHeightPx = 480,
        supportedAspectRatios = listOf(4.0 / 3.0, 16.0 / 9.0),
        aspectRatioTolerance = 0.08,
        minBoardWidthPercent = 15.0,
        minBoardHeightPercent = 15.0,
        minImageCoveragePercent = 5.0,
        warningImageCoveragePercent = 10.0
    )

    @Test
    fun resolution_pass_atOrAboveMinimum() {
        val m = QualityMetricRules.imageResolution(1920, 1080, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun resolution_fail_belowMinimum() {
        val m = QualityMetricRules.imageResolution(320, 240, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m.reason).contains("below minimum")
    }

    @Test
    fun aspectRatio_pass_for16x9() {
        val m = QualityMetricRules.aspectRatio(1920, 1080, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun aspectRatio_fail_forUnsupported() {
        val m = QualityMetricRules.aspectRatio(1000, 700, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
    }

    @Test
    fun coverage_fail_whenMissingBoundingBox() {
        val m = QualityMetricRules.targetCoverage(null, null, null, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m.reason).contains("unavailable")
    }

    @Test
    fun coverage_pass_whenAdequate() {
        val m = QualityMetricRules.targetCoverage(40.0, 40.0, 16.0, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun coverage_fail_whenTooSmall() {
        val m = QualityMetricRules.targetCoverage(10.0, 10.0, 1.0, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
    }
}
