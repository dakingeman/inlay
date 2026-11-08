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

/** Three looks for the black-and-green secret theme, drawn by the real keyboard (design round). Touch build/visuals-size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class XboxThemeExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val out = File("build/retro-ship").apply { mkdirs() }
    private fun c(h: String) = Color.parseColor(h)

    // Round 5 (5 Nov): Black jewel A made its own (things a normal theme can't do), Grey brick C balanced two colours
    // a side with top bar designs, Indigo's flat colours with the cube's vent along the top.
    private val lime = c("#9BE04A")
    private val black = RetroLook(RetroLook.Pattern.X, c("#141514"), lip = c("#070807"), shape = KeyShape.SOFT)
    private val lit = black.copy(patternColour = c("#1E201E"), jewels = setOf(KeyType.SPACE), flatJewel = true)
    private val limeControls = listOf(KeyType.SHIFT, KeyType.DELETE, KeyType.ENTER, KeyType.LAYER).associateWith { lime }
    private fun jewel(look: RetroLook, ground: String = "#0B0C0B") = Tone("Black jewel", c(ground), c("#232423"), c("#1A1B1A"),
        specials = mapOf(KeyType.SPACE to c("#7DC243")), ownHighlight = lime, ownStyle = HighlightStyle.SOLID, look = look)
    private val symbols = mapOf(KeyType.SHIFT to c("#F08A8A"), KeyType.LAYER to c("#5CC9AE"), KeyType.DELETE to c("#E99BD3"), KeyType.ENTER to c("#8FA9E8"))
    private fun brick(look: RetroLook) = Tone("Grey brick", c("#9E9E9C"), c("#B9B9B7"), c("#505052"),
        specials = symbols + (KeyType.SPACE to c("#505052")), ownHighlight = c("#2A4FB0"), ownStyle = HighlightStyle.OUTLINE, look = look)
    private val greyKeys = RetroLook(lip = c("#A2A2A0"), shape = KeyShape.SOFT)
    private fun indigo(look: RetroLook) = Tone("Indigo", c("#3A3468"), c("#5D53A2"), c("#D3D4D9"),
        specials = mapOf(KeyType.DELETE to c("#E8414B"), KeyType.LAYER to c("#F7C702")), ownHighlight = c("#11B377"), ownStyle = HighlightStyle.SOLID, look = look)
    private val cube = RetroLook(RetroLook.Pattern.GRID, lip = c("#3A3468"), rounded = true, shape = KeyShape.ROUND)
    private val looks = listOf(
        "X1-F-green-controls" to jewel(lit.copy(inks = limeControls)),
        "X2-H-side-fins" to jewel(lit.copy(pattern = RetroLook.Pattern.RIBS, patternColour = c("#242624"), stripBand = c("#121312"), patternLine = 1.5f, patternGap = 6f, inks = limeControls)),
        "P1-C1-ridges" to brick(greyKeys.copy(pattern = RetroLook.Pattern.RIDGES, patternColour = c("#8F8F8D"))),
        "P2-C3-back-bar" to brick(greyKeys.copy(pattern = RetroLook.Pattern.RIDGES, patternColour = c("#A4A4A2"), stripBand = c("#8E8E8C"))),
        "G1-A1-fine-grid" to indigo(cube.copy(patternColour = c("#332D5E"))),
    )

    private fun shot(name: String, s: KeyboardSettings, act: (KeyGridView) -> Unit = {}) {
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
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); b.eraseColor(c("#2A2C33"))
        v.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun export() {
        assumeTrue(File("build/visuals-size").exists())
        // The shipping three, on a letter and on the space bar
        try {
            Palette.retro.forEachIndexed { i, tone ->
                Palette.labTone = tone
                val s = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, toneIndex = Palette.LAB_TONE, retroUnlocked = true)
                shot("T$i-letter", s) { v -> repeat(40) { if (v.stepTowards("g") == true) return@repeat } }
                shot("T$i-space", s) { v -> repeat(40) { if (v.stepTowards(" ") == true) return@repeat } }
            }
        } finally { Palette.labTone = null }
    }
}
