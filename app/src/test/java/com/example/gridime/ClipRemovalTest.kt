package com.example.gridime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Removing a clip has a way back: X removes, X on the "Removed" place brings it back, and it only goes for real later. */
class ClipRemovalTest {
    private val history = mutableListOf("a", "b", "c", "d")
    private val r = ClipRemoval { history.removeAt(it) }

    @Test fun xTwiceBringsItBack() {
        val first = r.pressX(1, 0)
        assertEquals(false, first.undone); assertEquals(1, r.pending); assertEquals(4, history.size)
        val again = r.pressX(1, 500)
        assertTrue(again.undone); assertEquals(null, r.pending); assertEquals(listOf("a", "b", "c", "d"), history)
    }

    @Test fun itWaitsWhileTheHighlightRestsOnIt() {
        r.pressX(2, 0)
        for (t in 0L..20_000L step 250L) r.tick(2, t)            // twenty seconds on the slot: still there
        assertEquals(2, r.pending); assertEquals(4, history.size)
        r.tick(0, 20_000)                                     // the highlight leaves...
        r.tick(0, 20_000 + ClipRemoval.GRACE_MS - 250)
        assertEquals(4, history.size)                         // ...not yet...
        r.tick(0, 20_000 + ClipRemoval.GRACE_MS)
        assertEquals(listOf("a", "b", "d"), history); assertEquals(null, r.pending)
    }

    @Test fun comingBackPausesTheClock() {
        r.pressX(0, 0)
        r.tick(1, 100); r.tick(1, 3000); r.tick(0, 3500)       // back on it before the time is up
        r.tick(1, 3600); r.tick(1, 3600 + ClipRemoval.GRACE_MS - 1)
        assertEquals(4, history.size)
    }

    @Test fun aSecondRemovalSettlesTheFirstAndKeepsPlacesRight() {
        r.pressX(0, 0)                                        // "a" pending
        val second = r.pressX(2, 100)                          // "c" (place 2 before "a" goes)
        assertEquals(listOf("b", "c", "d"), history)           // "a" went through
        assertEquals(1, second.slot); assertEquals(1, r.pending)  // "c" is now at place 1
        r.commit()
        assertEquals(listOf("b", "d"), history)
    }
}
