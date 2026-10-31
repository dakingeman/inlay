package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every button badge is drawn whole, with clear room all round: no pixel of a badge, its edge or its letter touches the
 * edge of the space it was given, for every button, every button style and every size the app and the images use.
 * Measured on the drawn pixels, not on sizes. The badges' edge used to be drawn across their box, so half of it stuck
 * out and was cut off wherever a badge was sized to its box (the launch videos' button names, 31 Oct 2026).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class BadgeInkTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val buttons = listOf("A", "B", "X", "Y", "L1", "R1", "L2", "R2", "SELECT", "START", "R3", "L3")
    private val lines = listOf("hold {A}", "{R2} + {A}", "{L1} {R1}", "{SELECTx2}", "{R2} + {X}", "{R3} left or right")

    private fun touchesEdge(v: View): Boolean {
        v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth.coerceAtLeast(1), v.measuredHeight.coerceAtLeast(1))
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); b.eraseColor(0); v.draw(Canvas(b))
        val w = b.width; val h = b.height
        val edge = (0 until w).any { (b.getPixel(it, 0) ushr 24) != 0 || (b.getPixel(it, h - 1) ushr 24) != 0 } ||
            (0 until h).any { (b.getPixel(0, it) ushr 24) != 0 || (b.getPixel(w - 1, it) ushr 24) != 0 }
        b.recycle(); return edge
    }

    @Test fun everyBadgeIsWhole() {
        val bad = mutableListOf<String>(); var n = 0
        for (style in ButtonStyle.values()) for (scale in listOf(1f, Design.BADGE_INLINE, 1.45f, 5f)) {
            for (k in buttons + lines) {
                val text = if (k in buttons) "{$k}" else k
                val v = BadgeLineView(context, HintText.parse(text), style, Design.TYPE_CAPTION, SettingsStyle.textSecondary, badgeScale = scale, gapBadgeToBadge = 6f, gapBadgeToText = 8f)
                n++; if (touchesEdge(v)) bad += "$style $text at ${scale}x"
            }
        }
        println("badges drawn whole: $n checked, ${bad.size} touching their edge")
        assertTrue("Badges cut off or touching their edge:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
