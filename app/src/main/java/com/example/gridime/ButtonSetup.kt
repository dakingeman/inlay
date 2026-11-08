package com.example.gridime

import android.view.KeyEvent

/**
 * Button setup (1.3): where each face button really is, and which one selects and which one closes.
 *
 * Asked once, by pressing: left, top, right, bottom, then "the one you select with" and "the one you close with".
 * That covers every controller without knowing any of them: an Xbox-style handheld (the Odin), a Nintendo-style
 * one (Retroid Pocket 6), one set to a Nintendo layout that sends its buttons swapped, a pad that swaps only one
 * pair, and pads that send numbered buttons (BUTTON_1…). Delete and Space take the two buttons left over.
 *
 * Small on purpose: four codes and two positions, saved as one short string. Turning a press into its job is a
 * lookup in a four-entry array, so typing is exactly as fast as before.
 */
data class FaceSetup(
    /** What each position sends, in the order [LEFT], [TOP], [RIGHT], [BOTTOM]. */
    val sends: List<Int> = STANDARD,
    /** The positions that select (type) and close (go back). */
    val select: Int = BOTTOM,
    val close: Int = RIGHT,
) {
    companion object {
        const val LEFT = 0; const val TOP = 1; const val RIGHT = 2; const val BOTTOM = 3
        val POSITIONS = listOf(LEFT, TOP, RIGHT, BOTTOM)
        /** What Android says the four positions send (and what most controllers do). */
        val STANDARD = listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A)

        /** The jobs, as Inlay's own codes everywhere inside: A selects, B closes, X deletes, Y is space. */
        const val SELECT = KeyEvent.KEYCODE_BUTTON_A; const val CLOSE = KeyEvent.KEYCODE_BUTTON_B
        const val DELETE = KeyEvent.KEYCODE_BUTTON_X; const val SPACE = KeyEvent.KEYCODE_BUTTON_Y

        /** A button that can be a face button: the lettered ones and numbered ones. Never the D-pad, shoulders, sticks or Start. */
        fun isFace(code: Int) = code in FACE_CODES
        private val FACE_CODES = setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_C,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Z) +
            (KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16)

        /** Settings saved before 1.3's setup: Swap and Type with: Right, as the same thing in positions. */
        fun fromOld(swap: Boolean, typeOnRight: Boolean) = FaceSetup(
            sends = if (swap) listOf(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B) else STANDARD,
            select = if (typeOnRight) RIGHT else BOTTOM, close = if (typeOnRight) BOTTOM else RIGHT)

        /** "99,100,97,96:3,2": the four codes, then select and close. Anything odd reads as the standard setup. */
        fun parse(s: String?): FaceSetup? = runCatching {
            val (codes, jobs) = s!!.split(":")
            val sends = codes.split(",").map { it.toInt() }
            val (sel, cl) = jobs.split(",").map { it.toInt() }
            FaceSetup(sends, sel, cl).takeIf { it.valid }
        }.getOrNull()

        /** The printed letter Android names a code by, for the picture ("A", "1", ...). */
        fun letter(code: Int): String = when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"; KeyEvent.KEYCODE_BUTTON_B -> "B"; KeyEvent.KEYCODE_BUTTON_C -> "C"
            KeyEvent.KEYCODE_BUTTON_X -> "X"; KeyEvent.KEYCODE_BUTTON_Y -> "Y"; KeyEvent.KEYCODE_BUTTON_Z -> "Z"
            in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 -> (code - KeyEvent.KEYCODE_BUTTON_1 + 1).toString()
            else -> "?"
        }
    }

    val valid: Boolean get() = sends.size == 4 && sends.toSet().size == 4 && sends.all { isFace(it) } &&
        select in POSITIONS && close in POSITIONS && select != close

    /** The two left over: Delete takes the first of left, top, bottom, right that's free (left, as most expect), Space the other. */
    private val rest: List<Int> get() = listOf(LEFT, TOP, BOTTOM, RIGHT).filter { it != select && it != close }
    val delete: Int get() = rest[0]
    val space: Int get() = rest[1]

    /** The job at a position, as Inlay's own code. */
    fun jobAt(position: Int): Int = when (position) { select -> SELECT; close -> CLOSE; delete -> DELETE; else -> SPACE }

    /** Where a code comes from (-1: not one of the four). */
    fun positionOf(code: Int): Int = sends.indexOf(code)

    /** A press, as the job Inlay acts on; anything that isn't one of the four is left as it is. */
    fun job(code: Int): Int { val p = positionOf(code); return if (p < 0) code else jobAt(p) }

    /** True when nothing needs changing (Android's own rule, bottom selects). */
    val standard: Boolean get() = sends == STANDARD && select == BOTTOM && close == RIGHT

    fun save(): String = sends.joinToString(",") + ":" + select + "," + close

    /** In words, by position, for Settings' summary ("Select: bottom · Close: right"). */
    fun summary(): String = "Select: ${name(select)} · Close: ${name(close)}"
    private fun name(p: Int) = listOf("left", "top", "right", "bottom")[p]
}

/**
 * The setup's steps, apart from any screen (so they can be checked, and the tour and Settings share them).
 * Feed it presses as they arrive from the device, before anything turns them into jobs.
 */
class SetupFlow(private val before: FaceSetup) {
    enum class Step { LEFT, TOP, RIGHT, BOTTOM, SELECT, CLOSE, DONE }
    /** What the last press did, so the screen can say so. */
    enum class Heard { NONE, LEARNT, AGAIN, NOT_FACE, CHOSE, SAME_AS_SELECT, TRIED, FINISH, START_OVER }

    var step = Step.LEFT; private set
    private val sends = IntArray(4) { -1 }
    var select = -1; private set
    var close = -1; private set
    /** The position the last press came from, for the picture to answer it. */
    var lastPosition = -1; private set
    /** On the last steps, which job the press tried (for "That's Delete"). */
    var lastJob = -1; private set
    private var notFace = 0

    /** The code learnt for a position so far (or -1). */
    fun sendsAt(position: Int) = sends[position]

    /** The result, once done (null before). */
    val result: FaceSetup? get() = if (step != Step.DONE) null else FaceSetup(sends.toList(), select, close).takeIf { it.valid }

    /** How many presses in a row weren't one of the four (the screen gently points at the right ones after two). */
    val strayPresses get() = notFace

    fun press(code: Int): Heard {
        lastPosition = -1; lastJob = -1
        if (!FaceSetup.isFace(code)) { notFace++; return Heard.NOT_FACE }
        notFace = 0
        when (step) {
            Step.LEFT, Step.TOP, Step.RIGHT, Step.BOTTOM -> {
                val at = step.ordinal
                val already = sends.indexOf(code)
                if (already >= 0) { lastPosition = already; return Heard.AGAIN }
                sends[at] = code; lastPosition = at
                step = Step.values()[at + 1]
                return Heard.LEARNT
            }
            Step.SELECT -> {
                val p = sends.indexOf(code).takeIf { it >= 0 } ?: return Heard.NOT_FACE
                select = p; lastPosition = p; step = Step.CLOSE; return Heard.CHOSE
            }
            Step.CLOSE -> {
                val p = sends.indexOf(code).takeIf { it >= 0 } ?: return Heard.NOT_FACE
                lastPosition = p
                if (p == select) return Heard.SAME_AS_SELECT
                close = p; step = Step.DONE; return Heard.CHOSE
            }
            Step.DONE -> {
                val r = result ?: return Heard.NONE
                val p = r.positionOf(code).takeIf { it >= 0 } ?: return Heard.NOT_FACE
                lastPosition = p; lastJob = r.jobAt(p)
                return when (lastJob) { FaceSetup.SELECT -> Heard.FINISH; FaceSetup.CLOSE -> Heard.START_OVER.also { restart() }; else -> Heard.TRIED }
            }
        }
    }

    fun restart() { step = Step.LEFT; sends.fill(-1); select = -1; close = -1 }

    /** Left without finishing: what's kept (always what was there before). */
    fun keep(): FaceSetup = before
}
