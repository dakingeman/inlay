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

/** Tour pages on the screens the scaling work is judged on, for before/after review. Touch build/visuals-scale. Output: build/scale/<label>/ */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourScaleShots {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun run(screen: String, pic: PictureChoice = PictureChoice.HANDHELD) {
        assumeTrue(File("build/visuals-scale").exists())
        val label = File("build/visuals-scale").readText().trim().ifEmpty { "now" }
        val dir = File("build/scale/$label/$screen").apply { mkdirs() }
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = pic).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java)).setup().get()
        repeat(t.pageCheck().size) { i ->
            idle(if (i == 0) 2500 else 2800)
            val v = t.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            File(dir, "tour-$i.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val now = SystemClock.uptimeMillis()
            t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
        }
        t.finish()
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("odin")
    @Test @Config(sdk = [34], qualifiers = "w1097dp-h617dp-land-280dpi") fun hdSmall() = run("1080p-small")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi") fun hdDefault() = run("1080p-default")
    @Test @Config(sdk = [34], qualifiers = "w683dp-h512dp-land-xhdpi") fun fourThree() = run("4x3")
}
