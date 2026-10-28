package com.example.gridime

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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
 * Vertical handhelds have small, squarer screens. Every screen of the app, drawn at the sizes
 * Android gives them (minus the status and navigation bars), for checking by eye:
 *  - 4:3, 4 inch, 960x720 (Anbernic RG406V): about 480x312 dp
 *  - 31:27, 3.92 inch, 1240x1080 (Retroid Pocket Classic, AYANEO Pocket DMG): about 472x363 dp
 *  - 4:3, 4.7 inch, 1280x960 (Anbernic RG477V): about 640x432 dp
 *  - 1:1, 3.95 inch, 720x720 (Anbernic RG Cube): about 480x432 dp
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallScreensTest {
    private val out = File("build/screens/small").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun save(a: Activity, name: String) {
        val v = a.window.decorView
        val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun press(a: Activity, keyCode: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        idle(250)
    }

    private fun shots(prefix: String) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.SWITCH,
            picture = PictureChoice.VERTICAL, showPreview = false).save(context)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(600)
        repeat(6) { i -> save(s, "$prefix-settings-$i"); press(s, KeyEvent.KEYCODE_BUTTON_R1); idle(400) }
        press(s, KeyEvent.KEYCODE_BUTTON_R1); press(s, KeyEvent.KEYCODE_BUTTON_R1); idle(300)   // Typing
        press(s, KeyEvent.KEYCODE_BUTTON_Y); idle(900)
        save(s, "$prefix-settings-keyboard")
        val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        repeat(tour.pageCheck().size) { i -> idle(if (i == 0) 900 else 2600); save(tour, "$prefix-tour-$i"); press(tour, KeyEvent.KEYCODE_BUTTON_A) }
        val wn = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        repeat(wn.pageCheck().size) { i -> idle(2600); save(wn, "$prefix-new-$i"); press(wn, KeyEvent.KEYCODE_BUTTON_A) }
    }

    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun fourThreeSmall() = shots("a43s")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocketClassic() = shots("b3127")
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThreeLarge() = shots("c43l")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = shots("d11")
}
