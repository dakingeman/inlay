package com.example.gridime

/**
 * Which of a page's button rows the demo is acting out right now, worked out from what the device picture
 * lights. The tour uses it twice: on wide screens the row lights up in the list, and on small screens it is
 * the one row shown, as a caption that changes with the demo (so every row gets its moment in full).
 *
 * Each row is read from its own hint text, so a new page needs nothing extra:
 *  - a plain row ("{X}", "{L1} {R1}", "{R2} + {A}") matches when its button is pressed (a combo's first
 *    button held at the time)
 *  - a hold row ("hold {A}") matches when its button is held down
 *  - a let-go row ("let go of {A}") matches when that held button comes up
 *  - remote rows read their words: OK, Back, the ring, up
 * When several rows match, the one with more buttons wins (then one not yet shown this run).
 * On wide screens every row is already in view, so only the rows the demo really acts out light up
 * ([takeTurns] off). The caption takes turns, so that every row is seen:
 *
 * So the caption is never jumpy and never skips anything:
 *  - a row stays at least [DWELL_MS] before the next one takes over (fast typing keeps one row)
 *  - once a row has shown for [IDLE_MS], a row that hasn't had its turn this run comes next (rows the
 *    demo never presses, like Close the keyboard, included), so every row appears in every run or two.
 *    TourCaptionTest checks this on every page and device.
 */
class RowLight(rows: List<List<HintPart>>, private val takeTurns: Boolean = true, private val onChange: (row: Int, byDemo: Boolean) -> Unit) {

    private enum class Kind { PRESS, HOLD, RELEASE }
    private class Row(val press: Set<String>, val held: Set<String>, val kind: Kind) { val size get() = press.size + held.size }

    private val rows: List<Row> = rows.map { read(it) }
    val count get() = rows.size

    var current = -1
        private set
    private val shownThisRun = mutableSetOf<Int>()
    /** For the checks: every row shown since the page opened. */
    internal val everShown = mutableSetOf<Int>()
    private var lastSwitch = 0L
    private var lastEvent = 0L
    private var pending = -1
    private var prevLit = emptySet<String>()
    private var prevHolding = emptySet<String>()

    /** The page opens: its first row shows at once, so there is never an empty caption. */
    fun start(now: Long) {
        shownThisRun.clear(); everShown.clear(); owed.clear(); pending = -1; prevLit = emptySet(); prevHolding = emptySet()
        lastEvent = now
        if (rows.isNotEmpty()) show(0, now)
    }

    fun allShownThisRun() = rows.isNotEmpty() && shownThisRun.size >= rows.size

    /** The demo starts a new run: every row is due again. */
    fun newRun() {
        // Rows the last run never got to come first in this one
        owed.clear(); owed += rows.indices.filter { it !in shownThisRun }
        shownThisRun.clear(); current.takeIf { it >= 0 }?.let { shownThisRun += it }
    }
    private val owed = ArrayDeque<Int>()

    /** The device picture's lights changed. */
    fun lit(lit: Set<String>, holding: Set<String>, now: Long) {
        val pressed = lit - prevLit
        val gotHeld = holding - prevHolding
        val letGo = prevHolding - holding
        prevLit = lit; prevHolding = holding
        if (pressed.isEmpty() && gotHeld.isEmpty() && letGo.isEmpty()) return
        val matches = rows.indices.filter { i ->
            val r = rows[i]
            when (r.kind) {
                Kind.HOLD -> r.held.isNotEmpty() && r.held.all { it in holding } && r.held.any { it in gotHeld }
                Kind.RELEASE -> r.press.any { it in letGo }
                Kind.PRESS -> r.press.isNotEmpty() && r.press.any { it in pressed } && r.held.all { it in holding || it in lit }
            }
        }
        if (matches.isEmpty()) return
        lastEvent = now
        val best = matches.maxOf { rows[it].size }
        val top = matches.filter { rows[it].size == best }
        val pick = if (current in top) current else top.firstOrNull { it !in shownThisRun } ?: top.first()
        // Taking turns (the caption): while some rows still wait for their turn this run, a row that has
        // already had one doesn't cut in, so the round goes through calmly, one row after another
        if (takeTurns && pick in shownThisRun && rows.indices.any { it !in shownThisRun }) return
        if (pick == current) { pending = -1; return }
        if (now - lastSwitch >= DWELL_MS && owed.isEmpty()) show(pick, now, byDemo = true) else pending = pick
    }

    /** Called a few times a second: shows a waiting row once the dwell is over, and fills quiet moments. */
    fun tick(now: Long) {
        if (rows.isEmpty()) return
        if (!takeTurns) { if (pending >= 0 && now - lastSwitch >= DWELL_MS) { val p = pending; pending = -1; show(p, now, byDemo = true) }; return }
        if (owed.isNotEmpty() && now - lastSwitch >= DWELL_MS) { show(owed.removeFirst(), now); return }
        if (pending >= 0 && now - lastSwitch >= DWELL_MS) { val p = pending; pending = -1; show(p, now, byDemo = true); return }
        // A row that has had its time gives way to one that hasn't had its turn this run, even while the
        // demo keeps pressing the same button (typing presses A all the time): every row gets its moment
        val quiet = now - lastSwitch >= IDLE_MS
        if (quiet && rows.size > 1) {
            val next = (1..rows.size).map { (current + it) % rows.size }.firstOrNull { it !in shownThisRun }
            if (next != null) { show(next, now); lastEvent = now }
        }
    }

    private fun show(i: Int, now: Long, byDemo: Boolean = false) {
        current = i; lastSwitch = now
        shownThisRun += i; everShown += i
        onChange(i, byDemo)
    }

    /** The device buttons of row [i] (picture names: A, RB, Dup…). */
    fun keysOf(i: Int): Set<String> = rows.getOrNull(i)?.let { it.press + it.held } ?: emptySet()

    private fun read(parts: List<HintPart>): Row {
        val press = mutableSetOf<String>(); val held = mutableSetOf<String>()
        var release = false
        for (p in parts) when (p) {
            is HintPart.Badge -> if (p.held) held += DevicePicture.keyFor(p.button) else press += DevicePicture.keyFor(p.button)
            is HintPart.Dpad -> press += "D" + mapOf('U' to "up", 'D' to "down", 'L' to "left", 'R' to "right").getValue(p.dir)
            is HintPart.HeldWord -> remoteKeys(p.word).let { held += it }
            is HintPart.Text -> {
                val t = p.text.lowercase()
                if ("let go" in t) release = true
                val keys = remoteKeys(t)
                if (Regex("""(^|\s)hold\s""").containsMatchIn("$t ") && keys.isNotEmpty()) held += keys else press += keys
            }
        }
        val kind = when {
            release -> Kind.RELEASE.also { press += held; held.clear() }
            press.isEmpty() && held.isNotEmpty() -> Kind.HOLD
            else -> Kind.PRESS
        }
        return Row(press, held, kind)
    }

    /** A TV remote's rows are words: OK and Back are its A and B, the ring is the D-pad. */
    private fun remoteKeys(text: String): Set<String> {
        val t = text.lowercase()
        val out = mutableSetOf<String>()
        if (Regex("""\bok\b""").containsMatchIn(t)) out += "A"
        if (Regex("""\bback\b""").containsMatchIn(t)) out += "B"
        if ("the ring" in t) out += setOf("Dup", "Ddown", "Dleft", "Dright")
        if (Regex("""\bup\b""").containsMatchIn(t)) out += "Dup"
        return out
    }

    companion object {
        /** The least a row stays before the next takes over: long enough to read a short row. */
        const val DWELL_MS = 1100L
        /** After a row has shown this long, a row that hasn't had its turn yet comes next. */
        const val IDLE_MS = 1600L
        /** How often [tick] runs. */
        const val TICK_MS = 200L
    }
}
