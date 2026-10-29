package com.example.gridime

/**
 * Removing a clip from the clipboard history, with a way back (Subframe Motion's "undo is safe" habit).
 *
 * X on a clip doesn't delete it straight away: the clip becomes a "Removed" slot in the same place, and X on that
 * slot brings it back. The removal is made for real ([commit]) once the highlight has been away from the slot for
 * [GRACE_MS], when the clipboard closes, or when another clip is removed or a new one is copied (so places never
 * shift under a pending removal). While the highlight rests on the slot, the clock waits.
 *
 * Pure logic, so ClipRemovalTest can walk through it; the keyboard service wires it to the history and the view.
 */
class ClipRemoval(private val removeFromHistory: (Int) -> Unit) {
    /** The clip waiting to be removed (its place in the list), or null. */
    var pending: Int? = null
        private set
    private var awaySince = -1L

    /**
     * X on clip [index] at [now]. Returns what happened: the slot came back (undo), or a clip is now pending,
     * and where it sits (an earlier pending removal goes through first and may move it up one place).
     */
    fun pressX(index: Int, now: Long): Result {
        val p = pending
        if (p == index) { pending = null; awaySince = -1L; return Result(undone = true, slot = index) }
        var at = index
        if (p != null) { commit(); if (p < index) at = index - 1 }
        pending = at; awaySince = -1L
        return Result(undone = false, slot = at)
    }

    /** Called now and then with the clip the highlight is on (null when it's elsewhere). True when it committed. */
    fun tick(focused: Int?, now: Long): Boolean {
        val p = pending ?: return false
        if (focused == p) { awaySince = -1L; return false }
        if (awaySince < 0) { awaySince = now; return false }
        if (now - awaySince < GRACE_MS) return false
        commit(); return true
    }

    /** The removal goes through now (clipboard closed, a new copy, another removal). */
    fun commit() {
        val p = pending ?: return
        pending = null; awaySince = -1L
        removeFromHistory(p)
    }

    class Result(val undone: Boolean, val slot: Int)

    companion object {
        /** How long a "Removed" slot stays once the highlight has left it. */
        const val GRACE_MS = 4000L
        /** How often the keyboard checks. */
        const val TICK_MS = 250L
    }
}
