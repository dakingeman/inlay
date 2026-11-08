package com.example.gridime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The button setup screen (1.3), in the tour's own look: its backdrop, its words column (label, big title,
 * one line of help), its card of steps, its stage card and its control bar. One thing to do at a time.
 * - three steps, shown as the tour shows its card: learn the four buttons, choose Select and Close, try them
 * - the button to press waits for you: it breathes in your colour and sends out a slow ripple (the tour's
 *   research: the waiting state must look different from a demo). Nothing else moves
 * - each press answers at once: the button swells, fills and draws its tick, and a note under the help says
 *   what it heard. Never the letter it sent: a handheld set to a Nintendo layout sends Y from the button
 *   printed X, and showing that would only confuse
 * - choosing: the one you press turns into Select (green) or Close (red); Delete and Space take the others
 * - at the end every press shows its job; Select finishes, Close starts again
 * - wide screens: words left, stage right. Upright and narrow: words on top, the stage under them. Short
 *   screens: the tour's smaller title. Everything is sized from the room it has, so nothing overlaps
 * Nothing is saved until it finishes (the host keeps what was there before). Drawn, so it costs no images.
 */
class ButtonSetupView(context: Context, before: FaceSetup = FaceSetup()) : View(context) {
    val flow = SetupFlow(before)
    /** Finished (the new setup) or left (null): the host saves and closes. */
    var onDone: ((FaceSetup?) -> Unit)? = null
    var accent: Int = SettingsStyle.accent
        set(v) { if (field != v) { field = v; backdrop = null; labelInk = null; invalidate() } }

    private var heard = SetupFlow.Heard.NONE
    private var heardAt = 0L
    private var stepAt = 0L
    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val badges = BadgePainter(dp).apply { scale = Design.BADGE_INLINE }

    private var backdrop: Backdrop? = null
    private var labelInk: Int? = null
    /** Words in your colour, kept at 4.5:1 on the backdrop's light (the tour's own rule). */
    private fun label() = labelInk ?: TutorialActivity.labelColor(accent, false).also { labelInk = it }

    /** A press from the device, as it came (before any swap or job). True: it was the setup's. */
    fun press(code: Int): Boolean {
        val was = flow.step
        heard = flow.press(code); heardAt = SystemClock.uptimeMillis()
        if (flow.step != was) stepAt = heardAt
        if (heard == SetupFlow.Heard.FINISH) postDelayed({ onDone?.invoke(flow.result) }, Design.SETUP_FINISH_MS)
        invalidate()
        return true
    }

    /** Left without finishing (held Start): nothing changes. */
    fun leave() = onDone?.invoke(null)

    /** Checks: where the last drawing put the jobs' tags, the buttons and the diamond's room. */
    internal val tagsForCheck = mutableListOf<RectF>()
    internal val buttonsForCheck = mutableListOf<RectF>()
    internal val stageForCheck = RectF()

    // ---- What's new: the setup playing itself on a slide ------------------------------------------------------
    /** Only the stage card, with the step's title at its top (no backdrop, words column or bar): What's new plays the setup on it. */
    var stageOnly = false
    /** In [stageOnly]: the title instead of the step's own (null: the step's). Changing it rises in, as a new step does. */
    var captionTitle: String? = null
        set(v) { if (field != v) { field = v; stepAt = SystemClock.uptimeMillis(); invalidate() } }
    /** A controller's marks on the four buttons instead of the ticks, by position (left, top, right, bottom). */
    var marks: List<String>? = null
        set(v) { field = v; invalidate() }
    /** 0 to 1: how far the marks show. */
    var marksShown = 0f
        set(v) { field = v; invalidate() }
    /** 0 to 1: how far the ticks have gone (once gone they stay gone while one style of marks gives way to the next). */
    var ticksGone = 0f
        set(v) { field = v; invalidate() }

    // ---- What it says ------------------------------------------------------------------------------------------
    private val names = listOf("left", "top", "right", "bottom")
    private val learning get() = flow.step.ordinal <= SetupFlow.Step.BOTTOM.ordinal
    /** Which of the three steps: 0 learn, 1 choose, 2 try. */
    private val part get() = when { learning -> 0; flow.step == SetupFlow.Step.DONE -> 2; else -> 1 }

    private fun title(): String = when (flow.step) {
        SetupFlow.Step.SELECT -> "Which one selects?"
        SetupFlow.Step.CLOSE -> "Which one goes back?"
        SetupFlow.Step.DONE -> "All set. Try them"
        else -> "Press your ${names[flow.step.ordinal]} button"
    }
    private fun help(): String = when (flow.step) {
        SetupFlow.Step.LEFT -> "Use the four buttons on the right of your controller. The letters on them don't matter: Inlay learns where each one is."
        SetupFlow.Step.TOP, SetupFlow.Step.RIGHT, SetupFlow.Step.BOTTOM -> "The one at the ${names[flow.step.ordinal]} of the four, whatever letter it has."
        SetupFlow.Step.SELECT -> "Press the button you want to type letters and pick things with."
        SetupFlow.Step.CLOSE -> "Press the one you want to go back and close things with."
        SetupFlow.Step.DONE -> "Press any of the four to see what it does. Select finishes, Close starts again."
    }
    /** What the last press did, in a few words. Good news in your colour; a gentle note otherwise. */
    private fun note(): Pair<String, Boolean>? = when (heard) {
        SetupFlow.Heard.LEARNT -> "Got your ${names[flow.lastPosition]} button" to true
        SetupFlow.Heard.AGAIN -> "That's your ${names[flow.lastPosition]} button. Try the ${names[flow.step.ordinal]} one" to false
        SetupFlow.Heard.NOT_FACE -> if (flow.strayPresses >= 2) "That one isn't one of the four on the right" to false else null
        SetupFlow.Heard.CHOSE -> if (flow.step == SetupFlow.Step.DONE) "${cap(names[flow.close])} button goes back" to true
            else "${cap(names[flow.select])} button selects" to true
        SetupFlow.Heard.SAME_AS_SELECT -> "That one selects. Pick another one to go back" to false
        SetupFlow.Heard.TRIED -> (if (flow.lastJob == FaceSetup.DELETE) "That's Delete" else "That's Space") to true
        SetupFlow.Heard.FINISH -> "Saved" to true
        SetupFlow.Heard.START_OVER -> "Starting again" to false
        SetupFlow.Heard.NONE -> null
    }
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private val steps = listOf("Your four buttons", "Select and Close", "Try them")

    // ---- Drawing ---------------------------------------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = SystemClock.uptimeMillis()
        if (stageOnly) { drawSlide(canvas, w, h, now); if (isShown) postInvalidateOnAnimation(); return }
        val bd = backdrop ?: Backdrop(accent, false).also { backdrop = it }
        bd.setBounds(0, 0, width, height); bd.draw(canvas)

        // Room: the tour's rules. Wide = words beside the stage; short = the smaller title
        val wide = w > h && w / dp >= WIDE_DP
        val short = h / dp < TutorialActivity.SHORT_MAX_DP
        val gutter = (if (wide) Design.SPACE_XXL else Design.SPACE_XL) * dp
        val barH = BAR_DP * dp
        val top = (if (short) Design.SPACE_L else Design.SPACE_XXL) * dp
        val bottom = h - barH - (if (short) Design.SPACE_M else Design.SPACE_L) * dp

        val wordsW = if (wide) (w - 2 * gutter) * WORDS_SHARE else w - 2 * gutter
        val wordsBottom = drawWords(canvas, gutter, top, min(wordsW, BODY_MAX_DP * dp), short, now)

        val stage = if (wide) RectF(gutter + wordsW + Design.SPACE_XL * dp, top, w - gutter, bottom)
                    else RectF(gutter, wordsBottom + Design.SPACE_L * dp, w - gutter, bottom)
        // The steps card: wide screens, at the foot of the words column, level with the stage's foot
        if (wide) {
            val cardH = stepsHeight()
            if (bottom - cardH > wordsBottom + Design.SPACE_L * dp) drawSteps(canvas, RectF(gutter, bottom - cardH, gutter + wordsW, bottom))
        }
        drawStage(canvas, stage, now)
        drawBar(canvas, w, h, barH)

        if (isShown) postInvalidateOnAnimation()
    }

    // The words: label (· step n of 3), title, help, and the note about the last press
    private fun drawWords(canvas: Canvas, x: Float, top: Float, width: Float, short: Boolean, now: Long): Float {
        var y = top
        text.textAlign = Paint.Align.LEFT; text.letterSpacing = 0f
        text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_SECTION * sp
        text.color = label()
        val lead = "Your buttons"
        val base = y - text.ascent()
        canvas.drawText(lead, x, base, text)
        val leadW = text.measureText(lead)
        text.color = SettingsStyle.textSecondary
        canvas.drawText("· Step ${part + 1} of 3", x + leadW + Design.SPACE_S * dp, base, text)
        y += text.descent() - text.ascent() + (if (short) Design.SPACE_XS else Design.SPACE_S) * dp

        // A new step's title rises in softly, so the eye sees it change
        val t = ((now - stepAt) / Design.MOTION_PAGE.toFloat()).coerceIn(0f, 1f)
        val rise = (1f - t) * 6f * dp
        text.typeface = SettingsStyle.medium; text.color = SettingsStyle.textPrimary; text.letterSpacing = Design.TRACKING_TITLE
        text.textSize = (if (short) Design.TYPE_TITLE else Design.TYPE_DISPLAY) * sp
        text.alpha = (90 + 165 * t).toInt()
        y += layout(canvas, title(), x, y + rise, width, 1.1f) + (if (short) Design.SPACE_XS else Design.SPACE_M) * dp
        text.alpha = 255; text.letterSpacing = 0f

        text.typeface = SettingsStyle.regular; text.color = SettingsStyle.textSecondary
        text.textSize = (if (short) Design.TYPE_CAPTION else Design.TYPE_ROW) * sp
        y += layout(canvas, help(), x, y, width, 1.3f)

        // The note: a small pill, so it reads as the app answering, not as more instructions
        val n = note()
        val noteH = (Design.TYPE_CAPTION * sp) * 2.1f
        y += Design.SPACE_M * dp
        if (n != null) {
            val (words, good) = n
            val fade = ((now - heardAt) / Design.MOTION_FADE.toFloat()).coerceIn(0f, 1f)
            text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_CAPTION * sp
            val icon = noteH * 0.42f
            val pad = Design.SPACE_M * dp
            val pillW = min(width, pad + icon + Design.SPACE_S * dp + text.measureText(words) + pad)
            val box = RectF(x, y, x + pillW, y + noteH)
            fill.color = if (good) Contrast.withAlpha(accent, (Design.ALPHA_TINT_TAG * fade).toInt()) else Contrast.withAlpha(SettingsStyle.surfaceFocused, (255 * fade).toInt())
            canvas.drawRoundRect(box, noteH / 2, noteH / 2, fill)
            if (!good) { ring.color = Contrast.withAlpha(Design.EDGE_CARD, ((Design.EDGE_CARD ushr 24) * fade).toInt()); ring.strokeWidth = Design.STROKE_HAIRLINE * dp
                canvas.drawRoundRect(box, noteH / 2, noteH / 2, ring) }
            val ix = x + pad + icon / 2; val iy = y + noteH / 2
            val ink = if (good) label() else SettingsStyle.textSecondary
            if (good) tick(canvas, ix, iy, icon / 0.84f, Contrast.withAlpha(ink, (255 * fade).toInt()), 1f, Design.STROKE_ICON)
            else { fill.color = Contrast.withAlpha(ink, (255 * fade).toInt()); canvas.drawCircle(ix, iy, icon * 0.16f, fill) }
            text.color = if (good) label() else SettingsStyle.textPrimary; text.alpha = (255 * fade).toInt()
            val tx = ix + icon / 2 + Design.SPACE_S * dp
            canvas.drawText(TextUtilsEllipsize(words, box.right - pad - tx), tx, iy - (text.descent() + text.ascent()) / 2, text)
            text.alpha = 255
        }
        return y + noteH
    }

    private fun TextUtilsEllipsize(s: String, room: Float): String =
        android.text.TextUtils.ellipsize(s, text, max(0f, room), android.text.TextUtils.TruncateAt.END).toString()

    /** Draws wrapped words at (x, y) and returns their height. */
    private fun layout(canvas: Canvas, s: String, x: Float, y: Float, width: Float, spacing: Float): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, text, max(1, width.toInt()))
            .setLineSpacing(0f, spacing).setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED).setIncludePad(false).build()
        canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore()
        return l.height.toFloat()
    }

    // The steps card: three rows, like the tour's card (the step you're on bright, the ones done ticked)
    private val rowDp = 40f
    private fun stepsHeight() = (rowDp * 3 + Design.SPACE_M * 2) * dp
    private fun drawSteps(canvas: Canvas, box: RectF) {
        card(canvas, box)
        val pad = Design.SPACE_L * dp
        var y = box.top + Design.SPACE_M * dp
        for (i in 0..2) {
            val cy = y + rowDp * dp / 2
            val dotR = 11f * dp; val dx = box.left + pad + dotR
            val done = i < part; val here = i == part
            when {
                done -> { fill.color = accent; canvas.drawCircle(dx, cy, dotR, fill); tick(canvas, dx, cy, dotR, Contrast.inkFor(accent), 1f, Design.STROKE_ICON) }
                here -> { ring.color = accent; ring.strokeWidth = Design.STROKE_FOCUS * dp; canvas.drawCircle(dx, cy, dotR - dp, ring) }
                else -> { ring.color = Design.DOT_IDLE; ring.strokeWidth = Design.STROKE_HAIRLINE * dp * 1.5f; canvas.drawCircle(dx, cy, dotR - dp, ring) }
            }
            if (!done) {
                text.textAlign = Paint.Align.CENTER; text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_MIN * sp
                text.color = if (here) label() else SettingsStyle.textSecondary
                canvas.drawText("${i + 1}", dx, cy - (text.descent() + text.ascent()) / 2, text)
                text.textAlign = Paint.Align.LEFT
            }
            // Its pips: one per press (4, 2, and none for trying)
            val pips = listOf(4, 2, 0)[i]
            val pipGap = Design.SPACE_M * dp; val pipR = 3.5f * dp
            val pipsW = if (pips == 0) 0f else (pips - 1) * pipGap + 2 * pipR
            val filled = when { done -> pips; here && i == 0 -> flow.step.ordinal; here && i == 1 -> flow.step.ordinal - 4; else -> 0 }
            for (k in 0 until pips) {
                fill.color = if (k < filled) accent else Contrast.withAlpha(Design.DOT_IDLE, if (here) 255 else 120)
                canvas.drawCircle(box.right - pad - pipsW + pipR + k * pipGap, cy, pipR, fill)
            }
            text.typeface = if (here) SettingsStyle.medium else SettingsStyle.regular; text.textSize = Design.TYPE_CAPTION * sp
            text.color = if (here) SettingsStyle.textPrimary else SettingsStyle.textSecondary
            val tx = dx + dotR + Design.SPACE_M * dp
            canvas.drawText(TextUtilsEllipsize(steps[i], box.right - pad - pipsW - Design.SPACE_M * dp - tx), tx, cy - (text.descent() + text.ascent()) / 2, text)
            y += rowDp * dp
        }
    }

    private fun card(canvas: Canvas, box: RectF) {
        val r = Design.RADIUS_CARD * dp
        fill.color = Contrast.withAlpha(Design.SURFACE_CARD, CARD_ALPHA); canvas.drawRoundRect(box, r, r, fill)
        ring.color = Design.EDGE_CARD; ring.strokeWidth = max(1f, Design.STROKE_HAIRLINE * dp)
        val e = ring.strokeWidth / 2
        canvas.drawRoundRect(box.left + e, box.top + e, box.right - e, box.bottom - e, r - e, r - e, ring)
    }

    /**
     * The slide: the stage card filling the view, the setup screen's own label and title at its top left (the title
     * rising in when it changes), the diamond under them.
     */
    private fun drawSlide(canvas: Canvas, w: Float, h: Float, now: Long) {
        val box = RectF(0f, 0f, w, h)
        card(canvas, box)
        if (h / dp < SLIDE_COMPACT_DP) {
            // A very short card (small 4:3 and upright handheld screens): one line of title, the rest for the buttons
            val pad = Design.SPACE_M * dp
            val t = ((now - stepAt) / Design.MOTION_PAGE.toFloat()).coerceIn(0f, 1f)
            text.textAlign = Paint.Align.LEFT; text.letterSpacing = 0f
            text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_CAPTION * sp; text.color = SettingsStyle.textPrimary
            text.alpha = (255 * (0.35f + 0.65f * Design.EASE_OUT.getInterpolation(t))).toInt()
            canvas.drawText(TextUtilsEllipsize(slideTitle(), w - 2 * pad), pad, pad - text.ascent(), text)
            text.alpha = 255
            stagePad = Design.SPACE_S * dp
            drawStage(canvas, RectF(0f, pad + text.descent() - text.ascent(), w, h), now, withCard = false)
            stagePad = Design.SPACE_L * dp
            return
        }
        val short = h / dp < SLIDE_SHORT_DP
        val pad = (if (short) Design.SPACE_M else Design.SPACE_L) * dp
        // The title on top, or (a short, wide card) in a column on the left: whichever leaves the buttons more room
        val colW = min(w * SLIDE_COLUMN_SHARE, SLIDE_COLUMN_MAX_DP * dp)
        val topH = captionHeight(w - 2 * pad, short)
        val side = sizeFor(RectF(colW, 0f, w, h)) > sizeFor(RectF(0f, topH, w, h)) * 1.1f
        val x = pad; var y = pad
        val wordsW = if (side) colW - pad - Design.SPACE_S * dp else w - 2 * pad
        text.textAlign = Paint.Align.LEFT; text.letterSpacing = 0f
        text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_CAPTION * sp
        val base = y - text.ascent()
        text.color = label(); canvas.drawText("Your buttons", x, base, text)
        if (captionTitle == null) {
            val leadW = text.measureText("Your buttons")
            text.color = SettingsStyle.textSecondary
            val step = "· Step ${part + 1} of 3"
            if (side && leadW + Design.SPACE_S * dp + text.measureText(step) > wordsW) { y += text.descent() - text.ascent(); canvas.drawText(step.removePrefix("· "), x, y - text.ascent(), text) }
            else canvas.drawText(step, x + leadW + Design.SPACE_S * dp, base, text)
        }
        y += text.descent() - text.ascent() + Design.SPACE_XS * dp
        val t = ((now - stepAt) / Design.MOTION_PAGE.toFloat()).coerceIn(0f, 1f)
        val eased = Design.EASE_OUT.getInterpolation(t)
        titlePaint(short)
        text.alpha = (255 * (0.35f + 0.65f * eased)).toInt()
        y += layout(canvas, slideTitle(), x, y + (1f - eased) * 6f * dp, wordsW, 1.1f)
        text.alpha = 255; text.letterSpacing = 0f
        drawStage(canvas, if (side) RectF(colW, 0f, w, h) else RectF(box.left, y, box.right, box.bottom), now, withCard = false)
    }
    private fun slideTitle() = captionTitle ?: if (flow.step == SetupFlow.Step.DONE) "All set" else title()
    private fun titlePaint(short: Boolean) {
        text.typeface = SettingsStyle.medium; text.color = SettingsStyle.textPrimary; text.letterSpacing = Design.TRACKING_TITLE
        text.textSize = (if (short) Design.TYPE_ROW else Design.TYPE_TITLE) * sp
    }
    /** The label and the longest title, one above the other, at [width]: the room the caption takes on top. */
    private fun captionHeight(width: Float, short: Boolean): Float {
        text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_CAPTION * sp
        var hgt = text.descent() - text.ascent() + Design.SPACE_XS * dp + (if (short) Design.SPACE_M else Design.SPACE_L) * dp
        titlePaint(short)
        hgt += listOf("Press your bottom button", "Which one goes back?", ANY).maxOf { s ->
            StaticLayout.Builder.obtain(s, 0, s.length, text, max(1, width.toInt())).setLineSpacing(0f, 1.1f).setIncludePad(false).build().height }
        text.letterSpacing = 0f
        return hgt
    }

    /** How far apart the buttons can be in [box] with every job's tag inside it; also picks where the tags go. */
    private fun sizeFor(box: RectF): Float {
        val pad = stagePad
        // Sized from the room: the tags beside the side buttons and above/below the others always fit inside the card
        text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_CAPTION * sp
        val tagW = listOf("Delete", "Select", "Space", "Close").maxOf { text.measureText(it) } + Design.SPACE_M * dp * 2
        val tagH = text.textSize * 2f
        val k = 1f + RADIUS_SHARE                     // spread plus the button's own radius, as a share of the spread
        val most = Design.SETUP_SPREAD_DP * dp * 1.15f
        val high = (box.height() - 2 * pad - 2 * (Design.SPACE_S * dp + tagH)) / (2 * k)
        // Side tags beside their buttons, or (narrow cards, like an upright phone) under them, whichever leaves the bigger diamond
        val beside = minOf(high, (box.width() - 2 * pad - 2 * (Design.SPACE_S * dp + tagW)) / (2 * k), most)
        val under = minOf(high, (box.width() - 2 * pad - tagW / 2 + tagW / 2) / (2 * k), most)
            .takeIf { (1f - RADIUS_SHARE) * it > tagW / 2 + Design.SPACE_S * dp } ?: 0f
        // Short cards: the top and bottom jobs tucked beside their buttons (on the right) frees the height for bigger buttons.
        // Whichever of the three ways gives the biggest buttons wins
        val tuckedHigh = (box.height() - 2 * pad) / (2 * k)
        val tucked = minOf(tuckedHigh, (box.width() - 2 * pad - 2 * (Design.SPACE_S * dp + tagW)) / (2 * k), most)
            .takeIf { it * (1f - RADIUS_SHARE) > tagH * 0.55f } ?: 0f
        tagsTucked = tucked > max(beside, under) * 1.1f
        sideUnder = !tagsTucked && under > beside * 1.1f
        return (if (tagsTucked) tucked else max(beside, if (sideUnder) under else 0f)).coerceAtLeast(18f * dp)
    }

    // The stage: a card with the four buttons as a big diamond
    private var sideUnder = false
    private var stagePad = Design.SPACE_L * dp
    private var tagsTucked = false
    private fun drawStage(canvas: Canvas, box: RectF, now: Long, withCard: Boolean = true) {
        if (box.height() < 80 * dp || box.width() < 120 * dp) return
        if (withCard) card(canvas, box)
        val gap = sizeFor(box)
        val r = gap * RADIUS_SHARE
        tagsForCheck.clear(); buttonsForCheck.clear(); stageForCheck.set(box)
        val cx = box.centerX(); val cy = box.centerY()
        val at = listOf(cx - gap to cy, cx to cy - gap, cx + gap to cy, cx to cy + gap)   // left, top, right, bottom
        val step = flow.step
        val result = flow.result
        val phase = (now % Design.SETUP_PULSE_MS) / Design.SETUP_PULSE_MS.toFloat()
        val pulse = 0.5f + 0.5f * sin(2 * PI * phase).toFloat()
        for (p in FaceSetup.POSITIONS) {
            val (x, y) = at[p]
            val code = flow.sendsAt(p)
            val target = step.ordinal == p
            val choosing = (step == SetupFlow.Step.SELECT) || (step == SetupFlow.Step.CLOSE && p != flow.select)
            val colour = when {
                p == flow.select && step >= SetupFlow.Step.CLOSE -> Design.SETUP_SELECT
                p == flow.close && step == SetupFlow.Step.DONE -> Design.SETUP_CLOSE
                code >= 0 -> SettingsStyle.surfaceFocused
                else -> 0
            }
            // Just pressed: a short swell, so the eye sees which one it heard
            val since = now - heardAt
            val swell = if (p == flow.lastPosition) (1f - (since / Design.SETUP_SWELL_MS.toFloat()).coerceIn(0f, 1f)) else 0f
            val rr = r * (1f + 0.12f * swell)
            buttonsForCheck += RectF(x - r, y - r, x + r, y + r)

            // The plate every button sits on: the badges' dark plate with their edge, so it reads as a button
            fill.color = Design.BADGE_PLATE; canvas.drawCircle(x, y, rr, fill)
            if (colour != 0) { fill.color = colour; canvas.drawCircle(x, y, rr, fill) }
            if (target || choosing) {
                // Waiting for you: it breathes, and a slow ripple goes out from it (only the one you're asked for)
                fill.color = Contrast.withAlpha(accent, (36 + 50 * pulse).toInt()); if (colour == 0) canvas.drawCircle(x, y, rr, fill)
                if (target) {
                    ring.color = Contrast.withAlpha(accent, (110 * (1f - phase)).toInt()); ring.strokeWidth = Design.STROKE_FOCUS * dp
                    canvas.drawCircle(x, y, rr * (1f + RIPPLE * phase), ring)
                }
                ring.color = accent; ring.strokeWidth = (Design.STROKE_FOCUS + 1f * pulse) * dp
                canvas.drawCircle(x, y, rr - ring.strokeWidth / 2f, ring)
            } else if (colour == 0) {
                ring.color = Design.DOT_IDLE; ring.strokeWidth = Design.STROKE_HAIRLINE * dp * 1.5f
                canvas.drawCircle(x, y, rr - ring.strokeWidth / 2f, ring)
            } else {
                ring.color = Design.EDGE_CARD; ring.strokeWidth = Design.STROKE_HAIRLINE * dp
                canvas.drawCircle(x, y, rr - ring.strokeWidth / 2f, ring)
            }
            // The tick draws itself in as the press lands
            if (code >= 0) {
                val draw = if (p == flow.lastPosition && heard == SetupFlow.Heard.LEARNT) (since / TICK_MS.toFloat()).coerceIn(0f, 1f) else 1f
                val ink = if (colour == 0 || colour == SettingsStyle.surfaceFocused) SettingsStyle.textPrimary else Contrast.inkFor(colour)
                val mark = marks?.getOrNull(p)
                val tickShare = 1f - ticksGone
                if (tickShare > 0f) tick(canvas, x, y, r, Contrast.withAlpha(ink, (255 * tickShare).toInt()), draw, Design.STROKE_ICON * 1.4f)
                if (mark != null && marksShown > 0f) {
                    // a new style's marks grow in a touch as they fade in, so the change reads as one movement
                    val grow = 0.82f + 0.18f * marksShown
                    canvas.save(); canvas.scale(grow, grow, x, y)
                    mark(canvas, mark, x, y, r, Contrast.withAlpha(ink, (255 * marksShown).toInt()))
                    canvas.restore()
                }
            }
            // At the end, the one you just tried keeps an outline, so you see which one you're testing (not on a slide: nothing is tried there)
            if (step == SetupFlow.Step.DONE && p == flow.lastPosition && !stageOnly) {
                ring.color = accent; ring.strokeWidth = Design.STROKE_FOCUS * dp
                canvas.drawCircle(x, y, rr + Design.STROKE_FOCUS * dp * 2f, ring)
            }
            // Its job, once it has one
            val job = when {
                p == flow.select && step >= SetupFlow.Step.CLOSE -> "Select"
                p == flow.close && step == SetupFlow.Step.DONE -> "Close"
                result != null && p == result.delete -> "Delete"
                result != null && p == result.space -> "Space"
                else -> null
            }
            if (job != null) tag(canvas, job, p, x, y, rr, colour.takeIf { it == Design.SETUP_SELECT || it == Design.SETUP_CLOSE })
        }
    }

    // The control bar: the tour's bar (hairline, badges at their inline size); at the end, what Select and Close do
    private fun drawBar(canvas: Canvas, w: Float, h: Float, barH: Float) {
        fill.color = Design.EDGE_DIVIDER; canvas.drawRect(0f, h - barH, w, h - barH + max(1f, Design.STROKE_HAIRLINE * dp), fill)
        text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_CAPTION * sp; text.textAlign = Paint.Align.LEFT
        text.color = SettingsStyle.textSecondary
        badges.gapBadgeToText = Design.BADGE_TEXT_GAP
        val leave = HintText.parse(if (w / dp < 420) "Hold {START} Leave" else "Hold {START} Leave without changes")
        val cy = h - barH / 2
        val parts = mutableListOf<Pair<Int, String>>()
        if (flow.step == SetupFlow.Step.DONE) { parts += Design.SETUP_SELECT to "Finish"; parts += Design.SETUP_CLOSE to "Start again" }
        val dotR = 5f * dp; val gap = Design.SPACE_XL * dp
        val partW = parts.map { 2 * dotR + Design.SPACE_S * dp + text.measureText(it.second) }
        val total = partW.sum() + gap * parts.size + badges.measureLine(leave, text)
        var x = (w - total) / 2f
        parts.forEachIndexed { i, (c, word) ->
            fill.color = c; canvas.drawCircle(x + dotR, cy, dotR, fill)
            canvas.drawText(word, x + 2 * dotR + Design.SPACE_S * dp, cy - (text.descent() + text.ascent()) / 2, text)
            x += partW[i] + gap
        }
        badges.drawLine(canvas, leave, x, cy, text)
    }

    /** "Got it": a tick, drawn with the icons' stroke, [p] of the way along (0 to 1). */
    private val tickPath = Path()
    private val tickPart = Path()
    private val measure = PathMeasure()
    private fun tick(canvas: Canvas, x: Float, y: Float, r: Float, colour: Int, p: Float, stroke: Float) {
        if (p <= 0f) return
        val s = r * 0.42f
        tickPath.reset(); tickPath.moveTo(x - s, y + s * 0.05f); tickPath.lineTo(x - s * 0.3f, y + s * 0.7f); tickPath.lineTo(x + s, y - s * 0.6f)
        ring.color = colour; ring.strokeWidth = stroke * dp; ring.strokeCap = Paint.Cap.ROUND; ring.strokeJoin = Paint.Join.ROUND
        if (p >= 1f) canvas.drawPath(tickPath, ring)
        else { measure.setPath(tickPath, false); tickPart.reset(); measure.getSegment(0f, measure.length * p, tickPart, true); canvas.drawPath(tickPart, ring) }
        ring.strokeCap = Paint.Cap.BUTT
    }

    /**
     * A controller's mark on a button: a letter, or one of the PlayStation shapes drawn with the icons' stroke
     * (drawn, not typed, so it's the same crisp shape on every device).
     */
    private fun mark(canvas: Canvas, m: String, x: Float, y: Float, r: Float, colour: Int) {
        val s = r * 0.36f
        ring.color = colour; ring.strokeWidth = Design.STROKE_ICON * 1.4f * dp; ring.strokeCap = Paint.Cap.ROUND; ring.strokeJoin = Paint.Join.ROUND
        when (m) {
            "triangle" -> { tickPath.reset(); tickPath.moveTo(x, y - s * 1.05f); tickPath.lineTo(x + s * 1.05f, y + s * 0.75f); tickPath.lineTo(x - s * 1.05f, y + s * 0.75f); tickPath.close(); canvas.drawPath(tickPath, ring) }
            "circle" -> canvas.drawCircle(x, y, s, ring)
            "cross" -> { canvas.drawLine(x - s * 0.85f, y - s * 0.85f, x + s * 0.85f, y + s * 0.85f, ring); canvas.drawLine(x + s * 0.85f, y - s * 0.85f, x - s * 0.85f, y + s * 0.85f, ring) }
            "square" -> canvas.drawRoundRect(x - s * 0.85f, y - s * 0.85f, x + s * 0.85f, y + s * 0.85f, s * 0.15f, s * 0.15f, ring)
            else -> {
                text.typeface = SettingsStyle.medium; text.textSize = r * 0.9f; text.textAlign = Paint.Align.CENTER; text.color = colour
                canvas.drawText(m, x, y - (text.descent() + text.ascent()) / 2f, text); text.textAlign = Paint.Align.LEFT
            }
        }
        ring.strokeCap = Paint.Cap.BUTT
    }

    /** A small pill beside a button, on its outer side, saying what it does. */
    private fun tag(canvas: Canvas, word: String, p: Int, x: Float, y: Float, r: Float, colour: Int?) {
        text.typeface = SettingsStyle.medium; text.textSize = Design.TYPE_CAPTION * sp; text.textAlign = Paint.Align.CENTER
        val tw = text.measureText(word) + Design.SPACE_M * dp * 2; val th = text.textSize * 2f
        val off = r + Design.SPACE_S * dp
        val (tx, ty) = when (p) {
            FaceSetup.LEFT, FaceSetup.RIGHT -> if (sideUnder) x to y + off + th / 2f
                else if (p == FaceSetup.LEFT) x - off - tw / 2f to y else x + off + tw / 2f to y
            FaceSetup.TOP -> if (tagsTucked) x + off + tw / 2f to y else x to y - off - th / 2f
            else -> if (tagsTucked) x + off + tw / 2f to y else x to y + off + th / 2f
        }
        val box = RectF(tx - tw / 2f, ty - th / 2f, tx + tw / 2f, ty + th / 2f)
        tagsForCheck += RectF(box)
        fill.color = colour ?: SettingsStyle.surfaceFocused
        canvas.drawRoundRect(box, th / 2f, th / 2f, fill)
        if (colour == null) { ring.color = Design.EDGE_CARD; ring.strokeWidth = Design.STROKE_HAIRLINE * dp; canvas.drawRoundRect(box, th / 2f, th / 2f, ring) }
        text.color = if (colour != null) Contrast.inkFor(colour) else SettingsStyle.textPrimary
        canvas.drawText(word, tx, ty - (text.descent() + text.ascent()) / 2, text)
        text.textAlign = Paint.Align.LEFT
    }

    companion object {
        const val WIDE_DP = 560f        // from here the words sit beside the stage (narrower: on top of it)
        const val WORDS_SHARE = 0.42f   // the words column's share of the width when wide
        const val BODY_MAX_DP = 520f    // comfortable line length for the help
        const val BAR_DP = 44f          // the tour's control bar
        const val RADIUS_SHARE = 0.42f  // a button's radius as a share of the diamond's spread
        const val CARD_ALPHA = 0xE6     // the cards sit a little into the backdrop's light, as the tour's do
        const val TICK_MS = 220L        // the tick drawing itself in
        const val RIPPLE = 0.32f        // the waiting ripple grows this far past the button (kept inside the card)
        const val SLIDE_SHORT_DP = 260f // a slide card shorter than this takes the smaller title
        const val SLIDE_COMPACT_DP = 210f // shorter than this: one line of title, the buttons get the rest
        const val SLIDE_COLUMN_SHARE = 0.4f   // the title's column beside the buttons, on short wide cards
        const val SLIDE_COLUMN_MAX_DP = 220f
        const val ANY = "Works with any controller"
    }
}
