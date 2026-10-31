package com.example.gridime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The on-screen keyboard: draws the grid, moves the highlight, reports key presses. */
class KeyGridView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onTool(tool: Tool)
        fun onClip(index: Int)
        /** A word suggestion was taken: replace the word being typed with it, plus a space. */
        fun onWord(word: String) {}
        /** Swap the [expected] text just before the cursor for [replacement]. False if it isn't there any more. */
        fun onReplaceLast(expected: String, replacement: String): Boolean = false
    }

    var listener: Listener? = null
    var feedback: Feedback? = null

    /**
     * Tour and What's new only: which button the keyboard just "used", so the device picture can
     * light it. [held] = true / false while a button is held and let go, null for a quick press.
     */
    var onDemoInput: ((key: String, held: Boolean?) -> Unit)? = null

    private fun demoMove(dx: Int, dy: Int) {
        val key = when {
            dx > 0 -> "Dright"
            dx < 0 -> "Dleft"
            dy < 0 -> "Dup"
            dy > 0 -> "Ddown"
            else -> return
        }
        onDemoInput?.invoke(key, null)
    }

    // =====================================================================
    // SIZES
    // =====================================================================
    private val dp = resources.displayMetrics.density
    /** Normal, Large, Largest. (TV use is TV mode, below: not a bigger size.) */
    private val sizeScales = floatArrayOf(1f, 1.2f, 1.4f)
    private var sizeLevel = 0
    /**
     * Shrinks the keyboard on short screens (small 4:3 handhelds, split screen) so it
     * never covers more than [MAX_SCREEN_SHARE] of the height. 1 = no shrinking needed.
     */
    private var fit = 1f
    /** Previews (the tour): when the view is narrower than the real keyboard, scale everything down with it. */
    var keepProportions = false
        set(v) { field = v; requestLayout() }
    /**
     * The tour's window sets the keyboard's size directly: one scale for the keys, letters and hints,
     * with the width chosen to match (see TutorialActivity's StagePanel). 0 = not set.
     */
    var previewFit = 0f
        set(v) { if (field != v) { field = v; requestLayout() } }
    /** The keyboard's height at scale 1 (the number row adds its own). */
    fun previewUnitHeightPx() = (DESIGN_HEIGHT_DP + (if (settings.numberRow) NUM_ROW_DP + NUM_ROW_GAP_DP else 0f)) * dp * sizeScales[sizeLevel]
    /** The keyboard's width at scale 1 in the tour: always [PREVIEW_ASPECT] times the letters' height, so keys keep one pleasant shape. */
    fun previewUnitWidthPx() = PREVIEW_ASPECT * DESIGN_HEIGHT_DP * dp * sizeScales[sizeLevel]

    /** The TV line's hint while nothing is typed: "controller", or "remote" when that's what you hold. */
    var placeholder = "Type with your controller"
        set(v) { field = v; invalidate() }
    private val scale get() = sizeScales[sizeLevel] * fit
    private fun u(v: Float) = v * dp * scale          // design units -> pixels at this size
    /** Pixels per design unit at this size (the tour draws its loop line along the TV panel with it). */
    internal fun unitForCheck() = u(1f)

    private val toolHeight get() = u(26f)
    private val keyHeight get() = u(31f)
    private val gap get() = u(Design.SPACE_XS)
    private val edge get() = u(EDGE_DP)
    /** Corner badges share the key corner's centre (nested corners), never closer than 3 units to the edge. */
    private val badgeInset get() = u(max(3f, settings.keyShape.radiusDp - BADGE_RADIUS_DP))
    private val corner get() = u(settings.keyShape.radiusDp)
    private val maxWidth = 1000f * dp                  // keys stop stretching on very wide screens

    // =====================================================================
    // APPEARANCE (filled in by applySettings)
    // =====================================================================
    private var settings = KeyboardSettings()
    private var colBackground = Color.BLACK
    private var colKey = Color.DKGRAY
    private var colSpecialKey = Color.DKGRAY
    private var colAccent = Color.BLUE
    private var colAccentSoft = Color.BLUE
    private var colGlow = Color.BLUE
    /** What sits behind the keys for contrast maths: the background, or black for the Clear theme. */
    private var colBackdrop = Color.BLACK
    private var colEdge = 0                  // key borders (Outline and Clear themes), 0 = none
    private var clear = false
    private var chipFill = Contrast.withAlpha(Color.WHITE, Design.ALPHA_FAINT)
    private var chipEdge = Contrast.withAlpha(Color.WHITE, Design.ALPHA_FAINT_STRONG)

    private val badges = BadgePainter(dp)

    /** The tool strip: word suggestions take the place of Cut / Copy / Paste while a word is being typed. */
    private val toolRow: List<Key> get() = Layouts.toolRow(settings.selectAllButton, if (showWords) words else emptyList())
    private var letterRows = Layouts.letterRows(KeyLayout.GRID)
    private var symbolRows = Layouts.symbolRows(KeyLayout.GRID)

    fun applySettings(s: KeyboardSettings) {
        val layoutChanged = s.layout != settings.layout || s.language != settings.language || s.smartKeys != settings.smartKeys ||
            s.selectAllButton != settings.selectAllButton || s.numberRow != settings.numberRow
        val heightChanged = s.numberRow != settings.numberRow
        resetAccents()
        settings = s
        val tone = s.tone
        clear = tone.clear
        if (clear) {
            // See-through: 0 = a dark veil behind the keys, 10 = fully clear.
            // The keys keep a dark tint at every level, so white letters stay readable on anything.
            val t = s.seeThrough.coerceIn(0, 10) / 10f
            colBackground = Contrast.withAlpha(Color.BLACK, ((1f - t) * Design.CLEAR_VEIL * 255).roundToInt())
            colKey = Contrast.withAlpha(Color.BLACK, ((Design.CLEAR_KEY - Design.CLEAR_KEY_FADE * t) * 255).roundToInt())
            colSpecialKey = Contrast.withAlpha(Color.BLACK, ((Design.CLEAR_SPECIAL - Design.CLEAR_KEY_FADE * t) * 255).roundToInt())
            colBackdrop = Color.BLACK
        } else {
            colBackground = tone.background
            colKey = tone.key
            colSpecialKey = tone.special
            colBackdrop = tone.background
        }
        colEdge = tone.edge
        val chipInk = Contrast.inkFor(colBackdrop)
        // See-through: chips sit on the app behind, which may be white, so they get the special keys' dark tint
        chipFill = if (clear) colSpecialKey else Contrast.withAlpha(chipInk, Design.ALPHA_FAINT)
        chipEdge = Contrast.withAlpha(chipInk, Design.ALPHA_FAINT_STRONG)
        // The highlight must stand out (3:1) on this theme's keys and background: deepened on Light
        colAccent = if (clear) s.accent else Contrast.accentOn(s.accent, colKey, colSpecialKey, colBackground)
        colAccentSoft = Contrast.withAlpha(colAccent, Design.ALPHA_TINT_SOFT)
        colGlow = Contrast.withAlpha(colAccent, Design.ALPHA_TINT_GLOW)
        badges.style = s.buttonStyle
        letterRows = Layouts.letterRows(s.layout, s.language, if (s.smartKeys) field else FieldKind.TEXT)
        symbolRows = Layouts.symbolRows(s.layout, s.language, s.numberRow)
        if (!s.wordSuggestions) words = emptyList()
        if (layer == Layer.NUMPAD && !numeric) layer = Layer.LETTERS     // smart keys just turned off
        val level = s.sizeLevel.coerceIn(0, sizeScales.lastIndex)
        val sizeChanged = level != sizeLevel
        if (sizeChanged) sizeLevel = level
        if (sizeChanged || heightChanged) requestLayout()
        badges.scale = scale
        if (layoutChanged || sizeChanged) preferredCentre = null
        focusReady = false
        clampFocus()
        invalidate()
    }

    // =====================================================================
    // PAINTS AND FONTS
    // =====================================================================
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fontRegular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val fontMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val fontHyperlegible by lazy { runCatching { resources.getFont(R.font.atkinson_next) }.getOrNull() }
    private val fontInter by lazy { runCatching { resources.getFont(R.font.inter) }.getOrNull() }

    private val customFont: Typeface?
        get() = when (settings.font) {
            FontChoice.HYPERLEGIBLE -> fontHyperlegible
            FontChoice.INTER -> fontInter
            FontChoice.SYSTEM -> null
        }

    /**
     * Sets the chosen font at a weight (400 regular, 500 medium, 600 semibold).
     *
     * Speed: asking a Paint for a new weight builds a brand-new font every time, and
     * Android then has to re-measure every letter from scratch. That ran for every label
     * on every frame and was the main cause of the highlight lagging. Now each font and
     * weight is built once and kept, so a frame only swaps in a ready-made font.
     */
    private fun applyFont(paint: Paint, weight: Int) {
        lastFontWeight = weight
        paint.fontVariationSettings = null                  // free when it's already empty
        val custom = customFont
        paint.typeface = if (custom != null) weighted(custom, weight)
        else if (weight >= 500) fontMedium else fontRegular
    }

    private var lastFontWeight = 400
    private val weightedFonts = HashMap<Pair<Typeface, Int>, Typeface>()
    private val fontMaker = Paint()

    /** The font at this weight, built the first time it's needed and then reused. */
    private fun weighted(base: Typeface, weight: Int): Typeface =
        weightedFonts.getOrPut(base to weight) {
            fontMaker.fontVariationSettings = null
            fontMaker.typeface = base
            fontMaker.fontVariationSettings = "'wght' $weight"
            val derived = fontMaker.typeface
            if (derived != null && derived !== base) derived else buildWeighted(base, weight) ?: base
        }

    /** Backup route for Android versions where a Paint keeps the weight to itself. */
    private fun buildWeighted(base: Typeface, weight: Int): Typeface? = runCatching {
        val resId = if (base === fontInter) R.font.inter else R.font.atkinson_next
        val font = android.graphics.fonts.Font.Builder(resources, resId)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight.coerceIn(1, 1000))
            .build()
        Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font).build())
            .setSystemFallback("sans-serif")
            .build()
    }.getOrNull()

    // =====================================================================
    // STATE
    // =====================================================================
    private var layer = Layer.LETTERS
    private var layerBeforeClips = Layer.LETTERS
    private var shiftState = ShiftState.OFF
    private var heldShift = false          // R2 is held right now
    private var selRow = 2                 // row 0 is the tool strip
    private var selCol = 4
    private var selecting = false
    private var selectedCount = 0
    private var clipPreview: String? = null
    private var clips: List<String> = emptyList()

    /**
     * Column memory: where you were aiming horizontally when you started moving up
     * or down. Keeps vertical moves on a straight line, so Down then Up always
     * returns to the key you left. Any sideways move forgets it.
     */
    private var preferredCentre: Float? = null

    private fun allRows(): List<List<Key>> = listOf(toolRow) +
        (if (hasNumberRow) listOf(Layouts.numberRow()) else emptyList()) + when (layer) {
        Layer.LETTERS -> letterRows
        Layer.SYMBOLS -> symbolRows
        Layer.CLIPS -> Layouts.clipRows(clips)
        Layer.NUMPAD -> Layouts.numpadRows(field)
    }

    // =====================================================================
    // NUMBER ROW, WORD SUGGESTIONS, HOLD-A ACCENTS
    // =====================================================================
    /** The number row shows on the letters and symbols pages (the number pad and clipboard have their own layouts). */
    private val hasNumberRow get() = settings.numberRow && (layer == Layer.LETTERS || layer == Layer.SYMBOLS)
    /** The row the highlight starts on: the middle of the letters. */
    private val homeRow get() = if (hasNumberRow) 3 else 2

    private var words: List<String> = emptyList()
    private var wordPrefix = ""
    /** Words only on the letters page and never in select mode (Cut and Copy matter then). */
    private val showWords get() = words.isNotEmpty() && layer == Layer.LETTERS && !selecting && settings.wordSuggestions
    private var stripFade = 1f
    private var stripAnim: ValueAnimator? = null

    /** Called by the service when typing pauses: [list] most likely first, [prefix] = the word being typed. */
    fun setSuggestions(list: List<String>, prefix: String) {
        if (list == words && prefix == wordPrefix) return
        val before = showWords
        if (list != words) wordsSince = android.os.SystemClock.uptimeMillis()
        keepStripFocus {
            words = list
            wordPrefix = prefix
        }
        if (before != showWords) fadeStripIn()
        invalidate()
    }

    /** For the checks: the word suggestions on show now (none while the strip shows the tools). */
    internal fun wordsForCheck(): List<String> = if (showWords) words else emptyList()

    /**
     * For the checks ("what you see is what happens", 30 Oct 2026): told whenever the tour or What's new does something
     * the screen doesn't show, such as a key pressed while the highlight is elsewhere, a word taken that isn't on show,
     * or one taken before it could be read.
     */
    internal var truthCheck: ((String) -> Unit)? = null
    private var wordsSince = 0L
    /** A word is taken: it must be on show, and have been for [WORD_SEEN_MS]. */
    private fun checkWordSeen(word: String) {
        val report = truthCheck ?: return
        if (!showWords || word !in words) { report("the word \"$word\" was taken but isn't on show (showing: ${if (showWords) words else "the tools"})"); return }
        val seen = android.os.SystemClock.uptimeMillis() - wordsSince
        if (seen < WORD_SEEN_MS) report("the word \"$word\" was taken $seen ms after it showed (at least $WORD_SEEN_MS)")
    }
    /** The tour takes the first word with R3 (no highlight on it): it must be the first word on show. */
    fun noteBestWordTaken(word: String) {
        if (truthCheck == null || quiet) return
        if (showWords && words.firstOrNull() != word) truthCheck?.invoke("R3 took \"$word\" but the first word on show is \"${words.firstOrNull()}\"")
        checkWordSeen(word)
    }

    /** The word R3 takes, or null when none is showing. */
    fun bestWord(): String? = if (showWords) words.firstOrNull() else null

    /** For the tour and What's new: move the highlight onto the first suggested word. */
    fun focusBestWord() {
        val i = allRows()[0].indexOfFirst { it.best }
        if (i < 0) return
        selRow = 0
        selCol = i
        preferredCentre = null
        animateFocus()
    }

    /** When the strip changes under the highlight, keep it on whatever is now in the same place. */
    private inline fun keepStripFocus(change: () -> Unit) {
        val onStrip = selRow == 0 && accents?.open != true
        val centre = if (onStrip) centreOf(allRows()[0], selCol.coerceIn(0, allRows()[0].lastIndex)) else 0f
        change()
        if (onStrip) {
            selCol = nearestCol(allRows()[0], centre)
            skipGap(0)
            animateFocus()
        }
    }

    /** Empty word slots are never focused: step past them (towards the first word when there's no direction). */
    private fun skipGap(dir: Int) {
        val row = allRows().getOrNull(selRow) ?: return
        if (row.getOrNull(selCol)?.type != KeyType.GAP) return
        val best = row.indexOfFirst { it.best }
        val d = if (dir != 0) dir else if (selCol < best) 1 else -1
        repeat(row.size) { if (row[selCol].type == KeyType.GAP) selCol = (selCol + d + row.size) % row.size }
    }

    private fun fadeStripIn() {
        stripAnim?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) { stripFade = 1f; return }
        stripAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Design.MOTION_FADE
            interpolator = easeOut
            addUpdateListener {
                stripFade = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---- Hold A for accents ------------------------------------------------------------
    /*
     * One thumb at a time, so A and B are never needed together:
     *  - Hold A on a letter: it's typed at once. Keep holding and its accents rise into the strip.
     *  - Moved with the D-pad while still holding? Let go of A = type the highlighted accent.
     *  - Let go without moving: the accents stay open. D-pad to choose, A types it, B cancels.
     *  - Any other button types what's showing, then does its usual job (the service handles that).
     */
    private class AccentState(val list: List<String>, val keyRect: RectF) {
        var progress = 0f          // 0..1: first half fades the strip out, second half fades the accents in
        var open = false
        var index = 0
        var sticky = false
        var moved = false
    }

    private var accents: AccentState? = null
    private var aHeld = false
    private var accentAnim: ValueAnimator? = null
    private val accentHold = Runnable { revealAccents() }

    fun isAccentsOpen() = accents?.open == true
    /** The accents are open and the highlight has moved along them since (the tour's card follows this). */
    fun accentsMoved() = accents?.let { it.open && it.moved } == true

    /** A pressed (the service calls this on the first A down). */
    fun pressA() {
        onDemoInput?.invoke("A", true)
        aHeld = true
        val a = accents
        if (a != null && a.open) {
            if (a.sticky) closeAccents(keep = true)
            return
        }
        if (a != null) resetAccents()
        clampFocus()
        val rows = allRows()
        val key = rows[selRow][selCol]
        val typed = press(key)
        if (typed == null || !settings.holdAccents || layer != Layer.LETTERS || key.type != KeyType.CHAR || typed.length != 1) return
        val list = Accents.variants(typed[0], settings.language) ?: return
        accents = AccentState(list, keyRect(selRow, rows[selRow], selCol))
        postDelayed(accentHold, ACCENT_HOLD_START_MS)
    }

    /** A released. */
    fun releaseA() {
        onDemoInput?.invoke("A", false)
        aHeld = false
        val a = accents ?: return
        if (a.open) {
            if (a.moved) closeAccents(keep = true) else {
                a.sticky = true
                invalidate()
            }
        } else fadeAccentsOut()
    }

    /** B while the accents are open: back to the plain letter. */
    fun cancelAccents() = closeAccents(keep = false)

    /** Another button while the accents are open: keep what's showing and carry on. */
    fun commitAccents() {
        if (isAccentsOpen()) closeAccents(keep = true)
    }

    /** Silently forget the accents (new text box, keyboard hidden, settings changed). */
    fun resetAccents() {
        removeCallbacks(accentHold)
        accentAnim?.cancel()
        accentAnim = null
        val wasShowing = accents?.let { it.open || it.progress > 0f } == true
        accents = null
        aHeld = false
        // The highlight was up in the accents: straight back onto its key, so nothing is left behind
        if (wasShowing) snapFocus()
    }

    /** The highlight jumps onto its key at once (no glide), and the keyboard redraws. */
    private fun snapFocus() {
        focusAnim?.cancel()
        if (!quiet && width > 0 && focusReady) { clampFocus(); focusRect.set(targetRect()) }
        invalidate()
    }

    /** For the checks: where the highlight is drawn, and where it should be. */
    internal fun focusRectForCheck() = RectF(focusRect)
    internal fun focusTargetForCheck() = targetRect()

    private fun revealAccents() {
        val a = accents ?: return
        if (!aHeld) return fadeAccentsOut()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // "Remove animations" is on: they simply appear
            a.progress = 1f
            openAccents()
            return
        }
        accentAnim?.cancel()
        accentAnim = ValueAnimator.ofFloat(a.progress, 1f).apply {
            duration = ACCENT_REVEAL_MS
            interpolator = Design.EASE_OUT
            addUpdateListener {
                a.progress = it.animatedValue as Float
                invalidate()
                if (a.progress >= 1f && !a.open) openAccents()
            }
            start()
        }
    }

    private fun openAccents() {
        val a = accents ?: return
        if (a.open) return
        if (listener?.onReplaceLast(a.list[0], a.list[1]) != true) return resetAccents().also { invalidate() }
        a.open = true
        a.index = 1
        feedback?.on()
        // Straight onto the accent: a glide from the held key up to the tray read as the keyboard navigating there
        // by itself (Odin test, 30 Oct 2026). Moving between accents still glides.
        animateFocus(snap = true)
    }

    private fun chooseAccent(index: Int) {
        val a = accents ?: return
        if (!a.open || index == a.index || index !in a.list.indices) return
        if (listener?.onReplaceLast(a.list[a.index], a.list[index]) != true) return resetAccents().also { animateFocus() }
        a.index = index
        if (aHeld) a.moved = true
        feedback?.move()
        animateFocus()
    }

    private fun closeAccents(keep: Boolean) {
        accents?.let { a -> if (keep && a.open && !quiet) truthCheck?.let { report ->
            if (!cellRect(a, a.index).contains(focusRect.centerX(), focusRect.centerY()))
                report("the accent \"${a.list.getOrNull(a.index)}\" was kept while the highlight showed somewhere else") } }
        val a = accents ?: return
        if (!a.open) return resetAccents()
        if (!keep && a.index != 0) listener?.onReplaceLast(a.list[a.index], a.list[0])
        if (keep) feedback?.key() else feedback?.back()
        a.open = false
        a.sticky = false
        pulse()
        fadeAccentsOut()
        animateFocus(snap = true)               // back on the key at once, as the tray fades
    }

    /** Fades the accents away and brings the strip back. */
    private fun fadeAccentsOut() {
        removeCallbacks(accentHold)
        val a = accents ?: return
        accentAnim?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled() || a.progress <= 0f) {
            accents = null
            invalidate()
            return
        }
        accentAnim = ValueAnimator.ofFloat(a.progress, 0f).apply {
            duration = Design.MOTION_FADE
            interpolator = Design.ease(arriving = false)
            addUpdateListener {
                a.progress = it.animatedValue as Float
                if (a.progress <= 0f && accents === a) accents = null
                invalidate()
            }
            start()
        }
    }

    /** Hand-over, never overlap: the strip fades out first, then the accents fade in. */
    private fun stripOut(a: AccentState?) = if (a == null) 0f else min(1f, a.progress * 2f)
    private fun accentAlpha(a: AccentState?) = if (a == null) 0f else max(0f, a.progress * 2f - 1f)

    /** One accent cell: in the tool strip, one letter column wide, the first accent straight above the held key. */
    private fun cellRect(a: AccentState, i: Int): RectF {
        val w = contentWidth() / settings.language.columns
        val total = a.list.size * w
        val left = (a.keyRect.centerX() - 1.5f * w).coerceIn(contentLeft(), max(contentLeft(), contentLeft() + contentWidth() - total))
        // Each accent is a key the full height of the tool strip's slot, so the tray keeps the same gap to the
        // keys as every other row (the "Keys" look, chosen 30 Oct 2026; cells were inset in an outlined tray before)
        val top = rowTop(0) + (1f - easeOut.getInterpolation(accentAlpha(a))) * u(5f)
        return RectF(left + i * w + gap / 2f, top, left + (i + 1) * w - gap / 2f, top + toolHeight)
    }

    /** The colour a key really shows (see-through keys sit on black). */
    private fun solid(c: Int): Int = if (Color.alpha(c) == 255) c else Contrast.over(c, if (clear) Color.BLACK else colBackdrop)

    /**
     * What a colour looks like at its lightest, for choosing ink on it. On the see-through theme the app
     * behind can be white, so words are measured against the key over white (the hardest case for white
     * ink); on the other themes it's simply the colour on the keyboard's own background.
     */
    private fun seen(c: Int): Int = if (!clear) solid(c) else Contrast.over(c, Contrast.over(colBackground, Color.WHITE))

    /** Accent cells are lifted one step towards white on dark themes so they stand out. Light keys are already white. */
    private fun lifted(c: Int): Int {
        val b = solid(c)
        return if (Contrast.inkFor(b) == Color.WHITE) Contrast.over(Contrast.withAlpha(Color.WHITE, Design.ALPHA_FAINT_STRONG), b) else b
    }

    /**
     * The accent layer ("Keys", chosen 30 Oct 2026): the keyboard dims; each accent is a key in the tool strip's
     * slot, the plain letter first and quieter; the held key keeps a soft ring in your colour, so the only lit
     * thing is the accent A will type. Solid themes dim towards their own background, Clear dims with black.
     */
    private fun drawAccentLayer(canvas: Canvas, rows: List<List<Key>>) {
        val a = accents ?: return
        val alpha = accentAlpha(a)
        if (alpha <= 0f) return
        val full = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val saved = canvas.saveLayerAlpha(full, (alpha * 255).roundToInt())
        labelTag = "tray"
        fillPaint.color = if (clear) Contrast.withAlpha(Color.BLACK, Design.SCRIM_ACCENTS_CLEAR) else Contrast.withAlpha(colBackdrop, Design.SCRIM_ACCENTS)
        if (tv) canvas.drawRoundRect(tvPanel(), u(20f), u(20f), fillPaint) else canvas.drawRect(full, fillPaint)
        // the held key: its own key, with a soft ring in your colour (the highlight belongs to the accent)
        fillPaint.color = solid(colKey)
        canvas.drawRoundRect(a.keyRect, corner, corner, fillPaint)
        // Your colour at full strength (a softened ring fell below 3 : 1 on dark themes, 30 Oct 2026): it stays the
        // quieter of the two by being a ring, where the accent you're on is filled
        ringPaint.color = heldRing(); ringPaint.strokeWidth = u(Design.STROKE_FOCUS)
        val hi = ringPaint.strokeWidth / 2f
        canvas.drawRoundRect(RectF(a.keyRect).apply { inset(hi, hi) }, corner - hi, corner - hi, ringPaint)
        drawLetter(canvas, a.list[0], a.keyRect.centerX(), a.keyRect.centerY(), Contrast.inkFor(solid(colKey)), keyW = a.keyRect.width())
        val cr = min(corner, toolHeight / 2f)
        a.list.indices.forEach { i ->
            val rc = cellRect(a, i)
            logRect(canvas, "cell", rc)
            fillPaint.color = if (i == 0) solid(colSpecialKey) else lifted(colKey)
            canvas.drawRoundRect(rc, cr, cr, fillPaint)
            if (colEdge != 0 && !clear) {
                ringPaint.color = colEdge; ringPaint.strokeWidth = u(Design.STROKE_HAIRLINE)
                canvas.drawRoundRect(RectF(rc).apply { inset(u(0.5f), u(0.5f)) }, cr, cr, ringPaint)
            }
        }
        // The highlight rides with its accent key while the tray rises (unless it's gliding between accents)
        if (a.open && focusAnim?.isRunning != true) focusRect.set(cellRect(a, a.index))
        if (a.open) drawHighlight(canvas, cr)
        a.list.forEachIndexed { i, ch ->
            val rc = cellRect(a, i)
            val focused = a.open && i == a.index
            val bg = if (focused) focusFill else if (i == 0) solid(colSpecialKey) else lifted(colKey)
            val ink = Contrast.inkFor(bg)
            drawLetter(canvas, ch, rc.centerX(), rc.centerY(), if (i == 0 && !focused) Contrast.withAlpha(ink, Design.ALPHA_INK_SOFT) else ink, Design.TYPE_KEY_TRAY)
        }
        if (a.open && settings.showHints) rows.forEachIndexed { r, row -> row.forEachIndexed { c, key ->
            if (key.type == KeyType.SPACE) drawChips(canvas, keyRect(r, row, c), solid(colBackdrop), accentChips(a)) } }
        labelTag = "label"
        canvas.restoreToCount(saved)
    }

    /**
     * The held letter's ring: your colour where it shows at 3 : 1 against both its key and the dimmed keyboard
     * around it, otherwise the key's own ink (on Clear over a bright app, some colours vanished into the grey).
     */
    private fun heldRing(): Int {
        val held = solid(colKey); val around = dimmedAroundTray()
        return listOf(colAccent, Contrast.inkFor(held)).firstOrNull { Contrast.ratio(it, held) >= 3.0 && Contrast.ratio(it, around) >= 3.0 }
            ?: listOf(colAccent, Contrast.inkFor(held)).maxByOrNull { min(Contrast.ratio(it, held), Contrast.ratio(it, around)) }!!
    }

    /** The keyboard under the accent layer's dim; on Clear the hardest case, a white app behind. */
    private fun dimmedAroundTray(): Int =
        if (clear) Contrast.over(Contrast.withAlpha(Color.BLACK, Design.SCRIM_ACCENTS_CLEAR), Color.WHITE)
        else Contrast.over(Contrast.withAlpha(colBackdrop, Design.SCRIM_ACCENTS), solid(colBackdrop))

    /**
     * Test hook: every colour pair the accent tray and number row show, as (what, ink, background), for the
     * contrast check. The held ring is measured against its key and against the dimmed keyboard around it.
     */
    internal fun trayColoursForTest(): List<Triple<String, Int, Int>> {
        val cell = lifted(colKey); val plain = solid(colSpecialKey)
        val dimmed = dimmedAroundTray()
        val held = solid(colKey)
        val ring = heldRing()
        val digit = seen(colKey)
        return listOf(
            Triple("accent letter", Contrast.inkFor(cell), cell),
            Triple("plain letter", Contrast.over(Contrast.withAlpha(Contrast.inkFor(plain), Design.ALPHA_INK_SOFT), plain), plain),
            Triple("chosen accent", Contrast.inkFor(focusFill), focusFill),
            Triple("held letter", Contrast.inkFor(held), held),
            Triple("held ring on its key", ring, held),
            Triple("held ring on the dimmed keyboard", ring, dimmed),
            Triple("digit", Contrast.over(Contrast.withAlpha(Contrast.inkFor(digit), Design.ALPHA_INK_STRONG), digit), digit),
        )
    }

    private fun accentChips(a: AccentState): List<List<HintPart>> = when {
        a.sticky -> listOf(
            listOf(HintPart.Badge(ControllerButton.A), HintPart.Text("Type")),
            listOf(HintPart.Badge(ControllerButton.B), HintPart.Text("Cancel"))
        )
        a.moved -> listOf(listOf(HintPart.Badge(ControllerButton.A), HintPart.Text("Let go to type")))
        else -> listOf(
            listOf(HintPart.Text("D-pad to choose")),
            listOf(HintPart.Badge(ControllerButton.A), HintPart.Text("Let go to keep it open"))
        )
    }

    /**
     * The optional number row ("Keys", chosen 30 Oct 2026): a slim key for every digit, in the letters' key colour
     * and shape, lined up with the letter columns. It was one bar with dividers, a look nothing else used.
     */
    private fun drawNumberBar(canvas: Canvas, rows: List<List<Key>>) {
        if (!hasNumberRow) return
        val row = rows[1]
        val r = numberKeyRadius
        for (i in row.indices) {
            val k = keyRect(1, row, i)
            fillPaint.color = colKey; canvas.drawRoundRect(k, r, r, fillPaint)
            if (colEdge != 0) {
                ringPaint.color = colEdge; ringPaint.strokeWidth = u(Design.STROKE_HAIRLINE)
                canvas.drawRoundRect(RectF(k).apply { inset(u(0.5f), u(0.5f)) }, r - u(0.5f), r - u(0.5f), ringPaint)
            }
        }
    }

    /** The digit keys' corners follow your key shape, never rounder than a pill. */
    private val numberKeyRadius get() = min(corner, numRowHeight / 2f)

    /** A hairline between the two plain word suggestions (the first one already sits on a key). */
    private fun drawWordDividers(canvas: Canvas, strip: List<Key>) {
        if (!showWords) return
        fillPaint.color = Contrast.withAlpha(Contrast.inkFor(colBackdrop), Design.ALPHA_FAINT_STRONG)
        val h = toolHeight * 0.4f
        for (i in 1 until strip.size) {
            if (strip[i].tool == Tool.WORD && strip[i - 1].tool == Tool.WORD && !strip[i].best && !strip[i - 1].best) {
                val x = keyRect(0, strip, i).left - gap / 2f
                val top = rowTop(0)
                canvas.drawRect(x - u(0.5f), top + (toolHeight - h) / 2f, x + u(0.5f), top + (toolHeight + h) / 2f, fillPaint)
            }
        }
    }

    // =====================================================================
    // TV MODE: a compact floating keyboard with a big line showing what you type
    // =====================================================================
    private var tv = false
    fun isTvMode(): Boolean = tv
    private var previewBefore = ""
    private var previewSelected = ""
    private var previewAfter = ""

    fun setTvMode(on: Boolean) {
        if (tv == on) return
        tv = on
        focusReady = false
        requestLayout()
        invalidate()
    }

    /** The text around the cursor, from the app's text box (dots for passwords). */
    fun setPreview(before: String, selected: String, after: String) {
        previewBefore = before
        previewSelected = selected
        previewAfter = after
        if (tv) invalidate()
    }

    /**
     * The preview line: big enough to read from the sofa, so you never have to find the
     * app's own (often small, far away or hidden) text box. The cursor and selection show too.
     */
    private fun drawPreview(canvas: Canvas) {
        val box = RectF(contentLeft(), contentTop, contentLeft() + contentWidth(), contentTop + previewHeight)
        fillPaint.color = Contrast.withAlpha(Contrast.inkFor(colBackdrop), Design.ALPHA_FAINT)
        canvas.drawRoundRect(box, u(10f), u(10f), fillPaint)
        val ink = Contrast.inkFor(colBackdrop)
        textPaint.textSize = u(TV_PREVIEW_TEXT_DP)
        applyFont(textPaint, 500)
        coverMissingGlyphs(textPaint, previewBefore + previewSelected + previewAfter)
        textPaint.textAlign = Paint.Align.LEFT
        val padX = u(12f)
        val room = box.width() - 2 * padX
        // Keep the cursor in view: trim the text before it from the left if needed
        var before = previewBefore
        val selW = textPaint.measureText(previewSelected)
        while (before.isNotEmpty() && textPaint.measureText(before) + selW > room * 0.8f) before = before.drop(1)
        val baseline = box.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        var x = box.left + padX
        val boxBg = Contrast.over(fillPaint.color, solid(colBackdrop))
        if (previewBefore.isEmpty() && previewSelected.isEmpty() && previewAfter.isEmpty()) {
            // Readable (4.5 : 1) and starting after the cursor, never under it (audit, 28 Oct 2026)
            textPaint.color = quietInk(ink, boxBg, 90)
            canvas.drawText(placeholder, x + u(6f), baseline, textPaint)
        }
        textPaint.color = ink
        canvas.drawText(before, x, baseline, textPaint)
        x += textPaint.measureText(before)
        if (previewSelected.isNotEmpty()) {
            fillPaint.color = Contrast.withAlpha(colAccent, Design.ALPHA_TINT_GLOW)
            canvas.drawRoundRect(x - u(1f), box.top + u(7f), x + selW + u(1f), box.bottom - u(7f), u(4f), u(4f), fillPaint)
            canvas.drawText(previewSelected, x, baseline, textPaint)
            x += selW
        } else {
            fillPaint.color = colAccent
            canvas.drawRect(x + u(1f), box.top + u(8f), x + u(3f), box.bottom - u(8f), fillPaint)
            x += u(4f)
        }
        // NEW: the rest of the first suggested word, faint, right after the cursor (only at the end of the text)
        val best = bestWord()
        if (best != null && previewSelected.isEmpty() && previewAfter.isBlank() && accents == null &&
            wordPrefix.isNotEmpty() && best.length > wordPrefix.length && best.startsWith(wordPrefix, ignoreCase = true)) {
            textPaint.color = quietInk(ink, boxBg, 90)
            canvas.drawText(best.substring(wordPrefix.length), x - u(3f), baseline, textPaint)
        }
        textPaint.color = ink              // text after the cursor is ordinary text, as in the tour's text box
        canvas.save()
        canvas.clipRect(box.left, box.top, box.right - padX, box.bottom)
        canvas.drawText(previewAfter, x, baseline, textPaint)
        canvas.restore()
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** After a quick language switch the name shows on the space bar for a moment, even when hidden. */
    private var languageFlashUntil = 0L

    /** Called after RT + LT changed the language. */
    fun flashLanguage() {
        languageFlashUntil = SystemClock.uptimeMillis() + LANGUAGE_FLASH_MS
        invalidate()
        postDelayed({ invalidate() }, LANGUAGE_FLASH_MS - Design.MOTION_FADE)   // the fade then drives itself
        postDelayed({ invalidate() }, LANGUAGE_FLASH_MS + 20)
    }

    /** The kind of text box in use (set by the keyboard service for every new box). */
    private var field = FieldKind.TEXT
    private val numeric get() = settings.smartKeys && (field == FieldKind.NUMBER || field == FieldKind.PHONE)

    /**
     * A new text box: smart keys follow its kind, and number boxes open on the number pad.
     */
    fun setField(kind: FieldKind) {
        field = kind
        resetAccents()
        words = emptyList()
        letterRows = Layouts.letterRows(settings.layout, settings.language, if (settings.smartKeys) field else FieldKind.TEXT)
        layer = if (numeric) Layer.NUMPAD else Layer.LETTERS
        selRow = homeRow                                    // middle of the keys
        selCol = if (layer == Layer.NUMPAD) 1 else 4
        preferredCentre = null
        focusReady = false
        clampFocus()
        invalidate()
    }

    private fun rowWidth(row: List<Key>) = row.fold(0f) { sum, k -> sum + k.weight }

    private fun centreOf(row: List<Key>, col: Int): Float {
        var x = (Layouts.UNITS - rowWidth(row)) / 2f
        for (i in 0 until col) x += row[i].weight
        return x + row[col].weight / 2f
    }

    /** The key under [centre]; on an exact tie, the one to the left. */
    private fun nearestCol(row: List<Key>, centre: Float): Int {
        var x = (Layouts.UNITS - rowWidth(row)) / 2f
        row.forEachIndexed { i, key ->
            if (centre < x + key.weight - 0.001f) return i
            x += key.weight
        }
        return row.lastIndex
    }

    private fun clampFocus() {
        val rows = allRows()
        selRow = selRow.coerceIn(0, rows.lastIndex)
        selCol = selCol.coerceIn(0, rows[selRow].lastIndex)
    }

    private val capitals get() = layer == Layer.LETTERS && (shiftState != ShiftState.OFF || heldShift)

    /** Capitals for single letters only: ".com" stays lowercase, like web addresses are. */
    private fun displayLabel(key: Key): String =
        if (capitals && key.label.length == 1) settings.language.upper(key.label) else key.label

    // =====================================================================
    // ANIMATION
    // =====================================================================
    private val focusRect = RectF()
    private var focusReady = false

    /**
     * Keys to point at (the tour shows which key a button row is about: X on Delete, Y on the space bar):
     * every key whose corner badge is one of these buttons gets a soft tint, fading in and out.
     */
    var spotlight: Set<ControllerButton> = emptySet()
        set(v) {
            if (field == v) return
            val from = if (v.isEmpty()) spotFade else 0f
            if (v.isNotEmpty()) field = v
            spotAnim?.cancel()
            if (!ValueAnimator.areAnimatorsEnabled()) { spotFade = if (v.isEmpty()) 0f else 1f; if (v.isEmpty()) field = v; invalidate(); return }
            spotAnim = ValueAnimator.ofFloat(from, if (v.isEmpty()) 0f else 1f).apply {
                duration = Design.MOTION_FADE; interpolator = Design.ease(arriving = v.isNotEmpty())
                addUpdateListener { spotFade = it.animatedValue as Float; invalidate() }
                if (v.isEmpty()) addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) { if (spotFade <= 0f) field = emptySet() }
                })
                start()
            }
        }
    private var spotFade = 0f
    private var spotAnim: ValueAnimator? = null

    /** Test hook: where the highlight ("pill") and each corner badge ("badge") were drawn. Off (null) in the app. */
    internal var drawLog: MutableList<Pair<String, RectF>>? = null
    /** Test hook: moves the highlight straight to [row], [col] (no animation) and returns that key's rectangle. */
    internal fun focusForTest(row: Int, col: Int): RectF? {
        val rows = allRows()
        val r = rows.getOrNull(row) ?: return null
        if (col !in r.indices || r[col].type == KeyType.GAP || r[col].type == KeyType.EMPTY) return null
        selRow = row; selCol = col; focusReady = false
        return keyRect(row, r, col)
    }
    /** Logs [r] as it really lands on screen, through any scaling the canvas has at that moment. */
    private fun logRect(canvas: Canvas, tag: String, r: RectF) {
        val log = drawLog ?: return
        val m = android.graphics.Matrix()
        @Suppress("DEPRECATION") canvas.getMatrix(m)
        log += tag to RectF(r).also { m.mapRect(it) }
    }
    /** Test hook: where a word or letter's ink landed, tagged "label:" + the text. */
    private fun logText(canvas: Canvas, text: String, x: Float, y: Float) {
        if (drawLog == null || text.isBlank()) return
        val path = Path(); textPaint.getTextPath(text, 0, text.length, x, y, path)
        val r = RectF(); path.computeBounds(r, true)
        logRect(canvas, "$labelTag:$text", r)
    }
    /** "label" for the keyboard's own words; "tray" while the accent layer draws over it, so checks tell them apart. */
    private var labelTag = "label"
    /** Test hook: every key on screen now, with what it is. */
    internal fun keysForTest(): List<Pair<String, RectF>> = allRows().flatMapIndexed { r, row ->
        row.indices.filter { row[it].type != KeyType.GAP && row[it].type != KeyType.EMPTY }.map { c -> "${row[c].type}:${row[c].label}" to keyRect(r, row, c) }
    }
    internal fun rowCountForTest() = allRows().size
    internal fun rowSizeForTest(row: Int) = allRows()[row].size
    internal val gapForTest get() = gap
    private var focusAnim: ValueAnimator? = null
    private var pressScale = 1f
    private var pulseAnim: ValueAnimator? = null
    private var comboFade = 0f             // 0 = tool strip, 1 = RT combo chips
    private var comboAnim: ValueAnimator? = null

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private companion object {
        /** The least time a word suggestion is on show before the tour takes it (a reader sees where it came from). */
        const val WORD_SEEN_MS = 600L
        const val LANGUAGE_FLASH_MS = 1400L
        // TV mode (Android TV design guide: ~3 m viewing distance, 5 % safe area)
        const val TV_WIDTH_DP = 600f          // compact keyboard, centred
        const val TV_SAFE_SIDE = 0.05f
        const val TV_SAFE_BOTTOM = 0.05f
        const val TV_SCREEN_SHARE = 0.55f     // leave the game visible
        const val TV_PREVIEW_DP = 34f
        const val TV_PREVIEW_TEXT_DP = 18f
        const val TOOL_MIN_FIT = 0.7f        // the top row's shared word size never goes below this
        const val LABEL_MIN_FIT = 0.85f      // a key's word shrinks at most this far to keep clear of its badge
        const val EDGE_DP = 6f               // the keyboard's own margin round its keys
        const val TV_PANEL_PAD = 10f          // the floating TV panel's padding round the keys, on every side
        const val LEAD_AHEAD = 1.3f
        const val LIFT_SCALE = 1.07f
        /** Space above and below the accent cells inside their tray. */
        /** BadgePainter's circle radius at scale 1. */
        const val BADGE_RADIUS_DP = 6.5f
        /** Height of the keyboard at size 1, in dp (edges, tool strip, four key rows, gaps). */
        const val DESIGN_HEIGHT_DP = 178f
        /** The keyboard never takes more than this share of the screen height. */
        const val MAX_SCREEN_SHARE = 0.6f
        const val PREVIEW_MIN_FIT = 0.75f
        /** The tour keyboard's width : height (without the number row). Wider than a phone's, flatter than the real one on a handheld. */
        const val PREVIEW_ASPECT = 3.1f
        const val PREVIEW_FLOOR_FIT = 0.4f      // the smallest a demo keyboard gets when its height is limited (it always fits)
        /** The number row is as slim as the tool strip, plus one gap. */
        const val NUM_ROW_DP = 26f
        const val NUM_ROW_GAP_DP = 6f
        /** Hold A: nothing shows for this long, so quick taps never flash the accents... */
        /** What a clip waiting to be removed says in its place. */
        const val REMOVED_LABEL = "Removed"
        /** The smallest a Shift, Delete or Enter icon gets to keep its badge; any smaller and the badge steps aside. */
        const val ICON_MIN_SCALE = 0.85f
        /** The least room between a letter and its key's side. */
        const val LETTER_SIDE_DP = 2f
        const val ACCENT_HOLD_START_MS = Design.MOTION_REVEAL_WAIT
        /** ...then the strip hands over to the accents over this long. */
        const val ACCENT_REVEAL_MS = Design.MOTION_REVEAL
    }

    private var lastMoveAt = 0L
    private val easeOut = Design.EASE_OUT

    /**
     * Glides the highlight to the selected key.
     *  - Instant: jumps.
     *  - Subtle: one smooth glide that settles softly.
     *  - Fluid: the leading edge arrives a touch before the trailing edge, so the
     *    highlight stretches very slightly in the direction of travel, then settles.
     * While a direction is held, each glide gets shorter so the highlight never lags
     * behind the repeats. A wrap from one edge to the other snaps instead of sweeping.
     */
    private fun animateFocus(wrapped: Boolean = false, snap: Boolean = false) {
        if (quiet) return                  // a dry run: nothing moves on screen
        clampFocus()
        if (width == 0 || !focusReady) {
            invalidate()
            return
        }
        val to = targetRect()
        val from = RectF(focusRect)
        focusAnim?.cancel()

        val now = SystemClock.uptimeMillis()
        val sinceLast = now - lastMoveAt
        lastMoveAt = now

        if (settings.motion == HighlightMotion.INSTANT || wrapped || snap) {
            focusRect.set(to)
            if (wrapped && settings.motion != HighlightMotion.INSTANT) pulse(from = Design.PRESS_SQUEEZE_WRAP)
            invalidate()
            return
        }

        val fluid = settings.motion == HighlightMotion.FLUID
        val movingRight = to.centerX() > from.centerX()
        val movingDown = to.centerY() > from.centerY()
        // Speed: the very first frame already shows the highlight most of the way at the
        // new key, so the eye never waits for it. The glide only polishes the last bit.
        from.set(
            lerp(from.left, to.left, Design.MOTION_FOCUS_JUMP),
            lerp(from.top, to.top, Design.MOTION_FOCUS_JUMP),
            lerp(from.right, to.right, Design.MOTION_FOCUS_JUMP),
            lerp(from.bottom, to.bottom, Design.MOTION_FOCUS_JUMP)
        )
        focusRect.set(from)
        invalidate()
        focusAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = sinceLast.coerceIn(Design.MOTION_FOCUS_MIN, Design.MOTION_FOCUS)
            interpolator = null                       // edges are eased individually below
            addUpdateListener {
                val t = it.animatedValue as Float
                val base = easeOut.getInterpolation(t)
                // Fluid: the leading edge runs ~30 % ahead in time, then both settle together
                val lead = if (fluid) easeOut.getInterpolation(minOf(1f, t * LEAD_AHEAD)) else base
                focusRect.set(
                    lerp(from.left, to.left, if (movingRight) base else lead),
                    lerp(from.top, to.top, if (movingDown) base else lead),
                    lerp(from.right, to.right, if (movingRight) lead else base),
                    lerp(from.bottom, to.bottom, if (movingDown) lead else base)
                )
                invalidate()
            }
            start()
        }
    }

    private fun pulse(from: Float = Design.PRESS_SQUEEZE) {
        if (quiet) return
        pulseAnim?.cancel()
        pulseAnim = ValueAnimator.ofFloat(from, 1f).apply {
            duration = Design.MOTION_PRESS
            interpolator = easeOut
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun fadeCombo(show: Boolean) {
        comboAnim?.cancel()
        comboAnim = ValueAnimator.ofFloat(comboFade, if (show) 1f else 0f).apply {
            duration = Design.MOTION_FADE
            interpolator = Design.ease(arriving = show)
            addUpdateListener {
                comboFade = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // =====================================================================
    // CALLED BY THE KEYBOARD SERVICE
    // =====================================================================
    /**
     * Runs [block] (D-pad steps for the tour's demos) without anything showing: the highlight is put
     * back where it was afterwards, and nothing lights, sounds or animates. The tour uses it to work
     * out how long a demo's run takes before playing it.
     */
    fun <T> dryRun(block: () -> T): T {
        if (quiet) return block()          // already inside one: carry on from where it is
        val row = selRow; val col = selCol; val centre = preferredCentre
        val was = Triple(layer, layerBeforeClips, shiftState)
        val sel = selecting to selectedCount
        val (fb, ls, demoIn) = Triple(feedback, listener, onDemoInput)
        // Presses are walked through too (a ?123 press changes where the next key is), silently
        feedback = null; listener = null; onDemoInput = null
        quiet = true
        try { return block() } finally {
            selRow = row; selCol = col; preferredCentre = centre
            layer = was.first; layerBeforeClips = was.second; shiftState = was.third
            selecting = sel.first; selectedCount = sel.second
            feedback = fb; listener = ls; onDemoInput = demoIn
            quiet = false
        }
    }

    /** One D-pad step towards the first key of [type] (Shift, ?123). True when it's there, null if there's none. */
    fun stepToKey(type: KeyType, wrap: Boolean = false): Boolean? {
        val rows = allRows()
        val r = rows.indexOfFirst { row -> row.any { it.type == type } }
        if (r < 0) return null
        val c = rows[r].indexOfFirst { it.type == type }
        clampFocus()
        if (selRow != r) { moveFocus(0, if (r > selRow) 1 else -1); return false }
        if (selCol != c) { moveFocus(sideways(rows[r].size, c, wrap), 0); return false }
        return true
    }
    private var quiet = false

    fun moveFocus(dx: Int, dy: Int) {
        if (quiet) {                       // a dry run: the same move, nothing shown
            moveFocusQuietly(dx, dy)
            return
        }
        demoMove(dx, dy)
        val a = accents
        if (a != null && a.open) {
            // Accents open: left and right choose, up and down do nothing
            if (dx != 0) chooseAccent((a.index + dx).coerceIn(0, a.list.lastIndex))
            return
        }
        if (a != null) resetAccents().also { invalidate() }     // moving away before they opened
        val rows = allRows()
        var wrapped = false
        if (dx != 0) {
            val size = rows[selRow].size
            val next = selCol + dx
            wrapped = next < 0 || next >= size
            selCol = (next + size) % size
            preferredCentre = null
            skipGap(dx)
        } else if (dy != 0) {
            val target = preferredCentre ?: centreOf(rows[selRow], selCol)
            preferredCentre = target
            val next = selRow + dy
            wrapped = next < 0 || next >= rows.size
            selRow = (next + rows.size) % rows.size
            selCol = nearestCol(rows[selRow], target)
            skipGap(0)
        }
        feedback?.move()
        animateFocus(wrapped)
    }

    private fun moveFocusQuietly(dx: Int, dy: Int) {
        val rows = allRows()
        if (dx != 0) {
            val size = rows[selRow].size
            selCol = (selCol + dx + size) % size
            preferredCentre = null
            skipGap(dx)
        } else if (dy != 0) {
            val target = preferredCentre ?: centreOf(rows[selRow], selCol)
            preferredCentre = target
            selRow = (selRow + dy + rows.size) % rows.size
            selCol = nearestCol(rows[selRow], target)
            skipGap(0)
        }
    }

    /** Where the highlight should be: on a key, or on an accent while they're open. */
    private fun targetRect(): RectF {
        val a = accents
        if (a != null && a.open) return cellRect(a, a.index)
        clampFocus()
        val rows = allRows()
        return keyRect(selRow, rows[selRow], selCol)
    }

    /**
     * One D-pad step towards the key with this label (" " = the space bar) on the current layer.
     * Returns true when the highlight is already on it, null if the key isn't on this layer.
     * Used by the tour to type real words.
     */
    fun stepTowards(label: String, wrap: Boolean = false): Boolean? {
        val rows = allRows()
        var targetRow = -1
        var targetCol = -1
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, key ->
                val hit = if (label == " ") key.type == KeyType.SPACE
                else (key.type == KeyType.CHAR || key.type == KeyType.DIGIT) && displayLabel(key).equals(label, ignoreCase = true)
                if (hit && targetRow < 0) { targetRow = r; targetCol = c }
            }
        }
        if (targetRow < 0) return null
        clampFocus()
        if (selRow != targetRow) { moveFocus(0, if (targetRow > selRow) 1 else -1); return false }
        if (selCol != targetCol) { moveFocus(sideways(rows[selRow].size, targetCol, wrap), 0); return false }
        return true
    }

    /** Left or right towards [targetCol]; with [wrap], whichever way is shorter going round the row's edge. */
    private fun sideways(size: Int, targetCol: Int, wrap: Boolean): Int {
        if (!wrap) return if (targetCol > selCol) 1 else -1
        val right = (targetCol - selCol + size) % size
        return if (right <= size - right) 1 else -1
    }

    /** Demos: how many D-pad steps [stepTowards] will take to reach [label] from here (0 = already on it). */
    internal fun stepsTo(label: String, wrap: Boolean = false): Int = countSteps { stepTowards(label, wrap) }

    /** Demos: how many D-pad steps to the first key of [type] (the Delete key, say). */
    internal fun stepsToKey(type: KeyType, wrap: Boolean = false): Int = countSteps { stepToKey(type, wrap) }

    private fun countSteps(step: () -> Boolean?): Int {
        // a dry run: nothing drawn, no device input, and everything put back
        val r0 = selRow; val c0 = selCol; val p0 = preferredCentre; val q0 = quiet; var n = 0
        quiet = true
        try { while (n < 60 && step() == false) n++ } finally { quiet = q0; selRow = r0; selCol = c0; preferredCentre = p0 }
        return n
    }

    /** One D-pad step towards a tool in the top row (Copy, the clipboard clock…). True when it's there. */
    fun stepToTool(tool: Tool): Boolean? {
        val rows = allRows()
        val r = rows.indexOfFirst { row -> row.any { it.type == KeyType.TOOL && it.tool == tool } }
        if (r < 0) return null
        val c = rows[r].indexOfFirst { it.type == KeyType.TOOL && it.tool == tool }
        clampFocus()
        if (selRow != r) { moveFocus(0, if (r > selRow) 1 else -1); return false }
        if (selCol != c) { moveFocus(if (c > selCol) 1 else -1, 0); return false }
        return true
    }

    /**
     * Demos: a face button's shortcut (X Delete, Y Space, Start Enter). The device lights [button] and the key
     * acts, while the highlight stays where it is. [lit] false: a held button repeating, without lighting again.
     */
    fun pressShortcut(button: String, type: KeyType, lit: Boolean = true) {
        if (lit) onDemoInput?.invoke(button, null)
        when (type) {
            KeyType.DELETE -> { feedback?.delete(); listener?.onDelete() }
            KeyType.SPACE -> { feedback?.space(); listener?.onText(" ") }
            KeyType.ENTER -> { feedback?.confirm(); listener?.onEnter() }
            else -> Unit
        }
    }

    /** Demos: [button] held down or let go, as the device picture shows it. */
    fun holdButton(button: String, down: Boolean) { onDemoInput?.invoke(button, down) }

    fun pressSelected() {
        onDemoInput?.invoke("A", null)
        clampFocus()
        if (!quiet) truthCheck?.let { report ->
            val rows = allRows()
            val key = rows[selRow][selCol]
            val what = if (key.tool == Tool.WORD) "the word \"${key.label}\"" else "\"${displayLabel(key)}\""
            // (a highlight waiting to be placed is drawn on its key in the very next frame)
            if (focusReady && !keyRect(selRow, rows[selRow], selCol).contains(focusRect.centerX(), focusRect.centerY()))
                report("$what was pressed while the highlight showed somewhere else")
            if (key.tool == Tool.WORD) checkWordSeen(key.label)
        }
        press(allRows()[selRow][selCol])
    }

    /** R2 tap: off -> next letter only -> caps lock -> off. */
    /** RT, or the Shift key itself ([fromKey]: that's the key being pressed, not RT). */
    fun cycleShift(fromKey: Boolean = false) {
        if (!fromKey) onDemoInput?.invoke("RT", null)
        if (layer != Layer.LETTERS) return
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ONCE.also { feedback?.on() }
            ShiftState.ONCE -> ShiftState.LOCK.also { feedback?.lock() }
            ShiftState.LOCK -> ShiftState.OFF.also { feedback?.off() }
        }
        invalidate()
    }

    /** Back to lowercase, hints gone at once (used by the tour between pages). */
    fun resetShift() {
        shiftState = ShiftState.OFF
        heldShift = false
        comboAnim?.cancel()
        comboFade = 0f
        invalidate()
    }

    /** R2 held: capitals on the keys, and the RT combos fade in over the tool strip. */
    fun setHeldShift(on: Boolean) {
        if (heldShift == on) return
        onDemoInput?.invoke("RT", on)
        heldShift = on
        if (settings.comboHints) fadeCombo(on) else comboFade = 0f
        invalidate()
    }

    /** Demos: back to the letters, without a sound. */
    fun resetLayer() {
        if (layer == Layer.LETTERS) return
        layer = Layer.LETTERS
        clampFocus()
        invalidate()
    }

    /** Demos: the highlight onto the first key of [type] (Shift, say), which has no letter to look for. */
    fun focusKey(type: KeyType) {
        allRows().forEachIndexed { r, row ->
            val c = row.indexOfFirst { it.type == type }
            if (c >= 0) { selRow = r; selCol = c; preferredCentre = null; animateFocus(); return }
        }
    }

    /** LT, or the ?123 key itself ([fromKey]: that's the key being pressed, not LT). */
    fun toggleLayer(fromKey: Boolean = false) {
        if (!fromKey) onDemoInput?.invoke("LT", null)
        val centre = centreOf(allRows()[selRow], selCol)
        layer = when (layer) {
            // Text boxes: letters <-> symbols. Number boxes go round all three:
            // number pad -> letters -> symbols -> number pad, so every character is reachable.
            Layer.LETTERS -> Layer.SYMBOLS.also { feedback?.on() }
            Layer.SYMBOLS -> (if (numeric) Layer.NUMPAD else Layer.LETTERS).also { feedback?.off() }
            Layer.NUMPAD -> Layer.LETTERS.also { feedback?.on() }
            Layer.CLIPS -> Layer.LETTERS.also { feedback?.off() }
        }
        clampFocus()
        selCol = nearestCol(allRows()[selRow], centre)
        preferredCentre = null
        animateFocus()
    }

    fun isClipsOpen() = layer == Layer.CLIPS

    fun toggleClips() {
        if (layer == Layer.CLIPS) {
            closeClips(focusOnHistoryButton = true)
            feedback?.off()
        } else {
            openClips()
        }
    }

    private fun openClips() {
        layerBeforeClips = layer
        layer = Layer.CLIPS
        selRow = 1
        selCol = 0
        preferredCentre = null
        feedback?.on()
        animateFocus()
    }

    fun closeClips(focusOnHistoryButton: Boolean) {
        if (layer != Layer.CLIPS) return
        onClipsClosed?.invoke()
        layer = layerBeforeClips
        if (focusOnHistoryButton) {
            selRow = 0
            selCol = toolRow.indexOfFirst { it.tool == Tool.HISTORY }
        } else {
            selRow = homeRow
            selCol = 4
        }
        preferredCentre = null
        animateFocus()
    }

    fun focusedClipIndex(): Int? {
        if (layer != Layer.CLIPS) return null
        clampFocus()
        val key = allRows()[selRow][selCol]
        return if (key.type == KeyType.CLIP) key.index else null
    }

    /** The clip shown as a "Removed" slot, waiting a moment so X can bring it back (see [ClipRemoval]). */
    var removedClip: Int? = null
        set(v) { field = v; invalidate() }

    /** Told when the clipboard closes, whatever closed it (a pending removal goes through then). */
    var onClipsClosed: (() -> Unit)? = null

    fun setClips(list: List<String>) {
        clips = list.toList()
        if (layer == Layer.CLIPS) clampFocus()
        invalidate()
    }

    fun setAutoShift(shouldCapitalise: Boolean) {
        if (shiftState == ShiftState.LOCK) return
        val target = if (shouldCapitalise) ShiftState.ONCE else ShiftState.OFF
        if (shiftState != target) {
            shiftState = target
            invalidate()
        }
    }

    fun setSelecting(on: Boolean, count: Int) {
        val before = showWords
        keepStripFocus {
            selecting = on
            selectedCount = count
        }
        if (before != showWords) fadeStripIn()
        invalidate()
    }

    fun setClipPreview(text: String?) {
        clipPreview = text
            ?.replace('\n', ' ')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.length > 16) it.take(16) + "…" else it }
        invalidate()
    }

    /** Presses a key. Returns the text it typed (letters, symbols and digits), otherwise null. */
    private fun press(key: Key): String? {
        if (key.type == KeyType.GAP) return null
        pulse()
        when (key.type) {
            KeyType.CHAR, KeyType.DIGIT -> {
                feedback?.key()
                val text = displayLabel(key)
                if (!heldShift && layer == Layer.LETTERS && shiftState == ShiftState.ONCE && key.type == KeyType.CHAR) {
                    shiftState = ShiftState.OFF
                }
                invalidate()
                listener?.onText(text)
                return text
            }
            KeyType.SPACE -> {
                feedback?.space()
                listener?.onText(" ")
            }
            KeyType.ENTER -> {
                feedback?.confirm()
                listener?.onEnter()
            }
            KeyType.DELETE -> {
                feedback?.delete()
                listener?.onDelete()
            }
            KeyType.SHIFT -> cycleShift(fromKey = true)
            KeyType.LAYER -> toggleLayer(fromKey = true)
            KeyType.TOOL -> when (key.tool) {
                Tool.HISTORY -> toggleClips()
                Tool.PASTE -> {
                    feedback?.confirm()
                    listener?.onTool(Tool.PASTE)
                }
                Tool.CUT, Tool.COPY -> {
                    feedback?.key()
                    listener?.onTool(key.tool)
                }
                Tool.WORD -> {
                    feedback?.space()
                    listener?.onWord(key.label)
                }
                // Select, select all and settings play their own sounds
                else -> key.tool?.let { listener?.onTool(it) }
            }
            KeyType.CLIP -> {
                if (key.index == removedClip) return null      // a removed slot pastes nothing: X brings it back
                feedback?.confirm()
                listener?.onClip(key.index)
                closeClips(focusOnHistoryButton = false)
            }
            KeyType.EMPTY, KeyType.GAP -> Unit
        }
        return null
    }

    // =====================================================================
    // SIZE AND POSITIONS
    // =====================================================================
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // Height at the chosen size, before any shrinking: 6+26+4+4×31+3×4+6 = 178 dp
        // (TV mode adds the preview line and keeps a safe margin at the bottom of the screen)
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        val tvExtraDp = if (tv) TV_PREVIEW_DP + Design.SPACE_XS + 2 * (TV_PANEL_PAD + 1f - EDGE_DP) else 0f
        val numberRowDp = if (settings.numberRow) NUM_ROW_DP + NUM_ROW_GAP_DP else 0f
        val designHeight = (DESIGN_HEIGHT_DP + tvExtraDp + numberRowDp) * dp * sizeScales[sizeLevel]
        val share = if (tv) TV_SCREEN_SHARE else MAX_SCREEN_SHARE
        var newFit = min(1f, (screenHeight * share) / designHeight)
        if (keepProportions && !tv && previewFit > 0f) {
            newFit = previewFit
        } else if (keepProportions && !tv) {
            // A preview narrower than the real keyboard: shrink the height to match, so the
            // keys keep their real shape and the hints their real size relative to the keys
            val realWidth = min(resources.displayMetrics.widthPixels.toFloat(), maxWidth)
            val given = MeasureSpec.getSize(widthSpec).toFloat()
            // ...but never below 3/4, so letters and hints stay readable in a small preview
            if (given > 0f && given < realWidth) newFit = min(newFit, max(PREVIEW_MIN_FIT, given / realWidth))
            // ...and never taller than the space it's given (the tour's stage)
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                val room = MeasureSpec.getSize(heightSpec).toFloat()
                if (room > 0f) newFit = min(newFit, max(PREVIEW_FLOOR_FIT, room / designHeight))
            }
        }
        if (newFit != fit) {
            fit = newFit
            badges.scale = scale
            focusReady = false
        }
        val h = bodyTop + bodyHeight + (if (tv) u(TV_PANEL_PAD) + u(1f) else edge) + tvBottomMargin()
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), h.roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        focusReady = false
    }

    private fun contentWidth() =
        if (tv) min(width * (1f - 2 * TV_SAFE_SIDE), u(TV_WIDTH_DP))      // compact: shorter D-pad trips
        else min(width - 2 * edge, maxWidth)
    private fun contentLeft() = (width - contentWidth()) / 2f

    /** Top of the keys area: in TV mode the preview line sits above the tool strip. */
    private val keysTop get() = contentTop + if (tv) previewHeight + gap else 0f
    /** Where the content starts. In TV mode the floating panel's padding and its edge sit above it, inside the view. */
    private val contentTop get() = if (tv) u(TV_PANEL_PAD) + u(1f) else edge
    private val previewHeight get() = u(TV_PREVIEW_DP)

    /** TV mode keeps clear of the screen's bottom edge (about 5 %, Android TV's safe area). */
    private fun tvBottomMargin() = if (tv) resources.displayMetrics.heightPixels * TV_SAFE_BOTTOM else 0f

    /*
     * Rows: the tool strip, the optional number row, then four equal slots. The keyboard is the
     * same height on every page (letters, symbols, number pad, clipboard), so it never jumps when
     * you switch. Pages without the number row (number pad, clipboard) get slightly taller keys.
     */
    private val numRowHeight get() = u(NUM_ROW_DP)
    /** A little more air under the number row than between key rows, so it reads as its own shelf. */
    private val numRowGap get() = u(NUM_ROW_GAP_DP)
    private val bodyTop get() = keysTop + toolHeight + gap
    private val bodyHeight get() = (if (settings.numberRow) numRowHeight + numRowGap else 0f) + 4 * keyHeight + 3 * gap
    private val numberRowSpace get() = if (hasNumberRow) numRowHeight + numRowGap else 0f
    private val slotHeight get() = (bodyHeight - numberRowSpace - 3 * gap) / 4f

    private fun rowTop(r: Int): Float = when {
        r == 0 -> keysTop
        hasNumberRow && r == 1 -> bodyTop
        else -> bodyTop + numberRowSpace + (r - if (hasNumberRow) 2 else 1) * (slotHeight + gap)
    }

    private fun rowHeight(r: Int) = when {
        r == 0 -> toolHeight
        hasNumberRow && r == 1 -> numRowHeight
        else -> slotHeight
    }

    /** The bottom slot, wherever the rows above end (the clipboard's button guide sits there). */
    private fun lastSlotTop() = bodyTop + bodyHeight - slotHeight

    /** For the checks: the floating TV panel, which must sit wholly inside the view. */
    internal fun tvPanelForTest() = tvPanel()

    private fun tvPanel(): RectF {
        // Even padding, so the nested corners stay parallel; the whole panel and its edge stay inside the view
        val pad = u(TV_PANEL_PAD)
        return RectF(contentLeft() - pad, contentTop - pad, contentLeft() + contentWidth() + pad, bodyTop + bodyHeight + pad)
    }

    private fun keyRect(r: Int, row: List<Key>, col: Int): RectF {
        val unit = contentWidth() / Layouts.UNITS
        var x = contentLeft() + (Layouts.UNITS - rowWidth(row)) / 2f * unit
        for (i in 0 until col) x += row[i].weight * unit
        val top = rowTop(r)
        return RectF(x + gap / 2f, top, x + row[col].weight * unit - gap / 2f, top + rowHeight(r))
    }

    private fun stripRect() = RectF(contentLeft(), rowTop(0), contentLeft() + contentWidth(), rowTop(0) + toolHeight)

    // =====================================================================
    // DRAWING
    // =====================================================================
    override fun onDraw(canvas: Canvas) {
        if (tv) {
            // TV mode: a floating panel; the app or game stays visible around it
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.SRC)
            val panel = tvPanel()
            fillPaint.color = if (clear) colBackground else Contrast.withAlpha(colBackground, Design.PANEL_TV)
            canvas.drawRoundRect(panel, u(20f), u(20f), fillPaint)
            ringPaint.color = Contrast.withAlpha(Contrast.inkFor(colBackdrop), Design.ALPHA_FAINT_STRONG)
            ringPaint.strokeWidth = u(Design.STROKE_HAIRLINE)
            canvas.drawRoundRect(panel, u(20f), u(20f), ringPaint)
            drawPreview(canvas)
        } else {
            // SRC replaces what's there, so a see-through background really is see-through
            canvas.drawColor(colBackground, android.graphics.PorterDuff.Mode.SRC)
        }
        clampFocus()
        val rows = allRows()
        if (!focusReady) {
            focusRect.set(targetRect())
            focusReady = true
        }
        val a = accents
        // The tool strip steps aside for the RT chips and for the accents, and fades in after a change
        val stripAlpha = (1f - max(comboFade, stripOut(a))).coerceIn(0f, 1f) * stripFade

        // 0. The number row's bar
        drawNumberBar(canvas, rows)
        // 0b. See-through: the tool strip gets a plate like the special keys, so its words and icons read on any app behind
        if (clear && stripAlpha > 0f) {
            fillPaint.color = fade(colSpecialKey, stripAlpha)
            canvas.drawRoundRect(stripRect(), corner, corner, fillPaint)
        }

        // 1. Key backgrounds
        rows.forEachIndexed { r, row ->
            if (r == 0 && stripAlpha <= 0f) return@forEachIndexed
            row.forEachIndexed { c, key ->
                val bg = backgroundFor(key) ?: return@forEachIndexed
                fillPaint.color = if (r == 0) fade(bg, stripAlpha) else bg
                val rect = keyRect(r, row, c)
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
                // Key borders: on every key, but not on tool-strip buttons (they have no key shape), except the first word
                if (colEdge != 0 && (key.type != KeyType.TOOL || key.best)) {
                    ringPaint.color = if (r == 0) fade(colEdge, stripAlpha) else colEdge
                    ringPaint.strokeWidth = u(Design.STROKE_HAIRLINE)
                    val inset = ringPaint.strokeWidth / 2f
                    canvas.drawRoundRect(RectF(rect).apply { inset(inset, inset) }, corner - inset, corner - inset, ringPaint)
                }
            }
        }
        // 1b. Keys the tour points at (the button row being shown): a soft tint of your colour, never the
        // highlight's solid fill, so the key you're on stays obvious
        if (spotlight.isNotEmpty() && spotFade > 0f) rows.forEachIndexed { r, row ->
            if (r == 0 && stripAlpha <= 0f) return@forEachIndexed
            row.forEachIndexed { c, key ->
                if (key.hint == null || key.hint !in spotlight) return@forEachIndexed
                fillPaint.color = Contrast.withAlpha(colAccent, (Design.ALPHA_TINT_SOFT * spotFade).roundToInt())
                canvas.drawRoundRect(keyRect(r, row, c), corner, corner, fillPaint)
            }
        }
        if (stripAlpha > 0f) {
            val s = canvas.saveLayerAlpha(stripRect(), (stripAlpha * 255).roundToInt())
            drawWordDividers(canvas, rows[0])
            canvas.restoreToCount(s)
        }

        // 2. The gliding highlight, in the chosen style (while the accents are open it sits on their tray)
        // ...and none on the empty clipboard's note: it isn't something to pick (audit, 30 Oct 2026)
        val onNote = rows.getOrNull(selRow)?.getOrNull(selCol)?.type == KeyType.EMPTY
        val highlightHidden = a != null && a.open
        if (!highlightHidden && !onNote) drawHighlight(canvas)

        // 3. Labels, icons, badges
        iconPaint.strokeWidth = u(Design.STROKE_ICON)
        rows.forEachIndexed { r, row ->
            val alpha = if (r == 0) stripAlpha else 1f
            if (alpha <= 0f) return@forEachIndexed
            val layerSaved = if (alpha < 1f) canvas.saveLayerAlpha(stripRect(), (alpha * 255).roundToInt()) else -1
            row.forEachIndexed { c, key ->
                val focused = r == selRow && c == selCol && !highlightHidden
                val rect = keyRect(r, row, c)
                if (focused && style == HighlightStyle.LIFT) {
                    // Lift: the content moves out with the lifted key by exactly the same step, so badges stay
                    // inside it on every key width (scaling made the space bar's badge overshoot, 28 Oct 2026)
                    val g = liftGrow(rect)
                    drawKeyContent(canvas, key, RectF(rect).apply { inset(-g, -g) }, focused = true)
                } else {
                    drawKeyContent(canvas, key, rect, focused)
                }
            }
            if (layerSaved >= 0) canvas.restoreToCount(layerSaved)
        }

        // 3b. NEW: the accents, over a dimmed keyboard
        drawAccentLayer(canvas, rows)

        // 4. RT combo chips, faded in over the tool strip (never over the accents: those come first)
        if (comboFade > 0f && accents == null) {
            val saved = canvas.saveLayerAlpha(stripRect(), (comboFade * 255).roundToInt())
            drawChips(canvas, stripRect(), colBackdrop, comboChips())
            canvas.restoreToCount(saved)
        }

        // 5. Button guide under the clipboard cards
        if (layer == Layer.CLIPS && settings.showHints) {
            val top = lastSlotTop()
            drawChips(
                canvas, RectF(contentLeft(), top, contentLeft() + contentWidth(), top + slotHeight), colBackdrop,
                if (clips.isEmpty() && removedClip == null) listOf(
                    listOf(HintPart.Badge(ControllerButton.B), HintPart.Text("Back"))
                ) else if (removedClip != null && focusedClipIndex() == removedClip) listOf(
                    listOf(HintPart.Badge(ControllerButton.X), HintPart.Text("Undo")),
                    listOf(HintPart.Badge(ControllerButton.B), HintPart.Text("Back"))
                ) else listOf(
                    listOf(HintPart.Badge(ControllerButton.A), HintPart.Text("Paste")),
                    listOf(HintPart.Badge(ControllerButton.X), HintPart.Text("Remove")),
                    listOf(HintPart.Badge(ControllerButton.B), HintPart.Text("Back"))
                )
            )
        }
    }

    private fun fade(colour: Int, alpha: Float) =
        Contrast.withAlpha(colour, (Color.alpha(colour) * alpha).roundToInt().coerceIn(0, 255))

    private val shiftLook: ShiftState
        get() = if (heldShift && shiftState == ShiftState.OFF) ShiftState.ONCE else shiftState

    private fun backgroundFor(key: Key): Int? = when (key.type) {
        KeyType.CLIP -> if (key.index == removedClip) null else colKey   // a removed clip leaves an empty place
        KeyType.CHAR, KeyType.SPACE -> colKey
        KeyType.EMPTY, KeyType.GAP -> null
        KeyType.DIGIT -> null                                // drawn by drawNumberBar: slim keys, their own radius
        KeyType.TOOL -> when {
            key.tool == Tool.WORD && key.best -> colKey      // the first word sits on a key you can press
            key.tool == Tool.WORD -> null
            key.tool == Tool.SELECT && selecting -> colAccentSoft
            key.tool == Tool.HISTORY && layer == Layer.CLIPS -> colAccentSoft
            else -> null
        }
        KeyType.SHIFT -> when (shiftLook) {
            ShiftState.OFF -> colSpecialKey
            ShiftState.ONCE -> colAccentSoft
            ShiftState.LOCK -> colAccent
        }
        else -> colSpecialKey
    }

    /** [ink] made quiet (about half strength) but never below 4.5 : 1 on [bg] (Light theme, design audit 28 Oct 2026). */
    private fun quietInk(ink: Int, bg: Int, start: Int = 120): Int {
        var a = start
        // A little over 4.5, so the drawn letters (soft edges, the see-through theme's shadow) still measure 4.5 (LegibilityRenderTest)
        val target = if (clear) 4.9 else 4.6
        while (a < 255 && Contrast.ratio(Contrast.over(Contrast.withAlpha(ink, a), bg), bg) < target) a += 5
        return Contrast.withAlpha(ink, a.coerceAtMost(255))
    }

    /** The colour actually visible behind a key's label. */
    private val style get() = settings.highlightStyle

    /**
     * The fill of the highlighted key, per style (after Android TV's focus system):
     *  - Glow:    accent fill with a soft accent halo (default)
     *  - Solid:   accent fill, nothing else. The simplest
     *  - Outline: the key stays dark but lifts one tone, with a crisp accent ring around it
     *  - Lift:    the key rises: accent-tinted surface, grows ~7 %, small accent bar underneath
     */
    private val focusFill: Int
        get() = when (style) {
            HighlightStyle.GLOW, HighlightStyle.SOLID -> colAccent
            HighlightStyle.OUTLINE -> Contrast.over(Contrast.withAlpha(Color.WHITE, Design.ALPHA_FAINT_STRONG), colKey)
            HighlightStyle.LIFT -> liftFill
        }

    /**
     * Lift's tinted surface: the softest tint of the highlight (from 35 %) that still stands out 3:1 from the
     * keys around it, so a lifted key is easy to spot from a distance on every theme. Where even a strong tint
     * can't (the Light theme), it stops at 70 % and the accent bar under the label carries the contrast.
     */
    private val liftFill: Int
        get() {
            var a = Design.ALPHA_TINT_GLOW
            while (a < Design.ALPHA_LIFT_MAX) {
                val c = Contrast.over(Contrast.withAlpha(colAccent, a), colKey)
                if (Contrast.ratio(c, colKey) >= 3.0) return c
                a += 8
            }
            return Contrast.over(Contrast.withAlpha(colAccent, Design.ALPHA_LIFT_MAX), colKey)
        }

    /** How far a lifted key grows on each side: the same small step for every key, at most a quarter of the gap. */
    private fun liftGrow(r: RectF) = min(r.height() * (LIFT_SCALE - 1f) / 2f, gap / 4f)

    private fun drawHighlight(canvas: Canvas, radius: Float = corner) {
        val lifting = style == HighlightStyle.LIFT
        val cx = focusRect.centerX()
        val cy = focusRect.centerY()
        // Lift grows the key, but by the same small step on every side and never more than a
        // quarter of the gap, so wide keys (space, enter) don't reach their neighbours
        val grow = if (lifting) liftGrow(focusRect) else 0f
        val hw = focusRect.width() / 2f * pressScale + grow
        val hh = focusRect.height() / 2f * pressScale + grow
        val pill = RectF(cx - hw, cy - hh, cx + hw, cy + hh)
        logRect(canvas, "pill", pill)
        val r = min(radius + grow, pill.height() / 2f)

        // Rings sit on the key's own edge, never outside it: an outside ring fills the gap and
        // touches the next key (design audit, 28 Oct 2026). Nested corners share a centre.
        val halo = style == HighlightStyle.GLOW || tv
        if (halo) {
            // Glow: a soft accent ring on the key's edge, a hairline of the key showing, then the fill.
            // TV mode always adds it, thicker, so the highlight reads from across the room.
            val ringW = u(if (tv) Design.RING_GLOW_TV else Design.RING_GLOW)
            ringPaint.color = if (tv) Contrast.withAlpha(colAccent, Design.ALPHA_GLOW_TV) else colGlow
            ringPaint.strokeWidth = ringW
            val half = ringW / 2f
            canvas.drawRoundRect(RectF(pill).apply { inset(half, half) }, max(0f, r - half), max(0f, r - half), ringPaint)
        }
        val fillInset = if (halo) u(if (tv) Design.RING_GLOW_TV else Design.RING_GLOW) + u(Design.RING_GAP) else 0f
        val fillR = max(0f, r - fillInset)
        fillPaint.color = focusFill
        canvas.drawRoundRect(RectF(pill).apply { inset(fillInset, fillInset) }, fillR, fillR, fillPaint)

        when (style) {
            HighlightStyle.OUTLINE -> {
                // A crisp accent ring on the key's own edge (the lifted key fills the inside), heavy enough to read from a distance
                val ringW = u(Design.RING_OUTLINE)
                ringPaint.color = colAccent
                ringPaint.strokeWidth = ringW
                val half = ringW / 2f
                canvas.drawRoundRect(RectF(pill).apply { inset(half, half) }, max(0f, r - half), max(0f, r - half), ringPaint)
            }
            HighlightStyle.LIFT -> {
                // A short accent bar under the label, like a lit indicator
                val barW = pill.width() * 0.28f
                val barH = u(2.5f)
                // Low enough to clear descenders (g, p, y) on every key height (audit, 28 Oct 2026)
                val barY = pill.bottom - u(2.5f)
                fillPaint.color = colAccent
                canvas.drawRoundRect(cx - barW / 2, barY - barH, cx + barW / 2, barY, barH, barH, fillPaint)
            }
            else -> Unit
        }
        // See-through: the app behind can be any colour, even white. A dark hairline on the highlight's own
        // edge (a two-tone edge) keeps it standing out on light and dark alike (ClearThemeTest)
        if (clear) {
            ringPaint.color = Contrast.withAlpha(Design.INK_DARK, Design.CLEAR_FOCUS_EDGE)
            ringPaint.strokeWidth = u(Design.STROKE_HAIRLINE)
            val half = ringPaint.strokeWidth / 2f
            canvas.drawRoundRect(RectF(pill).apply { inset(half, half) }, max(0f, r - half), max(0f, r - half), ringPaint)
        }
    }

    private fun visibleBg(key: Key, focused: Boolean): Int {
        if (focused) return focusFill
        if (key.type == KeyType.DIGIT) return seen(colKey)          // digits sit on their own slim keys
        // On the see-through theme the tool strip sits on its own plate (drawn in onDraw)
        val bg = backgroundFor(key) ?: return if (clear) seen(colSpecialKey) else colBackdrop
        return if (clear) seen(bg) else if (Color.alpha(bg) == 255) bg else Contrast.over(bg, colBackdrop)
    }

    /** The accent colour if readable on this background, otherwise plain ink. */
    private fun accentOn(bg: Int): Int =
        if (Contrast.ratio(colAccent, bg) >= 3.0) colAccent else Contrast.inkFor(bg)

    private val undoOnDelete get() = heldShift && settings.undoCombo

    private fun drawKeyContent(canvas: Canvas, key: Key, rect: RectF, focused: Boolean) {
        val bg = visibleBg(key, focused)
        val ink = Contrast.inkFor(bg)
        // Quiet words (tools, the other suggestions): soft, but never under 4.5:1 on what's really behind them
        val soft = if (clear) quietInk(ink, bg, Design.ALPHA_INK_SOFT) else Contrast.withAlpha(ink, Design.ALPHA_INK_SOFT)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = u(6f)

        // Icons (shift, delete, enter) step left, out of the corner badge's way, on keys too narrow for both
        // to sit centred (the ZR badge sat on the shift arrow on vertical handhelds, audit 28 Oct 2026)
        val hint = if (settings.showHints) key.hint?.takeIf { settings.hasSticks || !it.isStick } else null
        var iconS = s
        var iconYields = false
        var iconHalfW = s * 1.35f
        var iconHalfH = s * 1.3f
        var iconCx = cx
        var iconCy = cy
        // Badges follow a Round key's corner when there's room; on narrow keys they keep the tight inset
        val inset = if (hint != null && rect.width() - badges.width(hint) - 2 * iconHalfW - u(10f) < 2 * badgeInset) u(3f) else badgeInset
        if (hint != null) {
            val badgeLeft = rect.right - inset - badges.width(hint)
            val badgeBottom = rect.top + inset + 2 * badges.radius
            if (cy - iconHalfH < badgeBottom + u(2f)) {
                // First step left; if the key is too narrow for that, also step down below the badge
                iconCx = max(rect.left + u(4f) + iconHalfW, min(cx, badgeLeft - u(3f) - iconHalfW))
                if (iconCx + iconHalfW > badgeLeft - u(1f)) {
                    // Below the badge; on a key too short for that too (Shift one key wide on a phone in Turkish,
                    // Russian or Ukrainian; audit, 30 Oct 2026) the icon gets just small enough to fit under it
                    // (a little; if it would have to shrink more than that, the badge steps aside instead and the
                    // icon stays whole and centred, as words do. Holding RT still shows its tips)
                    val room = rect.bottom - u(3f) - (badgeBottom + u(2f))
                    val k = if (room < 2 * iconHalfH) room / (2 * iconHalfH) else 1f
                    if (k < ICON_MIN_SCALE) {
                        iconYields = true; iconCx = cx; iconCy = cy
                    } else {
                        if (k < 1f) {
                            iconS = s * k; iconHalfW *= k; iconHalfH *= k
                            iconCx = max(rect.left + u(4f) + iconHalfW, min(cx, badgeLeft - u(3f) - iconHalfW))
                        }
                        iconCy = min(rect.bottom - u(3f) - iconHalfH, max(cy, badgeBottom + u(2f) + iconHalfH))
                    }
                }
            }
        }
        // Words on the key keep clear of the badge the same way (drawLabel reads these two)
        badgeYields = false
        labelKey = rect
        labelBadge = hint?.let { RectF(rect.right - inset - badges.width(it), rect.top + inset, rect.right - inset, rect.top + inset + 2 * badges.radius) }
        if (key.type == KeyType.SHIFT || key.type == KeyType.DELETE || key.type == KeyType.ENTER)
            logRect(canvas, "icon", RectF(iconCx - iconHalfW, iconCy - iconHalfH, iconCx + iconHalfW, iconCy + iconHalfH))

        when (key.type) {
            // The number pad's digits are bigger: its keys are a compact block, not a stretched row
            KeyType.CHAR -> drawLetter(canvas, displayLabel(key), cx, cy, ink, if (layer == Layer.NUMPAD) Design.TYPE_KEY_DIGIT else Design.TYPE_KEY, rect.width())
            // The number row: smaller and a touch quieter than letters
            // (on Clear only as quiet as 4.5 : 1 allows: over a white app the fixed step fell to 4.1, 30 Oct 2026)
            KeyType.DIGIT -> drawLabel(canvas, key.label, cx, cy, if (focused) ink else if (clear) quietInk(ink, bg, Design.ALPHA_INK_STRONG) else Contrast.withAlpha(ink, Design.ALPHA_INK_STRONG), Design.TYPE_KEY_ACCENT, 500)
            KeyType.GAP -> Unit
            KeyType.LAYER -> drawLabel(canvas, key.label, cx, cy, ink, Design.TYPE_KEY_LABEL, 600)
            KeyType.SPACE -> {
                if (selecting && settings.showHints) {
                    drawChips(canvas, rect, bg, selectChips())
                    return                                  // the chips replace the Y badge
                }
                if (accents?.open == true && settings.showHints) return     // the accent hints use the space bar
                val left = languageFlashUntil - SystemClock.uptimeMillis()
                val showName = settings.showLanguageLabel || left > 0
                // A flashed name fades into the line over its last moment instead of vanishing in one frame (30 Oct 2026)
                val nameAlpha = if (settings.showLanguageLabel || left >= Design.MOTION_FADE || !ValueAnimator.areAnimatorsEnabled()) 1f
                    else Design.EASE_IN.getInterpolation(1f - left.toFloat() / Design.MOTION_FADE).let { 1f - it }
                fun line(alpha: Float) {
                    // Clean: just a short line, as in 1.0
                    iconPaint.color = fade(soft, alpha)
                    iconPaint.style = Paint.Style.STROKE
                    canvas.drawLine(cx - rect.width() * 0.12f, cy + s * 0.5f, cx + rect.width() * 0.12f, cy + s * 0.5f, iconPaint)
                }
                if (showName && labelFits(settings.language.label, rect)) {
                    // The language you're typing in, quietly, like phone keyboards do
                    drawLabel(canvas, settings.language.label, cx, cy, fade(quietInk(ink, seen(bg)), nameAlpha), Design.TYPE_KEY_LABEL, 500)
                    if (nameAlpha < 1f) { line(1f - nameAlpha); postInvalidateOnAnimation() }
                } else line(1f)
            }
            KeyType.SHIFT -> drawShift(canvas, iconCx, iconCy, iconS, focused, bg, ink)
            KeyType.DELETE -> if (undoOnDelete) drawUndo(canvas, iconCx, iconCy, iconS, ink) else drawBackspace(canvas, iconCx, iconCy, iconS, ink)
            KeyType.ENTER -> drawEnter(canvas, iconCx, iconCy, iconS, ink)
            KeyType.TOOL -> drawTool(canvas, key, rect, s, focused, bg, ink, soft)
            KeyType.CLIP -> drawClip(canvas, key, rect, ink)
            KeyType.EMPTY -> drawLabel(canvas, key.label, cx, cy, soft, Design.TYPE_KEY_LABEL, 400)
        }

        if (iconYields && hint != null) logRect(canvas, "yield:${key.type}", rect)
        if (hint != null && !badgeYields && !iconYields) {
            hint.let {
                val right = rect.right - inset
                val left = badges.drawInCorner(canvas, it, right, rect.top + inset)
                logRect(canvas, "badge", RectF(left, rect.top + inset, right, rect.top + inset + 2 * badges.radius))
            }
        }
        labelKey = null; labelBadge = null
    }

    // ---- Hint chips ---------------------------------------------------------

    /** Shown over the tool strip while RT is held. */
    private fun comboChips(): List<List<HintPart>> {
        val accent = listOf(HintPart.Badge(ControllerButton.Y), HintPart.Text("Accent"))
        val undo = if (settings.undoCombo) listOf(HintPart.Badge(ControllerButton.X), HintPart.Text("Undo")) else null
        // Languages with accented letters need the accent tip more than undo, so it comes first.
        // Chips that don't fit are dropped from the end.
        val accentsMatter = settings.language != Language.ENGLISH
        val switchLanguage = if (settings.languages.size > 1)
            listOf(HintPart.Badge(ControllerButton.L2), HintPart.Text("Language")) else null
        return listOfNotNull(
            listOf(HintPart.Badge(ControllerButton.L1), HintPart.Badge(ControllerButton.R1), HintPart.Text("Jump word")),
            listOf(HintPart.Badge(ControllerButton.A), HintPart.Text("Capital")),
            if (accentsMatter) accent else undo,
            switchLanguage,
            if (accentsMatter) undo else accent
        )
    }

    /** Shown on the space bar in select mode, most useful first. */
    private fun selectChips(): List<List<HintPart>> = listOfNotNull(
        listOf(HintPart.Badge(ControllerButton.L1), HintPart.Badge(ControllerButton.R1), HintPart.Text("Extend")),
        if (!settings.selectAllButton) {
            listOf(HintPart.Badge(ControllerButton.SELECT, presses = 2), HintPart.Text("All"))
        } else {
            null
        },
        listOf(HintPart.Badge(ControllerButton.B), HintPart.Text("Done")),
        listOf(HintPart.Badge(ControllerButton.R2), HintPart.Text("Word"))
    )

    private fun drawChips(canvas: Canvas, area: RectF, background: Int, chips: List<List<HintPart>>) {
        hintPaint.textSize = u(Design.TYPE_CHIP)
        hintPaint.color = Contrast.withAlpha(Contrast.inkFor(background), Design.ALPHA_INK_STRONG)
        applyFont(hintPaint, 500)
        badges.drawChipRow(canvas, chips, area, hintPaint, chipFill, chipEdge)
    }

    // ---- Keys ---------------------------------------------------------------

    private fun drawTool(
        canvas: Canvas, key: Key, rect: RectF, s: Float,
        focused: Boolean, bg: Int, ink: Int, soft: Int
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val active = (key.tool == Tool.SELECT && selecting) || (key.tool == Tool.HISTORY && layer == Layer.CLIPS)
        val colour = when {
            focused -> ink
            active -> accentOn(bg)
            else -> soft
        }
        when (key.tool) {
            Tool.HISTORY -> drawClock(canvas, cx, cy, s * 0.95f, colour)
            Tool.SETTINGS -> drawGear(canvas, cx, cy, s * 0.95f, colour)
            Tool.WORD -> {
                // A suggested word, in your letter font. The first one sits on a key with its RS badge;
                // the others are plain, softer text.
                val weight = if (key.best) 600 else 500
                val wordColour = if (focused || key.best) ink else soft
                textPaint.textSize = u(Design.TYPE_KEY_WORD)
                applyFont(textPaint, weight)
                // The word centres in the room the RS badge leaves (not the whole key), so a long first word
                // keeps all its letters on narrow keyboards (4:3 screens, the tour); it only moves when the badge shows
                var badgeRoom = if (key.best && settings.showHints && settings.hasSticks) badges.width(ControllerButton.R3) + u(3f) else 0f
                // A word you'd take should be readable whole: first it gets a little smaller, then the RS badge steps
                // aside, and only then is it cut ("re…" for "republic" on a phone; audit, 30 Oct 2026)
                val w = textPaint.measureText(key.label)
                var size = Design.TYPE_KEY_WORD
                fun fits(room: Float) = w * LABEL_MIN_FIT <= room
                if (badgeRoom > 0f && !fits(rect.width() - u(12f) - badgeRoom) && fits(rect.width() - u(12f))) {
                    badgeRoom = 0f; badgeYields = true; labelBadge = null
                }
                val room = rect.width() - u(12f) - badgeRoom
                if (w > room && fits(room)) size *= room / w
                drawLabel(canvas, if (fits(room)) key.label else ellipsize(key.label, room), cx - badgeRoom / 2f, cy, wordColour, size, weight)
            }
            else -> {
                val text = when (key.tool) {
                    // The count only if it fits next to the corner badge; the word never overlaps anything
                    Tool.SELECT -> if (selecting) toolLabelThatFits(rect, "Selecting · $selectedCount", "Selecting", "Select") else "Select"
                    Tool.PASTE -> clipPreview?.let { toolLabelThatFits(rect, "Paste  “$it”", "Paste", badge = key.hint) } ?: "Paste"
                    else -> key.label
                }
                val size = Design.TYPE_KEY_LABEL * toolFit()
                // On a key too narrow for the word and its badge (Select on a phone at Largest), the badge steps aside
                key.hint?.takeIf { settings.showHints }?.let { b ->
                    textPaint.textSize = u(size); applyFont(textPaint, 500)
                    // the same room drawLabel works with: from the key's padding to 3 dp short of the badge
                    if (textPaint.measureText(text) > rect.width() - u(4f) - badgeInset - badges.width(b) - u(3f)) { badgeYields = true; labelBadge = null }
                }
                drawLabel(canvas, text, cx, cy, colour, size, 500)
            }
        }
    }

    /**
     * One size for every word in the top row (Select, Cut, Copy, Paste): each used to shrink on its own, so "Copy"
     * came out smaller than "Cut" beside it on a phone at Largest (audit, 30 Oct 2026). The size is the one the
     * tightest word needs (badges step aside rather than shrink the row); drawLabel then has nothing left to do.
     */
    private fun toolFit(): Float {
        val row = allRows().firstOrNull() ?: return 1f
        val state = "${row.size}|${width}|$scale|${settings.font}|${settings.showHints}|$selecting"
        toolFitCache?.let { (k, f) -> if (k == state) return f }
        val paint = Paint(textPaint).apply { textSize = u(Design.TYPE_KEY_LABEL) }
        applyFont(paint, 500)
        var f = 1f
        row.forEachIndexed { c, key ->
            if (key.type != KeyType.TOOL || key.tool == Tool.WORD || key.tool == Tool.HISTORY || key.tool == Tool.SETTINGS) return@forEachIndexed
            val rect = keyRect(0, row, c)
            // 1 dp more each side than drawLabel keeps, so drawLabel never has to shrink one word on its own
            val room = rect.width() - 2 * u(5f)
            val w = paint.measureText(key.label)
            if (w > room && w > 0f) f = min(f, room / w)
        }
        f = max(f, TOOL_MIN_FIT)
        toolFitCache = state to f
        return f
    }
    private var toolFitCache: Pair<String, Float>? = null

    private fun drawClip(canvas: Canvas, key: Key, rect: RectF, ink: Int) {
        val pad = u(10f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = u(Design.TYPE_KEY_LABEL)
        textPaint.color = ink
        applyFont(textPaint, 400)
        // A clip waiting to be removed: its place stays, quietly saying so (X brings it back)
        if (key.index == removedClip) {
            val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            // Quiet when the highlight is elsewhere; full ink on the highlight, so it reads on any colour
            val onIt = layer == Layer.CLIPS && focusedClipIndex() == key.index
            textPaint.color = if (onIt) ink else Contrast.withAlpha(ink, Design.ALPHA_INK_SOFT)
            canvas.drawText(REMOVED_LABEL, rect.left + pad, y, textPaint)
            logRect(canvas, "label:$REMOVED_LABEL", RectF(rect.left + pad, rect.top, rect.left + pad + textPaint.measureText(REMOVED_LABEL), rect.bottom))
            textPaint.textAlign = Paint.Align.CENTER
            return
        }
        val oneLine = key.label.replace('\n', ' ').trim()
        val available = max(0f, rect.width() - 2 * pad)
        val fits = textPaint.breakText(oneLine, true, available, null)
        val shown = if (fits < oneLine.length) oneLine.take(max(0, fits - 1)) + "…" else oneLine
        val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(shown, rect.left + pad, y, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** Shortens [text] with "…" until it fits [room] (textPaint must already have its size and font). */
    private fun ellipsize(text: String, room: Float): String {
        if (textPaint.measureText(text) <= room) return text
        var t = text
        while (t.length > 1 && textPaint.measureText("$t…") > room) t = t.dropLast(1)
        return "$t…"
    }

    /** Letters: the chosen font, at the chosen weight. */
    private fun drawLetter(canvas: Canvas, text: String, cx: Float, cy: Float, colour: Int, sizeDp: Float = Design.TYPE_KEY, keyW: Float = 0f) {
        textPaint.color = colour
        textPaint.textSize = u(sizeDp) * letterFit(sizeDp, keyW)
        applyFont(textPaint, 400 + settings.letterWeight * 25)
        coverMissingGlyphs(textPaint, text)
        shadowForClear(textPaint)
        if (customFont == null && settings.letterWeight > 0) {
            // The system font can't change weight smoothly, so thicken the outline instead
            textPaint.style = Paint.Style.FILL_AND_STROKE
            textPaint.strokeWidth = settings.letterWeight * 0.1f * dp * scale
        }
        val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, cx, y, textPaint)
        logText(canvas, text, cx, y)
        textPaint.style = Paint.Style.FILL
        textPaint.strokeWidth = 0f
    }

    /**
     * How much the letters shrink so the widest one on the keyboard fits its key with [LETTER_SIDE_DP] each side:
     * 1 almost always. On a small phone at Largest, W, Æ, Ж, Щ and Ю ran past their keys (audit, 30 Oct 2026).
     * One factor for every letter on screen, so a W is never smaller than the M beside it. Cached per state.
     */
    private fun letterFit(sizeDp: Float, keyW: Float): Float {
        if (keyW <= 0f) return 1f
        val state = "$sizeDp|${keyW.toInt()}|$layer|$shiftState|$heldShift|${settings.language}|${settings.font}|${settings.letterWeight}|$scale"
        letterFitCache?.let { (k, f) -> if (k == state) return f }
        val paint = Paint(textPaint).apply { textSize = u(sizeDp); style = Paint.Style.FILL }
        applyFont(paint, 400 + settings.letterWeight * 25)
        val stroke = if (customFont == null) settings.letterWeight * 0.1f * dp * scale else 0f
        // single letters only: ".com" in web boxes is a CHAR key too, and shrank every letter to its size
        val widest = allRows().flatten().filter { it.type == KeyType.CHAR && displayLabel(it).codePointCount(0, displayLabel(it).length) == 1 }.maxOfOrNull {
            val t = displayLabel(it); coverMissingGlyphs(paint, t); paint.measureText(t) + stroke
        } ?: 0f
        val room = keyW - 2 * u(LETTER_SIDE_DP)
        val f = if (widest > room && widest > 0f) room / widest else 1f
        letterFitCache = state to f
        return f
    }
    private var letterFitCache: Pair<String, Float>? = null

    /**
     * Hyperlegible has no Cyrillic letters (and no ₺ ₽ ₴). For those, switch to Inter,
     * which has them all, instead of letting Android mix in a font that doesn't match.
     */
    private fun coverMissingGlyphs(paint: Paint, text: String) {
        if (settings.font != FontChoice.HYPERLEGIBLE) return
        val inter = fontInter ?: return
        val missing = glyphCache.getOrPut(text) {
            val probe = Paint(paint).apply { typeface = fontHyperlegible }
            text.any { !it.isWhitespace() && !probe.hasGlyph(it.toString()) }
        }
        if (missing) paint.typeface = weighted(inter, lastFontWeight)   // keeps the chosen letter weight
    }

    /** Which labels Hyperlegible can't draw, worked out once per label (not every frame). */
    private val glyphCache = HashMap<String, Boolean>()

    /** The first of these labels that fits a tool key, clear of its corner badge. The last one is used regardless. */
    /**
     * The longest of [options] that fits beside the key's corner badge. Measured with the badge this key
     * really shows (its width differs per button style) and the corner inset of the chosen key shape;
     * the label is centred, so it keeps that room on both sides. CombinationsTest checks every case.
     */
    private fun toolLabelThatFits(rect: RectF, vararg options: String, badge: ControllerButton? = ControllerButton.SELECT): String {
        textPaint.textSize = u(Design.TYPE_KEY_LABEL)
        applyFont(textPaint, 500)
        val side = if (settings.showHints && badge != null) badges.width(badge) + badgeInset + u(Design.BADGE_TEXT_GAP) else u(6f)
        val room = rect.width() - 2 * side
        return options.firstOrNull { textPaint.measureText(it) <= room } ?: options.last()
    }

    /** True when a space-bar label fits with room to spare (short space bars in web boxes, narrow screens). */
    private fun labelFits(text: String, rect: RectF): Boolean {
        textPaint.textSize = u(Design.TYPE_KEY_LABEL)
        applyFont(textPaint, 500)
        return textPaint.measureText(text) <= rect.width() - 2 * u(18f)       // clear of the corner badge
    }

    /** On the Clear theme a soft dark shadow keeps letters crisp over busy pictures. */
    private fun shadowForClear(paint: Paint) {
        if (clear) paint.setShadowLayer(u(2f), 0f, u(0.5f), Contrast.withAlpha(Color.BLACK, Design.SHADOW_CLEAR))
        else paint.clearShadowLayer()
    }

    /** The key being drawn and its corner badge, so its words keep clear of the badge (set in drawKeyContent). */
    private var labelKey: RectF? = null
    private var labelBadge: RectF? = null
    /** Set when a key's word needs the badge's room: that key shows no badge this frame. */
    private var badgeYields = false

    /**
     * A word on a key, centred. On a key too narrow for the word and its corner badge side by side (phones
     * held upright, small handhelds, the largest size) the word first steps left, out of the badge's way,
     * then shrinks a little (to [LABEL_MIN_FIT] at most), and last steps down below the badge. It never
     * leaves its key. CombinationsTest checks every language, size, style and screen.
     */
    private fun drawLabel(canvas: Canvas, text: String, cx: Float, cy: Float, colour: Int, sizeDp: Float, weight: Int) {
        textPaint.color = colour
        textPaint.textSize = u(sizeDp)
        applyFont(textPaint, weight)
        coverMissingGlyphs(textPaint, text)
        shadowForClear(textPaint)
        var x = cx
        var y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        val key = labelKey
        if (key != null && key.contains(cx, cy)) {
            val pad = u(4f)
            var badge = labelBadge
            fun place() {
                val w = textPaint.measureText(text)
                val b = badge
                val right = if (b != null && y + textPaint.descent() > b.top && y + textPaint.ascent() < b.bottom) b.left - u(3f) else key.right - pad
                if (w > right - (key.left + pad))
                    textPaint.textSize = max(u(sizeDp) * LABEL_MIN_FIT, textPaint.textSize * (right - key.left - pad) / w)
                val w2 = textPaint.measureText(text)
                x = max(key.left + pad + w2 / 2f, min(cx, right - w2 / 2f))
            }
            place()
            // Still in the badge's way: below it instead
            val b0 = badge
            if (b0 != null && x + textPaint.measureText(text) / 2f > b0.left - u(1f) && y + textPaint.ascent() < b0.bottom) {
                y = min(key.bottom - u(2f) - textPaint.descent(), b0.bottom + u(1f) - textPaint.ascent())
                x = cx; place()
            }
            // No room for both at all (a short key in the tool strip): the word is what the key does, so the
            // badge steps aside on this key (the Button guide still lists it)
            val w = textPaint.measureText(text)
            if (b0 != null && x + w / 2f > b0.left - u(1f) && y + textPaint.descent() > b0.top && y + textPaint.ascent() < b0.bottom) {
                badgeYields = true
                logRect(canvas, "yield:$text", key)
                y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                textPaint.textSize = u(sizeDp)
                badge = null; place()
            }
            // Wider than the key itself (TV mode at the largest size on a phone): smaller, never outside
            val w3 = textPaint.measureText(text)
            if (w3 > key.width() - 2 * pad) {
                textPaint.textSize *= (key.width() - 2 * pad) / w3
                x = cx
            }
        }
        canvas.drawText(text, x, y, textPaint)
        logText(canvas, text, x, y)
    }

    // =====================================================================
    // ICONS: one family. Same stroke, round caps and joins, fit a 2.4 s box.
    // =====================================================================

    /** Shift: outline = off, filled on tinted key = next letter / RT held, bar on solid key = caps lock. */
    // The icons themselves live in Icons.kt (one family for the keyboard, Settings and the tour)
    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, s: Float, focused: Boolean, bg: Int, ink: Int) {
        iconPaint.color = if (!focused && shiftLook == ShiftState.ONCE) accentOn(bg) else ink
        Icons.shift(canvas, cx, cy, s, shiftLook, iconPaint)
    }

    private fun icon(ink: Int) = iconPaint.apply { color = ink; style = Paint.Style.STROKE }
    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float, s: Float, ink: Int) = Icons.backspace(canvas, cx, cy, s, icon(ink))
    private fun drawUndo(canvas: Canvas, cx: Float, cy: Float, s: Float, ink: Int) = Icons.undo(canvas, cx, cy, s, icon(ink))
    private fun drawEnter(canvas: Canvas, cx: Float, cy: Float, s: Float, ink: Int) = Icons.enter(canvas, cx, cy, s, icon(ink))
    private fun drawClock(canvas: Canvas, cx: Float, cy: Float, r: Float, colour: Int) = Icons.clock(canvas, cx, cy, r, icon(colour))
    private fun drawGear(canvas: Canvas, cx: Float, cy: Float, r: Float, colour: Int) = Icons.gear(canvas, cx, cy, r, icon(colour))

    // =====================================================================
    // TOUCH
    // =====================================================================
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        // Accents open: tap one to type it, tap anywhere else to keep the plain letter
        accents?.takeIf { it.open }?.let { a ->
            val hit = a.list.indices.firstOrNull { i ->
                val rc = cellRect(a, i)
                e.x >= rc.left - gap / 2 && e.x <= rc.right + gap / 2 && e.y >= rc.top - gap && e.y <= rc.bottom + gap / 2
            }
            if (hit != null) {
                chooseAccent(hit)
                closeAccents(keep = true)
            } else closeAccents(keep = false)
            return true
        }
        val rows = allRows()
        for (r in rows.indices) {
            for (c in rows[r].indices) {
                val rect = keyRect(r, rows[r], c)
                val hit = e.x >= rect.left - gap / 2 && e.x <= rect.right + gap / 2 &&
                    e.y >= rect.top - gap / 2 && e.y <= rect.bottom + gap / 2
                if (hit) {
                    if (rows[r][c].type == KeyType.GAP) return true
                    selRow = r
                    selCol = c
                    preferredCentre = null
                    animateFocus()
                    press(rows[r][c])
                    return true
                }
            }
        }
        return true
    }
}
