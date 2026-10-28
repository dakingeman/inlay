package com.example.gridime

import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the custom colour can't be made unreadable: every combination of the three sliders
 * (colour, strength, brightness), checked where the colour is used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomColourTest {

    @Test
    fun everyCustomColourStaysReadable() {
        val problems = mutableListOf<String>()
        val backgrounds = listOf("black" to Design.SURFACE_BG, "card" to Design.SURFACE_CARD, "focused row" to Design.SURFACE_RAISED,
            "tour graphite" to Color.parseColor("#16171C"))
        var worstBg = 99.0; var worstInk = 99.0
        for (h in 0 until 360 step 5) for (st in 0..100 step 5) for (b in 0..100 step 5) {
            val c = Palette.customAccent(h, st, b)
            // Text, rings and bars in the colour, on every dark surface: at least 4.5 : 1
            backgrounds.forEach { (name, bg) ->
                val r = Contrast.ratio(c, bg); worstBg = minOf(worstBg, r)
                if (r < 4.5) problems += "h$h s$st b$b on $name: ${"%.2f".format(r)}"
            }
            // Words on the colour (a lit key, the chosen tab): the ink Inlay picks reads at least 4.5 : 1
            val ink = Contrast.ratio(Contrast.inkFor(c), c); worstInk = minOf(worstInk, ink)
            if (ink < 4.5) problems += "h$h s$st b$b ink: ${"%.2f".format(ink)}"
        }
        println("Custom colours: lowest contrast on a dark surface ${"%.2f".format(worstBg)}, lowest for words on the colour ${"%.2f".format(worstInk)}")
        assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theSlidersReallyChangeTheColour() {
        // Strength and brightness each make a visible difference at every hue (they're not decoration)
        for (h in 0 until 360 step 15) {
            val soft = Palette.customAccent(h, 0, 50); val vivid = Palette.customAccent(h, 100, 50)
            val deep = Palette.customAccent(h, 60, 0); val light = Palette.customAccent(h, 60, 100)
            fun dist(a: Int, b: Int) = Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b))
            assertTrue("hue $h: strength barely changes it", dist(soft, vivid) > 60)
            assertTrue("hue $h: brightness barely changes it", dist(deep, light) > 60)
        }
    }

    @Test
    fun anOldCustomColourLooksTheSameAfterTheUpdate() {
        // Saved by 1.1: only a hue, drawn as a soft pastel. After 1.2 it must look the same (within a hair)
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        for (old in listOf(0, 45, 120, 210, 300)) {
            val before = Color.HSVToColor(floatArrayOf(old.toFloat(), 0.48f, 0.98f))
            val file = java.io.File(ctx.filesDir, "settings.json")
            ctx.filesDir.listFiles()?.forEach { it.delete() }
            ctx.getSharedPreferences(ctx.packageName + "_preferences", 0).edit().clear().commit()
            KeyboardSettings(accentIndex = -1, customHue = old).save(ctx)
            // Turn the saved file back into a 1.1 one: no strength or brightness in it
            val saved = ctx.filesDir.walk().firstOrNull { it.isFile && it.readText().contains("custom_strength") }
            if (saved != null) {
                val json = org.json.JSONObject(saved.readText()); json.remove("custom_strength"); json.remove("custom_brightness")
                json.put("custom_hue", old); saved.writeText(json.toString())
            }
            val after = KeyboardSettings.load(ctx).accent
            val d = Math.abs(Color.red(before) - Color.red(after)) + Math.abs(Color.green(before) - Color.green(after)) + Math.abs(Color.blue(before) - Color.blue(after))
            assertTrue("old hue $old: was ${Integer.toHexString(before)}, now ${Integer.toHexString(after)}", d <= 30)
            file.exists()
        }
    }
}
