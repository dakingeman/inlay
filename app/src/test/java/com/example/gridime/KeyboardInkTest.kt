package com.example.gridime

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every quiet word on the keyboard stays readable on every theme: soft labels and icons (tool names, the
 * plain letter in the accent tray), strong ink (digits, chip text) and chip text on its chip, all at least 4.5:1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardInkTest {
    @Test fun quietInksReadOnEveryTheme() {
        val problems = mutableListOf<String>()
        var worst = 99.0
        for (tone in TestTones.solid) {
            for ((where, surface) in listOf("key" to tone.key, "special key" to tone.special, "background" to tone.background)) {
                val ink = Contrast.inkFor(surface)
                for ((what, alpha) in listOf("soft ink" to Design.ALPHA_INK_SOFT, "strong ink" to Design.ALPHA_INK_STRONG)) {
                    val r = Contrast.ratio(Contrast.over(Contrast.withAlpha(ink, alpha), surface), surface); worst = minOf(worst, r)
                    if (r < 4.5) problems += "${tone.name}: $what on $where ${"%.2f".format(r)}"
                }
            }
            // chip text on its chip, on the background
            val ink = Contrast.inkFor(tone.background)
            val chip = Contrast.over(Contrast.withAlpha(ink, Design.ALPHA_FAINT), tone.background)
            val r = Contrast.ratio(Contrast.over(Contrast.withAlpha(ink, Design.ALPHA_INK_STRONG), chip), chip); worst = minOf(worst, r)
            if (r < 4.5) problems += "${tone.name}: chip text ${"%.2f".format(r)}"
        }
        println("Keyboard quiet inks: lowest ${"%.2f".format(worst)}")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
