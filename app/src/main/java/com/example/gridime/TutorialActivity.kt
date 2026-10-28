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
    private lateinit var loopBar: LoopBarView
    private var loopBarRoom = 0                 // what the picture leaves under itself for the loop bar
    /** NEW: the device you're holding, lighting up the buttons the keyboard acts out. */
    private lateinit var picture: DevicePictureView
    private lateinit var stage: LinearLayout
    private lateinit var panel: StagePanel
    private lateinit var pictureCol: LinearLayout
    private var stageRow: LinearLayout? = null
    private val rowTexts = mutableListOf<TextView>()
    private val rowCells = mutableListOf<View>()
    private var shownRows: List<Pair<String, String>> = emptyList()
    /** How closely the rows pack when the words need the room: 0 normal, 1 like a short screen, 2 tightest. */
    private var tightRows = 0

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
    internal val loopBarShown get() = loopBar.visibility == View.VISIBLE
    internal val loopRuns get() = loopBar.runsForCheck.toList()

    /** For the consistency checks: the device picture, to listen to what it lights. */
    internal val pictureForCheck get() = picture

    private fun offNote(on: Boolean, what: String) = if (on) "" else " $what switched off right now: turn it on in Settings → Typing."

    /** The highlights of [WhatsNew.VERSION], each acted out like the tour. */
    private val whatsNewPages: List<Page> by lazy {
        val learns = " It learns the words you use, like names and game titles."
        listOf(
            Page("Word suggestions that learn",
                "While you type, the words you probably mean show in the top row." + learns +
                    offNote(settings.wordSuggestions, "They're"),
                listOf("Take the first word" to "{R3}", "Pick another one" to "up, then {A}"),
                Demo.WORDS, For.CONTROLLER, sticks = true),
            Page("Word suggestions that learn",
                "While you type, the words you probably mean show in the top row." + learns +
                    offNote(settings.wordSuggestions, "They're"),
                listOf("Take a word" to "up, then {A}"),
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
                listOf("Accents · move, then let go" to "hold {A}", "Keep the plain letter" to "{B}", "Take a word" to "up, then {A}"),
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
    /** A short landscape screen (a small 4:3 or squarish handheld): everything a little tighter. */
    private val short get() = wide && resources.configuration.screenHeightDp < SHORT_MAX_DP
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
            // Your colour as a soft tint, never a ring: rings mean "you are here" (audit + Emre, 28 Oct 2026)
            setTextColor(SettingsStyle.accent)
            includeFontPadding = false
            if (whatsNew) {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = px(100f).toFloat()
                    setColor(Contrast.withAlpha(settings.accent, 0x26))
                }
                setPadding(px(Design.SPACE_S + 2f), px(5f), px(Design.SPACE_S + 2f), px(5f))
            }
        }
        eyebrowCount = TextView(this).apply {
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
            addView(eyebrowRow, lpMatchWrap())
            addView(title, lpMatchWrap(topMargin = if (short) 0f else Design.SPACE_S))
            addView(body, lpMatchWrap(topMargin = if (short) Design.SPACE_XS else Design.SPACE_M))
            // The label counts the pages ("4 OF 7"), so the dots only show where a short screen hides it
            if (short) eyebrowRow.visibility = View.GONE else dots.visibility = View.GONE
        }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- The stage: the device you hold and the keyboard it drives, side by side ----
        field = DemoFieldView(this)
        loopBar = LoopBarView(this).apply { accent = settings.accent; visibility = View.INVISIBLE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
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
        pictureCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(picture, lpMatchWrap())
            addView(loopBar, lpMatchWrap(topMargin = if (short) Design.SPACE_XS else Design.SPACE_S))
        }
        // The picture grows to fill its column, so it leaves the loop bar its room
        loopBarRoom = px(LoopBarView.HEIGHT_DP + if (short) Design.SPACE_XS else Design.SPACE_S)
        picture.roomBelowPx = loopBarRoom

        val side = px(Design.SPACE_XXL)
        words.addView(rows, lpMatchWrap(topMargin = if (short) Design.SPACE_S else if (wide) Design.SPACE_M else Design.SPACE_XL))
        rowsPane = rows
        if (wide) {
            // The words across the top, then the device and the keyboard side by side, both as big as they fit.
            // The keyboard is a scaled copy of the real one, so keys and hints keep their shape.
            demo.keepProportions = true
            body.maxWidth = px(HEADER_BODY_MAX_DP)
            words.setPadding(side, px(if (short) Design.SPACE_S else Design.SPACE_L), side, 0)
            root.addView(words, lpMatchWrap())
            wordsScroll = null
            stageRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(side, px(if (short) Design.SPACE_S else Design.SPACE_L), side, px(if (short) Design.SPACE_S else Design.SPACE_M))
                addView(pictureCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.3f))
                addView(stage, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f).apply { marginStart = side })
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
        tightRows = 0
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
     * The button rows. Badges sit in their own column, sized to the widest badge group on the
     * page, then a full 24 dp of air, then the words. Every row is at least 44 dp tall.
     */
    private fun buildRows(list: List<Pair<String, String>>) {
        shownRows = list
        rows.removeAllViews()
        rowTexts.clear()
        rowCells.clear()
        if (list.isEmpty()) return
        // Packed as tight as they go, rows that all start with the same held button ("HOLD RT + …")
        // show it once above them, so the rows themselves get narrow enough to sit side by side
        val combo = Regex("""^(\{\w+\}) \+ (.+)$""")
        val matches = list.map { combo.matchEntire(it.second) }
        val shared = if (tightRows >= 2 && list.size >= 2 && matches.all { it != null } &&
            matches.map { it!!.groupValues[1] }.distinct().size == 1) matches.first()!!.groupValues[1] else null
        val shown = if (shared != null) list.mapIndexed { i, (what, _) -> what to matches[i]!!.groupValues[2] } else list
        if (shared != null) rows.addView(BadgeLineView(this, HintText.parse("$shared +"), settings.buttonStyle, Design.TYPE_CAPTION,
            SettingsStyle.textSecondary, badgeScale = badgeScale, gapBadgeToBadge = BADGE_GAP, gapBadgeToText = TEXT_GAP),
            lpMatchWrap().apply { bottomMargin = px(Design.SPACE_XS) })
        val parsed = shown.map { (what, buttons) -> what to HintText.parse(buttons) }
        val painter = BadgePainter(dp).apply {
            style = settings.buttonStyle; scale = badgeScale
            gapBadgeToBadge = BADGE_GAP; gapBadgeToText = TEXT_GAP
        }
        val joiner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, Design.TYPE_CAPTION, resources.displayMetrics)
        }
        val column = parsed.maxOf { painter.measureLine(it.second, joiner) }.roundToInt() + 2 + px(Design.SPACE_L)
        val cells = parsed.map { (what, parts) ->
            LinearLayout(this).apply {
                rowCells += this
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = px(when {
                    tightRows >= 2 -> GRID_ROW_TIGHT_DP
                    short || tightRows == 1 -> GRID_ROW_SHORT_DP
                    wide -> GRID_ROW_HEIGHT_DP
                    else -> ROW_HEIGHT_DP
                })
                addView(BadgeLineView(context, parts, settings.buttonStyle, Design.TYPE_CAPTION,
                    SettingsStyle.textSecondary, badgeScale = badgeScale, gapBadgeToBadge = BADGE_GAP, gapBadgeToText = TEXT_GAP),
                    LinearLayout.LayoutParams(column, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(context).apply {
                    text = what
                    textSize = Design.TYPE_ROW
                    setTextColor(SettingsStyle.textPrimary)
                    setLineSpacing(0f, 1.15f)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    rowTexts += this
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        // Across the top the grid needs room for the words beside each button group; on a narrow
        // screen with wide groups (HOLD RT + two buttons) it falls back to one row under the other
        val wordsWidth = resources.displayMetrics.widthPixels - 2 * px(Design.SPACE_XXL)
        val gridCols = if (cells.size >= 5) 3 else 2
        val cellRoom = (wordsWidth - (gridCols - 1) * px(Design.SPACE_XL)) / gridCols - column
        if (!wide || cellRoom < px(MIN_ROW_TEXT_DP)) {
            // One row under the other, like a list
            cells.forEachIndexed { i, cell -> rows.addView(cell, lpMatchWrap(topMargin = if (i > 0) Design.SPACE_XS else 0f)) }
            return
        }
        // Across the top: a neat grid, two columns (three for five or more rows), read left to right
        val cols = gridCols
        cells.chunked(cols).forEachIndexed { line, group ->
            rows.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                group.forEachIndexed { i, cell ->
                    addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i > 0) marginStart = px(Design.SPACE_XL)
                    })
                }
                repeat(cols - group.size) { addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = px(Design.SPACE_XL) }) }
            }, lpMatchWrap(topMargin = if (line > 0) Design.SPACE_XS else 0f))
        }
    }

    /**
     * Every row must be visible without scrolling: the controller can't reach what's below the edge.
     * Side by side, the words may take at most [HEADER_SHARE] of the height, so the device and the
     * keyboard always get the rest. On short screens or long pages the words shrink a step at a time
     * until they fit. Measured once the screen has its real size (on the very first page it doesn't yet).
     */
    private fun fitWhenLaidOut() {
        val host = wordsScroll ?: (words.parent as View)
        if (host.height > 0 && words.width > 0) { fitWords(); return }
        host.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (host.height <= 0 || words.width <= 0) return
                host.viewTreeObserver.removeOnGlobalLayoutListener(this)
                fitWords()
            }
        })
    }

    private fun wordsRoom(): Int {
        wordsScroll?.let { return it.height - it.paddingTop - it.paddingBottom }
        val screen = words.parent as View
        val avail = screen.height - screen.paddingTop - screen.paddingBottom - dots.height - controlBar.height
        // The keyboard window keeps at least [StagePanel.MIN_FIT] of its size: the words give way first
        val row = stageRow
        val stageNeed = panel.heightAt(StagePanel.MIN_FIT) + (row?.paddingTop ?: 0) + (row?.paddingBottom ?: 0)
        return maxOf((avail * MIN_HEADER_SHARE).roundToInt(), minOf((avail * HEADER_SHARE).roundToInt(), avail - stageNeed))
    }

    private fun fitWords() {
        if (tightRows > 0) { tightRows = 0; buildRows(shownRows) }
        applyTextScale(1f)
        body.visibility = View.VISIBLE
        var scale = 1f
        fun fits(): Boolean {
            val room = wordsRoom()
            if (room <= 0 || words.width <= 0) return true
            words.measure(View.MeasureSpec.makeMeasureSpec(words.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return words.measuredHeight <= room
        }
        repeat(FIT_STEPS) { step ->
            if (fits()) return
            // Halfway, before the words get really small: the rows close up and the badges take
            // the short screens' size, which costs less than smaller words
            if (step == TIGHTEN_AT_STEP && wide && !short && rowCells.isNotEmpty()) {
                tightRows = 1
                buildRows(shownRows)
                applyTextScale(scale)
                if (fits()) return
            }
            scale -= FIT_STEP
            applyTextScale(scale)
        }
        if (fits()) return
        if (!wide || rows.childCount == 0) return
        // Still too much: the rows say the same as the text above them, so the text steps aside
        // and the keyboard keeps its room
        body.visibility = View.GONE
        applyTextScale(1f - FIT_STEP)
        if (fits()) return
        // And last, the rows pack as tight as they go (a page of HOLD capsules on a small 4:3 screen)
        tightRows = 2
        buildRows(shownRows)
        applyTextScale(1f - FIT_STEP)
        if (fits()) return
        applyTextScale(1f - FIT_STEPS * FIT_STEP)
    }

    /**
     * For the checks: how far the device or the keyboard reaches past the room they're given, in pixels
     * (0 when everything shows). Side by side only; in portrait the words scroll instead.
     */
    internal fun stageOverflowPx(): Int {
        val row = stageRow ?: return 0
        val top = row.paddingTop
        val bottom = row.height - row.paddingBottom
        var worst = 0
        listOf(stage, pictureCol).forEach { col ->
            if (col.visibility != View.VISIBLE) return@forEach
            for (i in 0 until col.childCount) {
                val c = col.getChildAt(i)
                if (c.visibility == View.GONE) continue
                worst = maxOf(worst, top - (col.top + c.top), (col.top + c.bottom) - bottom)
            }
        }
        return worst
    }

    // Across the top (side by side) the words are a touch smaller, so the device and keyboard keep their room
    private val titleSize get() = if (short) SHORT_TITLE_SP else if (wide) HEADER_TITLE_SP else Design.TYPE_DISPLAY
    private val textSize get() = if (short) SHORT_TEXT_SP else if (wide) HEADER_TEXT_SP else Design.TYPE_ROW
    private val badgeScale get() = when {
        tightRows >= 2 -> BADGE_SCALE_TIGHT
        short || tightRows == 1 -> BADGE_SCALE_SHORT
        else -> BADGE_SCALE
    }

    private fun applyTextScale(f: Float) {
        title.textSize = titleSize * f
        body.textSize = textSize * f
        rowTexts.forEach { it.textSize = textSize * f }
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
        // The loop bar shows on every demo that plays in a loop. The welcome showcase never ends, so it
        // has none, and its devices get that room to be bigger. Elsewhere INVISIBLE when still, so the
        // picture keeps its size.
        loopBar.accent = settings.accent
        loopBar.restart()
        loopBar.visibility = when {
            demoKind == Demo.WANDER -> View.GONE
            moving -> View.VISIBLE
            else -> View.INVISIBLE
        }
        picture.roomBelowPx = if (demoKind == Demo.WANDER) 0 else loopBarRoom
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
            firstRun?.let { loopBar.begin(it, due) }
            due += ms
            var first = true
            val tick = object : Runnable {
                override fun run() {
                    if (!first) runStart?.invoke()?.let { loopBar.begin(it, due) }
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
        when (demoKind) {
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
            Demo.TYPE -> {
                // Types a real sentence key by key, pauses, clears, and goes again
                val sentence = texts.search
                if (!moving) showText(sentence)
                typeLoop(sentence)
            }
            Demo.SHIFT_AND_SYMBOLS -> {
                showText(texts.shiftLine)
                // One button per step, so the picture only lights the one that's acting
                every(1100, 1100L + 5 * 1100L, { if (step % 5 == 0) 5 * 1100L else null }) {
                    when (step++ % 5) {
                        0 -> demo.cycleShift()           // RT: shift
                        1 -> demo.cycleShift()           // RT: caps lock
                        2 -> demo.cycleShift()           // RT: off
                        3 -> demo.toggleLayer()          // LT: symbols
                        else -> demo.toggleLayer()       // LT: back to letters
                    }; null
                }
            }
            Demo.HOLD_RT -> {
                // RT held: the tips show, and the cursor jumps a word at a time
                val wordsOnScreen = wordsOf(texts.jump)
                demo.setHeldShift(true)
                showText("", "", wordsOnScreen.joinToString(""))
                var at = 0
                val run = 900L * wordsOnScreen.size + PAUSE_MS
                every(900, run, { if (at == wordsOnScreen.size) run else null }) {
                    at = (at + 1) % (wordsOnScreen.size + 1)
                    if (at > 0) picture.flash("RB", 320)
                    showText(wordsOnScreen.take(at).joinToString(""), "", wordsOnScreen.drop(at).joinToString(""))
                    if (at == wordsOnScreen.size) PAUSE_MS else null
                }
            }
            Demo.EDIT -> {
                // The selection grows a word at a time, then the highlight goes up to Copy and presses it,
                // then over to the clipboard clock, which opens with the copy on top
                val wordsOnScreen = wordsOf(texts.copy)
                val all = wordsOnScreen.joinToString("")
                val earlier = listOf(texts.stick, "github.com/dakingeman/inlay", texts.search)
                showText("", "", all)
                if (!moving) {
                    showText("", wordsOnScreen.take(3).joinToString(""), wordsOnScreen.drop(3).joinToString(""))
                    demo.setSelecting(true, wordsOnScreen.take(3).joinToString("").length)
                }
                var at = 0
                var phase = 0
                // One run: the selection grows, up to Copy, over to the clock, the clipboard stays open, back
                fun run(): Long {
                    val (toCopy, toClock) = demo.dryRun {
                        demo.setSelecting(true, all.length)
                        var a = 0; while (demo.stepToTool(Tool.COPY) == false && a < MAX_WALK) a++
                        demo.setSelecting(false, 0)
                        var b = 0; while (demo.stepToTool(Tool.HISTORY) == false && b < MAX_WALK) b++
                        a to b
                    }
                    return 800L * (wordsOnScreen.size - 1) + 900L + toCopy * TOOL_STEP_MS + 700L + toClock * TOOL_STEP_MS + CLIPS_MS + 900L
                }
                every(800, if (moving) 800L + run() else null, { if (phase == 0 && at == 0) run() else null }) {
                    when (phase) {
                        0 -> {                                   // grow the selection
                            at++
                            picture.flash(if (at == 1) "SELECT" else "RB", 320)
                            showText("", wordsOnScreen.take(at).joinToString(""), wordsOnScreen.drop(at).joinToString(""))
                            demo.setSelecting(true, wordsOnScreen.take(at).joinToString("").length)
                            if (at == wordsOnScreen.size) { phase = 1; 900L } else null
                        }
                        1 -> when (demo.stepToTool(Tool.COPY)) { // up to Copy, then press it
                            false -> TOOL_STEP_MS
                            else -> {
                                demo.pressSelected()
                                demo.setSelecting(false, 0)
                                showText(all)
                                demo.setClips(listOf(all) + earlier)
                                phase = 2; 700L
                            }
                        }
                        2 -> when (demo.stepToTool(Tool.HISTORY)) { // over to the clock: the clipboard opens
                            false -> TOOL_STEP_MS
                            else -> { demo.pressSelected(); phase = 3; CLIPS_MS }
                        }
                        else -> {                                // back to the letters and start over
                            at = 0; phase = 0
                            demo.closeClips(focusOnHistoryButton = false)
                            demo.setClips(emptyList())
                            demo.focusKey(KeyType.SPACE)
                            showText("", "", all)
                            900L
                        }
                    }
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
            Demo.WORDS -> runSteps(wordSteps(), moving, still = 4)
            Demo.ACCENTS -> runSteps(accentSteps(), moving, still = 2)
            Demo.ACCENTS_AND_WORDS -> runSteps(accentSteps() + wordSteps(), moving, still = 2)
            Demo.SHIFT_REMOTE -> runSteps(shiftSteps(), moving, still = 9)
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
                    loopBar.accent = shown.accent
                    picture.show(controls.kind, shown.buttonStyle, controls.sticks)
                    null
                }
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
        loopBar.begin(FIRST_STEP_MS + run, due)
        due += FIRST_STEP_MS
        val tick = object : Runnable {
            override fun run() {
                if (i > 0 && i % steps.size == 0) loopBar.begin(run, due)
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
    private fun prepareStage(demoKind: Demo) {
        val kind = DevicePicture.resolve(this, settings.picture)
        val share = when {
            demoKind == Demo.DEVICES || demoKind == Demo.WANDER -> PICTURE_SHARE_SHOWCASE
            kind == DeviceKind.WIDE -> PICTURE_SHARE_WIDE
            kind == DeviceKind.CONTROLLER -> PICTURE_SHARE_CONTROLLER
            kind == DeviceKind.VERTICAL -> PICTURE_SHARE_VERTICAL
            else -> PICTURE_SHARE_REMOTE
        }
        if (stageRow != null) {
            (pictureCol.layoutParams as LinearLayout.LayoutParams).weight = share
            (stage.layoutParams as LinearLayout.LayoutParams).weight = 1f - share
            pictureCol.requestLayout()
        }
    }

    /** With a remote, Shift and ?123 are keys: over to Shift, OK, a capital, then ?123, OK, a number. */
    private fun shiftSteps(): List<Step> {
        fun key(label: String, then: Long = 380L) = Step(then) { focusOn(label); demo.pressSelected() }
        return listOf(
            Step(500L) { demo.resetShift(); demo.resetLayer(); typed = ""; showText("") },
            Step(500L) { demo.focusKey(KeyType.SHIFT) }, Step(500L) { demo.pressSelected() },   // Shift, then OK
            key("d"), key("u"), key("n"), key("e"), key(" "),
            Step(500L) { focusOn("?123") }, Step(500L) { demo.pressSelected() },                 // ?123, then OK
            key("2", 1800L)
        )
    }

    private fun focusOn(label: String) {
        repeat(24) { if (demo.stepTowards(label) == true) return }
    }

    /**
     * Hold A: e.g. "pok" → hold A on e → the accents rise → move past the one we want and back →
     * let go → "poké" → "pokémon". The accents start on the first one after the plain letter.
     */
    private fun accentSteps(): List<Step> {
        val t = texts
        val list = Accents.variants(t.accentLetter, settings.language).orEmpty()
        val target = list.indexOf(t.accentChoice).coerceAtLeast(1)
        val furthest = maxOf(target + 1, 3).coerceAtMost((list.size - 1).coerceAtLeast(1))
        val steps = mutableListOf(
            Step(550L) { demo.resetAccents(); demo.setSuggestions(emptyList(), ""); typed = t.accentBefore; showText(typed); focusOn(t.accentLetter.toString()) },
            Step(700L) { demo.pressA() }                    // types the letter; the accents rise after a moment
        )
        val walk = if (furthest > 3) 220L else 450L
        repeat(furthest - 1) { steps += Step(walk) { demo.moveFocus(1, 0) } }     // look along them
        repeat(furthest - target) { steps += Step(420L) { demo.moveFocus(-1, 0) } } // back to the one we want
        steps += Step(700L) { demo.releaseA() }                                     // let go: it's typed
        steps += Step(1300L) { typed += t.accentAfter; showText(typed) }           // and the rest of the word
        return steps
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

    /** E.g. "super mario " → types w, o, r with words appearing → right stick in: "super mario world ". */
    private fun wordSteps(): List<Step> {
        val t = texts
        val guesses = t.wordOthers + listOf(t.wordGuesses)
        fun typeLetter(i: Int) {
            focusOn(t.wordTyped[i].toString())
            demo.pressSelected()
            wordPrefix = typed.substringAfterLast(' ')
            demo.setSuggestions(guesses.getOrElse(i) { t.wordGuesses }, wordPrefix)
        }
        val steps = mutableListOf(
            Step(600L) { demo.resetAccents(); typed = t.wordBefore; wordPrefix = ""; demo.setSuggestions(emptyList(), ""); showText(typed) }
        )
        t.wordTyped.indices.forEach { i -> steps += Step(520L + if (i == t.wordTyped.lastIndex) 500L else 0L) { typeLetter(i) } }
        if (!controls.sticks) {
            steps += Step(650L) { demo.focusBestWord(); picture.flash("Dup", 300) }               // up to the first word
            steps += Step(1500L) { demo.pressSelected() }                                        // A (or OK): takes it, with a space
        } else {
            steps += Step(1500L) { picture.flash("RS", 420); demo.bestWord()?.let { onWord(it) } }   // press the right stick in: takes it, with a space
        }
        return steps
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
            KeyEvent.KEYCODE_DPAD_UP -> wordsScroll?.smoothScrollBy(0, -px(SCROLL_DP))
            KeyEvent.KEYCODE_DPAD_DOWN -> wordsScroll?.smoothScrollBy(0, px(SCROLL_DP))
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
        /** Open this screen as "What's new" instead of the tour. */
        const val EXTRA_WHATS_NEW = "whats_new"
        /** The sound showcase's pace: the name shows and the pack loads, each letter, then Enter and a breath. */
        private const val SOUND_LEAD_MS = 520L
        private const val SOUND_KEY_MS = 175L
        private const val SOUND_HOLD_MS = 760L
        /** The window around the text box and keyboard on the tour's background: corners, black fill and one solid edge (as in the GitHub kit). */
        private const val PANEL_RADIUS_DP = 18f
        private const val KEYBOARD_RADIUS_DP = 10f
        private val PANEL_FILL = 0xFF000000.toInt()
        private val PANEL_EDGE = 0xFF2F323A.toInt()
        private const val FIT_STEPS = 4
        private const val FIT_STEP = 0.08f
        private const val TIGHTEN_AT_STEP = 2   // after two steps of smaller words, the rows close up first
        private const val SCROLL_DP = 80f
        const val KEY_PAGE = "page"
        const val CLOSE_DELAY_MS = 140L
        const val TV_SAFE = 0.05f
        const val BADGE_SCALE = 1.45f
        const val BADGE_GAP = 6f            // between two badges in one group (dp, before scaling)
        const val TEXT_GAP = 8f             // between a badge and a "+" (dp, before scaling)
        const val ROW_HEIGHT_DP = 44f       // comfortable rows, like a list
        const val GRID_ROW_HEIGHT_DP = 38f  // the grid across the top: a little tighter
        const val BODY_MAX_WIDTH_DP = 520f
        const val FIELD_HEIGHT_DP = 40f   // at the keyboard's full size; it scales with the keyboard
        const val STEP_MS = 85L             // one D-pad step
        const val PRESS_MS = 190L           // a breath after each letter
        const val PAUSE_MS = 1400L          // read the result before it starts over
        const val CLIPS_MS = 2000L          // the clipboard stays open this long
        const val DEVICE_STEPS = 4             // the welcome page shows the next device every 4 steps (2.8 s)
        const val YOURS_STEPS = 6              // the "make it yours" page: five other looks, then yours again
        const val FIRST_STEP_MS = 400L      // acted-out steps begin this long after the page opens
        const val MAX_WALK = 40             // a demo walk never takes more D-pad steps than this
        const val TOOL_STEP_MS = 170L       // a calm D-pad step up to the top row
        // Side by side: how much of the stage's width the device takes, by its shape
        const val PICTURE_SHARE_WIDE = 0.36f
        const val PICTURE_SHARE_SHOWCASE = 0.44f   // the pages that show every device: room for the widest at a good size
        const val SHOWCASE_OUT_MS = 160L          // the device showcase: the old one fades back...
        const val SHOWCASE_IN_MS = 340L           // ...and the new one settles in
        const val SHOWCASE_SCALE = 0.94f
        const val SHOWCASE_MORPH_MS = 560L        // a near-identical device changes in place instead
        const val PICTURE_SHARE_CONTROLLER = 0.32f
        const val PICTURE_SHARE_VERTICAL = 0.2f
        const val PICTURE_SHARE_REMOTE = 0.13f
        const val PICTURE_NARROW_DP = 150f  // portrait: the device's height above the keyboard
        const val HEADER_SHARE = 0.5f       // the most of the height the words take, side by side
        const val MIN_HEADER_SHARE = 0.3f   // ...and the least, even when the keyboard would like more
        const val PANEL_PAD_DP = 10f        // inside the window around the text box and keyboard
        const val PANEL_GAP_DP = 10f        // between the text box and the keyboard
        const val HEADER_TITLE_SP = 24f
        const val HEADER_TEXT_SP = 15f
        const val SMART_LINE = "i think i beat it . what a game"
        // Short landscape screens (small 4:3 and squarish handhelds)
        const val SHORT_MAX_DP = 400
        const val SHORT_TITLE_SP = 19f
        const val SHORT_TEXT_SP = 13f
        const val BADGE_SCALE_SHORT = 1.1f
        const val GRID_ROW_SHORT_DP = 28f
        const val GRID_ROW_TIGHT_DP = 24f     // the last step when the words still don't fit
        const val BADGE_SCALE_TIGHT = 0.95f
        const val FIELD_HEIGHT_SHORT_DP = 34f
        const val MIN_ROW_TEXT_DP = 72f       // the least room a row's words get beside its buttons
        const val HEADER_BODY_MAX_DP = 760f // two comfortable lines across the top
    }
}

/**
 * A text box for the tour: shows what the demo keyboard types, with a blinking cursor
 * and the selection in your highlight colour, drawn the same way as the TV preview line.
 */
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
        typeface = SettingsStyle.medium
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
        val r = Design.RADIUS_ROW * dp * scale
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
            fill.color = Contrast.withAlpha(accent, 0x59)
            canvas.drawRoundRect(x - 2 * dp, box.top + inset, x + w + 2 * dp, box.bottom - inset, 4 * dp, 4 * dp, fill)
            canvas.drawText(selected, x, baseline, text)
            x += w
        } else {
            // Blinks like a real cursor, and always shows right after a change
            val since = android.os.SystemClock.uptimeMillis() - shownAt
            // With "Remove animations" on it stays lit; otherwise it redraws only when it turns on or off
            val moving = WhatsNew.motionAllowed()
            if (!moving || since < 400 || (since / 530) % 2 == 0L) {
                fill.color = accent
                canvas.drawRoundRect(x - 1 * dp, box.top + inset, x + 1 * dp, box.bottom - inset, 1 * dp, 1 * dp, fill)
            }
            if (moving) postInvalidateDelayed(minOf(if (since < 400) 400 - since else Long.MAX_VALUE, 530 - since % 530).coerceAtLeast(16))
        }
        // Text after the cursor is ordinary text, the same as before it: the cursor only sits between letters
        text.color = SettingsStyle.textPrimary
        canvas.drawText(after, x, baseline, text)
        drawTag(canvas, box)
    }

    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = SettingsStyle.medium; textAlign = Paint.Align.CENTER }

    /** The name tag: a pill in the highlight colour, like the "NEW IN" tag, popping in over 180 ms. */
    private fun drawTag(canvas: Canvas, box: RectF) {
        val label = tag ?: return
        val t = if (WhatsNew.motionAllowed()) ((android.os.SystemClock.uptimeMillis() - tagAt) / 180f).coerceIn(0f, 1f) else 1f
        val e = 1f - (1f - t) * (1f - t)                                   // eases out
        tagText.textSize = text.textSize * 0.82f
        val padX = 12f * dp * scale; val h = box.height() * 0.58f
        val w = tagText.measureText(label) + 2 * padX
        val right = box.right - Design.SPACE_M * dp * scale
        val cy = box.centerY()
        canvas.save()
        canvas.scale(0.9f + 0.1f * e, 0.9f + 0.1f * e, right - w / 2, cy)
        val a = (255 * e).toInt()
        fill.color = Contrast.withAlpha(accent, (0x26 * e).toInt())
        canvas.drawRoundRect(right - w, cy - h / 2, right, cy + h / 2, h / 2, h / 2, fill)
        edge.color = Contrast.withAlpha(accent, (0x99 * e).toInt()); val ew = edge.strokeWidth; edge.strokeWidth = 1.5f * dp
        canvas.drawRoundRect(right - w, cy - h / 2, right, cy + h / 2, h / 2, h / 2, edge)
        edge.strokeWidth = ew; edge.color = Design.EDGE_CARD
        tagText.color = accent; tagText.alpha = a
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
 *  4. The words above give way first, so the scale stays at least [MIN_FIT] (TutorialActivity.wordsRoom).
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
        /** The smallest the keyboard gets in the tour, as a share of its full size: letters stay readable from arm's length. */
        const val MIN_FIT = 0.72f
    }
}
