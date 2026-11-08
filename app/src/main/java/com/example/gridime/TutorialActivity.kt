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
import android.view.MotionEvent
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
    private lateinit var keyboardBox: View
    private lateinit var pictureCol: LinearLayout
    /** What's new: the page's designed close-up, in place of the device and the keyboard. */
    private var showcaseView: WhatsNewStage? = null
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
                       val audience: For = For.ALL, val sticks: Boolean? = null, val scene: WhatsNewShowcase.Scene? = null)
    private enum class Demo { WANDER, TYPE, SHIFT_AND_SYMBOLS, HOLD_RT, EDIT, YOURS, DONE,
        WORDS, ACCENTS, ACCENTS_AND_WORDS, NUMBER_ROW, DEVICES, SHIFT_REMOTE, SMART, SOUNDS, SPLIT, MORE }

    /** What the typing button is called on this controller (bottom, or right with ButtonRoles), so the words match the badge. */
    private val aName get() = if (ButtonRoles.position(ControllerButton.A) == ControllerButton.A) when (settings.shownButtonStyle) {
        ButtonStyle.XBOX -> "A"
        ButtonStyle.PLAYSTATION -> "Cross"
        ButtonStyle.SWITCH -> "B"
    } else when (settings.shownButtonStyle) {
        ButtonStyle.XBOX -> "B"
        ButtonStyle.PLAYSTATION -> "Circle"
        ButtonStyle.SWITCH -> "A"
    }

    /** What the right trigger is called on this controller. */
    private val rtName get() = when (settings.shownButtonStyle) {
        ButtonStyle.XBOX -> "RT"
        ButtonStyle.PLAYSTATION -> "R2"
        ButtonStyle.SWITCH -> "ZR"
    }

    /** What the left trigger and the right bumper are called on this controller (What's new speaks your controller's names). */
    private val ltName get() = when (settings.shownButtonStyle) { ButtonStyle.XBOX -> "LT"; ButtonStyle.PLAYSTATION -> "L2"; ButtonStyle.SWITCH -> "ZL" }
    private val rbName get() = when (settings.shownButtonStyle) { ButtonStyle.XBOX -> "RB"; ButtonStyle.PLAYSTATION -> "R1"; ButtonStyle.SWITCH -> "R" }

    /** What the "select" button is called on this controller, so the words match the badge. */
    private val selectName get() = when (settings.shownButtonStyle) {
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

    private val texts: DemoTexts get() = if (remote) TV_TEXTS else when (settings.shownButtonStyle) {
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
    /** For WhatsNewSenseTest: each page shown on this device, as title, words and scene. */
    internal fun pageWordsCheck(): List<Triple<String, String, String?>> = pages.map { Triple(it.title, it.body, it.scene?.name) }
    internal val pageCountShownForCheck get() = eyebrowCount.visibility == View.VISIBLE && eyebrowCount.text.isNotEmpty()
    /** The release film (WhatsNewFilmExport.clip): no button prompts at the bottom, the page fills the frame (Emre, 8 Nov). */
    internal fun hideControlsForFilm() { controlBar.visibility = View.GONE; dots.visibility = View.GONE }
    internal val dotsShownForCheck get() = (dots.visibility == View.VISIBLE && dots.childCount > 0) || controlBar.dots != null

    /** For the consistency checks: whether this page shows the loop bar, and its runs so far. */
    internal val loopBarShown get() = panel.loopShown
    internal val loopRuns get() = loop.runsForCheck.toList()

    /** For the consistency checks: the device picture, to listen to what it lights. */
    internal val pictureForCheck get() = picture

    /** For the renders: the area holding the device and the keyboard. */
    internal val stageAreaForCheck: View? get() = splitRight ?: stageRow

    private fun offNote(on: Boolean, what: String) = if (on) "" else " $what switched off right now: turn it on in Settings → Typing."

    /** The highlights of [WhatsNew.VERSION], each acted out like the tour. (The retro set stays a secret; one page teases it.) */
    private val whatsNewPages: List<Page> by lazy {
        // Each page has its own designed close-up of the feature (WhatsNewShowcase), not a typing demo (Emre, 7 Nov)
        listOfNotNull(
            Page("A new welcome tour",
                "Learn every button by typing a few game names, one small step at a time. Replay it any time in Settings\u00A0\u2192 About.",
                emptyList(), Demo.MORE, For.CONTROLLER, scene = WhatsNewShowcase.Scene.TOUR),
            Page("Split keyboard",
                "Two halves at the sides of the screen, so you still see the app you're typing into. Turn it on in Settings\u00A0\u2192 Look." +
                    if (SplitRoom.splitPossible(this, settings.language)) "" else " It needs a wide screen held sideways.",
                emptyList(), Demo.MORE, For.CONTROLLER, scene = WhatsNewShowcase.Scene.SPLIT),
            Page("Show Inlay your buttons",
                "Press your four face buttons once, then choose which one selects and which goes back. Works with any controller. Settings\u00A0\u2192 Device.",
                emptyList(), Demo.MORE, For.CONTROLLER, scene = WhatsNewShowcase.Scene.BUTTONS),
            Page("Quick symbols",
                "Hold $ltName and press $aName on a symbol to type just that one, then let go to get back to your letters. Hold $aName on a symbol for related ones, like \u20AC\u00A0\u2192 \$\u00A0\u00A3\u00A0\u00A5.",
                emptyList(), Demo.MORE, For.CONTROLLER, scene = WhatsNewShowcase.Scene.SYMBOLS),
            Page("And a few more",
                "Find any setting with $rbName, type by voice with a voice app, keep each app's language, and back up your settings.",
                emptyList(), Demo.MORE, For.CONTROLLER, scene = WhatsNewShowcase.Scene.MORE),
            // A teaser for the Konami Code, never the answer (Emre, 8 Nov: light words; the D-pad does the hinting).
            // Controllers only (a remote can't enter it), and gone once the secret has been found.
            if (settings.retroUnlocked) null else Page("Something's hidden",
                "There's a secret somewhere in Inlay. Happy hunting.",
                emptyList(), Demo.MORE, For.CONTROLLER, scene = WhatsNewShowcase.Scene.SECRET),
            // A remote's What's new is this one page, so it stands on its own: never "And a few more" (Emre, 8 Nov).
            // WhatsNewSenseTest holds every audience's set to that.
            Page("Four small upgrades",
                "Find any setting from the top of Settings, hold OK on the full stop for punctuation, keep each app's language, and back up your settings in Settings\u00A0\u2192 About.",
                emptyList(), Demo.MORE, For.REMOTE, scene = WhatsNewShowcase.Scene.MORE_REMOTE)
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
            Page("Typing faster",
                "Accents, a suggestion and numbers.",
                listOf("Shift · tap twice for caps lock" to "{R2}", "Numbers and symbols" to "{L2}") +
                    (if (controls.sticks) listOf("Keyboard size" to "{L3}") else emptyList()),
                Demo.SHIFT_AND_SYMBOLS, For.CONTROLLER),
            Page("Capitals, numbers and symbols",
                "Shift and ?123 are keys on the keyboard: move to one and press OK. Shift twice is caps lock.",
                listOf("Capitals" to "Shift key, then OK", "Numbers and symbols" to "?123, then OK"),
                Demo.SHIFT_REMOTE, For.REMOTE),
            Page("Accents and word suggestions",
                "Hold OK on a letter for é, ñ and more. Inlay also suggests words as you type.",
                listOf("Type an accent · move, then let go" to "hold OK", "Keep the plain letter" to "Back", "Take a word" to "up, then OK"),
                Demo.ACCENTS_AND_WORDS, For.REMOTE),
            Page("Fixing and editing",
                "The cursor, selecting and copying, undo, and closing the keyboard.",
                listOf("Move the text cursor" to "{L1} {R1}", "Start selecting" to "{SELECT}",
                    "Grow the selection" to "{L1} {R1}", "Select everything" to "{SELECTx2}"),
                Demo.EDIT, For.CONTROLLER),
            Page("Make it yours",
                if (remote) "Themes, sound packs, languages and a number row are all in Settings. About replays this tour."
                else "A few more things, for when you want them. Settings → About replays this tour.",
                if (remote) emptyList() else listOfNotNull(
                    if (controls.sticks) "Keyboard size · Normal, Large, Largest" to "{L3}" else null,
                    "Switch language" to "hold {R2} + {L2}",
                    "Find any setting · in Settings" to "{R1}",
                    "Themes, Split and icons · in Look" to "Settings",
                    "Sound packs · in Sound & haptics" to "Settings"),
                Demo.YOURS),
            // The end: what you've learnt, and a second go at anything that took a few tries
            Page("You're all set", "", if (remote) emptyList() else listOf("Hold it any time · see what the keys do" to "hold {R2}"), Demo.DONE)
        )
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    /** The page is designed at Odin size and drawn larger, as one unit, on screens with more room ([TourScale]). */
    override fun attachBaseContext(newBase: android.content.Context) {
        tourScale = TourScale.of(newBase.resources.configuration)
        super.attachBaseContext(TourScale.wrap(newBase))
    }
    /** How much larger than designed this screen draws the page (1 = as designed). */
    internal var tourScale = 1f; private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.init(this)
        SettingsStyle.init(this)
        settings = KeyboardSettings.load(this)
        SettingsStyle.accent = settings.activeAccent
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
        // First of all, on a controller: show Inlay your buttons, so everything after presses the right ones
        if (savedInstanceState == null && !whatsNew && !remote && !settings.faceSetUp && askSetup) startSetup()
    }

    // ---- The button setup, before the tour (ButtonSetup.kt): once, on a controller --------------------------------
    private var setup: ButtonSetupView? = null
    private var setupDevice = -1
    private val leaveSetup = Runnable { setup?.leave() }
    internal val setupForCheck get() = setup

    private fun startSetup() {
        val v = ButtonSetupView(this, settings.face).apply { accent = settings.activeAccent }
        v.onDone = { result ->
            if (result != null) {
                settings = settings.withFace(result, ButtonRoles.descriptor(setupDevice)); settings.save(this); ButtonRoles.apply(settings)
                feedback.confirm()
                show(page, animate = false)                     // every {A} on the pages now shows the button you chose
            }
            setup = null
            v.animate().alpha(0f).setDuration(Design.MOTION_FADE).withEndAction { (v.parent as? ViewGroup)?.removeView(v) }.start()
        }
        setup = v
        (findViewById<View>(android.R.id.content) as ViewGroup).addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** A press while the setup shows: raw, as sent. Hold Start leaves it (then the tour, with the buttons as they were). */
    private fun setupKey(raw: KeyEvent): Boolean {
        val v = setup ?: return false
        if (raw.keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            if (raw.action == KeyEvent.ACTION_DOWN && raw.repeatCount == 0) dots.postDelayed(leaveSetup, Design.SETUP_LEAVE_HOLD_MS)
            else if (raw.action == KeyEvent.ACTION_UP) dots.removeCallbacks(leaveSetup)
            return true
        }
        if (raw.action == KeyEvent.ACTION_DOWN && raw.repeatCount == 0) {
            if (FaceSetup.isFace(raw.keyCode)) setupDevice = raw.deviceId
            v.press(raw.keyCode); feedback.move()
        }
        return true
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
    private val captionMode get() = true   // one step at a time, in the card, on every screen (30 Oct 2026)
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
            background = Backdrop(settings.activeAccent, whatsNew)
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
        // The label above the title says which this is: "Welcome tour" in quiet grey, or, for
        // What's new, a "New in 1.2.0" tag. The page count sits next to it, quieter. Sentence case, no
        // spaced-out capitals (30 Oct 2026)
        eyebrow = TextView(this).apply {
            textSize = Design.TYPE_SECTION
            typeface = SettingsStyle.medium
            // Your colour as a soft tint, never a ring: rings mean "you are here" (audit + Emre, 28 Oct 2026).
            // The words are measured on the backdrop's light (and the tag's tint): 4.5:1 with any colour
            setTextColor(labelColor(settings.activeAccent, whatsNew))
            includeFontPadding = false
            if (whatsNew) {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Design.RADIUS_PILL
                    setColor(Contrast.withAlpha(settings.activeAccent, Design.ALPHA_TINT_TAG))
                }
                setPadding(px(Design.SPACE_S), px(Design.SPACE_XS), px(Design.SPACE_S), px(Design.SPACE_XS))
            }
        }
        eyebrowCount = TextView(this).apply {
            fontFeatureSettings = Design.NUMBERS_TABULAR      // "2 of 7" → "3 of 7": the line keeps its length
            textSize = Design.TYPE_SECTION
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
            accent = settings.activeAccent
            maxHeightDp = if (wide) 2000f else PICTURE_NARROW_DP   // side by side: as tall as the stage allows
            tallBoost = false                                       // the stage (or the portrait cap) sets the size
            show(controls.kind, settings.shownButtonStyle, controls.sticks)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        demo = KeyGridView(this).apply {
            clearsBehind = false                     // inside the page (never cut a hole through the tour)
            isFocusable = false
            allowSplit = false        // the tour's scripted demos show the standard keyboard; Split gets its own What's new page
            listener = this@TutorialActivity
            // The picture lights whatever the keyboard acts out: D-pad steps, A, RT, LT
            onDemoInput = { key, held ->
                when (held) {
                    // a D-pad step lights as long as a key press, so the device and the keyboard start and stop together
                    null -> if (key in DevicePictureView.DPAD_ARMS) picture.flash(key, Design.MOTION_PRESS) else picture.flash(key)
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
        keyboardBox = keyboardClip
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
        panel.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom -> if (bottom - top != oldBottom - oldTop) fitCardToPanel() }
        panel.loopAccent = settings.activeAccent

        // The screen's side margin; small screens keep a little less, so the keyboard gets the width
        val side = px(if (short) Design.SPACE_XL else Design.SPACE_XXL)
        val sc = if (whatsNew) WhatsNewStage(this, settings, controls) else null
        showcaseView = sc
        words.addView(rows, lpMatchWrap(topMargin = if (short) Design.SPACE_S else if (wide) Design.SPACE_M else Design.SPACE_XL))
        rowsPane = rows
        if (split) {
            // The card's foot sits on the keyboard window's foot: the same spot on every page (Emre, 30 Oct 2026)
            words.addView(View(this), words.indexOfChild(rows), LinearLayout.LayoutParams(1, 0, 1f))
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
                if (sc != null) addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                else {
                    addView(pictureCol, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                    addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(Design.SPACE_M) })
                }
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
                if (sc == null) addView(pictureCol, LinearLayout.LayoutParams(deviceColumnPx(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
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
                if (sc != null) addView(sc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                else addView(stage, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            root.addView(stageRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            // Portrait: the words (they scroll if a page is long), then the device, the text box and the keyboard
            val wordsPane = noFocusScroll(words)
            wordsScroll = wordsPane
            wordsPane.setPadding(side, px(Design.SPACE_M), side, px(Design.SPACE_M))
            root.addView(wordsPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            if (sc != null) {
                // Upright: the words take what they need, the close-up the rest (it scales to fit)
                (wordsPane.layoutParams as LinearLayout.LayoutParams).apply { height = ViewGroup.LayoutParams.WRAP_CONTENT; weight = 0f }
                root.addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { marginStart = side; marginEnd = side; bottomMargin = px(Design.SPACE_M) })
            } else {
                root.addView(pictureCol, lpMatchWrap().apply { bottomMargin = px(Design.SPACE_M) })
                if (onTv) stage.setPadding(side, 0, side, 0)
                root.addView(stage, lpMatchWrap().apply { if (!onTv) { marginStart = side; marginEnd = side } })
            }
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
        eyebrow.text = if (whatsNew) "New in ${WhatsNew.VERSION}" else "Welcome tour"
        eyebrowCount.text = if (whatsNew) "${index + 1} of ${pages.size}" else "· ${index + 1} of ${pages.size}"
        // one page is nothing to count: no "1 of 1"
        eyebrowCount.visibility = if (pages.size > 1) View.VISIBLE else View.GONE
        title.text = p.title
        body.text = p.body
        buildRows(p.rows)
        rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
        refreshDots()
        val next = if (index < pages.lastIndex) "Next" else if (whatsNew) "Got it" else "Done"
        controlBar.show(settings.shownButtonStyle, if (remote) listOf(
            // A remote has OK and Back, no badges: Back steps back, or leaves from the first page
            "OK · $next",
            if (index > 0) "Back · Previous" else if (whatsNew) "Back · Close" else "Back · Skip the tour"
        ) else listOfNotNull(
            "{A} $next",
            if (index > 0) "{B} Back" else null,
            if (index < pages.lastIndex) (if (whatsNew) "{START} Close" else "hold {START} Skip the tour") else null
        ))
        endTurn()
        dots.removeCallbacks(hint); hinted = false; watching = false
        if (p.demo == Demo.DONE) showDone()
        val sc = showcaseView
        if (sc != null) { handler.removeCallbacksAndMessages(null); sc.scene = p.scene } else startDemo(p.demo)
        // The last two pages show, they don't act out: no loop to wait for; the card lights its buttons on the picture
        // The last page shows, nothing loops. (The card's rows take turns on their own, lit on the picture: RowLight.)
        if (p.demo == Demo.DONE) panel.loopShown = false
        // Try first (Emre's pick, 5 Nov 2026): a page with a try asks straight away. The demo is the help, played
        // once if nothing's pressed for a few seconds ([HINT_AFTER_MS])
        if (tryFor(p) != null) beginTurn()
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
     *  1. the label line ("Welcome tour · 2 of 7", or the New in tag)
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
        if (shared != null) rows.addView(BadgeLineView(this, HintText.parse("$shared +"), settings.shownButtonStyle, Design.TYPE_CAPTION,
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

    private fun badgeLine(parts: List<HintPart>) = BadgeLineView(this, parts, settings.shownButtonStyle, Design.TYPE_CAPTION,
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

    /** A badge line's width, margins included: the badge line's own rule (the badges' right edges were cut off before). */
    private fun badgeWidth(parts: List<HintPart>): Int = badgeLine(parts).naturalWidth()

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
            setColor(markerColor(settings.activeAccent, whatsNew))
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
        while (g != null && g !== top) {
            // A scrolling area keeps its own edge: words scrolled past it must never draw over the device below
            // (a small upright phone showed them over the controller, 30 Oct 2026). Its padding opens up for the
            // card; the screen around it still clips it to its own box.
            if (g is ScrollView) { g.clipToPadding = false; g.clipChildren = false; break }
            g.clipChildren = false; g.clipToPadding = false; g = g.parent as? ViewGroup
        }
    }

    private fun badgeHeight(parts: List<HintPart>): Float =
        BadgePainter(dp).apply { style = settings.shownButtonStyle; scale = badgeScale; gapBadgeToBadge = BADGE_GAP; gapBadgeToText = TEXT_GAP }.lineHeight(parts)

    // ---- The card: one step at a time, the one the demo is acting out (every screen, 30 Oct 2026) ----------
    /**
     * A step's parts, read from its row: "Delete · hold to speed up" is the action "Delete" and the detail "Hold to
     * speed up". A detail that runs in order ("move, then let go") becomes numbered mini-steps, with the held button
     * first ("Hold A"). Rows that all start with the same held button ("{R2} + …") show it once, at the card's top.
     */
    private class CardStep(val buttons: List<HintPart>, val action: String, val detail: String?, val sequence: List<String>,
                           val full: List<HintPart>, val said: String?)
    private class Card(val content: LinearLayout, val badges: FrameLayout, val action: TextView, val detail: TextView,
                       val sequence: LinearLayout, val pips: LinearLayout, val next: TextView, val steps: List<CardStep>)
    private var card: Card? = null
    private var caption: Card? get() = card; set(v) { card = v }
    private var sequenceAt = -1

    private fun stepsOf(list: List<Pair<String, String>>): Pair<String?, List<CardStep>> {
        val combo = Regex("""^(\{\w+\}) \+ (.+)$""")
        val matches = list.map { combo.matchEntire(it.second) }
        val shared = if (list.size >= 2 && matches.all { it != null } && matches.map { it!!.groupValues[1] }.distinct().size == 1)
            matches.first()!!.groupValues[1] else null
        return shared to list.mapIndexed { i, (what, buttons) ->
            val parts = HintText.parse(if (shared != null) matches[i]!!.groupValues[2] else buttons)
            val action = what.substringBefore(" · ")
            val detail = what.substringAfter(" · ", "").takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
            val sequence = if (detail != null && Regex("""\bthen\b""").containsMatchIn(detail)) {
                val held = Regex("""^hold (.+)$""", RegexOption.IGNORE_CASE).find(buttons.trim())?.groupValues?.get(1)
                val heldName = held?.let { h -> if ("{A}" in h) aName else h }      // the words match the badge: A, Cross, B, or OK
                listOfNotNull(heldName?.let { "Hold $it" }) + detail.split(Regex(""",? then |, """)).map { it.trim().replaceFirstChar { c -> c.uppercase() } }
            } else emptyList()
            CardStep(parts, action, if (sequence.isEmpty()) detail else null, sequence, HintText.parse(buttons),
                what.substringAfter(" · ", "").takeIf { it.isNotEmpty() })
        }
    }

    /**
     * Small screens (the words above the stage, beside the device): the compact card, so the keyboard keeps its size.
     * Buttons, action and progress dots on one line; the detail or the mini-steps on a second line only if the page
     * has any; no "Next:" line (the dots show where you are).
     */
    private val compactCard get() = wide && !split
    private val cardBadgeScale get() = if (compactCard) badgeScale else badgeScale * CARD_BADGE
    private val actionSp get() = if (compactCard) bodySp() else Design.TYPE_TITLE

    /** The card's buttons at the card's size. */
    private fun cardBadges(parts: List<HintPart>) = BadgeLineView(this, parts, settings.shownButtonStyle, Design.TYPE_CAPTION, SettingsStyle.textSecondary,
        badgeScale = cardBadgeScale, gapBadgeToBadge = BADGE_GAP, gapBadgeToText = TEXT_GAP)

    /**
     * THE STEP CARD, laid on the page's grid (Emre, 30 Oct 2026):
     *  - beside the keyboard (split) the card is the keyboard window's twin: same top line, same foot, same corners, so
     *    the words column reads as two rows, the sentence beside the device and the card beside the keyboard
     *  - inside, the same "button, then what it does" line as the button bar at the foot of the screen: the buttons on
     *    the card's left edge, in line with a held button shared by every step (once, as the card's first line), the
     *    words straight after them (Emre, 30 Oct 2026: a button floating in a column looked lost); the detail or the
     *    numbered mini-steps under the words
     *  - the step sits in one place (top of a block as tall as the page's tallest step), centred in the card's room;
     *    the progress dots and the next step at the foot
     *  - the card lies inside its column, never past its edge: nothing can cut it off while a page fades in
     * When a page's buttons would take more than [CARD_BADGE_COL_MAX] of the card's width, the buttons sit above the
     * words for every step of that page instead (one rule per page, so the steps never differ).
     */
    private fun buildCaption(list: List<Pair<String, String>>) {
        val (shared, steps) = stepsOf(list)
        val pad = px(if (compactCard) Design.SPACE_M else Design.SPACE_L)
        val padV = px(if (short) Design.SPACE_XS else if (compactCard) Design.SPACE_S else Design.SPACE_L)   // the shortest screens need every dp for the keyboard
        val cardRoom = (wordsWidthOverride ?: (if (header != null && columnRegular >= 0) wordsWidthFor(pages[page].demo) else null)
            ?: if (words.width > 0) words.width - words.paddingLeft - words.paddingRight else resources.displayMetrics.widthPixels - 2 * px(Design.SPACE_XXL))
        val inner = cardRoom - 2 * pad
        val gap = px(Design.SPACE_M)
        val colW = if (compactCard) 0 else steps.maxOf { cardBadges(it.buttons).naturalWidth() }
        val stacked = !compactCard && colW > inner * CARD_BADGE_COL_MAX
        cardInner = inner; cardStacked = stacked

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val badges = FrameLayout(this)
        val lead = LinearLayout(this).apply { orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = if (stacked) Gravity.START else Gravity.CENTER_VERTICAL }
        lead.addView(badges, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(lead, lpMatchWrap())
        val action = TextView(this).apply {
            typeface = SettingsStyle.medium
            textSize = actionSp
            setTextColor(SettingsStyle.textPrimary)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED   // two lines break evenly, never one word alone
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END               // a safety net only: TourTemplateTest wants two lines at most
            setLineSpacing(0f, 1.1f)
            includeFontPadding = false
            rowTexts += this
        }
        when {
            compactCard -> lead.addView(action, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(ROW_TEXT_GAP_DP) })
            stacked -> lead.addView(action, lpMatchWrap(topMargin = Design.SPACE_S))
            else -> lead.addView(action, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = gap })
        }
        val detail = TextView(this).apply {
            typeface = SettingsStyle.regular; textSize = Design.TYPE_CAPTION; setTextColor(SettingsStyle.textSecondary)
            maxLines = 1; includeFontPadding = false
        }
        content.addView(detail, lpMatchWrap(topMargin = Design.SPACE_XS))
        val sequence = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        content.addView(sequence, lpMatchWrap(topMargin = if (compactCard) Design.SPACE_XS else Design.SPACE_S))
        val pips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (list.size > 1) repeat(list.size) { addView(View(context).apply { background = pip(false) },
                LinearLayout.LayoutParams(px(PIP_DP), px(PIP_DP)).apply { marginStart = if (it == 0) 0 else px(PIP_GAP_DP) }) }
        }
        val next = TextView(this).apply {
            typeface = SettingsStyle.regular; textSize = Design.TYPE_CAPTION; setTextColor(SettingsStyle.textSecondary)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
        }
        val foot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pips)
            addView(next, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(Design.SPACE_M) })
        }
        // The step's room: the step sits at its top, and the room is centred between the card's first line and its foot
        val stage = FrameLayout(this).apply { addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = CARD_TAG
            // A quiet raised panel, corners like the keyboard window's
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = px(PANEL_RADIUS_DP).toFloat()
                setColor(Contrast.withAlpha(android.graphics.Color.WHITE, Design.ALPHA_FAINT_LINE))
                setStroke(max(2, px(1f)), Contrast.withAlpha(android.graphics.Color.WHITE, Design.ALPHA_FAINT_STRONG))
            }
            setPadding(pad, padV, pad, padV)
            if (shared != null && !compactCard) addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badgeLine(HintText.parse("hold $shared")))
                addView(TextView(context).apply {
                    text = "and press"; textSize = Design.TYPE_CAPTION; typeface = SettingsStyle.regular; setTextColor(SettingsStyle.textSecondary)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(Design.SPACE_S) })
            }, lpMatchWrap().apply { bottomMargin = px(Design.SPACE_M) })
            addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!compactCard) addView(foot, lpMatchWrap(topMargin = Design.SPACE_M))
        }
        if (compactCard) { foot.removeView(pips); lead.addView(pips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(Design.SPACE_S) }) }
        rows.addView(box, lpMatchWrap())
        val c = Card(content, badges, action, detail, sequence, pips, next, steps)
        card = c
        cardFresh = true
        // One height for every step of the page: the tallest step's, so the words and the dots never jump as the steps change
        var tallest = 0
        steps.indices.forEach { k ->
            setCaption(k)
            content.measure(View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            tallest = maxOf(tallest, content.measuredHeight)
        }
        content.minimumHeight = tallest
        setCaption(0)
        fitCardToPanel()
    }

    private var cardRoom = -1
    /**
     * The card is as tall as its tallest step plus its padding, never stretched to the window: a stretched card was
     * mostly empty on big screens (Reddit video, 3 Nov 2026). Beside the keyboard its foot still sits on the
     * keyboard window's foot (the words column ends where the window ends), so it keeps one place on every page.
     */
    private fun fitCardToPanel() {
        val box = cardBox() ?: return
        if (box.minimumHeight != 0) { box.minimumHeight = 0; box.requestLayout() }
    }

    /** For the checks: the card's words now, the step the demo is on, and whether the card is fully shown. */
    internal fun cardForCheck(): Triple<String, String?, Boolean>? {
        val c = card ?: return null
        val row = rowLight?.current?.takeIf { it >= 0 } ?: return null
        val shown = c.content.alpha == 1f && c.content.translationY == 0f && words.alpha == 1f && words.translationX == 0f
        return Triple(c.action.text.toString().substringBefore(" · "), c.steps.getOrNull(row)?.action, shown)
    }

    /** For the checks, at any moment: the card's words, the steps of the page on show, and how faded the card's words are. */
    internal fun cardNowForCheck(): Triple<String, List<String>, Float>? {
        val c = card ?: return null
        return Triple(c.action.text.toString().substringBefore(" · "), c.steps.map { it.action }, c.content.alpha)
    }

    /** The card itself (its outer panel). */
    private fun cardBox(): View? = rows.findViewWithTag(CARD_TAG)

    /** The card's inside width, and whether its buttons sit above the words (both one per page). */
    private var cardInner = 0
    private var cardStacked = false

    /** A new card shows its first step at once: it arrives with the page, never fading in a second time. */
    private var cardFresh = false

    private fun pip(on: Boolean) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(if (on) settings.activeAccent else Design.DOT_IDLE)
    }

    private fun setCaption(i: Int) {
        val c = card ?: return
        val st = c.steps.getOrNull(i) ?: return
        c.badges.removeAllViews()
        c.badges.addView(cardBadges(if (compactCard) st.full else st.buttons),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START))
        // The buttons on the card's left edge (in line with a held button above them), the words straight after them;
        // the detail or the mini-steps under the words, or from the card's edge when their line doesn't fit there
        if (!compactCard) {
            val under = if (cardStacked) 0 else cardBadges(st.buttons).naturalWidth() + px(Design.SPACE_M)
            (c.detail.layoutParams as ViewGroup.MarginLayoutParams).marginStart = under
            (c.sequence.layoutParams as ViewGroup.MarginLayoutParams).marginStart =
                if (st.sequence.isEmpty() || sequenceWidth(st.sequence) <= cardInner - under) under else 0
            c.detail.requestLayout(); c.sequence.requestLayout()
        }
        // The compact card keeps it to one line: the detail follows the action, quieter, as the row said it
        c.action.text = if (compactCard && st.said != null) android.text.SpannableString("${st.action} · ${st.said}").apply {
            setSpan(android.text.style.ForegroundColorSpan(SettingsStyle.textSecondary), st.action.length, length, 0) } else st.action
        c.detail.text = st.detail.orEmpty()
        c.detail.visibility = if (st.detail != null && !compactCard) View.VISIBLE else View.GONE
        c.sequence.removeAllViews()
        st.sequence.forEachIndexed { k, label -> c.sequence.addView(sequenceChip(k, label, false),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (k > 0) marginStart = px(Design.SPACE_M) }) }
        c.sequence.visibility = if (st.sequence.isNotEmpty() && !compactCard) View.VISIBLE else View.GONE
        sequenceAt = -1
        for (k in 0 until c.pips.childCount) c.pips.getChildAt(k).background = pip(k == i)
        val nxt = c.steps.getOrNull(i + 1)
        c.next.text = nxt?.let { "Next: ${it.action}" }.orEmpty()
    }

    /** How wide a line of mini-steps is (each one's dot and words, and the gaps between). */
    private fun sequenceWidth(labels: List<String>): Int {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEachIndexed { k, label -> row.addView(sequenceChip(k, label, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (k > 0) marginStart = px(Design.SPACE_M) }) }
        row.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return row.measuredWidth
    }

    /** One numbered mini-step: a small numbered dot and its words; the one happening now is lit. */
    private fun sequenceChip(k: Int, label: String, on: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val size = px(SEQ_DOT_DP)
        addView(TextView(context).apply {
            text = "${k + 1}"; gravity = Gravity.CENTER; textSize = Design.TYPE_MIN; typeface = SettingsStyle.medium
            includeFontPadding = false
            setTextColor(if (on) Contrast.inkFor(settings.activeAccent) else SettingsStyle.textSecondary)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                if (on) setColor(settings.activeAccent) else setStroke(max(2, px(1f)), Design.DOT_IDLE)
            }
        }, LinearLayout.LayoutParams(size, size))
        addView(TextView(context).apply {
            text = label; textSize = Design.TYPE_CAPTION; typeface = if (on) SettingsStyle.medium else SettingsStyle.regular
            maxLines = 1; isSingleLine = true
            setTextColor(if (on) SettingsStyle.textPrimary else SettingsStyle.textSecondary); includeFontPadding = false
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(Design.SPACE_XS) })
    }

    /**
     * The mini-step happening now, from what the demo is doing: holding the button (1), moving along the accents
     * (2), letting go (3). Called as the device picture changes and on the row tick.
     */
    private fun followSequence() {
        val c = card ?: return
        val row = rowLight?.current ?: return
        val st = c.steps.getOrNull(row) ?: return
        if (st.sequence.size < 2 || compactCard) return
        val held = picture.holding.isNotEmpty()
        val k = when {
            held && demo.accentsMoved() -> 1
            held -> 0
            sequenceAt >= 1 -> st.sequence.lastIndex
            else -> sequenceAt
        }
        if (k == sequenceAt) return
        sequenceAt = k
        st.sequence.forEachIndexed { i, label ->
            val lp = c.sequence.getChildAt(i)?.layoutParams ?: return@forEachIndexed
            c.sequence.removeViewAt(i); c.sequence.addView(sequenceChip(i, label, i == k), i, lp)
        }
    }

    /** The demo is acting out row [i]: light it in the list, or bring it into the caption. */
    private fun lightRow(i: Int) {
        val moving = WhatsNew.motionAllowed()
        caption?.let { c ->
            c.content.animate().cancel()
            if (!moving || cardFresh) { cardFresh = false; c.content.alpha = 1f; c.content.translationY = 0f; setCaption(i); return }
            val lift = px(CAPTION_LIFT_DP).toFloat()
            c.content.animate().cancel()
            c.content.animate().alpha(0f).translationY(-lift).setDuration(Design.MOTION_FADE / 2).setInterpolator(Design.EASE_IN).withEndAction {
                if (card !== c) return@withEndAction            // the page changed meanwhile: this card is gone
                setCaption(i)
                c.content.translationY = lift
                c.content.animate().alpha(1f).translationY(0f).setDuration(Design.MOTION_FADE).setInterpolator(Design.EASE_OUT).start()
            }.start()
            for (k in 0 until c.pips.childCount) c.pips.getChildAt(k).background = pip(k == i)
            c.next.text = c.steps.getOrNull(i + 1)?.let { "Next: ${it.action}" }.orEmpty()
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
            followSequence()
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
        // The end pages (Make it yours, You're all set) are for reading: each row stays a good while (Emre, 6 Nov)
        val reading = pages.getOrNull(page)?.demo.let { it == Demo.YOURS || it == Demo.DONE }
        val r = RowLight(full, takeTurns = captionMode, dwell = if (reading) READ_ROW_MS else RowLight.DWELL_MS,
            idle = if (reading) READ_ROW_MS else RowLight.IDLE_MS) { i, byDemo ->
            lightRow(i)
            demo.spotlight = pressed[i]
            // A row having its turn while the demo does something else: its buttons show on the device as
            // pointed at (the ring alone), so the caption, the device and the keyboard always agree
            picture.hinted = if (byDemo) emptySet() else rowLight?.keysOf(i).orEmpty()
        }
        rowLight = r
        loop.onRun = { r.newRun() }
        picture.onLitChanged = {
            handler.post { rowLight?.lit(picture.litNow(), picture.holding, android.os.SystemClock.uptimeMillis()); followSequence() }
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
    /**
     * For the checks: every step that doesn't fit the card: its action on more than two lines, its detail on more than
     * one, or its mini-steps wider than the card. Measured at the card's own text width.
     */
    internal fun captionOverflow(): List<String> {
        val c = card ?: return emptyList()
        val box = rows.findViewWithTag<View>(CARD_TAG) ?: return emptyList()
        val inner = box.width - box.paddingLeft - box.paddingRight
        if (inner <= 0) return emptyList()
        // The room each part really has: the words beside the button column, the mini-steps from where they start
        val room = if (c.action.width > 0) c.action.width else inner
        val detailRoom = inner - ((c.detail.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0)
        val seqRoom = inner - ((c.sequence.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0)
        fun lines(t: String, paint: android.text.TextPaint, w: Int = room) = android.text.StaticLayout.Builder.obtain(t, 0, t.length, paint, w)
            .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED).build().lineCount
        return c.steps.mapIndexedNotNull { i, st ->
            when {
                lines(st.action, android.text.TextPaint(c.action.paint)) > 2 -> "\"${st.action}\" takes more than two lines"
                st.detail != null && lines(st.detail, android.text.TextPaint(c.detail.paint), detailRoom) > 1 -> "\"${st.detail}\" takes more than one line"
                st.sequence.isNotEmpty() && sequenceWidth(st.sequence) > seqRoom -> "the steps of \"${st.action}\" don't fit one line"
                else -> null
            }
        }.also { setCaption(rowLight?.current?.coerceAtLeast(0) ?: 0) }
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
        wordsScroll?.let { sv ->
            if (wordsHeight > 0) { cardIntoView(); return }
            sv.post { if (wordsHeight == 0 && sv.height > 0 && words.width > 0) fitPortrait(sv) }
            return
        }
        val host = words.parent as View
        if (wordsHeight > 0) return
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
     * Upright screens: the words, then the device, then the keyboard. When the tallest page's words don't fit their
     * room, the device gives up height (down to [PICTURE_NARROW_MIN_DP]) before the words scroll; a page that still
     * doesn't fit scrolls so its step card shows (30 Oct 2026).
     */
    private fun fitPortrait(sv: ScrollView) {
        val now = page
        val room = sv.height - sv.paddingTop - sv.paddingBottom
        var tallest = 0
        pages.forEach { p ->
            title.text = p.title; body.text = p.body; buildRows(p.rows)
            rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
            words.measure(View.MeasureSpec.makeMeasureSpec(words.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            tallest = maxOf(tallest, words.measuredHeight)
        }
        wordsHeight = tallest
        val over = tallest - room
        if (over > 0 && picture.height > 0) {
            val shown = picture.height / dp
            picture.maxHeightDp = maxOf(PICTURE_NARROW_MIN_DP, minOf(picture.maxHeightDp, shown) - over / dp)
            picture.requestLayout()
        }
        reserveDebug = "portrait: room $room, tallest $tallest, device ${picture.maxHeightDp} dp"
        val p = pages[now]
        title.text = p.title; body.text = p.body; buildRows(p.rows)
        rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
        startRowLight()
        sv.post { cardIntoView() }
    }

    /** Upright screens: a page too tall for its room scrolls just far enough that its step card shows whole. */
    private fun cardIntoView() {
        val sv = wordsScroll ?: return
        val box = cardBox() ?: return
        sv.post {
            var y = 0; var v: View? = box
            while (v != null && v !== sv) { y += v.top; v = v.parent as? View }
            val viewport = sv.height - sv.paddingTop - sv.paddingBottom
            val want = y + box.height - sv.paddingTop - viewport
            sv.scrollTo(0, maxOf(0, want))
        }
    }

    /**
     * Measures every page's words at this screen's width, once. Split: if any page's button list doesn't fit
     * the words column, every page shows its rows as the caption instead (one rule per screen, so pages never
     * differ). Stacked: the words keep the room of the tallest page, so the stage never moves.
     */
    private fun reserveWords() {
        val now = page
        if (showcaseView != null) {
            // What's new: the words keep the tallest page's height, so the close-up below or beside them never moves
            // (counted in lines, so it works in every layout: beside the close-up, above it, or across the top)
            var titleLines = 1; var bodyLines = 1
            for (p in pages) {
                title.text = p.title; body.text = p.body
                title.measure(View.MeasureSpec.makeMeasureSpec(max(1, title.width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                body.measure(View.MeasureSpec.makeMeasureSpec(max(1, body.width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                titleLines = max(titleLines, title.layout?.lineCount ?: 1); bodyLines = max(bodyLines, body.layout?.lineCount ?: 1)
            }
            title.minLines = titleLines; body.minLines = bodyLines
            if (header != null) {
                // Small screens: the words sit across the top at their own height, and the close-up takes the rest
                words.measure(View.MeasureSpec.makeMeasureSpec(words.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                (words.layoutParams as LinearLayout.LayoutParams).height = words.measuredHeight
            }
            wordsHeight = max(1, words.height)
            val p = pages[now]
            title.text = p.title; body.text = p.body; buildRows(p.rows)
            rowsPane.visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
            words.requestLayout()
            return
        }
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
            // The room left under the tallest page's sentence (for the debug line)
            var textMost = 0
            pages.filter { it.rows.isNotEmpty() }.forEach { p ->     // pages without steps have no card: their words may take the column
                title.text = p.title; body.text = p.body; buildRows(emptyList()); rowsPane.visibility = View.GONE
                words.measure(View.MeasureSpec.makeMeasureSpec(words.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                textMost = maxOf(textMost, words.measuredHeight - words.paddingTop)
            }
            cardRoom = words.height - words.paddingTop - textMost - (rows.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            reserveDebug = "room $room at height ${words.height}, caption $tooTall, card room $cardRoom"
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

    /** For the checks: where What's new's close-up is drawn (in the window) and how big a design unit is, in dp. */
    internal fun showcaseForCheck(): Pair<android.graphics.Rect, Float>? {
        val sc = showcaseView ?: return null
        val l = IntArray(2); sc.getLocationInWindow(l); val d = sc.drawnForCheck
        return android.graphics.Rect(l[0] + d.left.toInt(), l[1] + d.top.toInt(), l[0] + d.right.toInt(), l[1] + d.bottom.toInt()) to
            (sc.unitPxForCheck / dp)
    }

    internal fun splitGapShareForCheck(): Float? = showcaseView?.splitGapShareForCheck()
    /** For the checks: how much of the What's new page you left still shows (the dissolve). */
    internal fun crossLeftForCheck(): Float = showcaseView?.crossLeftForCheck ?: 0f
    /** For the checks: where What's new's window and device sit now, and where the stage itself is in the window. */
    internal fun stageSteadyForCheck(): List<String>? {
        val sc = showcaseView ?: return null
        val l = IntArray(2); sc.getLocationInWindow(l)
        return listOf("stage ${l[0]},${l[1]} ${sc.width}x${sc.height}") + sc.steadyForCheck()
    }

    internal fun showcaseTooSmallForCheck(): String? = showcaseView?.apply { minFit = minFitForCheck }?.tooSmallForCheck()

    /** For the checks: what TourBalanceTest measures on the page on show. */
    internal class Balance(val pieces: String, val scale: Float, val titlePx: Float, val screenShortPx: Int, val screenH: Int, val cardFill: Float?, val biggestBand: Float)
    internal fun balanceForCheck(): Balance {
        val root = window.decorView
        val loc = IntArray(2)
        fun box(v: View?): android.graphics.Rect? {
            if (v == null || !v.isShown || v.width <= 0 || v.height <= 0) return null
            if (v is TextView && v.text.isNullOrEmpty()) return null
            v.getLocationInWindow(loc); return android.graphics.Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        }
        val pieces = listOfNotNull(box(eyebrowRow), box(dots), box(title), box(body), box(cardBox()), box(picture), box(panel), box(showcaseView), box(controlBar))
        // Where on the screen's height something is shown, then the tallest stretch right across the screen with nothing
        var reach = 0; var biggest = 0
        pieces.sortedBy { it.top }.forEach { r -> biggest = maxOf(biggest, r.top - reach); reach = maxOf(reach, r.bottom) }
        biggest = maxOf(biggest, root.height - reach)
        val fill = cardBox()?.let { b ->
            if (b.height <= 0) null else {
                val min = b.minimumHeight; b.minimumHeight = 0
                b.measure(View.MeasureSpec.makeMeasureSpec(b.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val natural = b.measuredHeight
                b.minimumHeight = min; b.requestLayout()
                natural.toFloat() / b.height
            }
        }
        return Balance(pieces.joinToString { it.toShortString() }, tourScale, title.textSize, minOf(root.width, root.height), root.height, fill, biggest.toFloat() / root.height)
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
    internal class LayoutCheck(val title: View, val words: View, val header: View?, val pictureCol: View, val picture: View, val side: Int,
                                val panel: View, val controls: View, val split: Boolean, val keyboard: View)
    /** For the checks: the device columns beside the words (small screens), and what the showcase column wanted. */
    internal fun columnsForCheck() = Triple(columnRegular, columnShowcase, if (header != null && !deviceBesideKeyboard) run {
        val content = resources.displayMetrics.widthPixels - 2 * px(Design.SPACE_XL); (content * DEVICE_HEADER_MAX_SHARE).roundToInt() } else 0)
    internal fun layoutForCheck() = LayoutCheck(title, words, header, pictureCol, picture, px(if (short) Design.SPACE_XL else Design.SPACE_XXL),
        panel, controlBar, split, keyboardBox)

    /** For the checks: each page's own words height on this screen. */
    internal val measuredPages = mutableMapOf<Int, Int>()
    /** For the checks: the room every page's words get, and whether any row needed a second line. */
    internal val wordsHeightForCheck get() = wordsHeight
    internal fun rowsWrapped(): List<String> = rowTexts.filter { it.lineCount > 2 || (0 until it.lineCount).any { l -> (it.layout?.getEllipsisCount(l) ?: 0) > 0 } }.map { it.text.toString() }
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
        showcaseView?.let { sc ->
            // What's new: the close-up must sit whole inside its room
            val r = win(sc); val d = sc.drawnForCheck
            return maxOf(b.top - (r.top + d.top.toInt()), (r.top + d.bottom.toInt()) - b.bottom, b.left - (r.left + d.left.toInt()), (r.left + d.right.toInt()) - b.right, 0)
        }
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
        if (short) { dots.visibility = View.GONE; controlBar.dots = if (pages.size > 1) pages.size to page else null; return }
        dots.removeAllViews()
        if (pages.size < 2) return                                    // one page: no lone dot
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
    /** For the checks: report anything the demo does that the screen doesn't show (see [KeyGridView.truthCheck]). */
    internal fun watchTruth(report: (String) -> Unit) { demo.truthCheck = report }
    /** For the checks: draws the demo keyboard, as a frame on screen would (the highlight is set up when it's first drawn). */
    internal fun drawDemoForCheck(c: android.graphics.Canvas) = demo.draw(c)
    /** For the checks: the demo's text, and the word suggestions over it. */
    internal val typedForCheck get() = typed
    internal val wordsForCheck get() = demo.wordsForCheck()

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
            picture.morphTo(kind, settings.shownButtonStyle, sticks, SHOWCASE_MORPH_MS)
            return
        }
        picture.animate().cancel()
        picture.animate().alpha(0f).scaleX(SHOWCASE_SCALE).scaleY(SHOWCASE_SCALE)
            .setDuration(SHOWCASE_OUT_MS).setInterpolator(Design.EASE_IN)
            .withEndAction {
                picture.show(kind, settings.shownButtonStyle, sticks)
                picture.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(SHOWCASE_IN_MS).setInterpolator(Design.EASE_OUT).start()
            }.start()
    }

    private fun startDemo(demoKind: Demo) {
        handler.removeCallbacksAndMessages(null)
        beatList = emptyList(); beatHeld = false; beatsShown.clear()
        panel.holdLevel = null
        demo.animate().cancel(); demo.translationY = 0f; demo.alpha = 1f
        feedback.update(settings)            // your own sound pack again (the sound showcase borrows others)
        field.tag = null
        picture.animate().cancel()
        picture.alpha = 1f; picture.scaleX = 1f; picture.scaleY = 1f
        smartDemo = false
        // Every page starts from your own colour and device: the theme demo and the device tour change them
        picture.accent = settings.activeAccent
        picture.show(controls.kind, settings.shownButtonStyle, controls.sticks)
        field.accent = settings.activeAccent
        // Normal size, so the words always have room above it; hints on, as a new user sees them.
        // The number row only on its own page, and suggestions and accents always on, so every demo looks the same for everyone.
        val base = settings.copy(showHints = true, comboHints = true).forControls(controls).copy(sizeLevel = 0,
            numberRow = demoKind == Demo.NUMBER_ROW, wordSuggestions = true, holdAccents = true,
            split = demoKind == Demo.SPLIT && SplitRoom.splitPossible(this, settings.language))
        demo.applySettings(base)
        demoBase = base
        demo.setSuggestions(emptyList(), "")
        wordPrefix = ""
        picture.held = emptySet()
        picture.holding = emptySet()
        picture.stickPush = emptyMap()
        picture.show(controls.kind, settings.shownButtonStyle, controls.sticks)
        demo.setField(FieldKind.TEXT)
        demo.closeClips(focusOnHistoryButton = false)
        demo.setHeldShift(false)
        demo.resetShift()                    // after setHeldShift, so the RT hints are gone at once
        demo.setSelecting(false, 0)
        demo.setClips(emptyList())
        typed = ""
        field.accent = settings.activeAccent
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
        panel.loopAccent = settings.activeAccent
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
            // 1.3: the keyboard in two halves (where the screen allows), typing a line, and again
            Demo.SPLIT -> { if (!moving) showText(texts.numbers); typeLoop(texts.numbers) }
            // 1.3: the smaller things: the microphone in the top row, typing a line
            Demo.MORE -> { if (!moving) showText(texts.numbers); typeLoop(texts.numbers) }
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
                        buttonStyle = styles[(settings.buttonStyle.ordinal + k) % styles.size], themeIcons = false)
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
    private fun walk(press: Boolean, wait: Long, stepMs: Long = STEP_MS, pressed: () -> Unit = {}, step: () -> Boolean?) = Act({
        var n = 0
        while (n < MAX_WALK && step() == false) n++
        if (press) demo.pressSelected()       // a dry run (timing only): [pressed] changes what shows, so it waits for the real run
        n * stepMs + wait
    }) {
        var walked = 0
        var arrived = false
        val p: () -> Long? = {
            if (arrived) null else when (if (walked >= MAX_WALK) null else step()) {
                false -> { walked++; stepMs }
                else -> { arrived = true; if (press) { demo.pressSelected(); pressed() }; wait }
            }
        }
        p
    }

    /** Types [text] key by key; [after] runs the moment each letter is typed (the suggestions follow it, never a beat late). */
    private fun typeActs(text: String, after: (Int) -> Unit = {}) = text.mapIndexed { i, ch ->
        walk(press = true, wait = PRESS_MS, pressed = { after(i) }) { demo.stepTowards(ch.toString()) }
    }

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

    private fun setTyped(text: String) { typed = text; tail = ""; showText(text) }
    /** A turn's text: [before] the cursor and [after] it (Mario|Kart). Typing goes in at the cursor. */
    private var tail = ""
    private fun setField(before: String, after: String) { typed = before; tail = after; showText(before, after = after) }

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
        val reset = { panel.holdLevel = null; demo.resetShift(); demo.resetLayer(); field.tag = null; showText(texts.shiftLine) }
        when {
            "RT" in keys -> Beat(i, reset, listOf(act(SHIFT_SEE_MS) { demo.cycleShift() }, act(0L) { demo.cycleShift() }))
            "LT" in keys -> Beat(i, reset, listOf(act(SHIFT_SEE_MS + 400L) { demo.toggleLayer() }, act(0L) { demo.toggleLayer() }))
            // Each press of L3 the next size; the text box names it, since the stage fits the keyboard to its room
            else -> Beat(i, { reset(); panel.holdLevel = SIZE_NAMES.lastIndex; demo.applySettings(demoBase); field.tag = SIZE_NAMES[0] }, (1..SIZE_NAMES.size).map { k ->
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

    /**
     * The plain letter. A controller: hold A, the accents rise, let go without moving and they close, the letter stays.
     * A TV remote: let go and they stay open, then Back keeps the plain letter.
     */
    private fun plainLetterBeat(row: Int): Beat {
        val t = texts
        val start = listOf(walk(press = false, wait = 0L) { demo.stepTowards(t.accentLetter.toString()) }, act(ACCENT_OPEN_MS) { demo.pressA() })
        return Beat(row, { demo.resetAccents(); setTyped(t.accentBefore) },
            if (!remote) start + act(ACCENT_TYPED_MS) { demo.releaseA() }
            else start + listOf(act(ACCENT_STEP_MS) { demo.releaseA(remote = true) }, act(ACCENT_STEP_MS) { demo.moveFocus(1, 0) },
                act(0L) { picture.flash("B"); demo.cancelAccents() }))
    }

    /** What's new, Hold A: show them (held), let go to type one, and let go without moving to keep the letter (a remote: choose later, then Back). */
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
        ) + (if (!remote) listOf(plainLetterBeat(2)) else listOf(
            // a TV remote: let go first and choose later, then Back keeps the plain letter
            Beat(2, { demo.resetAccents(); setTyped(t.accentBefore) },
                listOf(toLetter, act(ACCENT_OPEN_MS) { demo.pressA() }, act(ACCENT_STEP_MS) { demo.releaseA(remote = true) }) + along +
                    act(PRESS_MS) { demo.pressA() } + act(ACCENT_TYPED_MS) { demo.releaseA(remote = true) } + act(0L) { setTyped(typed + t.accentAfter) }),
            plainLetterBeat(3)
        )).filter { it.row < shownRows.size }
    }

    /** Types the start of a word, the suggestions follow each letter; then takes one (the right stick, or up and A). */
    private fun wordBeat(row: Int, another: Boolean): Beat {
        val t = texts
        val guesses = t.wordOthers + listOf(t.wordGuesses)
        val keys = keysOfRow(row)
        val take = if ("RS" in keys) listOf(act(0L) { picture.flash("RS", 420); demo.bestWord()?.let { demo.noteBestWordTaken(it); onWord(it) } })
        else listOf(act(WORD_UP_MS, both = { demo.focusBestWord() }) { picture.flash("Dup", 300) }) +
            (if (another) listOf(act(WORD_UP_MS, both = { demo.moveFocus(1, 0) })) else emptyList()) +
            act(0L) { demo.pressSelected() }
        return Beat(row, { demo.resetAccents(); wordPrefix = ""; demo.setSuggestions(emptyList(), ""); setTyped(t.wordBefore) },
            typeActs(t.wordTyped) { i ->
                wordPrefix = typed.substringAfterLast(' ')
                demo.setSuggestions(guesses.getOrElse(i) { t.wordGuesses }, wordPrefix)
            } + act(WORD_LOOK_MS) {} + take)   // the word it takes is on show first, so you see where it came from
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
    private fun soundSteps(): List<Step> = SoundPack.listed(false).flatMap { pack ->
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
        showText(typed, after = tail)
        if (lessons.isNotEmpty()) dots.post { lessonCheck() }
    }

    override fun onReplaceLast(expected: String, replacement: String): Boolean {
        if (!typed.endsWith(expected)) return false
        typed = typed.dropLast(expected.length) + replacement
        showText(typed, after = tail)            // the text after the cursor stays (it vanished: "Piñata", Emre 6 Nov)
        if (lessons.isNotEmpty()) dots.post { lessonCheck() }
        return true
    }

    // ---- What the demo keyboard types lands in the text box ----------------
    override fun onText(text: String) { typed += text; if (smartDemo) typed = smartFix(typed); showText(typed, after = tail); if (lessons.isNotEmpty()) dots.post { lessonCheck() } }

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
    override fun onDelete() { typed = typed.dropLast(1); showText(typed, after = tail); if (lessons.isNotEmpty()) dots.post { lessonCheck() } }
    override fun onEnter() { if (lessons.isNotEmpty()) { if (lesson?.send == true) sent = true else note("Start sends it. Not yet."); dots.post { lessonCheck() } } }
    override fun onTool(tool: Tool) {
        if (lessons.isEmpty()) return
        when (tool) {
            Tool.COPY -> if (selected.isNotEmpty()) { copied = selected; note("Copied.", good = true) } else note("Select something first.")
            Tool.SELECT -> select()
            else -> note("Not that one.")
        }
        dots.post { lessonCheck() }
    }
    override fun onClip(index: Int) = Unit

    // =====================================================================
    // CONTROLLER
    // =====================================================================
    /** Face buttons by position, before the tour sees them (FaceButtons). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (setup != null) return setupKey(event)                // the setup reads presses as they arrive
        return super.dispatchKeyEvent(FaceButtons.event(event))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        // The tour (not What's new): tap Start skips this step, hold it skips the tour
        if (keyCode == KeyEvent.KEYCODE_BUTTON_START && !whatsNew) {
            dots.removeCallbacks(skipTour); dots.postDelayed(skipTour, START_HOLD_MS); startDown = true
            return true
        }
        if (watchingKey(keyCode)) return true
        if (lessons.isNotEmpty() && lessonKey(keyCode, down = true)) return true
        if (keyCode == KeyEvent.KEYCODE_BUTTON_Y && pages.getOrNull(page)?.demo == Demo.DONE && reviewAgain()) return true
        if (turn != null && turnKey(keyCode, down = true)) return true
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_START && !whatsNew) {
            dots.removeCallbacks(skipTour)
            if (startDown && !finishing) {
                startDown = false
                // a tap: the Search turn's answer, otherwise skip this step
                if (lessons.isNotEmpty()) lessonKey(KeyEvent.KEYCODE_BUTTON_START, true)
                else if (turn?.key == "START" && !turnDone) { turnKey(KeyEvent.KEYCODE_BUTTON_START, true); turnKey(KeyEvent.KEYCODE_BUTTON_START, false) }
                else next()
            }
            return true
        }
        if (lessons.isNotEmpty()) { lessonKey(keyCode, down = false); return true }
        if (turn != null && turnKey(keyCode, down = false)) return true
        return if (keyCode in UNUSED_PAD_KEYS) true else super.onKeyUp(keyCode, event)
    }

    /** The triggers send an axis on most controllers (the Odin's too), not a key: RT for a turn that asks for it. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Only the trigger is the lesson's: everything else in the event (a hat D-pad, the sticks) carries on as usual,
        // so Android still turns it into D-pad presses (taking the whole event left the D-pad dead: Emre, 6 Nov)
        if (lessons.isNotEmpty() && event.action == MotionEvent.ACTION_MOVE) {
            val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            if (rt > TRIGGER_DOWN && !rtDown) { rtDown = true; lessonKey(KeyEvent.KEYCODE_BUTTON_R2, down = true) }
            else if (rt < TRIGGER_UP && rtDown) { rtDown = false; lessonKey(KeyEvent.KEYCODE_BUTTON_R2, down = false) }
            // LT the same (the Odin sends both triggers as axes: LT did nothing in "Add the 5", Emre 6 Nov)
            val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
            if (lt > TRIGGER_DOWN && !ltDown) { ltDown = true; lessonKey(KeyEvent.KEYCODE_BUTTON_L2, down = true) }
            else if (lt < TRIGGER_UP && ltDown) ltDown = false
        }
        val t = turn
        if (t != null && t.key == "RT" && event.action == MotionEvent.ACTION_MOVE) {
            val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            if (rt > TRIGGER_DOWN && !rtDown) { rtDown = true; turnKey(KeyEvent.KEYCODE_BUTTON_R2, down = true) }
            else if (rt < TRIGGER_UP && rtDown) { rtDown = false; turnKey(KeyEvent.KEYCODE_BUTTON_R2, down = false) }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
    private var rtDown = false
    private var ltDown = false
    private var rtSince = 0L

    private var startDown = false
    /** Start held: skip the whole tour (a tap only skips this step). */
    private val skipTour = Runnable { startDown = false; finishTour() }

    // =====================================================================
    // YOUR TURN
    // =====================================================================
    /**
     * A try at the end of a tour page (1.3, picked with Emre: "Your turn", option C). The page acts its rows out once,
     * then the keyboard is yours: the card asks for one thing, the picture lights the button for it, and the keyboard
     * does it for real. Right, and "That's it" moves on; another button, and the card says which one is lit.
     *
     * [key] as the picture names it (A, RT, SELECT), so it is the typing button wherever ButtonRoles puts it.
     * [press] says if a press already did it; a hold (accents) checks on [release] instead.
     */
    /**
     * One thing to do on your turn. Learn by typing (1.3, option B): a turn can be a small goal in a few steps
     * ([then]: "Type hi" = the h, the i, then Space), with what you'll type shown faintly in the text box ([ghost]).
     * [check] says what's wrong before a press counts ("That's the g. Move to the h first"); null = fine.
     */
    private class Try(val key: String, val badge: String, val headline: String, val ask: String, val setup: () -> Unit,
                      val press: () -> Boolean, val release: (() -> Boolean)? = null, val moves: Boolean = false, val tooSoon: String = "",
                      val then: Try? = null, val ghost: String? = null, val check: (() -> String?)? = null,
                      val guide: KeyGridView.Guide? = null, val where: Boolean = false) {
        val steps: Int get() = 1 + (then?.steps ?: 0)
        /** The turns in this chain: steps with their own headline start a new one ("Add the capital Z"). */
        val turns: Int get() = (if (headline.isNotEmpty()) 1 else 0) + (then?.turns ?: 0)
    }

    /** The keyboard's guide for a key type, with the button that does it. */
    private fun on(type: KeyType, button: ControllerButton?) = KeyGridView.Guide(button) { it.type == type }
    private fun onLetter(c: Char) = KeyGridView.Guide(ControllerButton.A) { (it.type == KeyType.CHAR || it.type == KeyType.DIGIT) && it.label.equals(c.toString(), true) }
    private fun onTool(tool: Tool, button: ControllerButton?) = KeyGridView.Guide(button) { it.tool == tool && (tool != Tool.WORD || it.best) }
    private var turnRoot: Try? = null

    /** A step that types one letter: the highlight has to be on it first. */
    private fun letter(c: Char, ask: String, then: Try? = null, headline: String = "", ghost: String? = null, setup: () -> Unit = {}) =
        Try("A", "{A}", headline, ask, setup, { demo.pressSelected(); true }, moves = true, then = then, ghost = ghost, guide = onLetter(c), check = {
            val on = demo.focusedLetter()
            when { on == null -> "Move onto the $c first."; on.equals(c.toString(), ignoreCase = true) -> null; else -> "That's the $on. Move to the $c first." }
        })

    private var turn: Try? = null
    private var turnDone = false

    /** The try for page [p], if it has one: the controller tour only (What's new and the remote stay as they are). */
    private fun tryFor(p: Page): Try? {
        if (whatsNew || remote || !yourTurn) return null
        val t = texts
        return when (p.demo) {
            // Typing, in four short turns: each button shows up on the key it works on (1.3, Emre's direction A)
            Demo.TYPE -> {
                val search = Try("START", "{START}", "Search for it", "Press Start: it sends what you typed, like Search or Go.",
                    { setField("Celeste", "") }, { demo.pressShortcut("START", KeyType.ENTER); true }, moves = true,
                    guide = on(KeyType.ENTER, ControllerButton.START), where = true)
                val space = Try("Y", "{Y}", "Add the space", "Press Y. It adds a space where the cursor is.",
                    { setField("Mario", "Kart") }, { demo.pressShortcut("Y", KeyType.SPACE); true }, moves = true,
                    guide = on(KeyType.SPACE, ControllerButton.Y), then = search)
                val delete = Try("X", "{X}", "Delete the extra d", "Press X. Hold it to delete faster.",
                    { setField("Metroidd", "") }, { demo.pressShortcut("X", KeyType.DELETE); true }, moves = true,
                    guide = on(KeyType.DELETE, ControllerButton.X), then = space)
                Try("RT", "{R2}", "Add the capital Z", "Tap $rtName. The next letter comes out as a capital.",
                    { demo.resetShift(); demo.resetLayer(); setField("", "elda") }, { demo.cycleShift(); true }, moves = true,
                    guide = on(KeyType.SHIFT, ControllerButton.R2), where = true,
                    then = letter('z', "Now move to the z and press $aName.", then = delete))
            }
            Demo.SHIFT_AND_SYMBOLS -> Try("RT", "{R2}", "Type \"Hi\" with a capital", "Tap $rtName once: the next letter comes out as a capital.",
                { demo.resetShift(); demo.resetLayer(); field.tag = null; setTyped("") }, { demo.cycleShift(); true }, moves = true, ghost = "Hi",
                then = letter('h', "Now the h.", then = letter('i', "And the i. It's small again by itself.")))
            Demo.ACCENTS_AND_WORDS -> Try("A", "{A}", "Hold $aName for accents", "Hold $aName on the ${t.accentLetter} until its accents rise.",
                {
                    demo.resetAccents(); setTyped(t.accentBefore)
                    var guard = 0
                    while (demo.stepTowards(t.accentLetter.toString()) == false && guard++ < 60) Unit
                },
                { demo.pressA(); false },
                { val open = demo.isAccentsOpen(); demo.releaseA(); open }, moves = true,
                tooSoon = "Hold $aName a little longer, until the accents rise.")
            Demo.HOLD_RT -> Try("RT", "{R2}", "Hold $rtName for more", "Hold it, and the keyboard shows what else each button does.",
                { demo.setHeldShift(false) }, { demo.setHeldShift(true); rtSince = android.os.SystemClock.uptimeMillis(); false },
                { val long = android.os.SystemClock.uptimeMillis() - rtSince >= RT_HOLD_MS; demo.setHeldShift(false); long },
                tooSoon = "Keep $rtName held a moment, and read the tips on the keys.")
            Demo.EDIT -> Try("SELECT", "{SELECT}", "Press $selectName to select", "The text starts to highlight as you go.",
                { demo.setSelecting(false, 0); showText("", "", wordsOf(t.copy).joinToString("")) },
                { demo.setSelecting(true, 0); picture.flash("SELECT"); true })
            else -> null
        }
    }

    // ---- Guided turns (1.3): learn the controller by typing ----------------------------------------------------
    /*
     * The rules (Emre, 6 Nov 2026), from the user's side:
     * - a turn is a small goal ("Zelda"); it's done the moment the text is right, however you got there: the button
     *   (X) or the key on the keyboard (Delete, then A) both count, and using the key earns a one-line tip
     * - the card on the left is the guide: the step you're on, its button, and the one line that answers you
     * - the button sits big on the key it works on; the controller picture is always there, the same size: the
     *   button to press has a soft ring (pointed at), a press flashes filled
     * - nothing is blocked: the keyboard works as it does in any app. A wrong letter really types, and the card says
     *   how to fix it; too far off and the turn starts again by itself. B goes back a page, holding Start skips
     * - the same button pressed again and again with nothing happening gets a gentle word; help comes only after a
     *   long quiet (never a demo taking over), and much later, the way to skip
     * - each turn notes whether it went cleanly (no slips, no help): the tour's way of telling it's been learnt
     */
    private class LessonStep(val action: String, val buttons: String, val guide: KeyGridView.Guide?, val pic: String, val done: () -> Boolean)
    /**
     * [lock]: the highlight stays on its key until something opens (the accents page: Emre, 6 Nov, "show how the
     * accent feature works"). [finish]: done by something other than the text (copied, closed). [undo]: RT + X brings
     * [goal] back. [close]: B is the answer here, not "back a page". [setup]: anything else the turn starts with.
     */
    private class Lesson(val title: String, val before: String, val after: String, val goal: String, val steps: List<LessonStep>,
                         val shortcut: String? = null, val tip: String? = null, val send: Boolean = false,
                         val lock: Char? = null, val finish: (() -> Boolean)? = null, val undo: Boolean = false,
                         val close: Boolean = false, val setup: () -> Unit = {})
    private var lessons: List<Lesson> = emptyList()
    private var lessonAt = -1
    private val lesson get() = lessons.getOrNull(lessonAt)
    private var stepAt = 0
    private var sent = false
    private var usedKey = false
    private var lessonBusy = false
    private var lastButton = ""
    private var samePresses = 0
    private var slips = 0
    private var helped = false
    /** Each turn's title and whether it went cleanly: what the tour learnt about how you're doing. */
    internal val learnt = mutableListOf<Pair<String, Boolean>>()

    private fun typingLessons(): List<Lesson> = listOf(
        Lesson("Add the capital Z", "", "elda", "Zelda", listOf(
            LessonStep("Tap $rtName · for a capital", "{R2}", on(KeyType.SHIFT, ControllerButton.R2), "RT") { demo.shiftOn || typed.startsWith("Z") },
            LessonStep("Go to the z, press $aName", "{A}", onLetter('z'), "A") { typed == "Z" })),
        Lesson("Delete the extra d", "Metroidd", "", "Metroid", listOf(
            LessonStep("Press X · deletes", "{X}", on(KeyType.DELETE, ControllerButton.X), "X") { typed == "Metroid" }),
            shortcut = "X", tip = "X does this from anywhere."),
        Lesson("Add the space", "Mario", "Kart", "Mario Kart", listOf(
            LessonStep("Press Y · adds a space", "{Y}", on(KeyType.SPACE, ControllerButton.Y), "Y") { typed == "Mario " }),
            shortcut = "Y", tip = "Y does this from anywhere."),
        Lesson("Search for it", "Celeste", "", "Celeste", listOf(
            LessonStep("Press Start · sends it", "{START}", on(KeyType.ENTER, ControllerButton.START), "START") { sent }),
            shortcut = "START", tip = "Start does this from anywhere.", send = true),
    )

    /** Typing faster: accents, a suggestion, numbers. */
    private fun fasterLessons(): List<Lesson> = listOf(
        Lesson("Add the é", "Pok", "mon", "Pokémon", listOf(
            LessonStep("Hold $aName on the e", "hold {A}", KeyGridView.Guide(ControllerButton.A, dim = true) { it.type == KeyType.CHAR && it.label == "e" }, "A") { demo.isAccentsOpen() || typed.endsWith("é") },
            LessonStep("Let go on é", "{A}", null, "A") { false }), lock = 'e'),
        Lesson("Take the suggestion", "Castlev", "", "Castlevania ", listOf(
            if (controls.sticks) LessonStep("Press R3 · takes the word", "{R3}", onTool(Tool.WORD, ControllerButton.R3), "RS") { false }
            else LessonStep("Go up to the word, press $aName", "{A}", onTool(Tool.WORD, ControllerButton.A), "A") { false }),
            setup = { wordPrefix = "Castlev"; demo.setSuggestions(listOf("Castlevania", "Castle", "Castles"), "Castlev") }),
        Lesson("Add the 5", "Persona ", "", "Persona 5", listOf(
            LessonStep("Tap LT · numbers", "{L2}", on(KeyType.LAYER, ControllerButton.L2), "LT") { demo.onSymbols || typed.endsWith("5") },
            LessonStep("Go to the 5, press $aName", "{A}", onLetter('5'), "A") { false })),
    )

    /** Fixing: the cursor, selecting and copying, selecting it all, undo, closing. */
    private fun fixingLessons(): List<Lesson> = listOf(
        Lesson("Fix the typo", "Sonc", "", "Sonic", listOf(
            LessonStep("Press LB · cursor back", "{L1}", null, "LB") { typed == "Son" },
            LessonStep("Type the i", "{A}", onLetter('i'), "A") { false })),
        Lesson("Copy the 3", "Dark Souls 3", "", "Dark Souls 3", listOf(
            LessonStep("Press $selectName · start selecting", "{SELECT}", null, "SELECT") { selecting },
            LessonStep("Press LB · grows it", "{L1}", null, "LB") { selected == "3" },
            LessonStep("Go up to Copy, press $aName", "{A}", onTool(Tool.COPY, ControllerButton.A), "A") { copied == "3" }),
            finish = { copied == "3" }),
        Lesson("Clear it all", "Dark Souls 3", "", "", listOf(
            LessonStep("Press $selectName twice · selects all", "{SELECTx2}", null, "SELECT") { selected == "Dark Souls 3" },
            LessonStep("Press X · deletes it", "{X}", on(KeyType.DELETE, ControllerButton.X), "X") { false })),
        Lesson("Bring it back", "Hollow ", "", "Hollow Knight", listOf(
            LessonStep("Hold $rtName · keep holding", "hold {R2}", null, "RT") { rtHeld },
            LessonStep("Press X · undo", "{X}", on(KeyType.DELETE, ControllerButton.X), "X") { false }), undo = true),
        Lesson("Close the keyboard", "Hades", "", "Hades", listOf(
            LessonStep("Press B · closes it", "{B}", null, "B") { closed }), finish = { closed }, close = true),
    )
    private var selecting = false
    private var selected = ""
    private var copied = ""
    private var rtHeld = false
    private var closed = false
    private var lastSelectAt = 0L
    private var aDown = false

    private fun startLessons(list: List<Lesson>) {
        handler.removeCallbacksAndMessages(null)
        beatList = emptyList(); beatHeld = false; panel.loopShown = false
        lessons = list; lessonAt = -1
        turnLook(true)
        nextLesson()
    }

    private fun nextLesson() {
        lessonAt++
        val l = lesson ?: return finishLessons()
        stepAt = 0; sent = false; usedKey = false; lessonBusy = false; offerSkip = false; samePresses = 0; slips = 0; helped = false; lastButton = ""
        demo.resetShift(); demo.resetLayer(); demo.setHeldShift(false); demo.resetAccents()
        demo.setSuggestions(emptyList(), ""); demo.setSelecting(false, 0); demo.animate().cancel(); demo.alpha = 1f; demo.translationY = 0f
        selecting = false; selected = ""; copied = ""; rtHeld = false; closed = false
        setField(l.before, l.after)
        l.setup()
        l.lock?.let { c -> var guard = 0; while (demo.stepTowards(c.toString()) == false && guard++ < 60) Unit }
        title.text = l.title
        body.visibility = View.GONE                         // the card says it all; no second set of words
        eyebrowCount.text = "· ${lessonAt + 1} of ${lessons.size}"; eyebrowCount.visibility = View.VISIBLE
        buildRows(l.steps.map { it.action to it.buttons })
        rowsPane.animate().cancel(); rowsPane.alpha = 1f; rowsPane.visibility = View.VISIBLE
        showLessonStep()
        armStuck()
    }

    private fun showLessonStep() {
        val l = lesson ?: return
        val s = l.steps[stepAt]
        setCaption(stepAt)
        demo.guide = s.guide
        picture.holding = emptySet()
        picture.held = setOf(s.pic)          // its ring: "this one" (only a press fills, and only for a moment)
        controlBar.show(settings.shownButtonStyle, listOf("Hold {START} Skip the tour", "{B} Back"))
    }

    /** The card's answer line: what just happened, or how to put it right. */
    private fun note(text: String, good: Boolean = false) {
        val d = card?.detail ?: return
        d.text = text; d.visibility = View.VISIBLE; d.maxLines = 1; d.ellipsize = android.text.TextUtils.TruncateAt.END
        // Always readable: a note that's too long for its line gets a touch smaller first (Emre, 6 Nov: "Nearly…" was cut)
        d.textSize = Design.TYPE_CAPTION
        d.post {
            val room = d.width - d.paddingLeft - d.paddingRight
            if (room <= 0) return@post
            val w = d.paint.measureText(text)
            if (w > room) d.textSize = Design.TYPE_CAPTION * max(NOTE_MIN_SCALE, room / w)
        }
        d.setTextColor(if (good) TutorialActivity.labelColor(settings.activeAccent, whatsNew) else SettingsStyle.textPrimary)
    }

    /** After anything that changed the text or Shift: on track, done, or how to fix it. */
    private fun lessonCheck() {
        val l = lesson ?: return
        if (lessonBusy) return
        val full = typed + selected + tail
        l.finish?.let { if (it()) return lessonDone() }
        if (l.finish == null && full == l.goal && (!l.send || sent) && !demo.isAccentsOpen()) return lessonDone()
        // The accents: let go too soon, or without moving, and the plain letter types: put it back and say so
        if (l.lock != null && !aDown && !demo.isAccentsOpen() && full == l.before + l.lock + l.after) {
            val why = if (stepAt == 0) "Hold it a bit longer." else "Let go on é."
            lessonBusy = true
            handler.postDelayed({ if (lesson === l) { lessonBusy = false; setField(l.before, l.after); stepAt = 0; showLessonStep(); note(why) } }, Design.MOTION_PAGE)
            note(why); slips++
            return
        }
        // While A is held on the accents page, the letter and its accents come and go: not a slip
        if (l.lock != null && (aDown || demo.isAccentsOpen())) {
            val now3 = l.steps.indexOfFirst { !it.done() }.let { if (it < 0) l.steps.lastIndex else it }
            if (now3 != stepAt) { stepAt = now3; showLessonStep() }
            return
        }
        if (l.finish != null || selecting) {
            val now2 = l.steps.indexOfFirst { !it.done() }.let { if (it < 0) l.steps.lastIndex else it }
            if (now2 != stepAt) { stepAt = now2; showLessonStep() }
            armStuck(); return
        }
        val now = l.steps.indexOfFirst { !it.done() }.let { if (it < 0) l.steps.lastIndex else it }
        val start = l.before + l.after
        if (full == start || (l.goal.startsWith(typed) && tail == l.after && full.length <= l.goal.length && now > 0)) {
            if (now != stepAt) { stepAt = now; showLessonStep() }
            samePresses = 0; armStuck(); return
        }
        // Off the path: say how to put it right, in the card
        slips++
        val missing = if (l.goal.endsWith(tail) && l.goal.startsWith(typed) && full.length < l.goal.length)
            l.goal.substring(typed.length, l.goal.length - tail.length) else null
        when {
            full.equals(l.goal, ignoreCase = true) -> note("Small ${typed.lastOrNull() ?: ""}. X deletes it, then $rtName first.")
            missing != null -> note("One too many. Type ${missing.trim().ifEmpty { "a space" }} again.")
            full.length - l.goal.length > 2 || slips > 4 -> { note("Let's try that again."); restartSoon() }
            typed.isNotEmpty() -> note("Typed ${typed.last()}. X deletes it.")
            else -> { note("Let's try that again."); restartSoon() }
        }
        armStuck()
    }

    private fun restartSoon() {
        lessonBusy = true
        handler.postDelayed({ lessonAt--; nextLesson() }, RESTART_MS)
    }

    private fun lessonDone() {
        val l = lesson ?: return
        lessonBusy = true
        dots.removeCallbacks(stuck)
        feedback.confirm()
        learnt += l.title to (slips == 0 && !helped); learntLesson[l.title] = l
        val tip = if (usedKey && l.tip != null) l.tip else null
        note(if (l.close) "Closed. Any text box brings it back." else if (tip != null) "Nice. $tip" else "Done.", good = true)
        for (k in 0 until (card?.pips?.childCount ?: 0)) card?.pips?.getChildAt(k)?.background = pip(true)
        handler.postDelayed({ if (lesson === l) nextLesson() },
            if (l.close) CLOSED_MS + 2 * Design.MOTION_PANEL + TURN_GAP_MS else if (tip != null) TIP_MS else TURN_GAP_MS)
    }

    private fun finishLessons() {
        endLessons()
        if (reviewing) { reviewing = false; show(page, animate = true); return }   // back to the summary, now updated
        next()
    }

    private fun endLessons() {
        if (lessons.isEmpty()) return
        dots.removeCallbacks(stuck)
        lessons = emptyList(); lessonAt = -1; lessonBusy = false
        demo.guide = null; picture.held = emptySet(); tail = ""
        body.visibility = View.VISIBLE
        turnLook(false)
    }

    /** Help after a long quiet, in the card: first a hand, much later the way to skip. Never a demo. */
    private val stuck = Runnable {
        val l = lesson ?: return@Runnable
        if (lessonBusy) return@Runnable
        if (!helped) { helped = true; note("Look for the ringed key.")
            dots.postDelayed(stuckLater, STUCK_SKIP_MS - STUCK_MS) }
    }
    private val stuckLater = Runnable { if (lesson != null && !lessonBusy && lesson?.send != true) { offerSkip = true; note("Stuck? Tap Start to skip.") } }
    /** After the long quiet, a tap on Start skips the turn (on the Search turn, Start is the answer anyway). */
    private var offerSkip = false
    private fun skipLesson() {
        val l = lesson ?: return
        lessonBusy = true; learnt += l.title to false; learntLesson[l.title] = l
        handler.postDelayed({ if (lesson === l) nextLesson() }, Design.MOTION_FADE)
    }
    private fun armStuck() { dots.removeCallbacks(stuck); dots.removeCallbacks(stuckLater); dots.postDelayed(stuck, STUCK_MS) }

    /** A press during a lesson: the keyboard works as it does in any app. True: the lesson took it. */
    private fun lessonKey(keyCode: Int, down: Boolean): Boolean {
        val l = lesson ?: return false
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
            if (!down) return true
            if (l.close && !lessonBusy) {                       // here B is the answer: the keyboard goes down, and comes back
                picture.flash("B"); closed = true
                val drop = (demo.height + px(Design.SPACE_XL)).toFloat()
                demo.animate().translationY(drop).alpha(0f).setDuration(Design.MOTION_PANEL).setInterpolator(Design.EASE_STANDARD).withEndAction {
                    demo.postDelayed({ demo.animate().translationY(0f).alpha(1f).setDuration(Design.MOTION_PANEL).start() }, CLOSED_MS)
                }.start()
                lessonCheck(); return true
            }
            if (demo.isAccentsOpen()) { demo.cancelAccents(); return true }
            if (lessonAt > 0) { feedback.back(); lessonAt -= 2; nextLesson(); return true }   // the turn before
            endLessons(); back()
            return true
        }
        if (lessonBusy) return true
        val key = DevicePicture.keyFor(keyCode) ?: return true
        // Let go: A ends a hold (the accents), RT ends the held combos
        if (!down) {
            if (key == "A") { aDown = false; if (l.lock != null) demo.releaseA(); dots.post { lessonCheck() } }
            if (key == "RT" && rtHeld) { rtHeld = false; demo.setHeldShift(false)
                if (lesson?.undo == true && typed + tail != l.goal) { stepAt = 0; showLessonStep(); note("Keep $rtName held, then X.") } }
            return true
        }
        if (key in DPAD_KEYS) {
            // The accents page: the highlight stays on the e until its accents are up (then it moves along them)
            if (l.lock != null && !demo.isAccentsOpen()) { note("Just the ${l.lock} this time."); return true }
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> demo.moveFocus(-1, 0); KeyEvent.KEYCODE_DPAD_RIGHT -> demo.moveFocus(1, 0)
                KeyEvent.KEYCODE_DPAD_UP -> demo.moveFocus(0, -1); else -> demo.moveFocus(0, 1)
            }
            return true
        }
        picture.flash(key)
        val before = typed + selected + tail + demo.shiftOn + sent + demo.onSymbols + selecting + copied + rtHeld
        when (key) {
            "A" -> { usedKey = true; aDown = true; if (l.lock != null) demo.pressA() else demo.pressSelected() }
            "X" -> {
                usedKey = false
                when {
                    l.undo && rtHeld -> { setField(l.goal, ""); demo.pressShortcut("X", KeyType.EMPTY, lit = false) }
                    l.undo -> { note("Hold $rtName first, then X."); return true }
                    selecting && selected.isNotEmpty() -> { selected = ""; selecting = false; demo.setSelecting(false, 0); showText(typed, after = tail); feedback.delete() }
                    else -> demo.pressShortcut("X", KeyType.DELETE, lit = false)
                }
            }
            "LB", "RB" -> moveCursor(if (key == "LB") -1 else 1)
            "SELECT" -> select()
            "LT" -> { usedKey = false; demo.toggleLayer() }
            "RS" -> if (controls.sticks) { usedKey = false; demo.wordsForCheck().firstOrNull()?.let { onWord(it) } }
            "Y" -> { usedKey = false; demo.pressShortcut("Y", KeyType.SPACE, lit = false) }
            "START" -> { if (offerSkip && !l.send) { skipLesson(); return true }; usedKey = false; demo.pressShortcut("START", KeyType.ENTER, lit = false) }
            "RT" -> { usedKey = false; if (l.undo) { rtHeld = true; demo.setHeldShift(true) } else demo.cycleShift() }
        }
        val changed = before != (typed + selected + tail + demo.shiftOn + sent + demo.onSymbols + selecting + copied + rtHeld)
        if (changed) { lastButton = ""; samePresses = 0; lessonCheck(); return true }
        // Nothing happened: the same button again and again gets a gentle word; one press, just the step again
        samePresses = if (key == lastButton) samePresses + 1 else 1
        lastButton = key
        if (samePresses >= SPAM_PRESSES) note("That one does nothing here.")
        else if (key !in listOf("A")) note("Not that one.")
        return true
    }

    /** LB and RB: the cursor a letter back or on; while selecting, the selection grows (back) or shrinks. */
    private fun moveCursor(dir: Int) {
        if (selecting) {
            if (dir < 0 && typed.isNotEmpty()) { selected = typed.last() + selected; typed = typed.dropLast(1) }
            else if (dir > 0 && selected.isNotEmpty()) { typed += selected.first(); selected = selected.drop(1) }
            demo.setSelecting(true, selected.length)
            showText(typed, selected, tail); feedback.move(); dots.post { lessonCheck() }; return
        }
        if (dir < 0 && typed.isNotEmpty()) { tail = typed.last() + tail; typed = typed.dropLast(1) }
        else if (dir > 0 && tail.isNotEmpty()) { typed += tail.first(); tail = tail.drop(1) }
        else return
        showText(typed, after = tail); feedback.move(); dots.post { lessonCheck() }
    }

    /** View: starts selecting; twice quickly (or again while selecting) selects everything. */
    private fun select() {
        val now = android.os.SystemClock.uptimeMillis()
        val gap = now - lastSelectAt
        // The same timing as the keyboard itself, so what you learn here works in your apps
        if (selecting && gap < KEYBOARD_TWICE_MS) {
            selected = typed + selected + tail; typed = ""; tail = ""
        } else if (selecting && selected.isEmpty() && gap < NEARLY_TWICE_MS && lesson?.steps?.firstOrNull()?.buttons == "{SELECTx2}") {
            // Twice, but not quite fast enough for "everything": say so, and start over cleanly
            selecting = false
            lastSelectAt = 0L
            demo.setSelecting(false, 0); showText(typed, after = tail); feedback.back()
            note("Nearly: a little quicker.")
            return
        } else if (!selecting) { selecting = true; selected = "" }
        else { selecting = false; typed += selected; selected = "" }
        lastSelectAt = now
        demo.setSelecting(selecting, selected.length)
        showText(typed, selected, tail); feedback.move()
        dots.post { lessonCheck() }
    }

    /** The guided pages (controllers): typing, typing faster, fixing. */
    private fun lessonsFor(p: Page): List<Lesson>? = if (p.audience != For.CONTROLLER) null else when (p.demo) {
        Demo.TYPE -> typingLessons()
        Demo.SHIFT_AND_SYMBOLS -> fasterLessons()
        Demo.EDIT -> fixingLessons()
        else -> null
    }

    // ---- The end of the tour ----------------------------------------------------------------------------------
    /** The turns that took a few tries (or help), with the lessons themselves, for a second go. */
    private val learntLesson = HashMap<String, Lesson>()
    private fun toReview(): List<Lesson> = learnt.filter { !it.second }.map { it.first }.distinct().mapNotNull { learntLesson[it] }
        .filter { t -> learnt.lastOrNull { it.first == t.title }?.second == false }
    private var reviewing = false

    /** You're all set: one line on how it went; a second go at what took a few tries, if anything did. */
    private fun showDone() {
        val again = toReview()
        title.text = if (learnt.isEmpty() || again.isEmpty()) "You're all set" else "Nicely done"
        body.text = when {
            remote || learnt.isEmpty() -> "That's everything. Settings → About replays this tour."
            again.isEmpty() -> "Every turn, first time. Hold $rtName any time to see what the keys do."
            else -> "Worth another go: ${again.joinToString(", ") { it.title.replaceFirstChar { c -> c.lowercase() } }}."
        }
        controlBar.show(settings.shownButtonStyle, if (remote) listOf("OK · Done", "Back · Previous") else listOfNotNull(
            "{A} Start typing", if (again.isNotEmpty() && !remote) "{Y} Try those again" else null, "{B} Back"))
    }

    /** Y on the last page: the turns that took a few tries, once more, then back here. */
    private fun reviewAgain(): Boolean {
        val again = toReview()
        if (again.isEmpty() || lessons.isNotEmpty()) return false
        reviewing = true
        startLessons(again)
        return true
    }

    /** For the checks. */
    internal val lessonForCheck get() = lesson?.title
    internal val fieldForCheck get() = typed + tail
    internal fun startTypingForCheck() = startLessons(typingLessons())
    internal fun skipLessonsForCheck() { if (lessons.isNotEmpty()) finishLessons() }
    internal fun lessonJumpForCheck(i: Int) { lessonAt = i - 1; nextLesson() }
    internal val lessonCountForCheck get() = lessons.size
    internal val stepCountForCheck get() = lesson?.steps?.size ?: 0
    internal fun lessonStepForCheck(i: Int) { stepAt = i; showLessonStep() }
    internal val demoForCheck get() = demo
    internal val selectedForCheck get() = selected
    internal val copiedForCheck get() = copied

    /** The page's demo has played once: over to you. */
    private fun beginTurn() {
        lessonsFor(pages[page])?.let { if (!finishing && lessons.isEmpty()) startLessons(it); return }
        val t = tryFor(pages[page]) ?: return
        if (finishing || turn != null) return
        handler.removeCallbacksAndMessages(null)        // the demo stops where it is: the keyboard is yours now
        beatList = emptyList(); beatHeld = false
        panel.loopShown = false
        picture.held = emptySet(); picture.holding = emptySet(); picture.stickPush = emptyMap()
        demo.setHeldShift(false)
        turn = t; turnRoot = t; turnDone = false
        t.setup()
        field.ghost = t.ghost
        turnLook(true)
        showStep(t)
        title.text = t.headline
        say(t.ask)
        stepCount(t)
        // A goal in steps: the page's button card steps right out (it lists the page, not this step). The words say
        // what to do, the picture lights the button, the text box shows what's still to type
        if (t.steps > 1) { rowsPane.animate().cancel(); rowsPane.alpha = 0f }   // at once: fading it let it flash up (Emre, 6 Nov)
        // Turns with guides: the button shows on the keyboard, so the controller picture steps out (it read as the
        // button being held: Emre, 6 Nov). It comes back, smaller, only to show where a button you may not know is
        // (showStep does both)
        controlBar.show(settings.shownButtonStyle, listOf("${t.badge} Try it",
            if (t.turns >= 2) "Hold {START} Skip the tour" else "{START} Skip this step", "{B} Back"))
        armHint()
    }

    /** The clip as help: once per page, when nothing has been pressed for [HINT_AFTER_MS]. */
    private var hinted = false
    /** The clip is playing as help: any button (but Start and B) goes straight back to trying. */
    private var watching = false
    private val hint = Runnable { playHint() }
    private fun armHint() { dots.removeCallbacks(hint); if (!hinted && !turnDone) dots.postDelayed(hint, HINT_AFTER_MS) }

    private fun playHint() {
        if (turn == null || turnDone || finishing) return
        val p = pages[page]
        hinted = true
        endTurn()
        startDemo(p.demo)
        watching = true
        eyebrow.text = "Here's how"
        eyebrowCount.visibility = View.GONE
        title.text = p.title; body.text = p.body
        controlBar.show(settings.shownButtonStyle, listOf("{A} Try it now", "{START} Skip this step", "{B} Back"))
        // One run of the clip, then over to you again (posted: the clip's own step finishes and stops first)
        val back = { if (watching) { watching = false; eyebrowCount.visibility = View.VISIBLE; beginTurn() } }
        if (WhatsNew.motionAllowed()) loop.onFirstRunDone = { dots.post(back) } else handler.postDelayed(back, STILL_TURN_MS)
    }

    /** A button while the help clip plays: Start and B as usual, anything else is "let me try". */
    private fun watchingKey(keyCode: Int): Boolean {
        if (!watching || keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) return false
        watching = false
        eyebrowCount.visibility = View.VISIBLE
        beginTurn()
        return true
    }

    /**
     * Your turn must look like a different moment from the demo (Emre, 5 Nov 2026: "ambiguous and subtle"). The label
     * says YOUR TURN, the title becomes what to do, the button list steps back, and the keyboard's window takes your
     * highlight colour on its edge: the keyboard is yours. The words arrive like a new page.
     */
    private fun turnLook(on: Boolean) {
        if (on) {
            eyebrow.text = "Your turn"
            eyebrowCount.visibility = View.GONE
            rowsPane.animate().alpha(TURN_ROWS_ALPHA).setDuration(Design.MOTION_PAGE).start()
            (panel.background as? android.graphics.drawable.GradientDrawable)?.setStroke(px(TURN_EDGE_DP), markerColor(settings.activeAccent, whatsNew))
            if (WhatsNew.motionAllowed()) {
                words.alpha = 0f; words.translationX = px(12f).toFloat()
                words.animate().alpha(1f).translationX(0f).setDuration(Design.MOTION_PAGE).setInterpolator(Design.EASE_OUT).start()
            }
        } else {
            eyebrowCount.visibility = View.VISIBLE
            rowsPane.animate().cancel(); rowsPane.alpha = 1f
            (panel.background as? android.graphics.drawable.GradientDrawable)?.setStroke(max(2, px(1f)), PANEL_EDGE)
        }
    }

    /** What to do; a lead ("Nearly.", "Not that one.") in bold and the label colour first. */
    private fun say(what: String, lead: String = "") {
        if (lead.isEmpty()) { body.text = what; return }          // the label already says Your turn
        val text = android.text.SpannableString("$lead $what")
        text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, lead.length, 0)
        text.setSpan(android.text.style.ForegroundColorSpan(labelColor(settings.activeAccent, whatsNew)), 0, lead.length, 0)
        body.text = text
    }

    /** A button during a turn. True when the turn took it. */
    private fun turnKey(keyCode: Int, down: Boolean): Boolean {
        val t = turn ?: return false
        if (turnDone) return true
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
            if (down) { endTurn(); back() }                 // the page before (try first: no demo phase to go back to)
            return true
        }
        val key = DevicePicture.keyFor(keyCode) ?: return false
        if (down) armHint()
        if (t.moves && key in DPAD_KEYS) {                  // a turn that types lets you move the highlight
            if (down) when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> demo.moveFocus(-1, 0); KeyEvent.KEYCODE_DPAD_RIGHT -> demo.moveFocus(1, 0)
                KeyEvent.KEYCODE_DPAD_UP -> demo.moveFocus(0, -1); else -> demo.moveFocus(0, 1)
            }
            return true
        }
        if (key == t.key) {
            val wrong = if (down) t.check?.invoke() else null
            if (wrong != null) { feedback.back(); say(wrong, "Not quite."); return true }
            if (down) { picture.holding = setOf(t.key); if (t.press()) doneTurn() }
            else {
                picture.holding = emptySet()
                t.release?.let { if (it()) doneTurn() else { feedback.back(); say(t.tooSoon, "Nearly.") } }
            }
            return true
        }
        // Another button: nothing happens, and the card points at the lit one
        if (down) { feedback.back(); picture.flash(t.key, NUDGE_MS)
            say(if (t.guide != null) t.ask else "${t.ask} It's lit on the picture.", "Not that one.") }
        return true
    }

    /** "Your turn · 2 of 3" while a goal has more than one step. */
    private fun stepCount(t: Try) {
        val root = turnRoot ?: return
        if (root.turns >= 2) {      // turns with their own headlines: count turns ("1 of 4"), not steps
            eyebrowCount.text = "· ${root.turns - t.turns + (if (t.headline.isEmpty()) 0 else 1)} of ${root.turns}"
            eyebrowCount.visibility = View.VISIBLE; return
        }
        val all = root.steps
        if (all < 2) return
        eyebrowCount.text = "· ${all - t.steps + 1} of $all"; eyebrowCount.visibility = View.VISIBLE
    }

    /** A step's look: the key it's about on the keyboard, and the picture only when it says where a button is. */
    private fun showStep(t: Try) {
        demo.guide = t.guide
        picture.held = setOf(t.key); picture.holding = emptySet()
        if (t.guide == null) return
        // Where a button is: the picture, smaller, as a pointer rather than a stage (pivot: its top middle)
        picture.pivotX = picture.width / 2f; picture.pivotY = 0f
        val s = if (t.where) WHERE_SCALE else 1f
        picture.animate().alpha(if (t.where) 1f else 0f).scaleX(s).scaleY(s).setDuration(Design.MOTION_FADE).start()
    }

    private fun doneTurn() {
        val t = turn
        val next = t?.then
        if (next != null && next.headline.isNotEmpty()) {     // a turn done, the next one after a breath
            feedback.confirm()
            turnDone = true                       // the guide stays until the next one: the keys' small badges don't flicker back
            say("", "Done.")
            handler.postDelayed({
                if (turn !== t) return@postDelayed
                turnDone = false; turn = next
                next.setup(); title.text = next.headline
                showStep(next); say(next.ask); stepCount(next)
                controlBar.show(settings.shownButtonStyle, listOf("${next.badge} Try it", "Hold {START} Skip the tour", "{B} Back"))
                armHint()
            }, TURN_GAP_MS)
            return
        }
        if (next != null) {                       // a step within a turn: the next one, straight away
            feedback.confirm()
            turn = next
            showStep(next)
            say(next.ask, "Good.")
            stepCount(next)
            controlBar.show(settings.shownButtonStyle, listOf("${next.badge} Try it",
                if ((turnRoot?.turns ?: 0) >= 2) "Hold {START} Skip the tour" else "{START} Skip this step", "{B} Back"))
            armHint()
            return
        }
        turnDone = true
        dots.removeCallbacks(hint)
        feedback.confirm()
        demo.guide = null
        picture.held = emptySet(); picture.holding = emptySet()
        picture.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Design.MOTION_FADE).start()
        title.text = "That's it"
        say("On to the next one.", "Nice.")
        handler.postDelayed({ endTurn(); next() }, TURN_DONE_MS)
    }

    private fun endTurn() { if (turn != null) { turnLook(false); field.ghost = null; demo.guide = null; picture.alpha = 1f; picture.scaleX = 1f; picture.scaleY = 1f; tail = "" }; turn = null; turnRoot = null; turnDone = false; rtDown = false }

    /** For the checks: whether a turn is on, and its button. */
    internal val turnForCheck get() = turn?.key
    internal fun beginTurnForCheck() = beginTurn()
    /** For the checks: one D-pad step towards a letter on the demo keyboard (true once it's there). */
    internal fun stepDemoForCheck(label: String) = demo.stepTowards(label)

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
        dots.removeCallbacks(hint)
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
        const val START_HOLD_MS = 600L      // hold Start this long to skip the tour
        const val TURN_ROWS_ALPHA = 0.35f   // the button list steps back during your turn
        const val HINT_AFTER_MS = 4000L     // nothing pressed this long: the clip plays once, as help
        const val RT_HOLD_MS = 600L         // RT held this long counts as holding it
        const val TURN_EDGE_DP = 2f
        const val TURN_DONE_MS = 1100L      // "That's it" stays this long, then the next page
        const val NUDGE_MS = 900L
        const val STILL_TURN_MS = 2500L
        const val TRIGGER_DOWN = 0.5f
        const val TRIGGER_UP = 0.3f
        private val DPAD_KEYS = setOf("Dup", "Ddown", "Dleft", "Dright")
        /** "Your turn" on tour pages. The other checks page through with A and watch the demos, so they run without it. */
        @JvmField internal var yourTurn = System.getProperty("inlay.checks.noTurns") == null
        /** The button setup before the tour (off for the other checks, which test the tour itself). */
        @JvmField internal var askSetup = System.getProperty("inlay.checks.noSetup") == null
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
        const val PICTURE_NARROW_MIN_DP = 96f  // ...and the least it gives up to the words on a short upright screen
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
        const val PIP_DP = 6f                        // the card's progress dots
        const val CARD_TAG = "step-card"
        const val CARD_BADGE = 1.35f                 // the card's buttons: this much bigger than a list row's
        const val CARD_BADGE_SHORT = 1.15f
        const val WORD_LOOK_MS = 1000L               // the word suggestions, complete, on show (about a second) before one is taken
        const val CARD_BADGE_COL_MAX = 0.4f          // the card's button column at most this share of its width (else the buttons sit above the words)
        const val SEQ_DOT_DP = 18f                   // a mini-step's numbered dot
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
/** The goal's faint letters in the text box: secondary text at about 45 %. */
/** The breath between two turns: long enough to see what you did, short enough to keep moving. */
/** The controller picture when it only shows where a button is. */
private const val WHERE_SCALE = 0.72f
/** Guided turns: help after this long with no progress, the way to skip after this long. */
private const val STUCK_MS = 12_000L
private const val STUCK_SKIP_MS = 30_000L
/** The same button this many times with nothing happening: a gentle word. */
private const val SPAM_PRESSES = 3
/** A turn starting again after going too far off; a turn's tip staying a moment longer. */
private const val RESTART_MS = 1_400L
private const val TIP_MS = 1_900L
/** Twice, but slower than this and within this: "a little quicker" (the real double press is the keyboard's own). */
private const val NEARLY_TWICE_MS = 1_500L
/** The keyboard's own double press (GridKeyboardService.DOUBLE_PRESS_MS): the tour uses the same. */
private const val KEYBOARD_TWICE_MS = 350L
/** Close: how long the keyboard stays down before it comes back. */
private const val CLOSED_MS = 900L
/** A card note too long for its line shrinks to no less than this before it's cut. */
private const val NOTE_MIN_SCALE = 0.82f
/** Make it yours: each row of the card stays this long, to read in peace. */
private const val READ_ROW_MS = 4_200L
private const val TURN_GAP_MS = 650L
private const val GHOST_ALPHA = 0x73
private val DEMO_APP_FONT: android.graphics.Typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

class DemoFieldView(context: android.content.Context) : View(context) {

    var placeholder = "Type with your controller"

    /** What your turn asks you to type, shown faintly after what's typed so far (1.3). Gone once it doesn't match. */
    var ghost: String? = null
        set(v) { field = v; invalidate() }

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
        if (before.isEmpty() && selected.isEmpty() && after.isEmpty() && ghost == null) {
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
        // The goal, faintly, right after what you've typed: what's still to come
        val g = ghost
        if (g != null && after.isEmpty() && selected.isEmpty() && g.startsWith(before) && g.length > before.length) {
            text.color = Contrast.withAlpha(SettingsStyle.textSecondary, GHOST_ALPHA)
            canvas.drawText(g.substring(before.length), x + 2 * dp * scale, baseline, text)
        }
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

    /**
     * While the Keyboard size row plays: the window is measured for this size (Largest), and every size is drawn at
     * that one scale inside it. Normal shows smaller, Largest fills the window, so the keys really grow, while the
     * window (and the device picture beside it) keeps still. Before, each size was fitted to the same window, so the
     * keys never grew and the picture was squeezed (Odin test, 5 Nov 2026). Null: each size fits the window itself.
     */
    var holdLevel: Int? = null
        set(v) {
            if (field == v) return
            field = v
            // Held: the keyboard sits on the window's bottom edge, like a real one growing up from the bottom
            gravity = if (v != null) android.view.Gravity.BOTTOM else android.view.Gravity.TOP
            requestLayout()
        }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (!framed) { super.onMeasure(widthSpec, heightSpec); return }
        val w = MeasureSpec.getSize(widthSpec)
        val h = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else MeasureSpec.getSize(heightSpec)
        val g = if (fieldShown) gap else 0
        val level = holdLevel ?: demo.shownSizeLevel()
        val fitW = (w - 2f * pad) / demo.previewUnitWidthPx(level)
        val fitH = (h - 2f * pad - g) / (demo.previewUnitHeightPx(level) + if (fieldShown) fieldUnit else 0)
        val fit = minOf(1f, fitW, fitH).coerceAtLeast(0.3f)
        demo.previewFit = fit
        field.scale = fit
        (field.layoutParams as LayoutParams).height = (fieldUnit * fit).toInt()
        (clip.layoutParams as LayoutParams).topMargin = g
        val panelW = minOf(w, (demo.previewUnitWidthPx(level) * fit + 2 * pad).toInt())
        // Held: the window keeps the held size's height too
        val held = holdLevel?.let { (2 * pad + g + fit * (demo.previewUnitHeightPx(it) + if (fieldShown) fieldUnit else 0)).toInt() }
        super.onMeasure(MeasureSpec.makeMeasureSpec(panelW, MeasureSpec.EXACTLY),
            if (held != null) MeasureSpec.makeMeasureSpec(minOf(held, h), MeasureSpec.EXACTLY) else heightSpec)
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
