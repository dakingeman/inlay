package com.example.gridime

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The colour maths behind the tour's light, in OKLCH (a colour space where "lightness" and "colour
 * strength" mean what they look like, so every hue comes out equally soft). Works for any colour:
 * the presets and anything the colour slider makes.
 */
object AccentLight {
    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
    private fun gam(v: Double): Int { val c = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055; return (c.coerceIn(0.0, 1.0) * 255).roundToInt() }

    /** Lightness (0–1), chroma (colour strength) and hue (degrees) of a colour. */
    fun oklch(color: Int): DoubleArray {
        val r = lin(Color.red(color)); val g = lin(Color.green(color)); val b = lin(Color.blue(color))
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val h = Math.toDegrees(atan2(B, A)).let { if (it < 0) it + 360 else it }
        return doubleArrayOf(L, hypot(A, B), h)
    }

    /** Back to an ordinary colour. Out-of-range colours are brought in by lowering the chroma, never by clipping the hue. */
    fun color(L: Double, C: Double, h: Double, alpha: Int = 255): Int {
        var c = C
        repeat(12) {
            val a = c * cos(Math.toRadians(h)); val b = c * sin(Math.toRadians(h))
            val l = (L + 0.3963377774 * a + 0.2158037573 * b).pow(3)
            val m = (L - 0.1055613458 * a - 0.0638541728 * b).pow(3)
            val s = (L - 0.0894841775 * a - 1.2914855480 * b).pow(3)
            val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
            val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
            val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
            if (r in -0.001..1.001 && g in -0.001..1.001 && bl in -0.001..1.001 || c < 0.002) return Color.argb(alpha, gam(r), gam(g), gam(bl))
            c *= 0.85
        }
        return Color.argb(alpha, gam(L), gam(L), gam(L))
    }

    /** True when the colour exists on an ordinary (sRGB) screen. */
    private fun inGamut(L: Double, C: Double, h: Double): Boolean {
        val a = C * cos(Math.toRadians(h)); val b = C * sin(Math.toRadians(h))
        val l = (L + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (L - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (L - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return r in -0.0005..1.0005 && g in -0.0005..1.0005 && bl in -0.0005..1.0005
    }

    /** The most vivid a colour of this lightness and hue can be on a screen. */
    fun maxChroma(L: Double, h: Double): Double {
        var lo = 0.0; var hi = 0.4
        repeat(24) { val mid = (lo + hi) / 2; if (inGamut(L, mid, h)) lo = mid else hi = mid }
        return lo
    }

    /** A hue turned [deg] degrees towards blue-violet (265°), the short way round: teal never drifts green. */
    fun turn(h: Double, deg: Double): Double {
        val d = ((265 - h + 540) % 360) - 180
        return (h + Math.signum(d) * min(abs(d), deg) + 360) % 360
    }

    /**
     * How much colour a dark wash of hue [h] can carry before it looks muddy. Dark yellows and oranges
     * turn olive and brown, so around yellow (95°) the wash keeps only a warm hint; cool hues keep it all.
     */
    fun washStrength(h: Double): Double {
        val d = abs(((h - 95 + 540) % 360) - 180)          // how far from yellow, in degrees
        return if (d >= 60) 1.0 else 1.0 - 0.6 * (0.5 + 0.5 * cos(Math.PI * d / 60))
    }

    /** Hues near yellow turn towards orange, never through green; all others turn towards blue. */
    private fun washHue(h: Double, deg: Double): Double =
        if (abs(((h - 95 + 540) % 360) - 180) < 50) (h - min(deg, 20.0) + 360) % 360 else turn(h, deg)

    /** The soft wash of the highlight colour: low lightness and chroma, its hue turned 14° towards blue (yellows: a warm hint). */
    fun whisper(accent: Int): Int { val (_, c, h) = oklch(accent).toList(); return color(0.30, min(0.045, c) * washStrength(h), washHue(h, 14.0)) }

    /** The deeper second wash used by What's new, turned further towards blue. */
    fun deepWash(accent: Int): Int { val (_, c, h) = oklch(accent).toList(); return color(0.26, min(0.04, c) * washStrength(h), washHue(h, 38.0)) }
}

/**
 * The background of the tour and What's new: graphite (never flat black), one soft light from above,
 * and one wash of the user's highlight colour. The welcome tour is lit from the top left; What's new
 * from the top right, with a second, deeper wash rising from the bottom left, so the two feel related
 * but are told apart at a glance. No tiles, no glow: everything else sits on top unchanged.
 */
class Backdrop(private val accent: Int, private val whatsNew: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    /**
     * The light is painted once into a picture at half the screen's size and reused for every frame:
     * one image instead of five full-screen gradients each time anything on top moves (the tour's demos
     * redraw many times a second). Soft gradients look the same scaled up. Made again only for a new size.
     */
    private var cache: android.graphics.Bitmap? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val cw = (b.width() / 2).coerceAtLeast(1); val ch = (b.height() / 2).coerceAtLeast(1)
        val c = cache?.takeIf { it.width == cw && it.height == ch } ?: android.graphics.Bitmap.createBitmap(cw, ch, android.graphics.Bitmap.Config.ARGB_8888).also {
            cache?.recycle(); cache = it; paintLight(Canvas(it), cw.toFloat(), ch.toFloat())
        }
        canvas.drawBitmap(c, null, b, blit)
        // Fine grain on top, at the screen's own resolution: dark gradients on OLED otherwise show faint
        // rings where one shade steps to the next (the soft light is painted at half size, which smooths
        // its own dithering away). A small random tile, repeated: one more texture, no extra memory to speak of.
        canvas.drawRect(b, grain)
    }

    private val grain = Paint().apply {
        shader = android.graphics.BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun paintLight(canvas: Canvas, w: Float, h: Float) {
        // 1. graphite base, Ink at the bottom
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Design.BACKDROP_TOP, Design.BACKDROP_MID, Design.BACKDROP_BOTTOM),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        // The lights keep one shape on every screen: sized by the screen's long and short sides, always
        // wider than tall. Upright (a phone) the light spreads across the top as it does on a landscape
        // screen, instead of turning into a narrow beam down one side.
        val long = max(w, h); val short = min(w, h)
        // 2. second wash (What's new only): bottom left, deeper and bluer
        if (whatsNew) wash(canvas, AccentLight.deepWash(accent), .55f, w * .12f, h + short * .08f, long * .55f, short * .75f)
        // 3. the whisper of the highlight colour in a top corner
        wash(canvas, AccentLight.whisper(accent), .78f, if (whatsNew) w * .86f else w * .14f, -short * .06f, long * .66f, short * .85f)
        // 4. one soft neutral light from above
        wash(canvas, Color.WHITE, .04f, w * .5f, -short * .2f, long * .9f, short * .9f)
        // 5. a light vignette
        paint.shader = RadialGradient(w / 2, h * .46f, hypot(w, h) * .55f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Design.BACKDROP_VIGNETTE shl 24),
            floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    /** One soft elliptical wash of [color] at [strength], centred on (cx, cy), fading out at 70 % of its radii. */
    private fun wash(canvas: Canvas, color: Int, strength: Float, cx: Float, cy: Float, rx: Float, ry: Float) {
        val a = (255 * strength).roundToInt().coerceIn(0, 255)
        val c0 = (color and 0x00FFFFFF) or (a shl 24); val c1 = color and 0x00FFFFFF
        paint.shader = RadialGradient(cx, cy, rx, intArrayOf(c0, (c0 and 0x00FFFFFF) or ((a * .45f).roundToInt() shl 24), c1),
            floatArrayOf(0f, .38f, .7f), Shader.TileMode.CLAMP)
        canvas.save(); canvas.scale(1f, max(.01f, ry / rx), cx, cy)
        canvas.drawCircle(cx, cy, rx, paint)
        canvas.restore()
    }

    companion object {
        /**
         * The grain: white at 0 to [Design.BACKDROP_GRAIN] alpha, random per pixel (a fixed seed, so it's the same
         * every time). It lifts each pixel by at most a step or two of brightness, enough to break up banding,
         * never enough to see as noise.
         */
        private val grainTile: android.graphics.Bitmap by lazy {
            val n = 128; val rnd = java.util.Random(7)
            val px = IntArray(n * n) { val a = (rnd.nextInt(Design.BACKDROP_GRAIN + 1) + rnd.nextInt(Design.BACKDROP_GRAIN + 1)) / 2; (a shl 24) or 0xFFFFFF }
            android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888)
        }

        /**
         * The brightest point of the top half, where the words sit: what words in the highlight colour are
         * measured against (TutorialActivity.labelColor). A tiny render, once per screen.
         */
        fun peak(accent: Int, whatsNew: Boolean): Int {
            val w = 96; val h = 54
            val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            Backdrop(accent, whatsNew).apply { setBounds(0, 0, w, h) }.draw(Canvas(b))
            var top = Color.BLACK
            for (y in 0 until h / 2) for (x in 0 until w) { val c = b.getPixel(x, y); if (Color.luminance(c) > Color.luminance(top)) top = c }
            b.recycle()
            return top or 0xFF000000.toInt()
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
