package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * Pictures of Split as the app draws it (mock-up material, not a check). The keyboard view on its own, at the
 * screen's real width, over a flat stand-in for the app behind (a plain dark grey, labelled as such where shown).
 * Touch build/visuals-split to run. Output: build/split-preview/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplitPreviewExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val out = File("build/split-preview").apply { mkdirs() }

    private fun shot(name: String, s: KeyboardSettings, behind: Int = Color.parseColor("#2A2C33"), act: (KeyGridView) -> Unit = {}) {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(host); v.applySettings(s); v.setField(FieldKind.TEXT)
        v.listener = object : KeyGridView.Listener {
            override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
            override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
            override fun onReplaceLast(expected: String, replacement: String) = true
        }
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(300); act(v); idle(1500)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); b.eraseColor(behind)
        v.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val base = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, split = true)
    private fun focus(v: KeyGridView, l: String) { repeat(40) { if (v.stepTowards(l) == true) return } }

    /** For the 1.3 release cover: the Split keyboard alone, big and crisp (4x), nothing behind it, no badges (8 Nov). */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xxxhdpi")
    fun cover() {
        assumeTrue(File("build/visuals-split").exists())
        shot("cover-split", base.copy(showHints = false), Color.TRANSPARENT) { focus(it, "g") }
        shot("cover-split-hints", base, Color.TRANSPARENT) { focus(it, "g") }
        shot("cover-split-icons", base.copy(showHints = false, toolStyle = 1), Color.TRANSPARENT) { focus(it, "g") }
        shot("cover-split-both", base.copy(showHints = false, toolStyle = 2), Color.TRANSPARENT) { focus(it, "g") }
        shot("cover-whole", base.copy(split = false, showHints = false), Color.TRANSPARENT) { focus(it, "g") }
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun odin() {
        assumeTrue(File("build/visuals-split").exists())
        shot("odin-letters", base) { focus(it, "e") }
        shot("odin-right", base) { focus(it, "k") }
        shot("odin-words", base) { it.setSuggestions(listOf("zelda", "zero", "zone"), "ze"); focus(it, "l") }
        shot("odin-accents", base) { focus(it, "e"); it.pressA() }
        shot("odin-symbols", base) { it.toggleLayer() }
        shot("odin-rt", base) { it.setHeldShift(true) }
        shot("odin-number-row", base.copy(numberRow = true)) { focus(it, "e") }
        shot("odin-largest", base.copy(sizeLevel = 2)) { focus(it, "e") }
        shot("odin-clipboard", base) { it.setClips(listOf("Chrono Trigger", "Metroid Fusion", "Tekken 3")); it.toggleClips() }
        val clear = Palette.tones.indexOfFirst { it.clear }
        shot("odin-clear", base.copy(toneIndex = clear), Color.parseColor("#3B4A5C")) { focus(it, "e") }
        shot("odin-indigo", base.copy(toneIndex = Palette.RETRO_BASE + 1)) { focus(it, "e") }
        shot("odin-normal-for-comparison", base.copy(split = false)) { focus(it, "e") }
    }

    @Test @Config(sdk = [34], qualifiers = "w640dp-h480dp-land-xhdpi")
    fun fourByThree() {
        assumeTrue(File("build/visuals-split").exists())
        shot("4by3-letters", base) { focus(it, "e") }
    }

    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xxhdpi")
    fun hd1080() {
        assumeTrue(File("build/visuals-split").exists())
        shot("1080p-letters", base) { focus(it, "e") }
    }
}
