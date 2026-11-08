package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Pin a clip (1.3): Y on a clip keeps it. Pinned clips sit first and never drop off, at most four, so two places
 * always stay for new copies. On every screen the pin sits inside its card, never on the words, and the guide's
 * chips (A Paste, Y Pin, X Remove, B Back) all fit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipPinTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test @Config(sdk = [34]) fun pinsStayAndNeverDropOff() {
        ClipboardHistory.clear(context)
        val h = ClipboardHistory(context).apply { reload() }
        listOf("a", "b", "c", "d").forEach { h.add(it) }                    // newest first: d c b a
        assertEquals(ClipboardHistory.Pin.PINNED, h.togglePin(2))           // b
        assertEquals(listOf("b", "d", "c", "a"), h.all); assertEquals(1, h.pinnedCount)
        listOf("e", "f", "g", "h", "i", "j", "k").forEach { h.add(it) }    // seven new copies
        assertEquals("the pin stays at the top", "b", h.all.first())
        assertEquals(Layouts.MAX_CLIP_CARDS, h.all.size)
        h.add("b")                                                           // copying a pinned clip again changes nothing
        assertEquals("b", h.all.first()); assertEquals(1, h.pinnedCount)
        // up to four pins, then Y says no
        repeat(3) { assertEquals(ClipboardHistory.Pin.PINNED, h.togglePin(h.pinnedCount)) }
        assertEquals(4, h.pinnedCount)
        assertEquals(ClipboardHistory.Pin.FULL, h.togglePin(5))
        h.add("new"); assertEquals("new copies still come in under the pins", "new", h.all[4])
        // unpinning puts it just under the pins; removing a pin counts it down
        val first = h.all[0]
        assertEquals(ClipboardHistory.Pin.UNPINNED, h.togglePin(0))
        assertEquals(3, h.pinnedCount); assertEquals(first, h.all[3])
        h.removeAt(0); assertEquals(2, h.pinnedCount)
        // it survives a reload (the keyboard and Settings read the file fresh)
        val again = ClipboardHistory(context).apply { reload() }
        assertEquals(h.all, again.all); assertEquals(2, again.pinnedCount)
    }

    @Test @Config(sdk = [34]) fun theOldFileStillReads() {
        SmallFile.write(context, "clipboard.json", "[\"one\",\"two\"]")
        val h = ClipboardHistory(context).apply { reload() }
        assertEquals(listOf("one", "two"), h.all); assertEquals(0, h.pinnedCount)
    }

    private fun card(name: String, split: Boolean = false) {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(context)
        v.applySettings(KeyboardSettings(tutorialDone = true, split = split))
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(100)
        v.setClips(listOf("github.com/dakingeman/inlay is the address you asked for", "jet set radio future",
            "conker live and reloaded", "ninja gaiden black", "my.email@example.com", "Super Metroid"), pinned = 2)
        v.toggleClips(); idle(300)
        val bad = mutableListOf<String>()
        for (on in listOf(0, 3)) {                                          // on a pinned clip (Unpin), then a plain one (Pin)
            v.focusClip(on); idle(300)
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            val log = mutableListOf<Pair<String, RectF>>(); v.drawLog = log; v.draw(Canvas(b)); v.drawLog = null
            File("build/clippins").mkdirs(); File("build/clippins/$name-on$on.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fun r(tag: String) = log.lastOrNull { it.first == tag }?.second
            for (i in 0 until 2) {
                val pin = r("pin:$i") ?: run { bad += "clip $i has no pin"; continue }
                val card = r("card:$i")!!; val words = r("clip:$i")!!
                if (!card.contains(pin)) bad += "pin $i leaves its card"
                if (RectF.intersects(pin, words)) bad += "pin $i sits on the words"
            }
            if (r("pin:3") != null) bad += "an unpinned clip has a pin"
            val (shown, wanted) = v.clipGuideForCheck
            if (shown < wanted) bad += "on clip $on only $shown of $wanted guide chips fit"
        }
        assertTrue("$name: " + bad.joinToString("; "), bad.isEmpty())
        assertFalse(bad.isNotEmpty())
    }
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odinReal() = card("odinreal")
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odinSplit() = card("odinsplit", split = true)
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = card("small43")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun vertical() = card("vertical")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun upright() = card("upright")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = card("tv")
}
