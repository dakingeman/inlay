package com.example.gridime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Turns "Hold {R2} and press {X}" into words and button badges. */
object HintText {
    /** {X} = a button, {SELECTx2} = press it twice quickly, {DUP}/{DDOWN}/{DLEFT}/{DRIGHT} = a D-pad direction. */
    private val token = Regex("""\{([A-Z0-9]+?)(?:x(\d))?\}""")

    fun parse(text: String): List<HintPart> {
        val parts = mutableListOf<HintPart>()
        var last = 0
        token.findAll(text).forEach { match ->
            text.substring(last, match.range.first).trim().takeIf { it.isNotEmpty() }?.let { parts += HintPart.Text(it) }
            val name = match.groupValues[1]
            val button = ControllerButton.values().firstOrNull { it.name == name }
            val presses = match.groupValues[2].toIntOrNull() ?: 1
            val dpad = mapOf("DUP" to 'U', "DDOWN" to 'D', "DLEFT" to 'L', "DRIGHT" to 'R')[name]
            parts += when { button != null -> HintPart.Badge(button, presses); dpad != null -> HintPart.Dpad(dpad); else -> HintPart.Text(match.value) }
            last = match.range.last + 1
        }
        text.substring(last).trim().takeIf { it.isNotEmpty() }?.let { parts += HintPart.Text(it) }
        return markHolds(parts)
    }

    private val holdWord = Regex("""(?i)(^|\s)hold$""")

    /**
     * The one rule for held buttons, so every hint follows it: a button right after the word "hold"
     * is held (the word goes into its HOLD capsule), and so is the first button of a combo
     * ("{R2} + {A}": hold RT, press A).
     */
    private fun markHolds(parts: List<HintPart>): List<HintPart> {
        val out = parts.toMutableList()
        out.indices.forEach { i ->
            val badge = out[i] as? HintPart.Badge ?: return@forEach
            val before = out.getOrNull(i - 1) as? HintPart.Text
            val after = out.getOrNull(i + 1) as? HintPart.Text
            if (before != null && holdWord.containsMatchIn(before.text)) {
                out[i] = badge.copy(held = true)
                out[i - 1] = HintPart.Text(before.text.replace(holdWord, "").trim())
            } else if (after != null && after.text.startsWith("+") && out.getOrNull(i - 1) !is HintPart.Badge) {
                out[i] = badge.copy(held = true)
            }
        }
        return out.filterNot { it is HintPart.Text && it.text.isEmpty() }.flatMap(::remoteHolds)
    }

    private val holdRemoteKey = Regex("""(?i)\bhold (OK|Back)\b""")

    /** A remote has no badges, so "hold OK" becomes the same capsule with the key's name in it. */
    private fun remoteHolds(part: HintPart): List<HintPart> {
        if (part !is HintPart.Text) return listOf(part)
        val out = mutableListOf<HintPart>()
        var last = 0
        holdRemoteKey.findAll(part.text).forEach { m ->
            part.text.substring(last, m.range.first).trim().takeIf { it.isNotEmpty() }?.let { out += HintPart.Text(it) }
            out += HintPart.HeldWord(m.groupValues[1])
            last = m.range.last + 1
        }
        part.text.substring(last).trim().takeIf { it.isNotEmpty() }?.let { out += HintPart.Text(it) }
        return out
    }
}

/**
 * Small quiet label above a group of settings, in sentence case ("Get started"): plain words, no spaced-out
 * capitals (brand voice and design bible rule 15; capitals dropped everywhere, 30 Oct 2026).
 */
class SectionHeader(context: Context, title: String) : TextView(context) {
    init {
        val dp = resources.displayMetrics.density
        text = title
        textSize = Design.TYPE_SECTION
        typeface = SettingsStyle.medium
        setTextColor(SettingsStyle.textSecondary)
        // starts where the row titles start (card inset + row padding), so text lines up down the page
        setPadding(((Design.CARD_INSET + Design.SPACE_L) * dp).roundToInt(), (Design.SPACE_XXL * dp).roundToInt(), 0, (Design.SPACE_S * dp).roundToInt())
    }
}

/** A rounded card that groups related rows, with hairlines between them. */
class SettingsCard(context: Context) : LinearLayout(context) {
    private val dp = resources.displayMetrics.density
    private var rows = 0

    init {
        orientation = VERTICAL
        val pad = (Design.CARD_INSET * dp).roundToInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = Design.RADIUS_CARD * dp
            setColor(SettingsStyle.surface)
            setStroke(max(1, (Design.STROKE_HAIRLINE * dp).roundToInt()), Design.EDGE_CARD)
        }
    }

    fun addRow(row: View) {
        if (rows > 0) {
            addView(View(context).apply { setBackgroundColor(SettingsStyle.divider) },
                LayoutParams(LayoutParams.MATCH_PARENT, max(1, (Design.STROKE_HAIRLINE * dp).roundToInt())).apply {
                    marginStart = (Design.SPACE_L * dp).roundToInt()
                    marginEnd = (Design.SPACE_L * dp).roundToInt()
                })
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rows++
    }
}

/**
 * Section tabs across the top, switched with the shoulder buttons.
 * Every tab is a pill; an indicator in the highlight colour slides to the one you're on.
 */
private const val TAB_MOTION_MS = Design.MOTION_CONTROL

class TabBar(
    context: Context,
    private val titles: List<String>,
    private val onTap: (Int) -> Unit
) : View(context) {

    /**
     * Without shoulder buttons (a TV remote), the tabs take the focus: up from the first row lands
     * here, left and right switch sections, down goes back to the list. With a remote there's also
     * a keyboard key at the end of the row ([onKeyboard]), since there's no Y.
     */
    var remote = false
        set(v) { field = v; invalidate() }
    var onKeyboard: () -> Unit = {}
    var onDown: () -> Unit = {}
    /** The slot the focus is on: a tab, or titles.size for the keyboard key. */
    private var focusSlot = 0
    private val keyRect = RectF()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isFocusable = true
        isFocusableInTouchMode = true      // reachable even after the screen was touched
        defaultFocusHighlightEnabled = false
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: android.graphics.Rect?) {
        super.onFocusChanged(gained, direction, previous)
        if (gained) focusSlot = selected
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val last = if (remote) titles.size else titles.lastIndex
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> if (focusSlot > 0) { focusSlot--; if (focusSlot < titles.size) onTap(focusSlot) }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> if (focusSlot < last) { focusSlot++; if (focusSlot < titles.size) onTap(focusSlot) }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_BUTTON_A ->
                if (focusSlot == titles.size) onKeyboard() else onDown()
            android.view.KeyEvent.KEYCODE_DPAD_UP -> Unit
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private val dp = resources.displayMetrics.density
    private val painter = BadgePainter(dp).apply { scale = 1.45f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_CAPTION, resources.displayMetrics)
        typeface = SettingsStyle.medium
    }
    private val tabHeight = 36f * dp
    private val tabGap = Design.SPACE_S * dp
    private val tabPad = 16f * dp

    var style: ButtonStyle = ButtonStyle.XBOX
        set(value) {
            field = value
            painter.style = value
            invalidate()
        }

    var selected = 0
        private set
    private var indicator = 0f
    private var anim: ValueAnimator? = null

    /** Where the highlight is (between tabs while it slides). */
    val indicatorAt: Float get() = indicator

    /**
     * 0 = the tabs as usual; 1 = folded into a slim line of bars at the bottom edge of this view, one per
     * tab and as wide as it, the one you're on in the highlight colour. Settings moves this with the
     * keyboard sliding up (and slides this view up so only that line shows): the tabs themselves shrink
     * into the line, so nothing appears from behind anything.
     */
    var collapse = 0f
        set(v) { val c = v.coerceIn(0f, 1f); if (field != c) { field = c; invalidate() } }
    /** How tall the folded line is (the part of this view still showing with the keyboard up). */
    val foldedHeightPx: Float get() = FOLDED_DP * dp
    private val tabRects = mutableListOf<RectF>()

    fun select(index: Int) {
        // The focus ring follows the section, however it was switched (L1/R1 too)
        if (focusSlot < titles.size) focusSlot = index
        if (index == selected) return
        selected = index
        anim?.cancel()
        anim = ValueAnimator.ofFloat(indicator, index.toFloat()).apply {
            // Quick, so the fill keeps up with fast presses; the ring and the slide ride on the same value
            duration = TAB_MOTION_MS
            interpolator = Design.EASE_STANDARD          // the fill moves across: eases in and out
            addUpdateListener {
                indicator = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (64f * dp).roundToInt())
    }

    /** Where the tabs may be drawn: between the badges (or the keyboard key) at the ends. */
    private var clipLeft = 0f
    private var clipRight = 0f

    private fun layoutTabs() {
        tabRects.clear()
        val widths = titles.map { text.measureText(it) + 2 * tabPad }
        val total = widths.sum() + tabGap * (titles.size - 1)
        // A remote has no L1/R1 (no badges at the ends); it has the keyboard key at the end instead
        val left = if (remote) Design.SPACE_L * dp else Design.SPACE_L * dp + painter.width(ControllerButton.L1) + Design.SPACE_M * dp
        val right = if (remote) Design.SPACE_L * dp + tabHeight * 1.3f + Design.SPACE_S * dp
            else Design.SPACE_L * dp + painter.width(ControllerButton.R1) + Design.SPACE_M * dp
        clipLeft = left; clipRight = width - right
        val available = width - left - right
        // Centre the tabs; if they don't fit, they slide (with the indicator) so the chosen one stays in view
        fun startFor(i: Int): Float {
            val before = widths.take(i).sum() + tabGap * i
            return (left + available / 2f - (before + widths[i] / 2f)).coerceIn(left + available - total, left)
        }
        var x = if (total <= available) left + (available - total) / 2f else {
            val lo = floor(indicator).toInt().coerceIn(0, titles.lastIndex)
            val hi = ceil(indicator).toInt().coerceIn(0, titles.lastIndex)
            val t = indicator - floor(indicator)
            startFor(lo) + (startFor(hi) - startFor(lo)) * t
        }
        val top = (height - tabHeight) / 2f
        widths.forEach { w ->
            tabRects += RectF(x, top, x + w, top + tabHeight)
            x += w + tabGap
        }
    }

    private val fade = Paint()

    /** A tab's pill as drawn right now: itself, or on its way to (or as) its bar in the folded line. */
    private fun shaped(rect: RectF): RectF {
        if (collapse <= 0f) return rect
        val c = collapse
        val e = c * c * (3 - 2 * c)                                  // smooth, so it settles softly
        val h = tabHeight + (FOLD_BAR_DP * dp - tabHeight) * e
        val cy = rect.centerY() + (height - FOLDED_DP * dp / 2f - rect.centerY()) * c
        val inset = FOLD_INSET_DP * dp * e
        return RectF(rect.left + inset, cy - h / 2f, rect.right - inset, cy + h / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        layoutTabs()
        val c = collapse
        val ringGap = 3.5f * dp + 2.5f * dp
        // Words, badges and rings belong to the full tabs: they're gone in the first third of the fold
        val chrome = (1f - c / 0.35f).coerceIn(0f, 1f)
        canvas.save()
        canvas.clipRect(clipLeft - ringGap, 0f, clipRight + ringGap, height.toFloat())
        fill.color = SettingsStyle.blend(SettingsStyle.surface, SettingsStyle.track, c)
        tabRects.forEach { val s = shaped(it); val rr = s.height() / 2f; canvas.drawRoundRect(s, rr, rr, fill) }

        // Sliding indicator
        val a = shaped(tabRects[floor(indicator).toInt().coerceIn(0, tabRects.lastIndex)])
        val b = shaped(tabRects[ceil(indicator).toInt().coerceIn(0, tabRects.lastIndex)])
        val t = indicator - floor(indicator)
        val r = a.height() / 2f
        fill.color = SettingsStyle.accent
        canvas.drawRoundRect(
            a.left + (b.left - a.left) * t, a.top, a.right + (b.right - a.right) * t, a.bottom, r, r, fill
        )

        if (chrome > 0f) {
            val baseline = a.centerY() - (text.descent() + text.ascent()) / 2f
            text.alpha = 255
            titles.forEachIndexed { i, title ->
                val onIndicator = 1f - min(1f, abs(indicator - i))
                text.color = SettingsStyle.blend(SettingsStyle.textSecondary, SettingsStyle.onAccent, onIndicator)
                text.alpha = (255 * chrome).roundToInt()
                canvas.drawText(title, tabRects[i].centerX(), baseline, text)
            }
            text.alpha = 255
        }

        // Focus (moving through the tabs without shoulder buttons): a ring round the tab. On the
        // chosen tab it rides with the fill, so the two always move as one
        if (c == 0f && hasFocus() && focusSlot < titles.size) {
            val f = if (focusSlot == selected) RectF(
                a.left + (b.left - a.left) * t, a.top, a.right + (b.right - a.right) * t, a.bottom) else tabRects[focusSlot.coerceIn(0, tabRects.lastIndex)]
            // On the tab's own edge, never outside it, so it can't reach the next tab (design audit, 28 Oct 2026)
            // On the chosen (accent-filled) tab the ring is drawn in the text colour, a little inside the edge
            val onChosen = focusSlot == selected
            ring.color = if (onChosen) SettingsStyle.onAccent else SettingsStyle.accent
            ring.strokeWidth = Design.STROKE_FOCUS * dp
            val g = ring.strokeWidth / 2f + if (onChosen) 2f * dp else 0f
            canvas.drawRoundRect(f.left + g, f.top + g, f.right - g, f.bottom - g, r - g, r - g, ring)
        }
        canvas.restore()

        // Tabs that run past the ends fade out softly instead of being cut
        val first = tabRects.first(); val last = tabRects.last()
        val fw = 16f * dp
        val bg = SettingsStyle.background
        val clear = bg and 0x00FFFFFF
        if (first.left < clipLeft - 1f) {
            // The solid end overlaps the clip line by 2 px, so no seam shows where the tab is cut (audit, 28 Oct 2026)
            val x0 = clipLeft - ringGap - 2f * dp
            fade.shader = android.graphics.LinearGradient(x0 + 2f * dp, 0f, x0 + 2f * dp + fw, 0f, bg, clear, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(x0, 0f, x0 + 2f * dp + fw, height.toFloat(), fade)
        }
        if (last.right > clipRight + 1f) {
            val x1 = clipRight + ringGap + 2f * dp
            fade.shader = android.graphics.LinearGradient(x1 - 2f * dp - fw, 0f, x1 - 2f * dp, 0f, clear, bg, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(x1 - 2f * dp - fw, 0f, x1, height.toFloat(), fade)
        }

        val cy = height / 2f
        if (chrome <= 0f) return
        val layer = if (chrome < 1f) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * chrome).roundToInt()) else -1
        if (remote) {
            // The keyboard key: a small keyboard drawn in a round key
            val right = width - Design.SPACE_L * dp
            keyRect.set(right - tabHeight * 1.3f, tabRects[0].top, right, tabRects[0].bottom)
            fill.color = SettingsStyle.surface
            canvas.drawRoundRect(keyRect, tabHeight / 2f, tabHeight / 2f, fill)
            Icons.keyboard(canvas, keyRect.centerX(), cy, 8f * dp, Icons.line(ring, dp, SettingsStyle.textSecondary), fill)
        } else {
            // Shoulder button badges at each end
            painter.draw(canvas, ControllerButton.L1, Design.SPACE_L * dp, cy)
            painter.draw(canvas, ControllerButton.R1, width - Design.SPACE_L * dp - painter.width(ControllerButton.R1), cy)
        }
        // The keyboard key's own focus ring
        if (hasFocus() && focusSlot >= titles.size) {
            val f = keyRect
            ring.color = SettingsStyle.accent
            ring.strokeWidth = Design.STROKE_FOCUS * dp
            val g = ring.strokeWidth / 2f
            canvas.drawRoundRect(f.left + g, f.top + g, f.right - g, f.bottom - g, tabHeight / 2f - g, tabHeight / 2f - g, ring)
        }
        if (layer >= 0) canvas.restoreToCount(layer)
    }

    companion object {
        const val FOLDED_DP = 16f       // the folded line: what still shows of the tabs with the keyboard up
        const val FOLD_BAR_DP = 4f      // each bar's thickness when folded
        const val FOLD_INSET_DP = 8f    // each bar is this much shorter than its tab at both ends
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            tabRects.indexOfFirst { it.contains(e.x, e.y) }.takeIf { it >= 0 }?.let { onTap(it) }
        }
        return true
    }
}

/** The control guide along the bottom, like a console: [A] Select  [B] Close ... */
class ControlBar(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private val painter = BadgePainter(dp).apply { scale = Design.BADGE_INLINE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_CAPTION, resources.displayMetrics)
        typeface = SettingsStyle.medium
        color = SettingsStyle.textSecondary
    }
    private var groups: List<List<HintPart>> = emptyList()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The tour's page dots, at the start of the bar ([count] pages, [current] lit), on small screens where a
     * row of their own would take the keyboard's room. The buttons then sit at the bar's end.
     */
    var dots: Pair<Int, Int>? = null
        set(v) { field = v; invalidate() }

    fun show(style: ButtonStyle, hints: List<String>) {
        painter.style = style
        groups = hints.map { HintText.parse(it) }
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (44f * dp).roundToInt())
    }

    private val hairline = Paint().apply { color = Design.EDGE_DIVIDER }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), max(1f, Design.STROKE_HAIRLINE * dp), hairline)
        // On narrow screens: first tighten the gaps, then leave out the least needed hints
        // (the one just before "Close" goes first; "Close" always stays).
        var shown = groups
        var gap = Design.SPACE_XL * dp
        fun total() = shown.sumOf { painter.measureLine(it, text).toDouble() }.toFloat() + gap * (shown.size - 1)
        val dotsW = dots?.let { (n, _) -> (n - 1) * (8f + Design.SPACE_XS) * dp + (24f + Design.SPACE_XL) * dp } ?: 0f
        val room = width - 2 * Design.SPACE_L * dp - dotsW
        if (total() > room) gap = Design.SPACE_L * dp
        while (shown.size > 1 && total() > room) shown = shown.filterIndexed { i, _ -> i != shown.size - 2 }
        val d = dots
        var x = (width - total()) / 2f
        if (d != null) {
            // Dots at the start, the same dots as the tour's own row: 8 dp, the current one a 24 dp pill
            val h = 8f * dp; var dx = Design.SPACE_L * dp; val cy = height / 2f
            for (i in 0 until d.first) {
                val w = if (i == d.second) 24f * dp else h
                dotPaint.color = if (i == d.second) SettingsStyle.accent else Design.DOT_IDLE
                canvas.drawRoundRect(dx, cy - h / 2, dx + w, cy + h / 2, h / 2, h / 2, dotPaint)
                dx += w + Design.SPACE_XS * dp
            }
            x = width - Design.SPACE_L * dp - total()
        }
        shown.forEach { parts ->
            painter.drawLine(canvas, parts, x, height / 2f, text)
            x += painter.measureLine(parts, text) + gap
        }
    }
}


