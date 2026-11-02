package com.sourabh.jarvistube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenVideosTest {
    @Test
    fun sameVideoHasOneIdInEveryLinkForm() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, videoIdOf("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, videoIdOf("https://www.youtube.com/shorts/$id"))
        assertEquals(id, videoIdOf("https://youtu.be/$id"))
    }

    @Test
    fun listNeverGrowsPastItsCapAndForgetsTheOldest() {
        val list = SeenList(3)
        listOf("a", "b", "c", "d").forEach { list.add(it) }
        assertEquals(3, list.size)
        assertFalse("a" in list)
        assertTrue("d" in list)
    }

    @Test
    fun listSurvivesSavingAndLoading() {
        val list = SeenList(10)
        list.add("a")
        list.add("b")
        val loaded = SeenList(10).apply { decode(list.encode()) }
        assertTrue("a" in loaded && "b" in loaded)
        assertEquals(2, loaded.size)
    }

    @Test
    fun addingTheSameIdTwiceChangesNothing() {
        val list = SeenList(10)
        assertTrue(list.add("a"))
        assertFalse(list.add("a"))
    }

    @Test
    fun seenItemsAreSkippedWhenThereAreEnoughNewOnes() {
        assertEquals(listOf(2, 4), freshFirst(listOf(1, 2, 3, 4), 2) { it % 2 == 1 })
    }

    @Test
    fun seenItemsFollowWhenNewOnesRunOut() {
        assertEquals(listOf(2, 1, 3), freshFirst(listOf(1, 2, 3), 2) { it % 2 == 1 })
    }
}
