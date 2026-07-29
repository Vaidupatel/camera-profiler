package com.openprofiler

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [MainActivity].
 * Phase 0: Placeholder verifying application context.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun applicationContextUsesCorrectPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(appContext.packageName.startsWith("com.openprofiler"))
    }
}
