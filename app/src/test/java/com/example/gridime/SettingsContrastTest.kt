package com.example.gridime

import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every word in Settings, in every state, with every preset colour: at least 4.5:1 (WCAG AA for text).
 * Covers the states the screen renders don't show at once: focused rows, option pickers, tags, finished set-up steps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsContrastTest {
    private fun dim(c: Int, alpha: Float, on: Int) = Contrast.over(Contrast.withAlpha(c, (255 * alpha).toInt()), on)

    @Test fun everyStateReads() {
        val problems = mutableListOf<String>()
        var worst = 99.0
        fun check(what: String, fg: Int, bg: Int) { val r = Contrast.ratio(fg, bg); worst = minOf(worst, r); if (r < 4.5) problems += "$what: ${"%.2f".format(r)}" }
        for ((surfaceName, surface) in listOf("card" to Design.SURFACE_CARD, "focused row" to Design.SURFACE_RAISED, "background" to Design.SURFACE_BG)) {
            check("title on $surfaceName", Design.TEXT_PRIMARY, surface)
            check("subtitle on $surfaceName", Design.TEXT_SECONDARY, surface)
        }
        check("option picker word on its track", Design.TEXT_PRIMARY, Design.TRACK)
        // A finished set-up step: grey title, grey subtitle (never faded)
        check("finished step title", Design.TEXT_SECONDARY, Design.SURFACE_CARD)
        val customs = (0 until 360 step 30).flatMap { h -> listOf(0, 100).flatMap { s -> listOf(0, 100).map { b -> "custom $h/$s/$b" to Palette.customAccent(h, s, b) } } }
        for ((name, accent) in Palette.accents + customs) {
            val onAccent = Contrast.inkFor(accent)
            check("$name: words on the chosen tab / picker", onAccent, accent)
            // Tags: the accent's words on its own soft tint, on black and on the card
            for ((bgName, bg) in listOf("black" to Design.SURFACE_BG, "card" to Design.SURFACE_CARD))
                check("$name: tag word on its tint on $bgName", accent, Contrast.over(Contrast.withAlpha(accent, Design.ALPHA_TINT_TAG), bg))
        }
        println("Settings text: lowest ${"%.2f".format(worst)}")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
