package com.amaankhan.toolkit.documents

import org.junit.Assert.*
import org.junit.Test
class PageSelectionTest {
    @Test fun selectionIsSortedDistinctWhileOrderingPreservesDuplicates() {
        assertEquals(listOf(0, 2, 3, 4), PageSelection.parse("5, 1, 3-5", 6))
        assertEquals(listOf(4, 0, 0, 1), PageSelection.parse("5, 1, 1-2", 6, true))
        assertEquals(listOf(0, 1, 2), PageSelection.parse("1–3", 6))
        assertEquals(listOf(0, 1, 2), PageSelection.parse("1—3", 6))
    }
    @Test fun invalidAndOutOfRangeSelectionsNeverSilentlyBecomeAllPages() {
        listOf("", "1,", "0", "2-1", "7", "-1", "1--3", "x", "999999999999").forEach {
            try { PageSelection.parse(it, 6); fail("Accepted invalid selection: $it") } catch (_: IllegalArgumentException) { }
        }
    }
}
