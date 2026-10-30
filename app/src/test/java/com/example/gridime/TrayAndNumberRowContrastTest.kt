package com.example.gridime

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The accent tray and the number row ("Keys" look, 30 Oct 2026) stay readable on every theme in every highlight
 * colour, custom ones included: letters and digits at 4.5 : 1, the quieter plain letter at 3 : 1 (it's a choice
 * you rarely take, and it's also the letter on the held key), the held letter's ring at 3 : 1 against its key and
 * against the dimmed keyboard around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrayAndNumberRowContrastTest {
    @Test fun everyThemeAndColour() {
        val context = RuntimeEnvironment.getApplication()
        val colours = Palette.accents.indices.map { it to null } +
            listOf(0, 60, 120, 180, 240, 300).flatMap { h -> listOf(0, 100).map { b -> -1 to (h to b) } }
        val problems = mutableListOf<String>()
        var checked = 0
        for (tone in Palette.tones.indices) for ((accent, custom) in colours) {
            val s = KeyboardSettings(toneIndex = tone, accentIndex = accent,
                customHue = custom?.first ?: 210, customBrightness = custom?.second ?: Palette.DEFAULT_BRIGHTNESS)
            val v = KeyGridView(context); v.applySettings(s)
            for ((what, ink, bg) in v.trayColoursForTest()) {
                val need = when (what) { "plain letter", "held ring on its key", "held ring on the dimmed keyboard" -> 3.0; else -> 4.5 }
                val r = Contrast.ratio(ink, bg)
                checked++
                if (r < need) problems += "${Palette.tones[tone].name} · ${if (accent >= 0) Palette.accents[accent].first else "custom h${custom!!.first} b${custom.second}"}: $what ${"%.2f".format(r)} : 1 (needs $need)"
            }
        }
        println("tray contrast: $checked pairs checked, ${problems.size} below")
        assertTrue(problems.size.toString() + " below:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
