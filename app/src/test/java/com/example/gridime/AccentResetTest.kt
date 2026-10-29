package com.example.gridime

import android.os.Looper
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
 * When the accents are cleared at once (the keyboard closes, a new text box, the tour moving on), the highlight
 * goes straight back to its key: nothing is left drawn where the accents were.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class AccentResetTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun theHighlightComesBackToItsKey() {
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
        v.applySettings(KeyboardSettings(tutorialDone = true, holdAccents = true))
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(100)
        // Drawn as the screen would be (the highlight starts on the first frame)
        fun frame() { val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(b)); b.recycle() }
        frame()
        repeat(30) { if (v.stepTowards("n") == true) return@repeat }
        idle(200); frame()
        for (when_ in listOf("while open", "while rising")) {
            v.pressA(); idle(if (when_ == "while open") 800 else 250); frame()
            if (when_ == "while open") { v.moveFocus(1, 0); idle(200); frame() }
            v.resetAccents(); idle(16); frame()
            val drawn = v.focusRectForCheck(); val key = v.focusTargetForCheck()
            assertTrue("$when_: the highlight is at $drawn, its key at $key",
                kotlin.math.abs(drawn.centerX() - key.centerX()) < 1f && kotlin.math.abs(drawn.centerY() - key.centerY()) < 1f)
            v.releaseA(); idle(300)
        }
    }
}
