package com.example.gridime

import android.view.KeyEvent

/**
 * Button mapping: which button does which keyboard function, set like a game's controls (Settings → Device →
 * Button mapping: pick a function, then press the button you want for it).
 *
 * Example: pick Backspace and press L1. L1 now deletes, and X (which deleted before) takes L1's old function,
 * moving the cursor left. Every function is always on exactly one button, so nothing can be lost.
 *
 * Kept small and safe on purpose:
 * - The twelve buttons in [BUTTONS] can be mapped, each with the function it has out of the box ([NAMES]). The D-pad
 *   (and the sticks' pushes) keep moving the highlight, so the keyboard can always be steered.
 * - A function is named by the button that has it out of the box: X's code means Backspace. Face buttons are taken
 *   after "Your buttons" (FaceButtons), so a face code means the face button that does that job in your setup.
 * - Giving a button a function swaps it with the button that had that function ([with]).
 * - Only the keyboard uses it (and its hints follow it). Settings and the tour read the buttons as they are, so a
 *   mapping can never lock you out of Settings, where it is undone.
 * - Saved as one short string (see [save]) in the settings file (KeyboardSettings.buttonRemap).
 *
 * Immutable, and nothing here touches Android beyond key-code constants.
 */
class ButtonRemap private constructor(private val moved: Map<Int, Int>) {

    constructor() : this(emptyMap())

    /** The function a press of [code] does: the code of the button that has it out of the box. Anything else is unchanged. */
    fun role(code: Int): Int = moved[code] ?: code

    /** The button that does [job] now (the other way round from [role]). */
    fun buttonFor(job: Int): Int = moved.entries.firstOrNull { it.value == job }?.key ?: job   // unmoved: its own button

    /** True when nothing was moved (the keyboard then behaves exactly as before). */
    val isIdentity: Boolean get() = moved.isEmpty()

    /**
     * [button] now does [job], and the button that did [job] takes over what [button] did (a swap).
     * Anything that isn't a mappable button changes nothing.
     */
    fun with(button: Int, job: Int): ButtonRemap {
        if (button !in BUTTONS || job !in BUTTONS) return this
        val before = role(button)
        if (before == job) return this
        val other = BUTTONS.first { role(it) == job }          // exists: the roles are a shuffle of BUTTONS
        val next = BUTTONS.associateWith { role(it) }.toMutableMap()
        next[button] = job
        next[other] = before
        return of(next)
    }

    /** The badge for the button that does [job] now (the keyboard's hints follow the mapping). */
    fun shownButton(job: ControllerButton): ControllerButton {
        val code = KEYS[job] ?: return job
        return BUTTON_OF[buttonFor(code)] ?: job
    }

    /** "103:102,102:103" (button:job, moved buttons only, in button order; "" when nothing moved). */
    fun save(): String = BUTTONS.filter { it in moved }.joinToString(",") { "$it:${moved.getValue(it)}" }

    override fun equals(other: Any?) = other is ButtonRemap && other.moved == moved
    override fun hashCode() = moved.hashCode()
    override fun toString() = "ButtonRemap(${save()})"

    companion object {
        /** The buttons that can be mapped, in the order Settings lists their functions. */
        val BUTTONS: List<Int> = listOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR
        )

        /** Each button's function out of the box, in the same order as [BUTTONS]: what Settings calls them. */
        val NAMES: List<String> = listOf(
            "Type", "Close", "Backspace", "Space",
            "Cursor left", "Cursor right", "Symbols", "Shift",
            "Select text", "Enter", "Resize", "Take suggestion"
        )

        /** A little more about each function, for its row (same order). */
        val DETAILS: List<String> = listOf(
            "Types the highlighted key. Hold for accents", "Closes the keyboard, or what's open in it",
            "Deletes. Hold to delete faster", "Types a space",
            "Moves the text cursor left. Hold to keep going", "Moves the text cursor right. Hold to keep going",
            "Hold for quick symbols, tap to switch", "Tap for a capital, twice for caps lock. Hold for combos",
            "Select mode. Press twice to select all", "Enter, or the app's action (Go, Send)",
            "Normal, large, largest", "Types the first suggested word"
        )

        /** The badge for each mappable button ({X} in hints), and the other way round. */
        val KEYS: Map<ControllerButton, Int> = mapOf(
            ControllerButton.A to KeyEvent.KEYCODE_BUTTON_A, ControllerButton.B to KeyEvent.KEYCODE_BUTTON_B,
            ControllerButton.X to KeyEvent.KEYCODE_BUTTON_X, ControllerButton.Y to KeyEvent.KEYCODE_BUTTON_Y,
            ControllerButton.L1 to KeyEvent.KEYCODE_BUTTON_L1, ControllerButton.R1 to KeyEvent.KEYCODE_BUTTON_R1,
            ControllerButton.L2 to KeyEvent.KEYCODE_BUTTON_L2, ControllerButton.R2 to KeyEvent.KEYCODE_BUTTON_R2,
            ControllerButton.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT, ControllerButton.START to KeyEvent.KEYCODE_BUTTON_START,
            ControllerButton.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL, ControllerButton.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR
        )
        val BUTTON_OF: Map<Int, ControllerButton> = KEYS.entries.associate { (b, k) -> k to b }

        fun name(job: Int): String = NAMES.getOrNull(BUTTONS.indexOf(job)) ?: "?"

        /** "{L1}": a button as a hint badge. */
        fun badge(code: Int): String = BUTTON_OF[code]?.let { "{${it.name}}" } ?: "?"

        /** The names of Settings' rows (also what Find looks for). */
        fun rowTitle(job: Int): String = "${name(job)} button"
        const val SECTION = "Button mapping"
        const val RESET_ROW = "Reset button mapping"

        /** Everyday words for Find, by row name (merged into SettingsFind.WORDS). */
        val FIND_WORDS: Map<String, String> = BUTTONS.associate {
            rowTitle(it) to "remap rebind assign map mapping controls"
        } + (RESET_ROW to "remap rebind reassign undo default buttons mapping controls")

        /** A shuffle among [BUTTONS], unmoved buttons left out; nothing moved when [map] isn't one. */
        private fun of(map: Map<Int, Int>): ButtonRemap {
            val moved = map.filter { (k, v) -> k != v }
            val valid = moved.keys.all { it in BUTTONS } && moved.values.all { it in BUTTONS } &&
                moved.keys == moved.values.toSet()
            return if (valid) ButtonRemap(moved) else ButtonRemap()
        }

        /** Reads [save]'s text. Anything odd (a made-up code, a duplicate, half a swap) reads as nothing moved. */
        fun parse(s: String?): ButtonRemap {
            if (s.isNullOrBlank()) return ButtonRemap()
            return runCatching {
                val pairs = s.split(",").map { e ->
                    val (b, j) = e.split(":"); b.trim().toInt() to j.trim().toInt()
                }
                if (pairs.map { it.first }.toSet().size != pairs.size) ButtonRemap() else of(pairs.toMap())
            }.getOrDefault(ButtonRemap())
        }

        // The keyboard asks on every press: the same text gives the same answer without parsing it again.
        @Volatile private var cachedText: String = ""
        @Volatile private var cached: ButtonRemap = ButtonRemap()

        fun cachedParse(s: String): ButtonRemap {
            if (s == cachedText) return cached
            return parse(s).also { cached = it; cachedText = s }
        }
    }
}
