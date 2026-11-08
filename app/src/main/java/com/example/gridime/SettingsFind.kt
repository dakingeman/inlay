package com.example.gridime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Find a setting (1.3). Press Start in Settings, type a few letters with the keyboard below, and the best matches
 * show at the top; LB / RB pick one, Start or A on a result goes there and the row flashes.
 *
 * Why: people asked for things Inlay already had (the number row, see-through, bigger keys, R3 for a word), so the
 * problem was finding them. Search finds a row by its name, its hint, its group and tab, and the everyday words
 * people use for it ([WORDS]: "transparent" finds Clear, "bigger" finds Keyboard size).
 * SettingsFindTest makes sure every row on every tab can be found by its own name, and that every entry in [WORDS]
 * still names a real row.
 */
object SettingsFind {
    /** One row Settings can go to. [page] and [row] say where: the tab, and the row's place among its focusable rows. */
    data class Entry(val page: Int, val row: Int, val title: String, val where: String, val text: String)

    /**
     * The everyday words for a row, by its name as written (before any " · " detail). Keep them few and real: words someone would type when
     * looking for that thing. A row renamed here without its words is caught by SettingsFindTest.
     */
    val WORDS: Map<String, String> = mapOf(
        "What you're holding" to "device picture handheld controller gamepad remote vertical",
        "Button icons" to "xbox playstation nintendo switch symbols badges labels",
        "Your buttons" to "swap a b x y nintendo layout swapped wrong button retroid thor reversed opposite confirm select close back which button types right bottom typing button set up setup",
        "Try your buttons" to "test controller gamepad check buttons full screen what does button guide",
        "Stick speed" to "joystick fast slow move speed",
        "Right stick moves the text cursor" to "cursor caret joystick right stick",
        "TV mode" to "television tv floating docked couch big screen",
        "Hints on keys" to "badges hints labels corners combo tips rt chips shortcuts",
        "Theme" to "colours dark light black oled clear transparent see through background",
        "Collection" to "retro secret themes",
        "See-through" to "transparent clear opacity see through",
        "Highlight colour" to "accent colour color cursor selection",
        "Keyboard size" to "bigger larger smaller big small zoom scale size",
        "Key layout" to "grid classic stagger qwerty",
        "Number row" to "numbers digits 1234",
        "Split" to "split halves two windows thumbs",
        "Key shape" to "round square corners",
        "Highlight style" to "cursor outline solid glow lift",
        "Highlight motion" to "animation glide instant fluid speed",
        "Font" to "typeface letters text style",
        "Letter weight" to "bold thin thickness",
        "Language" to "languages english german french spanish russian keyboard",
        "Second language" to "languages bilingual switch",
        "Word suggestions" to "autocomplete predictions predict words",
        "Learn my words" to "dictionary personal names",
        "Hold for accents" to "accents umlaut diacritics special characters é ñ ü",
        "Automatic capitals" to "capital uppercase caps shift",
        "Double space for full stop" to "period full stop dot",
        "Smart keys" to "url web address email numbers .com",
        "Select all key" to "select all",
        "Undo shortcut" to "undo",
        "Remember copied text" to "clipboard history paste copy",
        "Vibration" to "haptics haptic buzz rumble vibrate",
        "Sound pack" to "sounds clicks audio noise",
        "Volume" to "loud quiet sound",
        "Navigation ticks" to "move sound ticks",
        "Play in silent mode" to "mute silent",
        "Turn on Inlay" to "enable activate default keyboard set up setup",
        "Switch keyboard" to "change keyboard gboard",
        "Replay the tour" to "tutorial help how to onboarding",
        "Get updates automatically" to "obtainium update updates",
        "Take the first suggested word" to "r3 word suggestion pick stick",
        "Move the text cursor" to "cursor caret arrows lb rb",
        "Close the keyboard" to "hide dismiss close b",
    )

    /** Splits text into lower-case words, ignoring punctuation. */
    private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}.]+")).filter { it.isNotEmpty() }

    /** Words that say nothing about which setting: "how do I make the keys bigger" searches "make keys bigger". */
    private val FILLER = setOf("how", "do", "i", "can", "the", "a", "an", "to", "my", "is", "it", "of", "in", "on", "for", "where", "what",
        "change", "set", "turn", "make", "get", "want", "with", "and", "or", "please", "settings", "setting", "option")

    /** The settings people look for most (from the questions asked on Reddit and GitHub), shown before anything is typed. */
    val POPULAR = listOf("Keyboard size", "Theme", "Number row", "Split", "Button icons", "Try your buttons")

    /** Rows Find leaves out: facts to read rather than things to change, and the test build's own rows. */
    fun searchable(row: SettingRow) = row !is InfoRow && !row.rowTitle.startsWith("Test build")

    /**
     * Does [typed] start [word], allowing one slip in longer words (a missed, extra, swapped or wrong letter), so
     * "vibraton" and "keybord" still find their settings. Cheap: a few dozen short words.
     */
    private fun starts(word: String, typed: String): Boolean {
        if (word.startsWith(typed)) return true
        if (typed.length < 4) return false
        val n = typed.length
        fun close(cand: String): Boolean {          // at most one edit between typed and cand
            if (kotlin.math.abs(cand.length - n) > 1) return false
            var i = 0; var j = 0; var edits = 0
            while (i < n && j < cand.length) {
                if (typed[i] == cand[j]) { i++; j++; continue }
                if (++edits > 1) return false
                when {
                    i + 1 < n && j + 1 < cand.length && typed[i] == cand[j + 1] && typed[i + 1] == cand[j] -> { i += 2; j += 2 }  // swapped
                    n > cand.length -> i++
                    n < cand.length -> j++
                    else -> { i++; j++ }
                }
            }
            return edits + (n - i) + (cand.length - j) <= 1
        }
        return (n - 1..n + 1).any { len -> len <= word.length && close(word.substring(0, len)) }
    }

    /**
     * The best matches for [query], best first. Every word typed (filler aside) has to start a word somewhere in the
     * entry; a match in the name counts most, then the everyday words, then the hint and where it lives.
     * Nothing typed yet: the [POPULAR] rows.
     */
    fun search(entries: List<Entry>, query: String, max: Int = 4): List<Entry> {
        val q = words(query).filter { it !in FILLER }.ifEmpty { words(query) }
        if (q.isEmpty()) return POPULAR.mapNotNull { p -> entries.firstOrNull { it.title.substringBefore(" · ") == p } }.take(max)
        return entries.mapNotNull { e ->
            // A name's detail after " · " ("Close the keyboard · leave select mode or the clipboard") counts like a hint
            val name = e.title.substringBefore(" · ")
            val title = words(name); val extra = words(WORDS[name] ?: ""); val rest = words(e.title.removePrefix(name) + " " + e.text + " " + e.where)
            var score = 0
            for (w in q) {
                score += when {
                    title.any { it.startsWith(w) } -> 6 + (if (title.firstOrNull()?.startsWith(w) == true) 2 else 0)
                    extra.any { it.startsWith(w) } -> 4
                    title.any { starts(it, w) } -> 4
                    extra.any { starts(it, w) } -> 3
                    rest.any { it.startsWith(w) } -> 1
                    else -> return@mapNotNull null
                }
            }
            if (name in POPULAR) score += 1                 // on a tie, the one people look for most
            e to score
        }.sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.page }.thenBy { it.first.row })
            .map { it.first }.distinctBy { it.title + it.where }.take(max)
    }
}

/**
 * Find's search box: a magnifier, then what you've typed and a blinking cursor. Empty, the cursor sits at the
 * start and "Find a setting" waits after it in the quiet grey, so it reads as a box to type in, not as text.
 * While the keyboard is up it carries your colour's ring (that's where the letters go).
 */
class FindField(context: Context) : android.view.View(context) {
    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = Design.TYPE_TITLE * resources.displayMetrics.scaledDensity }
    var query = ""; set(v) { field = v; caretOn = true; invalidate() }
    var typing = true; set(v) { field = v; invalidate() }
    private var caretOn = true
    private val blink = object : Runnable { override fun run() { caretOn = !caretOn; invalidate(); postDelayed(this, CARET_MS) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(blink, CARET_MS) }
    override fun onDetachedFromWindow() { removeCallbacks(blink); super.onDetachedFromWindow() }
    /** A short screen: the box is a little lower, so the matches still fit above the keyboard. */
    var compact = false; set(v) { if (field != v) { field = v; requestLayout() } }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), ((if (compact) 44f else 56f) * dp).roundToInt())

    override fun onDraw(canvas: Canvas) {
        val r = Design.RADIUS_ROW * dp; val h = height.toFloat(); val cy = h / 2f
        paint.style = Paint.Style.FILL; paint.color = SettingsStyle.surface
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, r, r, paint)
        if (typing) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = Design.STROKE_FOCUS * dp; paint.color = SettingsStyle.accent
            val i = paint.strokeWidth / 2f
            canvas.drawRoundRect(i, i, width - i, h - i, r - i, r - i, paint)
        }
        // the magnifier
        paint.style = Paint.Style.STROKE; paint.strokeWidth = Design.STROKE_ICON * dp; paint.strokeCap = Paint.Cap.ROUND
        paint.color = SettingsStyle.textSecondary
        Icons.magnifier(canvas, 27.4f * dp, cy, 7f * dp, paint)
        val x0 = 50f * dp; val base = cy - (text.descent() + text.ascent()) / 2f
        text.typeface = if (query.isEmpty()) SettingsStyle.regular else SettingsStyle.medium
        text.color = if (query.isEmpty()) SettingsStyle.textSecondary else SettingsStyle.textPrimary
        val shown = if (query.isEmpty()) "Find a setting" else query
        // the cursor: before the placeholder when empty, after the words when typing
        val caretX = if (query.isEmpty()) x0 else x0 + text.measureText(query) + 2f * dp
        canvas.drawText(shown, if (query.isEmpty()) x0 + 6f * dp else x0, base, text)
        if (typing && caretOn) {
            paint.style = Paint.Style.FILL; paint.color = SettingsStyle.accent
            canvas.drawRect(caretX, cy - 12f * dp, caretX + 2f * dp, cy + 12f * dp, paint)
        }
    }
    private companion object { const val CARET_MS = 530L }
}

/**
 * The Find panel at the top of Settings: the search box, then the matches (before anything is typed, the popular
 * ones). The one picked has your colour's ring, like a focused row. Two ways to use it, like the keyboard everywhere:
 * typing (keyboard up, LB / RB pick) and browsing (keyboard down with B, the D-pad goes through the matches).
 */
class FindPanel(context: Context) : LinearLayout(context) {
    private val dp = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).roundToInt()
    val field = FindField(context)
    private val label = TextView(context)
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    var results: List<SettingsFind.Entry> = emptyList(); private set
    var picked = 0; private set

    init {
        orientation = VERTICAL
        setPadding(px(Design.SPACE_XL), px(Design.SPACE_M), px(Design.SPACE_XL), px(Design.SPACE_M))
        setBackgroundColor(SettingsStyle.background)
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        label.apply {
            textSize = Design.TYPE_CAPTION; setTextColor(SettingsStyle.textSecondary)
            setPadding(px(Design.SPACE_L), px(Design.SPACE_M), px(Design.SPACE_L), px(Design.SPACE_XS))
        }
        addView(label)
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * Typing (keyboard up): the matches sit side by side as chips under the box, three at most, picked with LB / RB.
     * Browsing (keyboard down): a list, one match a line with where it lives, picked with the D-pad.
     */
    var chips = true

    /** How many chips fit side by side without cutting their names: three, two on a phone (5 Nov audit). */
    fun chipsThatFit(width: Int): Int = if (oneLine) 2 else ((width - paddingLeft - paddingRight) / (CHIP_MIN_DP * dp)).toInt().coerceIn(1, 3)

    /**
     * Fits the panel to [room] pixels of height above the keyboard. On a short screen (a small 4:3 or a vertical
     * handheld) the full panel didn't fit and the chips were cut away or drawn empty (5 Nov audit): there the box
     * gets lower, the "Popular" line goes and the padding tightens, so the chips always show.
     */
    fun fit(room: Int) {
        val natural = px(Design.SPACE_M) * 2 + px(56f) + px(LABEL_DP) + px(CHIP_DP)
        val compactNeed = px(Design.SPACE_XS) * 3 + px(44f) + px(CHIP_DP)
        val small = chips && room in 1 until natural
        // Shorter still (a small 4:3 screen, about 80 dp): the box and the chips share one line
        oneLine = chips && room in 1 until compactNeed
        orientation = if (oneLine) HORIZONTAL else VERTICAL
        gravity = if (oneLine) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
        field.compact = small
        label.visibility = if (small) GONE else VISIBLE
        val v = if (small) px(Design.SPACE_XS) else px(Design.SPACE_M)
        setPadding(px(Design.SPACE_XL), v, px(Design.SPACE_XL), v)
        field.layoutParams = if (oneLine) LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f) else LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        list.layoutParams = (if (oneLine) LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.3f) else LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)).apply {
            topMargin = if (small && !oneLine) px(Design.SPACE_XS) else 0
            marginStart = if (oneLine) px(Design.SPACE_S) else 0
        }
    }
    /** The box and the chips on one line (a very short screen): two chips beside the box. */
    private var oneLine = false

    private companion object {
        const val CHIP_MIN_DP = 150f    // a chip narrower than this cuts most names
        const val LABEL_DP = 30f        // the "Popular" line
        const val CHIP_DP = 44f
    }

    /** Shows [query] and its [found] rows; [keepPick] keeps the picked one (the same list, browsing). */
    fun show(query: String, found: List<SettingsFind.Entry>, keepPick: Boolean = false) {
        field.query = query
        list.orientation = if (chips) HORIZONTAL else VERTICAL
        results = found
        picked = if (keepPick) picked.coerceIn(0, (found.size - 1).coerceAtLeast(0)) else 0
        label.text = when {
            query.isBlank() -> "Popular"
            found.isEmpty() -> "Nothing found for \"${query.trim()}\". Try another word, like size or colour"
            else -> "Best matches"
        }
        list.removeAllViews()
        found.forEachIndexed { i, e -> list.addView(resultView(e, i == picked)) }
    }

    /** The next or previous match (LB / RB while typing, the D-pad while browsing). Stops at the ends. */
    fun pick(dir: Int): Boolean {
        val next = (picked + dir).coerceIn(0, (results.size - 1).coerceAtLeast(0))
        if (next == picked) return false
        picked = next
        for (i in 0 until list.childCount) list.getChildAt(i).isSelected = i == picked
        return true
    }

    /** The picked result looks like a focused row: raised, with a ring in your colour. */
    private fun pickedBackground() = android.graphics.drawable.StateListDrawable().apply {
        fun shape(fill: Int, ring: Boolean) = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = Design.RADIUS_ROW * dp; setColor(fill)
            if (ring) setStroke((Design.STROKE_FOCUS * dp).roundToInt(), SettingsStyle.accent)
        }
        setEnterFadeDuration(Design.MOTION_FADE.toInt()); setExitFadeDuration(Design.MOTION_CONTROL.toInt())
        addState(intArrayOf(android.R.attr.state_selected), shape(SettingsStyle.surfaceFocused, ring = true))
        addState(intArrayOf(), shape(android.graphics.Color.TRANSPARENT, ring = false))
    }

    private fun resultView(e: SettingsFind.Entry, on: Boolean) = TextView(context).apply {
        // One line each: the name, then where it lives (as a chip, just the name)
        val name = e.title.substringBefore(" · ")
        val t = if (chips) name else "$name   ${e.where}"
        text = android.text.SpannableString(t).apply {
            setSpan(android.text.style.ForegroundColorSpan(SettingsStyle.textSecondary), name.length, t.length, 0)
            setSpan(android.text.style.AbsoluteSizeSpan(Design.TYPE_CAPTION.roundToInt(), true), name.length, t.length, 0)
        }
        textSize = Design.TYPE_ROW; typeface = SettingsStyle.medium; setTextColor(SettingsStyle.textPrimary)
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        background = pickedBackground()
        setPadding(px(Design.SPACE_L), px(Design.SPACE_S), px(Design.SPACE_L), px(Design.SPACE_S))
        isSelected = on
        gravity = if (chips) Gravity.CENTER else Gravity.CENTER_VERTICAL
        layoutParams = if (chips) LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(Design.SPACE_XS) }
            else LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(Design.SPACE_XS) }
    }
}

/**
 * Find went to this row: a soft wash of your colour that rises, holds for a moment so the eye can land on it, and
 * fades slowly (the first one blinked by too fast to see, 4 Nov 2026).
 */
class RowFlash(private val row: android.view.View) {
    private companion object { const val RISE_MS = 220L; const val HOLD_MS = 450L; const val FADE_MS = 750L }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alpha = 0f
    private var anim: android.animation.Animator? = null
    fun start() {
        anim?.cancel()
        val rise = android.animation.ValueAnimator.ofFloat(0f, 1f).apply { duration = RISE_MS; interpolator = Design.EASE_OUT }
        val fade = android.animation.ValueAnimator.ofFloat(1f, 0f).apply { duration = FADE_MS; startDelay = HOLD_MS; interpolator = Design.EASE_STANDARD }
        listOf(rise, fade).forEach { it.addUpdateListener { a -> alpha = a.animatedValue as Float; row.invalidate() } }
        anim = android.animation.AnimatorSet().apply { playSequentially(rise, fade); start() }
    }
    fun draw(canvas: Canvas) {
        if (alpha <= 0f) return
        paint.color = Contrast.withAlpha(SettingsStyle.accent, (Design.ALPHA_TINT_SOFT * alpha).roundToInt())
        val r = Design.RADIUS_ROW * row.resources.displayMetrics.density
        canvas.drawRoundRect(RectF(0f, 0f, row.width.toFloat(), row.height.toFloat()), r, r, paint)
    }
}
