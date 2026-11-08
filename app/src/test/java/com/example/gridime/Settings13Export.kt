package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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

/** Pictures of the 1.3 Settings changes, drawn by the app (mock-up material). Touch build/visuals-settings13. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class Settings13Export {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val out = File("build/settings13").apply { mkdirs() }
    private fun shot(a: android.app.Activity, name: String) {
        val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b)); File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun press(a: android.app.Activity, key: Int, wait: Long = 150) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0)); idle(wait)
    }
    @Suppress("UNCHECKED_CAST")
    private fun rows(a: SettingsActivity) = SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<SettingRow>

    @Test fun export() {
        assumeTrue(File("build/visuals-settings13").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false, retroUnlocked = true,
            toneIndex = Palette.RETRO_BASE).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        rows(a).first().requestFocusFromTouch(); idle(300)
        shot(a, "1-device-top")
        rows(a).first { it.rowTitle == "Try your buttons" }.requestFocus(); idle(600); shot(a, "2-device-try-row")
        rows(a).first { it.rowTitle == "Button icons" }.requestFocus(); idle(600); shot(a, "3-device-button-icons")
        press(a, KeyEvent.KEYCODE_BUTTON_R1, 900)
        rows(a).first { it.rowTitle == "Highlight colour" }.requestFocus(); idle(900); shot(a, "11-look-highlight-match")
        rows(a).first { it.rowTitle == "Highlight style" }.requestFocus(); idle(900); shot(a, "12-look-style-match")
        press(a, KeyEvent.KEYCODE_BUTTON_L1, 900); rows(a).first().requestFocus(); idle(300)
        press(a, KeyEvent.KEYCODE_BUTTON_START, 1200); shot(a, "4-find-empty")
        press(a, KeyEvent.KEYCODE_BUTTON_B, 1200); press(a, KeyEvent.KEYCODE_DPAD_DOWN, 300); shot(a, "4b-find-browsing")
        press(a, KeyEvent.KEYCODE_BUTTON_Y, 1200)
        val kb = SettingsActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(a) as KeyGridView
        for (ch in "clea") { var g = 0; while (kb.stepTowards(ch.toString()) == false && g++ < 40) idle(5); press(a, KeyEvent.KEYCODE_BUTTON_A) }
        idle(400); shot(a, "5-find-clea")
        press(a, KeyEvent.KEYCODE_BUTTON_START, 1500); shot(a, "6-find-went-there")
        press(a, KeyEvent.KEYCODE_BUTTON_R1, 900)
        rows(a).first { it.rowTitle == "Sound pack" }.requestFocus(); idle(900)
        shot(a, "7-sound-retro")
        press(a, KeyEvent.KEYCODE_BUTTON_R1, 900); shot(a, "8-guide-top")
        val gs = SettingsActivity::class.java.getDeclaredField("scroll").apply { isAccessible = true }.get(a) as android.widget.ScrollView
        gs.scrollTo(0, gs.height * 2 / 3); idle(400); shot(a, "8b-guide-middle")
        gs.scrollTo(0, gs.height * 4 / 3); idle(400); shot(a, "8c-guide-lower")
        press(a, KeyEvent.KEYCODE_BUTTON_L1, 900); press(a, KeyEvent.KEYCODE_BUTTON_L1, 900); press(a, KeyEvent.KEYCODE_BUTTON_L1, 900); press(a, KeyEvent.KEYCODE_BUTTON_L1, 900)
        val call = SettingsActivity::class.java.getDeclaredMethod("startButtonTest").apply { isAccessible = true }
        call.invoke(a); idle(900); shot(a, "13-try-start")
        val t = SystemClock.uptimeMillis()
        fun down(k: Int) = a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0))
        fun up(k: Int) = a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0))
        down(KeyEvent.KEYCODE_BUTTON_X); idle(300); shot(a, "9-try-x"); up(KeyEvent.KEYCODE_BUTTON_X); idle(200)
        down(KeyEvent.KEYCODE_BUTTON_A); idle(300); shot(a, "14-try-a"); up(KeyEvent.KEYCODE_BUTTON_A); idle(200)
        down(KeyEvent.KEYCODE_BUTTON_R2); idle(300); shot(a, "15-try-rt-held")
        down(KeyEvent.KEYCODE_BUTTON_A); idle(300); shot(a, "16-try-rt-a"); up(KeyEvent.KEYCODE_BUTTON_A); up(KeyEvent.KEYCODE_BUTTON_R2); idle(200)
        down(KeyEvent.KEYCODE_BUTTON_THUMBR); idle(300); shot(a, "10-try-r3")
    }
}
