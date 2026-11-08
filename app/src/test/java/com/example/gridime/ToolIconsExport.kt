package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import org.junit.Assert.assertTrue
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

/** The top row's three styles (words, icons, icons and words), drawn by the real keyboard. Touch build/visuals-icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolIconsExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun run(name: String, split: Boolean = false) {
        assumeTrue(File("build/visuals-icons").exists())
        val bad = mutableListOf<String>()
        for (style in 0..2) for (state in listOf("plain", "preview")) {
            val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            val frame = android.widget.FrameLayout(host); host.setContentView(frame)
            val v = KeyGridView(context)
            v.applySettings(KeyboardSettings(tutorialDone = true, split = split))
            v.toolStyle = style
            frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
            idle(200)
            if (state == "preview") v.setClipPreview("Super Metroid")
            idle(200)
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            val log = mutableListOf<Pair<String, RectF>>(); v.drawLog = log; v.draw(Canvas(b)); v.drawLog = null
            File("build/toolicons").mkdirs(); File("build/toolicons/$name-$style-$state.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val icons = log.filter { it.first.startsWith("toolicon:") }; val words = log.filter { it.first.startsWith("toolword:") }
            if (style > 0 && icons.size != 4) bad += "$name style $style: ${icons.size} icons"
            for ((t, r) in icons) words.firstOrNull { it.first == t.replace("icon", "word") }?.let { (_, w) -> if (RectF.intersects(r, w)) bad += "$name: $t touches its word" }
            // every icon and word stays inside its own key (so it never runs into the next one)
            for ((t, r) in icons + words) {
                val key = log.lastOrNull { it.first == "toolkey:" + t.substringAfter(':') }?.second ?: continue
                if (!RectF(key).apply { inset(-1f, -1f) }.contains(r)) bad += "$name style $style $state: $t leaves its key"
            }
        }
        assertTrue(bad.joinToString("; "), bad.isEmpty())
    }
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odin() = run("odin")
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odinSplit() = run("odinsplit", split = true)
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = run("small43")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun upright() = run("upright")
}
