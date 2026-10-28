package com.example.gridime

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** A piece of a hint line: a controller badge or a few words. */
sealed class HintPart {
    /** [presses] > 1 draws a small "×2" inside the badge: press it that many times quickly. */
    data class Badge(val button: ControllerButton, val presses: Int = 1, val held: Boolean = false) : HintPart()
    data class Text(val text: String) : HintPart()
    /** A remote's key held down ("hold OK"): the same HOLD capsule, with the key's name. */
    data class HeldWord(val word: String) : HintPart()
}

/**
 * Draws controller button badges (Xbox, PlayStation or Switch) on a dark disc,
 * so they stay readable on any key colour. Shared by the keyboard and the Settings app.
 */
class BadgePainter(private val dp: Float) {

    var style = ButtonStyle.XBOX
    var scale = 1f
    /**
     * Breathing room in hint lines (dp, before scaling). The keyboard's chips are tight;
     * Settings and the tour set these wider, so badges and words never look jammed together.
     */
    var gapBadgeToBadge = 2f
    var gapBadgeToText = 4f

    private val plate = Color.parseColor("#F20A0A0D")
    private val edge = Color.parseColor("#4DFFFFFF")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val radius: Float get() = 6.5f * dp * scale
    val height: Float get() = radius * 2

    private fun baseWidth(button: ControllerButton): Float = when {
        button.isStick -> height + shoulderPaint().measureText(stickText(button)) + 6f * dp * scale
        else -> shoulderText(button)?.let { shoulderPaint().measureText(it) + 8f * dp * scale } ?: height
    }

    private fun countText(presses: Int) = "×$presses"
    private fun countPaint() = label.apply { textSize = 7.5f * dp * scale }

    /** Extra width for the "×2" tail, tucked inside the same pill. */
    private fun tailWidth(presses: Int): Float =
        if (presses > 1) countPaint().measureText(countText(presses)) + 5f * dp * scale else 0f

    fun width(button: ControllerButton, presses: Int = 1): Float = baseWidth(button) + tailWidth(presses)

    // ---- Held: the button sits in a HOLD capsule with a double outline ---------------------
    private fun holdPaint() = label.apply { textSize = 6.5f * dp * scale; letterSpacing = 0.14f }
    private val holdPad get() = 2.5f * dp * scale

    /** Room for the softer second outline around the capsule, so it's never clipped. */
    private val holdHalo get() = 3.2f * dp * scale
    /** How tall a line needs to be: taller when it holds a HOLD capsule. */
    fun lineHeight(parts: List<HintPart>): Float =
        if (parts.any { (it is HintPart.Badge && it.held) || it is HintPart.HeldWord }) height + 2 * holdPad + 2 * holdHalo else height

    private fun holdLabelWidth() = 2 * holdHalo + holdPad + 5f * dp * scale + holdPaint().measureText("HOLD").also { label.letterSpacing = 0f } + 4f * dp * scale

    /** A held button's width: the word HOLD, then the badge, in one capsule. */
    fun heldWidth(button: ControllerButton): Float = holdLabelWidth() + width(button) + holdPad

    private fun wordPaint() = label.apply { textSize = 9f * dp * scale; letterSpacing = 0f }
    fun heldWordWidth(word: String): Float = holdLabelWidth() + wordPaint().measureText(word) + 6f * dp * scale

    /** Draws a remote key held down: HOLD, then its name, in the same capsule. */
    fun drawHeldWord(canvas: Canvas, word: String, left: Float, centerY: Float): Float {
        val w = heldWordWidth(word)
        val right = holdCapsule(canvas, left, w, centerY)
        val p = wordPaint().apply { color = Color.WHITE; textAlign = Paint.Align.RIGHT }
        canvas.drawText(word, right - holdPad - 3f * dp * scale, centerY - (p.descent() + p.ascent()) / 2f, p)
        p.textAlign = Paint.Align.CENTER
        return left + w
    }

    /** Draws a held button (the HOLD capsule). Returns the right edge. */
    fun drawHeld(canvas: Canvas, button: ControllerButton, left: Float, centerY: Float): Float {
        val w = heldWidth(button)
        val right = holdCapsule(canvas, left, w, centerY)
        draw(canvas, button, right - holdPad - width(button), centerY)
        return left + w
    }

    /** The capsule itself: dark plate, accent outline, a softer second outline, and HOLD. */
    private fun holdCapsule(canvas: Canvas, left: Float, w: Float, centerY: Float): Float {
        val h = height + 2 * holdPad
        val box = RectF(left + holdHalo, centerY - h / 2, left + w - holdHalo, centerY + h / 2)
        val accent = SettingsStyle.accent
        fill.color = plate
        canvas.drawRoundRect(box, h / 2, h / 2, fill)
        ring.strokeWidth = 1.2f * dp * scale
        ring.color = accent
        canvas.drawRoundRect(box, h / 2, h / 2, ring)
        val g = 2.2f * dp * scale                    // the second, softer outline: "held down"
        ring.color = Contrast.withAlpha(accent, 110)
        canvas.drawRoundRect(box.left - g, box.top - g, box.right + g, box.bottom + g, h / 2 + g, h / 2 + g, ring)
        val p = holdPaint().apply { color = accent; textAlign = Paint.Align.LEFT }
        canvas.drawText("HOLD", box.left + holdPad + 5f * dp * scale, centerY - (p.descent() + p.ascent()) / 2f, p)
        p.textAlign = Paint.Align.CENTER
        p.letterSpacing = 0f
        return box.right
    }

    /** Draws with the left edge at [left], vertically centred on [centerY]. Returns the right edge. */
    fun draw(canvas: Canvas, button: ControllerButton, left: Float, centerY: Float, presses: Int = 1): Float {
        val w = width(button, presses)
        val r = radius
        val box = RectF(left, centerY - r, left + w, centerY + r)
        fill.color = plate
        ring.color = edge
        ring.strokeWidth = 1f * dp * scale
        canvas.drawRoundRect(box, r, r, fill)
        canvas.drawRoundRect(box, r, r, ring)

        if (presses > 1) {
            val p = countPaint().apply { color = Contrast.withAlpha(Color.WHITE, 210) }
            val tailCx = box.right - tailWidth(presses) / 2f - 1.5f * dp * scale
            canvas.drawText(countText(presses), tailCx, centerY - (p.descent() + p.ascent()) / 2f, p)
        }

        val cx = left + baseWidth(button) / 2f
        if (button.isStick) {
            // A thumbstick seen from above: the ring it sits in and its cap, then which one (L or R)
            val sx = left + r
            glyph.color = Color.WHITE
            glyph.strokeWidth = 1.1f * dp * scale
            canvas.drawCircle(sx, centerY, r * 0.62f, glyph)
            fill.color = Color.WHITE
            canvas.drawCircle(sx, centerY, r * 0.3f, fill)
            val p = shoulderPaint().apply { color = Color.WHITE; textAlign = Paint.Align.LEFT }
            canvas.drawText(stickText(button), sx + r * 0.62f + 2.5f * dp * scale, centerY - (p.descent() + p.ascent()) / 2f, p)
            p.textAlign = Paint.Align.CENTER
            return box.right
        }
        shoulderText(button)?.let {
            val p = shoulderPaint().apply { color = Color.WHITE }
            canvas.drawText(it, cx, centerY - (p.descent() + p.ascent()) / 2f, p)
            return box.right
        }

        glyph.strokeWidth = 1.3f * dp * scale
        val g = r * 0.48f
        when (button) {
            ControllerButton.A, ControllerButton.B, ControllerButton.X, ControllerButton.Y ->
                drawFace(canvas, button, cx, centerY, g)
            ControllerButton.START -> {           // menu / options: three lines
                glyph.color = Color.WHITE
                for (i in -1..1) canvas.drawLine(cx - g, centerY + i * g * 0.6f, cx + g, centerY + i * g * 0.6f, glyph)
            }
            ControllerButton.SELECT -> {          // view / create: two overlapping squares
                glyph.color = Color.WHITE
                val q = g * 1.2f
                canvas.drawRect(cx - g, centerY - g, cx - g + q, centerY - g + q, glyph)
                canvas.drawRect(cx + g - q, centerY + g - q, cx + g, centerY + g, glyph)
            }
            else -> Unit
        }
        return box.right
    }

    /** Draws a badge with its top-right corner at ([right], [top]). Returns its left edge. */
    fun drawInCorner(canvas: Canvas, button: ControllerButton, right: Float, top: Float): Float {
        val left = right - width(button)
        draw(canvas, button, left, top + radius)
        return left
    }

    // ---- Hint lines: badges mixed with short words --------------------------

    fun measureLine(parts: List<HintPart>, text: Paint): Float {
        var w = 0f
        parts.forEachIndexed { i, part ->
            w += when (part) {
                is HintPart.Badge -> if (part.held) heldWidth(part.button) else width(part.button, part.presses)
                is HintPart.Text -> text.measureText(part.text)
                is HintPart.HeldWord -> heldWordWidth(part.word)
            }
            if (i < parts.lastIndex) w += spacingAfter(part, parts[i + 1])
        }
        return w
    }

    /** Draws the line starting at [left]. [text] must be LEFT aligned. */
    fun drawLine(canvas: Canvas, parts: List<HintPart>, left: Float, centerY: Float, text: Paint) {
        var x = left
        val baseline = centerY - (text.descent() + text.ascent()) / 2f
        parts.forEachIndexed { i, part ->
            x = when (part) {
                is HintPart.Badge -> if (part.held) drawHeld(canvas, part.button, x, centerY) else draw(canvas, part.button, x, centerY, part.presses)
                is HintPart.Text -> {
                    canvas.drawText(part.text, x, baseline, text)
                    x + text.measureText(part.text)
                }
                is HintPart.HeldWord -> drawHeldWord(canvas, part.word, x, centerY)
            }
            if (i < parts.lastIndex) x += spacingAfter(part, parts[i + 1])
        }
    }

    private fun spacingAfter(part: HintPart, next: HintPart): Float = when {
        part is HintPart.Badge && next is HintPart.Badge -> gapBadgeToBadge * dp * scale
        else -> gapBadgeToText * dp * scale
    }

    // ---- Hint chips: the one shape every hint uses --------------------------
    // A soft pill holding badges and a word, e.g. [LB][RB] Word.

    private val chipFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val chipPad: Float get() = 6f * dp * scale
    private val chipGap: Float get() = 6f * dp * scale
    val chipHeight: Float get() = height + 6f * dp * scale

    fun chipWidth(parts: List<HintPart>, text: Paint): Float = measureLine(parts, text) + 2 * chipPad

    /**
     * Draws chips in a centred row inside [area]. If they don't all fit,
     * chips are dropped from the end, so list the most useful first.
     */
    fun drawChipRow(
        canvas: Canvas,
        chips: List<List<HintPart>>,
        area: RectF,
        text: Paint,
        fill: Int,
        edge: Int
    ) {
        var shown = chips
        fun total() = shown.sumOf { chipWidth(it, text).toDouble() }.toFloat() + chipGap * (shown.size - 1)
        while (shown.size > 1 && total() > area.width() - 2 * chipGap) shown = shown.dropLast(1)

        chipFill.color = fill
        chipEdge.color = edge
        chipEdge.strokeWidth = 1f * dp * scale
        val h = chipHeight
        val cy = area.centerY()
        var x = area.centerX() - total() / 2f
        shown.forEach { parts ->
            val w = chipWidth(parts, text)
            val box = RectF(x, cy - h / 2f, x + w, cy + h / 2f)
            canvas.drawRoundRect(box, h / 2f, h / 2f, chipFill)
            canvas.drawRoundRect(box, h / 2f, h / 2f, chipEdge)
            drawLine(canvas, parts, x + chipPad, cy, text)
            x += w + chipGap
        }
    }

    // ---- Per-style details -------------------------------------------------

    private fun shoulderPaint() = label.apply { textSize = 7f * dp * scale }

    private fun shoulderText(button: ControllerButton): String? {
        val sony = style == ButtonStyle.PLAYSTATION
        val nintendo = style == ButtonStyle.SWITCH
        return when (button) {
            ControllerButton.L1 -> if (sony) "L1" else if (nintendo) "L" else "LB"
            ControllerButton.R1 -> if (sony) "R1" else if (nintendo) "R" else "RB"
            ControllerButton.L2 -> if (sony) "L2" else if (nintendo) "ZL" else "LT"
            ControllerButton.R2 -> if (sony) "R2" else if (nintendo) "ZR" else "RT"
            else -> null
        }
    }

    /** Next to the stick drawing: L or R (PlayStation says L3 and R3). */
    private fun stickText(button: ControllerButton) =
        if (style == ButtonStyle.PLAYSTATION) (if (button == ControllerButton.L3) "L3" else "R3")
        else (if (button == ControllerButton.L3) "L" else "R")

    private fun drawFace(canvas: Canvas, button: ControllerButton, cx: Float, cy: Float, g: Float) {
        when (style) {
            ButtonStyle.PLAYSTATION -> {
                glyph.color = Color.parseColor(
                    when (button) {
                        ControllerButton.A -> "#8FB4FF"
                        ControllerButton.B -> "#FF6B6B"
                        ControllerButton.X -> "#F59BD5"
                        else -> "#4DD6B5"
                    }
                )
                when (button) {
                    ControllerButton.A -> {
                        canvas.drawLine(cx - g, cy - g, cx + g, cy + g, glyph)
                        canvas.drawLine(cx + g, cy - g, cx - g, cy + g, glyph)
                    }
                    ControllerButton.B -> canvas.drawCircle(cx, cy, g, glyph)
                    ControllerButton.X -> canvas.drawRect(cx - g * 0.85f, cy - g * 0.85f, cx + g * 0.85f, cy + g * 0.85f, glyph)
                    else -> canvas.drawPath(Path().apply {
                        moveTo(cx, cy - g)
                        lineTo(cx + g, cy + g * 0.75f)
                        lineTo(cx - g, cy + g * 0.75f)
                        close()
                    }, glyph)
                }
            }
            ButtonStyle.XBOX -> letter(
                canvas, button.name, cx, cy,
                Color.parseColor(
                    when (button) {
                        ControllerButton.A -> "#6CC24A"
                        ControllerButton.B -> "#F0503C"
                        ControllerButton.X -> "#3E9BFF"
                        else -> "#F5C518"
                    }
                )
            )
            // Nintendo swaps positions: bottom = B, right = A, left = Y, top = X
            ButtonStyle.SWITCH -> letter(
                canvas,
                when (button) {
                    ControllerButton.A -> "B"
                    ControllerButton.B -> "A"
                    ControllerButton.X -> "Y"
                    else -> "X"
                },
                cx, cy, Color.parseColor("#F2F2F2")
            )
        }
    }

    private fun letter(canvas: Canvas, text: String, cx: Float, cy: Float, colour: Int) {
        label.textSize = 8.5f * dp * scale
        label.color = colour
        canvas.drawText(text, cx, cy - (label.descent() + label.ascent()) / 2f, label)
    }
}
