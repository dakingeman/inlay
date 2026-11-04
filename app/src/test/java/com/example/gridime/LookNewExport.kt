package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
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

/**
 * Mock-up material for "show them where it is": Settings → Look with the Theme row lit and a new theme picked,
 * drawn by the app's own code. The theme is slipped in through the wallpaper slot (after Settings has read the
 * real wallpaper), so nothing in the app changes. Touch build/visuals-split. Output: build/split/where/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LookNewExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun tone(t: Tone?) = Palette::class.java.getDeclaredField("wallpaperTone").apply { isAccessible = true }.set(Palette, t)
    private fun press(a: android.app.Activity, code: Int) {
        val t = android.os.SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    private fun run(screen: String) {
        assumeTrue(File("build/visuals-split").exists())
        val out = File("build/split/where").apply { mkdirs() }
        val brick = Tone("Grey brick", Color.parseColor("#A6A4AA"), Color.parseColor("#C4C2C7"), Color.parseColor("#62606A"))
        tone(brick)
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD,
            showPreview = false, toneIndex = Palette.tones.lastIndex).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        tone(brick)                                   // Settings read the real wallpaper on start: put the theme back
        fun shot(name: String) {
            val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            File(out, "$screen-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shot("device")
        press(a, KeyEvent.KEYCODE_BUTTON_R1); idle(1200); shot("look")
        @Suppress("UNCHECKED_CAST")
        (SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<android.view.View>).first().requestFocusFromTouch()
        idle(900); shot("look-theme")
        a.finish(); idle(200); tone(null)
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("odin")
    @Test @Config(sdk = [34], qualifiers = "w1097dp-h617dp-land-280dpi") fun hdSmall() = run("1080p-small")
}
