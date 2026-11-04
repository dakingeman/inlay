package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
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

/**
 * Mock-up material for the Konami Code ideas, drawn by the app's own code: a hidden theme on the keyboard, and the
 * Theme row in Settings with a locked "? ? ?" entry or the unlocked theme. The theme is slipped in through the
 * wallpaper slot, so nothing in the app changes. Touch build/visuals-split. Output: build/split/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class KonamiMockExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private val out = File("build/split").apply { mkdirs() }
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun save(v: View, name: String) {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun extraTone(t: Tone?) = Palette::class.java.getDeclaredField("wallpaperTone").apply { isAccessible = true }.set(Palette, t)

    private val hidden = mapOf(
        // 16-bit console grey: light grey keys, the darker grey of the shell, lavender-grey special keys
        "console" to Tone("Console grey", Color.parseColor("#8E8C96"), Color.parseColor("#D6D4DC"), Color.parseColor("#6F6A80")),
        // Cartridge: dark ridged plastic, keys a step lighter, a fine edge like a moulded seam
        "cartridge" to Tone("Cartridge", Color.parseColor("#101012"), Color.parseColor("#232326"), Color.parseColor("#18181B"), edge = Color.parseColor("#3A3A40")),
        // Pixel: pure black with hard white edges, like an old screen
        "pixel" to Tone("Pixel", Color.BLACK, Color.parseColor("#0A0A0A"), Color.BLACK, edge = Color.parseColor("#E8E8E8"))
    )

    private fun keyboard(name: String, s: KeyboardSettings) {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(host)
        v.applySettings(s); v.setField(FieldKind.TEXT)
        v.listener = object : KeyGridView.Listener {
            override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
            override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
            override fun onReplaceLast(expected: String, replacement: String) = true
        }
        val dp = context.resources.displayMetrics.density
        frame.addView(v, android.widget.FrameLayout.LayoutParams((827 * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        idle(300); v.focusForTest(2, 4); v.invalidate(); idle(900)
        save(v, name)
    }

    private fun settingsLook(name: String, s: KeyboardSettings) {
        s.save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(800)
        val t = android.os.SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1, 0))
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R1, 0))
        idle(1200)
        save(a.window.decorView, name)
        a.finish()
    }

    @Test fun export() {
        assumeTrue(File("build/visuals-split").exists())
        val base = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX,
            showHints = true, picture = PictureChoice.HANDHELD, showPreview = false)
        for ((key, tone) in hidden) {
            extraTone(tone)
            val s = base.copy(toneIndex = Palette.tones.lastIndex)
            keyboard("theme-$key", s)
            settingsLook("look-$key", s)
        }
        extraTone(Tone("? ? ?", Color.BLACK, Color.parseColor("#1B1B1F"), Color.parseColor("#111114")))
        settingsLook("look-locked", base.copy(toneIndex = Palette.tones.lastIndex))
        extraTone(null)
        settingsLook("look-normal", base)
    }
}
