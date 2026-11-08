package com.example.gridime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * WHAT'S NEW, ONE DESIGNED CLOSE-UP PER PAGE (1.3, Emre: "each slide really showcases what it's talking about").
 *
 * Each [Scene] is drawn in one design space ([W] × [H] units) and scaled to fit whatever room the page gives it, centred,
 * so it looks the same on a wide handheld, a small vertical one, a phone and a TV. Motion is small and calm: a few
 * beats on a loop ([Scene.loopMs]), and with Android's "Remove animations" on, the scene's finished frame stays.
 * Badges are Inlay's own ([BadgePainter]), so they follow the button icons the user picked.
 */
class WhatsNewShowcase(context: Context, private val accent: Int, private val buttons: ButtonStyle) : View(context) {

    enum class Scene(val loopMs: Long) { TOUR(5200), SPLIT(5600), BUTTONS(6000), SYMBOLS(6000), MORE(6000), MORE_REMOTE(6000), SECRET(4600) }

    var scene: Scene? = null
        set(value) { field = value; start = SystemClock.uptimeMillis(); invalidate() }
    /** Checks and stills: draw this moment (ms into the loop) instead of the clock. */
    var frozenAt: Long? = null
    private var start = SystemClock.uptimeMillis()
    private val moving get() = frozenAt == null && WhatsNew.motionAllowed()
    /** Drawn small (a small handheld): the quiet extra lines step out, so what's left stays readable. */
    private val small get() = s / resources.displayMetrics.density < SMALL_DP_PER_UNIT

    private var s = 1f            // px per design unit
    private var ox = 0f; private var oy = 0f
    /** Where the scene is drawn in this view, in px (checks: it must sit inside its room). */
    internal val drawnForCheck: RectF get() { fit(); val r = area(); return RectF(x(r.left), y(r.top), x(r.right), y(r.bottom)) }
    /** How big one design unit is drawn, in px (checks). */
    internal val unitPxForCheck: Float get() { fit(); return s }
    /** Too small to read whole (a small 4:3 handheld): the scene zooms in on its heart ([CORE]); "And a few more" lays
     *  its tiles out in one row instead. */
    private var compact = false
    internal val compactForCheck: Boolean get() { fit(); return compact }
    private fun area(): RectF = if (compact) CORE[scene] ?: FULL else FULL
    private fun fit() {
        compact = false
        val whole = min(width / W, height / H)
        if (whole / resources.displayMetrics.density < COMPACT_BELOW_DP_PER_UNIT && scene in CORE) compact = true
        val r = area()
        s = min(width / r.width(), height / r.height())
        ox = (width - r.width() * s) / 2f - r.left * s; oy = (height - r.height() * s) / 2f - r.top * s
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val painter = BadgePainter(1f).apply { style = buttons; gapBadgeToBadge = 4f; gapBadgeToText = 5f }
    private val ink = Contrast.inkFor(accent)

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightSpec)
        else {
            val want = (w * H / W).toInt()
            if (MeasureSpec.getMode(heightSpec) == MeasureSpec.AT_MOST) min(want, MeasureSpec.getSize(heightSpec)) else want
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val sc = scene ?: return
        fit()
        painter.scale = BADGE_SCALE
        // The tiles arrive once and then keep going: the clock never starts over (Emre, 7 Nov: no fade back in)
        val now = frozenAt ?: if (moving) (SystemClock.uptimeMillis() - start).let { if (sc in KEEP_GOING) it else it % sc.loopMs } else STILL_AT[sc] ?: 0L
        val t = now / 1000f
        when (sc) {
            Scene.TOUR -> tour(canvas, t)
            Scene.SPLIT -> split(canvas, t)
            Scene.BUTTONS -> buttons(canvas, t)
            Scene.SYMBOLS -> symbols(canvas, t)
            Scene.MORE -> more(canvas, t, remote = false)
            Scene.MORE_REMOTE -> more(canvas, t, remote = true)
            Scene.SECRET -> Unit                 // drawn live by WhatsNewStage
        }
        if (moving && isShown) postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- drawing helpers (design units)
    private fun x(u: Float) = ox + u * s
    private fun y(u: Float) = oy + u * s
    private fun rect(l: Float, t: Float, w: Float, h: Float) = RectF(x(l), y(t), x(l + w), y(t + h))

    private fun panel(c: Canvas, l: Float, t: Float, w: Float, h: Float, r: Float = 14f, alpha: Float = 1f) {
        val box = rect(l, t, w, h)
        fill.color = Contrast.withAlpha(PANEL, (255 * alpha).toInt()); c.drawRoundRect(box, r * s, r * s, fill)
        line.color = Contrast.withAlpha(EDGE, (255 * alpha).toInt()); line.strokeWidth = max(1f, Design.STROKE_HAIRLINE * s)
        c.drawRoundRect(box, r * s, r * s, line)
    }

    /** A key: [on] fills it in your colour with a ring around it, like the keyboard's highlight. */
    private fun key(c: Canvas, l: Float, t: Float, w: Float, h: Float, label: String, on: Float = 0f, alpha: Float = 1f, size: Float = h * 0.5f) {
        val box = rect(l, t, w, h); val r = h * 0.2f * s
        if (on > 0f) {
            line.color = Contrast.withAlpha(accent, (255 * on * alpha).toInt()); line.strokeWidth = Design.STROKE_FOCUS * 0.8f * s
            val g = 2.6f * s; c.drawRoundRect(RectF(box.left - g, box.top - g, box.right + g, box.bottom + g), r + g, r + g, line)
        }
        fill.color = Contrast.withAlpha(blend(KEY, accent, on), (255 * alpha).toInt())
        c.drawRoundRect(box, r, r, fill)
        labelPx(c, label, box.centerX(), box.centerY(), size, blend(KEY_INK, ink, on), alpha, center = true, font = SettingsStyle.medium)
    }

    /** Words at design point ([cx], [cy]): the left edge (or the centre) and the middle of the line. */
    private fun label(c: Canvas, s0: String, cx: Float, cy: Float, size: Float, color: Int, alpha: Float = 1f,
                      center: Boolean = false, font: android.graphics.Typeface = SettingsStyle.regular) =
        labelPx(c, s0, x(cx), y(cy), size, color, alpha, center, font)

    private fun labelPx(c: Canvas, s0: String, cx: Float, cy: Float, size: Float, color: Int, alpha: Float = 1f,
                      center: Boolean = false, font: android.graphics.Typeface = SettingsStyle.regular) {
        text.typeface = font; text.textSize = size * s; text.color = Contrast.withAlpha(color, (Color.alpha(color) * alpha).toInt())
        text.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s0, cx, cy - (text.descent() + text.ascent()) / 2f, text)
    }

    /** One of Inlay's button badges at design point ([l], [cy]); [lit] rings it in your colour (held). Returns its right edge in units. */
    private fun badge(c: Canvas, b: ControllerButton, l: Float, cy: Float, lit: Float = 0f): Float {
        val right = pdpDraw(c, b, x(l), y(cy))
        if (lit > 0f) {
            val w = right - x(l); val r = 6.5f * BADGE_SCALE * s
            line.color = Contrast.withAlpha(accent, (255 * lit).toInt()); line.strokeWidth = Design.STROKE_FOCUS * 0.8f * s
            val g = 2.4f * s; c.drawRoundRect(RectF(x(l) - g, y(cy) - r - g, x(l) + w + g, y(cy) + r + g), r + g, r + g, line)
        }
        return (right - ox) / s
    }
    private fun pdpDraw(c: Canvas, b: ControllerButton, left: Float, cy: Float): Float {
        // The painter draws in px with its own dp: scale it to this scene
        val keep = painter.scale
        painter.scale = BADGE_SCALE * s
        val r = painter.draw(c, b, left, cy)
        painter.scale = keep
        return r
    }

    private fun field(c: Canvas, l: Float, t: Float, w: Float, typed: String, ghost: String = "", caret: Boolean = true, alpha: Float = 1f, size: Float = 15f) {
        val h = 30f; val box = rect(l, t, w, h)
        fill.color = Contrast.withAlpha(FIELD, (255 * alpha).toInt()); c.drawRoundRect(box, 9 * s, 9 * s, fill)
        line.color = Contrast.withAlpha(EDGE, (255 * alpha).toInt()); line.strokeWidth = max(1f, Design.STROKE_HAIRLINE * s); c.drawRoundRect(box, 9 * s, 9 * s, line)
        text.typeface = SettingsStyle.regular; text.textSize = size * s; text.textAlign = Paint.Align.LEFT
        val base = box.centerY() - (text.descent() + text.ascent()) / 2f
        var cx = box.left + 12 * s
        text.color = Contrast.withAlpha(Color.WHITE, (255 * alpha).toInt()); c.drawText(typed, cx, base, text); cx += text.measureText(typed)
        if (caret) { fill.color = Contrast.withAlpha(accent, (255 * alpha).toInt()); c.drawRect(cx + 0.5f * s, box.top + 7 * s, cx + 2f * s, box.bottom - 7 * s, fill) }
        if (ghost.isNotEmpty()) { text.color = Contrast.withAlpha(Color.WHITE, (90 * alpha).toInt()); c.drawText(ghost, cx + 3 * s, base, text) }
    }

    private fun tick(c: Canvas, cx: Float, cy: Float, size: Float, color: Int, progress: Float) {
        if (progress <= 0f) return
        line.color = color; line.strokeWidth = Design.STROKE_ICON * size / 11f * s; line.strokeCap = Paint.Cap.ROUND; line.strokeJoin = Paint.Join.ROUND
        val p = android.graphics.Path()
        val a = floatArrayOf(cx - size * 0.38f, cy + size * 0.02f); val b = floatArrayOf(cx - size * 0.1f, cy + size * 0.3f); val e = floatArrayOf(cx + size * 0.42f, cy - size * 0.3f)
        p.moveTo(x(a[0]), y(a[1]))
        val first = min(1f, progress * 2.4f)
        p.lineTo(x(a[0] + (b[0] - a[0]) * first), y(a[1] + (b[1] - a[1]) * first))
        if (progress > 1f / 2.4f) { val k = min(1f, (progress - 1f / 2.4f) / (1f - 1f / 2.4f)); p.lineTo(x(b[0] + (e[0] - b[0]) * k), y(b[1] + (e[1] - b[1]) * k)) }
        c.drawPath(p, line); line.strokeCap = Paint.Cap.BUTT
    }

    // ---------------------------------------------------------------- the scenes
    /** One tour turn in miniature: the card asks, Shift wears its RT badge, z types the capital. */
    private fun tour(c: Canvas, t: Float) {
        panel(c, 14f, 14f, 452f, 332f)
        val pressed = t > 2.4f
        field(c, 34f, 34f, 300f, if (pressed) "Z" else "", if (pressed) "elda" else "Zelda")
        label(c, "Type a capital Z", 34f, 92f, 17f, Color.WHITE, font = SettingsStyle.medium)
        // "Hold RT on Shift, then A on z", with real badges
        var u = 34f
        label(c, "Hold", u, 116f, 13f, MUTED); u += measure("Hold", 13f) + 5f
        u = badge(c, ControllerButton.R2, u, 116f, lit = if (t in 1.2f..3.2f) 1f else 0f) + 5f
        label(c, "on Shift, then", u, 116f, 13f, MUTED); u += measure("on Shift, then", 13f) + 5f
        u = badge(c, ControllerButton.A, u, 116f, lit = if (t in 2.2f..2.6f) 1f else 0f) + 5f
        label(c, "on z", u, 116f, 13f, MUTED)
        val k = 40f; val kh = 31f; val step = 44f
        "asdfgh".forEachIndexed { i, ch -> key(c, 34f + i * step, 146f, k, kh, ch.toString()) }
        val ring = if (t < 2.4f) 0.55f + 0.45f * pulse(t, 1.4f) else 0f
        key(c, 34f, 186f, 60f, kh, "⇧", on = if (t in 1.2f..3.2f) 1f else ring)
        // RT on the Shift key's corner, as the tour shows it
        badge(c, ControllerButton.R2, 34f + 60f - 18f, 186f, lit = 0f)
        "zxcv".forEachIndexed { i, ch -> key(c, 102f + i * step, 186f, k, kh, ch.toString(), on = if (ch == 'z' && t in 2.2f..2.8f) 1f else 0f) }
        "qwerty".forEachIndexed { i, ch -> key(c, 34f + i * step, 226f, k, kh, ch.toString(), alpha = 0.32f) }
        // the turn counter
        panel(c, 352f, 34f, 92f, 30f, r = 15f)
        label(c, "1 of 4", 398f, 49f, 13f, Color.WHITE, center = true, font = SettingsStyle.medium)
        if (!small) label(c, "Every button is taught right on its key.", 34f, 312f, 12.5f, MUTED)
    }

    /** The keyboard parts down the middle and the halves glide out to your thumbs, the app showing between them. */
    private fun split(c: Canvas, t: Float) {
        val seam = ((t - 0.8f) / 0.6f).coerceIn(0f, 1f)
        val e = ease(((t - 1.5f) / 1.0f).coerceIn(0f, 1f)) * (1f - ((t - 5.0f) / 0.5f).coerceIn(0f, 1f))
        val app = 0.28f + 0.72f * e
        field(c, 90f, 18f, 300f, "Super Met", alpha = app)
        for (i in 0 until 4) {
            val l = 90f + i * 76f
            panel(c, l, 60f, 68f, 92f, r = 8f, alpha = app)
            fill.color = Contrast.withAlpha(EDGE, (255 * app).toInt()); c.drawRoundRect(rect(l + 8f, 138f, 36f, 4f), 2 * s, 2 * s, fill)
        }
        val wHalf = 202f; val hHalf = 118f; val top = 190f + 22f * e
        val lx = 38f - 34f * e; val rx = 240f + 34f * e
        panel(c, lx, top, wHalf, hHalf)
        panel(c, rx, top, wHalf, hHalf)
        fun half(x0: Float, rows: List<String>) = rows.forEachIndexed { r, row ->
            row.forEachIndexed { i, ch -> key(c, x0 + r * 9f + i * 36f, top + 11f + r * 33f, 32f, 27f, ch.toString(), size = 14f) }
        }
        half(lx + 13f, listOf("qwert", "asdfg", "zxcv"))
        half(rx + 15f, listOf("yuiop", "hjkl", "bnm"))
        if (seam > 0f && e < 1f) {
            fill.color = Contrast.withAlpha(accent, (255 * seam * (1f - e)).toInt())
            val mx = (lx + wHalf + rx) / 2f
            c.drawRoundRect(rect(mx - 1.5f, top + 6f, 3f, hHalf - 12f), 1.5f * s, 1.5f * s, fill)
        }
        if (e > 0.5f) {
            val a = (e - 0.5f) * 2f
            text.alpha = 255
            labelWithStick(c, ControllerButton.L3, lx + wHalf / 2f, top + hHalf + 18f, a)
            labelWithStick(c, ControllerButton.R3, rx + wHalf / 2f, top + hHalf + 18f, a)
        }
    }
    private fun labelWithStick(c: Canvas, b: ControllerButton, cx: Float, cy: Float, a: Float) {
        if (a <= 0f) return
        val save = c.saveLayerAlpha(null, (255 * a).toInt())
        val w = (painter.apply { scale = BADGE_SCALE }.width(b)) // in painter's units (dp = 1)
        badge(c, b, cx - w / 2f, cy)
        c.restoreToCount(save)
    }

    /** Your four face buttons tick in one by one, then each shows its job. */
    private fun buttons(c: Canvas, t: Float) {
        val cx = 150f; val cy = 176f; val d = 64f; val r = 27f
        val order = listOf("left" to (cx - d to cy), "top" to (cx to cy - d), "right" to (cx + d to cy), "bottom" to (cx to cy + d))
        val jobs = mapOf("bottom" to "Select", "right" to "Back", "left" to "Delete", "top" to "Space")
        order.forEachIndexed { i, (name, p) ->
            val appear = ((t - 0.5f - i * 0.55f) / 0.4f).coerceIn(0f, 1f)
            val select = name == "bottom" && t > 3.2f
            fill.color = if (select) accent else KEY
            c.drawCircle(x(p.first), y(p.second), r * s, fill)
            line.color = if (select) accent else Contrast.withAlpha(Color.WHITE, Design.ALPHA_INK_STRONG); line.strokeWidth = Design.STROKE_FOCUS * s
            c.drawCircle(x(p.first), y(p.second), r * s, line)
            tick(c, p.first, p.second, 26f, if (select) ink else accent, appear)
            val jobAlpha = ((t - 3.0f) / 0.5f).coerceIn(0f, 1f)
            if (jobAlpha > 0f) {
                val (lx, ly) = when (name) { "top" -> cx to cy - d - r - 14f; "bottom" -> cx to cy + d + r + 14f
                    "left" -> cx - d - r - 36f to cy; else -> cx + d + r + 34f to cy }
                label(c, jobs.getValue(name), lx, ly, 15f, if (select) Contrast.accentText(accent, Color.BLACK) else Color.WHITE, jobAlpha, center = true, font = SettingsStyle.medium)
            }
        }
        panel(c, 300f, 70f, 160f, 212f)
        listOf("Press left", "Press top", "Press right", "Press bottom").forEachIndexed { i, s0 ->
            val done = ((t - 0.5f - i * 0.55f) / 0.4f).coerceIn(0f, 1f)
            tick(c, 320f, 96f + i * 32f, 14f, accent, done)
            label(c, s0, 334f, 96f + i * 32f, 13f, if (done > 0f) Color.WHITE else MUTED, font = SettingsStyle.medium)
        }
        val end = ((t - 3.0f) / 0.5f).coerceIn(0f, 1f)
        label(c, "Bottom selects", 316f, 238f, 13f, Contrast.accentText(accent, PANEL), end, font = SettingsStyle.medium)
        if (!small) label(c, "Each controller keeps its own", 316f, 258f, 11f, MUTED, end)
    }

    /** A close-up of the symbols: LT held, € pressed, its relatives rise and $ is picked. */
    private fun symbols(c: Canvas, t: Float) {
        panel(c, 14f, 34f, 452f, 214f, r = 18f)
        val lt = if (t in 0.4f..4.2f) 1f else 0f
        var u = badge(c, ControllerButton.L2, 34f, 60f, lit = lt) + 6f
        label(c, "held", u, 60f, 13f, if (lt > 0f) Color.WHITE else MUTED, font = SettingsStyle.medium)
        val k = 46f; val kh = 38f; val step = 52f
        val onEuro = t in 1.0f..1.6f || t > 3.0f && t < 4.2f
        "@#$%&-+".forEachIndexed { i, ch -> key(c, 34f + i * step, 98f, k, kh, ch.toString(), size = 18f) }
        "*\"':!?€".forEachIndexed { i, ch -> key(c, 34f + i * step, 146f, k, kh, ch.toString(), on = if (ch == '€' && onEuro) 1f else 0f, size = 18f) }
        "()/;=,.".forEachIndexed { i, ch -> key(c, 34f + i * step, 194f, k, kh, ch.toString(), alpha = 0.3f, size = 18f) }
        // the relatives, rising from € while A is held
        val rise = ease(((t - 1.6f) / 0.3f).coerceIn(0f, 1f)) * (1f - ((t - 3.0f) / 0.2f).coerceIn(0f, 1f))
        if (rise > 0f) {
            val list = Accents.symbolVariants('€', Language.GERMAN).orEmpty().take(5)
            val cw = 38f; val gap = 4f; val trayW = list.size * cw + (list.size - 1) * gap + 12f
            val tl = 34f + 6 * step + k - trayW; val tt = 52f + 8f * (1f - rise)
            val save = c.saveLayerAlpha(null, (255 * rise).toInt())
            panel(c, tl, tt, trayW, cw + 12f, r = 12f)
            val pick = if (t > 2.3f) 1 else 0
            list.forEachIndexed { i, sym -> key(c, tl + 6f + i * (cw + gap), tt + 6f, cw, cw, sym, on = if (i == pick) 1f else 0f, size = 17f) }
            c.restoreToCount(save)
        }
        val typed = t > 3.0f
        field(c, 34f, 272f, 220f, if (typed) "Ticket: 20$" else "Ticket: 20")
        if (!small) label(c, if (t > 4.2f) "Back on your letters" else "Let go to get back", 270f, 287f, 12.5f, MUTED)
    }

    /** The smaller things, each as a small tile. On a TV remote: no RB and no microphone. */
    private fun more(c: Canvas, t: Float, remote: Boolean) {
        fun appear(i: Int) = ease(((t - 0.15f - i * 0.15f) / 0.4f).coerceIn(0f, 1f))
        // a remote has no voice hand-off; it gets the full stop's punctuation in that place
        val tiles = if (remote) listOf(0, 4, 2, 3) else listOf(0, 1, 2, 3)
        if (compact) return moreInARow(c, t, tiles, remote)
        val boxes = listOf(RectF(14f, 14f, 234f, 172f), RectF(246f, 14f, 466f, 172f), RectF(14f, 186f, 234f, 346f), RectF(246f, 186f, 466f, 346f))
        tiles.forEachIndexed { n, which ->
            val b = boxes[n]; val a = appear(n)
            if (a <= 0f) return@forEachIndexed
            val save = c.saveLayerAlpha(null, (255 * a).toInt())
            c.translate(0f, (1f - a) * 8f * s)
            panel(c, b.left, b.top, b.width(), b.height(), r = 14f)
            val l = b.left + 14f; val w = b.width() - 28f
            when (which) {
                0 -> {   // Find
                    field(c, l, b.top + 16f, w, "sou", caret = blink(t))
                    if (!remote) badge(c, ControllerButton.R1, l + w - 34f, b.top + 31f)
                    val row = rect(l, b.top + 56f, w, 24f)
                    fill.color = Contrast.withAlpha(accent, Design.ALPHA_TINT_SOFT); c.drawRoundRect(row, 7 * s, 7 * s, fill)
                    line.color = accent; line.strokeWidth = Design.STROKE_FOCUS * 0.7f * s; c.drawRoundRect(row, 7 * s, 7 * s, line)
                    label(c, "Sound pack", l + 10f, b.top + 68f, 12.5f, Color.WHITE, font = SettingsStyle.medium)
                    if (!small) label(c, "Sound in silent mode", l + 10f, b.top + 94f, 11.5f, MUTED)
                    label(c, "Find any setting", l, b.bottom - 18f, 14f, Color.WHITE, font = SettingsStyle.medium)
                }
                1 -> {   // Voice typing
                    label(c, "Copy", l, b.top + 34f, 12.5f, MUTED); label(c, "Paste", l + 44f, b.top + 34f, 12.5f, MUTED)
                    key(c, l + 92f, b.top + 18f, 34f, 32f, "", on = 1f)
                    mic(c, l + 109f, b.top + 34f)
                    for (i in 0 until 24) {
                        val h = 4f + 18f * kotlin.math.abs(sin(i * 0.7f + t * 3f) * kotlin.math.cos(i * 0.23f))
                        fill.color = Contrast.withAlpha(accent, (80 + 120 * h / 22f).toInt())
                        c.drawRoundRect(rect(l + i * 8.2f, b.top + 82f - h / 2f, 4.4f, h), 2.2f * s, 2.2f * s, fill)
                    }
                    label(c, "Voice typing", l, b.bottom - 18f, 14f, Color.WHITE, font = SettingsStyle.medium)
                }
                2 -> {   // Languages per app
                    // an example: three apps, each with its own language
                    listOf("Chat" to "DE", "Browser" to "EN", "Notes" to "ES").forEachIndexed { i, (app, lang) ->
                        val r0 = rect(l, b.top + 14f + i * 30f, w, 25f)
                        fill.color = KEY; c.drawRoundRect(r0, 8 * s, 8 * s, fill)
                        label(c, app, l + 10f, b.top + 26.5f + i * 30f, 12.5f, Color.WHITE, font = SettingsStyle.medium)
                        val pill = RectF(r0.right - 34f * s, r0.top + 5f * s, r0.right - 8f * s, r0.bottom - 5f * s)
                        if (i == 0) { fill.color = accent; c.drawRoundRect(pill, 5 * s, 5 * s, fill) }
                        else { line.color = Color.WHITE; line.strokeWidth = Design.STROKE_HAIRLINE * 1.2f * s; c.drawRoundRect(pill, 5 * s, 5 * s, line) }
                        text.typeface = SettingsStyle.medium; text.textSize = 10.5f * s; text.textAlign = Paint.Align.CENTER
                        text.color = if (i == 0) ink else Color.WHITE
                        c.drawText(lang, pill.centerX(), pill.centerY() - (text.descent() + text.ascent()) / 2f, text)
                    }
                    label(c, "Each app keeps its language", l, b.bottom - 18f, 14f, Color.WHITE, font = SettingsStyle.medium)
                }
                3 -> {   // Back up and restore
                    val fx = l; val fy = b.top + 18f
                    line.color = Color.WHITE; line.strokeWidth = Design.STROKE_ICON * s; line.strokeJoin = Paint.Join.ROUND
                    val p = android.graphics.Path().apply {
                        moveTo(x(fx), y(fy)); lineTo(x(fx + 18f), y(fy)); lineTo(x(fx + 26f), y(fy + 8f)); lineTo(x(fx + 26f), y(fy + 34f)); lineTo(x(fx), y(fy + 34f)); close()
                    }
                    c.drawPath(p, line)
                    label(c, "inlay-backup.json", fx + 36f, fy + 9f, 12.5f, Color.WHITE, font = SettingsStyle.medium)
                    val done = ((t - 1.6f) / 0.5f).coerceIn(0f, 1f)
                    tick(c, fx + 41f, fy + 27f, 10f, accent, done)
                    label(c, "Backed up", fx + 50f, fy + 27f, 12f, Contrast.accentText(accent, PANEL), done, font = SettingsStyle.medium)
                    label(c, "Back up your settings", l, b.bottom - 18f, 14f, Color.WHITE, font = SettingsStyle.medium)
                }
                4 -> {   // Hold the full stop: its punctuation opens, the highlight steps to the question mark
                    // one highlight at a time, as on the keyboard: the full stop, then its first relative, then the question mark
                    val open = ((t - 0.9f) / 0.25f).coerceIn(0f, 1f); val at = if (t > 1.7f) 2 else 0
                    key(c, l, b.top + 14f, 34f, 32f, ".", on = 1f - open)
                    listOf(".", ",", "?", "!", "\u2026").forEachIndexed { i, ch ->
                        key(c, l + i * 36f, b.top + 60f, 32f, 30f, ch, on = if (i == at) 1f else 0f, alpha = open)
                    }
                    label(c, "Hold the full stop", l, b.bottom - 18f, 14f, Color.WHITE, font = SettingsStyle.medium)
                }
            }
            c.restoreToCount(save)
        }
    }

    /** Small screens: the same four (or three) things in one row, each a picture and one word. */
    private fun moreInARow(c: Canvas, t: Float, tiles: List<Int>, remote: Boolean) {
        val gap = 10f; val tw = (W - 28f - gap * (tiles.size - 1)) / tiles.size; val top = 20f; val th = 140f
        tiles.forEachIndexed { n, which ->
            val a = ease(((t - 0.15f - n * 0.15f) / 0.4f).coerceIn(0f, 1f))
            if (a <= 0f) return@forEachIndexed
            val save = c.saveLayerAlpha(null, (255 * a).toInt())
            val l = 14f + n * (tw + gap)
            panel(c, l, top, tw, th, r = 14f)
            val cx = l + tw / 2f
            when (which) {
                0 -> { field(c, l + 10f, top + 24f, tw - 20f, "sou", caret = blink(t), size = 14f)
                    if (!remote) badge(c, ControllerButton.R1, cx - 12f, top + 76f) }
                1 -> { key(c, cx - 18f, top + 18f, 36f, 34f, "", on = 1f); mic(c, cx, top + 35f)
                    for (i in 0 until 9) { val h = 4f + 16f * kotlin.math.abs(sin(i * 0.9f + t * 3f))
                        fill.color = Contrast.withAlpha(accent, Design.ALPHA_INK_STRONG); c.drawRoundRect(rect(cx - 40f + i * 9f, top + 78f - h / 2f, 4.4f, h), 2.2f * s, 2.2f * s, fill) } }
                2 -> listOf("DE", "EN", "ES").forEachIndexed { i, lang ->
                    val pill = rect(cx - 22f, top + 12f + i * 26f, 44f, 20f)
                    if (i == 0) { fill.color = accent; c.drawRoundRect(pill, 6 * s, 6 * s, fill) }
                    else { line.color = Color.WHITE; line.strokeWidth = Design.STROKE_HAIRLINE * 1.2f * s; c.drawRoundRect(pill, 6 * s, 6 * s, line) }
                    labelPx(c, lang, pill.centerX(), pill.centerY(), 12f, if (i == 0) ink else Color.WHITE, center = true, font = SettingsStyle.medium) }
                3 -> { line.color = Color.WHITE; line.strokeWidth = Design.STROKE_ICON * s; line.strokeJoin = Paint.Join.ROUND
                    val fx = cx - 13f; val fy = top + 18f
                    c.drawPath(android.graphics.Path().apply { moveTo(x(fx), y(fy)); lineTo(x(fx + 18f), y(fy)); lineTo(x(fx + 26f), y(fy + 8f))
                        lineTo(x(fx + 26f), y(fy + 34f)); lineTo(x(fx), y(fy + 34f)); close() }, line)
                    tick(c, cx, top + 74f, 16f, accent, ((t - 1.6f) / 0.5f).coerceIn(0f, 1f)) }
                4 -> { val open = ((t - 0.9f) / 0.25f).coerceIn(0f, 1f)
                    key(c, cx - 17f, top + 14f, 34f, 32f, ".", on = 1f - open)
                    listOf(",", "?", "!").forEachIndexed { i, ch -> key(c, cx - 40f + i * 27f, top + 62f, 24f, 26f, ch, on = (if (i == 1) (if (t > 1.7f) 1f else 0f) else if (i == 0 && t <= 1.7f) 1f else 0f) * open, alpha = open) } }
            }
            label(c, listOf("Find", "Voice", "Languages", "Backup", "Punctuation")[which], cx, top + th - 22f, 15f, Color.WHITE, center = true, font = SettingsStyle.medium)
            c.restoreToCount(save)
        }
    }

    /** The keyboard's own microphone icon (Icons.mic), in its line weight. */
    private fun mic(c: Canvas, cx: Float, cy: Float) {
        line.color = ink; line.strokeWidth = Design.STROKE_ICON * s; line.strokeCap = Paint.Cap.ROUND; line.strokeJoin = Paint.Join.ROUND
        Icons.mic(c, x(cx), y(cy), 8f * s, line)
        line.strokeCap = Paint.Cap.BUTT
    }


    private fun measure(s0: String, size: Float): Float { text.typeface = SettingsStyle.regular; text.textSize = size * s; return text.measureText(s0) / s }
    private fun pulse(t: Float, period: Float) = (0.5f + 0.5f * sin(t / period * 2f * Math.PI.toFloat())).coerceIn(0f, 1f)
    private fun blink(t: Float) = !moving || (t % 1.1f) < 0.6f
    private fun ease(v: Float) = if (v < .5f) 4 * v * v * v else 1 - Math.pow((-2.0 * v + 2), 3.0).toFloat() / 2f
    private fun blend(a: Int, b: Int, k: Float): Int {
        if (k <= 0f) return a; if (k >= 1f) return b
        return Contrast.over(Contrast.withAlpha(b, (255 * k).toInt()), a)
    }

    companion object {
        const val W = 480f
        const val H = 360f
        private const val BADGE_SCALE = 1.25f
        private const val SMALL_DP_PER_UNIT = 0.8f
        private val KEEP_GOING = setOf(Scene.MORE, Scene.MORE_REMOTE)
        private const val COMPACT_BELOW_DP_PER_UNIT = 0.55f
        private val FULL = RectF(0f, 0f, W, H)
        /** Each scene's heart, for small screens: what it zooms in on. */
        private val CORE = mapOf(
            Scene.TOUR to RectF(24f, 76f, 312f, 262f),
            Scene.SPLIT to RectF(0f, 180f, W, 360f),
            Scene.BUTTONS to RectF(60f, 66f, 470f, 286f),
            Scene.SYMBOLS to RectF(14f, 34f, 466f, 248f),
            Scene.MORE to RectF(0f, 10f, W, 170f),
            Scene.MORE_REMOTE to RectF(0f, 10f, W, 170f))
        private val PANEL = Design.SHOWCASE_PANEL
        private val EDGE = Design.TRACK
        private val KEY = Design.SHOWCASE_KEY
        private val KEY_INK = Design.SHOWCASE_KEY_INK
        private val FIELD = Design.SHOWCASE_FIELD
        private val MUTED = Design.TEXT_SECONDARY
        /** With motion off: the moment that tells the whole story. */
        private val STILL_AT = mapOf(Scene.TOUR to 2500L, Scene.SPLIT to 3200L, Scene.BUTTONS to 4500L, Scene.SYMBOLS to 2600L,
            Scene.MORE to 3000L, Scene.MORE_REMOTE to 3000L)
    }
}
