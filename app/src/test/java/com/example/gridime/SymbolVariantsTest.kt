package com.example.gridime

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Hold A on a symbol (1.3): its everyday relatives, the symbol itself first; letting go keeps the one you're on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class SymbolVariantsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun theListsAreShortAndUseful() {
        assertEquals(listOf("€", "$", "£", "¥", "¢", "₹"), Accents.symbolVariants('€', Language.GERMAN))
        assertEquals("the language's own money sign second", "£", Accents.symbolVariants('$', Language.ENGLISH)!![1])
        assertEquals("₺", Accents.symbolVariants('$', Language.TURKISH)!![1])
        assertEquals(listOf("-", "–", "—", "_"), Accents.symbolVariants('-', Language.ENGLISH))
        assertEquals(listOf("?", "¿"), Accents.symbolVariants('?', Language.SPANISH))
        assertNull("no made-up relatives", Accents.symbolVariants('@', Language.ENGLISH))
        for (c in "@#\$%&-+()/*\"':;!?=0€") Accents.symbolVariants(c, Language.ENGLISH)?.let { list ->
            assertEquals("$c comes first", c.toString(), list.first())
            assertEquals("$c: no repeats", list.size, list.distinct().size)
            assert(list.size <= 7) { "$c: keep it short" }
        }
    }

    @Test fun holdingAOnTheEuroOffersTheDollar() {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(context)
        var text = ""
        v.listener = object : KeyGridView.Listener {
            override fun onText(t: String) { text += t }
            override fun onDelete() {}
            override fun onEnter() {}
            override fun onTool(tool: Tool) {}
            override fun onClip(index: Int) {}
            override fun onWord(word: String) {}
            override fun onReplaceLast(expected: String, replacement: String): Boolean {
                if (!text.endsWith(expected)) return false
                text = text.dropLast(expected.length) + replacement; return true
            }
        }
        v.applySettings(KeyboardSettings(tutorialDone = true, holdAccents = true, language1 = Language.GERMAN))
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(100)
        v.toggleLayer()
        repeat(40) { if (v.stepTowards("€") == true) return@repeat }
        idle(200)
        v.pressA(); idle(900)
        v.releaseA(); idle(300)
        assertEquals("hold and let go: the first relative, as with é", "$", text)
        v.pressA(); idle(900)
        v.moveFocus(1, 0); idle(200)
        v.releaseA(); idle(300)
        assertEquals("one step along: £", "$£", text)
    }

    private fun keyboard(language: Language = Language.ENGLISH): Pair<KeyGridView, () -> String> {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(context)
        var text = ""
        v.listener = object : KeyGridView.Listener {
            override fun onText(t: String) { text += t }
            override fun onDelete() {}
            override fun onEnter() {}
            override fun onTool(tool: Tool) {}
            override fun onClip(index: Int) {}
            override fun onReplaceLast(expected: String, replacement: String): Boolean {
                if (!text.endsWith(expected)) return false
                text = text.dropLast(expected.length) + replacement; return true
            }
        }
        v.applySettings(KeyboardSettings(tutorialDone = true, holdAccents = true, language1 = language))
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(100)
        return v to { text }
    }

    /** 1.3 (Emre, 8 Nov): hold the full stop for punctuation, hold .com for other web endings. */
    @Test fun holdingTheFullStopOffersPunctuation() {
        assertEquals(".", Accents.punctuation('.')!!.first())
        val (v, text) = keyboard()
        repeat(40) { if (v.stepTowards(".") == true) return@repeat }
        v.pressA(); idle(900)
        assertEquals("the tray: the full stop and its relatives", Accents.punctuation('.')!!.size, v.accentCellsForCheck().size)
        shot(v, "punctuation")
        v.moveFocus(1, 0); idle(200)
        v.releaseA(); idle(300)
        assertEquals("one step along from the comma: the question mark", "?", text())
    }

    @Test fun holdingDotComOffersTheLanguagesEndings() {
        for (l in Language.values()) {
            val d = Accents.domains(l)
            assertEquals("$l: .com first", ".com", d.first())
            assertEquals("$l: five, one row", 5, d.size)
            assertEquals("$l: no repeats", 5, d.distinct().size)
        }
        val (v, text) = keyboard(Language.GERMAN)
        v.setField(FieldKind.URL); idle(100)
        repeat(40) { if (v.stepTowards(".com") == true) return@repeat }
        v.pressA(); idle(900)
        val cells = v.accentCellsForCheck()
        assertEquals(5, cells.size)
        shot(v, "domains")
        cells.forEach { c -> assert(c.left >= 0f && c.right <= v.width) { "every ending stays on the keyboard ($c of ${v.width})" } }
        cells.zipWithNext().forEach { (a, b) -> assert(a.right <= b.left) { "cells side by side, not overlapping" } }
        v.releaseA(); idle(300)
        assertEquals("let go at once: the first ending after .com", ".de", text())
    }

    private fun shot(v: android.view.View, name: String) {
        val dir = java.io.File("build/hold-trays").takeIf { it.exists() } ?: return
        val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(b))
        java.io.File(dir, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
