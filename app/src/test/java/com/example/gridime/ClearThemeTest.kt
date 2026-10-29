package com.example.gridime

import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The see-through theme shows the app behind the keyboard, which can be any colour. At every see-through
 * level (0 to 10) and on a white, black, mid-grey, yellow and blue app behind: letters read at least 4.5:1
 * on their keys, and the highlight stands out 3:1 from the key around it, by its colour or by its dark edge,
 * with every preset and the custom colour's extremes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClearThemeTest {
    @Test fun readableOnAnythingBehind() {
        val behind = listOf("white" to Color.WHITE, "black" to Color.BLACK, "grey" to Color.parseColor("#808080"),
            "yellow" to Color.parseColor("#FFE63C"), "blue" to Color.parseColor("#2F6BFF"))
        val accents = Palette.accents + listOf(0, 120, 240).flatMap { h -> listOf(0, 100).map { b -> "custom $h/$b" to Palette.customAccent(h, 100, b) } }
        val problems = mutableListOf<String>()
        var worstText = 99.0; var worstMark = 99.0
        for (level in 0..10) for ((what, app) in behind) {
            val t = level / 10f
            val veil = Contrast.withAlpha(Color.BLACK, ((1f - t) * Design.CLEAR_VEIL * 255).toInt())
            val under = Contrast.over(veil, app)
            for ((kind, a) in listOf("letter key" to Design.CLEAR_KEY, "special key" to Design.CLEAR_SPECIAL)) {
                val key = Contrast.over(Contrast.withAlpha(Color.BLACK, ((a - Design.CLEAR_KEY_FADE * t) * 255).toInt()), under)
                val text = Contrast.ratio(Color.WHITE, key); worstText = minOf(worstText, text)
                if (text < 4.5) problems += "level $level on $what: words on a $kind ${"%.2f".format(text)}"
                if (kind != "letter key") continue
                val edge = Contrast.over(Contrast.withAlpha(Design.INK_DARK, Design.CLEAR_FOCUS_EDGE), key)
                for ((name, accent) in accents) {
                    val stands = maxOf(Contrast.ratio(accent, key), Contrast.ratio(edge, key), Contrast.ratio(accent, edge).takeIf { Contrast.ratio(edge, key) >= 1.5 } ?: 0.0)
                    worstMark = minOf(worstMark, stands)
                    if (stands < 3.0) problems += "level $level on $what, $name: the highlight ${"%.2f".format(stands)}"
                }
            }
        }
        println("See-through: words lowest ${"%.2f".format(worstText)}, highlight lowest ${"%.2f".format(worstMark)}")
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }
}
