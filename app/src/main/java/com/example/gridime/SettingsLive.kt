package com.example.gridime

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Try your buttons, live: under the picture, what the button you just pressed does while typing. One clear line
 * ("Delete") and a short detail under it ("Hold to delete faster"). Holding RT shows "Hold RT, then:" with its few
 * combos as badges; pressing one names it. Everything comes from the Button guide's own rows, so the two never disagree.
 * Calm by design: one place to look (where your eyes already are), two lines at most, no list of every combo.
 */
class LiveCaption(context: Context) : LinearLayout(context) {
    private val dp = resources.displayMetrics.density
    private val title = TextView(context).apply {
        textSize = Design.TYPE_ROW; typeface = SettingsStyle.medium; setTextColor(SettingsStyle.textPrimary); gravity = Gravity.CENTER
    }
    private var detail: android.view.View? = null

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** [line] may hold button badges ("{A} One capital  {X} Undo"); null for none. */
    fun show(what: String, line: String?, style: ButtonStyle) {
        title.text = what
        detail?.let { removeView(it) }; detail = null
        if (line.isNullOrBlank()) return
        val parts = HintText.parse(line)
        val v = if (parts.none { it is HintPart.Badge || it is HintPart.Dpad }) TextView(context).apply {
            text = line; textSize = Design.TYPE_CAPTION; setTextColor(SettingsStyle.textSecondary); gravity = Gravity.CENTER
        } else BadgeLineView(context, parts, style, Design.TYPE_CAPTION, SettingsStyle.textSecondary, badgeScale = Design.BADGE_INLINE, wrap = true)
            .apply { alignCentre = true }
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (Design.SPACE_XS * dp).toInt() })
        detail = v
    }

    companion object {
        private val MOVES = setOf("Dup", "Ddown", "Dleft", "Dright", "LS")
        private fun cap(s: String) = s.trim().replaceFirstChar { it.uppercase() }

        /**
         * What [key] does, from the Button guide's [rows] (name, buttons): the button on its own, a hold on it, and with
         * RT held ([rtHeld]) its combo. RT itself: what it does, then its combos.
         */
        fun describe(key: String, rtHeld: Boolean, rows: List<Pair<String, String>>): Pair<String, String?> {
            if (key in MOVES) return "Move the highlight" to null
            fun keys(b: String) = DevicePicture.keysIn(b)
            fun held(b: String) = DevicePicture.holdKeysIn(b)
            if (rtHeld && key != "RT") rows.firstOrNull { keys(it.second) == setOf("RT", key) }?.let { (t, _) ->
                return cap(t.substringBefore(" · ")) to t.substringAfter(" · ", "").takeIf { it.isNotEmpty() }?.let(::cap)
            }
            val alone = rows.filter { keys(it.second) == setOf(key) }
            val plain = alone.firstOrNull { key !in held(it.second) }
            val hold = alone.firstOrNull { key in held(it.second) }
            if (key == "RT") {
                // the button combos only ("RT + R3 left or right" belongs to the stick, and reads badly in a line)
                val combos = rows.filter { "RT" in keys(it.second) && "RT" in held(it.second) &&
                        Regex("""\{[A-Z0-9]+\}( \{[A-Z0-9]+\})?""").matches(it.second.substringAfter("+").trim()) }
                    .mapNotNull { (t, b) -> (keys(b) - "RT").firstOrNull()?.let { k -> b.substringAfter("+").trim() to t.substringBefore(" · ") } }
                    .distinctBy { it.first }.take(4)
                val what = plain?.first?.substringBefore(" · ")?.let(::cap) ?: "Shift"
                return what to (if (combos.isEmpty()) null else "Hold it, then: " + combos.joinToString("   ") { (b, t) -> "$b ${shortName(t)}" })
            }
            if (plain == null && hold == null) return "Not used while typing" to null
            val main = plain ?: hold!!
            val name = cap(main.first.substringBefore(" · "))
            val bits = listOfNotNull(main.first.substringAfter(" · ", "").takeIf { it.isNotEmpty() }?.let(::cap),
                hold?.takeIf { it !== main }?.let { "Hold: " + it.first.substringBefore(" · ").lowercase() })
            return name to bits.joinToString(". ").ifEmpty { null }
        }

        /** Combo names short enough to sit four in a line. */
        private fun shortName(t: String) = when {
            t.startsWith("One capital") -> "Capital"
            t.startsWith("Accent the last") -> "Accent"
            t.startsWith("Jump a whole word") -> "Jump word"
            t.startsWith("Switch language") -> "Language"
            else -> t.substringBefore(" · ").split(" ").take(2).joinToString(" ")
        }
    }
}
