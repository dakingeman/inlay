package com.example.gridime

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Hold LT for one symbol (1.3, MaestroLiendre): A while held types it and letting go comes back. A tap is unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class QuickSymbolTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private var text = ""

    private fun grid(): KeyGridView {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(context)
        v.listener = object : KeyGridView.Listener {
            override fun onText(t: String) { text += t }
            override fun onDelete() {}
            override fun onEnter() {}
            override fun onTool(tool: Tool) {}
            override fun onClip(index: Int) {}
            override fun onWord(word: String) {}
            override fun onReplaceLast(expected: String, replacement: String) = false
        }
        v.applySettings(KeyboardSettings(tutorialDone = true))
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(100)
        return v
    }

    @Test fun holdTypeLetGoComesBackToTheSameKey() {
        val v = grid()
        repeat(30) { if (v.stepTowards("g") == true) return@repeat }
        idle(200)
        val row = v.focusRowForCheck(); val col = v.focusColForCheck()
        v.quickLayerStart()
        assertEquals("SYMBOLS", v.layerForCheck())
        v.pressA(); v.releaseA(); idle(100)
        v.quickLayerEnd(used = true); idle(200)
        assertEquals("one symbol typed", 1, text.length)
        assertEquals("back on the letters", "LETTERS", v.layerForCheck())
        assertEquals("on the same key", row to col, v.focusRowForCheck() to v.focusColForCheck())
    }

    @Test fun aTapStillSwapsTheLayer() {
        val v = grid()
        v.quickLayerStart(); v.quickLayerEnd(used = false)
        assertEquals("a tap stays on the symbols", "SYMBOLS", v.layerForCheck())
        v.quickLayerStart(); v.quickLayerEnd(used = false)
        assertEquals("and a second tap goes back", "LETTERS", v.layerForCheck())
    }
}
