package com.example.gridime

import android.content.Intent
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

/** Films tour and What's new pages as they play, for review by eye. Touch build/visuals-film. Output: build/film/<name>/f000.png… */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourFilmExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun film(name: String, pic: PictureChoice, style: ButtonStyle, whatsNew: Boolean, page: Int, seconds: Int, fps: Int = 12, accent: Int = 2, sticks: Boolean = false) {
        assumeTrue(File("build/visuals-film").exists() || File("build/visuals-beats").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = pic, accentIndex = accent, verticalSticks = sticks).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
        repeat(page) { idle(200); val n = SystemClock.uptimeMillis(); t.dispatchKeyEvent(KeyEvent(n, n, 0, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(n, n, 1, KeyEvent.KEYCODE_BUTTON_A, 0)) }
        val dir = File("build/film/$name").apply { deleteRecursively(); mkdirs() }
        // Real time: each frame is taken at its own moment from the start (drawing can move the test clock on a little)
        val t0 = SystemClock.uptimeMillis()
        val times = mutableListOf<Long>()
        for (f in 0 until seconds * fps) {
            (t0 + f * 1000L / fps - SystemClock.uptimeMillis()).let { if (it > 0) idle(it) }
            val v = t.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            File(dir, "f%03d.png".format(f)).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle(); times += SystemClock.uptimeMillis() - t0
        }
        File(dir, "times.txt").writeText(times.joinToString("\n"))   // when each frame was taken (ms), for a true-speed GIF
        t.finish()
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() {
        film("odin-type", PictureChoice.HANDHELD, ButtonStyle.XBOX, false, 1, 14)
        film("odin-ps-coral", PictureChoice.CONTROLLER, ButtonStyle.PLAYSTATION, false, 1, 24, accent = 9)
    }
    /** The D-pad on the device picture against the keyboard's steps, frame by frame (Odin test, 30 Oct 2026). */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun dpadSync() = film("odin-dpad", PictureChoice.HANDHELD, ButtonStyle.XBOX, false, 1, 7, fps = 30)
    /** Every tour page with rows, for checking the beats by eye (touch build/visuals-beats). */
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun beatsPocket() = beats("pocket")
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun beatsOdin() = beats("odin")
    private fun beats(screen: String) {
        assumeTrue(File("build/visuals-beats").exists())
        val only = File("build/visuals-beats").readText().trim().split(' ').mapNotNull { it.toIntOrNull() }.ifEmpty { (1..5).toList() }
        for (page in only) film("beats-$screen-$page", PictureChoice.VERTICAL, ButtonStyle.XBOX, false, page, 24, fps = 12, accent = 2, sticks = true)
        if (screen == "pocket") film("beats-pocket-handheld-1", PictureChoice.HANDHELD, ButtonStyle.PLAYSTATION, false, 1, 8, fps = 12, accent = 9)
        if (screen == "pocket") film("beats-pocket-remote-1", PictureChoice.REMOTE, ButtonStyle.XBOX, false, 1, 8, fps = 12, accent = 5)
        if (only.size == 5) film("beats-$screen-wn-accents", PictureChoice.VERTICAL, ButtonStyle.XBOX, true, 2, 18, fps = 12, accent = 2, sticks = true)
    }
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocket() {
        film("pocket-switch-lime", PictureChoice.VERTICAL, ButtonStyle.SWITCH, false, 1, 24, accent = 5)
        film("pocket-xbox-orchid", PictureChoice.VERTICAL, ButtonStyle.XBOX, false, 1, 24, accent = 11)
    }
}
