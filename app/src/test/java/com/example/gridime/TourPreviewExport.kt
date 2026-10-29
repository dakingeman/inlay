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

/** Every tour and What's new page as the template draws it, for review. Touch build/visuals-tour. Output: build/tour/<screen>/ */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourPreviewExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun run(screen: String, pic: PictureChoice, style: ButtonStyle, sticks: Boolean = false) {
        assumeTrue(File("build/visuals-tour").exists())
        val dir = File("build/tour/$screen").apply { mkdirs() }
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = pic, verticalSticks = sticks).save(context)
        for (wn in listOf(false, true)) {
            val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, wn)).setup().get()
            repeat(t.pageCheck().size) { i ->
                idle(if (i == 0) 2500 else 2800)
                val v = t.window.decorView
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
                File(dir, "${if (wn) "whatsnew" else "tour"}-$i.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val now = SystemClock.uptimeMillis()
                t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
            }
            t.finish()
        }
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("odin", PictureChoice.HANDHELD, ButtonStyle.XBOX)
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocket() = run("pocket", PictureChoice.VERTICAL, ButtonStyle.SWITCH)
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = run("tv", PictureChoice.REMOTE, ButtonStyle.XBOX)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone() = run("phone", PictureChoice.CONTROLLER, ButtonStyle.PLAYSTATION)
}
