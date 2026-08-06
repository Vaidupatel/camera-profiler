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
    fun resolution_pass_landscape_atOrAboveMinimum() {
        val m1 = QualityMetricRules.imageResolution(1920, 1080, thresholds)
        assertThat(m1.status).isEqualTo(QualityMetricStatus.PASS)

        val m2 = QualityMetricRules.imageResolution(640, 480, thresholds)
        assertThat(m2.status).isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun resolution_pass_portrait_atOrAboveMinimum() {
        // 1080x1920 portrait frame from rotated sensor (equivalent to 1920x1080)
        val m1 = QualityMetricRules.imageResolution(1080, 1920, thresholds)
        assertThat(m1.status).isEqualTo(QualityMetricStatus.PASS)

        // 480x640 portrait frame from rotated sensor (equivalent to 640x480)
        val m2 = QualityMetricRules.imageResolution(480, 640, thresholds)
        assertThat(m2.status).isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun resolution_fail_landscape_belowMinimum() {
        val m1 = QualityMetricRules.imageResolution(320, 240, thresholds)
        assertThat(m1.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m1.reason).contains("below minimum requirement")

        val m2 = QualityMetricRules.imageResolution(600, 400, thresholds)
        assertThat(m2.status).isEqualTo(QualityMetricStatus.FAIL)
    }

    @Test
    fun resolution_fail_portrait_belowMinimum() {
        val m1 = QualityMetricRules.imageResolution(240, 320, thresholds)
        assertThat(m1.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m1.reason).contains("below minimum requirement")

        val m2 = QualityMetricRules.imageResolution(400, 600, thresholds)
        assertThat(m2.status).isEqualTo(QualityMetricStatus.FAIL)
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
