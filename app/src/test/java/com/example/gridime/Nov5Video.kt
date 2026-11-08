package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assume.assumeTrue
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

/** Screen recordings for Emre (frames at 20 a second, joined with ffmpeg). Touch build/visuals-video5. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class Nov5Video {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private class Rec(val a: android.app.Activity, val dir: File) {
        var n = 0
        fun frame() {
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            File(dir, "f%05d.png".format(n++)).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
    }
    private val step = 50L
    private fun Rec.hold(ms: Long) { repeat((ms / step).toInt()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step)); frame() } }
    private fun Rec.down(k: Int) { val t = SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)) }
    private fun Rec.up(k: Int) { val t = SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)) }
    private fun Rec.tap(k: Int, after: Long = 700) { down(k); hold(100); up(k); hold(after) }
    private fun dir(n: String) = File("build/video5/$n").apply { deleteRecursively(); mkdirs() }

    @Test fun tour() {
        assumeTrue(File("build/visuals-video5").exists())
        TutorialActivity.yourTurn = true
        try {
            KeyboardSettings(whatsNewSeen = WhatsNew.VERSION).save(context)
            val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(1200)
            val r = Rec(a, dir("1-tour"))
            r.hold(1500)
            r.tap(KeyEvent.KEYCODE_BUTTON_A, 1800)          // to Move and type: your turn straight away
            r.tap(KeyEvent.KEYCODE_BUTTON_X, 1800)          // the wrong button
            r.tap(KeyEvent.KEYCODE_DPAD_RIGHT, 400); r.tap(KeyEvent.KEYCODE_DPAD_RIGHT, 400)
            r.tap(KeyEvent.KEYCODE_BUTTON_A, 2000)          // types, Nice, next page
            r.hold(2000)                                    // Capitals: your turn
            r.tap(KeyEvent.KEYCODE_BUTTON_R2, 2200)         // capitals, Nice, next
            r.hold(1500)                                    // Hold RT
            r.down(KeyEvent.KEYCODE_BUTTON_R2); r.hold(1400); r.up(KeyEvent.KEYCODE_BUTTON_R2); r.hold(2000)
            r.hold(5500)                                    // Accents: wait, the clip plays as help
            r.hold(4000)
        } finally { TutorialActivity.yourTurn = false }
    }

    @Test fun sizeSlide() {
        assumeTrue(File("build/visuals-video5").exists())
        KeyboardSettings(whatsNewSeen = WhatsNew.VERSION).save(context)
        val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(1200)
        val r = Rec(a, dir("2-size"))
        val at = a.pageCheck().indexOfFirst { it.first.startsWith("Capitals") }
        repeat(at) { r.down(KeyEvent.KEYCODE_BUTTON_A); r.up(KeyEvent.KEYCODE_BUTTON_A); idle(300) }
        r.down(KeyEvent.KEYCODE_DPAD_DOWN); r.up(KeyEvent.KEYCODE_DPAD_DOWN); idle(200)
        r.down(KeyEvent.KEYCODE_DPAD_DOWN); r.up(KeyEvent.KEYCODE_DPAD_DOWN)
        r.hold(9000)
    }

    @Test fun settings() {
        assumeTrue(File("build/visuals-video5").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(900)
        @Suppress("UNCHECKED_CAST")
        fun rows() = SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<SettingRow>
        val r = Rec(a, dir("3-settings"))
        r.hold(1500)
        r.tap(KeyEvent.KEYCODE_BUTTON_R1, 900); r.tap(KeyEvent.KEYCODE_BUTTON_R1, 900); r.tap(KeyEvent.KEYCODE_BUTTON_L1, 900); r.tap(KeyEvent.KEYCODE_BUTTON_L1, 900)
        // Your buttons: A opens the setup; left, top, right, bottom, then Select and Close; then try one and finish
        val row = rows().first { it.rowTitle == ButtonRoles.ROW }
        row.requestFocusFromTouch()
        val sv = SettingsActivity::class.java.getDeclaredField("scroll").apply { isAccessible = true }.get(a) as android.widget.ScrollView
        val box = android.graphics.Rect(); row.getDrawingRect(box); sv.offsetDescendantRectToMyCoords(row, box)
        val to = (box.top - sv.height / 3).coerceAtLeast(0)
        repeat(12) { k -> sv.scrollTo(0, sv.scrollY + (to - sv.scrollY) / (12 - k)); r.hold(50) }
        r.hold(1000)
        r.tap(KeyEvent.KEYCODE_BUTTON_A, 1500)
        for (k in listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A)) r.tap(k, 1000)
        r.tap(KeyEvent.KEYCODE_BUTTON_A, 1400); r.tap(KeyEvent.KEYCODE_BUTTON_B, 1800)
        r.tap(KeyEvent.KEYCODE_BUTTON_X, 1400); r.tap(KeyEvent.KEYCODE_BUTTON_A, 2500)
    }
}
