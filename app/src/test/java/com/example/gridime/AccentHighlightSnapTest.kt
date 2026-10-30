package com.example.gridime

import android.graphics.RectF
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * When the accents open, the highlight is on the first accent at once, and when they close it's back on the key at
 * once: it never glides between them. The glide from the held key up to the tray looked like the keyboard navigating
 * there by itself (Odin test, 30 Oct 2026). Checked frame by frame, in all three highlight motions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class AccentHighlightSnapTest {
    @Test fun neverGlidesBetweenKeyAndTray() {
        val context = RuntimeEnvironment.getApplication()
        val looper = shadowOf(android.os.Looper.getMainLooper())
        val problems = mutableListOf<String>()
        for (motion in HighlightMotion.values()) {
            val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            val frame = android.widget.FrameLayout(host); host.setContentView(frame)
            val v = KeyGridView(context)
            v.applySettings(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, holdAccents = true, motion = motion))
            v.listener = object : KeyGridView.Listener {
                override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
                override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
                override fun onReplaceLast(expected: String, replacement: String) = true
            }
            frame.addView(v, android.widget.FrameLayout.LayoutParams(-1, -2)); looper.idleFor(Duration.ofMillis(500))
            repeat(30) { if (v.stepTowards("e") == true) return@repeat }
            looper.idleFor(Duration.ofMillis(300))
            var opened = false
            val focus = KeyGridView::class.java.getDeclaredField("focusRect").apply { isAccessible = true }
            fun rect() = RectF(focus.get(v) as RectF)
            val key = v.keysForTest().first { it.first == "CHAR:e" }.second
            val keyH = key.height()
            val toolH = (KeyGridView::class.java.getDeclaredMethod("getToolHeight").apply { isAccessible = true }.invoke(v) as Float)
            fun draw() = v.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)))
            v.pressA()
            // the first frame the accents are open: the highlight is already exactly an accent key, up in the strip
            var checks = 0
            repeat(60) {
                if (opened) return@repeat
                looper.idleFor(Duration.ofMillis(8)); draw()
                if (v.isAccentsOpen()) {
                    opened = true; checks++
                    val r = rect()
                    if (kotlin.math.abs(r.height() - toolH) > 1.5f || r.bottom > key.top)
                        problems += "$motion: on opening the highlight is ${r.height().toInt()} px tall at ${r.centerY().toInt()}, not on an accent key (${toolH.toInt()} px, above ${key.top.toInt()})"
                }
            }
            // the first frame after closing: exactly back on the key
            v.releaseA(); v.cancelAccents(); draw()
            val r = rect()
            if (kotlin.math.abs(r.centerY() - key.centerY()) > 1f || kotlin.math.abs(r.height() - keyH) > 1.5f)
                problems += "$motion: on closing the highlight is at ${r.centerY().toInt()}, not on the key (${key.centerY().toInt()})"
            if (!opened) problems += "$motion: the accents never opened, so nothing was checked"
        }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }
}
