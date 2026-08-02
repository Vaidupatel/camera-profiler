package com.openprofiler.camera

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.openprofiler.CameraNavTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Verifies Camera→Calibration navigation reuses the CameraX use-case graph when both
 * screens bind to the Activity lifecycle (not per-destination NavBackStackEntry).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CameraRebindNavigationInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val resetBinderCounts = object : ExternalResource() {
        override fun before() {
            CountingCameraUseCaseBinder.reset()
        }
    }

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<CameraNavTestActivity>()

    @Test
    fun navigatingCameraToCalibration_bindsUseCasesOnlyOnce() {
        hiltRule.inject()

        // Wait for first Preview surface → startCamera → bind.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            CountingCameraUseCaseBinder.bindCount.get() >= 1
        }
        val bindsAfterCamera = CountingCameraUseCaseBinder.bindCount.get()
        assertThat(bindsAfterCamera).isEqualTo(1)
        assertThat(CountingCameraUseCaseBinder.lastLifecycleOwnerName)
            .isEqualTo(CameraNavTestActivity::class.java.name)

        composeRule.onNodeWithText("Start Calibration Session").performClick()

        composeRule.onNodeWithText("Calibrating...", substring = true).assertIsDisplayed()
        composeRule.waitForIdle()

        // Allow Calibration Preview SurfaceProvider callback to run.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            // Either still 1 (reuse) or briefly wait for surface ready without extra binds.
            true
        }
        Thread.sleep(1500)
        composeRule.waitForIdle()

        assertThat(CountingCameraUseCaseBinder.bindCount.get())
            .isEqualTo(bindsAfterCamera)
        assertThat(CountingCameraUseCaseBinder.lastLifecycleOwnerName)
            .isEqualTo(CameraNavTestActivity::class.java.name)
    }
}
