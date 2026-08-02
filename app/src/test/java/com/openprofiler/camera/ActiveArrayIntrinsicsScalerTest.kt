package com.openprofiler.camera

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActiveArrayIntrinsicsScalerTest {

    @Test
    fun centerCrop_sameAspect_usesFullActiveArray() {
        val crop = ActiveArrayIntrinsicsScaler.centerCropWindow(4000.0, 3000.0, 800, 600)
        assertThat(crop.cropWidth).isWithin(1e-9).of(4000.0)
        assertThat(crop.cropHeight).isWithin(1e-9).of(3000.0)
        assertThat(crop.offsetX).isWithin(1e-9).of(0.0)
        assertThat(crop.offsetY).isWithin(1e-9).of(0.0)
    }

    @Test
    fun centerCrop_widerOutput_cropsTopBottom() {
        val crop = ActiveArrayIntrinsicsScaler.centerCropWindow(4000.0, 3000.0, 1920, 1080)
        assertThat(crop.cropWidth).isWithin(1e-6).of(4000.0)
        assertThat(crop.cropHeight).isWithin(1e-6).of(2250.0)
        assertThat(crop.offsetY).isWithin(1e-6).of(375.0)
    }

    @Test
    fun scaleK_isIsotropicForMismatchedAspect() {
        val K = ActiveArrayIntrinsicsScaler.scaleKToImage(
            fxActive = 3000.0,
            fyActive = 3000.0,
            cxActive = 2000.0,
            cyActive = 1500.0,
            activeWidth = 4000.0,
            activeHeight = 3000.0,
            imageWidth = 640,
            imageHeight = 480,
        )
        // 4:3 → 4:3
        assertThat(K[0]).isWithin(1e-6).of(480.0)
        assertThat(K[4]).isWithin(1e-6).of(480.0)
    }
}
