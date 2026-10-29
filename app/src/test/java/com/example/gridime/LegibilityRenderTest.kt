package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Looper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Measured on the drawn pixels, not on the colour maths: every word and letter on the keyboard against
 * what's really around it. Every theme with every highlight style, and the see-through theme at three
 * levels over a white, a black and a yellow app. States: words showing, RT held (its chips), symbols,
 * and the number row. A label passes at 4.5:1 between its ink and the surface around it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class LegibilityRenderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun lum(c: Int) = Color.luminance(c).toDouble()
    private fun ratio(a: Double, b: Double) = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

    /** The label's ink (its most extreme pixels) against the median of a thin ring just outside it. */
    private fun legibility(bmp: Bitmap, r: RectF): Double {
        val l = r.left.toInt().coerceIn(0, bmp.width - 1); val t = r.top.toInt().coerceIn(0, bmp.height - 1)
        val rr = r.right.toInt().coerceIn(l + 1, bmp.width); val b = r.bottom.toInt().coerceIn(t + 1, bmp.height)
        val inside = mutableListOf<Double>()
        for (y in t until b) for (x in l until rr) inside += lum(bmp.getPixel(x, y))
        val ring = mutableListOf<Double>()
        val pad = 4
        for (y in (t - pad).coerceAtLeast(0) until (b + pad).coerceAtMost(bmp.height)) for (x in (l - pad).coerceAtLeast(0) until (rr + pad).coerceAtMost(bmp.width))
            if (x < l - 1 || x > rr || y < t - 1 || y > b) ring += lum(bmp.getPixel(x, y))
        if (inside.isEmpty() || ring.isEmpty()) return 99.0
        inside.sort(); ring.sort()
        val around = ring[ring.size / 2]
        val light = inside[(inside.size * 0.98).toInt().coerceAtMost(inside.lastIndex)]
        val dark = inside[(inside.size * 0.02).toInt()]
        return maxOf(ratio(light, around), ratio(dark, around))
    }

    @Test fun everyLabelReadsOnWhatsReallyBehindIt() {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val clear = Palette.tones.indexOfFirst { it.clear }
        val cases = mutableListOf<Triple<String, KeyboardSettings, Int>>()
        val base = KeyboardSettings(tutorialDone = true, wordSuggestions = true)
        Palette.tones.forEachIndexed { i, t -> if (!t.clear) HighlightStyle.values().forEach { h ->
            cases += Triple("${t.name}/$h", base.copy(toneIndex = i, highlightStyle = h), Color.BLACK) } }
        for (level in listOf(0, 5, 10)) for ((name, app) in listOf("white" to Color.WHITE, "black" to Color.BLACK, "yellow" to Color.parseColor("#FFE63C")))
            cases += Triple("see-through $level over $name", base.copy(toneIndex = clear, seeThrough = level), app)
        val problems = mutableListOf<String>(); var worst = 99.0
        for ((tag, s, app) in cases) for (numberRow in listOf(false, true)) {
            val v = KeyGridView(context); v.applySettings(s.copy(numberRow = numberRow))
            frame.removeAllViews(); frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2)); idle(16)
            val states = listOf<Pair<String, () -> Unit>>(
                "words" to { v.setSuggestions(listOf("republic", "report", "reply"), "re") },
                "RT held" to { v.setSuggestions(emptyList(), ""); v.setHeldShift(true) },
                "symbols" to { v.setHeldShift(false); v.resetShift(); v.toggleLayer() })
            for ((state, act) in states) {
                act(); idle(300)
                val keyboard = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                val log = mutableListOf<Pair<String, RectF>>(); v.drawLog = log; v.draw(Canvas(keyboard)); v.drawLog = null
                val shot = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); shot.eraseColor(app)
                Canvas(shot).drawBitmap(keyboard, 0f, 0f, null); keyboard.recycle()
                for ((what, r) in log.filter { it.first.startsWith("label:") }) {
                    val c = legibility(shot, r); worst = minOf(worst, c)
                    if (c < 4.5) problems += "$tag${if (numberRow) "/number row" else ""}/$state: \"${what.removePrefix("label:")}\" ${"%.2f".format(c)}"
                }
                shot.recycle()
            }
        }
        println("Legibility on the drawn keyboard: lowest ${"%.2f".format(worst)}")
        problems.forEach { println("PROBLEM $it") }
        assertTrue("${problems.size} labels under 4.5:1\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }
}
