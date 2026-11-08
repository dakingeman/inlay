package com.example.gridime

/** DIGIT = a key on the optional number row. GAP = an empty word slot in the tool strip (never focused). */
enum class KeyType { CHAR, SHIFT, LAYER, SPACE, ENTER, DELETE, TOOL, CLIP, EMPTY, DIGIT, GAP }
enum class ShiftState { OFF, ONCE, LOCK }
enum class Tool { SELECT, SELECT_ALL, CUT, COPY, PASTE, HISTORY, SETTINGS, WORD, VOICE }
enum class Layer { LETTERS, SYMBOLS, CLIPS, NUMPAD }

/**
 * What kind of text box is being typed into, from the app's own description of it.
 * Smart keys use it: web addresses get "/" and ".com", email boxes get "@" and ".com",
 * numbers and phone numbers open a big number pad.
 */
enum class FieldKind { TEXT, URL, EMAIL, NUMBER, PHONE }

/**
 * One key on the grid.
 * weight = width in units (a full row is 10 units)
 * hint   = the controller badge shown in the key's corner
 * index  = which clipboard item a clip card holds
 * best   = the first word suggestion (drawn as a key, taken with R3)
 */
data class Key(
    val type: KeyType,
    val label: String = "",
    val weight: Float = 1f,
    val tool: Tool? = null,
    val hint: ControllerButton? = null,
    val index: Int = -1,
    val best: Boolean = false
)

/**
 * All keyboard layouts. Change a row here to change the keyboard.
 *
 * The GRID layout lines every key up in 10 columns, the way Microsoft's
 * controller keyboard does, so Up and Down always land on the key directly above
 * or below. CLASSIC keeps the familiar phone stagger.
 */
object Layouts {
    const val UNITS = 10f
    const val MAX_CLIP_CARDS = 6      // 3 rows of 2; the 4th row holds the button guide

    private fun chars(s: String, weight: Float = 1f) = s.map { Key(KeyType.CHAR, it.toString(), weight) }

    /**
     * The tool strip. While a word is being typed, [words] (most likely first) take the place
     * of Cut / Copy / Paste. Always three slots, so one or two words keep their normal size.
     */
    /** [voice]: a voice typing app is there to hand off to (1.3): a microphone joins the icons on the right. */
    fun toolRow(selectAllButton: Boolean, words: List<String> = emptyList(), split: Boolean = false, voice: Boolean = false): List<Key> {
        if (split) return splitToolRow(selectAllButton, words, voice)
        val select = Key(KeyType.TOOL, "Select", if (selectAllButton) 1.7f else 1.9f, Tool.SELECT, ControllerButton.SELECT)
        val tail = listOfNotNull(
            if (voice) Key(KeyType.TOOL, "", 0.9f, Tool.VOICE) else null,
            Key(KeyType.TOOL, "", 0.9f, Tool.HISTORY),
            Key(KeyType.TOOL, "", 0.9f, Tool.SETTINGS)
        )
        val icons = tail.sumOf { it.weight.toDouble() }.toFloat()
        val give = icons - 1.8f                                    // what the microphone takes from Paste
        if (words.isNotEmpty()) {
            val slot = (UNITS - select.weight - icons) / 3f
            val slots = (0 until 3).map { i ->
                words.getOrNull(i)?.let {
                    Key(KeyType.TOOL, it, slot, Tool.WORD, if (i == 0) ControllerButton.R3 else null, best = i == 0)
                } ?: Key(KeyType.GAP, weight = slot)
            }
            return listOf(select) + slots + tail
        }
        return if (selectAllButton) {
            listOf(
                select,
                Key(KeyType.TOOL, "All", 1.3f, Tool.SELECT_ALL),
                Key(KeyType.TOOL, "Cut", 1.0f, Tool.CUT),
                Key(KeyType.TOOL, "Copy", 1.2f, Tool.COPY),
                Key(KeyType.TOOL, "Paste", 3.0f - give, Tool.PASTE)
            ) + tail
        } else {
            listOf(
                select,
                Key(KeyType.TOOL, "Cut", 1.2f, Tool.CUT),
                Key(KeyType.TOOL, "Copy", 1.3f, Tool.COPY),
                Key(KeyType.TOOL, "Paste", 3.8f - give, Tool.PASTE)
            ) + tail
        }
    }

    /**
     * The tool strip for Split: each window gets its own part, and both parts are exactly half a row, so nothing
     * straddles the middle. Left: Select, (All), Cut, Copy, or the first word. Right: Paste, or the other words, and
     * the clipboard and Settings icons.
     */
    private fun splitToolRow(selectAllButton: Boolean, words: List<String>, voice: Boolean = false): List<Key> {
        val half = UNITS / 2
        val tail = listOfNotNull(if (voice) Key(KeyType.TOOL, "", 0.9f, Tool.VOICE) else null,
            Key(KeyType.TOOL, "", 0.9f, Tool.HISTORY), Key(KeyType.TOOL, "", 0.9f, Tool.SETTINGS))
        val icons = tail.sumOf { it.weight.toDouble() }.toFloat()
        val select = Key(KeyType.TOOL, "Select", if (selectAllButton) 1.7f else 1.9f, Tool.SELECT, ControllerButton.SELECT)
        if (words.isNotEmpty()) {
            val first = Key(KeyType.TOOL, words[0], half - select.weight, Tool.WORD, ControllerButton.R3, best = true)
            val slot = (half - icons) / 2f
            val rest = (1..2).map { i -> words.getOrNull(i)?.let { Key(KeyType.TOOL, it, slot, Tool.WORD) } ?: Key(KeyType.GAP, weight = slot) }
            return listOf(select, first) + rest + tail
        }
        val left = if (selectAllButton) listOf(select, Key(KeyType.TOOL, "All", 1.1f, Tool.SELECT_ALL),
            Key(KeyType.TOOL, "Cut", 1.0f, Tool.CUT), Key(KeyType.TOOL, "Copy", 1.2f, Tool.COPY))
        else listOf(select, Key(KeyType.TOOL, "Cut", 1.5f, Tool.CUT), Key(KeyType.TOOL, "Copy", 1.6f, Tool.COPY))
        return left + Key(KeyType.TOOL, "Paste", half - icons, Tool.PASTE) + tail
    }

    /**
     * Split: a row made ready for two windows. A space bar (or the empty clipboard's note) that runs across the middle
     * becomes two, one in each window; the right-hand space bar has no badge, since Y already says where space is.
     * Every other key stays as it is: [splitIndex] puts it on the side where the row's middle edge is nearest.
     */
    fun splitRow(row: List<Key>, half: Float? = null): List<Key> {
        val mid = row.fold(0f) { a, k -> a + k.weight } / 2f
        var x = 0f
        val out = ArrayList<Key>(row.size + 1)
        for (k in row) {
            val l = x; val r = x + k.weight; x = r
            if ((k.type == KeyType.SPACE || k.type == KeyType.EMPTY) && l < mid - SPLIT_EPS && r > mid + SPLIT_EPS) {
                out += k.copy(weight = mid - l)
                out += k.copy(weight = r - mid, hint = null, label = if (k.type == KeyType.EMPTY) "" else k.label)
            } else out += k
        }
        if (half == null || out.size < 2) return out
        // Each side fills its own window. With 11 columns a row splits 5 + 6: the five used to sit in a window sized
        // for six, leaving a hole at its edge (Emre, 6 Nov, Swedish). Now the five are a little wider instead, the same
        // in every row, so the columns still line up within each window.
        val at = splitIndex(out)
        val leftSum = out.take(at).sumOf { it.weight.toDouble() }.toFloat()
        val rightSum = out.drop(at).sumOf { it.weight.toDouble() }.toFloat()
        return out.mapIndexed { i, k -> k.copy(weight = k.weight * half / (if (i < at) leftSum else rightSum)) }
    }

    /**
     * Split: how many keys of [row] go in the left window, at the key edge nearest the row's middle (on a tie, the
     * left gets fewer). Always at least one key on each side when there are two or more.
     */
    fun splitIndex(row: List<Key>): Int {
        if (row.size < 2) return row.size
        val mid = row.fold(0f) { a, k -> a + k.weight } / 2f
        var best = 1; var bestD = Float.MAX_VALUE; var x = 0f
        for (i in 0 until row.size - 1) {
            x += row[i].weight
            val d = kotlin.math.abs(x - mid)
            if (d < bestD - SPLIT_EPS) { bestD = d; best = i + 1 }
        }
        return best
    }

    /** Split: the widest half of a language's rows, in units (half a row, or one column more for odd columns). */
    fun splitHalfUnits(language: Language): Float {
        val cols = language.columns
        return if (cols % 2 == 0) UNITS / 2 else ((cols + 1) / 2) * UNITS / cols
    }

    private const val SPLIT_EPS = 0.001f

    /**
     * The letter rows for a language. Every letter is one column wide, so the
     * columns stay straight even when a language needs 11 or 12 of them.
     * Shift and Delete fill the rest of the third row with whole columns.
     */
    fun letterRows(layout: KeyLayout, language: Language = Language.ENGLISH, field: FieldKind = FieldKind.TEXT): List<List<Key>> {
        val cols = language.columns
        val w = UNITS / cols
        val row2 = if (layout == KeyLayout.CLASSIC) language.classicRow2 ?: language.rows[1] else language.rows[1]
        val row3 = language.rows[2]
        val spare = cols - row3.length
        // Grid: Shift gets the smaller half, Delete the bigger one (it's used far more).
        // Classic: both halves equal, like a phone keyboard.
        val shiftCols = if (layout == KeyLayout.GRID) (spare / 2).toFloat() else spare / 2f
        val deleteCols = spare - shiftCols
        val side = if (layout == KeyLayout.GRID) 2f else 1.5f
        val bottom = when (field) {
            // Smart keys: the comma makes way for what that kind of box needs most
            FieldKind.URL -> smartBottomRow("?123", "/", side)
            FieldKind.EMAIL -> smartBottomRow("?123", "@", side)
            else -> bottomRow("?123", space = 10f - 2 * side - 2f, side = side)
        }
        return listOf(
            chars(language.rows[0], w),
            chars(row2, w),
            listOf(shift(shiftCols * w)) + chars(row3, w) + delete(deleteCols * w),
            bottom
        )
    }

    /** The optional number row: a slim bar of 1 to 0 above the letters (and the symbols). */
    fun numberRow(): List<Key> = "1234567890".map { Key(KeyType.DIGIT, it.toString()) }

    /** With the number row on, the numbers are already on top, so the symbols page brings extra symbols instead. */
    fun symbolRows(layout: KeyLayout, language: Language = Language.ENGLISH, numberRow: Boolean = false): List<List<Key>> {
        val space = if (layout == KeyLayout.GRID) 4f else 5f
        val side = if (layout == KeyLayout.GRID) 2f else 1.5f
        return listOf(
            if (numberRow) chars("~`|\\{}[]<>") else chars("1234567890"),
            chars("@#\$%&-+()/"),
            chars("*\"':;!?=" + language.currency) + delete(1f),
            bottomRow(language.lettersKey, space, side)
        )
    }

    /**
     * The number pad: four even columns, like a phone's. The last column holds Delete
     * and Enter, the extra sign depends on the box (minus for numbers, plus and # for phones).
     */
    fun numpadRows(field: FieldKind): List<List<Key>> {
        val phone = field == FieldKind.PHONE
        fun n(s: String) = Key(KeyType.CHAR, s, NUMPAD_KEY)
        return listOf(
            listOf(n("1"), n("2"), n("3"), Key(KeyType.DELETE, weight = NUMPAD_KEY, hint = ControllerButton.X)),
            listOf(n("4"), n("5"), n("6"), n(if (phone) "+" else "-")),
            listOf(n("7"), n("8"), n("9"), n(if (phone) "*" else ",")),
            listOf(Key(KeyType.LAYER, "ABC", NUMPAD_KEY, hint = ControllerButton.L2), n("0"), n(if (phone) "#" else "."),
                Key(KeyType.ENTER, weight = NUMPAD_KEY, hint = ControllerButton.START))
        )
    }

    private const val NUMPAD_KEY = 1.6f     // 4 columns of 1.6 units: a compact block in the middle, not stretched

    fun clipRows(clips: List<String>): List<List<Key>> {
        if (clips.isEmpty()) {
            return listOf(listOf(Key(KeyType.EMPTY, "Copied text will show up here", UNITS)))
        }
        return clips.take(MAX_CLIP_CARDS).chunked(2).mapIndexed { r, pair ->
            pair.mapIndexed { c, text -> Key(KeyType.CLIP, text, UNITS / 2, index = r * 2 + c) }
        }
    }

    private fun shift(weight: Float) = Key(KeyType.SHIFT, weight = weight, hint = ControllerButton.R2)
    private fun delete(weight: Float) = Key(KeyType.DELETE, weight = weight, hint = ControllerButton.X)

    /** Web address / email bottom row: the special key, a shorter space bar, ".com", then "." */
    private fun smartBottomRow(layerLabel: String, special: String, side: Float) = listOf(
        Key(KeyType.LAYER, layerLabel, side, hint = ControllerButton.L2),
        Key(KeyType.CHAR, special),
        Key(KeyType.SPACE, "", 10f - 2 * side - 4f, hint = ControllerButton.Y),
        Key(KeyType.CHAR, ".com", 2f),
        Key(KeyType.CHAR, "."),
        Key(KeyType.ENTER, "", side, hint = ControllerButton.START)
    )

    private fun bottomRow(layerLabel: String, space: Float, side: Float) = listOf(
        Key(KeyType.LAYER, layerLabel, side, hint = ControllerButton.L2),
        Key(KeyType.CHAR, ","),
        Key(KeyType.SPACE, "", space, hint = ControllerButton.Y),
        Key(KeyType.CHAR, "."),
        Key(KeyType.ENTER, "", side, hint = ControllerButton.START)
    )
}
