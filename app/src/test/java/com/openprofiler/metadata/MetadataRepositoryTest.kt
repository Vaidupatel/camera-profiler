package com.openprofiler.metadata

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying [MetadataRepositoryImpl] safety and data model extraction.
 */
class MetadataRepositoryTest {

    private lateinit var context: Context
    private lateinit var cameraManager: CameraManager
    private lateinit var characteristics: CameraCharacteristics
    private lateinit var repository: MetadataRepositoryImpl

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        cameraManager = mockk(relaxed = true)
        characteristics = mockk(relaxed = true)

        every { context.getSystemService(Context.CAMERA_SERVICE) } returns cameraManager
        every { cameraManager.cameraIdList } returns arrayOf("0", "1")
        every { cameraManager.getCameraCharacteristics(any()) } returns characteristics

        // Mock CameraCharacteristics.get to return null for all keys safely
        every { characteristics.get(any<CameraCharacteristics.Key<Any>>()) } returns null

        repository = MetadataRepositoryImpl(context)
    }

    @Test
    fun `getAvailableCameraIds returns list of camera IDs`() {
        val ids = repository.getAvailableCameraIds()
        assertEquals(2, ids.size)
        assertEquals("0", ids[0])
        assertEquals("1", ids[1])
    }

    @Test
    fun `collectMetadata populates non-null metadata object safely`() {
        val metadata = repository.collectMetadata("0")
        assertNotNull(metadata)
        assertNotNull(metadata.device)
        assertNotNull(metadata.operatingSystem)
        assertNotNull(metadata.application)
        assertNotNull(metadata.camera)
        assertNotNull(metadata.sensor)
        assertNotNull(metadata.cameraCharacteristics)
    }
}
