package com.example.gridime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * "What's new": a short screen that shows the highlights of an update, once.
 *
 * It opens by itself the first time Settings opens after an update (never on the keyboard,
 * so it never interrupts typing). New users get the tour instead, which also counts as having
 * seen this. Updates with only fixes leave [VERSION] alone, so no screen appears for them.
 * It can be opened again from About → What's new.
 *
 * The pages themselves live in [TutorialActivity] (same layout, same badges, same acted-out
 * demos as the tour), so the two always look and behave alike.
 *
 * Every update with something new: bump [VERSION], write 1 to 3 pages, and make the matching
 * card and short clip for GitHub from the same storyboard (see docs/UPDATING.md).
 */
object WhatsNew {
    /** The update these highlights belong to. Change it only when there's something to show. */
    const val VERSION = "1.1.1"

    fun shouldShow(settings: KeyboardSettings) = settings.tutorialDone && settings.whatsNewSeen != VERSION

    /**
     * Android's "Remove animations" (accessibility) sets the animation speed to zero.
     * Our demos then show one still moment instead of moving.
     */
    fun motionAllowed(): Boolean = ValueAnimator.areAnimatorsEnabled()
}

/**
 * The right stick, drawn as a ring with a knob that leans the way it's pushed, plus a bar that
 * fills with the push and a short caption. Shown under the keyboard on the right-stick page.
 * [push] is -1 (full left) … 0 (resting) … 1 (full right).
 */
class StickGaugeView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_CAPTION, resources.displayMetrics)
    }

    var accent: Int = SettingsStyle.accent
    var style: ButtonStyle = ButtonStyle.XBOX
    var push: Float = 0f
        set(value) { field = value.coerceIn(-1f, 1f); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (HEIGHT_DP * dp).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val r = RING_DP * dp
        val barW = BAR_DP * dp
        val gap = Design.SPACE_XL * dp
        val arrowRoom = 14 * dp
        // The ring, its arrows, the bar and caption sit together as one group, centred
        val groupW = 2 * (r + arrowRoom + 6 * dp) + gap + barW
        val left = (width - groupW) / 2f
        val cx = left + r + arrowRoom + 6 * dp
        val cy = height / 2f
        val strength = kotlin.math.abs(push)
        val dir = if (push > 0) 1 else if (push < 0) -1 else 0

        // Ring
        fill.color = SettingsStyle.surface
        canvas.drawCircle(cx, cy, r, fill)
        line.color = Contrast.withAlpha(SettingsStyle.textPrimary, 0x33)
        line.strokeWidth = max(1f, dp)
        canvas.drawCircle(cx, cy, r, line)

        // Knob: leans with the push, never past the ring
        val kr = r * 0.52f
        val kx = cx + push * (r - kr - 2 * dp)
        fill.color = if (strength > 0f) Contrast.withAlpha(accent, (0x40 + 0xBF * strength).toInt()) else SettingsStyle.track
        canvas.drawCircle(kx, cy, kr, fill)
        line.color = if (strength > 0f) accent else Contrast.withAlpha(SettingsStyle.textPrimary, 0x44)
        canvas.drawCircle(kx, cy, kr, line)
        label.textSize = kr * 0.62f
        label.color = if (strength > 0.5f) Contrast.inkFor(accent) else SettingsStyle.textPrimary
        canvas.drawText(if (style == ButtonStyle.PLAYSTATION) "R3" else "RS", kx, cy - (label.descent() + label.ascent()) / 2f, label)

        // Arrows: the one in the push direction lights up
        line.strokeWidth = 2.2f * dp
        for (d in intArrayOf(-1, 1)) {
            line.color = if (d == dir) accent else Contrast.withAlpha(SettingsStyle.textPrimary, 0x30)
            val ax = cx + d * (r + 6 * dp)
            canvas.drawLine(ax, cy - 6 * dp, ax + d * 7 * dp, cy, line)
            canvas.drawLine(ax + d * 7 * dp, cy, ax, cy + 6 * dp, line)
        }

        // Speed bar and caption
        val bx = cx + r + arrowRoom + 6 * dp + gap
        val bh = 6 * dp
        fill.color = SettingsStyle.track
        canvas.drawRoundRect(RectF(bx, cy - bh - 4 * dp, bx + barW, cy - 4 * dp), bh / 2, bh / 2, fill)
        if (strength > 0f) {
            fill.color = accent
            canvas.drawRoundRect(RectF(bx, cy - bh - 4 * dp, bx + barW * strength, cy - 4 * dp), bh / 2, bh / 2, fill)
        }
        caption.color = SettingsStyle.textSecondary
        val words = when {
            strength == 0f -> "Resting"
            strength < 0.6f -> "Light push · slow"
            else -> "Full push · fast"
        }
        canvas.drawText(words, bx, cy + 6 * dp - caption.ascent(), caption)
    }

    private companion object {
        const val HEIGHT_DP = 72f
        const val RING_DP = 26f
        const val BAR_DP = 120f
    }
}

/**
 * "And this screen": a small drawing of the What's new screen, with the same screen inside it,
 * and inside that… It zooms slowly into itself, in a loop with no visible jump.
 * With animations turned off in Android, it stays still.
 */
class ScreenInScreenView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val startedAt = SystemClock.uptimeMillis()
    var accent: Int = SettingsStyle.accent

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 9f / 16f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // The inner screen sits where the stage sits on the real screen
        val ix = w * INNER_X
        val iy = h * INNER_Y
        val k = INNER_SIZE
        // Zoom: over one loop, the inner screen grows to exactly where the outer one was.
        // The point that stays put is the fixed point of that move: P = inner corner / (1 - k).
        val t = if (WhatsNew.motionAllowed()) ((SystemClock.uptimeMillis() - startedAt) % LOOP_MS) / LOOP_MS.toFloat() else 0f
        val px = ix / (1 - k)
        val py = iy / (1 - k)
        val s = (1f / k).pow(t)
        val saved = canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        canvas.translate(px, py)
        canvas.scale(s, s)
        canvas.translate(-px, -py)
        drawLevel(canvas, 0f, 0f, w, h, DEPTH)
        canvas.restoreToCount(saved)
        if (WhatsNew.motionAllowed()) postInvalidateOnAnimation()
    }

    private fun drawLevel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, depth: Int) {
        if (depth == 0 || w < 6 * dp) return
        val u = w / 100f                                   // one hundredth of this level's width
        fill.color = SettingsStyle.background
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 1.2f * u, 1.2f * u, fill)
        edge.color = Contrast.withAlpha(SettingsStyle.textPrimary, 0x26)
        edge.strokeWidth = max(0.6f, 0.18f * u)
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 1.2f * u, 1.2f * u, edge)
        // Progress dots, the last one lit
        for (i in 0..2) {
            fill.color = if (i == 2) accent else SettingsStyle.track
            val dw = if (i == 2) 2.4f * u else 0.8f * u
            val dx = x + w / 2f - 2.4f * u + i * 1.5f * u
            canvas.drawRoundRect(RectF(dx, y + 1.9f * u, dx + dw, y + 2.7f * u), 0.4f * u, 0.4f * u, fill)
        }
        // Eyebrow, title, three lines of body
        fill.color = accent
        canvas.drawRoundRect(RectF(x + 4f * u, y + 5f * u, x + 17f * u, y + 6.2f * u), 0.4f * u, 0.4f * u, fill)
        fill.color = Contrast.withAlpha(SettingsStyle.textPrimary, 0xD9)
        canvas.drawRoundRect(RectF(x + 4f * u, y + 8.4f * u, x + 30f * u, y + 11f * u), 0.6f * u, 0.6f * u, fill)
        fill.color = Contrast.withAlpha(SettingsStyle.textSecondary, 0x80)
        floatArrayOf(29f, 25f, 21f).forEachIndexed { i, len ->
            canvas.drawRoundRect(RectF(x + 4f * u, y + (14f + i * 2.7f) * u, x + (4f + len) * u, y + (15.4f + i * 2.7f) * u), 0.5f * u, 0.5f * u, fill)
        }
        // The control bar's line
        fill.color = Contrast.withAlpha(SettingsStyle.textPrimary, 0x18)
        canvas.drawRect(x, y + h * 0.907f, x + w, y + h * 0.907f + max(0.6f, 0.12f * u), fill)
        // And the same screen again, where the stage is
        drawLevel(canvas, x + w * INNER_X, y + h * INNER_Y, w * INNER_SIZE, h * INNER_SIZE, depth - 1)
    }

    private companion object {
        const val INNER_X = 0.40f
        const val INNER_Y = 0.275f
        const val INNER_SIZE = 0.565f
        const val DEPTH = 9
        const val LOOP_MS = 4200L
    }
}
