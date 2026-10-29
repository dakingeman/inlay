package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every icon in Icons.kt, drawn at the smallest and largest size it's used: its ink stays inside its own box
 * (so it never touches the key or row around it), it's centred in that box, and no two icons in one family
 * differ much in how big they look. Writes build/icons/sheet.png for a look.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconSheetTest {
    companion object { const val OPTICAL_SLACK = 0.04f }
    private class Icon(val name: String, val draw: (Canvas, Float, Float, Float, Paint, Paint) -> Unit)

    private val icons = listOf(
        Icon("shift") { c, x, y, s, p, _ -> Icons.shift(c, x, y, s, ShiftState.OFF, p) },
        Icon("shift once") { c, x, y, s, p, _ -> Icons.shift(c, x, y, s, ShiftState.ONCE, p) },
        Icon("caps lock") { c, x, y, s, p, _ -> Icons.shift(c, x, y, s, ShiftState.LOCK, p) },
        Icon("delete") { c, x, y, s, p, _ -> Icons.backspace(c, x, y, s, p) },
        Icon("undo") { c, x, y, s, p, _ -> Icons.undo(c, x, y, s, p) },
        Icon("enter") { c, x, y, s, p, _ -> Icons.enter(c, x, y, s, p) },
        Icon("clipboard") { c, x, y, s, p, _ -> Icons.clock(c, x, y, s, p) },
        Icon("settings") { c, x, y, s, p, _ -> Icons.gear(c, x, y, s, p) },
        Icon("tick") { c, x, y, s, p, _ -> Icons.check(c, x, y, s, p) },
        Icon("chevron") { c, x, y, s, p, _ -> Icons.chevron(c, x, y, s, p) },
        Icon("loop") { c, x, y, s, p, f -> Icons.loop(c, x, y, s, p, f) },
        Icon("keyboard") { c, x, y, s, p, f -> Icons.keyboard(c, x, y, s, p, f) },
    )

    /** Draws one icon centred in a cell; returns the ink's box (left, top, right, bottom) in the cell, or null. */
    private fun ink(icon: Icon, s: Float, unit: Float, cell: Int): IntArray? {
        val b = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
        val p = Icons.line(Paint(Paint.ANTI_ALIAS_FLAG), unit, Color.WHITE)
        icon.draw(Canvas(b), cell / 2f, cell / 2f, s, p, Paint(Paint.ANTI_ALIAS_FLAG))
        var l = cell; var t = cell; var r = -1; var bo = -1
        for (y in 0 until cell) for (x in 0 until cell) if (Color.alpha(b.getPixel(x, y)) > 40) { l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); bo = maxOf(bo, y) }
        b.recycle()
        return if (r < 0) null else intArrayOf(l, t, r, bo)
    }

    /**
     * Optical centre: the eye places a shape between the middle of its outline and the middle of its ink (a
     * triangle sits low, an arrow leans towards its head). Each icon's visual centre, the average of the two, sits
     * within [OPTICAL_SLACK] of its box's centre, so icons look centred on their keys, not only measure centred.
     */
    @Test fun everyIconLooksCentred() {
        val problems = mutableListOf<String>()
        val px = 7f * 3f; val unit = 3f; val cell = (px * 6).toInt()
        for (icon in icons) {
            val b = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
            icon.draw(Canvas(b), cell / 2f, cell / 2f, px, Icons.line(Paint(Paint.ANTI_ALIAS_FLAG), unit, Color.WHITE), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            var l = cell; var t = cell; var r = -1; var bo = -1; var sx = 0.0; var sy = 0.0; var sw = 0.0
            for (y in 0 until cell) for (x in 0 until cell) { val a = Color.alpha(b.getPixel(x, y)); if (a > 40) { l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); bo = maxOf(bo, y) }; sx += x * a.toDouble(); sy += y * a.toDouble(); sw += a }
            b.recycle()
            val c = cell / 2f; val size = maxOf(r - l, bo - t).toFloat()
            val vx = (((l + r) / 2f + sx / sw) / 2f - c) / size; val vy = (((t + bo) / 2f + sy / sw) / 2f - c) / size
            println("ICON ${icon.name}: visual centre ${"%+.1f".format(vx * 100)} %, ${"%+.1f".format(vy * 100)} % of its size")
            if (kotlin.math.abs(vx) > OPTICAL_SLACK || kotlin.math.abs(vy) > OPTICAL_SLACK) problems += "${icon.name}: looks off centre by ${"%+.1f".format(vx * 100)} %, ${"%+.1f".format(vy * 100)} % of its size"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyIconStaysInItsBoxAndCentred() {
        val problems = mutableListOf<String>()
        // icon size s at 1 dp = 3 px (xxhdpi): the keyboard's icons are about 7 units, scaled 0.72..1.3
        for (unit in listOf(2f, 3f, 4f)) for (icon in icons) for (s in listOf(5f, 7f, 9f)) {
            val px = s * unit; val cell = (px * 6).toInt()
            val box = ink(icon, px, unit, cell) ?: run { problems += "${icon.name}: draws nothing"; continue }
            val half = px * 1.6f + Design.STROKE_ICON * unit      // the icon's own box: its size plus its line
            val c = cell / 2f
            if (box[0] < c - half || box[2] > c + half || box[1] < c - half || box[3] > c + half)
                problems += "${icon.name} at size $s, ${unit}x: ink reaches past its box (${box.joinToString()})"
            val dx = (box[0] + box[2]) / 2f - c; val dy = (box[1] + box[3]) / 2f - c
            if (kotlin.math.abs(dx) > px * 0.3f || kotlin.math.abs(dy) > px * 0.3f) problems += "${icon.name} at size $s: off centre by ${"%.1f".format(dx)}, ${"%.1f".format(dy)} px"
        }
        // The sheet, for the eye: every icon at three sizes
        val cell = 72; val sheet = Bitmap.createBitmap(cell * icons.size, cell * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); canvas.drawColor(Design.SURFACE_CARD)
        icons.forEachIndexed { i, icon -> listOf(5f, 7f, 9f).forEachIndexed { j, s ->
            icon.draw(canvas, i * cell + cell / 2f, j * cell + cell / 2f, s * 2.6f, Icons.line(Paint(Paint.ANTI_ALIAS_FLAG), 2.6f, Design.TEXT_PRIMARY), Paint(Paint.ANTI_ALIAS_FLAG)) } }
        File("build/icons").mkdirs(); File("build/icons/sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The app icon: everything in its foreground sits inside Android's safe zone (a 66 dp circle in the
     * 108 dp canvas), so no launcher's mask (circle, squircle, teardrop) ever cuts the logo; the monochrome
     * (themed) icon has the same shape. And the icon and TV banner use only the brand's colours.
     */
    @Test fun appIconFitsEveryMaskAndUsesTheBrandColours() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val problems = mutableListOf<String>()
        fun inkOf(id: Int): List<Pair<Int, Int>> {
            val d = ctx.getDrawable(id)!!; val n = 432
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, n, n); d.draw(Canvas(b))
            val out = (0 until n step 2).flatMap { y -> (0 until n step 2).mapNotNull { x -> if (Color.alpha(b.getPixel(x, y)) > 40) x to y else null } }
            b.recycle(); return out
        }
        val n = 432f; val safe = n * 33f / 108f
        for ((name, id) in listOf("foreground" to R.drawable.ic_launcher_foreground, "monochrome" to R.drawable.ic_launcher_monochrome)) {
            val ink = inkOf(id)
            if (ink.size < 200) { problems += "$name: draws (almost) nothing"; continue }
            val far = ink.maxOf { (x, y) -> kotlin.math.hypot(x - n / 2, y - n / 2) }
            println("app icon $name: reaches ${"%.1f".format(far / n * 108)} dp of 33")
            if (far > safe) problems += "$name: reaches ${"%.1f".format(far / n * 108)} dp from the centre (the safe zone is 33)"
        }
        val brand = setOf(Design.BRAND_ACCENT, Design.INK_LIGHT, Design.BACKDROP_BOTTOM, Color.BLACK).map { "#%06X".format(it and 0xFFFFFF) }
        for (f in listOf("ic_launcher_foreground", "ic_launcher_background", "ic_launcher_monochrome", "tv_banner")) {
            Regex("""(fill|stroke)Color="(#[0-9A-Fa-f]{6,8})"""").findAll(File("src/main/res/drawable/$f.xml").readText()).map { it.groupValues[2].uppercase().takeLast(6).let { c -> "#$c" } }
                .filter { it !in brand }.forEach { problems += "$f.xml: $it isn't a brand colour" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The TV banner in the app is generated by tools/brand/kit.py next to docs/brand/inlay-tv-banner.svg. Drawn by
     * Android, it must match the brand file's picture: fewer than 0.5 % of pixels may differ clearly (more than a
     * quarter of the brightness range), which allows for the two renderers smoothing edges differently, nothing more.
     */
    @Test fun tvBannerMatchesTheBrandFile() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val ref = android.graphics.BitmapFactory.decodeFile("../docs/brand/png/inlay-tv-banner-640.png")!!
        val w = ref.width; val h = ref.height
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        ctx.getDrawable(R.drawable.tv_banner)!!.apply { setBounds(0, 0, w, h) }.draw(Canvas(b))
        File("build/icons").mkdirs(); File("build/icons/tv_banner.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var off = 0
        for (y in 0 until h) for (x in 0 until w) {
            val p = b.getPixel(x, y); val q = ref.getPixel(x, y)
            val d = maxOf(kotlin.math.abs(Color.red(p) - Color.red(q)), kotlin.math.abs(Color.green(p) - Color.green(q)), kotlin.math.abs(Color.blue(p) - Color.blue(q)))
            if (d > 64) off++
        }
        val share = off * 100.0 / (w * h)
        println("TV banner: ${"%.3f".format(share)} % of pixels differ from the brand file")
        assertTrue("TV banner differs from docs/brand: ${"%.2f".format(share)} % of pixels", share < 0.5)
    }
}
