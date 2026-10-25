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
    /** {X} = a button, {SELECTx2} = press it twice quickly. */
    private val token = Regex("""\{([A-Z0-9]+?)(?:x(\d))?\}""")

    fun parse(text: String): List<HintPart> {
        val parts = mutableListOf<HintPart>()
        var last = 0
        token.findAll(text).forEach { match ->
            text.substring(last, match.range.first).trim().takeIf { it.isNotEmpty() }?.let { parts += HintPart.Text(it) }
            val button = ControllerButton.values().firstOrNull { it.name == match.groupValues[1] }
            val presses = match.groupValues[2].toIntOrNull() ?: 1
            parts += if (button != null) HintPart.Badge(button, presses) else HintPart.Text(match.value)
            last = match.range.last + 1
        }
        text.substring(last).trim().takeIf { it.isNotEmpty() }?.let { parts += HintPart.Text(it) }
        return parts
    }
}

/** Small uppercase label above a group of settings. */
class SectionHeader(context: Context, title: String) : TextView(context) {
    init {
        val dp = resources.displayMetrics.density
        text = title.uppercase()
        textSize = Design.TYPE_SECTION
        letterSpacing = 0.12f
        typeface = SettingsStyle.medium
        setTextColor(SettingsStyle.textSecondary)
        setPadding((Design.SPACE_XL * dp).roundToInt(), (Design.SPACE_XXL * dp).roundToInt(), 0, (Design.SPACE_S * dp).roundToInt())
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
                LayoutParams(LayoutParams.MATCH_PARENT, max(1, dp.roundToInt() / 2)).apply {
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
 * Every tab is a pill; a white indicator slides to the one you're on.
 */
class TabBar(
    context: Context,
    private val titles: List<String>,
    private val onTap: (Int) -> Unit
) : View(context) {

    private val dp = resources.displayMetrics.density
    private val painter = BadgePainter(dp).apply { scale = 1.45f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        typeface = SettingsStyle.medium
    }
    private val tabHeight = 36f * dp
    private val tabGap = Design.SPACE_S * dp
    private val tabPad = 18f * dp

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
    private val tabRects = mutableListOf<RectF>()

    fun select(index: Int) {
        if (index == selected) return
        selected = index
        anim?.cancel()
        anim = ValueAnimator.ofFloat(indicator, index.toFloat()).apply {
            duration = Design.MOTION_CONTROL
            interpolator = Design.EASE_OUT
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

    private fun layoutTabs() {
        tabRects.clear()
        val widths = titles.map { text.measureText(it) + 2 * tabPad }
        val total = widths.sum() + tabGap * (titles.size - 1)
        val side = Design.SPACE_L * dp + painter.width(ControllerButton.L1) + Design.SPACE_M * dp
        val available = width - 2 * side
        // Centre the tabs; if they don't fit, slide them so the chosen one stays visible
        var x = if (total <= available) side + (available - total) / 2f else {
            val before = widths.take(selected).sum() + tabGap * selected
            val wanted = side + available / 2f - (before + widths[selected] / 2f)
            wanted.coerceIn(side + available - total, side)
        }
        val top = (height - tabHeight) / 2f
        widths.forEach { w ->
            tabRects += RectF(x, top, x + w, top + tabHeight)
            x += w + tabGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        layoutTabs()
        val r = tabHeight / 2f
        fill.color = SettingsStyle.surface
        tabRects.forEach { canvas.drawRoundRect(it, r, r, fill) }

        // Sliding indicator
        val a = tabRects[floor(indicator).toInt().coerceIn(0, tabRects.lastIndex)]
        val b = tabRects[ceil(indicator).toInt().coerceIn(0, tabRects.lastIndex)]
        val t = indicator - floor(indicator)
        fill.color = SettingsStyle.accent
        canvas.drawRoundRect(
            a.left + (b.left - a.left) * t, a.top, a.right + (b.right - a.right) * t, a.bottom, r, r, fill
        )

        val baseline = tabRects[0].centerY() - (text.descent() + text.ascent()) / 2f
        titles.forEachIndexed { i, title ->
            val onIndicator = 1f - min(1f, abs(indicator - i))
            text.color = SettingsStyle.blend(SettingsStyle.textSecondary, SettingsStyle.onAccent, onIndicator)
            canvas.drawText(title, tabRects[i].centerX(), baseline, text)
        }

        // Shoulder button badges at each end
        val cy = height / 2f
        painter.draw(canvas, ControllerButton.L1, Design.SPACE_L * dp, cy)
        painter.draw(canvas, ControllerButton.R1, width - Design.SPACE_L * dp - painter.width(ControllerButton.R1), cy)
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
    private val painter = BadgePainter(dp).apply { scale = 1.35f; gapBadgeToBadge = 3f; gapBadgeToText = 6f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_CAPTION, resources.displayMetrics)
        typeface = SettingsStyle.medium
        color = SettingsStyle.textSecondary
    }
    private var groups: List<List<HintPart>> = emptyList()

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
        val room = width - 2 * Design.SPACE_L * dp
        if (total() > room) gap = Design.SPACE_L * dp
        while (shown.size > 1 && total() > room) shown = shown.filterIndexed { i, _ -> i != shown.size - 2 }
        var x = (width - total()) / 2f
        shown.forEach { parts ->
            painter.drawLine(canvas, parts, x, height / 2f, text)
            x += painter.measureLine(parts, text) + gap
        }
    }
}
