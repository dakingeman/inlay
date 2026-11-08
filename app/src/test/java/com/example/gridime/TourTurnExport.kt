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

/** The real tour's pages, for the "Your turn" sketch (mock-up material). Touch build/visuals-settings13. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class TourTurnExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @Test fun export() {
        assumeTrue(File("build/visuals-settings13").exists())
        val out = File("build/settings13").apply { mkdirs() }
        KeyboardSettings(whatsNewSeen = WhatsNew.VERSION).save(context)
        val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(1500)
        fun shot(n: String) { val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); File(out, "$n.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        fun press(k: Int) { val t = SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)); idle(2500) }
        shot("tour-1")
        press(KeyEvent.KEYCODE_BUTTON_A); idle(3000); shot("tour-2")
        press(KeyEvent.KEYCODE_BUTTON_A); idle(3000); shot("tour-3")
    }
}
