package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * Button badges stay right in every style (design audit, 28 Oct 2026):
 *  - each button looks different from every other button in the same style (so Switch can never again
 *    borrow Xbox's View and Menu icons for − and +)
 *  - the icon's ink stays inside the badge with a clear margin, on round badges and pills alike
 *  - every badge is the same height, and round badges are exactly round
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class BadgeDesignTest {
    private val dp get() = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    /** Draws one badge on black at 4x; returns the bitmap and the badge's box. */
    private fun render(p: BadgePainter, b: ControllerButton): Pair<Bitmap, android.graphics.RectF> {
        val w = (p.width(b) + 20).toInt(); val h = (p.height + 20).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.BLACK)
        val right = p.draw(c, b, 10f, h / 2f)
        return bmp to android.graphics.RectF(10f, h / 2f - p.radius, right, h / 2f + p.radius)
    }

    /** Bright pixels that aren't the plate's grey edge: the icon itself. */
    private fun isInk(c: Int) = Color.red(c) + Color.green(c) + Color.blue(c) > 300 &&
        !(Color.red(c) == Color.green(c) && Color.green(c) == Color.blue(c) && Color.red(c) < 140)

    private fun fingerprint(style: ButtonStyle, b: ControllerButton): String {
        val p = BadgePainter(dp).apply { this.style = style; scale = 4f }
        val (bmp, _) = render(p, b)
        val sig = StringBuilder()
        for (y in 0 until bmp.height step 3) for (x in 0 until bmp.width step 3) sig.append(if (isInk(bmp.getPixel(x, y))) '1' else '0')
        bmp.recycle(); return sig.toString()
    }

    /** Each platform shows its own system buttons: Switch + and −, PlayStation Create, never Xbox's View and Menu. */
    @Test
    fun systemButtonsArePlatformSpecific() {
        val problems = mutableListOf<String>()
        for (b in listOf(ControllerButton.START, ControllerButton.SELECT))
            if (fingerprint(ButtonStyle.SWITCH, b) == fingerprint(ButtonStyle.XBOX, b)) problems += "Switch $b borrows Xbox's icon"
        if (fingerprint(ButtonStyle.PLAYSTATION, ControllerButton.SELECT) == fingerprint(ButtonStyle.XBOX, ControllerButton.SELECT))
            problems += "PlayStation Create borrows Xbox's View icon"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun everyBadgeIsDistinctAndItsIconFitsInside() {
        val problems = mutableListOf<String>()
        for (style in ButtonStyle.values()) {
            val p = BadgePainter(dp).apply { this.style = style; scale = 4f }
            val seen = mutableMapOf<String, ControllerButton>()
            for (b in ControllerButton.values()) {
                val (bmp, box) = render(p, b)
                if (box.height() != p.height) problems += "$style $b: height ${box.height()} ≠ ${p.height}"
                // Distinct: compare a fingerprint of the icon's ink
                val sig = StringBuilder()
                for (y in 0 until bmp.height step 3) for (x in 0 until bmp.width step 3) sig.append(if (isInk(bmp.getPixel(x, y))) '1' else '0')
                seen[sig.toString()]?.let { problems += "$style: $b looks exactly like $it" }
                seen[sig.toString()] = b
                // Inside: the icon keeps at least 12 % of the badge's height from its edge
                val margin = p.height * 0.12f
                val r = p.radius
                for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                    if (!isInk(bmp.getPixel(x, y))) continue
                    val cx = x + 0.5f; val cy = y + 0.5f
                    // distance inside the pill shape (round ends of radius r)
                    val nx = if (box.width() <= 2 * r + 0.5f) box.centerX() else cx.coerceIn(box.left + r, box.right - r)
                    val d = r - hypot(cx - nx, cy - box.centerY())
                    if (d < margin) { problems += "$style $b: icon ink only ${"%.1f".format(d)} px from the edge (min ${"%.1f".format(margin)})"; break }
                }
                bmp.recycle()
            }
        }
        assertTrue("${problems.size} problems:\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }
}
