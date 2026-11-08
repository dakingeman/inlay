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
    /** The secret's teaser page in motion at 60 fps (touch build/visuals-teaser). Output: build/teaser/<screen>/ */
    private fun teaser(screen: String, pic: PictureChoice) {
        assumeTrue(File("build/visuals-teaser").exists())
        val dir = File("build/teaser/$screen").apply { deleteRecursively(); mkdirs() }
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = pic).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        repeat(t.pageCheck().size - 1) { idle(600); val now = SystemClock.uptimeMillis()
            t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0)) }
        val v = t.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        for (f in 0 until 60 * 7) { b.eraseColor(0); v.draw(c); File(dir, "%04d.png".format(f)).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; idle(16) }
        t.finish()
    }
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun teaserOdin() = teaser("odinreal", PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun teaserController() = teaser("controller", PictureChoice.CONTROLLER)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("odin", PictureChoice.HANDHELD, ButtonStyle.XBOX)
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odinReal() = run("odinreal", PictureChoice.HANDHELD, ButtonStyle.XBOX)
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocket() = run("pocket", PictureChoice.VERTICAL, ButtonStyle.SWITCH)
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = run("tv", PictureChoice.REMOTE, ButtonStyle.XBOX)
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = run("small43", PictureChoice.HANDHELD, ButtonStyle.XBOX)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone() = run("phone", PictureChoice.CONTROLLER, ButtonStyle.PLAYSTATION)
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun upright() = run("upright", PictureChoice.CONTROLLER, ButtonStyle.XBOX)
    @Test @Config(sdk = [34], qualifiers = "w620dp-h540dp-land-xhdpi") fun square() = run("square", PictureChoice.VERTICAL, ButtonStyle.XBOX, sticks = true)
}
