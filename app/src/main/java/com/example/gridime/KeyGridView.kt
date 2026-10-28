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

    private val toolHeight get() = u(26f)
    private val keyHeight get() = u(31f)
    private val gap get() = u(Design.SPACE_XS)
    private val edge get() = u(6f)
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
    private var chipFill = Contrast.withAlpha(Color.WHITE, 0x12)
    private var chipEdge = Contrast.withAlpha(Color.WHITE, 0x24)

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
            colBackground = Contrast.withAlpha(Color.BLACK, ((1f - t) * 0.6f * 255).roundToInt())
            colKey = Contrast.withAlpha(Color.BLACK, ((0.88f - 0.38f * t) * 255).roundToInt())
            colSpecialKey = Contrast.withAlpha(Color.BLACK, ((0.96f - 0.38f * t) * 255).roundToInt())
            colBackdrop = Color.BLACK
        } else {
            colBackground = tone.background
            colKey = tone.key
            colSpecialKey = tone.special
            colBackdrop = tone.background
        }
        colEdge = tone.edge
        val chipInk = Contrast.inkFor(colBackdrop)
        chipFill = Contrast.withAlpha(chipInk, 0x12)
        chipEdge = Contrast.withAlpha(chipInk, 0x24)
        colAccent = s.accent
        colAccentSoft = Contrast.withAlpha(s.accent, 0x40)
        colGlow = Contrast.withAlpha(s.accent, 0x59)
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
        keepStripFocus {
            words = list
            wordPrefix = prefix
        }
        if (before != showWords) fadeStripIn()
        invalidate()
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
        accents = null
        aHeld = false
    }

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
        animateFocus()
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
        val a = accents ?: return
        if (!a.open) return resetAccents()
        if (!keep && a.index != 0) listener?.onReplaceLast(a.list[a.index], a.list[0])
        if (keep) feedback?.key() else feedback?.back()
        a.open = false
        a.sticky = false
        pulse()
        fadeAccentsOut()
        animateFocus()
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
            interpolator = easeOut
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
        val top = rowTop(0) + (1f - easeOut.getInterpolation(accentAlpha(a))) * u(5f)
        return RectF(left + i * w + gap / 2f, top, left + (i + 1) * w - gap / 2f, top + toolHeight)
    }

    /** The colour a key really shows (see-through keys sit on black). */
    private fun solid(c: Int): Int = if (Color.alpha(c) == 255) c else Contrast.over(c, if (clear) Color.BLACK else colBackdrop)

    /** Accent cells are lifted one step towards white on dark themes so they stand out. Light keys are already white. */
    private fun lifted(c: Int): Int {
        val b = solid(c)
        return if (Contrast.inkFor(b) == Color.WHITE) Contrast.over(Contrast.withAlpha(Color.WHITE, 0x1C), b) else b
    }

    /**
     * The accent layer: the keyboard dims, the held key stays lit, and the accents sit on a raised
     * tray edged in your highlight colour. Solid themes dim towards their own background (dark themes
     * darken, Light fades to its grey); Clear dims with black, so the picture behind darkens too.
     */
    private fun drawAccentLayer(canvas: Canvas, rows: List<List<Key>>) {
        val a = accents ?: return
        val alpha = accentAlpha(a)
        if (alpha <= 0f) return
        val full = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val saved = canvas.saveLayerAlpha(full, (alpha * 255).roundToInt())
        // 1. dim
        fillPaint.color = if (clear) Contrast.withAlpha(Color.BLACK, 0x8C) else Contrast.withAlpha(colBackdrop, 0xA6)
        if (tv) canvas.drawRoundRect(tvPanel(), u(20f), u(20f), fillPaint) else canvas.drawRect(full, fillPaint)
        // 2. the held key stays lit
        val heldBg = Contrast.over(colAccentSoft, lifted(colKey))
        fillPaint.color = heldBg
        canvas.drawRoundRect(a.keyRect, corner, corner, fillPaint)
        drawLetter(canvas, a.list[0], a.keyRect.centerX(), a.keyRect.centerY(), Contrast.inkFor(heldBg))
        // 3. the tray
        val cr = min(corner, toolHeight / 2f)
        val first = cellRect(a, 0)
        val last = cellRect(a, a.list.lastIndex)
        val p = u(3f)
        val tray = RectF(first.left - p, first.top - p, last.right + p, first.bottom + p)
        fillPaint.color = if (clear) Contrast.withAlpha(Color.BLACK, 0xE6) else solid(colBackdrop)
        canvas.drawRoundRect(tray, cr + p, cr + p, fillPaint)
        ringPaint.color = Contrast.withAlpha(colAccent, 0x99)
        ringPaint.strokeWidth = u(1f)
        canvas.drawRoundRect(RectF(tray).apply { inset(u(0.5f), u(0.5f)) }, cr + p, cr + p, ringPaint)
        // 4. cells: the plain letter first (quieter), then its accents
        a.list.indices.forEach { i ->
            val rc = cellRect(a, i)
            fillPaint.color = if (i == 0) solid(colSpecialKey) else lifted(colKey)
            canvas.drawRoundRect(rc, cr, cr, fillPaint)
            if (colEdge != 0 && !clear) {
                ringPaint.color = colEdge
                ringPaint.strokeWidth = u(1f)
                canvas.drawRoundRect(RectF(rc).apply { inset(u(0.5f), u(0.5f)) }, cr, cr, ringPaint)
            }
        }
        // 5. the highlight, then the letters on top
        if (a.open) drawHighlight(canvas, cr)
        a.list.forEachIndexed { i, ch ->
            val rc = cellRect(a, i)
            val focused = a.open && i == a.index
            val bg = if (focused) focusFill else if (i == 0) solid(colSpecialKey) else lifted(colKey)
            val ink = Contrast.inkFor(bg)
            val s = canvas.save()
            if (focused && style == HighlightStyle.LIFT) canvas.scale(LIFT_SCALE, LIFT_SCALE, rc.centerX(), rc.centerY())
            drawLetter(canvas, ch, rc.centerX(), rc.centerY(), if (i == 0 && !focused) Contrast.withAlpha(ink, 170) else ink, 17.5f)
            canvas.restoreToCount(s)
        }
        // 6. the hint, on the space bar as in select mode
        if (a.open && settings.showHints) {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, key ->
                    if (key.type == KeyType.SPACE) drawChips(canvas, keyRect(r, row, c), solid(colBackdrop), accentChips(a))
                }
            }
        }
        canvas.restoreToCount(saved)
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
     * The optional number row: one bar in the special-key colour, so it reads as a shelf above the
     * letters rather than more letters. Depth comes from light, as everywhere in Inlay: a faint
     * hairline edge (8 %, or the theme's own key border). Short, soft dividers sit between the
     * digits and step aside next to the highlighted one.
     */
    private fun drawNumberBar(canvas: Canvas, rows: List<List<Key>>) {
        if (!hasNumberRow) return
        val row = rows[1]
        val a = keyRect(1, row, 0)
        val b = keyRect(1, row, row.lastIndex)
        val bar = RectF(a.left, a.top, b.right, a.bottom)     // lines up with the letter keys' outer edges
        val r = numberBarRadius
        fillPaint.color = colSpecialKey
        canvas.drawRoundRect(bar, r, r, fillPaint)
        val ink = Contrast.inkFor(solid(colSpecialKey))
        ringPaint.color = if (colEdge != 0) colEdge else Contrast.withAlpha(ink, 0x14)
        ringPaint.strokeWidth = u(1f)
        canvas.drawRoundRect(RectF(bar).apply { inset(u(0.5f), u(0.5f)) }, r - u(0.5f), r - u(0.5f), ringPaint)
        fillPaint.color = Contrast.withAlpha(ink, 0x17)
        val h = numRowHeight * 0.36f
        val focusedCol = if (selRow == 1 && accents == null) selCol else -2
        for (i in 1 until row.size) {
            if (i == focusedCol || i - 1 == focusedCol) continue
            val x = keyRect(1, row, i).left - gap / 2f
            canvas.drawRoundRect(x - u(0.5f), bar.centerY() - h / 2f, x + u(0.5f), bar.centerY() + h / 2f, u(0.5f), u(0.5f), fillPaint)
        }
    }

    /** The bar's corners follow your key shape, never rounder than a pill. */
    private val numberBarRadius get() = min(corner, numRowHeight / 2f)

    /** A hairline between the two plain word suggestions (the first one already sits on a key). */
    private fun drawWordDividers(canvas: Canvas, strip: List<Key>) {
        if (!showWords) return
        fillPaint.color = Contrast.withAlpha(Contrast.inkFor(colBackdrop), 0x1A)
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
        val box = RectF(contentLeft(), edge, contentLeft() + contentWidth(), edge + previewHeight)
        fillPaint.color = Contrast.withAlpha(Contrast.inkFor(colBackdrop), 0x14)
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
        if (previewBefore.isEmpty() && previewSelected.isEmpty() && previewAfter.isEmpty()) {
            textPaint.color = Contrast.withAlpha(ink, 90)
            canvas.drawText(placeholder, x, baseline, textPaint)
        }
        textPaint.color = ink
        canvas.drawText(before, x, baseline, textPaint)
        x += textPaint.measureText(before)
        if (previewSelected.isNotEmpty()) {
            fillPaint.color = Contrast.withAlpha(colAccent, 0x59)
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
            textPaint.color = Contrast.withAlpha(ink, 90)
            canvas.drawText(best.substring(wordPrefix.length), x - u(3f), baseline, textPaint)
        }
        textPaint.color = Contrast.withAlpha(ink, 160)
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
    private var focusAnim: ValueAnimator? = null
    private var pressScale = 1f
    private var pulseAnim: ValueAnimator? = null
    private var comboFade = 0f             // 0 = tool strip, 1 = RT combo chips
    private var comboAnim: ValueAnimator? = null

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private companion object {
        const val LANGUAGE_FLASH_MS = 1400L
        // TV mode (Android TV design guide: ~3 m viewing distance, 5 % safe area)
        const val TV_WIDTH_DP = 600f          // compact keyboard, centred
        const val TV_SAFE_SIDE = 0.05f
        const val TV_SAFE_BOTTOM = 0.05f
        const val TV_SCREEN_SHARE = 0.55f     // leave the game visible
        const val TV_PREVIEW_DP = 34f
        const val TV_PREVIEW_TEXT_DP = 18f
        const val LEAD_AHEAD = 1.3f
        const val LIFT_SCALE = 1.07f
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
        const val ACCENT_HOLD_START_MS = 150L
        /** ...then the strip hands over to the accents over this long. */
        const val ACCENT_REVEAL_MS = 200L
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
    private fun animateFocus(wrapped: Boolean = false) {
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

        if (settings.motion == HighlightMotion.INSTANT || wrapped) {
            focusRect.set(to)
            if (wrapped && settings.motion != HighlightMotion.INSTANT) pulse(from = 0.94f)
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

    private fun pulse(from: Float = 0.92f) {
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
            interpolator = easeOut
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
        val row = selRow; val col = selCol; val centre = preferredCentre
        quiet = true
        try { return block() } finally { selRow = row; selCol = col; preferredCentre = centre; quiet = false }
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
    fun stepTowards(label: String): Boolean? {
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
        if (selCol != targetCol) { moveFocus(if (targetCol > selCol) 1 else -1, 0); return false }
        return true
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

    fun pressSelected() {
        onDemoInput?.invoke("A", null)
        clampFocus()
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
        val tvExtraDp = if (tv) TV_PREVIEW_DP + Design.SPACE_XS else 0f
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
        val h = bodyTop + bodyHeight + edge + tvBottomMargin()
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
    private val keysTop get() = edge + if (tv) previewHeight + gap else 0f
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

    private fun tvPanel(): RectF {
        val pad = u(10f)
        return RectF(contentLeft() - pad, edge - pad * 0.6f, contentLeft() + contentWidth() + pad, bodyTop + bodyHeight + pad)
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
            fillPaint.color = if (clear) colBackground else Contrast.withAlpha(colBackground, 0xF2)
            canvas.drawRoundRect(panel, u(20f), u(20f), fillPaint)
            ringPaint.color = Contrast.withAlpha(Contrast.inkFor(colBackdrop), 0x1F)
            ringPaint.strokeWidth = u(1f)
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
                    ringPaint.strokeWidth = u(1f)
                    val inset = ringPaint.strokeWidth / 2f
                    canvas.drawRoundRect(RectF(rect).apply { inset(inset, inset) }, corner - inset, corner - inset, ringPaint)
                }
            }
        }
        if (stripAlpha > 0f) {
            val s = canvas.saveLayerAlpha(stripRect(), (stripAlpha * 255).roundToInt())
            drawWordDividers(canvas, rows[0])
            canvas.restoreToCount(s)
        }

        // 2. The gliding highlight, in the chosen style (while the accents are open it sits on their tray)
        val highlightHidden = a != null && a.open
        if (!highlightHidden) drawHighlight(canvas)

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
                    // Lift: the label grows with its key
                    val saved = canvas.save()
                    canvas.scale(LIFT_SCALE, LIFT_SCALE, rect.centerX(), rect.centerY())
                    drawKeyContent(canvas, key, rect, focused = true)
                    canvas.restoreToCount(saved)
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
                listOf(
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
        KeyType.CHAR, KeyType.SPACE, KeyType.CLIP -> colKey
        KeyType.EMPTY, KeyType.GAP -> null
        KeyType.DIGIT -> null                                // the number row shares one bar (drawNumberBar)
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
            HighlightStyle.OUTLINE -> Contrast.over(Contrast.withAlpha(Color.WHITE, 0x1F), colKey)
            HighlightStyle.LIFT -> Contrast.over(Contrast.withAlpha(colAccent, 0x52), colKey)
        }

    private fun drawHighlight(canvas: Canvas, radius: Float = corner) {
        val lift = if (style == HighlightStyle.LIFT) LIFT_SCALE else 1f
        val cx = focusRect.centerX()
        val cy = focusRect.centerY()
        val hw = focusRect.width() / 2f * pressScale * lift
        val hh = focusRect.height() / 2f * pressScale * lift
        val pill = RectF(cx - hw, cy - hh, cx + hw, cy + hh)
        val r = min(radius, pill.height() / 2f) * lift

        if (style == HighlightStyle.GLOW || tv) {
            // TV mode always adds the halo, thicker, so the highlight reads from across the room
            val grow = u(if (tv) 3.5f else 2.5f)
            ringPaint.color = if (tv) Contrast.withAlpha(colAccent, 0x8C) else colGlow
            ringPaint.strokeWidth = u(if (tv) 3f else 2f)
            canvas.drawRoundRect(RectF(pill).apply { inset(-grow, -grow) }, r + grow, r + grow, ringPaint)
        }
        fillPaint.color = focusFill
        canvas.drawRoundRect(pill, r, r, fillPaint)

        when (style) {
            HighlightStyle.OUTLINE -> {
                // A ring just outside the key, with a hairline gap so it reads on any key colour
                val gapOut = u(2.5f)
                ringPaint.color = colAccent
                ringPaint.strokeWidth = u(2f)
                canvas.drawRoundRect(RectF(pill).apply { inset(-gapOut, -gapOut) }, r + gapOut, r + gapOut, ringPaint)
            }
            HighlightStyle.LIFT -> {
                // A short accent bar under the label, like a lit indicator
                val barW = pill.width() * 0.28f
                val barH = u(2.5f)
                val barY = pill.bottom - if (pill.height() < keyHeight * 0.9f) u(3.5f) else u(5f)
                fillPaint.color = colAccent
                canvas.drawRoundRect(cx - barW / 2, barY - barH, cx + barW / 2, barY, barH, barH, fillPaint)
            }
            else -> Unit
        }
    }

    private fun visibleBg(key: Key, focused: Boolean): Int {
        if (focused) return focusFill
        if (key.type == KeyType.DIGIT) return solid(colSpecialKey)
        val bg = backgroundFor(key) ?: return colBackdrop
        return if (Color.alpha(bg) == 255) bg else Contrast.over(bg, colBackdrop)
    }

    /** The accent colour if readable on this background, otherwise plain ink. */
    private fun accentOn(bg: Int): Int =
        if (Contrast.ratio(colAccent, bg) >= 3.0) colAccent else Contrast.inkFor(bg)

    private val undoOnDelete get() = heldShift && settings.undoCombo

    private fun drawKeyContent(canvas: Canvas, key: Key, rect: RectF, focused: Boolean) {
        val bg = visibleBg(key, focused)
        val ink = Contrast.inkFor(bg)
        val soft = Contrast.withAlpha(ink, 175)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = u(6f)

        when (key.type) {
            // The number pad's digits are bigger: its keys are a compact block, not a stretched row
            KeyType.CHAR -> drawLetter(canvas, displayLabel(key), cx, cy, ink, if (layer == Layer.NUMPAD) 22f else Design.TYPE_KEY)
            // The number row: smaller and a touch quieter than letters
            KeyType.DIGIT -> drawLabel(canvas, key.label, cx, cy, if (focused) ink else Contrast.withAlpha(ink, 215), 15.5f, 500)
            KeyType.GAP -> Unit
            KeyType.LAYER -> drawLabel(canvas, key.label, cx, cy, ink, Design.TYPE_KEY_LABEL, 600)
            KeyType.SPACE -> {
                if (selecting && settings.showHints) {
                    drawChips(canvas, rect, bg, selectChips())
                    return                                  // the chips replace the Y badge
                }
                if (accents?.open == true && settings.showHints) return     // the accent hints use the space bar
                val showName = settings.showLanguageLabel || SystemClock.uptimeMillis() < languageFlashUntil
                if (showName && labelFits(settings.language.label, rect)) {
                    // The language you're typing in, quietly, like phone keyboards do
                    drawLabel(canvas, settings.language.label, cx, cy, Contrast.withAlpha(ink, 120), Design.TYPE_KEY_LABEL, 500)
                } else {
                    // Clean: just a short line, as in 1.0
                    iconPaint.color = soft
                    iconPaint.style = Paint.Style.STROKE
                    canvas.drawLine(cx - rect.width() * 0.12f, cy + s * 0.5f, cx + rect.width() * 0.12f, cy + s * 0.5f, iconPaint)
                }
            }
            KeyType.SHIFT -> drawShift(canvas, cx, cy, s, focused, bg, ink)
            KeyType.DELETE -> if (undoOnDelete) drawUndo(canvas, cx, cy, s, ink) else drawBackspace(canvas, cx, cy, s, ink)
            KeyType.ENTER -> drawEnter(canvas, cx, cy, s, ink)
            KeyType.TOOL -> drawTool(canvas, key, rect, s, focused, bg, ink, soft)
            KeyType.CLIP -> drawClip(canvas, key, rect, ink)
            KeyType.EMPTY -> drawLabel(canvas, key.label, cx, cy, soft, 12.5f, 400)
        }

        if (settings.showHints) {
            key.hint?.takeIf { settings.hasSticks || !it.isStick }?.let { badges.drawInCorner(canvas, it, rect.right - u(3f), rect.top + u(3f)) }
        }
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
        hintPaint.color = Contrast.withAlpha(Contrast.inkFor(background), 210)
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
                val weight = if (key.best) 600 else 450
                val wordColour = if (focused || key.best) ink else soft
                textPaint.textSize = u(13.5f)
                applyFont(textPaint, weight)
                val badgeRoom = if (key.best && settings.showHints && settings.hasSticks) 2 * (badges.width(ControllerButton.R3) + u(3f)) else 0f
                val room = rect.width() - u(12f) - badgeRoom
                drawLabel(canvas, ellipsize(key.label, room), cx, cy, wordColour, 13.5f, weight)
            }
            else -> {
                val text = when (key.tool) {
                    // The count only if it fits next to the corner badge; the word never overlaps anything
                    Tool.SELECT -> if (selecting) toolLabelThatFits(rect, "Selecting · $selectedCount", "Selecting", "Sel.") else "Select"
                    Tool.PASTE -> clipPreview?.let { toolLabelThatFits(rect, "Paste  “$it”", "Paste") } ?: "Paste"
                    else -> key.label
                }
                drawLabel(canvas, text, cx, cy, colour, 12.5f, 500)
            }
        }
    }

    private fun drawClip(canvas: Canvas, key: Key, rect: RectF, ink: Int) {
        val pad = u(10f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = u(13f)
        textPaint.color = ink
        applyFont(textPaint, 400)
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
    private fun drawLetter(canvas: Canvas, text: String, cx: Float, cy: Float, colour: Int, sizeDp: Float = Design.TYPE_KEY) {
        textPaint.color = colour
        textPaint.textSize = u(sizeDp)
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
        textPaint.style = Paint.Style.FILL
        textPaint.strokeWidth = 0f
    }

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
    private fun toolLabelThatFits(rect: RectF, vararg options: String): String {
        textPaint.textSize = u(12.5f)
        applyFont(textPaint, 500)
        val badge = if (settings.showHints) u(18f) else u(6f)
        val room = rect.width() - 2 * badge
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
        if (clear) paint.setShadowLayer(u(2f), 0f, u(0.5f), Contrast.withAlpha(Color.BLACK, 0x99))
        else paint.clearShadowLayer()
    }

    private fun drawLabel(canvas: Canvas, text: String, cx: Float, cy: Float, colour: Int, sizeDp: Float, weight: Int) {
        textPaint.color = colour
        textPaint.textSize = u(sizeDp)
        applyFont(textPaint, weight)
        coverMissingGlyphs(textPaint, text)
        shadowForClear(textPaint)
        val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, cx, y, textPaint)
    }

    // =====================================================================
    // ICONS: one family. Same stroke, round caps and joins, fit a 2.4 s box.
    // =====================================================================

    /** Shift: outline = off, filled on tinted key = next letter / RT held, bar on solid key = caps lock. */
    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, s: Float, focused: Boolean, bg: Int, ink: Int) {
        val look = shiftLook
        iconPaint.color = if (!focused && look == ShiftState.ONCE) accentOn(bg) else ink
        iconPaint.style = if (look == ShiftState.OFF) Paint.Style.STROKE else Paint.Style.FILL_AND_STROKE
        val top = if (look == ShiftState.LOCK) cy - s * 1.25f else cy - s
        val arrow = Path().apply {
            moveTo(cx, top)
            lineTo(cx + s, top + s)
            lineTo(cx + s * 0.4f, top + s)
            lineTo(cx + s * 0.4f, top + s * 1.9f)
            lineTo(cx - s * 0.4f, top + s * 1.9f)
            lineTo(cx - s * 0.4f, top + s)
            lineTo(cx - s, top + s)
            close()
        }
        canvas.drawPath(arrow, iconPaint)
        if (look == ShiftState.LOCK) {
            iconPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                cx - s * 0.6f, top + s * 2.2f, cx + s * 0.6f, top + s * 2.5f,
                s * 0.15f, s * 0.15f, iconPaint
            )
        }
    }

    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float, s: Float, ink: Int) {
        iconPaint.color = ink
        iconPaint.style = Paint.Style.STROKE
        val shape = Path().apply {
            moveTo(cx - s * 1.2f, cy)
            lineTo(cx - s * 0.5f, cy - s * 0.8f)
            lineTo(cx + s * 1.2f, cy - s * 0.8f)
            lineTo(cx + s * 1.2f, cy + s * 0.8f)
            lineTo(cx - s * 0.5f, cy + s * 0.8f)
            close()
        }
        canvas.drawPath(shape, iconPaint)
        canvas.drawLine(cx, cy - s * 0.35f, cx + s * 0.7f, cy + s * 0.35f, iconPaint)
        canvas.drawLine(cx + s * 0.7f, cy - s * 0.35f, cx, cy + s * 0.35f, iconPaint)
    }

    /** Undo: a U-turn arrow pointing back, the shape every major app uses. */
    private fun drawUndo(canvas: Canvas, cx: Float, cy: Float, s: Float, ink: Int) {
        iconPaint.color = ink
        iconPaint.style = Paint.Style.STROKE
        val top = cy - s * 0.45f
        val bottom = cy + s * 0.85f
        val turn = Path().apply {
            moveTo(cx - s * 1.05f, top)                          // arrow tip
            lineTo(cx + s * 0.3f, top)                           // shaft
            cubicTo(cx + s * 1.25f, top, cx + s * 1.25f, bottom, cx + s * 0.3f, bottom)   // U-turn
            lineTo(cx - s * 0.3f, bottom)                        // tail
        }
        canvas.drawPath(turn, iconPaint)
        val head = Path().apply {
            moveTo(cx - s * 0.55f, top - s * 0.5f)
            lineTo(cx - s * 1.05f, top)
            lineTo(cx - s * 0.55f, top + s * 0.5f)
        }
        canvas.drawPath(head, iconPaint)
    }

    private fun drawEnter(canvas: Canvas, cx: Float, cy: Float, s: Float, ink: Int) {
        iconPaint.color = ink
        iconPaint.style = Paint.Style.STROKE
        val arrow = Path().apply {
            moveTo(cx + s, cy - s * 0.7f)
            lineTo(cx + s, cy + s * 0.3f)
            lineTo(cx - s, cy + s * 0.3f)
            moveTo(cx - s * 0.35f, cy - s * 0.35f)
            lineTo(cx - s, cy + s * 0.3f)
            lineTo(cx - s * 0.35f, cy + s * 0.95f)
        }
        canvas.drawPath(arrow, iconPaint)
    }

    private fun drawClock(canvas: Canvas, cx: Float, cy: Float, r: Float, colour: Int) {
        iconPaint.color = colour
        iconPaint.style = Paint.Style.STROKE
        canvas.drawCircle(cx, cy, r, iconPaint)
        canvas.drawLine(cx, cy, cx, cy - r * 0.55f, iconPaint)
        canvas.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.2f, iconPaint)
    }

    private fun drawGear(canvas: Canvas, cx: Float, cy: Float, r: Float, colour: Int) {
        iconPaint.color = colour
        iconPaint.style = Paint.Style.STROKE
        canvas.drawCircle(cx, cy, r * 0.62f, iconPaint)
        canvas.drawCircle(cx, cy, r * 0.22f, iconPaint)
        for (i in 0 until 8) {
            val a = i * PI / 4
            val c = cos(a).toFloat()
            val sn = sin(a).toFloat()
            canvas.drawLine(cx + c * r * 0.72f, cy + sn * r * 0.72f, cx + c * r * 1.05f, cy + sn * r * 1.05f, iconPaint)
        }
    }

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
