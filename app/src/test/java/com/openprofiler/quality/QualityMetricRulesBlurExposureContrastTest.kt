package com.openprofiler.quality

import com.google.common.truth.Truth.assertThat
import com.openprofiler.domain.model.QualityMetricStatus
import com.openprofiler.domain.model.QualityThresholds
import org.junit.Test

class QualityMetricRulesBlurExposureContrastTest {

    private val thresholds = QualityThresholds(
        minBlurLaplacianVariance = 100.0,
        warningBlurLaplacianVariance = 150.0,
        minMeanBrightness = 40.0,
        maxMeanBrightness = 220.0,
        warningMeanBrightnessLow = 60.0,
        warningMeanBrightnessHigh = 200.0,
        maxDarkPixelRatio = 0.35,
        maxBrightPixelRatio = 0.35,
        minContrastScore = 0.35,
        warningContrastScore = 0.50
    )

    @Test
    fun blur_fail_whenBelowMinimum() {
        val m = QualityMetricRules.blur(50.0, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m.value).isEqualTo(50.0)
        assertThat(m.threshold).isEqualTo(100.0)
        assertThat(m.reason).contains("below minimum")
    }

    @Test
    fun blur_warning_whenBetweenMinAndWarning() {
        val m = QualityMetricRules.blur(120.0, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.WARNING)
    }

    @Test
    fun blur_pass_whenAboveWarning() {
        val m = QualityMetricRules.blur(200.0, thresholds)
        assertThat(m.status).isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun exposure_fail_whenUnderexposedByMean() {
        val m = QualityMetricRules.exposure(
            meanBrightness = 20.0,
            darkRatio = 0.1,
            brightRatio = 0.0,
            t = thresholds
        )
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m.reason).contains("Underexposed")
        assertThat(m.secondaryValues["underexposed"]).isEqualTo(1.0)
    }

    @Test
    fun exposure_fail_whenOverexposedByBrightRatio() {
        val m = QualityMetricRules.exposure(
            meanBrightness = 180.0,
            darkRatio = 0.0,
            brightRatio = 0.50,
            t = thresholds
        )
        assertThat(m.status).isEqualTo(QualityMetricStatus.FAIL)
        assertThat(m.reason).contains("Overexposed")
    }

    @Test
    fun exposure_pass_inNominalRange() {
        val m = QualityMetricRules.exposure(
            meanBrightness = 120.0,
            darkRatio = 0.05,
            brightRatio = 0.05,
            t = thresholds
        )
        assertThat(m.status).isEqualTo(QualityMetricStatus.PASS)
    }

    @Test
    fun contrast_fail_warning_pass() {
        assertThat(QualityMetricRules.contrast(0.20, thresholds).status)
            .isEqualTo(QualityMetricStatus.FAIL)
        assertThat(QualityMetricRules.contrast(0.40, thresholds).status)
            .isEqualTo(QualityMetricStatus.WARNING)
        assertThat(QualityMetricRules.contrast(0.70, thresholds).status)
            .isEqualTo(QualityMetricStatus.PASS)
    }
}
