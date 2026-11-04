package com.example.gridime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Konami Code's reward, drawn in hard pixels like an old game screen: "Secret found" over streaking stars
 * (2.5 s), then the new themes as tiny keyboards and "3 new themes" (3 s). Nothing to navigate; any press skips it.
 *
 * Two ways to show it:
 *  - on the device's own screen (full-screen Try your buttons with a handheld): [showOn] with that screen's rect;
 *  - lifted out (full screen off, or a controller, which has no screen): [liftOut] grows a retro screen out of
 *    [from] to a readable size in the middle, with the page dimmed behind it.
 * Letters are never smaller than [MIN_LETTER_DP]: the number of art pixels follows the room.
 */
class SecretScreen(context: Context) : View(context) {
    private val dp = resources.displayMetrics.density
    private val font: Typeface? = runCatching { Typeface.createFromAsset(context.assets, "fonts/PressStart2P.ttf") }.getOrNull()
    private var target = RectF(); private var from: RectF? = null
    private var lifted = false
    private var started = 0L
    private var closing = 0L
    var onDone: () -> Unit = {}

    /**
     * The art's size in pixels for a screen [r]: always at least [MIN_COLS] wide and [MIN_ROWS] tall, so every
     * line of words fits on one line with room to spare whatever the screen's shape; never finer than [MAX_COLS].
     */
    fun artSize(r: RectF): Pair<Int, Int> {
        var px = min(r.width() / MIN_COLS, r.height() / MIN_ROWS)
        px = max(px, r.width() / MAX_COLS)
        return Pair(max(MIN_COLS, (r.width() / px).toInt()), max(MIN_ROWS, (r.height() / px).toInt()))
    }
    /** How tall a letter is on screen (dp) for a screen [r]. */
    fun letterDp(r: RectF): Float { val (c, _) = artSize(r); return r.width() / c * 7f / dp }

    /** On the device's screen (in this view's coordinates); too small to read there, it lifts out instead. */
    fun showOn(screen: RectF) {
        val inset = 3f * dp
        val r = RectF(screen.left + inset, screen.top + inset, screen.right - inset, screen.bottom - inset)
        if (r.width() < 60f * dp || r.height() < 40f * dp || r.width() > width * 1.01f || r.height() > height * 1.01f ||
            letterDp(r) < MIN_LETTER_DP) { liftOut(null); return }
        lifted = false; from = null; target = r; start()
    }
    /** A retro screen lifted out of [source] into the middle of this view. */
    fun liftOut(source: RectF?) {
        lifted = true; from = source?.let { RectF(it) }
        val w = width.toFloat(); val h = height.toFloat()
        var sh = h * 0.62f; var sw = sh * 4f / 3f
        if (sw > w * 0.9f) { sw = w * 0.9f; sh = sw * 3f / 4f }
        target = RectF((w - sw) / 2f, (h - sh) / 2f, (w + sw) / 2f, (h + sh) / 2f)
        start()
    }
    private val timeUp = Runnable { finish() }
    private fun start() {
        started = SystemClock.uptimeMillis(); closing = 0L; visibility = VISIBLE; alpha = 1f; invalidate()
        removeCallbacks(timeUp); postDelayed(timeUp, (TOTAL_S * 1000).toLong())       // ends on time even if not drawn
    }
    /** Ends it now (a press, or its time is up), with a short fade. */
    fun finish() {
        if (closing != 0L || visibility != VISIBLE) return
        closing = SystemClock.uptimeMillis(); removeCallbacks(timeUp)
        animate().alpha(0f).setDuration(Design.MOTION_PANEL).withEndAction { visibility = INVISIBLE; alpha = 1f; started = 0L; onDone() }.start()
    }
    val showing get() = visibility == VISIBLE && closing == 0L

    // ---- drawing ------------------------------------------------------------------------------------------------
    private val ink = Paint().apply { isAntiAlias = false }
    private val text = Paint().apply { isAntiAlias = false; textSize = 8f; typeface = font; color = WHITE }
    private val blit = Paint().apply { isFilterBitmap = false }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private var art: Bitmap? = null

    /** A drawing problem never takes the app down: the reward simply ends (and the log says why). */
    var problem: String? = null
    override fun onDraw(canvas: Canvas) {
        if (started == 0L) return
        try { drawReward(canvas) } catch (e: Throwable) {
            android.util.Log.e("InlayKonami", "reward drawing failed", e)
            if (problem == null) problem = e.toString() + " @ " + e.stackTrace.take(3).joinToString(" < ")
            if (KonamiCode.hasLab(context)) { val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; textSize = 14f * dp }
                canvas.drawText(problem!!.take(160), 16f * dp, 40f * dp, p); postDelayed({ finish() }, 8000) } else post { finish() }
        }
    }
    private fun drawReward(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - started) / 1000f
        // where the screen is now: lifting out grows from the source over 400 ms
        val e = if (lifted) ease(min(1f, t / 0.4f)) else 1f
        val r = from?.let { RectF(it.left + (target.left - it.left) * e, it.top + (target.top - it.top) * e,
            it.right + (target.right - it.right) * e, it.bottom + (target.bottom - it.bottom) * e) } ?: target
        if (lifted) {
            shade.color = Contrast.withAlpha(Color.BLACK, (Design.SECRET_DIM_ALPHA * e).toInt()); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
            val bez = 10f * dp * e
            shade.color = BODY; canvas.drawRoundRect(r.left - bez, r.top - bez, r.right + bez, r.bottom + bez, 14f * dp, 14f * dp, shade)
            shade.style = Paint.Style.STROKE; shade.strokeWidth = 2.5f; shade.color = Contrast.withAlpha(Design.SECRET_OUTLINE, (255 * e).toInt())
            canvas.drawRoundRect(r.left - bez, r.top - bez, r.right + bez, r.bottom + bez, 14f * dp, 14f * dp, shade); shade.style = Paint.Style.FILL
        }
        val (cols, rows) = artSize(target)
        val bm = art?.takeIf { it.width == cols && it.height == rows } ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).also { art = it }
        scene(Canvas(bm), cols, rows, t)
        canvas.save(); val clip = android.graphics.Path().apply { addRoundRect(r, 6f * dp, 6f * dp, android.graphics.Path.Direction.CW) }; canvas.clipPath(clip)
        canvas.drawBitmap(bm, null, r, blit)
        // soft scanlines, one per art pixel row
        shade.color = Contrast.withAlpha(Color.BLACK, Design.SECRET_SCANLINE_ALPHA); val step = r.height() / rows
        var y = r.top + step - 1f; while (y < r.bottom) { canvas.drawRect(r.left, y, r.right, y + max(1f, step * 0.25f), shade); y += step }
        canvas.restore()
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    /** Test builds compare reward designs (see [DESIGNS]). */
    var design = 0
    /** Where words were drawn this frame (art pixels), for the checks: they must always be inside the screen. */
    internal val textBounds = android.graphics.Rect()
    /** Each line of words and each keyboard drawn this frame (art pixels): no line may touch a keyboard. */
    internal val textLines = mutableListOf<android.graphics.Rect>()
    internal val boxes = mutableListOf<android.graphics.Rect>()
    /** The part of the art being drawn into right now (kept here too, so the checks know what can be seen). */
    private var visible = android.graphics.Rect(0, 0, 10000, 10000)
    private val clips = ArrayDeque<android.graphics.Rect>()
    private fun clipIn(c: Canvas, l: Int, t: Int, r: Int, b: Int) {
        c.save(); c.clipRect(l, t, r, b); clips.addLast(android.graphics.Rect(visible))
        visible = android.graphics.Rect(visible).apply { if (!intersect(l, t, r, b)) setEmpty() }
    }
    private fun clipOut(c: Canvas) { c.restore(); visible = clips.removeLastOrNull() ?: android.graphics.Rect(0, 0, 10000, 10000) }

    internal fun scene(c: Canvas, w: Int, h: Int, t: Float) {
        c.drawColor(INK)
        text.color = WHITE; text.alpha = 255; textBounds.setEmpty(); textLines.clear(); boxes.clear(); clips.clear(); visible = android.graphics.Rect(0, 0, w, h)
        if (t < INTRO_S) {
            // the words land with the code's last note; stars rush out behind them and slow to a drift
            stars(c, w, h, t, warp = max(0f, 1f - t / 0.9f))
            text.alpha = (255 * min(1f, t / 0.08f)).toInt(); label(c, "Secret found", w, h / 2 + 4); text.alpha = 255
            return
        }
        val u = t - INTRO_S
        when (design) {
            1 -> levelSelect(c, w, h, u)
            2 -> powerOn(c, w, h, u)
            3 -> memoryCard(c, w, h, u)
            4 -> titleScreen(c, w, h, u)
            else -> spotlight(c, w, h, u)
        }
    }

    /** Spotlight: each theme takes the whole screen in turn, wiping in from the right; its own highlight glides along. */
    private fun spotlight(c: Canvas, w: Int, h: Int, u: Float) {
        val i = (u / EACH_S).toInt()
        if (i >= THEMES.size) { endCard(c, w, h, u - THEMES.size * EACH_S); return }
        val f = u - i * EACH_S
        val th = THEMES[i]
        val x = (w * (1f - SecretScreen.ease(min(1f, f / 0.22f)))).toInt()
        if (i > 0 && x > 0) { val p = THEMES[i - 1]; clipIn(c, 0, 0, x, h); c.drawColor(p.bg); themeScreen(c, p, w, h, 1f); clipOut(c) }
        clipIn(c, x, 0, w, h); c.drawColor(th.bg); themeScreen(c, th, w, h, max(0f, (f - 0.25f) / (EACH_S - 0.45f))); clipOut(c)
    }

    /** Level select: three cartridges on a shelf; a frame steps from one to the next, and the chosen one shows itself. */
    private fun levelSelect(c: Canvas, w: Int, h: Int, u: Float) {
        val n = THEMES.size; val gap = max(4, w / 28); val below = 24                         // room for the name and the spark
        var cw = (w - 12 - (n - 1) * gap) / n; var ch = (cw * 1.15f).toInt()
        if (ch > h - below - 8) { ch = h - below - 8; cw = min(cw, (ch / 1.15f).toInt()) }
        val x0 = (w - (n * cw + (n - 1) * gap)) / 2; val y0 = max(4, (h - ch - below) / 2)
        val sel = min(n - 1, (u / EACH_S).toInt()); val f = u - sel * EACH_S
        val done = u >= n * EACH_S
        THEMES.forEachIndexed { i, th ->
            val drop = SecretScreen.ease(((u - 0.12f * i) / 0.3f).coerceIn(0f, 1f))
            val lift = if (!done && i == sel) 2 else 0
            val x = x0 + i * (cw + gap); val y = y0 - lift + ((1f - drop) * -(ch + y0)).toInt()
            clipIn(c, x, y, x + cw, y + ch); c.drawColor(th.bg)
            keyboard(c, th, x + 2, y + 3, cw - 4, ch - 6, if (!done && i == sel) max(0f, (f - 0.2f) / (EACH_S - 0.4f)) else -1f)
            clipOut(c)
            if (!done && i == sel) frame(c, x - 2, y - 2, x + cw + 2, y + ch + 2, WHITE)
        }
        val ly = y0 + ch + 6
        text.color = WHITE
        if (!done) labelFrom(c, THEMES[sel].name, w, ly)
        else if (u - n * EACH_S > 0.25f) labelFrom(c, "3 new themes in Look", w, ly)
    }

    /** Power on: each theme switches on like an old TV (a bright line opening up), shows itself, and switches off. */
    private fun powerOn(c: Canvas, w: Int, h: Int, u: Float) {
        val i = (u / EACH_S).toInt()
        if (i >= THEMES.size) { endCard(c, w, h, u - THEMES.size * EACH_S); return }
        val f = u - i * EACH_S; val th = THEMES[i]
        val open = SecretScreen.ease(min(1f, f / 0.18f)); val close = SecretScreen.ease(((f - (EACH_S - 0.2f)) / 0.2f).coerceIn(0f, 1f))
        val half = (h / 2f * open * (1f - close)).toInt().coerceAtLeast(if (close < 1f) 1 else 0)
        val cy = h / 2
        clipIn(c, 0, cy - half, w, cy + max(1, half)); c.drawColor(th.bg); themeScreen(c, th, w, h, max(0f, (f - 0.25f) / (EACH_S - 0.55f))); clipOut(c)
        if (open < 1f || close > 0f) { ink.color = WHITE; c.drawRect(0f, (cy - half).toFloat(), w.toFloat(), (cy - half + 1).toFloat(), ink); c.drawRect(0f, (cy + half - 1).toFloat(), w.toFloat(), (cy + half).toFloat(), ink) }
    }

    /**
     * Memory card: like an old console's save screen. Three new saves load into a list one by one (a small block
     * bar fills), each with its keyboard as the icon; the cursor steps down them in each theme's own highlight.
     */
    private fun memoryCard(c: Canvas, w: Int, h: Int, u: Float) {
        val n = THEMES.size; val listEnd = 1.1f + n * 0.75f
        val endText = "3 new themes in Look"; val endH = 9 + 12 * (lines(endText, w) - 1)
        val rowH = 12.coerceAtLeast(min(24, (h - 20 - 4 - endH - 6) / n))
        val roomBelow = h - (19 + n * rowH) >= endH + 8
        // too short a screen to show the list and the last words together: the list makes way for the end card
        if (u > listEnd && !roomBelow) { endCard(c, w, h, u - listEnd); return }
        c.drawColor(CARD_BG)
        ink.color = CARD_LINE; c.drawRect(0f, 14f, w.toFloat(), 15f, ink)
        text.color = GREY; label(c, "New saves", w, 10)
        val top = 19 + max(0, (h - 19 - (if (roomBelow) endH + 8 else 0) - n * rowH) / 2)
        val longest = THEMES.maxOf { tw(it.name).toInt() }
        val iconW = min((rowH * 1.4f).toInt(), w - 8 - 6 - longest).coerceAtLeast(8); val left = max(4, (w - (iconW + 6 + longest)) / 2)
        THEMES.forEachIndexed { i, th ->
            val at = u - 0.35f * i
            if (at < 0f) return@forEachIndexed
            val y = top + i * rowH
            val cur = min(n - 1, ((u - 1.1f) / 0.75f).toInt().coerceAtLeast(0))
            if (u > 1.1f && u < listEnd && i == cur) { ink.color = th.accent; frameF(c, (left - 3).toFloat(), y.toFloat(), (w - left + 3).toFloat(), (y + rowH).toFloat()) }
            clipIn(c, left, y + 2, left + iconW, y + rowH - 2); c.drawColor(th.bg)
            if (iconW >= 16 && rowH >= 16) keyboard(c, th, left + 1, y + 3, iconW - 2, rowH - 6, -1f)
            else { ink.color = th.special[0]; c.drawRect((left + 2).toFloat(), (y + rowH - 6).toFloat(), (left + iconW - 2).toFloat(), (y + rowH - 4).toFloat(), ink) }   // too small for keys: its colours
            clipOut(c)
            val load = min(1f, at / 0.3f)
            text.color = WHITE
            if (load < 1f) { ink.color = CARD_LINE; c.drawRect((left + iconW + 6).toFloat(), (y + rowH / 2 - 2).toFloat(), (left + iconW + 6 + 40 * load), (y + rowH / 2 + 2).toFloat(), ink) }
            else leftText(c, th.name, left + iconW + 6, y + rowH / 2 + 4)
        }
        if (u > listEnd) { text.color = WHITE; labelFrom(c, endText, w, h - 3 - endH) }
    }

    /**
     * Title screen: like a game's attract mode. The three keyboards glide across in a slow parade under "3 new
     * themes", and "Press any button" blinks underneath (and any button does end it).
     */
    private fun titleScreen(c: Canvas, w: Int, h: Int, u: Float) {
        val below = 5 + 9 + 12 * (lines("Press any button", w) - 1)                    // the blinking line(s) under the parade
        val kh = min(h - 16 - below - 6, w / 4); val kw = (kh * 1.6f).toInt(); val gap = kw / 3
        val y = 16 + max(0, (h - 16 - below - kh) / 2)
        val span = THEMES.size * (kw + gap)
        val shift = (u * 26f) % span
        for (rep in 0..2) THEMES.forEachIndexed { i, th ->
            val x = (rep * span + i * (kw + gap) - shift - span + w / 2f).toInt()
            if (x > w || x + kw < 0) return@forEachIndexed
            clipIn(c, x, y, x + kw, y + kh); c.drawColor(th.bg); keyboard(c, th, x + 2, y + 2, kw - 4, kh - 4, ((u * 0.6f + i * 0.33f) % 1f)); clipOut(c)
        }
        text.color = WHITE; label(c, "3 new themes", w, y - 7)
        if ((u * 1.6f).toInt() % 2 == 0) { text.color = GREY; labelFrom(c, "Press any button", w, y + kh + 5) }
    }

    /** One theme on the whole screen: its keyboard, its own highlight gliding along ([p] 0..1), its name below. */
    private fun themeScreen(c: Canvas, th: Theme, w: Int, h: Int, p: Float) {
        val nameRoom = 18                                                             // a line of words plus its gap
        val kh = min(h - nameRoom - 10, (w * 0.6f).toInt()); val kw = min(w - 12, (kh * 1.7f).toInt())
        val kx = (w - kw) / 2; val ky = max(4, (h - nameRoom - kh) / 2)
        keyboard(c, th, kx, ky, kw, kh, p)
        text.color = th.ink; labelFrom(c, th.name, w, ky + kh + 5)
    }
    /**
     * A small keyboard in [th]'s colours in the box [x],[y],[w],[h] (art pixels): three rows of letter keys and a
     * bottom row with its special keys. [p] 0..1 moves the highlight along the top row; below 0 there's none.
     */
    private fun keyboard(c: Canvas, th: Theme, x: Int, y: Int, w: Int, h: Int, p: Float) {
        android.graphics.Rect(x, y, x + w, y + h).let { if (it.intersect(visible)) boxes += it }
        val cols = 6; val rows = 4; val g = max(1, w / 40)
        val kw = (w - (cols - 1) * g) / cols; val kh = (h - (rows - 1) * g) / rows
        if (kw < 2 || kh < 2) return
        val ox = x + (w - (cols * kw + (cols - 1) * g)) / 2; val oy = y + (h - (rows * kh + (rows - 1) * g)) / 2
        fun key(cx: Int, cy: Int, cw: Int, face: Int) {
            val l = (ox + cx * (kw + g)).toFloat(); val t = (oy + cy * (kh + g)).toFloat(); val r = l + cw * kw + (cw - 1) * g; val b = t + kh
            if (th.edge != 0) { ink.color = th.edge; c.drawRect(l, t, r, b, ink); ink.color = face; c.drawRect(l, t, r, b - 1, ink) }
            else { ink.color = th.outline; c.drawRect(l, t, r, b, ink); ink.color = face; c.drawRect(l + 1, t + 1, r - 1, b - 1, ink) }
            if (kh >= 5 && cw == 1) { ink.color = th.ink; val mx = (l + r) / 2; val my = (t + b) / 2; c.drawRect(mx - 1, my - 1, mx + 1, my + 1, ink) }   // a letter, as a dot
        }
        for (r in 0 until 3) for (k in 0 until cols) key(k, r, 1, th.key)
        key(0, 3, 1, th.special[0]); key(1, 3, 4, th.key); key(5, 3, 1, th.special[1])
        if (p < 0f) return
        // the highlight glides key to key along the top row, like a held direction, and rests on the fourth
        val stops = 3f; val q = min(stops, p * (stops + 1.2f)); val k0 = q.toInt().coerceAtMost(stops.toInt()); val e = SecretScreen.ease(q - k0)
        val at = k0 + if (k0 < stops) e else 0f
        val l = ox + at * (kw + g); val t = oy.toFloat(); val r = l + kw; val b = t + kh
        when (th.highlight) {
            Glow.FILL -> { ink.color = th.accent; c.drawRect(l, t, r, b, ink) }
            Glow.CURSOR -> { ink.color = th.accent; frameF(c, l - 1, t - 1, r + 1, b + 1); ink.color = Color.WHITE; c.drawRect(l, t, l + 1, t + 1, ink) }
            Glow.GLOW -> { ink.color = th.accent; frameF(c, l - 1, t - 1, r + 1, b + 1); ink.color = (th.accent and 0x00FFFFFF) or 0x66000000; frameF(c, l - 2, t - 2, r + 2, b + 2) }
        }
    }
    private fun frameF(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        c.drawRect(l, t, r, t + 1, ink); c.drawRect(l, b - 1, r, b, ink); c.drawRect(l, t, l + 1, b, ink); c.drawRect(r - 1, t, r, b, ink)
    }
    private fun frame(c: Canvas, l: Int, t: Int, r: Int, b: Int, colour: Int) { ink.color = colour; frameF(c, l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()) }

    /** The last card: "3 new themes", "in Look", and the same spark that waits on Look's tab. */
    private fun endCard(c: Canvas, w: Int, h: Int, f: Float) {
        val a = (255 * min(1f, f / 0.25f)).toInt()
        text.color = WHITE; text.alpha = a; label(c, "3 new themes", w, h / 2 - 2)
        text.color = GREY; text.alpha = a; label(c, "in Look", w, h / 2 + 12)
        text.alpha = 255
        if (f > 0.35f) PixelSpark.draw(c, w / 2f + tw("in Look") / 2f + 8f, h / 2f + 8f, 1f, ACCENT_SPARK, 1f, ink)
    }

    /** How many lines [s] takes (1, or 2 when too wide for one). */
    private fun lines(s: String, w: Int) = if (tw(s) <= w - 8 || ' ' !in s) 1 else 2
    /** [label] by its top edge: the words start [top] and take 9 px a line (12 px apart). */
    private fun labelFrom(c: Canvas, s: String, w: Int, top: Int) = label(c, s, w, top + 8 + if (lines(s, w) == 2) 6 else 0)
    /** Centred words, on two lines (split near the middle) when one is too wide. */
    private fun label(c: Canvas, s: String, w: Int, baseline: Int) {
        if (tw(s) <= w - 8 || ' ' !in s) { centre(c, s, w, baseline); return }
        val mid = s.length / 2; val cut = s.indices.filter { s[it] == ' ' }.minByOrNull { kotlin.math.abs(it - mid) }!!
        centre(c, s.substring(0, cut), w, baseline - 6); centre(c, s.substring(cut + 1), w, baseline + 6)
    }
    /** Words' width in art pixels: the pixel font is monospaced, 8 px a letter (so layout never depends on measuring). */
    private fun tw(s: String) = 8f * s.length
    private fun centre(c: Canvas, s: String, w: Int, baseline: Int) = leftText(c, s, ((w - tw(s)) / 2f).toInt(), baseline)
    private fun leftText(c: Canvas, s: String, x: Int, baseline: Int) {
        c.drawText(s, x.toFloat(), baseline.toFloat(), text)
        textBounds.union(x, baseline - 8, x + tw(s).toInt(), baseline + 1)
        val seen = android.graphics.Rect(x, baseline - 8, x + tw(s).toInt(), baseline + 1)
        if (seen.intersect(visible)) textLines += seen                    // only the part that can be seen
    }
    private fun stars(c: Canvas, w: Int, h: Int, t: Float, warp: Float) {
        val rnd = java.util.Random(4); val cx = w / 2f; val cy = h / 2f
        for (i in 0 until 60) {
            val a = rnd.nextFloat() * 2 * PI.toFloat(); val z = (rnd.nextFloat() + t * (0.05f + warp * 0.9f)) % 1f; val rr = 6 + z * max(w, h) * 0.6f
            val x0 = cx + cos(a) * rr; val y0 = cy + sin(a) * rr * 0.75f; val l = warp * z * 10
            ink.color = if (z > 0.65f) WHITE else if (z > 0.3f) GREY else DIM
            if (l < 1f) c.drawRect(x0.toInt().toFloat(), y0.toInt().toFloat(), x0.toInt() + 1f, y0.toInt() + 1f, ink)
            else c.drawLine(x0, y0, cx + cos(a) * (rr + l), cy + sin(a) * (rr + l) * 0.75f, ink)
        }
    }

    companion object {
        val DESIGNS = listOf("Spotlight", "Level select", "Power on", "Memory card", "Title screen")
        const val INTRO_S = 1.8f        // stars and "Secret found"
        const val EACH_S = 1.5f         // each theme's moment
        const val TOTAL_S = INTRO_S + 3 * EACH_S + 1.8f
        const val MIN_LETTER_DP = 10f   // smaller than this on the device's screen, and the reward lifts out
        const val MIN_COLS = 112        // the longest line (12 letters, 96 px) plus a margin
        const val MIN_ROWS = 72
        const val MAX_COLS = 176
        private val INK = Design.SECRET_INK; private val WHITE = Design.INK_LIGHT
        private val GREY = Design.SECRET_GREY; private val DIM = Design.SECRET_DIM; private val BODY = Design.SECRET_BODY
        private val ACCENT_SPARK = Design.BRAND_ACCENT
        private val CARD_BG = Design.SECRET_CARD_BG; private val CARD_LINE = Design.SECRET_CARD_LINE
        enum class Glow { CURSOR, FILL, GLOW }
        /** A theme as the reward draws it. [edge]: the darker lip under each key (0: an [outline] instead). */
        class Theme(val name: String, val bg: Int, val key: Int, val edge: Int, val outline: Int, val ink: Int, val special: IntArray, val accent: Int, val highlight: Glow)
        /** The three retro themes in pixels, from the themes themselves (Palette.retro), each with its own highlight. */
        val THEMES: List<Theme> get() = Palette.retro.mapIndexed { i, t ->
            Theme(t.name, t.background, t.key, edge = listOf(Design.RETRO_BRICK_LIP, Design.RETRO_INDIGO_LIP, 0)[i], outline = t.edge,
                ink = Contrast.inkFor(t.key), special = intArrayOf(t.specials[KeyType.LAYER] ?: t.special, t.specials[KeyType.DELETE] ?: t.special),
                accent = t.ownHighlight ?: Design.BRAND_ACCENT, highlight = listOf(Glow.CURSOR, Glow.FILL, Glow.GLOW)[i])
        }
        fun ease(x: Float) = x * x * (3 - 2 * x)
    }
}

/** The small pixel spark Look's tab carries while the new themes haven't been seen. */
object PixelSpark {
    /** 7 x 7 art pixels of [px] each, four points in [colour] with a white middle. */
    fun draw(c: Canvas, cx: Float, cy: Float, px: Float, colour: Int, alpha: Float, p: Paint) {
        fun cell(i: Int, j: Int, col: Int) { p.color = col; p.alpha = (255 * alpha).toInt(); c.drawRect(cx + (i - 3.5f) * px, cy + (j - 3.5f) * px, cx + (i - 2.5f) * px, cy + (j - 2.5f) * px, p) }
        for (k in 0..6) { cell(3, k, colour); cell(k, 3, colour) }
        for (i in 2..4) for (j in 2..4) cell(i, j, colour)
        cell(3, 3, Color.WHITE)
    }
}
