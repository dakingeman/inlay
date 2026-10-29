package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tour and What's new, measured on their real backdrop (the brightest point of the light, where
 * words sit), with every preset colour and the colour sliders' extremes:
 * words at least 4.5:1, marks that carry meaning (dots, the cursor, the loop bar, the window's edge) at least 3:1.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TourContrastTest {

    /** The brightest point of the backdrop's top half (where the words sit), and of the whole screen. */
    private fun brightest(accent: Int, whatsNew: Boolean): Pair<Int, Int> {
        val w = 960; val h = 540
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Backdrop(accent, whatsNew).apply { setBounds(0, 0, w, h) }.draw(Canvas(b))
        var top = Color.BLACK; var all = Color.BLACK
        for (y in 0 until h step 2) for (x in 0 until w step 2) {
            val c = b.getPixel(x, y)
            if (Color.luminance(c) > Color.luminance(all)) all = c
            if (y < h / 2 && Color.luminance(c) > Color.luminance(top)) top = c
        }
        b.recycle()
        return top or 0xFF000000.toInt() to (all or 0xFF000000.toInt())
    }

    private fun accents(): List<Pair<String, Int>> = Palette.accents +
        (0 until 360 step 30).flatMap { hue -> listOf(0, 100).flatMap { s -> listOf(0, 100).map { br ->
            "custom hue $hue strength $s brightness $br" to Palette.customAccent(hue, s, br) } } }

    @Test fun everythingReadsOnTheBackdrop() {
        val problems = mutableListOf<String>()
        val worst = mutableMapOf<String, Double>()
        var where = ""
        fun check(what: String, fg: Int, bg: Int, min: Double) {
            val r = Contrast.ratio(fg, bg)
            worst[what] = minOf(worst[what] ?: 99.0, r)
            if (r < min) problems += "$where$what: ${"%.2f".format(r)} (needs $min)"
        }
        for ((name, accent) in accents()) for (wn in listOf(false, true)) {
            val (top, all) = brightest(accent, wn)
            where = (if (wn) "What's new" else "tour") + ", $name: "
            check("title", Design.TEXT_PRIMARY, top, 4.5)
            check("body and row words", Design.TEXT_SECONDARY, top, 4.5)
            val label = TutorialActivity.labelColor(accent, wn)
            if (wn) check("NEW IN tag word on its tint", label, Contrast.over(Contrast.withAlpha(accent, Design.ALPHA_TINT_TAG), top), 4.5)
            else check("WELCOME TOUR label", label, top, 4.5)
            check("page dot not reached", Design.DOT_IDLE, all, 3.0)
            check("current page dot", accent, all, 3.0)
            check("control bar words", Design.TEXT_SECONDARY, all, 4.5)
            check("loop line on the window's edge", accent, Design.TRACK, 3.0)
            // The current row: its marker (a graphic, 3:1) and bright words; the other rows are the body grey above
            check("current-row marker", TutorialActivity.markerColor(accent, wn), top, 3.0)
            check("loop sign", Design.TEXT_SECONDARY, all, 3.0)
            // the window around the text box and keyboard: black, with one solid edge
            check("window edge on the backdrop", Design.TRACK, Color.BLACK, 1.3)
        }
        where = ""
        // The demo app inside the window: its text box, on the black window
        val box = SettingsStyle.surface
        check("text box words", Design.TEXT_PRIMARY, box, 4.5)
        check("text box placeholder", Design.TEXT_SECONDARY, box, 4.5)
        for ((name, accent) in accents()) {
            check("cursor", accent, box, 3.0)
            check("a lit device button's label (dark ink on your colour)", Design.INK_DARK, accent, 4.5)
            check("selected words", Design.TEXT_PRIMARY, Contrast.over(Contrast.withAlpha(accent, Design.ALPHA_TINT_GLOW), box), 4.5)
            check("sound pack tag", TutorialActivity.fieldTagColor(accent), Contrast.over(Contrast.withAlpha(accent, Design.ALPHA_TINT_TAG), box), 4.5)
            check("HOLD on its capsule", BadgePainter.holdWordColor(accent), Contrast.over(BadgePainter.PLATE, Color.BLACK), 4.5)
        }
        println("Tour contrast, lowest per check:")
        worst.toSortedMap().forEach { (k, v) -> println("  %-40s %.2f".format(k, v)) }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }
}
