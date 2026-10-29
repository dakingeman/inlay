package com.example.gridime

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every line icon Inlay draws, in one place: change one here and it changes on the keyboard, in Settings
 * and in the tour together. All of them share one line ([Design.STROKE_ICON], round ends and corners),
 * so they always look like one family. [IconSheetTest] draws every one at every size it's used and checks
 * its lines stay inside its box.
 *
 * Each takes its centre and its size `s` (half its width, in pixels); the caller picks the colour.
 */
object Icons {
    /** Sets [paint] up as an icon line, [unit] pixels per dp (the keyboard passes its own scale). */
    fun line(paint: Paint, unit: Float, colour: Int): Paint = paint.apply {
        style = Paint.Style.STROKE
        strokeWidth = Design.STROKE_ICON * unit
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colour
    }

    /** Shift: an outline arrow (off), filled (once), filled with a bar under it (caps lock). */
    fun shift(canvas: Canvas, cx: Float, cy: Float, s: Float, state: ShiftState, paint: Paint) {
        paint.style = if (state == ShiftState.OFF) Paint.Style.STROKE else Paint.Style.FILL_AND_STROKE
        val top = if (state == ShiftState.LOCK) cy - s * 1.25f else cy - s
        canvas.drawPath(Path().apply {
            moveTo(cx, top)
            lineTo(cx + s, top + s)
            lineTo(cx + s * 0.4f, top + s)
            lineTo(cx + s * 0.4f, top + s * 1.9f)
            lineTo(cx - s * 0.4f, top + s * 1.9f)
            lineTo(cx - s * 0.4f, top + s)
            lineTo(cx - s, top + s)
            close()
        }, paint)
        if (state == ShiftState.LOCK) {
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(cx - s * 0.6f, top + s * 2.2f, cx + s * 0.6f, top + s * 2.5f, s * 0.15f, s * 0.15f, paint)
        }
        paint.style = Paint.Style.STROKE
    }

    fun backspace(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        canvas.drawPath(Path().apply {
            moveTo(cx - s * 1.2f, cy)
            lineTo(cx - s * 0.5f, cy - s * 0.8f)
            lineTo(cx + s * 1.2f, cy - s * 0.8f)
            lineTo(cx + s * 1.2f, cy + s * 0.8f)
            lineTo(cx - s * 0.5f, cy + s * 0.8f)
            close()
        }, paint)
        canvas.drawLine(cx, cy - s * 0.35f, cx + s * 0.7f, cy + s * 0.35f, paint)
        canvas.drawLine(cx + s * 0.7f, cy - s * 0.35f, cx, cy + s * 0.35f, paint)
    }

    /** Undo: a U-turn arrow pointing back, the shape every major app uses. */
    fun undo(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        val top = cy - s * 0.45f
        val bottom = cy + s * 0.85f
        canvas.drawPath(Path().apply {
            moveTo(cx - s * 1.05f, top)
            lineTo(cx + s * 0.3f, top)
            cubicTo(cx + s * 1.25f, top, cx + s * 1.25f, bottom, cx + s * 0.3f, bottom)
            lineTo(cx - s * 0.3f, bottom)
        }, paint)
        canvas.drawPath(Path().apply {
            moveTo(cx - s * 0.55f, top - s * 0.5f)
            lineTo(cx - s * 1.05f, top)
            lineTo(cx - s * 0.55f, top + s * 0.5f)
        }, paint)
    }

    fun enter(canvas: Canvas, cx: Float, cy0: Float, s: Float, paint: Paint) {
        // Lifted a tenth: the arrowhead's weight sits low, so drawn on the maths centre it looked low (IconSheetTest)
        val cy = cy0 - s * 0.1f
        canvas.drawPath(Path().apply {
            moveTo(cx + s, cy - s * 0.7f)
            lineTo(cx + s, cy + s * 0.3f)
            lineTo(cx - s, cy + s * 0.3f)
            moveTo(cx - s * 0.35f, cy - s * 0.35f)
            lineTo(cx - s, cy + s * 0.3f)
            lineTo(cx - s * 0.35f, cy + s * 0.95f)
        }, paint)
    }

    /** The clipboard's clock. */
    fun clock(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        canvas.drawCircle(cx, cy, s, paint)
        canvas.drawLine(cx, cy, cx, cy - s * 0.55f, paint)
        canvas.drawLine(cx, cy, cx + s * 0.45f, cy + s * 0.2f, paint)
    }

    /**
     * Settings: a cog with eight square-cut teeth and a round hole, drawn as one outline, so it reads as a
     * gear at any size (lines for teeth looked like a sun when small, and the hole closed up).
     */
    fun gear(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        val outer = s * 1.0f; val root = s * 0.76f; val teeth = 8
        val tip = Math.toRadians(9.0); val base = Math.toRadians(13.5)
        val cog = Path()
        for (i in 0 until teeth) {
            val a = 2 * PI * i / teeth - PI / 2
            fun pt(r: Float, ang: Double) = (cx + r * cos(ang).toFloat()) to (cy + r * sin(ang).toFloat())
            val pts = listOf(pt(root, a - base), pt(outer, a - tip), pt(outer, a + tip), pt(root, a + base))
            pts.forEachIndexed { j, (x, y) -> if (i == 0 && j == 0) cog.moveTo(x, y) else cog.lineTo(x, y) }
            // the root between this tooth and the next follows the circle
            val next = 2 * PI * (i + 1) / teeth - PI / 2 - base
            val from = Math.toDegrees(a + base).toFloat(); val sweep = Math.toDegrees(next - (a + base)).toFloat()
            cog.arcTo(RectF(cx - root, cy - root, cx + root, cy + root), from, sweep)
        }
        cog.close()
        canvas.drawPath(cog, paint)
        canvas.drawCircle(cx, cy, s * 0.3f, paint)
    }

    /** A tick (finished set-up steps, the chosen colour swatch). [s] is the circle it sits in. */
    fun check(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        val g = s * 0.44f
        canvas.drawPath(Path().apply {
            moveTo(cx - g, cy)
            lineTo(cx - g * 0.25f, cy + g * 0.7f)
            lineTo(cx + g, cy - g * 0.6f)
        }, paint)
    }

    /** A chevron pointing right (rows that open something, the option pickers) or left. [s] is its half height. */
    fun chevron(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint, left: Boolean = false) {
        val d = if (left) -1f else 1f
        canvas.drawPath(Path().apply {
            moveTo(cx - d * s * 0.5f, cy - s)
            lineTo(cx + d * s * 0.5f, cy)
            lineTo(cx - d * s * 0.5f, cy + s)
        }, paint)
    }

    /** The loop sign beside the tour's loop bar: a circle with a gap and an arrowhead at its end. */
    fun loop(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint, fill: Paint) {
        val ring = Path()
        for (i in 0..LOOP_POINTS) {        // traced point by point, so every renderer draws it the same
            val t = Math.toRadians((LOOP_START + LOOP_SWEEP * i / LOOP_POINTS).toDouble())
            val px = cx + s * cos(t).toFloat(); val py = cy + s * sin(t).toFloat()
            if (i == 0) ring.moveTo(px, py) else ring.lineTo(px, py)
        }
        canvas.drawPath(ring, paint)
        val end = Math.toRadians((LOOP_START + LOOP_SWEEP).toDouble())
        val ex = cx + s * cos(end).toFloat(); val ey = cy + s * sin(end).toFloat()
        val tx = -sin(end).toFloat(); val ty = cos(end).toFloat()   // the way the arc runs at its end (clockwise)
        val a = s * 0.56f
        fill.color = paint.color
        fill.style = Paint.Style.FILL
        canvas.drawPath(Path().apply {
            moveTo(ex + tx * a, ey + ty * a)
            lineTo(ex - ty * a * 0.9f, ey + tx * a * 0.9f)
            lineTo(ex + ty * a * 0.9f, ey - tx * a * 0.9f)
            close()
        }, fill)
    }

    /** A small keyboard (the remote's "show the keyboard" key in Settings). [s] is half its width. */
    fun keyboard(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint, fill: Paint) {
        val kw = 2 * s; val kh = kw * 11f / 16f
        val k = RectF(cx - kw / 2, cy - kh / 2, cx + kw / 2, cy + kh / 2)
        canvas.drawRoundRect(k, kw * 0.156f, kw * 0.156f, paint)
        fill.color = paint.color
        fill.style = Paint.Style.FILL
        for (row in 0..1) for (col in 0..3) canvas.drawCircle(k.left + kw * (col + 1) / 5f, k.top + kh * (row + 1) / 3.6f, kw * 0.056f, fill)
        canvas.drawLine(k.left + kw * 0.3f, k.bottom - kh * 0.25f, k.right - kw * 0.3f, k.bottom - kh * 0.25f, paint)
    }

    private const val LOOP_START = -70f
    private const val LOOP_SWEEP = 290f
    private const val LOOP_POINTS = 48
}
