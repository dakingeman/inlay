package com.example.gridime

import android.os.Handler
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns an analog stick into one clean direction, the way console menus do:
 * - a light push to start (ENGAGE), a near-centre return to stop (RELEASE)
 * - the push picks whichever way it leans more, so there are no dead diagonals
 * - while held, it only turns on a clear push the other way (TURN), so it never wobbles
 * - the small spring-back the other way when you let go is ignored, but a real,
 *   firm flick back the other way still counts, so quick left-right taps all land
 * [strength] says how far the stick is pushed (0 at the start point, 1 at the edge),
 * so held repeats can go faster the further you push.
 */
class StickDirection {
    var dx = 0
        private set
    var dy = 0
        private set
    var strength = 0f
        private set

    private var lastDx = 0
    private var lastDy = 0
    private var releasedAt = 0L

    /** Feeds one stick sample. Returns true when the direction changed. */
    fun update(x: Float, y: Float, now: Long): Boolean {
        val ax = abs(x)
        val ay = abs(y)
        val oldDx = dx
        val oldDy = dy
        val push = max(ax, ay)
        strength = ((push - ENGAGE) / (1f - ENGAGE)).coerceIn(0f, 1f)

        if (dx == 0 && dy == 0) {
            if (push >= ENGAGE) {
                val horizontal = ax >= ay
                val nx = if (horizontal) (if (x > 0) 1 else -1) else 0
                val ny = if (horizontal) 0 else (if (y > 0) 1 else -1)
                // A spring-back is quick AND weak. A firm push the other way is a real move.
                val springBack = now - releasedAt < REBOUND_MS && nx == -lastDx && ny == -lastDy && push < FIRM
                if (!springBack) {
                    dx = nx
                    dy = ny
                }
            }
        } else if (push < RELEASE) {
            lastDx = dx
            lastDy = dy
            releasedAt = now
            dx = 0
            dy = 0
        } else {
            // Turn only on a clear push along the other axis
            val along = if (dx != 0) ax else ay
            val across = if (dx != 0) ay else ax
            if (across >= ENGAGE && across >= along * TURN) {
                if (dx != 0) {
                    dx = 0
                    dy = if (y > 0) 1 else -1
                } else {
                    dy = 0
                    dx = if (x > 0) 1 else -1
                }
            }
        }
        return dx != oldDx || dy != oldDy
    }

    fun reset() {
        dx = 0
        dy = 0
        strength = 0f
    }

    private companion object {
        // A quick flick counts once the stick is a bit over a third of the way out.
        // Letting go below a quarter keeps worn, slightly drifting sticks from moving on their own.
        const val ENGAGE = 0.38f
        const val RELEASE = 0.25f
        const val TURN = 1.15f
        const val FIRM = 0.7f
        const val REBOUND_MS = 110L
    }
}

/**
 * Runs an action once right away, then again and again while a button is held.
 * [intervalFor] gives the wait before each repeat, so repeats can speed up.
 */
class Repeater(private val handler: Handler) {

    private var action: ((Int) -> Unit)? = null
    private var intervalFor: (Int) -> Long = { 100L }
    private var count = 0

    private val tick = object : Runnable {
        override fun run() {
            val a = action ?: return
            count++
            a(count)
            handler.postDelayed(this, intervalFor(count))
        }
    }

    val isRunning: Boolean get() = action != null

    fun start(firstDelay: Long, intervalFor: (Int) -> Long, action: (Int) -> Unit) {
        stop()
        this.action = action
        this.intervalFor = intervalFor
        count = 0
        action(0)
        handler.postDelayed(tick, firstDelay)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        action = null
    }
}
