package com.openprofiler.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ObservedBoundingBoxTest {

    @Test
    fun fromCorners_buildsAxisAlignedBoxFromMinMax() {
        val corners = listOf(
            DetectedCorner(0, 100f, 200f),
            DetectedCorner(1, 400f, 150f),
            DetectedCorner(2, 380f, 500f),
            DetectedCorner(3, 120f, 480f),
        )
        val box = ObservedBoundingBox.fromCorners(corners)!!
        assertThat(box).hasSize(4)
        assertThat(box[0]).isEqualTo(Point2D(100f, 150f)) // TL
        assertThat(box[1]).isEqualTo(Point2D(400f, 150f)) // TR
        assertThat(box[2]).isEqualTo(Point2D(400f, 500f)) // BR
        assertThat(box[3]).isEqualTo(Point2D(100f, 500f)) // BL
    }

    @Test
    fun fromCorners_nullWhenInsufficientPoints() {
        assertThat(ObservedBoundingBox.fromCorners(emptyList())).isNull()
        assertThat(
            ObservedBoundingBox.fromCorners(listOf(DetectedCorner(0, 1f, 2f)))
        ).isNull()
    }
}
