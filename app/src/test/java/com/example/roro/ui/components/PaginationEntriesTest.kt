package com.example.roro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationEntriesTest {
    @Test
    fun oneAndSmallPageCountsShowEveryPage() {
        assertEquals(listOf(0), paginationEntries(0, 1))
        assertEquals(listOf(0, 1, 2, 3, 4), paginationEntries(2, 5))
    }

    @Test
    fun largePageCountsKeepEndsAndCurrentWithGaps() {
        assertEquals(listOf(0, 1, null, 11), paginationEntries(0, 12))
        assertEquals(listOf(0, 1, 2, null, 11), paginationEntries(2, 12))
        assertEquals(listOf(0, null, 5, null, 11), paginationEntries(5, 12))
        assertEquals(listOf(0, null, 10, 11), paginationEntries(11, 12))
    }

    @Test
    fun everyPageRemainsSelectableAndTheListFits() {
        for (count in 1..100) for (current in 0 until count) {
            val entries = paginationEntries(current, count)
            assertTrue(entries.contains(current))
            assertEquals(0, entries.first())
            assertEquals(count - 1, entries.last())
            assertTrue(entries.size <= 5)
        }
    }
}
