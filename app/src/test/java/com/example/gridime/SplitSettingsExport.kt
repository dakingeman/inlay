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

/** Settings with Split on and the keyboard up, as on the Odin. Touch build/visuals-size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class SplitSettingsExport {
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @Test fun export() {
        assumeTrue(File("build/visuals-size").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, split = true).save(RuntimeEnvironment.getApplication())
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 0)); idle(1500)
        val root = a.window.decorView
        val bm = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(bm))
        File("build/split-settings.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
