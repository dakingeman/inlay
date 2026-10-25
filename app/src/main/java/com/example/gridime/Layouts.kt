package com.example.gridime

enum class KeyType { CHAR, SHIFT, LAYER, SPACE, ENTER, DELETE, TOOL, CLIP, EMPTY }
enum class ShiftState { OFF, ONCE, LOCK }
enum class Tool { SELECT, SELECT_ALL, CUT, COPY, PASTE, HISTORY, SETTINGS }
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
 */
data class Key(
    val type: KeyType,
    val label: String = "",
    val weight: Float = 1f,
    val tool: Tool? = null,
    val hint: ControllerButton? = null,
    val index: Int = -1
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

    fun toolRow(selectAllButton: Boolean): List<Key> {
        val select = Key(KeyType.TOOL, "Select", if (selectAllButton) 1.7f else 1.9f, Tool.SELECT, ControllerButton.SELECT)
        val tail = listOf(
            Key(KeyType.TOOL, "", 0.9f, Tool.HISTORY),
            Key(KeyType.TOOL, "", 0.9f, Tool.SETTINGS)
        )
        return if (selectAllButton) {
            listOf(
                select,
                Key(KeyType.TOOL, "All", 1.3f, Tool.SELECT_ALL),
                Key(KeyType.TOOL, "Cut", 1.0f, Tool.CUT),
                Key(KeyType.TOOL, "Copy", 1.2f, Tool.COPY),
                Key(KeyType.TOOL, "Paste", 3.0f, Tool.PASTE)
            ) + tail
        } else {
            listOf(
                select,
                Key(KeyType.TOOL, "Cut", 1.2f, Tool.CUT),
                Key(KeyType.TOOL, "Copy", 1.3f, Tool.COPY),
                Key(KeyType.TOOL, "Paste", 3.8f, Tool.PASTE)
            ) + tail
        }
    }

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

    fun symbolRows(layout: KeyLayout, language: Language = Language.ENGLISH): List<List<Key>> {
        val space = if (layout == KeyLayout.GRID) 4f else 5f
        val side = if (layout == KeyLayout.GRID) 2f else 1.5f
        return listOf(
            chars("1234567890"),
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

    private const val NUMPAD_KEY = 2f       // 4 columns of 2 units, centred in the 10-unit width

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
