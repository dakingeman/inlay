package com.example.gridime

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The highlight never disappears: on every keyboard theme, with every preset colour and the custom colour's
 * extremes, the colour Inlay actually draws stands out at least 3:1 from the keys and the background
 * (focus and selection, WCAG non-text contrast), and the words on it read at least 4.5:1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeAccentTest {
    @Test fun highlightStandsOutOnEveryTheme() {
        val colours = Palette.accents.map { it.first to it.second } +
            listOf(0, 60, 120, 180, 240, 300).flatMap { h -> listOf(0, 100).flatMap { b -> listOf(0, 100).map { st -> "custom h$h s$st b$b" to Palette.customAccent(h, st, b) } } }
        val problems = mutableListOf<String>()
        var worst = 99.0
        for (tone in TestTones.solid) for ((name, accent) in colours) {
            val drawn = Contrast.accentOn(accent, tone.key, tone.special, tone.background)
            for ((what, surface) in listOf("key" to tone.key, "special key" to tone.special, "background" to tone.background)) {
                val r = Contrast.ratio(drawn, surface); worst = minOf(worst, r)
                if (r < 3.0) problems += "${tone.name} · $name on $what: ${"%.2f".format(r)}"
            }
            val ink = Contrast.ratio(Contrast.inkFor(drawn), drawn)
            if (ink < 4.5) problems += "${tone.name} · $name words on it: ${"%.2f".format(ink)}"
        }
        println("Highlight: lowest contrast against keys or background on any theme ${"%.2f".format(worst)}")
        assertTrue(problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test fun darkThemesKeepTheColourUntouched() {
        // Only themes that need it get a deeper colour: on the dark themes every preset is drawn as chosen
        val dark = Palette.tones.filter { !it.clear && it.name != "Light" }
        val changed = dark.flatMap { t -> Palette.accents.filter { (_, a) -> Contrast.accentOn(a, t.key, t.special, t.background) != a }.map { "${t.name} · ${it.first}" } }
        assertTrue("changed on a dark theme: $changed", changed.isEmpty())
    }
}
