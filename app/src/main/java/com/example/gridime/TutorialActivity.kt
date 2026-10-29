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
    private lateinit var eyebrowCount: TextView
    private lateinit var eyebrowRow: LinearLayout
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var rows: LinearLayout
    private lateinit var rowsPane: View
    private lateinit var words: LinearLayout
    private lateinit var field: DemoFieldView
    private lateinit var demo: KeyGridView
    private lateinit var controlBar: ControlBar
    private var wordsScroll: ScrollView? = null
    /** The timing of the demo's loop, shown on the keyboard window's edge (StagePanel). */
    private val loop = LoopClock()
    /** NEW: the device you're holding, lighting up the buttons the keyboard acts out. */
    private lateinit var picture: DevicePictureView
    private lateinit var stage: LinearLayout
    private lateinit var panel: StagePanel
    private lateinit var pictureCol: LinearLayout
    private var stageRow: LinearLayout? = null
    /** Small screens: the words and the device beside them. */
    private var header: LinearLayout? = null
    private val rowTexts = mutableListOf<TextView>()
    private val rowCells = mutableListOf<View>()
    private var shownRows: List<Pair<String, String>> = emptyList()

    /** Opened as "What's new" instead of the tour. */
    private val whatsNew by lazy { intent.getBooleanExtra(EXTRA_WHATS_NEW, false) }
    private val handler = Handler(Looper.getMainLooper())
    private var page = 0

    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * dp).roundToInt()

    /** One page: a title, one plain sentence, up to five button rows, and what the keyboard acts out. */
    private class Page(val title: String, val body: String, val rows: List<Pair<String, String>>, val demo: Demo,
                       val audience: For = For.ALL, val sticks: Boolean? = null)
    private enum class Demo { WANDER, TYPE, SHIFT_AND_SYMBOLS, HOLD_RT, EDIT, YOURS,
        WORDS, ACCENTS, ACCENTS_AND_WORDS, NUMBER_ROW, DEVICES, SHIFT_REMOTE, SMART, SOUNDS }

    /** What the bottom face button (A on Xbox) is called on this controller, so the words match the badge. */
    private val aName get() = when (settings.buttonStyle) {
        ButtonStyle.XBOX -> "A"
        ButtonStyle.PLAYSTATION -> "Cross"
        ButtonStyle.SWITCH -> "B"
    }

    /** What the right trigger is called on this controller. */
    private val rtName get() = when (settings.buttonStyle) {
        ButtonStyle.XBOX -> "RT"
        ButtonStyle.PLAYSTATION -> "R2"
        ButtonStyle.SWITCH -> "ZR"
    }

    /** What the "select" button is called on this controller, so the words match the badge. */
    private val selectName get() = when (settings.buttonStyle) {
        ButtonStyle.XBOX -> "View"
        ButtonStyle.PLAYSTATION -> "Create"
        ButtonStyle.SWITCH -> "Minus"
    }

    /**
     * What the demos type: classic games people emulate, typed the way you'd search a game library.
     * They follow the button icons you picked, so PlayStation icons get PlayStation classics.
     * Typed lines use only letters and spaces (and digits on the number row page).
     */
    private class DemoTexts(
        val search: String,                 // Move and type
        val shiftLine: String,              // Capitals, numbers and symbols (shown, not typed)
        val jump: String,                   // Hold RT: jumping a word at a time
        val copy: String,                   // Select, copy and paste
        val stick: String,                  // Right stick moves the cursor
        val wordBefore: String,             // Word suggestions: already typed...
        val wordTyped: String,              // ...then these letters...
        val wordGuesses: List<String>,      // ...and these show, best first ...
        val wordOthers: List<List<String>>, // ...(what shows after each earlier letter)
        val accentBefore: String,           // Hold A: already typed...
        val accentLetter: Char,             // ...hold A on this letter...
        val accentChoice: String,           // ...pick this...
        val accentAfter: String,            // ...and the rest of the word
        val numbers: String                 // Number row
    )

    private val texts: DemoTexts get() = if (remote) TV_TEXTS else when (settings.buttonStyle) {
        ButtonStyle.SWITCH -> DemoTexts(
            search = "ocarina of time",
            shiftLine = "Kirby's Dream Land (1992)",
            jump = "donkey kong country returns",
            copy = "paper mario thousand year door",
            stick = "metroid prime",
            wordBefore = "super mario ", wordTyped = "wor",
            wordGuesses = listOf("world", "work", "word"),
            wordOthers = listOf(listOf("with", "will", "what"), listOf("world", "would", "work")),
            accentBefore = "pok", accentLetter = 'e', accentChoice = "é", accentAfter = "mon",
            numbers = "super mario 64")
        ButtonStyle.PLAYSTATION -> DemoTexts(
            search = "crash bandicoot",
            shiftLine = "Final Fantasy VII (1997)",
            jump = "legacy of kain soul reaver",
            copy = "castlevania symphony of the night",
            stick = "spyro the dragon",
            wordBefore = "metal gear ", wordTyped = "sol",
            wordGuesses = listOf("solid", "sold", "solo"),
            wordOthers = listOf(listOf("so", "some", "still"), listOf("solid", "some", "soon")),
            accentBefore = "", accentLetter = 'o', accentChoice = "ō", accentAfter = "kami",
            numbers = "gran turismo 2")
        ButtonStyle.XBOX -> DemoTexts(
            search = "ninja gaiden",
            shiftLine = "Ninja Gaiden Black (2005)",
            jump = "fable the lost chapters",
            copy = "conker live and reloaded",
            stick = "jet set radio future",
            wordBefore = "knights of the old ", wordTyped = "rep",
            wordGuesses = listOf("republic", "report", "reply"),
            wordOthers = listOf(listOf("right", "really", "read"), listOf("really", "read", "remember")),
            accentBefore = "viva pi", accentLetter = 'n', accentChoice = "ñ", accentAfter = "ata",
            numbers = "mechassault 2")
    }

    /** With a TV remote: films, series and what you'd search for in Android TV apps. */
    private val TV_TEXTS = DemoTexts(
        search = "stranger things",
        shiftLine = "Dune: Part Two (2024)",
        jump = "the lord of the rings",
        copy = "the grand budapest hotel",
        stick = "better call saul",
        wordBefore = "game of ", wordTyped = "thr",
        wordGuesses = listOf("thrones", "three", "through"),
        wordOthers = listOf(listOf("the", "to", "that"), listOf("the", "that", "this")),
        accentBefore = "am", accentLetter = 'e', accentChoice = "é", accentAfter = "lie",
        numbers = "apollo 13")

    /** "the last save" → "the", " last", " save": each step of the cursor or the selection. */
    private fun wordsOf(line: String) = line.split(' ').mapIndexed { i, w -> if (i == 0) w else " $w" }

    /**
     * A TV remote has a ring, OK and Back, and nothing else, so it gets its own pages where a
     * controller's wouldn't make sense. Each page says who it's for: [For.ALL] (most of them),
     * [For.CONTROLLER] or [For.REMOTE]. To add a feature, add its page once for everyone, or a
     * controller page and a remote page next to each other. The right ones are picked here.
     */
    private enum class For { ALL, CONTROLLER, REMOTE }

    /** What the picked device can press (see [Controls]). */
    private val controls by lazy { Controls.of(this, settings) }
    private val remote get() = controls.remote

    /**
     * The pages for this device: its audience, and (for pages that need them) whether it has sticks.
     * A page with [Page.sticks] = true is for controllers with sticks, false for those without.
     */
    private val pages: List<Page> by lazy {
        (if (whatsNew) whatsNewPages else tourPages).filter {
            (it.audience == For.ALL || it.audience == (if (remote) For.REMOTE else For.CONTROLLER)) &&
                (it.sticks == null || it.sticks == controls.sticks)
        }
    }

    /** For the consistency checks: each page shown on this device, with its button rows. */
    internal fun pageCheck(): List<Pair<String, List<String>>> = pages.map { it.title to it.rows.map { r -> r.second } }

    /** For the consistency checks: whether this page shows the loop bar, and its runs so far. */
    internal val loopBarShown get() = panel.loopShown
    internal val loopRuns get() = loop.runsForCheck.toList()

    /** For the consistency checks: the device picture, to listen to what it lights. */
    internal val pictureForCheck get() = picture

    /** For the renders: the area holding the device and the keyboard. */
    internal val stageAreaForCheck: View? get() = splitRight ?: stageRow

    private fun offNote(on: Boolean, what: String) = if (on) "" else " $what switched off right now: turn it on in Settings → Typing."

    /** The highlights of [WhatsNew.VERSION], each acted out like the tour. */
    private val whatsNewPages: List<Page> by lazy {
        val learns = " It learns the words you use, like names and game titles."
        listOf(
            Page("Word suggestions that learn",
                "While you type, the words you probably mean show in the top row." + learns +
                    offNote(settings.wordSuggestions, "They're"),
                listOf("Take the first word" to "{R3}", "Pick another one" to "{DUP} then {A}"),
                Demo.WORDS, For.CONTROLLER, sticks = true),
            Page("Word suggestions that learn",
                "While you type, the words you probably mean show in the top row." + learns +
                    offNote(settings.wordSuggestions, "They're"),
                listOf("Take a word" to "{DUP} then {A}"),
                Demo.WORDS, For.CONTROLLER, sticks = false),
            Page("Word suggestions that learn",
                "While you type, the words you probably mean show in the top row." + learns +
                    offNote(settings.wordSuggestions, "They're"),
                listOf("Take a word" to "up, then OK"),
                Demo.WORDS, For.REMOTE),
            Page("Nine sound packs, all remade",
                "From soft real keys to little notes in one scale. Each pack plays here as it types its own name. Pick yours in Settings\u00A0\u2192 Sound\u00A0&\u00A0haptics." +
                    (if (settings.soundVolume <= 0) " Sound is off right now: turn up Volume there to hear them."
                    else if (!settings.soundInSilent && Feedback.silentNow(this)) " Your device is on silent: turn on Play in silent mode there to hear them."
                    else ""),
                emptyList(), Demo.SOUNDS),
            Page("Hold $aName for accents",
                "Hold $aName on a letter and its accents rise into the top row. Move to one and let go, or let go first and choose later." +
                    offNote(settings.holdAccents, "It's"),
                listOf("Show the accents" to "hold {A}", "Type the one you chose" to "let go of {A}",
                    "Kept open: type it" to "{A}", "Keep the plain letter" to "{B}"),
                Demo.ACCENTS, For.CONTROLLER),
            Page("Hold OK for accents",
                "Hold OK on a letter and its accents rise into the top row. Move to one and let go, or let go first and choose later." +
                    offNote(settings.holdAccents, "It's"),
                listOf("Show the accents" to "hold OK", "Type the one you chose" to "let go of OK",
                    "Kept open: type it" to "OK", "Keep the plain letter" to "Back"),
                Demo.ACCENTS, For.REMOTE),
            Page("Tidies up as you type",
                "A capital starts each sentence and a lone i becomes I. A space typed just before a full stop or comma moves after it. Each one has its own switch in Settings → Typing.",
                emptyList(), Demo.SMART),
            Page("Made for what you hold",
                "Inlay knows if you hold a controller, a handheld, a vertical handheld or a TV remote, and fits its buttons and tips to it. Pick yours and try your buttons in Settings → Device.",
                emptyList(), Demo.DEVICES),
            Page("A number row, and tidier Settings",
                "Turn on Number row in Settings → Look for 1 to 0 above the letters. Settings now has six tabs, each about one thing.",
                emptyList(), Demo.NUMBER_ROW)
        )
    }

    private val tourPages: List<Page> by lazy {
        listOf(
            Page("Welcome to Inlay",
                "Type anywhere with your controller, no touch screen needed. This tour shows the buttons and takes about a minute.",
                emptyList(), Demo.WANDER, For.CONTROLLER),
            Page("Welcome to Inlay",
                "Type anywhere with your TV remote: search, sign in, name things. This tour shows how and takes about a minute.",
                emptyList(), Demo.WANDER, For.REMOTE),
            Page("Move and type",
                "Move the highlight with the D-pad or the left stick, then press a button.",
                listOf("Type the highlighted key" to "{A}", "Delete · hold to speed up" to "{X}", "Space" to "{Y}",
                    "Enter · Search · Go" to "{START}", "Close the keyboard" to "{B}"),
                Demo.TYPE, For.CONTROLLER, sticks = true),
            Page("Move and type",
                "Move the highlight with the D-pad, then press a button.",
                listOf("Type the highlighted key" to "{A}", "Delete · hold to speed up" to "{X}", "Space" to "{Y}",
                    "Enter · Search · Go" to "{START}", "Close the keyboard" to "{B}"),
                Demo.TYPE, For.CONTROLLER, sticks = false),
            Page("Move and type",
                "Move the highlight with the ring on your remote, then press OK. Space, Delete and Enter are keys on the keyboard itself.",
                listOf("Move the highlight" to "the ring", "Type the highlighted key" to "OK", "Close the keyboard" to "Back"),
                Demo.TYPE, For.REMOTE),
            Page("Capitals, numbers and symbols",
                "Tap the triggers to switch what the keys show.",
                listOf("Shift · tap twice for caps lock" to "{R2}", "Numbers and symbols" to "{L2}") +
                    (if (controls.sticks) listOf("Keyboard size" to "{L3}") else emptyList()),
                Demo.SHIFT_AND_SYMBOLS, For.CONTROLLER),
            Page("Capitals, numbers and symbols",
                "Shift and ?123 are keys on the keyboard: move to one and press OK. Shift twice is caps lock.",
                listOf("Capitals" to "Shift key, then OK", "Numbers and symbols" to "?123, then OK"),
                Demo.SHIFT_REMOTE, For.REMOTE),
            Page("Hold $rtName for more",
                "While you hold $rtName, tips appear on the keyboard, so you don't have to remember these.",
                listOf("One capital letter" to "{R2} + {A}", "Jump a whole word" to "{R2} + {L1} {R1}",
                    "Accent the last letter (é, ñ, å…)" to "{R2} + {Y}", "Undo" to "{R2} + {X}"),
                Demo.HOLD_RT, For.CONTROLLER),
            Page("Accents and word suggestions",
                "Hold $aName on a letter for é, ñ, ü and more. While you type, words show in the top row: press the right stick in for the first one.",
                listOf("Accents · move, then let go" to "hold {A}", "Keep the plain letter" to "{B}", "Take the first word" to "{R3}"),
                Demo.ACCENTS_AND_WORDS, For.CONTROLLER, sticks = true),
            Page("Accents and word suggestions",
                "Hold $aName on a letter for é, ñ, ü and more. While you type, words show in the top row: move up to one and press $aName.",
                listOf("Accents · move, then let go" to "hold {A}", "Keep the plain letter" to "{B}", "Take a word" to "{DUP} then {A}"),
                Demo.ACCENTS_AND_WORDS, For.CONTROLLER, sticks = false),
            Page("Accents and word suggestions",
                "Hold OK on a letter for é, ñ, ü and more. While you type, words show in the top row: move up to one and press OK.",
                listOf("Accents · move, then let go" to "hold OK", "Keep the plain letter" to "Back", "Take a word" to "up, then OK"),
                Demo.ACCENTS_AND_WORDS, For.REMOTE),
            Page("Select, copy and paste",
                "Press $selectName to start selecting. Cut, Copy and Paste are in the top row, and the clock keeps your last six copies.",
                listOf("Move the text cursor" to "{L1} {R1}", "Start selecting" to "{SELECT}",
                    "Grow the selection" to "{L1} {R1}", "Select everything" to "{SELECTx2}"),
                Demo.EDIT, For.CONTROLLER),
            Page("Make it yours",
                "Themes, sound packs, languages and a number row are all in Settings" +
                    (if (remote) "." else ", and Xbox, PlayStation or Switch button icons.") + " Settings → Device lets you try your buttons, and About replays this tour.",
                emptyList(), Demo.YOURS)
        )
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.init(this)
        SettingsStyle.init(this)
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
        setContentView(buildScreen().also { SettingsStyle.useFont(it) })
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

    /** Hidden (screen off, another app on top): the demo stops, so nothing runs that nobody sees. */
    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        feedback.update(settings)            // back to your own sound pack
        picture.animate().cancel()
        stopped = true
        super.onStop()
    }

    /** Back in view: the page's demo starts again from its beginning. */
    override fun onStart() {
        super.onStart()
        if (stopped) { stopped = false; startDemo(pages[page].demo) }
    }
    private var stopped = false

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
    /**
     * A compact landscape screen: short (a small 4:3 handheld) or too narrow for the split (a square screen, a
     * 16:9 phone). The words stack above the device and keyboard, a little tighter, with the caption for the rows.
     */
    private val short get() = wide && (resources.configuration.screenHeightDp < SHORT_MAX_DP || resources.configuration.screenWidthDp < SPLIT_MIN_DP)
    /** Landscape but too small for the split: the rows show one at a time, as a caption that follows the demo. */
    private val captionMode get() = wide && (!split || splitCaption)
    /** Split, but some page's button list is taller than the words column: the rows show as the caption there too. */
    private var splitCaption = false
    internal var reserveDebug = ""

    /** The width a row's words get in the list: the column, less the widest button group and the gaps. */
    private fun rowRoom(): Float {
        val cell = rowCells.firstOrNull() ?: return Float.MAX_VALUE
        val badge = (cell as LinearLayout).getChildAt(0).layoutParams.width
        return (words.width - words.paddingLeft - words.paddingRight - badge - px(ROW_TEXT_GAP_DP)).toFloat()
    }

    /** Landscape and wide enough to put the words beside the demo (handhelds, phones, tablets, TVs): see [buildScreen]. */
    private val split get() = wide && !short && resources.configuration.screenWidthDp >= SPLIT_MIN_DP
    private var splitRight: LinearLayout? = null

    /** Landscape with room for two columns of words: handhelds, tablets and TVs. */
    private val wide get() = resources.configuration.screenWidthDp >= Design.WIDE_MIN_DP &&
        resources.configuration.screenWidthDp > resources.configuration.screenHeightDp

    private val onTv get() = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Graphite with a soft light in the user's highlight colour; What's new is lit differently from the tour
            background = Backdrop(settings.accent, whatsNew)
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
            setPadding(0, px(if (short) Design.SPACE_S else Design.SPACE_L), 0, 0)
        }
        root.addView(dots, lpMatchWrap())

        // ---- The words ----
        // The label above the title says which this is: "WELCOME TOUR" in quiet grey, or, for
        // What's new, a "NEW IN 1.2.0" tag. The page count sits next to it, quieter.
        eyebrow = TextView(this).apply {
            textSize = Design.TYPE_SECTION
            letterSpacing = 0.12f
            typeface = SettingsStyle.medium
            // Your colour as a soft tint, never a ring: rings mean "you are here" (audit + Emre, 28 Oct 2026).
            // The words are measured on the backdrop's light (and the tag's tint): 4.5:1 with any colour
            setTextColor(labelColor(settings.accent, whatsNew))
            includeFontPadding = false
            if (whatsNew) {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Design.RADIUS_PILL
                    setColor(Contrast.withAlpha(settings.accent, Design.ALPHA_TINT_TAG))
                }
                setPadding(px(Design.SPACE_S), px(Design.SPACE_XS), px(Design.SPACE_S), px(Design.SPACE_XS))
            }
        }
        eyebrowCount = TextView(this).apply {
            fontFeatureSettings = Design.NUMBERS_TABULAR      // "2 OF 7" → "3 OF 7": the line keeps its length
            textSize = Design.TYPE_SECTION
            letterSpacing = 0.12f
            typeface = SettingsStyle.medium
            setTextColor(SettingsStyle.textSecondary)
            includeFontPadding = false
        }
        eyebrowRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(eyebrow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(eyebrowCount, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = px(if (whatsNew) Design.SPACE_M else Design.SPACE_S) })
        }
        title = TextView(this).apply {
            // Two lines break evenly ("Word suggestions / that learn"), never one word alone on the last line
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            textSize = Design.TYPE_DISPLAY
            typeface = SettingsStyle.medium
            setTextColor(SettingsStyle.textPrimary)
            setLineSpacing(0f, 1.1f)
            letterSpacing = Design.TRACKING_TITLE
        }
        body = TextView(this).apply {
            typeface = SettingsStyle.regular
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            textSize = Design.TYPE_ROW
            setTextColor(SettingsStyle.textSecondary)
            setLineSpacing(0f, 1.3f)
            maxWidth = px(BODY_MAX_WIDTH_DP)       // comfortable line length
        }
        title.textSize = titleSize
        body.textSize = bodySp()
        words = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP              // the title sits at one height on every page
            addView(eyebrowRow, lpMatchWrap())
            addView(title, lpMatchWrap(topMargin = if (short) 0f else Design.SPACE_S))
            addView(body, lpMatchWrap(topMargin = if (short) Design.SPACE_XS else Design.SPACE_M))
            // The label counts the pages ("4 OF 7"), so the dots only show where a short screen hides it
            if (short) eyebrowRow.visibility = View.GONE else dots.visibility = View.GONE
        }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- The stage: the device you hold and the keyboard it drives, side by side ----
        field = DemoFieldView(this)
        picture = DevicePictureView(this).apply {
            accent = settings.accent
            maxHeightDp = if (wide) 2000f else PICTURE_NARROW_DP   // side by side: as tall as the stage allows
            tallBoost = false                                       // the stage (or the portrait cap) sets the size
            show(controls.kind, settings.buttonStyle, controls.sticks)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        demo = KeyGridView(this).apply {
            isFocusable = false
            listener = this@TutorialActivity
            // The picture lights whatever the keyboard acts out: D-pad steps, A, RT, LT
            onDemoInput = { key, held ->
                when (held) {
                    null -> picture.flash(key)
                    true -> { picture.held = picture.held + key; picture.holding = picture.holding + key }
                    false -> { picture.held = picture.held - key; picture.holding = picture.holding - key }
                }
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (onTv) setTvMode(true)              // on a TV the keyboard is the compact TV panel, with its own text line
        }
        if (onTv) field.visibility = View.GONE
        // The text box and the keyboard sit together in one window, like an app on the device (and like the
        // GitHub images): a black panel with rounded corners and one solid edge. The keyboard is clipped to its
        // own rounded corners, so any theme (Light too) sits cleanly inside. On a TV the keyboard is already
        // its own floating panel, so it goes without.
        val keyboardClip = RoundClipFrame(this, px(KEYBOARD_RADIUS_DP).toFloat()).apply {
            addView(demo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        panel = StagePanel(this, field, keyboardClip, demo, framed = !onTv,
            pad = px(if (short) Design.SPACE_S else PANEL_PAD_DP), gap = px(if (short) Design.SPACE_XS else PANEL_GAP_DP),
            fieldUnit = px(if (short) FIELD_HEIGHT_SHORT_DP else FIELD_HEIGHT_DP)).apply {
            if (!onTv) background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = px(PANEL_RADIUS_DP).toFloat()
                setColor(PANEL_FILL)
                setStroke(max(2, px(1f)), PANEL_EDGE)
            }
        }
        // The slot the window sits in: centred, so a narrower window never hugs one side
        stage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        pictureCol = object : LinearLayout(this) {
            // Beside the words (small screens) the device fits the words' height and never adds to it
            override fun onMeasure(widthSpec: Int, heightSpec: Int) =
                if (header != null && !deviceBesideKeyboard && MeasureSpec.getMode(heightSpec) != MeasureSpec.EXACTLY)
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY))
                else super.onMeasure(widthSpec, heightSpec)
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(picture, lpMatchWrap())
        }
        panel.loop = loop
        panel.loopAccent = settings.accent

        // The screen's side margin; small screens keep a little less, so the keyboard gets the width
        val side = px(if (short) Design.SPACE_XL else Design.SPACE_XXL)
        words.addView(rows, lpMatchWrap(topMargin = if (short) Design.SPACE_S else if (wide) Design.SPACE_M else Design.SPACE_XL))
        rowsPane = rows
        if (split) {
            // Split: the words on the left, the device above the keyboard on the right. The words never take
            // the keyboard's room, so the device and the keyboard keep one size on every page and the keyboard
            // is drawn larger than it could be under a block of words.
            demo.keepProportions = true
            body.maxWidth = Int.MAX_VALUE
            wordsScroll = null
            words.setPadding(0, px(Design.SPACE_L), 0, 0)
            splitRight = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(pictureCol, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(Design.SPACE_M) })
            }
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(side, 0, side, px(Design.SPACE_M))
                addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, SPLIT_WORDS))
                addView(splitRight, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - SPLIT_WORDS).apply { marginStart = side; topMargin = px(Design.SPACE_L) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else if (wide) {
            // The words across the top, then the device and the keyboard side by side, both as big as they fit.
            // The keyboard is a scaled copy of the real one, so keys and hints keep their shape.
            demo.keepProportions = true
            body.maxWidth = px(HEADER_BODY_MAX_DP)
            // Small screens: the device sits beside the words, so the keyboard gets the screen's whole width (a small
            // screen's keyboard is held back by its width, never its height). The device is centred on the words,
            // the same margin on its right as the words have on their left.
            // The title runs across the whole width; the sentence and the caption sit beside the device, which is
            // centred on them. The caption stays at their foot, right above the keyboard it describes.
            words.setPadding(side, px(if (short) Design.SPACE_S else Design.SPACE_L), side, 0)
            words.removeView(body); words.removeView(rows)
            val bodyCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(body)
                addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
                addView(rows)
            }
            header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bodyCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                addView(pictureCol, LinearLayout.LayoutParams(deviceColumnPx(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginStart = px(DEVICE_HEADER_GAP_DP) })
            }
            words.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(words, lpMatchWrap())
            wordsScroll = null
            stageRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                // Centred in its room: the number row's page shows a taller keyboard, which grows evenly both ways
                gravity = Gravity.CENTER_VERTICAL
                setPadding(side, px(if (short) Design.SPACE_S else Design.SPACE_L), side, px(if (short) Design.SPACE_S else Design.SPACE_M))
                addView(stage, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            root.addView(stageRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            // Portrait: the words (they scroll if a page is long), then the device, the text box and the keyboard
            val wordsPane = noFocusScroll(words)
            wordsScroll = wordsPane
            wordsPane.setPadding(side, px(Design.SPACE_M), side, px(Design.SPACE_M))
            root.addView(wordsPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(pictureCol, lpMatchWrap().apply { bottomMargin = px(Design.SPACE_M) })
            if (onTv) stage.setPadding(side, 0, side, 0)
            root.addView(stage, lpMatchWrap().apply { if (!onTv) { marginStart = side; marginEnd = side } })
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
        eyebrow.text = if (whatsNew) "NEW IN ${WhatsNew.VERSION}" else "WELCOME TOUR"
        eyebrowCount.text = if (whatsNew) "${index + 1} OF ${pages.size}" else "· ${index + 1} OF ${pages.size}"
        title.text = p.title
        body.text = p.body
        buildRows(p.rows)
        rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
        refreshDots()
        val next = if (index < pages.lastIndex) "Next" else if (whatsNew) "Got it" else "Done"
        controlBar.show(settings.buttonStyle, if (remote) listOf(
            // A remote has OK and Back, no badges: Back steps back, or leaves from the first page
            "OK · $next",
            if (index > 0) "Back · Previous" else if (whatsNew) "Back · Close" else "Back · Skip the tour"
        ) else listOfNotNull(
            "{A} $next",
            if (index > 0) "{B} Back" else null,
            if (index < pages.lastIndex) (if (whatsNew) "{START} Close" else "{START} Skip the tour") else null
        ))
        startDemo(p.demo)
        wordsScroll?.scrollTo(0, 0)
        fitWhenLaidOut()
        if (animate) {
            words.alpha = 0f
            words.translationX = px(12f * direction).toFloat()
            words.animate().alpha(1f).translationX(0f).setDuration(Design.MOTION_PAGE)
                .setInterpolator(Design.EASE_OUT).start()
        }
    }

    /**
     * THE PAGE TEMPLATE. Every tour and What's new page is the same four zones, top to bottom:
     *  1. the label line ("WELCOME TOUR · 2 OF 7", or the NEW IN tag)
     *  2. the title: one line, one size per screen
     *  3. one or two sentences, never hidden
     *  4. the button rows: a grid (three columns for five or more rows on wide screens, two otherwise,
     *     one under the other upright). In each column the buttons get that column's own width, then the words.
     *     Rows that all start with the same held button ("{R2} + …") show it once, above them.
     * The words take the room of this screen's longest page (measured once, [reserveWords]), so the device and
     * the keyboard below keep one place and one size on every page: only the words change. A page whose words
     * don't fit the template on some screen fails TourTemplateTest, which names it.
     */
    private fun buildRows(list: List<Pair<String, String>>) {
        shownRows = list
        rows.removeAllViews()
        rowTexts.clear()
        rowCells.clear()
        caption = null
        if (list.isEmpty()) return
        if (captionMode) { buildCaption(list); return }
        val combo = Regex("""^(\{\w+\}) \+ (.+)$""")
        val matches = list.map { combo.matchEntire(it.second) }
        val shared = if (list.size >= 2 && matches.all { it != null } && matches.map { it!!.groupValues[1] }.distinct().size == 1)
            matches.first()!!.groupValues[1] else null
        val shown = if (shared != null) list.mapIndexed { i, (what, _) -> what to matches[i]!!.groupValues[2] } else list
        if (shared != null) rows.addView(BadgeLineView(this, HintText.parse("$shared +"), settings.buttonStyle, Design.TYPE_CAPTION,
            SettingsStyle.textSecondary, badgeScale = badgeScale, gapBadgeToBadge = BADGE_GAP, gapBadgeToText = TEXT_GAP),
            lpMatchWrap().apply { bottomMargin = px(Design.SPACE_XS) })
        val parsed = shown.map { (what, buttons) -> what to HintText.parse(buttons) }
        // The buttons sit right next to their words, and the words line up down the list
        val badgeW = parsed.maxOf { badgeWidth(it.second) }
        parsed.forEachIndexed { i, (what, parts) ->
            val own = badgeWidth(parts)
            rows.addView(LinearLayout(this).apply {
                rowCells += this
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = px(rowHeightDp)
                // The marker, then the buttons, then the words. The marker hangs in the margin, a fixed step
                // before its own buttons, so the buttons and words still line up with the title
                addView(rowMarker(parts).apply { alpha = 0f })
                addView(badgeLine(parts), LinearLayout.LayoutParams(own, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(rowLabel(what).apply { setTextColor(SettingsStyle.textSecondary) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(ROW_TEXT_GAP_DP) })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = px(ROW_GAP_DP)
                marginStart = -markerReach() + (badgeW - own)
            })
        }
        allowMarkerMargin()
    }

    private fun badgeLine(parts: List<HintPart>) = BadgeLineView(this, parts, settings.buttonStyle, Design.TYPE_CAPTION,
        SettingsStyle.textSecondary, badgeScale = badgeScale, gapBadgeToBadge = BADGE_GAP, gapBadgeToText = TEXT_GAP)

    private fun rowLabel(what: String) = TextView(this).apply {
        typeface = SettingsStyle.regular
        text = what
        textSize = bodySp()
        setTextColor(SettingsStyle.textPrimary)
        setLineSpacing(0f, 1.15f)
        maxLines = 2                     // a safety net only: TourTemplateTest wants every row on one line
        ellipsize = android.text.TextUtils.TruncateAt.END
        rowTexts += this
    }

    private fun badgeWidth(parts: List<HintPart>): Int {
        val painter = BadgePainter(dp).apply { style = settings.buttonStyle; scale = badgeScale; gapBadgeToBadge = BADGE_GAP; gapBadgeToText = TEXT_GAP }
        val joiner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = SettingsStyle.regular             // as the badge line draws its words
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_CAPTION, resources.displayMetrics)
        }
        return painter.measureLine(parts, joiner).roundToInt()
    }

    /**
     * THE CURRENT-ROW MARKER, one for the list (wide screens) and the caption (small screens): a short bar in your
     * colour, as tall as the row's buttons, [MARKER_GAP_DP] before them, in the margin. In the list the row it
     * marks is bright and the others step back to the quiet grey. No box, so it never reads as a button.
     * TourMarkerTest checks it is drawn whole, centred on the buttons, the same step before them everywhere.
     */
    private fun rowMarker(parts: List<HintPart>) = View(this).apply {
        tag = MARKER_TAG
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = px(MARKER_WIDTH_DP / 2f).toFloat()
            setColor(markerColor(settings.accent, whatsNew))
        }
        layoutParams = LinearLayout.LayoutParams(px(MARKER_WIDTH_DP), badgeHeight(parts).roundToInt()).apply { marginEnd = px(MARKER_GAP_DP) }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** How far the marker reaches out before the buttons: its width and its gap. */
    private fun markerReach() = px(MARKER_WIDTH_DP) + px(MARKER_GAP_DP)

    /** The marker sits in the screen's margin, outside the words' own edge: nothing on the way may cut it off. */
    private fun allowMarkerMargin() {
        var g: ViewGroup? = rows
        val top = window.decorView as? ViewGroup
        while (g != null && g !== top) { g.clipChildren = false; g.clipToPadding = false; g = g.parent as? ViewGroup }
    }

    private fun badgeHeight(parts: List<HintPart>): Float =
        BadgePainter(dp).apply { style = settings.buttonStyle; scale = badgeScale; gapBadgeToBadge = BADGE_GAP; gapBadgeToText = TEXT_GAP }.lineHeight(parts)

    // ---- The caption (small screens): one row at a time, the one the demo is acting out ----------------
    private class Caption(val box: LinearLayout, val label: TextView, val pips: LinearLayout, val parts: List<List<HintPart>>, val words: List<String>, val marker: View)
    private var caption: Caption? = null

    private fun buildCaption(list: List<Pair<String, String>>) {
        val parts = list.map { HintText.parse(it.second) }
        val label = rowLabel(list[0].first).apply { maxLines = 2 }
        val pips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (list.size > 1) repeat(list.size) { addView(View(context).apply { background = pip(false) },
                LinearLayout.LayoutParams(px(PIP_DP), px(PIP_DP)).apply { marginStart = if (it == 0) 0 else px(PIP_GAP_DP) }) }
        }
        // One line when every row fits beside its buttons and the markers; otherwise the markers sit under it
        // and a long row may take a second line. Measured for every row, so the caption never changes height.
        val room = (wordsWidthOverride ?: (if (header != null && columnRegular >= 0) wordsWidthFor(pages[page].demo) else null)
            ?: if (words.width > 0) words.width - words.paddingLeft - words.paddingRight else resources.displayMetrics.widthPixels - 2 * px(Design.SPACE_XXL)).toFloat()
        val paint = android.text.TextPaint(label.paint)
        val pipsW = if (list.size > 1) px(Design.SPACE_M) + list.size * px(PIP_DP) + (list.size - 1) * px(PIP_GAP_DP) else 0
        val widths = list.mapIndexed { i, (what, _) -> badgeWidth(parts[i]) + px(ROW_TEXT_GAP_DP) + paint.measureText(what) }
        val inline = widths.all { it + pipsW <= room }
        // The same marker as the list's, always on (the caption is the current row), hanging in the margin
        val marker = rowMarker(parts[0])
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badgeLine(parts[0]))
            addView(label, LinearLayout.LayoutParams(if (inline) ViewGroup.LayoutParams.WRAP_CONTENT else 0, ViewGroup.LayoutParams.WRAP_CONTENT, if (inline) 0f else 1f)
                .apply { marginStart = px(ROW_TEXT_GAP_DP) })
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(marker)
            addView(inner, LinearLayout.LayoutParams(if (inline) ViewGroup.LayoutParams.WRAP_CONTENT else 0, ViewGroup.LayoutParams.WRAP_CONTENT, if (inline) 0f else 1f))
            if (inline) addView(pips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(Design.SPACE_M) })
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = px(rowHeightDp)
            gravity = Gravity.CENTER_VERTICAL
            addView(line, lpMatchWrap().apply { marginStart = -markerReach() })
            if (!inline) addView(pips, lpMatchWrap(topMargin = Design.SPACE_XS))
        }
        // The tallest any row makes it, so the caption keeps one height on the page
        val lineH = label.lineHeight
        val twoLines = !inline && widths.any { it > room }
        box.minimumHeight = maxOf(px(rowHeightDp), (if (twoLines) 2 * lineH else lineH) + (if (inline) 0 else px(Design.SPACE_XS) + px(PIP_DP)) + px(Design.SPACE_S))
        rows.addView(box, lpMatchWrap())
        caption = Caption(inner, label, pips, parts, list.map { it.first }, marker)
        allowMarkerMargin()
        setCaption(0)
    }

    private fun pip(on: Boolean) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(if (on) settings.accent else Design.DOT_IDLE)
    }

    private fun setCaption(i: Int) {
        val c = caption ?: return
        c.box.removeViewAt(0)
        c.box.addView(badgeLine(c.parts[i]), 0)
        c.marker.layoutParams.height = badgeHeight(c.parts[i]).roundToInt()      // as tall as this row's buttons
        c.marker.requestLayout()
        c.label.text = c.words[i]
        for (k in 0 until c.pips.childCount) c.pips.getChildAt(k).background = pip(k == i)
    }

    /** The demo is acting out row [i]: light it in the list, or bring it into the caption. */
    private fun lightRow(i: Int) {
        val moving = WhatsNew.motionAllowed()
        caption?.let { c ->
            if (!moving) { setCaption(i); return }
            val lift = px(CAPTION_LIFT_DP).toFloat()
            c.box.animate().cancel()
            c.box.animate().alpha(0f).translationY(-lift).setDuration(Design.MOTION_FADE / 2).setInterpolator(Design.EASE_IN).withEndAction {
                setCaption(i)
                c.box.translationY = lift
                c.box.animate().alpha(1f).translationY(0f).setDuration(Design.MOTION_FADE).setInterpolator(Design.EASE_OUT).start()
            }.start()
            return
        }
        // The marked row is bright, the others step back to the quiet grey
        rowCells.forEachIndexed { k, cell ->
            val on = k == i
            val marker = cell.findViewWithTag<View>(MARKER_TAG) ?: return@forEachIndexed
            val label = (cell as ViewGroup).getChildAt(cell.childCount - 1) as? TextView ?: return@forEachIndexed
            val ink = if (on) SettingsStyle.textPrimary else SettingsStyle.textSecondary
            marker.animate().cancel()
            if (!moving) { marker.alpha = if (on) 1f else 0f; label.setTextColor(ink); return@forEachIndexed }
            marker.animate().alpha(if (on) 1f else 0f).setDuration(Design.MOTION_FADE).setInterpolator(Design.ease(arriving = on)).start()
            if (label.currentTextColor != ink) android.animation.ValueAnimator.ofArgb(label.currentTextColor, ink).apply {
                duration = Design.MOTION_FADE; interpolator = Design.EASE_OUT
                addUpdateListener { label.setTextColor(it.animatedValue as Int) }
            }.start()
        }
    }

    /** Follows the demo: which row it's acting out (see [RowLight]). Set up for each page's demo. */
    private var rowLight: RowLight? = null
    private val rowTick = object : Runnable {
        override fun run() {
            val r = rowLight ?: return
            val now = android.os.SystemClock.uptimeMillis()
            r.tick(now)
            // A still demo (animations off, or the endless welcome showcase): the rows take turns on their own
            if (captionMode && !loop.running && r.allShownThisRun()) r.newRun()
            handler.postDelayed(this, RowLight.TICK_MS)
        }
    }

    private fun startRowLight() {
        // A page that plays in beats leads with its own rows: the rows were just rebuilt, so light the current one again
        if (beatList.isNotEmpty()) { showBeat(beatList[beatAt].row); return }
        handler.removeCallbacks(rowTick)
        rowLight = null
        demo.spotlight = emptySet()
        picture.hinted = emptySet()
        rowCells.forEach { it.findViewWithTag<View>(MARKER_TAG)?.alpha = 0f }
        if (shownRows.isEmpty()) return
        val full = shownRows.map { HintText.parse(it.second) }
        // The buttons each row presses (not the ones it holds first): the keys on the keyboard they point at
        val pressed = full.map { parts -> parts.filterIsInstance<HintPart.Badge>().filter { !it.held }.map { it.button }.toSet() }
        val r = RowLight(full, takeTurns = captionMode) { i, byDemo ->
            lightRow(i)
            demo.spotlight = pressed[i]
            // A row having its turn while the demo does something else: its buttons show on the device as
            // pointed at (the ring alone), so the caption, the device and the keyboard always agree
            picture.hinted = if (byDemo) emptySet() else rowLight?.keysOf(i).orEmpty()
        }
        rowLight = r
        loop.onRun = { r.newRun() }
        picture.onLitChanged = {
            handler.post { rowLight?.lit(picture.litNow(), picture.holding, android.os.SystemClock.uptimeMillis()) }
        }
        r.start(android.os.SystemClock.uptimeMillis())
        handler.postDelayed(rowTick, RowLight.TICK_MS)
    }

    /** For the checks: every row the caption or the light has shown on this page so far. */
    internal fun rowsShownForCheck(): Set<Int> = rowLight?.everShown?.toSet().orEmpty() + beatsShown
    /** For the checks: the beat playing now (its row), and every beat's length in this run (ms). */
    internal val beatRowForCheck get() = beatList.getOrNull(beatAt)?.row ?: -1
    internal val beatLensForCheck get() = beatLens.toList()
    internal val beatsForCheck get() = beatList.map { it.row }
    internal val rowCountForCheck get() = shownRows.size
    internal val captionModeForCheck get() = captionMode
    internal val minFitForCheck get() = if (resources.configuration.screenHeightDp < StagePanel.TINY_DP) StagePanel.MIN_FIT_TINY else StagePanel.MIN_FIT
    /** For the checks: whether every row of the page fits the caption's one line. */
    internal fun captionOverflow(): List<String> {
        if (!captionMode) return emptyList()
        val c = caption ?: return emptyList()
        val room = words.width - words.paddingLeft - words.paddingRight
        val paint = android.text.TextPaint(c.label.paint)
        // Each row on at most two lines, in the room beside its buttons
        return shownRows.mapIndexedNotNull { i, (what, buttons) ->
            val beside = room - badgeWidth(HintText.parse(buttons)) - px(ROW_TEXT_GAP_DP)
            val layout = android.text.StaticLayout.Builder.obtain(what, 0, what.length, paint, beside.coerceAtLeast(1)).build()
            if (layout.lineCount > 2) what else null
        }
    }

    private val rowHeightDp get() = if (short) GRID_ROW_SHORT_DP else if (split) SPLIT_ROW_DP else if (wide) GRID_ROW_HEIGHT_DP else ROW_HEIGHT_DP

    /**
     * Small screens: the device column beside the words. As wide as your device's shape needs at the height of the
     * words ([DEVICE_HEADER_HEIGHT_DP]), never more than [DEVICE_HEADER_MAX_SHARE] of the width. The same on every
     * page (the welcome and devices pages fit the other devices into it).
     */
    private fun deviceColumnPx(demoKind: Demo? = null): Int {
        if (columnRegular >= 0) return if (demoKind == Demo.WANDER || demoKind == Demo.DEVICES) columnShowcase else columnRegular
        val content = resources.displayMetrics.widthPixels - 2 * px(Design.SPACE_XL)
        val most = content * DEVICE_HEADER_MAX_SHARE
        // The pages that show every device (welcome, devices) take the widest column, so a controller or a wide
        // handheld shows at a good size there too
        if (demoKind == Demo.WANDER || demoKind == Demo.DEVICES) return most.roundToInt()
        return minOf(px(DEVICE_HEADER_HEIGHT_DP) * picture.aspectRatio, most).roundToInt()
    }

    /** Small screens: the device column for this page, and the room it leaves the words. */
    private fun fitDeviceColumn(demoKind: Demo) {
        if (header == null || deviceBesideKeyboard) return
        val lp = pictureCol.layoutParams as LinearLayout.LayoutParams
        val w = deviceColumnPx(demoKind)
        pictureCol.visibility = if (w > 0) View.VISIBLE else View.GONE
        val gap = if (w > 0) px(DEVICE_HEADER_GAP_DP) else 0
        if (lp.width != w || lp.marginStart != gap) { lp.width = w; lp.marginStart = gap; pictureCol.layoutParams = lp }
    }

    /** A screen too short for the keyboard to use its whole width: the device sits beside the keyboard instead. */
    private var deviceBesideKeyboard = false
    /** Small screens: the device columns, once measured ([reserveWords]); -1 before. */
    private var columnRegular = -1
    private var columnShowcase = -1
    /** While measuring a page for another width than the words have now. */
    private var wordsWidthOverride: Int? = null

    /** Small screens: how wide the words are on a page with [demoKind] (the screen less the device beside them). */
    private fun wordsWidthFor(demoKind: Demo): Int {
        if (header == null) return words.width
        val w = deviceColumnPx(demoKind)
        return words.width - words.paddingLeft - words.paddingRight - (if (w > 0) w + px(DEVICE_HEADER_GAP_DP) else 0)
    }

    /** The words' height for this screen: the tallest page's, so the stage below never moves. */
    private var wordsHeight = 0

    private fun fitWhenLaidOut() {
        val host = wordsScroll ?: (words.parent as View)
        if (wordsHeight > 0 || wordsScroll != null) return
        if (host.width > 0 && words.width > 0 && words.height > 0) { reserveWords(); return }
        host.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (host.width <= 0 || words.width <= 0 || words.height <= 0) return
                host.viewTreeObserver.removeOnGlobalLayoutListener(this)
                reserveWords()
            }
        })
    }

    /**
     * Measures every page's words at this screen's width, once. Split: if any page's button list doesn't fit
     * the words column, every page shows its rows as the caption instead (one rule per screen, so pages never
     * differ). Stacked: the words keep the room of the tallest page, so the stage never moves.
     */
    private fun reserveWords() {
        val now = page
        if (split) {
            val room = words.height - words.paddingTop - words.paddingBottom
            val tooTall = pages.any { p ->
                title.text = p.title; body.text = p.body; buildRows(p.rows)
                rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
                // a row on two lines doesn't fit the list either
                if (rowTexts.any { r -> r.paint.measureText(r.text.toString()) > rowRoom() }) return@any true
                words.measure(View.MeasureSpec.makeMeasureSpec(words.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                words.measuredHeight - words.paddingTop > room
            }
            splitCaption = tooTall
            reserveDebug = "room $room at height ${words.height}, caption $tooTall"
            wordsHeight = words.height
            val p = pages[now]
            title.text = p.title; body.text = p.body; buildRows(p.rows)
            rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
            startRowLight()
            return
        }
        // How tall page [p]'s words are in [width], and whether its title and sentence keep to their lines (beside the
        // device the sentence may take three shorter lines; the comfortable line length is 45 to 75 letters)
        fun measure(p: Page, width: Int, captionRoom: Int = width): Pair<Int, Boolean> {
            wordsWidthOverride = captionRoom
            title.text = p.title; body.text = p.body; buildRows(p.rows)
            rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
            words.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            wordsWidthOverride = null
            // (a page without button rows has the caption's room for its sentence too)
            val tidy = (title.layout?.lineCount ?: 1) <= titleLinesAllowed &&
                (body.layout?.lineCount ?: 1) <= bodyLinesAllowed + (if (header != null && p.rows.isEmpty()) 1 else 0)
            return words.measuredHeight to tidy
        }
        var tallest = 0
        val h = header
        if (h != null) {
            // Small screens: the words keep the height they'd have across the whole width, so the keyboard keeps its
            // room. Beside them the device gets the widest column (up to what its shape wants) that adds no line to any
            // page, so the keyboard gets the screen's width and loses nothing in height
            val full = words.width - words.paddingLeft - words.paddingRight
            // Page [p] with a device column [col] wide beside its sentence and caption
            fun measureWith(p: Page, col: Int): Pair<Int, Boolean> {
                val lp = pictureCol.layoutParams as LinearLayout.LayoutParams
                lp.width = col; lp.marginStart = if (col > 0) px(DEVICE_HEADER_GAP_DP) else 0
                pictureCol.visibility = if (col > 0) View.VISIBLE else View.GONE
                return measure(p, words.width, full - (if (col > 0) col + px(DEVICE_HEADER_GAP_DP) else 0))
            }
            tallest = pages.maxOf { measureWith(it, 0).first }
            // The words may grow into the keyboard's height while the keyboard keeps [DEVICE_HEADER_KEEP_FIT] (letters about
            // 12 dp): the device beside the words is worth a line, never the keyboard's legibility
            val row = stageRow
            val screen = words.parent as View
            val avail = screen.height - screen.paddingTop - screen.paddingBottom - dots.height - controlBar.height
            val keyboardNeeds = panel.heightAt(minOf(panel.fitForWidth(full), DEVICE_HEADER_KEEP_FIT)) + (row?.paddingTop ?: 0) + (row?.paddingBottom ?: 0)
            val limit = maxOf(tallest, avail - keyboardNeeds)
            // A screen so short that the keyboard can't use the whole width anyway: the spare width is beside the
            // keyboard, so the device goes there and the words keep the whole width
            val stageH = avail - tallest - (row?.paddingTop ?: 0) - (row?.paddingBottom ?: 0)
            val spare = full - panel.widthAt(minOf(panel.fitForWidth(full), panel.fitForHeight(stageH))) - px(DEVICE_HEADER_GAP_DP)
            if (panel.fitForHeight(stageH) < panel.fitForWidth(full) && spare >= px(DEVICE_HEADER_MIN_DP) && row != null) {
                deviceBesideKeyboard = true
                (pictureCol.parent as? ViewGroup)?.removeView(pictureCol)
                row.addView(pictureCol, 0, LinearLayout.LayoutParams(spare, ViewGroup.LayoutParams.MATCH_PARENT).apply { marginEnd = px(DEVICE_HEADER_GAP_DP) })
                pictureCol.visibility = View.VISIBLE
                columnRegular = 0; columnShowcase = 0
                pages.forEachIndexed { i, p -> measuredPages[i] = measure(p, words.width, full).first }
                tallest = measuredPages.values.max()
                reserveDebug = "device beside the keyboard, $spare px"
            } else {
            fun widest(start: Int, group: List<Page>): Int {
                var w = maxOf(start, px(DEVICE_HEADER_MIN_DP))
                while (w >= px(DEVICE_HEADER_MIN_DP)) {
                    if (group.all { p -> measureWith(p, w).let { (height, tidy) -> height <= limit && tidy } }) return w
                    w -= px(Design.SPACE_XS)
                }
                return 0                                   // no room at all: the words come first, the device steps out
            }
            val showcase = pages.filter { it.demo == Demo.WANDER || it.demo == Demo.DEVICES }
            // What each column would like (asked before either is set: once set, deviceColumnPx answers with them)
            val wantRegular = deviceColumnPx(null)
            val wantShowcase = deviceColumnPx(Demo.WANDER)
            columnRegular = widest(wantRegular, pages - showcase.toSet())
            columnShowcase = if (showcase.isEmpty()) 0 else maxOf(columnRegular, widest(wantShowcase, showcase))
            pages.forEachIndexed { i, p -> measuredPages[i] = measureWith(p, deviceColumnPx(p.demo)).first }
            tallest = measuredPages.values.max()
            fitDeviceColumn(pages[now].demo)
            }
        } else pages.forEachIndexed { i, p ->
            val (height, _) = measure(p, words.width)
            tallest = maxOf(tallest, height)
            measuredPages[i] = height
        }
        val p = pages[now]
        title.text = p.title; body.text = p.body; buildRows(p.rows)
        rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
        wordsHeight = tallest
        (words.layoutParams as LinearLayout.LayoutParams).height = tallest
        words.requestLayout()
        startRowLight()
    }

    /** For the checks: where the keyboard window sits on screen, and how big its keys are drawn (1 = full size). */
    internal fun stageForCheck(): Pair<android.graphics.Rect, Float> {
        val loc = IntArray(2); panel.getLocationInWindow(loc)
        return android.graphics.Rect(loc[0], loc[1], loc[0] + panel.width, loc[1] + panel.height) to demo.previewFit
    }

    /** For the checks: the screen's height for words and stage together, and what the stage needs at [StagePanel.MIN_FIT] (px). */
    internal fun budgetForCheck(): Pair<Int, Int> {
        val screen = words.parent as View
        val avail = screen.height - screen.paddingTop - screen.paddingBottom - dots.height - controlBar.height
        val row = stageRow
        return avail to (panel.heightAt(StagePanel.MIN_FIT) + (row?.paddingTop ?: 0) + (row?.paddingBottom ?: 0))
    }

    /** For the checks: the pieces whose placement TourMarkerTest measures. */
    internal class LayoutCheck(val title: View, val words: View, val header: View?, val pictureCol: View, val picture: View, val side: Int)
    /** For the checks: the device columns beside the words (small screens), and what the showcase column wanted. */
    internal fun columnsForCheck() = Triple(columnRegular, columnShowcase, if (header != null && !deviceBesideKeyboard) run {
        val content = resources.displayMetrics.widthPixels - 2 * px(Design.SPACE_XL); (content * DEVICE_HEADER_MAX_SHARE).roundToInt() } else 0)
    internal fun layoutForCheck() = LayoutCheck(title, words, header, pictureCol, picture, px(if (short) Design.SPACE_XL else Design.SPACE_XXL))

    /** For the checks: each page's own words height on this screen. */
    internal val measuredPages = mutableMapOf<Int, Int>()
    /** For the checks: the room every page's words get, and whether any row needed a second line. */
    internal val wordsHeightForCheck get() = wordsHeight
    internal fun rowsWrapped(): List<String> = rowTexts.filter { it.lineCount > 1 || (it.layout?.getEllipsisCount(0) ?: 0) > 0 }.map { it.text.toString() }
    /** The template's limits: split screens have a narrower column, so the title may take two lines and the sentence four. */
    internal val titleLinesAllowed get() = if (split) 2 else 1
    internal val bodyLinesAllowed get() = if (split) 4 else if (header != null) 3 else 2   // beside the device the lines are shorter
    internal fun titleWrapped() = title.lineCount > titleLinesAllowed
    internal fun titleLines() = title.lineCount
    internal fun bodyLines() = body.lineCount

    /**
     * For the checks: how far the device or the keyboard reaches past the room they're given, in pixels
     * (0 when everything shows). Side by side only; in portrait the words scroll instead.
     */
    internal fun stageOverflowPx(): Int {
        val box = (splitRight ?: stageRow ?: return 0)
        fun win(v: View): android.graphics.Rect { val l = IntArray(2); v.getLocationInWindow(l); return android.graphics.Rect(l[0], l[1], l[0] + v.width, l[1] + v.height) }
        val b = win(box).apply { top += box.paddingTop; bottom -= box.paddingBottom }
        var worst = 0
        for (v in listOf(picture, panel)) {
            if (v.visibility != View.VISIBLE || (v === picture && pictureCol.parent !== box)) continue   // beside the words: checked there
            val r = win(v)
            worst = maxOf(worst, b.top - r.top, r.bottom - b.bottom)
        }
        // Split: the words must fit their column too
        if (split) worst = maxOf(worst, wordsOverflowPx())
        return worst
    }

    internal fun wordsOverflowPx(): Int = if (!split) 0 else words.paddingTop + wordsContentHeight() - words.height

    /** For the checks: which part runs past its room ("words" or "stage"). */
    internal fun overflowWhat(): String {
        val w = wordsOverflowPx()
        return if (w > 0) "the words, by $w px" else "the device or keyboard"
    }

    private fun wordsContentHeight(): Int {
        words.measure(View.MeasureSpec.makeMeasureSpec(words.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return words.measuredHeight - words.paddingTop
    }

    // One size per screen for each part of the template (never shrunk to fit: the template makes room)
    private val titleSize get() = if (short) SHORT_TITLE_SP else if (wide) HEADER_TITLE_SP else Design.TYPE_DISPLAY
    private fun bodySp() = if (short) SHORT_TEXT_SP else if (wide) HEADER_TEXT_SP else Design.TYPE_ROW
    private val badgeScale get() = if (short) BADGE_SCALE_SHORT else BADGE_SCALE

    /** Progress: one dot per page, the current one a wider accent pill. On short screens they sit in the control bar. */
    private fun refreshDots() {
        if (short) { dots.visibility = View.GONE; controlBar.dots = pages.size to page; return }
        dots.removeAllViews()
        pages.indices.forEach { i ->
            dots.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Design.RADIUS_PILL
                    setColor(if (i == page) SettingsStyle.accent else Design.DOT_IDLE)
                }
            }, LinearLayout.LayoutParams(if (i == page) px(24f) else px(8f), px(8f)).apply {
                marginStart = px(Design.SPACE_XS / 2)
                marginEnd = px(Design.SPACE_XS / 2)
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

    /**
     * The device showcase: the picture changes with a short "fade through". The old device fades
     * back a little; the new one comes forward from slightly smaller and settles in place.
     */
    private fun showcase(kind: DeviceKind, sticks: Boolean) {
        // The vertical handheld with and without sticks are nearly the same: one turns into the other in place
        if (kind == picture.kind && kind == DeviceKind.VERTICAL) {
            picture.morphTo(kind, settings.buttonStyle, sticks, SHOWCASE_MORPH_MS)
            return
        }
        picture.animate().cancel()
        picture.animate().alpha(0f).scaleX(SHOWCASE_SCALE).scaleY(SHOWCASE_SCALE)
            .setDuration(SHOWCASE_OUT_MS).setInterpolator(Design.EASE_IN)
            .withEndAction {
                picture.show(kind, settings.buttonStyle, sticks)
                picture.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(SHOWCASE_IN_MS).setInterpolator(Design.EASE_OUT).start()
            }.start()
    }

    private fun startDemo(demoKind: Demo) {
        handler.removeCallbacksAndMessages(null)
        beatList = emptyList(); beatHeld = false; beatsShown.clear()
        demo.animate().cancel(); demo.translationY = 0f; demo.alpha = 1f
        feedback.update(settings)            // your own sound pack again (the sound showcase borrows others)
        field.tag = null
        picture.animate().cancel()
        picture.alpha = 1f; picture.scaleX = 1f; picture.scaleY = 1f
        smartDemo = false
        // Every page starts from your own colour and device: the theme demo and the device tour change them
        picture.accent = settings.accent
        picture.show(controls.kind, settings.buttonStyle, controls.sticks)
        field.accent = settings.accent
        // Normal size, so the words always have room above it; hints on, as a new user sees them.
        // The number row only on its own page, and suggestions and accents always on, so every demo looks the same for everyone.
        val base = settings.copy(showHints = true, comboHints = true).forControls(controls).copy(sizeLevel = 0,
            numberRow = demoKind == Demo.NUMBER_ROW, wordSuggestions = true, holdAccents = true)
        demo.applySettings(base)
        demoBase = base
        demo.setSuggestions(emptyList(), "")
        wordPrefix = ""
        picture.held = emptySet()
        picture.holding = emptySet()
        picture.stickPush = emptyMap()
        picture.show(controls.kind, settings.buttonStyle, controls.sticks)
        demo.setField(FieldKind.TEXT)
        demo.closeClips(focusOnHistoryButton = false)
        demo.setHeldShift(false)
        demo.resetShift()                    // after setHeldShift, so the RT hints are gone at once
        demo.setSelecting(false, 0)
        demo.setClips(emptyList())
        typed = ""
        field.accent = settings.accent
        field.placeholder = if (remote) "Type with your remote" else "Type with your controller"
        demo.placeholder = field.placeholder
        field.visibility = if (onTv) View.GONE else View.VISIBLE
        showText("")
        // Android's "Remove animations" is on: one still moment per page, nothing moving
        val moving = WhatsNew.motionAllowed()
        prepareStage(demoKind)
        fitDeviceColumn(demoKind)
        // The loop bar shows on every demo that plays in a loop. The welcome showcase never ends, so it
        // has none, and its devices get that room to be bigger. Elsewhere INVISIBLE when still, so the
        // picture keeps its size.
        loop.restart()
        panel.loopAccent = settings.accent
        panel.loopShown = demoKind != Demo.WANDER && moving
        picture.requestLayout()
        val path = listOf(1 to 0, 1 to 0, 0 to -1, -1 to 0, 0 to 1, 1 to 0)
        var step = 0
        /**
         * Runs [action] in a loop. It returns how long to wait before the next step, or null for [ms].
         * The loop bar fills at an even pace over each run of the demo, so a looping demo says how long
         * its runs take: [firstRun], from now until the second run begins, and [runStart], asked before
         * each later step: the length of the run that step begins, or null if it doesn't begin one.
         * Every looping demo must give both; only a demo with no beginning or end passes null.
         */
        fun every(ms: Long, firstRun: Long?, runStart: (() -> Long?)?, action: () -> Long?) {
            if (!moving) return
            // Steps keep to a fixed timetable (not "this long after the last one ran"), so a run
            // takes exactly as long as the loop bar was told, however busy the screen is
            var due = android.os.SystemClock.uptimeMillis()
            firstRun?.let { loop.begin(it, due) }
            due += ms
            var first = true
            val tick = object : Runnable {
                override fun run() {
                    if (!first) runStart?.invoke()?.let { loop.begin(it, due) }
                    first = false
                    due += action() ?: ms
                    handler.postAtTime(this, due)
                }
            }
            handler.postAtTime(tick, due)
        }
        /**
         * Types [sentence] key by key, pauses, clears, and goes again. A run starts as the text clears;
         * its length is worked out by walking the same keys without moving the highlight.
         */
        fun typeLoop(sentence: String) {
            var at = 0
            fun typing() = typingMs(sentence)
            every(STEP_MS, STEP_MS + typing(), { if (at >= sentence.length) PAUSE_MS + typing() else null }) {
                if (at >= sentence.length) { at = 0; typed = ""; showText(""); return@every PAUSE_MS }
                when (demo.stepTowards(sentence[at].toString())) {
                    true -> { demo.pressSelected(); at++; if (at == sentence.length) PAUSE_MS else PRESS_MS }
                    false -> null
                    null -> { at++; null }      // not on this keyboard (never, for plain letters)
                }
            }
        }
        // Pages with button rows play one row at a time (see [Beat]); the others loop on their own
        val beats = beatsFor(demoKind)
        if (beats != null) runBeats(beats, moving) else when (demoKind) {
            Demo.WANDER -> {
                // The highlight wanders while the picture shows every device Inlay knows, yours first
                val all = listOf(DeviceKind.CONTROLLER to true, DeviceKind.WIDE to true, DeviceKind.VERTICAL to false,
                    DeviceKind.VERTICAL to true, DeviceKind.REMOTE to false)
                val start = all.indexOfFirst { it.first == controls.kind && (it.first != DeviceKind.VERTICAL || it.second == controls.sticks) }.coerceAtLeast(0)
                val tour = all.drop(start) + all.take(start)
                every(700, firstRun = null, runStart = null) {
                    val (dx, dy) = path[step++ % path.size]
                    demo.moveFocus(dx, dy)
                    if (moving && step % DEVICE_STEPS == 0) {
                        val (kind, sticks) = tour[(step / DEVICE_STEPS) % tour.size]
                        showcase(kind, sticks)
                    }
                    null
                }
            }
            Demo.DEVICES -> {
                // Your own device first, then the other two kinds, while the keyboard moves (lighting the D-pad)
                val yours = DevicePicture.resolve(this, settings.picture)
                val kinds = listOf(yours) + DeviceKind.values().filter { it != yours }
                var shown = 0
                val run = 700L * 4 * kinds.size
                every(700, 700L + run, { if (step % (4 * kinds.size) == 0) run else null }) {
                    val (dx, dy) = path[step++ % path.size]
                    demo.moveFocus(dx, dy)
                    if (step % 4 == 0) {
                        shown = (shown + 1) % kinds.size
                        showcase(kinds[shown], controls.sticks)
                    }
                    null
                }
            }
            Demo.SMART -> {
                // Types a short line the lazy way; the capital, the lone i and the stray space fix themselves
                val sentence = SMART_LINE
                if (!moving) { typed = smartFix(sentence); showText(typed) }
                smartDemo = true
                typeLoop(sentence)
            }
            Demo.SOUNDS -> runSteps(soundSteps(), moving, still = 1)
            Demo.NUMBER_ROW -> {
                // Types a short line with letters and the number row, pauses, clears, and goes again
                val sentence = texts.numbers
                if (!moving) showText(sentence)
                typeLoop(sentence)
            }
            Demo.YOURS -> {
                // Themes and highlight colours change, and the button icons switch Xbox → PlayStation → Switch.
                // A loop of YOURS_STEPS looks that ends back on your own, so it repeats exactly.
                showText("")
                val run = 1500L * YOURS_STEPS
                every(1500, 1500L + run, { if (step % YOURS_STEPS == 0) run else null }) {
                    step++
                    val k = step % YOURS_STEPS
                    val tones = Palette.tones
                    val styles = ButtonStyle.values()
                    val shown = if (k == 0) base else base.copy(
                        toneIndex = (settings.toneIndex + k) % tones.size,
                        accentIndex = (settings.accentIndex.coerceAtLeast(0) + k * 3) % Palette.accents.size,
                        buttonStyle = styles[(settings.buttonStyle.ordinal + k) % styles.size])
                    demo.applySettings(shown)
                    picture.accent = shown.accent
                    panel.loopAccent = shown.accent
                    picture.show(controls.kind, shown.buttonStyle, controls.sticks)
                    null
                }
            }
            else -> Unit                                     // played as beats, above
        }
        startRowLight()
    }

    // ---- Beats: the words lead, the demo follows ---------------------------
    /*
     * Every page with button rows plays as beats, one per row, in the rows' order. In a beat the row's words show
     * (the caption on small screens, the lit row in the list on wide ones), the device rings the row's buttons
     * and the keyboard tints its key. After a short lead (time to read) the demo acts out just that row; the
     * typing and the lit buttons can be quick, the words never are. Then the result stays a moment.
     *
     * How long a beat lasts ([beatMs]): the lead, the acting and the tail, but never less than [BEAT_MIN_MS],
     * nor less than it takes to read the row and look over at the device and the keyboard. Simple rows (Space)
     * take 3 s, rows with typing 5 to 7 s; a page's run stays around 15 to 25 s. TourBeatTest checks every page
     * on every device: one beat per row, each beat acts out its row's buttons, and the lengths stay in range.
     *
     * D-pad up and down step through the rows and stay on the one you picked; the loop line rests until the page
     * changes. With "Remove animations" on, the rows still take turns, each showing its result at once.
     */

    /** A piece of a beat: [ms] walks it through without showing anything and says how long it takes; [play] does it. */
    private class Act(val ms: () -> Long, val play: () -> (() -> Long?))

    /**
     * One row's turn. [setup] puts the text box (and anything a jump straight to this row needs) where the beat
     * starts; in the normal order that's where the previous beat left it, so nothing visibly jumps.
     */
    private class Beat(val row: Int, val setup: () -> Unit, val acts: List<Act>)

    /** Does [real] (and [both], which also runs in the dry walk-through: moves that change where the highlight is), then waits [ms]. */
    private fun act(ms: Long, both: (() -> Unit)? = null, real: () -> Unit = {}) = Act({ both?.invoke(); ms }) {
        var done = false
        val p: () -> Long? = { if (done) null else { done = true; both?.invoke(); real(); ms } }
        p
    }

    /** D-pad steps ([stepMs] each) until [step] says it's there, then a press if [press], then [wait]. */
    private fun walk(press: Boolean, wait: Long, stepMs: Long = STEP_MS, step: () -> Boolean?) = Act({
        var n = 0
        while (n < MAX_WALK && step() == false) n++
        if (press) demo.pressSelected()
        n * stepMs + wait
    }) {
        var walked = 0
        var arrived = false
        val p: () -> Long? = {
            if (arrived) null else when (if (walked >= MAX_WALK) null else step()) {
                false -> { walked++; stepMs }
                else -> { arrived = true; if (press) demo.pressSelected(); wait }
            }
        }
        p
    }

    /** Types [text] key by key. */
    private fun typeActs(text: String, after: (Int) -> Unit = {}) = text.mapIndexed { i, ch ->
        listOf(walk(press = true, wait = PRESS_MS) { demo.stepTowards(ch.toString()) }, act(0L) { after(i) })
    }.flatten()

    private fun rowWords(row: Int) = shownRows.getOrNull(row)?.first.orEmpty()
    private fun leadMs(row: Int) = (BEAT_LEAD_BASE_MS + rowWords(row).length * BEAT_LEAD_PER_LETTER_MS).coerceIn(BEAT_LEAD_MIN_MS, BEAT_LEAD_MAX_MS)

    /** How long beat [b] lasts (see the top of this section). Inside a dry run, so the walks are counted from where the last beat left the highlight. */
    private fun beatMs(b: Beat): Long {
        val acting = b.acts.sumOf { it.ms() }
        val read = BEAT_LOOK_MS + rowWords(b.row).length * 1000L / BEAT_CPS
        return maxOf(BEAT_MIN_MS, read, leadMs(b.row) + acting + BEAT_TAIL_MS)
    }

    private val beatToken = Any()
    private var beatList: List<Beat> = emptyList()
    private var beatLens: LongArray = LongArray(0)
    private var beatAt = 0
    /** Stepped to by hand (D-pad up or down): the beat stays until the next step or page. */
    private var beatHeld = false
    /** The page has just opened: the first beat waits [PAGE_READ_MS] more, for the title and the words above. */
    private var beatFirstRun = true
    private val beatsShown = mutableSetOf<Int>()

    private fun runBeats(list: List<Beat>, moving: Boolean) {
        handler.removeCallbacks(rowTick)
        rowLight = null; loop.onRun = null; picture.onLitChanged = null
        beatList = list; beatAt = 0; beatHeld = false; beatFirstRun = true
        if (list.isEmpty()) return
        playBeat(0, android.os.SystemClock.uptimeMillis(), moving)
    }

    private fun playBeat(i: Int, start: Long, moving: Boolean) {
        handler.removeCallbacksAndMessages(beatToken)
        val b = beatList[i]
        beatAt = i
        val extra = if (i == 0 && beatFirstRun && !beatHeld) PAGE_READ_MS else 0L
        if (i == 0 && !beatHeld || beatLens.size != beatList.size) {
            // A run begins: every beat's length, walked through from where the highlight is now, so the loop
            // line knows exactly how long this run takes
            beatLens = demo.dryRun { beatList.map { beatMs(it) } }.toLongArray()
            if (moving && !beatHeld) loop.begin(beatLens.sum() + extra, start)
        }
        b.setup()
        showBeat(b.row)
        if (i == 0) beatFirstRun = false
        if (!moving) {
            // Animations off: the row's result shows at once
            b.acts.forEach { a -> val p = a.play(); while (p() != null) Unit }
        } else {
            var due = start + leadMs(b.row) + extra
            var k = 0
            var player: (() -> Long?)? = null
            val step = object : Runnable {
                override fun run() {
                    while (k < b.acts.size) {
                        val p = player ?: b.acts[k].play().also { player = it }
                        val wait = p()
                        if (wait != null) { due += wait; handler.postAtTime(this, beatToken, due); return }
                        player = null; k++
                    }
                }
            }
            handler.postAtTime(step, beatToken, due)
        }
        if (!beatHeld) {
            val end = start + beatLens[i] + extra
            handler.postAtTime({ playBeat((i + 1) % beatList.size, end, moving) }, beatToken, end)
        }
    }

    /** Row [row] has its turn: the words, the device's ring and the keyboard's tint all point at it. */
    private fun showBeat(row: Int) {
        lightRow(row)
        beatsShown += row
        val parts = HintText.parse(shownRows.getOrNull(row)?.second ?: return)
        demo.spotlight = parts.filterIsInstance<HintPart.Badge>().filter { !it.held }.map { it.button }.toSet()
        picture.hinted = RowLight(listOf(parts)) { _, _ -> }.keysOf(0)
    }

    /** Everything back to rest (a jump between rows): no buttons held, nothing open, the keyboard in place. */
    private fun calmDemo() {
        demo.animate().cancel(); demo.translationY = 0f; demo.alpha = 1f
        demo.resetAccents()
        demo.setHeldShift(false)
        demo.resetShift(); demo.resetLayer()
        demo.setSelecting(false, 0)
        demo.closeClips(focusOnHistoryButton = false)
        demo.applySettings(demoBase)
        picture.held = emptySet(); picture.holding = emptySet()
    }

    /** D-pad up and down step through the rows and stay on the one you pick (the loop line rests). */
    private fun stepBeat(dir: Int): Boolean {
        if (beatList.size < 2 || wordsScroll != null) return false
        beatHeld = true
        loop.restart()
        calmDemo()
        playBeat((beatAt + dir + beatList.size) % beatList.size, android.os.SystemClock.uptimeMillis(), WhatsNew.motionAllowed())
        return true
    }

    private fun setTyped(text: String) { typed = text; showText(text) }

    /** The beats for this page's rows (each page's rows are known, so every row has its beat; TourBeatTest checks). */
    private fun beatsFor(kind: Demo): List<Beat>? = when (kind) {
        Demo.TYPE -> typeBeats()
        Demo.SHIFT_AND_SYMBOLS -> shiftBeats()
        Demo.SHIFT_REMOTE -> shiftRemoteBeats()
        Demo.HOLD_RT -> holdRtBeats()
        Demo.ACCENTS_AND_WORDS -> listOf(accentBeat(0), plainLetterBeat(1), wordBeat(2, another = false))
        Demo.ACCENTS -> accentPageBeats()
        Demo.WORDS -> shownRows.indices.map { wordBeat(it, another = it > 0) }
        Demo.EDIT -> editBeats()
        else -> null
    }?.takeIf { shownRows.isNotEmpty() }

    /** Which buttons row [i] presses, as the device picture names them (A, X, Dup…). */
    private fun keysOfRow(i: Int) = RowLight(listOf(HintText.parse(shownRows[i].second))) { _, _ -> }.keysOf(0)

    /** Move and type: Type adds the second word, Delete takes it away (a tap or two, then held), Space, Enter sends it, Close. */
    private fun typeBeats(): List<Beat> {
        val sentence = texts.search
        val cut = sentence.indexOf(' ').let { if (it < 0) 0 else it + 1 }
        val head = sentence.take(cut)
        val first = head.trimEnd()
        val tail = sentence.drop(cut)
        return shownRows.indices.map { i ->
            val keys = keysOfRow(i)
            when {
                "A" in keys -> Beat(i, { setTyped(head); demo.setSuggestions(emptyList(), "") }, typeActs(tail))
                "X" in keys -> {
                    val n = sentence.length - first.length
                    val taps = minOf(2, n)
                    Beat(i, { setTyped(sentence) },
                        List(taps) { act(DELETE_TAP_MS) { demo.pressShortcut("X", KeyType.DELETE) } } +
                            (if (n > taps) listOf(act(DELETE_HOLD_WAIT_MS) { demo.holdButton("X", true) }) +
                                List(n - taps) { act(DELETE_REPEAT_MS) { demo.pressShortcut("X", KeyType.DELETE, lit = false) } } +
                                act(0L) { demo.holdButton("X", false) } else emptyList()))
                }
                "Y" in keys -> Beat(i, { setTyped(first) }, listOf(act(0L) { demo.pressShortcut("Y", KeyType.SPACE) }))
                // Sent: the box empties, as a search or a message would
                "START" in keys -> Beat(i, { setTyped(head) }, listOf(act(SENT_MS) { demo.pressShortcut("START", KeyType.ENTER) }, act(0L) { setTyped("") }))
                "B" in keys -> Beat(i, { setTyped("") }, closeActs())
                else -> Beat(i, { setTyped(head) }, List(RING_STEPS) { k ->     // a remote's ring
                    act(RING_STEP_MS, both = { val (dx, dy) = RING_PATH[k % RING_PATH.size]; demo.moveFocus(dx, dy) })
                })
            }
        }
    }

    /** B: the keyboard slides away, stays away a moment, and comes back. */
    private fun closeActs() = listOf(
        act(CLOSED_MS) {
            picture.flash("B")
            demo.animate().translationY(demo.height.toFloat()).alpha(0f).setDuration(Design.MOTION_PANEL).setInterpolator(Design.EASE_IN).start()
        },
        act(0L) { demo.animate().translationY(0f).alpha(1f).setDuration(Design.MOTION_PANEL).setInterpolator(Design.EASE_OUT).start() })

    /** Triggers: RT shift, then caps lock; LT the symbols and back; L3 each keyboard size and back. */
    private fun shiftBeats(): List<Beat> = shownRows.indices.map { i ->
        val keys = keysOfRow(i)
        val reset = { demo.resetShift(); demo.resetLayer(); field.tag = null; showText(texts.shiftLine) }
        when {
            "RT" in keys -> Beat(i, reset, listOf(act(SHIFT_SEE_MS) { demo.cycleShift() }, act(0L) { demo.cycleShift() }))
            "LT" in keys -> Beat(i, reset, listOf(act(SHIFT_SEE_MS + 400L) { demo.toggleLayer() }, act(0L) { demo.toggleLayer() }))
            // Each press of L3 the next size; the text box names it, since the stage fits the keyboard to its room
            else -> Beat(i, { reset(); demo.applySettings(demoBase); field.tag = SIZE_NAMES[0] }, (1..SIZE_NAMES.size).map { k ->
                act(if (k < SIZE_NAMES.size) SHIFT_SEE_MS else 0L) {
                    picture.flash("LS")
                    demo.applySettings(demoBase.copy(sizeLevel = k % SIZE_NAMES.size))
                    field.tag = SIZE_NAMES[k % SIZE_NAMES.size]
                }
            })
        }
    }

    /** A remote: over to Shift, OK, "Dune" with its capital; then over to ?123, OK, a 2, and back to the letters. */
    private fun shiftRemoteBeats(): List<Beat> {
        val word = texts.shiftLine.substringBefore(':').substringBefore(' ').lowercase()
        return shownRows.indices.map { i ->
            if (i == 0) Beat(i, { demo.resetShift(); demo.resetLayer(); setTyped("") },
                listOf(walk(press = true, wait = PRESS_MS + 300L, stepMs = TOOL_STEP_MS) { demo.stepToKey(KeyType.SHIFT) }) + typeActs(word))
            else Beat(i, { demo.resetShift(); demo.resetLayer(); setTyped(word.replaceFirstChar { it.uppercase() } + " ") },
                listOf(walk(press = true, wait = PRESS_MS + 300L, stepMs = TOOL_STEP_MS) { demo.stepToKey(KeyType.LAYER) }) +
                    typeActs(texts.numbers.filter { it.isDigit() }.take(1).ifEmpty { "2" }) +
                    walk(press = true, wait = 0L, stepMs = TOOL_STEP_MS) { demo.stepToKey(KeyType.LAYER) })
        }
    }

    /** Hold RT: one capital; jump a word at a time; accent the last letter; undo. RT stays held through each. */
    private fun holdRtBeats(): List<Beat> {
        val line = texts.jump.replaceFirstChar { it.uppercase() }
        val firstWord = texts.jump.substringBefore(' ')
        val parts = wordsOf(line)
        // Accent the last letter: the demo's own word when one press of Y gives its accent, otherwise "café"
        val t = texts
        val own = Accents.next(t.accentLetter, settings.language) == t.accentChoice
        val before = if (own) t.accentBefore + t.accentLetter else "caf" + "e"
        val accented = if (own) t.accentBefore + t.accentChoice else "caf" + Accents.next('e', settings.language).orEmpty()
        val rest = if (own) t.accentAfter else ""
        val rtOn = act(RT_FIRST_MS) { demo.setHeldShift(true) }
        val rtOff = act(0L) { demo.setHeldShift(false) }
        var cursor = parts.size
        return shownRows.indices.map { i ->
            val keys = keysOfRow(i)
            when {
                "A" in keys -> Beat(i, { demo.setHeldShift(false); setTyped("") },
                    listOf(rtOn, walk(press = true, wait = PRESS_MS + 200L) { demo.stepTowards(firstWord.take(1)) }, act(RT_LET_GO_MS) { demo.setHeldShift(false) }) +
                        typeActs(firstWord.drop(1)))
                "LB" in keys || "RB" in keys -> Beat(i, { demo.setHeldShift(false); cursor = parts.size; showText(line) },
                    listOf(rtOn) + (List(2) { -1 } + List(2) { 1 }).map { d ->
                        act(JUMP_MS) {
                            cursor += d
                            picture.flash(if (d < 0) "LB" else "RB")
                            showText(parts.take(cursor).joinToString(""), "", parts.drop(cursor).joinToString(""))
                        }
                    } + rtOff)
                "Y" in keys -> Beat(i, { demo.setHeldShift(false); setTyped(before) },
                    listOf(rtOn, act(RT_SEE_MS) { picture.flash("Y"); demo.feedback?.key(); setTyped(accented) }, act(RT_LET_GO_MS) { demo.setHeldShift(false) }) +
                        typeActs(rest))
                else -> Beat(i, { demo.setHeldShift(false); setTyped(accented + rest) },       // undo: back to before the last typing
                    listOf(rtOn, act(RT_SEE_MS) { picture.flash("X"); demo.feedback?.off(); setTyped(accented) }, rtOff))
            }
        }
    }

    private fun accentTarget(): Pair<Int, Int> {
        val list = Accents.variants(texts.accentLetter, settings.language).orEmpty()
        val target = list.indexOf(texts.accentChoice).coerceAtLeast(1)
        return list.size to target
    }

    /** Hold A on the letter: the accents rise, along to the one we want, let go: typed, and the rest of the word. */
    private fun accentBeat(row: Int): Beat {
        val t = texts
        val (_, target) = accentTarget()
        return Beat(row, { demo.resetAccents(); demo.setSuggestions(emptyList(), ""); setTyped(t.accentBefore) },
            listOf(walk(press = false, wait = 0L) { demo.stepTowards(t.accentLetter.toString()) },
                act(ACCENT_OPEN_MS) { demo.pressA() }) +
                List(target - 1) { act(ACCENT_STEP_MS) { demo.moveFocus(1, 0) } } +
                act(ACCENT_TYPED_MS) { demo.releaseA() } +
                act(0L) { setTyped(typed + t.accentAfter) })
    }

    /** Hold A, let go: the accents stay open; B: the plain letter stays. */
    private fun plainLetterBeat(row: Int): Beat {
        val t = texts
        return Beat(row, { demo.resetAccents(); setTyped(t.accentBefore) },
            listOf(walk(press = false, wait = 0L) { demo.stepTowards(t.accentLetter.toString()) },
                act(ACCENT_OPEN_MS) { demo.pressA() },
                act(ACCENT_STEP_MS) { demo.releaseA() },
                act(ACCENT_STEP_MS) { demo.moveFocus(1, 0) },
                act(0L) { picture.flash("B"); demo.cancelAccents() }))
    }

    /** What's new, Hold A: show them (held), let go to type one, let go first and choose later, and B. */
    private fun accentPageBeats(): List<Beat> {
        val t = texts
        val (_, target) = accentTarget()
        val toLetter = walk(press = false, wait = 0L) { demo.stepTowards(t.accentLetter.toString()) }
        val along = List(target - 1) { act(ACCENT_STEP_MS) { demo.moveFocus(1, 0) } }
        return listOf(
            Beat(0, { demo.resetAccents(); setTyped(t.accentBefore) }, listOf(toLetter, act(ACCENT_OPEN_MS) { demo.pressA() })),
            // Straight on from the row before (A still held); reached by a jump, it holds A first
            Beat(1, {}, listOf(Act({ 0L }) {
                var done = false
                val p: () -> Long? = { if (done) null else { done = true; if (demo.isAccentsOpen()) 0L else { setTyped(t.accentBefore); demo.pressA(); ACCENT_OPEN_MS } } }
                p
            }) + along + act(ACCENT_TYPED_MS) { demo.releaseA() } + act(0L) { setTyped(typed + t.accentAfter) }),
            Beat(2, { demo.resetAccents(); setTyped(t.accentBefore) },
                listOf(toLetter, act(ACCENT_OPEN_MS) { demo.pressA() }, act(ACCENT_STEP_MS) { demo.releaseA() }) + along +
                    act(PRESS_MS) { demo.pressA() } + act(ACCENT_TYPED_MS) { demo.releaseA() } + act(0L) { setTyped(typed + t.accentAfter) }),
            plainLetterBeat(3)
        ).filter { it.row < shownRows.size }
    }

    /** Types the start of a word, the suggestions follow each letter; then takes one (the right stick, or up and A). */
    private fun wordBeat(row: Int, another: Boolean): Beat {
        val t = texts
        val guesses = t.wordOthers + listOf(t.wordGuesses)
        val keys = keysOfRow(row)
        val take = if ("RS" in keys) listOf(act(0L) { picture.flash("RS", 420); demo.bestWord()?.let { onWord(it) } })
        else listOf(act(WORD_UP_MS, both = { demo.focusBestWord() }) { picture.flash("Dup", 300) }) +
            (if (another) listOf(act(WORD_UP_MS, both = { demo.moveFocus(1, 0) })) else emptyList()) +
            act(0L) { demo.pressSelected() }
        return Beat(row, { demo.resetAccents(); wordPrefix = ""; demo.setSuggestions(emptyList(), ""); setTyped(t.wordBefore) },
            typeActs(t.wordTyped) { i ->
                wordPrefix = typed.substringAfterLast(' ')
                demo.setSuggestions(guesses.getOrElse(i) { t.wordGuesses }, wordPrefix)
            } + take)
    }

    /** Select, copy and paste: the cursor, Select, the selection grows, Select twice for all, then Copy and the clock. */
    private fun editBeats(): List<Beat> {
        val parts = wordsOf(texts.copy)
        val all = parts.joinToString("")
        val earlier = listOf(texts.stick, "github.com/dakingeman/inlay", texts.search)
        var at = all.length
        var grown = 0
        val rest = { demo.closeClips(focusOnHistoryButton = false); demo.setClips(emptyList()); demo.setSelecting(false, 0) }
        return shownRows.indices.map { i ->
            when (i) {
                0 -> Beat(i, { rest(); at = all.length; showText(all) }, (List(3) { -1 } + List(2) { 1 }).map { d ->
                    act(CURSOR_MS) { at += d; picture.flash(if (d < 0) "LB" else "RB"); showText(all.take(at), "", all.drop(at)) }
                })
                1 -> Beat(i, { rest(); showText("", "", all) },
                    listOf(act(0L, both = { demo.setSelecting(true, 0) }) { picture.flash("SELECT") }))
                2 -> Beat(i, { rest(); grown = 0; demo.setSelecting(true, 0); showText("", "", all) }, List(3) { k ->
                    act(if (k < 2) GROW_MS else 0L, both = { demo.setSelecting(true, parts.take(k + 1).joinToString("").length) }) {
                        grown = k + 1
                        picture.flash("RB")
                        showText("", parts.take(grown).joinToString(""), parts.drop(grown).joinToString(""))
                    }
                })
                else -> Beat(i, { rest(); demo.focusKey(KeyType.SPACE); showText(all) }, listOf(
                    act(SELECT_TWICE_MS) { picture.flash("SELECT") },
                    act(GROW_MS, both = { demo.setSelecting(true, all.length) }) { picture.flash("SELECT"); showText("", all, "") },
                    walk(press = false, wait = 0L, stepMs = TOOL_STEP_MS) { demo.stepToTool(Tool.COPY) },
                    act(GROW_MS, both = { demo.pressSelected(); demo.setSelecting(false, 0) }) { showText(all); demo.setClips(listOf(all) + earlier) },
                    walk(press = true, wait = CLIPS_MS, stepMs = TOOL_STEP_MS) { demo.stepToTool(Tool.HISTORY) }))
            }
        }
    }

    // ---- Acted-out steps for the new features ------------------------------
    // Each step does one thing and says how long to wait before the next. The list loops.

    /**
     * Runs [steps] in a loop. With "Remove animations" on, only the first [still] steps run,
     * leaving one still moment (the accents open, or the words showing).
     * Each step moves the loop bar on by one step's share, so these demos get it by themselves.
     */
    private fun runSteps(steps: List<Step>, moving: Boolean, still: Int) {
        var i = 0
        if (!moving) {
            steps.take(still).forEach { it.run() }
            return
        }
        val run = steps.sumOf { it.ms }
        var due = android.os.SystemClock.uptimeMillis()
        loop.begin(FIRST_STEP_MS + run, due)
        due += FIRST_STEP_MS
        val tick = object : Runnable {
            override fun run() {
                if (i > 0 && i % steps.size == 0) loop.begin(run, due)
                val step = steps[i % steps.size]
                step.run()
                i++
                due += step.ms
                handler.postAtTime(this, due)
            }
        }
        handler.postAtTime(tick, due)
    }

    /** One acted-out step: what it does, then how long it waits before the next. */
    private class Step(val ms: Long, val run: () -> Unit)

    /**
     * How long typing [text] takes in the demo, from the highlight where it is now: every D-pad step,
     * every press, and the pause after the last letter. Worked out without moving the highlight.
     */
    private fun typingMs(text: String): Long = demo.dryRun {
        var total = 0L
        text.forEachIndexed { i, ch ->
            var walked = 0
            while (true) {
                when (demo.stepTowards(ch.toString())) {
                    true -> { total += if (i == text.lastIndex) PAUSE_MS else PRESS_MS; break }
                    false -> { total += STEP_MS; if (++walked > MAX_WALK) break }
                    null -> { total += STEP_MS; break }
                }
            }
        }
        total
    }

    /**
     * The device and the keyboard share the stage. The device's column is as wide as its shape
     * needs (a tall handheld or a remote takes less, a wide handheld more), so the keyboard gets
     * the rest. The screen-in-screen page has no device; the devices page keeps the widest column,
     * so nothing moves while its picture changes.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun prepareStage(demoKind: Demo) = Unit

    private fun focusOn(label: String) {
        repeat(24) { if (demo.stepTowards(label) == true) return }
    }

    /**
     * The sound showcase: every pack in [SoundPack], in Settings order. For each, its name pops up in
     * the text box, the keyboard types the name in that pack's sounds, and Enter plays its confirm.
     * Sound only, no vibration, and your own pack comes back when the page changes. A new pack shows
     * up here by itself: nothing to add.
     */
    private fun soundSteps(): List<Step> = SoundPack.values().flatMap { pack ->
        val name = pack.label.lowercase()
        listOf(Step(SOUND_LEAD_MS) {
            feedback.showcasePack(pack)                // loads while its name shows, before the first key
            field.tag = pack.label
            typed = ""; showText("")
        }) + name.map { ch ->
            Step(SOUND_KEY_MS) {
                focusOn(ch.toString())
                demo.pressSelected()
                feedback.showcaseKey()
            }
        } + Step(SOUND_HOLD_MS) { feedback.showcaseConfirm() }
    }


    private var wordPrefix = ""

    override fun onWord(word: String) {
        typed = typed.dropLast(wordPrefix.length) + word + " "
        wordPrefix = ""
        demo.setSuggestions(emptyList(), "")
        showText(typed)
    }

    override fun onReplaceLast(expected: String, replacement: String): Boolean {
        if (!typed.endsWith(expected)) return false
        typed = typed.dropLast(expected.length) + replacement
        showText(typed)
        return true
    }

    // ---- What the demo keyboard types lands in the text box ----------------
    override fun onText(text: String) { typed += text; if (smartDemo) typed = smartFix(typed); showText(typed) }

    /** The "Tidies up" demo: the same three fixes the keyboard makes, applied to the demo's text. */
    private var smartDemo = false
    /** The demo keyboard's own settings on this page (your look, normal size, hints on). */
    private var demoBase = KeyboardSettings()
    private fun smartFix(text: String): String {
        var t = text
        t = Regex("(^|[ ])i(?= )").replace(t) { it.groupValues[1] + "I" }           // a lone i
        t = Regex("([\\p{L}\\d]) ([.,!?])").replace(t) { it.groupValues[1] + it.groupValues[2] + " " }.replace("  ", " ")   // "word ." -> "word. "
        t = Regex("(^|[.!?] )(\\p{Ll})").replace(t) { it.groupValues[1] + it.groupValues[2].uppercase() }   // a capital to start
        return t
    }
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
            KeyEvent.KEYCODE_DPAD_UP -> if (!stepBeat(-1)) wordsScroll?.smoothScrollBy(0, -px(SCROLL_DP))
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!stepBeat(1)) wordsScroll?.smoothScrollBy(0, px(SCROLL_DP))
            in UNUSED_PAD_KEYS -> Unit          // taken, so Android doesn't turn a stick click into "next"
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode in UNUSED_PAD_KEYS) true else super.onKeyUp(keyCode, event)

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
        /** "WELCOME TOUR" or the "NEW IN" tag's words: the highlight colour, kept at 4.5:1 on the brightest backdrop behind them. */
        /** The current-row marker: your colour, lifted where needed to 3:1 on the backdrop's light (a graphic, not text). */
        fun markerColor(accent: Int, whatsNew: Boolean): Int = Contrast.accentText(accent, Backdrop.peak(accent, whatsNew), min = 3.1)

        fun labelColor(accent: Int, whatsNew: Boolean): Int {
            val peak = Backdrop.peak(accent, whatsNew)
            // 4.6, a hair over 4.5: the light is measured on a small render, so leave room for its rounding
            return Contrast.accentText(accent, if (whatsNew) Contrast.over(Contrast.withAlpha(accent, Design.ALPHA_TINT_TAG), peak) else peak, min = 4.6)
        }

        /** The sound pack's name tag in the text box: the highlight colour on its own tint, 4.5:1. */
        fun fieldTagColor(accent: Int): Int =
            Contrast.accentText(accent, Contrast.over(Contrast.withAlpha(accent, Design.ALPHA_TINT_TAG), SettingsStyle.surface))

        /** Open this screen as "What's new" instead of the tour. */
        const val EXTRA_WHATS_NEW = "whats_new"
        /** The sound showcase's pace: the name shows and the pack loads, each letter, then Enter and a breath. */
        private const val SOUND_LEAD_MS = 520L
        private const val SOUND_KEY_MS = 175L
        private const val SOUND_HOLD_MS = 760L
        /** The window around the text box and keyboard on the tour's background: corners, black fill and one solid edge (as in the GitHub kit). */
        private const val PANEL_RADIUS_DP = 18f
        private const val KEYBOARD_RADIUS_DP = 10f   // = INNER_RADIUS_DP: nested inside the window (18 - 8)
        private val PANEL_FILL = Design.SURFACE_BG
        private val PANEL_EDGE = Design.TRACK
        private const val SCROLL_DP = 80f
        const val KEY_PAGE = "page"
        const val CLOSE_DELAY_MS = 140L
        const val TV_SAFE = 0.05f
        const val BADGE_SCALE = Design.BADGE_ROW
        const val BADGE_GAP = Design.BADGE_GAP
        const val TEXT_GAP = Design.BADGE_TEXT_GAP
        const val ROW_HEIGHT_DP = 44f       // comfortable rows, like a list
        const val GRID_ROW_HEIGHT_DP = 40f  // the grid across the top: a little tighter
        const val BODY_MAX_WIDTH_DP = 520f
        const val FIELD_HEIGHT_DP = 40f   // at the keyboard's full size; it scales with the keyboard
        const val STEP_MS = 85L             // one D-pad step
        const val PRESS_MS = 190L           // a breath after each letter
        const val PAUSE_MS = 1400L          // read the result before it starts over
        // Beats (one row at a time), tuned to be just long enough: read the row, look at the device and the
        // keyboard, see it happen, see the result. The typing and the lit buttons stay quick.
        const val BEAT_MIN_MS = 3000L       // the least any row stays (one press, like Space)
        const val BEAT_LOOK_MS = 1200L      // to look from the words to the device and the keyboard
        const val BEAT_CPS = 15L            // letters read a second (subtitles allow 15 to 17)
        const val BEAT_LEAD_BASE_MS = 500L  // the words show this long before the demo acts...
        const val BEAT_LEAD_PER_LETTER_MS = 30L //  ...plus a little for each letter of the row...
        const val BEAT_LEAD_MIN_MS = 800L   //  ...between these two
        const val BEAT_LEAD_MAX_MS = 1400L
        const val BEAT_TAIL_MS = 1000L      // the result stays this long before the next row
        const val PAGE_READ_MS = 1200L      // a page's first row waits this much more, for the title above
        const val DELETE_TAP_MS = 650L      // Delete: a tap, then another
        const val DELETE_HOLD_WAIT_MS = 450L //  then held: it waits, as the real one does, then repeats
        const val DELETE_REPEAT_MS = 110L
        const val SENT_MS = 700L            // Enter: the text shows a moment, then it's sent
        const val CLOSED_MS = 1400L         // Close: the keyboard stays away this long, then comes back
        const val RING_STEPS = 6            // a remote's ring: the highlight takes six steps
        const val RING_STEP_MS = 450L
        val RING_PATH = listOf(1 to 0, 1 to 0, 0 to 1, -1 to 0, 0 to -1, -1 to 0)
        const val SHIFT_SEE_MS = 1100L      // a trigger tap's result shows this long before the next
        val SIZE_NAMES = listOf("Normal", "Large", "Largest")   // as Settings names them, and as L3 goes round
        const val RT_FIRST_MS = 600L        // RT goes down (its tips fade in) this long before the other button
        const val RT_SEE_MS = 1000L         // what the combo did shows this long before RT is let go
        const val RT_LET_GO_MS = 400L
        const val JUMP_MS = 650L            // the cursor jumping a word
        const val ACCENT_OPEN_MS = 900L     // A held: the accents rise (after a moment) and show
        const val ACCENT_STEP_MS = 450L     // along the accents
        const val ACCENT_TYPED_MS = 700L    // let go: the accent shows typed before the rest of the word
        const val WORD_UP_MS = 650L         // up to the words
        const val CURSOR_MS = 380L          // the text cursor a letter at a time
        const val GROW_MS = 700L            // the selection a word at a time
        const val SELECT_TWICE_MS = 250L    // Select pressed twice
        const val CLIPS_MS = 2000L          // the clipboard stays open this long
        const val DEVICE_STEPS = 4             // the welcome page shows the next device every 4 steps (2.8 s)
        const val YOURS_STEPS = 6              // the "make it yours" page: five other looks, then yours again
        const val FIRST_STEP_MS = 400L      // acted-out steps begin this long after the page opens
        const val MAX_WALK = 40             // a demo walk never takes more D-pad steps than this
        const val TOOL_STEP_MS = 170L       // a calm D-pad step up to the top row
        const val SHOWCASE_OUT_MS = Design.MOTION_SHOWCASE_OUT   // the device showcase: the old one fades back...
        const val SHOWCASE_IN_MS = Design.MOTION_SHOWCASE_IN     // ...and the new one settles in
        const val SHOWCASE_SCALE = 0.94f
        const val SHOWCASE_MORPH_MS = Design.MOTION_MORPH        // a near-identical device changes in place instead
        const val PICTURE_NARROW_DP = 150f  // portrait: the device's height above the keyboard
        const val ROW_TEXT_GAP_DP = Design.SPACE_S   // between a row's buttons and its words
        const val ROW_GAP_DP = 2f                    // between rows in the list
        const val MARKER_WIDTH_DP = Design.SPACE_XS  // the current-row marker: 4 dp wide...
        const val MARKER_GAP_DP = Design.SPACE_S     // ...8 dp before the row's buttons (so it reaches 12 dp into the margin)
        const val MARKER_TAG = "row-marker"
        const val DEVICE_HEADER_GAP_DP = Design.SPACE_L   // small screens: between the words and the device beside them
        const val DEVICE_HEADER_HEIGHT_DP = 96f            //   the device's height there (about the words' own)
        const val DEVICE_HEADER_MAX_SHARE = 0.3f           //   and never more of the width than this
        const val DEVICE_HEADER_MIN_DP = 44f               //   nor less (below that it steps out, so the words fit)
        const val DEVICE_HEADER_KEEP_FIT = 0.66f           //   the keyboard keeps at least this scale for it (letters about 12 dp)
        const val PIP_DP = 6f                        // the caption's row markers
        const val PIP_GAP_DP = 5f
        const val CAPTION_LIFT_DP = 4f               // the caption rises this far as it changes
        const val SPLIT_MIN_DP = 760        // split layout from this width (the words column needs about 300 dp); narrower landscape screens stack
        const val SPLIT_WORDS = 0.4f        // the words' share of the width in the split layout
        const val SPLIT_ROW_DP = 36f        // a button row in the split's list
        const val PANEL_PAD_DP = 8f         // inside the window around the text box and keyboard
        /** Corners nested inside the window: its radius minus its padding (18 - 8), for the keyboard and the text box. */
        const val INNER_RADIUS_DP = 10f
        const val PANEL_GAP_DP = 8f         // between the text box and the keyboard
        const val HEADER_TITLE_SP = Design.TYPE_HEADER
        const val HEADER_TEXT_SP = Design.TYPE_ROW
        const val SMART_LINE = "i think i beat it . what a game"
        // Short landscape screens (small 4:3 and squarish handhelds)
        const val SHORT_MAX_DP = 400
        const val SHORT_TITLE_SP = Design.TYPE_TITLE
        const val SHORT_TEXT_SP = Design.TYPE_CAPTION
        const val BADGE_SCALE_SHORT = Design.BADGE_COMPACT
        const val GRID_ROW_SHORT_DP = 28f
        const val FIELD_HEIGHT_SHORT_DP = 36f
        const val HEADER_BODY_MAX_DP = 760f // two comfortable lines across the top
    }
}

/**
 * A text box for the tour: shows what the demo keyboard types, with a blinking cursor
 * and the selection in your highlight colour, drawn the same way as the TV preview line.
 */
/** The demo apps' own text: Android's font, as a real app's text box would show (docs/visuals-src/VISUAL_SYSTEM.md §6b). */
private val DEMO_APP_FONT: android.graphics.Typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

class DemoFieldView(context: android.content.Context) : View(context) {

    var placeholder = "Type with your controller"

    /** A small name tag at the right end of the box (the sound showcase shows the playing pack's name). Pops in when it changes. */
    var tag: String? = null
        set(v) { if (field != v) { field = v; tagAt = android.os.SystemClock.uptimeMillis(); invalidate() } }
    private var tagAt = 0L

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, Design.STROKE_HAIRLINE * dp)
        color = Design.EDGE_CARD
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_ROW, resources.displayMetrics)
        typeface = DEMO_APP_FONT                     // the demo app's own text box: Android's font, like a real app
    }
    private var before = ""
    private var selected = ""
    private var after = ""
    private var shownAt = 0L
    var accent: Int = SettingsStyle.accent
    private val baseTextSize = text.textSize
    /** Grows and shrinks with the keyboard below it (see StagePanel): words, padding and corners together. */
    var scale = 1f
        set(v) { if (field != v) { field = v; text.textSize = baseTextSize * v; invalidate() } }

    fun set(before: String, selected: String, after: String) {
        this.before = before
        this.selected = selected
        this.after = after
        shownAt = android.os.SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val box = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val r = TutorialActivity.INNER_RADIUS_DP * dp   // nested inside the window, like the keyboard
        fill.color = SettingsStyle.surface
        canvas.drawRoundRect(box, r, r, fill)
        canvas.drawRoundRect(box, r, r, edge)
        val padX = Design.SPACE_L * dp * scale
        val baseline = box.centerY() - (text.descent() + text.ascent()) / 2f
        var x = box.left + padX
        if (before.isEmpty() && selected.isEmpty() && after.isEmpty()) {
            text.color = SettingsStyle.textSecondary
            // The cursor stands just before the hint, as in a real empty text box, never on top of its first letter
            canvas.drawText(placeholder, x + 4 * dp * scale, baseline, text)
        }
        text.color = SettingsStyle.textPrimary
        canvas.drawText(before, x, baseline, text)
        x += text.measureText(before)
        val inset = (height - (text.descent() - text.ascent())) / 2f - 4 * dp
        if (selected.isNotEmpty()) {
            val w = text.measureText(selected)
            fill.color = Contrast.withAlpha(accent, Design.ALPHA_TINT_GLOW)
            canvas.drawRoundRect(x - 2 * dp, box.top + inset, x + w + 2 * dp, box.bottom - inset, 4 * dp, 4 * dp, fill)
            canvas.drawText(selected, x, baseline, text)
            x += w
        } else {
            // Blinks like a real cursor, and always shows right after a change
            val since = android.os.SystemClock.uptimeMillis() - shownAt
            // With "Remove animations" on it stays lit; otherwise it redraws only when it turns on or off
            val moving = WhatsNew.motionAllowed()
            val blink = Design.MOTION_BLINK; val hold = Design.MOTION_BLINK_HOLD
            if (!moving || since < hold || (since / blink) % 2 == 0L) {
                fill.color = accent
                canvas.drawRoundRect(x - 1 * dp, box.top + inset, x + 1 * dp, box.bottom - inset, 1 * dp, 1 * dp, fill)
            }
            if (moving) postInvalidateDelayed(minOf(if (since < hold) hold - since else Long.MAX_VALUE, blink - since % blink).coerceAtLeast(16))
        }
        // Text after the cursor is ordinary text, the same as before it: the cursor only sits between letters
        text.color = SettingsStyle.textPrimary
        canvas.drawText(after, x, baseline, text)
        drawTag(canvas, box)
    }

    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = DEMO_APP_FONT; textAlign = Paint.Align.CENTER }

    /** The name tag: a pill in the highlight colour, like the "NEW IN" tag, popping in over 180 ms. */
    private fun drawTag(canvas: Canvas, box: RectF) {
        val label = tag ?: return
        val t = if (WhatsNew.motionAllowed()) ((android.os.SystemClock.uptimeMillis() - tagAt) / Design.MOTION_POP.toFloat()).coerceIn(0f, 1f) else 1f
        val e = Design.EASE_OUT.getInterpolation(t)
        tagText.textSize = text.textSize * 0.82f
        val padX = 12f * dp * scale; val h = box.height() * 0.58f
        val w = tagText.measureText(label) + 2 * padX
        val right = box.right - Design.SPACE_M * dp * scale
        val cy = box.centerY()
        canvas.save()
        canvas.scale(0.9f + 0.1f * e, 0.9f + 0.1f * e, right - w / 2, cy)
        val a = (255 * e).toInt()
        // a tag: the accent as a soft tint, never a ring (tags say "this is yours", rings say "you are here")
        fill.color = Contrast.withAlpha(accent, (Design.ALPHA_TINT_TAG * e).toInt())
        canvas.drawRoundRect(right - w, cy - h / 2, right, cy + h / 2, h / 2, h / 2, fill)
        tagText.color = TutorialActivity.fieldTagColor(accent); tagText.alpha = a
        canvas.drawText(label, right - w / 2, cy - (tagText.descent() + tagText.ascent()) / 2f, tagText)
        canvas.restore()
        if (t < 1f) postInvalidateOnAnimation()
    }
}

/**
 * Clips what it holds to rounded corners, smoothly, in every drawing mode: on screen with the view's
 * outline (free on the graphics chip), and in software renders by cutting the corners away.
 */
class RoundClipFrame(context: android.content.Context, private val radius: Float) : FrameLayout(context) {
    private val cut = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
    private val outside = android.graphics.Path().apply { fillType = android.graphics.Path.FillType.EVEN_ODD }

    init {
        setWillNotDraw(false)
        // On screen (drawn by the graphics chip) the rounded outline clips for free, smoothly
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        clipToOutline = true
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        if (canvas.isHardwareAccelerated) { super.dispatchDraw(canvas); return }
        // Drawn in software (screenshots, the image renders), where outlines don't clip: cut the corners away
        val w = width.toFloat(); val h = height.toFloat()
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        super.dispatchDraw(canvas)
        outside.reset()
        outside.addRect(0f, 0f, w, h, android.graphics.Path.Direction.CW)
        outside.addRoundRect(0f, 0f, w, h, radius, radius, android.graphics.Path.Direction.CW)
        canvas.drawPath(outside, cut)
        canvas.restoreToCount(layer)
    }
}

/**
 * The window around the tour's text box and keyboard. One rule sizes everything in it, so no page ever
 * shows a squashed keyboard or an oversized text box:
 *  1. One scale ("fit") for the keys, letters, hints and the text box, as big as the room allows (at most 1).
 *  2. The keyboard is always [KeyGridView.PREVIEW_ASPECT] times as wide as its letters are tall, so keys keep
 *     one shape on every page; the window gets narrower rather than the keys flatter, and sits centred.
 *  3. The text box is [fieldUnit] tall at scale 1 and shrinks with the keyboard, its words too.
 *  4. The words above take the room of their tallest page (TutorialActivity.reserveWords); TourTemplateTest keeps the scale at least [MIN_FIT].
 */
class StagePanel(context: android.content.Context, private val field: DemoFieldView, private val clip: View,
                 private val demo: KeyGridView, private val framed: Boolean,
                 private val pad: Int, private val gap: Int, private val fieldUnit: Int) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        if (framed) setPadding(pad, pad, pad, pad)
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, fieldUnit))
        addView(clip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private val fieldShown get() = field.visibility != View.GONE

    /**
     * The loop indicator: the window's own bottom edge fills with your colour as the demo runs, and is full
     * the moment it starts over (then fades while the next run begins). No extra element on screen. On a TV
     * (no window) it runs along the bottom of the floating keyboard panel instead. Drawn on the straight part
     * of the edge only, so it never bends into the corners.
     */
    var loop: LoopClock? = null
    var loopShown = false
        set(v) { field = v; invalidate() }
    var loopAccent = 0
    private val edgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeCap = android.graphics.Paint.Cap.BUTT }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        val clock = loop ?: return
        if (!loopShown || !clock.running) return
        val d = resources.displayMetrics.density
        val now = android.os.SystemClock.uptimeMillis()
        // The edge: the window's bottom border, or the TV panel's bottom
        val left: Float; val right: Float; val y: Float
        if (framed) {
            val r = EDGE_CORNER_DP * d
            left = r; right = width - r; y = height - LOOP_LINE_DP * d / 2f
        } else {
            val p = demo.tvPanelForTest()
            val ox = (clip.left + demo.left).toFloat(); val oy = (clip.top + demo.top).toFloat()
            val r = 20f * demo.unitForCheck()
            left = ox + p.left + r; right = ox + p.right - r; y = oy + p.bottom - LOOP_LINE_DP * d / 2f
        }
        edgePaint.strokeWidth = LOOP_LINE_DP * d
        val fade = clock.fade(now)
        if (fade > 0f) {
            edgePaint.color = Contrast.withAlpha(loopAccent, (255 * fade).toInt())
            canvas.drawLine(left, y, right, y, edgePaint)
        }
        val p = clock.progress(now)
        if (p > 0f) {
            edgePaint.color = loopAccent
            canvas.drawLine(left, y, left + (right - left) * p, y, edgePaint)
        }
        if (isShown && WhatsNew.motionAllowed() && (p < 1f || fade > 0f)) postInvalidateDelayed(LoopClock.FRAME_MS)
    }

    /** The scale the keyboard is drawn at in a window [h] tall (when the width allows it). */
    fun fitForHeight(h: Int): Float {
        val g = if (fieldShown) gap else 0
        return minOf(1f, (h - (if (framed) 2f * pad else 0f) - g) / (demo.previewUnitHeightPx() + if (fieldShown) fieldUnit else 0))
    }

    /** The window's width at scale [fit]. */
    fun widthAt(fit: Float): Int = (demo.previewUnitWidthPx() * fit + (if (framed) 2 * pad else 0)).toInt()

    /** The scale the keyboard is drawn at in a window [w] wide (when the height allows it). */
    fun fitForWidth(w: Int): Float = minOf(1f, (w - (if (framed) 2f * pad else 0f)) / demo.previewUnitWidthPx())

    /** The window's height at scale [fit]. */
    fun heightAt(fit: Float): Int {
        val g = if (fieldShown) gap else 0
        val f = if (fieldShown) fieldUnit else 0
        return ((if (framed) 2 * pad else 0) + g + fit * (demo.previewUnitHeightPx() + f)).toInt()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (!framed) { super.onMeasure(widthSpec, heightSpec); return }
        val w = MeasureSpec.getSize(widthSpec)
        val h = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else MeasureSpec.getSize(heightSpec)
        val g = if (fieldShown) gap else 0
        val fitW = (w - 2f * pad) / demo.previewUnitWidthPx()
        val fitH = (h - 2f * pad - g) / (demo.previewUnitHeightPx() + if (fieldShown) fieldUnit else 0)
        val fit = minOf(1f, fitW, fitH).coerceAtLeast(0.3f)
        demo.previewFit = fit
        field.scale = fit
        (field.layoutParams as LayoutParams).height = (fieldUnit * fit).toInt()
        (clip.layoutParams as LayoutParams).topMargin = g
        val panelW = minOf(w, (demo.previewUnitWidthPx() * fit + 2 * pad).toInt())
        super.onMeasure(MeasureSpec.makeMeasureSpec(panelW, MeasureSpec.EXACTLY), heightSpec)
    }

    companion object {
        /** The loop line's thickness: twice the window's edge, so it reads as the edge lighting up. */
        const val LOOP_LINE_DP = 2f
        /** The window's corner radius (TutorialActivity.PANEL_RADIUS_DP): the line stays on the straight part. */
        const val EDGE_CORNER_DP = 18f
        /** The smallest the keyboard gets in the tour, as a share of its full size: letters stay readable from arm's length. */
        const val MIN_FIT = 0.6f            // letters at least 11 dp, on every screen (TourTemplateTest)...
        const val MIN_FIT_TINY = 0.5f       // ...except screens under [TINY_DP] tall, where the words need the room
        const val TINY_DP = 340
        /** What the stacked layout aims for, taking width from the device picture if it must. */
        const val FIT_GOAL = 0.7f
    }
}
