package com.openprofiler.common.util

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [DispatcherProvider].
 */
class DispatcherProviderTest {

    @Test
    fun `standard dispatcher provider returns correct dispatchers`() {
        val provider = StandardDispatcherProvider()
        assertEquals(Dispatchers.IO, provider.io)
        assertEquals(Dispatchers.Default, provider.default)
        assertEquals(Dispatchers.Unconfined, provider.unconfined)
    }
}
