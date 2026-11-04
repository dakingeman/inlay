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
 * Mock-up material: Try your buttons on every screen shape, with the preview keyboard closed and open, so the Konami
 * reveal can be placed for each. Writes the screen and where the device picture is (and whether it shows).
 * Touch build/visuals-split. Output: build/split/matrix/<screen>-<picture>-<kb|nokb>.png/.json
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TryMatrixExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun run(screen: String) {
        assumeTrue(File("build/visuals-split").exists())
        val out = File("build/split/matrix").apply { mkdirs() }
        for (pic in listOf(PictureChoice.HANDHELD, PictureChoice.VERTICAL, PictureChoice.CONTROLLER)) for (kb in listOf(false, true)) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = pic, showPreview = false).save(context)
            val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            idle(1000)
            if (kb) { SettingsActivity::class.java.getDeclaredMethod("togglePreview").apply { isAccessible = true }.invoke(a); idle(900) }
            SettingsActivity::class.java.getDeclaredMethod("startButtonTest").apply { isAccessible = true }.invoke(a)
            idle(400)
            val p = SettingsActivity::class.java.getDeclaredField("guidePicture").apply { isAccessible = true }.get(a) as DevicePictureView
            p.held = setOf("A"); idle(400)
            val name = "$screen-${pic.name.lowercase()}-${if (kb) "kb" else "nokb"}"
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val loc = IntArray(2); p.getLocationInWindow(loc)
            val seen = android.graphics.Rect(); val vis = p.getGlobalVisibleRect(seen)
            val kbv = SettingsActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(a) as android.view.View
            val kl = IntArray(2); kbv.getLocationInWindow(kl)
            File(out, "$name.json").writeText("{\"shown\":${p.isShown && vis},\"l\":${loc[0]},\"t\":${loc[1]},\"r\":${loc[0] + p.width},\"b\":${loc[1] + p.height}," +
                "\"kl\":${kl[0]},\"kt\":${kl[1]},\"kr\":${kl[0] + kbv.width},\"kb\":${kl[1] + kbv.height},\"kshown\":${kbv.isShown}," +
                "\"vl\":${seen.left},\"vt\":${seen.top},\"vr\":${seen.right},\"vb\":${seen.bottom},\"w\":${v.width},\"h\":${v.height}}")
            if (!kb) {      // the same picture scaled up as one unit (as the app would), for the full-screen test and the reward
                val k = 4f
                for (lit in listOf("", "A", "B", "Dup", "Ddown", "Dleft", "Dright")) {
                    p.held = if (lit.isEmpty()) emptySet() else setOf(lit); idle(300)
                    val bb = Bitmap.createBitmap((p.width * k).toInt(), (p.height * k).toInt(), Bitmap.Config.ARGB_8888)
                    p.draw(Canvas(bb).apply { scale(k, k) })
                    val tag = if (lit.isEmpty()) "" else "-$lit"
                    File(out, "$screen-${pic.name.lowercase()}-big$tag.png").outputStream().use { bb.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            a.finish(); idle(200)
        }
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("odin")
    @Test @Config(sdk = [34], qualifiers = "w1097dp-h617dp-land-280dpi") fun hdSmall() = run("1080p-small")
    @Test @Config(sdk = [34], qualifiers = "w683dp-h512dp-land-xhdpi") fun fourThree() = run("4x3")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun fourThreeSmall() = run("43-small")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun phoneUpright() = run("phone-upright")
}
