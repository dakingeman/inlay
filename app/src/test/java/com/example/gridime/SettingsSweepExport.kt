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

/** Every Settings tab (top and bottom) and Find, on every screen shape, for the 5 Nov audit. Touch build/visuals-size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSweepExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = sweep("1-odin")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xxhdpi") fun hd7() = sweep("2-1080p")
    @Test @Config(sdk = [34], qualifiers = "w640dp-h480dp-land-xhdpi") fun fourThree() = sweep("3-4by3")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun fourThreeSmall() = sweep("4-4by3-small")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocketClassic() = sweep("5-vertical")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = sweep("6-square")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun phone() = sweep("7-phone")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi") fun tvRemote() = sweep("8-tv-remote", tv = true, remote = true)
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi") fun tvController() = sweep("9-tv-controller", tv = true)

    private fun sweep(tag: String, tv: Boolean = false, remote: Boolean = false) {
        assumeTrue(File("build/visuals-size").exists())
        val out = File("build/sweep/$tag").apply { deleteRecursively(); mkdirs() }
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false,
            tvMode = if (tv) TvMode.ON else TvMode.AUTO, picture = if (remote) PictureChoice.REMOTE else if (tv) PictureChoice.CONTROLLER else PictureChoice.AUTO).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(900)
        fun shot(n: String) { val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); File(out, "$n.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
        fun press(k: Int, wait: Long = 400) { val t = SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)); idle(wait) }
        val sv = SettingsActivity::class.java.getDeclaredField("scroll").apply { isAccessible = true }.get(a) as android.widget.ScrollView
        val tabs = (SettingsActivity::class.java.getDeclaredField("pages\$delegate").apply { isAccessible = true }.get(a) as Lazy<*>).value as List<*>
        for (i in tabs.indices) {
            a.tabsForCheck(i); idle(700); sv.scrollTo(0, 0); idle(200); shot("tab$i-top")
            val end = (sv.getChildAt(0).height - sv.height).coerceAtLeast(0)
            if (end > 0) { sv.scrollTo(0, end / 2); idle(200); shot("tab$i-mid"); sv.scrollTo(0, end); idle(200); shot("tab$i-end") }
        }
        if (!remote) {
            a.tabsForCheck(0); idle(500)
            press(KeyEvent.KEYCODE_BUTTON_START, 1200); shot("find-1-open")
            val kb = SettingsActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(a) as KeyGridView
            for (ch in "vib") { var g = 0; while (kb.stepTowards(ch.toString()) == false && g++ < 40) idle(5); press(KeyEvent.KEYCODE_BUTTON_A, 150) }
            idle(400); shot("find-2-typed")
            press(KeyEvent.KEYCODE_BUTTON_B, 1000); shot("find-3-browse")
            press(KeyEvent.KEYCODE_BUTTON_A, 1800); shot("find-4-went")
        }
    }
}
