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
 * Every update with something new: bump [VERSION], write a page per highlight (keep it to the few that matter), and make the matching
 * card and short clip for GitHub from the same storyboard (see docs/UPDATING.md).
 * Each page's demo loops, and every looping demo shows the [LoopBarView] (the demo reports its
 * progress; see TutorialActivity.every and runSteps). No new page goes without it.
 *
 * The look comes for free: a new page is only its Page entry (title, one sentence, button rows, demo).
 * The background ([Backdrop], lit from the top right for What's new, top left for the tour), the
 * "NEW IN [VERSION]" tag or "WELCOME TOUR" label, the window around the text box and keyboard, and the
 * user's highlight colour (presets and the slider alike) are all applied by TutorialActivity.
 * The sound showcase (Demo.SOUNDS) is a template too: it plays every pack in SoundPack, with its name tag. Long words
 * shrink step by step to fit, and the consistency checks catch anything that still doesn't.
 */
object WhatsNew {
    /** The update these highlights belong to. Change it only when there's something to show. */
    const val VERSION = "1.2.0"

    fun shouldShow(settings: KeyboardSettings) = settings.tutorialDone && settings.whatsNewSeen != VERSION

    /**
     * Android's "Remove animations" (accessibility) sets the animation speed to zero.
     * Our demos then show one still moment instead of moving.
     */
    fun motionAllowed(): Boolean = ValueAnimator.areAnimatorsEnabled()
}

/**
 * The loop bar: a small loop sign and a thin bar under the device picture, on every page whose
 * demo plays in a loop. It fills as the demo plays and starts again with it, so you can see how
 * much is left before the demo repeats.
 *
 * Each run of a demo says how long it takes ([begin]), worked out before it plays, so the bar
 * fills at one even pace from empty to full, never in jumps. When the demo starts over, the full
 * bar fades while the new one begins from empty.
 *
 * The rule for new pages (tour and What's new): every looping demo gives the length of its runs,
 * and the bar shows by itself (see TutorialActivity.every and runSteps). Only the welcome page's
 * device showcase, which has no beginning or end, goes without.
 * With animations turned off in Android, the demos stand still and the bar is hidden.
 */
class LoopBarView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrow = android.graphics.Path()
    private val ring = android.graphics.Path()
    var accent: Int = SettingsStyle.accent
        set(value) { field = value; invalidate() }

    private var shown = 0f             // how far the bar is drawn
    private var loopStart = 0L         // when this run of the demo began
    private var loopMs = 0L            // how long this run takes (0: no run yet, the bar is empty)
    private var fadeStart = -1L        // the previous run's bar, fading out
    private var fadeWidth = 0f

    /** For the checks: when each run began and how long it said it would take. */
    internal val runsForCheck = mutableListOf<Pair<Long, Long>>()

    /** A new page: an empty bar until its demo begins its first run. */
    fun restart() {
        shown = 0f; loopMs = 0L; fadeStart = -1L
        runsForCheck.clear()
        invalidate()
    }

    /** A run of the demo begins [at] (its timetable) and takes [runMs]: the bar fills from empty to full over that time. */
    fun begin(runMs: Long, at: Long = SystemClock.uptimeMillis()) {
        val now = SystemClock.uptimeMillis()
        if (shown > 0.02f) { fadeStart = now; fadeWidth = shown }
        loopStart = at
        loopMs = runMs.coerceAtLeast(1L)
        if (runsForCheck.size < 64) runsForCheck += at to loopMs
        shown = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (HEIGHT_DP * dp).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        shown = if (loopMs > 0L) min(1f, (now - loopStart) / loopMs.toFloat()) else 0f

        // The loop sign and the bar sit together, centred. In a narrow column the bar gets shorter,
        // and where even that won't do (a tall handheld on a small screen) the bar shows alone.
        val r = ICON_R_DP * dp
        val sign = 2 * r + GAP_DP * dp
        val room = width - 2 * r                             // a little air at both ends
        val withSign = room - sign >= MIN_BAR_DP * dp
        val barW = if (withSign) min(BAR_DP * dp, room - sign) else min(BAR_DP * dp, room)
        val left = (width - ((if (withSign) sign else 0f) + barW)) / 2f
        val cy = height / 2f

        // Loop sign: a circle with a gap, an arrowhead at its end
        if (withSign) drawSign(canvas, left + r, cy, r)

        // The bar: a track, the previous run fading, this run filling
        val bx = left + if (withSign) sign else 0f
        val bh = BAR_H_DP * dp
        val bar = RectF(bx, cy - bh / 2, bx + barW, cy + bh / 2)
        fill.color = SettingsStyle.track
        canvas.drawRoundRect(bar, bh / 2, bh / 2, fill)
        if (fadeStart >= 0L) {
            val f = 1f - (now - fadeStart) / FADE_MS
            if (f > 0f) {
                fill.color = Contrast.withAlpha(accent, (0xFF * f).toInt())
                canvas.drawRoundRect(RectF(bx, bar.top, bx + barW * fadeWidth, bar.bottom), bh / 2, bh / 2, fill)
            } else fadeStart = -1L
        }
        if (shown > 0.002f) {
            fill.color = accent
            canvas.drawRoundRect(RectF(bx, bar.top, bx + max(bh, barW * shown), bar.bottom), bh / 2, bh / 2, fill)
        }
        // Redraw only while the bar fills or the last run fades; a full or empty bar stands still
        // 30 times a second is plenty for a bar that fills over seconds (it moves a pixel or two per frame),
        // and spares the screen redrawing up to 120 times a second for it
        if (isShown && WhatsNew.motionAllowed() && ((loopMs > 0L && shown < 1f) || fadeStart >= 0L)) postInvalidateDelayed(BAR_FRAME_MS)
    }

    private fun drawSign(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        icon.strokeWidth = ICON_STROKE_DP * dp
        icon.color = SettingsStyle.textSecondary
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        ring.reset()                                     // traced point by point, so every renderer draws it the same
        for (i in 0..ARC_POINTS) {
            val t = Math.toRadians((ARC_START + ARC_SWEEP * i / ARC_POINTS).toDouble())
            val px = oval.centerX() + r * kotlin.math.cos(t).toFloat()
            val py = oval.centerY() + r * kotlin.math.sin(t).toFloat()
            if (i == 0) ring.moveTo(px, py) else ring.lineTo(px, py)
        }
        canvas.drawPath(ring, icon)
        val end = Math.toRadians((ARC_START + ARC_SWEEP).toDouble())
        val ex = cx + r * kotlin.math.cos(end).toFloat()
        val ey = cy + r * kotlin.math.sin(end).toFloat()
        val tx = -kotlin.math.sin(end).toFloat()         // the way the arc runs at its end (clockwise)
        val ty = kotlin.math.cos(end).toFloat()
        val a = ARROW_DP * dp
        arrow.reset()
        arrow.moveTo(ex + tx * a, ey + ty * a)
        arrow.lineTo(ex - ty * a * 0.9f, ey + tx * a * 0.9f)
        arrow.lineTo(ex + ty * a * 0.9f, ey - tx * a * 0.9f)
        arrow.close()
        fill.color = SettingsStyle.textSecondary
        canvas.drawPath(arrow, fill)
    }

    companion object {
        /** How often the bar redraws while it fills (about 30 times a second). */
        const val BAR_FRAME_MS = 33L
        /** The bar's own height; the picture above leaves this much room (plus the gap) for it. */
        const val HEIGHT_DP = 16f
        private const val BAR_DP = 88f
        private const val MIN_BAR_DP = 40f
        private const val BAR_H_DP = 3f
        private const val GAP_DP = 8f
        private const val ICON_R_DP = 5f
        private const val ICON_STROKE_DP = 1.6f
        private const val ARROW_DP = 2.8f
        private const val ARC_START = -70f
        private const val ARC_SWEEP = 290f
        private const val ARC_POINTS = 48
        private const val FADE_MS = 260f
    }
}
