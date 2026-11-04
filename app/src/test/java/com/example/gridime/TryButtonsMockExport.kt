package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
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

/**
 * Mock-up material for entering the Konami Code in Settings → Device → Try your buttons: the real screen, with each
 * button lit on the device picture, one at a time, and all at once. Touch build/visuals-split. Output: build/split/try/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class TryButtonsMockExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private val out = File("build/split/try").apply { mkdirs() }
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun export() = run(PictureChoice.HANDHELD, out)
    @Test fun about() {
        assumeTrue(File("build/visuals-split").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD, showPreview = false).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(1000)
        repeat(5) { val t = android.os.SystemClock.uptimeMillis()
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_R1, 0))
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_R1, 0)); idle(500) }
        idle(800)
        val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
        File("build/split/about.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun vertical() = run(PictureChoice.VERTICAL, File("build/split/try-vertical").apply { mkdirs() })
    @Test fun controller() = run(PictureChoice.CONTROLLER, File("build/split/try-controller").apply { mkdirs() })

    private fun run(choice: PictureChoice, out: File) {
        assumeTrue(File("build/visuals-split").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX,
            picture = choice, showPreview = false).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(1200)
        SettingsActivity::class.java.getDeclaredMethod("startButtonTest").apply { isAccessible = true }.invoke(a)
        idle(400)
        val pic = SettingsActivity::class.java.getDeclaredField("guidePicture").apply { isAccessible = true }.get(a) as DevicePictureView
        fun shot(name: String, lit: Set<String>) {
            pic.held = lit; pic.holding = emptySet(); idle(500)
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shot("none", emptySet())
        val all = listOf("LT", "LB", "Dup", "Dright", "Ddown", "Dleft", "LS", "SELECT", "START", "RS", "X", "Y", "B", "A", "RB", "RT")
        all.forEach { shot("one-$it", setOf(it)) }
        shot("all", all.toSet())
        // the picture's box on screen, for placing things around it
        val loc = IntArray(2); pic.getLocationInWindow(loc)
        File(out, "picture.json").writeText("{\"l\":${loc[0]},\"t\":${loc[1]},\"r\":${loc[0] + pic.width},\"b\":${loc[1] + pic.height}}")
    }
}
