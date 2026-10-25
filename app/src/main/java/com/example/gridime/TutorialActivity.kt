package com.example.gridime

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A short first-run tour: six pages, about a minute. The same screen also shows
 * "What's new" after an update (see [WhatsNew]), with that update's highlights as its pages.
 *
 * The screen is laid out like a real app: the words at the top, a text box and the keyboard
 * at the bottom, full width, exactly where and how the keyboard appears in use. The keyboard
 * acts out each page into the text box (typing, word jumps, selecting, the clipboard, themes).
 *
 * Controller: A or Right = next, B or Left = back, Start = skip the tour (or close What's new).
 * Up / down scroll the words if a page is ever taller than the screen.
 * Opened automatically the first time Settings opens, and from About → Replay the tour.
 */
class TutorialActivity : Activity(), KeyGridView.Listener {

    private lateinit var settings: KeyboardSettings
    private lateinit var feedback: Feedback
    private lateinit var dots: LinearLayout
    private lateinit var eyebrow: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var rows: LinearLayout
    private lateinit var rowsPane: View
    private lateinit var words: LinearLayout
    private lateinit var field: DemoFieldView
    private lateinit var demo: KeyGridView
    private lateinit var controlBar: ControlBar
    private lateinit var wordsScroll: ScrollView
    private lateinit var gauge: StickGaugeView
    private lateinit var screenInScreen: ScreenInScreenView
    private val rowTexts = mutableListOf<TextView>()

    /** Opened as "What's new" instead of the tour. */
    private val whatsNew by lazy { intent.getBooleanExtra(EXTRA_WHATS_NEW, false) }
    private val handler = Handler(Looper.getMainLooper())
    private var page = 0

    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * dp).roundToInt()

    /** One page: a title, one plain sentence, up to five button rows, and what the keyboard acts out. */
    private class Page(val title: String, val body: String, val rows: List<Pair<String, String>>, val demo: Demo)
    private enum class Demo { WANDER, TYPE, SHIFT_AND_SYMBOLS, HOLD_RT, EDIT, YOURS, RIGHT_STICK, FASTER, THIS_SCREEN }

    /** What the "select" button is called on this controller, so the words match the badge. */
    private val selectName get() = when (settings.buttonStyle) {
        ButtonStyle.XBOX -> "View"
        ButtonStyle.PLAYSTATION -> "Create"
        ButtonStyle.SWITCH -> "Minus"
    }

    private val pages: List<Page> by lazy { if (whatsNew) whatsNewPages else tourPages }

    /** The highlights of [WhatsNew.VERSION]. One to three pages, each acted out like the tour. */
    private val whatsNewPages: List<Page> by lazy {
        listOf(
            Page("The right stick moves the cursor",
                "Push it sideways to move through your text. The further you push, the faster it goes." +
                    if (settings.rightStick) "" else " It's switched off right now: turn it on in Settings → Typing.",
                listOf(
                    "Move the text cursor" to "{R3}",
                    "Jump whole words" to "{R2} + {R3}"
                ), Demo.RIGHT_STICK),
            Page("Everything is faster",
                "Every press lands the moment you make it, and the left stick reacts to a lighter push.",
                listOf(
                    "Hold to delete, faster" to "{X}",
                    "Hold to move, faster" to "{L1} {R1}"
                ), Demo.FASTER),
            Page("And this screen",
                "Yes, one of the new things is the screen that shows you the new things. It appears once per update, then gets out of your way. See it again in About.",
                emptyList(), Demo.THIS_SCREEN)
        )
    }

    private val tourPages: List<Page> by lazy {
        listOf(
            Page("Welcome to Inlay",
                "Type anywhere with your controller, no touch screen needed. This tour shows the buttons and takes about a minute.",
                emptyList(), Demo.WANDER),
            Page("Move and type",
                "Move the highlight with the D-pad or the left stick, then press a button.",
                listOf(
                    "Type the highlighted key" to "{A}",
                    "Delete · hold to speed up" to "{X}",
                    "Space" to "{Y}",
                    "Enter · Search · Go" to "{START}",
                    "Close the keyboard" to "{B}"
                ), Demo.TYPE),
            Page("Capitals, numbers and symbols",
                "Tap the triggers to switch what the keys show.",
                listOf(
                    "Shift · tap twice for caps lock" to "{R2}",
                    "Numbers and symbols" to "{L2}",
                    "Keyboard size" to "{L3}"
                ), Demo.SHIFT_AND_SYMBOLS),
            Page("Hold RT for more",
                "While you hold RT, tips appear on the keyboard, so you don't have to remember these.",
                listOf(
                    "One capital letter" to "{R2} + {A}",
                    "Jump a whole word" to "{R2} + {L1} {R1}",
                    "Accent the last letter (é, ñ, å…)" to "{R2} + {Y}",
                    "Undo" to "{R2} + {X}"
                ), Demo.HOLD_RT),
            Page("Select, copy and paste",
                "Press $selectName to start selecting. Cut, Copy and Paste are in the top row, and the clock keeps your last six copies.",
                listOf(
                    "Move the text cursor" to "{L1} {R1}",
                    "Start selecting" to "{SELECT}",
                    "Grow the selection" to "{L1} {R1}",
                    "Select everything" to "{SELECTx2}"
                ), Demo.EDIT),
            Page("Make it yours",
                "Themes, sound packs, languages and Xbox, PlayStation or Switch button icons are all in Settings. Replay this tour any time from About.",
                emptyList(), Demo.YOURS)
        )
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.init(this)
        settings = KeyboardSettings.load(this)
        SettingsStyle.accent = settings.accent
        volumeControlStream = AudioManager.STREAM_MUSIC
        feedback = Feedback(this).apply { update(settings) }
        // Our own black from the very first frame, never the system theme's grey
        window.setBackgroundDrawable(ColorDrawable(SettingsStyle.background))
        @Suppress("DEPRECATION")
        window.statusBarColor = SettingsStyle.background
        @Suppress("DEPRECATION")
        window.navigationBarColor = SettingsStyle.background
        setContentView(buildScreen())
        goFullScreen()
        // Turning the device rebuilds the screen; stay on the same page
        show(savedInstanceState?.getInt(KEY_PAGE) ?: 0, animate = false)
    }

    /**
     * The tour fills the screen like a game does: no status bar, no navigation bar
     * (a swipe from the edge brings them back for a moment). A notch or camera cut-out
     * still gets padding, so nothing hides behind it.
     */
    private fun goFullScreen() {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideBars()
        val content = findViewById<View>(android.R.id.content)
        content.setOnApplyWindowInsetsListener { v, insets ->
            val cut = insets.displayCutout
            v.setPadding(cut?.safeInsetLeft ?: 0, cut?.safeInsetTop ?: 0, cut?.safeInsetRight ?: 0, cut?.safeInsetBottom ?: 0)
            insets
        }
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, page)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        feedback.release()
        super.onDestroy()
    }

    /** Like Settings: leaving with Home closes the tour completely, so nothing sits in Recents. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finishAndRemoveTask()
    }

    // =====================================================================
    // SCREEN
    // =====================================================================
    /** Landscape with room for two columns of words: handhelds, tablets and TVs. */
    private val wide get() = resources.configuration.screenWidthDp >= 600 &&
        resources.configuration.screenWidthDp > resources.configuration.screenHeightDp

    private val onTv get() = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SettingsStyle.background)
            // The controller never "focuses" anything here (that is what drew a grey box
            // around the words): the whole screen listens for buttons instead.
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            defaultFocusHighlightEnabled = false
            if (onTv) {
                // TVs cut off the outer 5 %: keep everything inside the safe area
                val w = resources.displayMetrics.widthPixels
                val h = resources.displayMetrics.heightPixels
                setPadding((w * TV_SAFE).roundToInt(), (h * TV_SAFE).roundToInt(), (w * TV_SAFE).roundToInt(), (h * TV_SAFE).roundToInt())
            }
        }
        dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, px(Design.SPACE_L), 0, 0)
        }
        root.addView(dots, lpMatchWrap())

        // ---- The words ----
        eyebrow = TextView(this).apply {
            textSize = Design.TYPE_SECTION
            letterSpacing = 0.12f
            typeface = SettingsStyle.medium
            setTextColor(SettingsStyle.accent)
        }
        title = TextView(this).apply {
            textSize = Design.TYPE_DISPLAY
            typeface = SettingsStyle.medium
            setTextColor(SettingsStyle.textPrimary)
            setLineSpacing(0f, 1.1f)
        }
        body = TextView(this).apply {
            textSize = Design.TYPE_ROW
            setTextColor(SettingsStyle.textSecondary)
            setLineSpacing(0f, 1.3f)
            maxWidth = px(BODY_MAX_WIDTH_DP)       // comfortable line length
        }
        words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(eyebrow, lpMatchWrap())
            addView(title, lpMatchWrap(topMargin = Design.SPACE_S))
            addView(body, lpMatchWrap(topMargin = Design.SPACE_M))
        }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- The stage: a text box and the real keyboard, full width, as in any app ----
        field = DemoFieldView(this)
        gauge = StickGaugeView(this).apply { accent = settings.accent; style = settings.buttonStyle; visibility = View.GONE }
        screenInScreen = ScreenInScreenView(this).apply { accent = settings.accent; visibility = View.GONE }
        demo = KeyGridView(this).apply {
            isFocusable = false
            listener = this@TutorialActivity
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (onTv) setTvMode(true)              // on a TV the keyboard is the compact TV panel, with its own text line
        }
        val stage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(field, lpMatchWrap().apply {
                height = px(FIELD_HEIGHT_DP)
                if (!wide) { marginStart = px(Design.SPACE_XXL); marginEnd = px(Design.SPACE_XXL) }
            })
            addView(demo, lpMatchWrap(topMargin = Design.SPACE_M))
            addView(gauge, lpMatchWrap(topMargin = Design.SPACE_M))
            addView(screenInScreen, lpMatchWrap())
        }
        if (onTv) field.visibility = View.GONE

        val side = px(Design.SPACE_XXL)
        words.addView(rows, lpMatchWrap(topMargin = Design.SPACE_XL))
        rowsPane = rows
        val wordsPane = noFocusScroll(words)
        wordsScroll = wordsPane
        if (wide) {
            // Side by side: the words on the left, the text box and keyboard on the right.
            // The keyboard is a scaled-down copy of the real one, so keys and hints keep their shape.
            demo.keepProportions = true
            val split = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(side, px(Design.SPACE_M), side, px(Design.SPACE_M))
                addView(wordsPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, WORDS_SHARE))
                addView(stage, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f - WORDS_SHARE)
                    .apply { marginStart = side })
            }
            root.addView(split, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            // Portrait: the words in one column (scrolls if a page is long), keyboard at the bottom, full width
            wordsPane.setPadding(side, px(Design.SPACE_M), side, px(Design.SPACE_M))
            root.addView(wordsPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(stage, lpMatchWrap())
        }
        controlBar = ControlBar(this)
        root.addView(controlBar, lpMatchWrap())
        root.isSoundEffectsEnabled = false
        return root
    }

    /** A scroll area that never takes focus (so Android never draws its grey focus box). */
    private fun noFocusScroll(content: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        isFocusable = false
        defaultFocusHighlightEnabled = false
        addView(FrameLayout(context).apply {
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        })
    }

    private fun lpMatchWrap(topMargin: Float = 0f) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { if (topMargin > 0f) this.topMargin = px(topMargin) }

    private fun show(index: Int, animate: Boolean, direction: Int = 1) {
        page = index
        val p = pages[index]
        eyebrow.text = if (whatsNew) "NEW IN ${WhatsNew.VERSION} · ${index + 1} OF ${pages.size}" else "${index + 1} OF ${pages.size}"
        title.text = p.title
        body.text = p.body
        buildRows(p.rows)
        rowsPane.visibility = if (p.rows.isEmpty()) View.GONE else View.VISIBLE
        refreshDots()
        controlBar.show(settings.buttonStyle, listOfNotNull(
            if (index < pages.lastIndex) "{A} Next" else if (whatsNew) "{A} Got it" else "{A} Done",
            if (index > 0) "{B} Back" else null,
            if (index < pages.lastIndex) (if (whatsNew) "{START} Close" else "{START} Skip the tour") else null
        ))
        startDemo(p.demo)
        wordsScroll.scrollTo(0, 0)
        fitWhenLaidOut()
        if (animate) {
            words.alpha = 0f
            words.translationX = px(12f * direction).toFloat()
            words.animate().alpha(1f).translationX(0f).setDuration(Design.MOTION_PAGE)
                .setInterpolator(Design.EASE_OUT).start()
        }
    }

    /**
     * The button rows. Badges sit in their own column, sized to the widest badge group on the
     * page, then a full 24 dp of air, then the words. Every row is at least 44 dp tall.
     */
    private fun buildRows(list: List<Pair<String, String>>) {
        rows.removeAllViews()
        rowTexts.clear()
        if (list.isEmpty()) return
        val parsed = list.map { (what, buttons) -> what to HintText.parse(buttons) }
        val painter = BadgePainter(dp).apply {
            style = settings.buttonStyle; scale = BADGE_SCALE
            gapBadgeToBadge = BADGE_GAP; gapBadgeToText = TEXT_GAP
        }
        val joiner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_CAPTION, resources.displayMetrics)
        }
        val column = parsed.maxOf { painter.measureLine(it.second, joiner) }.roundToInt() + 2 + px(Design.SPACE_XL)
        parsed.forEachIndexed { i, (what, parts) ->
            rows.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = px(ROW_HEIGHT_DP)
                addView(BadgeLineView(context, parts, settings.buttonStyle, Design.TYPE_CAPTION,
                    SettingsStyle.textSecondary, badgeScale = BADGE_SCALE, gapBadgeToBadge = BADGE_GAP, gapBadgeToText = TEXT_GAP),
                    LinearLayout.LayoutParams(column, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(context).apply {
                    text = what
                    textSize = Design.TYPE_ROW
                    setTextColor(SettingsStyle.textPrimary)
                    setLineSpacing(0f, 1.2f)
                    rowTexts += this
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }, lpMatchWrap(topMargin = if (i > 0) Design.SPACE_XS else 0f))
        }
    }

    /**
     * Every row must be visible without scrolling: the controller can't reach what's below
     * the edge. On short screens (a 1080p handheld with a long page), the words shrink a
     * step at a time until the page fits. Rows keep their 44 dp height and their badges.
     */
    /** Measures once the screen has its real size (on the very first page it doesn't yet). */
    private fun fitWhenLaidOut() {
        if (wordsScroll.height > 0 && words.width > 0) { fitWords(); return }
        wordsScroll.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (wordsScroll.height <= 0 || words.width <= 0) return
                wordsScroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                fitWords()
            }
        })
    }

    private fun fitWords() {
        applyTextScale(1f)
        var scale = 1f
        repeat(FIT_STEPS) {
            val room = wordsScroll.height - wordsScroll.paddingTop - wordsScroll.paddingBottom
            if (room <= 0 || words.width <= 0) return
            words.measure(View.MeasureSpec.makeMeasureSpec(words.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            if (words.measuredHeight <= room) return
            scale -= FIT_STEP
            applyTextScale(scale)
        }
    }

    private fun applyTextScale(f: Float) {
        title.textSize = Design.TYPE_DISPLAY * f
        body.textSize = Design.TYPE_ROW * f
        rowTexts.forEach { it.textSize = Design.TYPE_ROW * f }
    }

    /** Progress: one dot per page, the current one a wider accent pill. */
    private fun refreshDots() {
        dots.removeAllViews()
        pages.indices.forEach { i ->
            dots.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Design.RADIUS_PILL
                    setColor(if (i == page) SettingsStyle.accent else SettingsStyle.track)
                }
            }, LinearLayout.LayoutParams(if (i == page) px(20f) else px(6f), px(6f)).apply {
                marginStart = px(3f)
                marginEnd = px(3f)
            })
        }
    }

    // =====================================================================
    // THE KEYBOARD ACTS OUT EACH PAGE (silently: no sounds, no vibration)
    // =====================================================================
    private var typed = ""

    /** What the text box shows: text before the cursor, selected text, text after. */
    private fun showText(before: String, selected: String = "", after: String = "") {
        field.set(before, selected, after)
        demo.setPreview(before, selected, after)     // the TV panel has its own text line
    }

    private fun startDemo(demoKind: Demo) {
        handler.removeCallbacksAndMessages(null)
        // Normal size, so the words always have room above it; hints on, as a new user sees them
        val base = settings.copy(showHints = true, comboHints = true, sizeLevel = 0)
        demo.applySettings(base)
        demo.setField(FieldKind.TEXT)
        demo.closeClips(focusOnHistoryButton = false)
        demo.resetShift()
        demo.setHeldShift(false)
        demo.setSelecting(false, 0)
        demo.setClips(emptyList())
        typed = ""
        field.accent = settings.accent
        field.visibility = if (onTv || demoKind == Demo.THIS_SCREEN) View.GONE else View.VISIBLE
        demo.visibility = if (demoKind == Demo.THIS_SCREEN) View.GONE else View.VISIBLE
        gauge.visibility = if (demoKind == Demo.RIGHT_STICK) View.VISIBLE else View.GONE
        screenInScreen.visibility = if (demoKind == Demo.THIS_SCREEN) View.VISIBLE else View.GONE
        gauge.push = 0f
        showText("")
        // Android's "Remove animations" is on: one still moment per page, nothing moving
        val moving = WhatsNew.motionAllowed()
        val path = listOf(1 to 0, 1 to 0, 0 to -1, -1 to 0, 0 to 1, 1 to 0)
        var step = 0
        fun every(ms: Long, action: () -> Long?) {
            // action returns how long to wait before the next step, or null for the default
            if (!moving) return
            val tick = object : Runnable {
                override fun run() { handler.postDelayed(this, action() ?: ms) }
            }
            handler.postDelayed(tick, ms)
        }
        when (demoKind) {
            Demo.WANDER -> every(700) {
                val (dx, dy) = path[step++ % path.size]
                demo.moveFocus(dx, dy); null
            }
            Demo.TYPE -> {
                // Types a real sentence key by key, pauses, clears, and goes again
                val sentence = "hello from inlay"
                if (!moving) showText(sentence)
                var at = 0
                every(STEP_MS) {
                    if (at >= sentence.length) { at = 0; typed = ""; showText(""); return@every PAUSE_MS }
                    when (demo.stepTowards(sentence[at].toString())) {
                        true -> { demo.pressSelected(); at++; if (at == sentence.length) PAUSE_MS else PRESS_MS }
                        false -> null
                        null -> { at++; null }      // not on this keyboard (never, for plain letters)
                    }
                }
            }
            Demo.SHIFT_AND_SYMBOLS -> {
                showText("Hi! 2 + 2 = 4")
                every(1300) {
                    when (step++ % 4) {
                        0 -> demo.cycleShift()           // shift
                        1 -> demo.cycleShift()           // caps lock
                        2 -> { demo.cycleShift(); demo.toggleLayer() }   // off, then symbols
                        else -> demo.toggleLayer()       // back to letters
                    }; null
                }
            }
            Demo.HOLD_RT -> {
                // RT held: the tips show, and the cursor jumps a word at a time
                val wordsOnScreen = listOf("jump", " a", " whole", " word", " at", " once")
                demo.setHeldShift(true)
                showText("", "", wordsOnScreen.joinToString(""))
                var at = 0
                every(900) {
                    at = (at + 1) % (wordsOnScreen.size + 1)
                    showText(wordsOnScreen.take(at).joinToString(""), "", wordsOnScreen.drop(at).joinToString(""))
                    if (at == wordsOnScreen.size) PAUSE_MS else null
                }
            }
            Demo.EDIT -> {
                // The selection grows a word at a time, then Copy, then the clipboard opens with it on top
                val wordsOnScreen = listOf("select", " this", " text", " and", " copy", " it")
                val earlier = listOf("metroid prime", "github.com/dakingeman/inlay", "save slot 3")
                showText("", "", wordsOnScreen.joinToString(""))
                if (!moving) {
                    showText("", wordsOnScreen.take(3).joinToString(""), wordsOnScreen.drop(3).joinToString(""))
                    demo.setSelecting(true, wordsOnScreen.take(3).joinToString("").length)
                }
                var at = 0
                every(800) {
                    when {
                        at < wordsOnScreen.size -> {
                            at++
                            val chosen = wordsOnScreen.take(at).joinToString("")
                            showText("", chosen, wordsOnScreen.drop(at).joinToString(""))
                            demo.setSelecting(true, chosen.length)
                            if (at == wordsOnScreen.size) 1100L else null
                        }
                        at == wordsOnScreen.size -> {           // "Copy": the clipboard opens, the copy on top
                            at++
                            demo.setSelecting(false, 0)
                            showText(wordsOnScreen.joinToString(""))
                            demo.setClips(listOf(wordsOnScreen.joinToString("")) + earlier)
                            demo.toggleClips()
                            CLIPS_MS
                        }
                        else -> {                               // back to the letters and start over
                            at = 0
                            demo.closeClips(focusOnHistoryButton = false)
                            demo.setClips(emptyList())
                            showText("", "", wordsOnScreen.joinToString(""))
                            900L
                        }
                    }
                }
            }
            Demo.RIGHT_STICK -> {
                // Rest, a light push (slow steps), a full push (fast) to the end, rest, then fast back
                val text = "metroid prime"
                var at = 0
                var phase = 0
                fun place() = showText(text.take(at), "", text.drop(at))
                place()
                if (!moving) { at = 7; place(); gauge.push = 1f }
                every(STICK_REST_MS) {
                    when (phase) {
                        0 -> { phase = 1; gauge.push = 0.4f; STICK_SLOW_MS }                 // light push begins
                        1 -> { at++; place(); if (at >= 4) { phase = 2; gauge.push = 1f }; STICK_SLOW_MS }
                        2 -> { at++; place(); if (at >= text.length) { phase = 3; gauge.push = 0f; STICK_REST_MS } else STICK_FAST_MS }
                        3 -> { phase = 4; gauge.push = -1f; STICK_FAST_MS }                 // full push back
                        else -> { at--; place(); if (at <= 0) { phase = 0; gauge.push = 0f; STICK_REST_MS } else STICK_FAST_MS }
                    }
                }
            }
            Demo.FASTER -> {
                // The same typing as the tour, at a fast typist's pace: every press lands at once
                val sentence = "fast and snappy"
                if (!moving) showText(sentence)
                var at = 0
                every(FAST_STEP_MS) {
                    if (at >= sentence.length) { at = 0; typed = ""; showText(""); return@every PAUSE_MS }
                    when (demo.stepTowards(sentence[at].toString())) {
                        true -> { demo.pressSelected(); at++; if (at == sentence.length) PAUSE_MS else FAST_PRESS_MS }
                        false -> null
                        null -> { at++; null }
                    }
                }
            }
            Demo.THIS_SCREEN -> Unit      // the screen-in-screen drawing runs by itself
            Demo.YOURS -> {
                // Themes and highlight colours change, and the button icons switch Xbox → PlayStation → Switch
                showText("")
                every(1500) {
                    step++
                    val tones = Palette.tones
                    val styles = ButtonStyle.values()
                    demo.applySettings(base.copy(
                        toneIndex = (settings.toneIndex + step) % tones.size,
                        accentIndex = (settings.accentIndex.coerceAtLeast(0) + step * 3) % Palette.accents.size,
                        buttonStyle = styles[(settings.buttonStyle.ordinal + step) % styles.size]))
                    null
                }
            }
        }
    }

    // ---- What the demo keyboard types lands in the text box ----------------
    override fun onText(text: String) { typed += text; showText(typed) }
    override fun onDelete() { typed = typed.dropLast(1); showText(typed) }
    override fun onEnter() = Unit
    override fun onTool(tool: Tool) = Unit
    override fun onClip(index: Int) = Unit

    // =====================================================================
    // CONTROLLER
    // =====================================================================
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> next()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> back()
            KeyEvent.KEYCODE_BUTTON_START -> finishTour()
            KeyEvent.KEYCODE_DPAD_UP -> wordsScroll.smoothScrollBy(0, -px(SCROLL_DP))
            KeyEvent.KEYCODE_DPAD_DOWN -> wordsScroll.smoothScrollBy(0, px(SCROLL_DP))
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun next() {
        if (page == pages.lastIndex) return finishTour()
        feedback.move()
        show(page + 1, animate = true, direction = 1)
    }

    /** Back a page; on the first page, B leaves the tour (and counts as seen). */
    private fun back() {
        if (page == 0) return finishTour()
        feedback.back()
        show(page - 1, animate = true, direction = -1)
    }

    private var finishing = false

    /**
     * Done or skipped: remember it, so it doesn't open by itself again. Finishing the tour
     * also counts as having seen this update's What's new (a new user already saw it all).
     */
    private fun finishTour() {
        if (finishing) return
        finishing = true
        val saved = KeyboardSettings.load(this)
        (if (whatsNew) saved.copy(whatsNewSeen = WhatsNew.VERSION)
        else saved.copy(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION)).save(this)
        feedback.confirm()
        handler.removeCallbacksAndMessages(null)
        dots.postDelayed({ finish() }, CLOSE_DELAY_MS)
    }

    companion object {
        /** Open this screen as "What's new" instead of the tour. */
        const val EXTRA_WHATS_NEW = "whats_new"
        private const val FIT_STEPS = 3
        private const val FIT_STEP = 0.08f
        private const val SCROLL_DP = 80f
        private const val STICK_REST_MS = 900L     // the stick resting
        private const val STICK_SLOW_MS = 300L     // light push: a step at a time
        private const val STICK_FAST_MS = 55L      // full push
        private const val FAST_STEP_MS = 45L       // a fast typist's D-pad
        private const val FAST_PRESS_MS = 110L
        const val KEY_PAGE = "page"
        const val CLOSE_DELAY_MS = 140L
        const val TV_SAFE = 0.05f
        const val BADGE_SCALE = 1.45f
        const val BADGE_GAP = 6f            // between two badges in one group (dp, before scaling)
        const val TEXT_GAP = 8f             // between a badge and a "+" (dp, before scaling)
        const val ROW_HEIGHT_DP = 44f       // comfortable rows, like a list
        const val BODY_MAX_WIDTH_DP = 520f
        const val WORDS_SHARE = 0.36f       // of the width, in the side-by-side layout
        const val FIELD_HEIGHT_DP = 44f
        const val STEP_MS = 110L            // one D-pad step
        const val PRESS_MS = 260L           // a breath after each letter
        const val PAUSE_MS = 1800L          // read the result before it starts over
        const val CLIPS_MS = 2400L          // the clipboard stays open this long
    }
}

/**
 * A text box for the tour: shows what the demo keyboard types, with a blinking cursor
 * and the selection in your highlight colour, drawn the same way as the TV preview line.
 */
class DemoFieldView(context: android.content.Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, Design.STROKE_HAIRLINE * dp)
        color = Design.EDGE_CARD
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_ROW, resources.displayMetrics)
        typeface = SettingsStyle.medium
    }
    private var before = ""
    private var selected = ""
    private var after = ""
    private var shownAt = 0L
    var accent: Int = SettingsStyle.accent

    fun set(before: String, selected: String, after: String) {
        this.before = before
        this.selected = selected
        this.after = after
        shownAt = android.os.SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val box = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val r = Design.RADIUS_ROW * dp
        fill.color = SettingsStyle.surface
        canvas.drawRoundRect(box, r, r, fill)
        canvas.drawRoundRect(box, r, r, edge)
        val padX = Design.SPACE_L * dp
        val baseline = box.centerY() - (text.descent() + text.ascent()) / 2f
        var x = box.left + padX
        if (before.isEmpty() && selected.isEmpty() && after.isEmpty()) {
            text.color = SettingsStyle.textSecondary
            canvas.drawText("Type with your controller", x, baseline, text)
        }
        text.color = SettingsStyle.textPrimary
        canvas.drawText(before, x, baseline, text)
        x += text.measureText(before)
        val inset = (height - (text.descent() - text.ascent())) / 2f - 4 * dp
        if (selected.isNotEmpty()) {
            val w = text.measureText(selected)
            fill.color = Contrast.withAlpha(accent, 0x59)
            canvas.drawRoundRect(x - 2 * dp, box.top + inset, x + w + 2 * dp, box.bottom - inset, 4 * dp, 4 * dp, fill)
            canvas.drawText(selected, x, baseline, text)
            x += w
        } else {
            // Blinks like a real cursor, and always shows right after a change
            val since = android.os.SystemClock.uptimeMillis() - shownAt
            if (since < 400 || (since / 530) % 2 == 0L) {
                fill.color = accent
                canvas.drawRoundRect(x + 1 * dp, box.top + inset, x + 3 * dp, box.bottom - inset, 1 * dp, 1 * dp, fill)
            }
            x += 4 * dp
            postInvalidateDelayed(120)
        }
        text.color = SettingsStyle.textSecondary
        canvas.drawText(after, x, baseline, text)
    }
}
