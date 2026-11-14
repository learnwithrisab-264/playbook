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

class ZoomSnapTest {
    @Test
    fun fillCropsTheBars() {
        // A 16:9 video on a 20:9 screen grows by 20/16 to fill; an upright 9:16 one by much more.
        assertEquals(1.25f, fillScale(2000f, 900f, 16f / 9f), 0.001f)
        assertEquals(1f, fillScale(1600f, 900f, 16f / 9f), 0.001f)
        assertEquals((20f / 9f) / (9f / 16f), fillScale(2000f, 900f, 9f / 16f), 0.01f)
    }

    @Test
    fun pinchSettlesLikeYouTube() {
        assertEquals(1f, snapZoom(1.1f, 1.25f), 0f)       // small pinch: Original
        assertEquals(1.25f, snapZoom(1.2f, 1.25f), 0f)    // near fill: Zoomed to fill
        assertEquals(1.25f, snapZoom(1.4f, 1.25f), 0f)
        assertEquals(2.2f, snapZoom(2.2f, 1.25f), 0f)     // deeper: free zoom stays
    }
}
