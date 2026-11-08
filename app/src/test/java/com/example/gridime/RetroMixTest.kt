package com.example.gridime

import android.graphics.Color
import android.os.Looper
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Mixing on the Retro themes (Emre's Odin test, 4 Nov):
 * - your own highlight colour really shows on every retro theme (on Grey brick and Indigo every colour came out white),
 *   keeps its hue and stands out 3:1 on the letter keys and the ground
 * - the Match theme dot is the theme you're on, straight after switching between retro themes (it kept the old one)
 * - Match theme on Button icons reaches Settings too, not just the keys
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class RetroMixTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun yourColourShowsOnEveryRetroTheme() {
        val problems = mutableListOf<String>()
        Palette.retro.forEachIndexed { t, tone ->
            val shown = Palette.accents.indices.map { i ->
                val v = KeyGridView(context)
                v.applySettings(KeyboardSettings(toneIndex = Palette.RETRO_BASE + t, themeHighlight = false, themeStyle = false, accentIndex = i))
                KeyGridView::class.java.getDeclaredField("colAccent").apply { isAccessible = true }.getInt(v)
            }
            if (shown.distinct().size < Palette.accents.size - 2) problems += "${tone.name}: only ${shown.distinct().size} of ${Palette.accents.size} colours look different"
            shown.forEachIndexed { i, c ->
                if (Contrast.ratio(c, tone.key) < 3.0) problems += "${tone.name} / ${Palette.accents[i].first}: ${"%.2f".format(Contrast.ratio(c, tone.key))}:1 on the keys"
                if (Contrast.ratio(c, tone.background) < 3.0) problems += "${tone.name} / ${Palette.accents[i].first}: ${"%.2f".format(Contrast.ratio(c, tone.background))}:1 on the ground"
                val hue = { x: Int -> FloatArray(3).also { Color.colorToHSV(x, it) }[0] }
                val want = Palette.accents[i].second
                val sat = FloatArray(3).also { Color.colorToHSV(want, it) }[1]
                if (sat > 0.15f) { val d = kotlin.math.abs(hue(c) - hue(want)).let { minOf(it, 360 - it) }
                    if (d > 25f) problems += "${tone.name} / ${Palette.accents[i].first}: hue moved ${d.toInt()}°" }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun open(s: KeyboardSettings): SettingsActivity {
        s.save(context); val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(600); return a
    }

    @Test fun matchThemeDotFollowsTheTheme() {
        val a = open(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false, retroUnlocked = true, toneIndex = Palette.RETRO_BASE))
        val pick = SettingsActivity::class.java.getDeclaredMethod("pickTheme", Int::class.java).apply { isAccessible = true }
        a.tabsForCheck(1); idle(600)
        for (t in listOf(1, 2, 0)) {
            pick.invoke(a, Palette.RETRO_BASE + t); idle(600)
            @Suppress("UNCHECKED_CAST")
            val rows = SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<View>
            val sw = rows.filterIsInstance<SwatchRow>().first()
            assertEquals("Match theme dot on ${Palette.retro[t].name}", Palette.retro[t].ownHighlight, sw.leadColourForCheck)
        }
    }

    @Test fun matchThemeIconsReachSettings() {
        open(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false, retroUnlocked = true,
            toneIndex = Palette.RETRO_BASE, buttonStyle = ButtonStyle.XBOX, themeIcons = true))
        assertEquals(ButtonStyle.PLAYSTATION, SettingRow.buttonStyle)
    }
}
