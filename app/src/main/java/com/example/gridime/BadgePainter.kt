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
    /** A D-pad direction ({DUP}, {DDOWN}, {DLEFT}, {DRIGHT}): the same cross on every controller, the pressed arm lit. */
    data class Dpad(val dir: Char) : HintPart()
}

/**
 * Draws controller button badges (Xbox, PlayStation or Switch) on a dark disc,
 * so they stay readable on any key colour. Shared by the keyboard and the Settings app.
 */
class BadgePainter(private val dp: Float) {

    var style = ButtonStyle.XBOX

    companion object {
        /** One stroke weight for every icon line in every badge (brand: one unified stroke). */
        const val ICON_STROKE = Design.STROKE_BADGE
        /** The badges' dark plate. */
        val PLATE = Design.BADGE_PLATE
        /** HOLD in your colour on the capsule's dark plate, kept at 4.5:1 (deep custom colours brighten a little). */
        fun holdWordColor(accent: Int): Int = Contrast.accentText(accent, Contrast.over(PLATE, Color.BLACK))
    }
    var scale = 1f
    /**
     * Breathing room in hint lines (dp, before scaling). The keyboard's chips are tight;
     * Settings and the tour set these wider, so badges and words never look jammed together.
     */
    var gapBadgeToBadge = Design.BADGE_GAP
    var gapBadgeToText = Design.BADGE_TEXT_GAP

    private val plate = PLATE
    private val edge = Design.BADGE_EDGE

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
    private val holdPad get() = 2f * dp * scale

    /** The capsule has one outline (28 Oct 2026), so it needs no extra room round it. */
    private val holdHalo get() = 0f
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
        val p = wordPaint().apply { color = Design.BADGE_INK; textAlign = Paint.Align.RIGHT }
        canvas.drawText(word, right - holdPad - 3f * dp * scale, centerY - (p.descent() + p.ascent()) / 2f, p)
        p.textAlign = Paint.Align.CENTER
        return left + w
    }

    /** Draws a held button (the HOLD capsule). Returns the right edge. */
    fun drawHeld(canvas: Canvas, button: ControllerButton, left: Float, centerY: Float): Float {
        val w = heldWidth(button)
        val right = holdCapsule(canvas, left, w, centerY)
        draw(canvas, button, right - holdPad - width(button), centerY, edged = false)
        return left + w
    }

    /**
     * The capsule itself: dark plate, the badges' own edge, and HOLD in your colour. No accent ring:
     * rings mean "you are here" (audit + Emre, 28 Oct 2026). The word says what it is.
     */
    private fun holdCapsule(canvas: Canvas, left: Float, w: Float, centerY: Float): Float {
        val h = height + 2 * holdPad
        val box = RectF(left + holdHalo, centerY - h / 2, left + w - holdHalo, centerY + h / 2)
        fill.color = plate
        canvas.drawRoundRect(box, h / 2, h / 2, fill)
        ring.strokeWidth = Design.STROKE_HAIRLINE * dp * scale
        ring.color = edge
        val e = ring.strokeWidth / 2f
        canvas.drawRoundRect(box.left + e, box.top + e, box.right - e, box.bottom - e, h / 2 - e, h / 2 - e, ring)
        val p = holdPaint().apply { color = holdWordColor(SettingsStyle.accent); textAlign = Paint.Align.LEFT }
        canvas.drawText("HOLD", box.left + holdPad + 5f * dp * scale, centerY - (p.descent() + p.ascent()) / 2f, p)
        p.textAlign = Paint.Align.CENTER
        p.letterSpacing = 0f
        return box.right
    }

    /** Draws with the left edge at [left], vertically centred on [centerY]. Returns the right edge. */
    fun draw(canvas: Canvas, button: ControllerButton, left: Float, centerY: Float, presses: Int = 1, edged: Boolean = true): Float {
        val w = width(button, presses)
        val r = radius
        val box = RectF(left, centerY - r, left + w, centerY + r)
        fill.color = plate
        ring.color = edge
        ring.strokeWidth = Design.STROKE_HAIRLINE * dp * scale
        canvas.drawRoundRect(box, r, r, fill)
        // The edge is drawn inside the badge's box (inset by half its width), never across it: drawn on the box line,
        // half the stroke stuck out, and anything sized to the box cut it off (the video's button names, 31 Oct 2026)
        val e = ring.strokeWidth / 2f
        if (edged) canvas.drawRoundRect(box.left + e, box.top + e, box.right - e, box.bottom - e, r - e, r - e, ring)   // inside a HOLD capsule the capsule's edge is enough

        if (presses > 1) {
            val p = countPaint().apply { color = Contrast.withAlpha(Design.BADGE_INK, Design.ALPHA_INK_STRONG) }
            val tailCx = box.right - tailWidth(presses) / 2f - 1.5f * dp * scale
            canvas.drawText(countText(presses), tailCx, centerY - (p.descent() + p.ascent()) / 2f, p)
        }

        val cx = left + baseWidth(button) / 2f
        if (button.isStick) {
            // A thumbstick seen from above: the ring it sits in and its cap, then which one (L or R)
            val sx = left + r
            glyph.color = Design.BADGE_INK
            glyph.strokeWidth = ICON_STROKE * dp * scale
            canvas.drawCircle(sx, centerY, r * 0.62f, glyph)
            fill.color = Design.BADGE_INK
            canvas.drawCircle(sx, centerY, r * 0.3f, fill)
            val p = shoulderPaint().apply { color = Design.BADGE_INK; textAlign = Paint.Align.LEFT }
            canvas.drawText(stickText(button), sx + r * 0.62f + 2.5f * dp * scale, centerY - (p.descent() + p.ascent()) / 2f, p)
            p.textAlign = Paint.Align.CENTER
            return box.right
        }
        shoulderText(button)?.let {
            val p = shoulderPaint().apply { color = Design.BADGE_INK }
            canvas.drawText(it, cx, centerY - (p.descent() + p.ascent()) / 2f, p)
            return box.right
        }

        glyph.strokeWidth = ICON_STROKE * dp * scale
        val g = r * 0.48f
        when (button) {
            ControllerButton.A, ControllerButton.B, ControllerButton.X, ControllerButton.Y ->
                drawFace(canvas, button, cx, centerY, g)
            ControllerButton.START -> drawStart(canvas, cx, centerY, g)
            ControllerButton.SELECT -> drawSelect(canvas, cx, centerY, g)
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

    /**
     * A D-pad direction badge: the badges' own plate and edge, a plus-shaped D-pad in the badge stroke, and the
     * pressed arm filled. The same on Xbox, PlayStation and Switch (their D-pads share the shape). Returns the right edge.
     */
    fun drawDpad(canvas: Canvas, dir: Char, left: Float, centerY: Float): Float {
        val r = radius; val cx = left + r
        fill.color = plate; ring.color = edge; ring.strokeWidth = Design.STROKE_HAIRLINE * dp * scale
        canvas.drawCircle(cx, centerY, r, fill); canvas.drawCircle(cx, centerY, r - ring.strokeWidth / 2, ring)
        val a = r * 0.62f; val t = r * 0.22f                         // arm length from the centre, half the arm's width
        val cross = android.graphics.Path().apply {
            moveTo(cx - t, centerY - a); lineTo(cx + t, centerY - a); lineTo(cx + t, centerY - t); lineTo(cx + a, centerY - t)
            lineTo(cx + a, centerY + t); lineTo(cx + t, centerY + t); lineTo(cx + t, centerY + a); lineTo(cx - t, centerY + a)
            lineTo(cx - t, centerY + t); lineTo(cx - a, centerY + t); lineTo(cx - a, centerY - t); lineTo(cx - t, centerY - t); close()
        }
        // The cross as a quiet shape, the pressed arm solid white: which way reads at a glance
        fill.color = Contrast.withAlpha(Design.BADGE_INK, Design.BADGE_DPAD_REST)
        canvas.drawPath(cross, fill)
        fill.color = Design.BADGE_INK
        when (dir) {
            'U' -> canvas.drawRect(cx - t, centerY - a, cx + t, centerY - t, fill)
            'D' -> canvas.drawRect(cx - t, centerY + t, cx + t, centerY + a, fill)
            'L' -> canvas.drawRect(cx - a, centerY - t, cx - t, centerY + t, fill)
            else -> canvas.drawRect(cx + t, centerY - t, cx + a, centerY + t, fill)
        }
        return left + height
    }

    // ---- Hint lines: badges mixed with short words --------------------------

    fun measureLine(parts: List<HintPart>, text: Paint): Float {
        var w = 0f
        parts.forEachIndexed { i, part ->
            w += when (part) {
                is HintPart.Badge -> if (part.held) heldWidth(part.button) else width(part.button, part.presses)
                is HintPart.Text -> text.measureText(part.text)
                is HintPart.HeldWord -> heldWordWidth(part.word)
                is HintPart.Dpad -> height
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
                is HintPart.Dpad -> drawDpad(canvas, part.dir, x, centerY)
            }
            if (i < parts.lastIndex) x += spacingAfter(part, parts[i + 1])
        }
    }

    private fun spacingAfter(part: HintPart, next: HintPart): Float = when {
        (part is HintPart.Badge || part is HintPart.Dpad) && (next is HintPart.Badge || next is HintPart.Dpad) -> gapBadgeToBadge * dp * scale
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
        chipEdge.strokeWidth = Design.STROKE_HAIRLINE * dp * scale
        val h = chipHeight
        val cy = area.centerY()
        var x = area.centerX() - total() / 2f
        shown.forEach { parts ->
            val w = chipWidth(parts, text)
            val box = RectF(x, cy - h / 2f, x + w, cy + h / 2f)
            canvas.drawRoundRect(box, h / 2f, h / 2f, chipFill)
            val e = chipEdge.strokeWidth / 2f                                    // the edge inside the chip, as on every badge
            canvas.drawRoundRect(box.left + e, box.top + e, box.right - e, box.bottom - e, h / 2f - e, h / 2f - e, chipEdge)
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

    /** Start: Xbox Menu and PlayStation Options are three lines; Switch is a plus (traced from KDE's gamepad icon set). */
    private fun drawStart(canvas: Canvas, cx: Float, cy: Float, g: Float) {
        glyph.color = Design.BADGE_INK
        if (style == ButtonStyle.SWITCH) {
            canvas.drawLine(cx - g, cy, cx + g, cy, glyph)
            canvas.drawLine(cx, cy - g, cx, cy + g, glyph)
        } else for (i in -1..1) canvas.drawLine(cx - g * 0.9f, cy + i * g * 0.62f, cx + g * 0.9f, cy + i * g * 0.62f, glyph)
    }

    /** Select: Xbox View (a window in front of another), PlayStation Create (three short rays), Switch minus. */
    private fun drawSelect(canvas: Canvas, cx: Float, cy: Float, g: Float) {
        glyph.color = Design.BADGE_INK
        when (style) {
            ButtonStyle.SWITCH -> canvas.drawLine(cx - g, cy, cx + g, cy, glyph)
            ButtonStyle.PLAYSTATION -> {
                // Rays from a point below the badge, the middle one upright, the outer two tilted 22°
                val len = g * 0.95f
                val bottom = cy + len / 2f
                for (deg in listOf(-22.0, 0.0, 22.0)) {
                    val a = Math.toRadians(deg)
                    val off = if (deg == 0.0) 0f else g * 0.62f * Math.signum(deg).toFloat()
                    val bx = cx + off; val by = bottom + (if (deg == 0.0) 0f else len * 0.12f)
                    canvas.drawLine(bx, by, bx + (len * Math.sin(a)).toFloat(), by - (len * Math.cos(a)).toFloat(), glyph)
                }
            }
            ButtonStyle.XBOX -> {
                // The front window, and only the visible corner of the one behind it (corners on the keyline)
                val g = g * 0.9f
                val q = g * 1.15f
                val fx = cx + g - q; val fy = cy + g - q
                canvas.drawRect(fx, fy, cx + g, cy + g, glyph)
                val bx = cx - g; val by = cy - g; val sw = glyph.strokeWidth
                canvas.drawPath(Path().apply {
                    moveTo(bx, fy - sw); lineTo(bx, by); lineTo(bx + q, by); lineTo(bx + q, fy - sw)
                }, glyph)
            }
        }
    }

    private fun drawFace(canvas: Canvas, button: ControllerButton, cx: Float, cy: Float, g: Float) {
        when (style) {
            ButtonStyle.PLAYSTATION -> {
                glyph.color = Design.FACE_PLAYSTATION.getValue(faceKey(button))
                when (button) {
                    ControllerButton.A -> {
                        val k = g * 0.88f                       // the cross's tips on the same keyline as the others
                        canvas.drawLine(cx - k, cy - k, cx + k, cy + k, glyph)
                        canvas.drawLine(cx + k, cy - k, cx - k, cy + k, glyph)
                    }
                    // Optical balance: a square looks bigger than a circle of the same width and a triangle
                    // smaller, so the square shrinks a little and the triangle grows and sits on its visual centre
                    ControllerButton.B -> canvas.drawCircle(cx, cy, g * 0.98f, glyph)
                    ControllerButton.X -> canvas.drawRect(cx - g * 0.8f, cy - g * 0.8f, cx + g * 0.8f, cy + g * 0.8f, glyph)
                    else -> {
                        val t = g * 1.1f                        // half the base
                        val h = t * 1.73f                       // equilateral
                        val top = cy - h * 0.55f                // bounding box a touch below centre reads as centred
                        canvas.drawPath(Path().apply {
                            moveTo(cx, top)
                            lineTo(cx + t, top + h)
                            lineTo(cx - t, top + h)
                            close()
                        }, glyph)
                    }
                }
            }
            ButtonStyle.XBOX -> letter(
                canvas, button.name, cx, cy,
                Design.FACE_XBOX.getValue(faceKey(button))
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
                cx, cy, Design.INK_LIGHT
            )
        }
    }

    /** A, B, X or Y: the face button's key in Design.FACE_* (bottom, right, left, top). */
    private fun faceKey(button: ControllerButton) = when (button) {
        ControllerButton.A -> "A"; ControllerButton.B -> "B"; ControllerButton.X -> "X"; else -> "Y"
    }

    private fun letter(canvas: Canvas, text: String, cx: Float, cy: Float, colour: Int) {
        label.textSize = 8.5f * dp * scale
        label.color = colour
        canvas.drawText(text, cx, cy - (label.descent() + label.ascent()) / 2f, label)
    }
}
