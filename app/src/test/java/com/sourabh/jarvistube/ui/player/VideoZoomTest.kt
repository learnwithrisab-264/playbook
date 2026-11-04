package com.sourabh.jarvistube.ui.player

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoZoomTest {
    @Test
    fun zoomStaysBetweenOneAndThree() {
        assertEquals(1f, clampZoom(0.5f), 0f)
        assertEquals(2f, clampZoom(2f), 0f)
        assertEquals(3f, clampZoom(5f), 0f)
    }

    @Test
    fun noPanningWithoutZoom() {
        val p = clampPan(Offset(300f, -200f), 1f, 1000f, 500f)
        assertEquals(0f, p.x, 0.001f)
        assertEquals(0f, p.y, 0.001f)
    }

    @Test
    fun panStopsAtTheEdgesOfTheZoomedPicture() {
        // At 2x a 1000 x 500 picture can move 500 sideways and 250 up or down.
        assertEquals(Offset(500f, -250f), clampPan(Offset(900f, -900f), 2f, 1000f, 500f))
        assertEquals(Offset(100f, 50f), clampPan(Offset(100f, 50f), 2f, 1000f, 500f))
    }
}
