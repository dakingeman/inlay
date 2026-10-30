package com.example.gridime

import android.graphics.Color
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 1.2 swapped one highlight colour (Indigo out, Cherry in), which moved some colours in the list. Everyone updating
 * from 1.1 must keep exactly the colour they had; Indigo users get it back as the same custom colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccentMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val old11 = listOf("#8AB4F8", "#4FA8FF", "#72D6E8", "#3FD1B0", "#7EE6A8", "#B5E853", "#F5E46A", "#FFC857",
        "#FF9F5A", "#FF8A80", "#F48FB1", "#E27AE8", "#B39DFF", "#8C9EFF", "#D9C7A7", "#E8EAED").map { Color.parseColor(it) }

    private fun loadSaved11(index: Int): KeyboardSettings {
        SmallFile.write(context, "settings.json", JSONObject().put("accent", index).put("custom_strength", 50).toString())
        return KeyboardSettings.load(context)
    }

    @Test fun everyColourFrom11StaysTheSame() {
        for (i in old11.indices) {
            val s = loadSaved11(i)
            val (l1, c1, h1) = AccentLight.oklch(old11[i]).toList(); val (l2, c2, h2) = AccentLight.oklch(s.accent).toList()
            val dh = Math.toRadians(h2 - h1)
            val diff = Math.sqrt((l2 - l1) * (l2 - l1) + (c2 - c1) * (c2 - c1) + 4 * c1 * c2 * Math.sin(dh / 2) * Math.sin(dh / 2))
            println("1.1 colour $i -> index ${s.accentIndex}, difference $diff")
            if (i == 13) assertEquals("Indigo becomes a custom colour", -1, s.accentIndex)
            else assertEquals("1.1 colour $i keeps its exact colour", old11[i], s.accent)
            assertTrue("1.1 colour $i looks the same (OKLCH difference $diff)", diff < 0.02)
        }
    }

    @Test fun a12ChoiceIsReadAsIs() {
        SmallFile.write(context, "settings.json", JSONObject().put("accent", 13).put("accent_2", 10).toString())
        assertEquals("Cherry", Palette.accents[KeyboardSettings.load(context).accentIndex].first)
    }

    @Test fun cherryIsClearlyDifferentFromEveryOtherColour() {
        val cherry = Palette.accents.first { it.first == "Cherry" }.second
        assertEquals(16, Palette.accents.size)
        assertTrue(Palette.accents.none { it.first == "Indigo" })
        assertTrue(Palette.accents.filter { it.second != cherry }.all { Contrast.ratio(it.second, cherry) > 1.05 || it.second != cherry })
    }
}
