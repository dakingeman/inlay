package com.example.gridime

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which controller's button icons the keyboard shows. */
enum class ButtonStyle(val label: String) {
    XBOX("Xbox"),
    PLAYSTATION("PlayStation"),
    SWITCH("Switch")
}

/** Letter font choices. The first two ship inside the app (res/font). */
enum class FontChoice(val label: String) {
    HYPERLEGIBLE("Hyperlegible"),
    INTER("Inter"),
    SYSTEM("System")
}

/** TV mode: a floating keyboard with a big preview line. Automatic = docked to a TV, or Android TV. */
enum class TvMode(val label: String) {
    AUTO("Automatic"),
    ON("On"),
    OFF("Off")
}

/** Key arrangement. Grid lines every key up in columns, which suits a D-pad best. */
enum class KeyLayout(val label: String) {
    GRID("Grid"),
    CLASSIC("Classic")
}

/** How the highlight travels between keys. */
enum class HighlightMotion(val label: String) {
    INSTANT("Instant"),
    SUBTLE("Subtle"),
    FLUID("Fluid")
}

/**
 * Which set of sounds plays. Every pack has the same cues with the same meanings,
 * so switching never changes what a sound tells you. Settings shows them in this order
 * (physical packs first, then the tonal ones); the choice is saved by name, so the order
 * can change freely. [file] is the prefix of the pack's sounds in res/raw.
 */
enum class SoundPack(val label: String, val description: String, val file: String) {
    SOFT("Soft", "A gentle thud with a crisp click. The default", "soft"),
    THOCK("Thock", "A deep, rounded mechanical key", "thock"),
    TACTILE("Tactile", "Dry, crisp switch clicks, no notes", "tactile"),
    TYPEWRITER("Typewriter", "A metal strike, with a small bell on Enter", "typewriter"),
    FELT("Felt", "Very soft taps for playing at night", "felt"),
    CHIME("Chime", "Wooden mallet notes", "chime"),
    GLASS("Glass", "Tiny tuned taps on glass", "glass"),
    POP("Pop", "Round little drops, each on its own note", "pop"),
    RETRO("Retro", "Short 8-bit blips, like a handheld's menus", "retro");

    companion object {
        /** Saved choices from older versions: Soft Deep became Thock in 1.2. */
        fun fromSaved(name: String?): SoundPack? = when (name) {
            "SOFT_DEEP" -> THOCK
            else -> values().firstOrNull { it.name == name }
        }
    }
}

/** How the selected key is marked (after Android TV's focus system: fill, glow, outline, lift). */
enum class HighlightStyle(val label: String) {
    GLOW("Glow"),
    SOLID("Solid"),
    OUTLINE("Outline"),
    LIFT("Lift")
}

/** Key corner roundness. */
enum class KeyShape(val label: String, val radiusDp: Float) {
    SHARP("Sharp", 5f),
    SOFT("Soft", 9f),
    ROUND("Round", 14f)
}

/**
 * The physical buttons, named by Android's keycodes.
 * A = bottom face button, B = right, X = left, Y = top (Xbox positions).
 */
enum class ControllerButton { A, B, X, Y, L1, R1, L2, R2, SELECT, START, L3, R3;
    /** A stick press (or push): not there on most vertical handhelds, never on a remote. */
    val isStick get() = this == L3 || this == R3
}

/**
 * A key colour set: keyboard background, letter keys, special keys.
 * [edge] draws a thin border round every key (0 = none).
 * [clear] makes the keyboard see-through: the app behind shows, keys get a dark tint.
 */
data class Tone(
    val name: String,
    val background: Int,
    val key: Int,
    val special: Int,
    val edge: Int = 0,
    val clear: Boolean = false,
    /** Special keys with a colour of their own (Indigo's red delete, yellow ?123...); the rest use [special]. */
    val specials: Map<KeyType, Int> = emptyMap(),
    /** A theme that comes with its own highlight (colour and style) and its own sound pack (the retro set). */
    val ownHighlight: Int? = null,
    val ownStyle: HighlightStyle? = null,
    val ownSound: SoundPack? = null,
    /** One line on what the theme brings, shown under Theme. */
    val note: String? = null,
    /** The retro themes' console looks: see [RetroLook]. */
    val look: RetroLook? = null
)

/**
 * What makes a retro theme look like its console, beyond its colours: the ground's pattern, a darker lip under
 * each key (chunky old buttons), a soft shadow and glossy top (a glossy white menu), rounded letters, its own
 * corner shape, and the button hints drawn in its controller's style.
 */
data class RetroLook(
    val pattern: Pattern = Pattern.NONE,
    val patternColour: Int = 0,
    val lip: Int = 0,
    val shadow: Boolean = false,
    val gloss: Int = 0,               // the glossy top half's tint (0: none)
    val rounded: Boolean = false,
    val shape: KeyShape? = null,
    val badges: ButtonStyle? = null   // the button hints in this controller's style (by position, so still right)
) { enum class Pattern { NONE, RIDGES, STRIPES } }

object Palette {
    val accents: List<Pair<String, Int>> = listOf(
        "Sky" to "#8AB4F8",
        "Ocean" to "#4FA8FF",
        "Inlay" to "#72D6E8",      // the brand teal, same as the app icon (was Cyan #4DD9E8); = Design.BRAND_ACCENT (TokenSyncTest)
        "Teal" to "#3FD1B0",
        "Mint" to "#7EE6A8",
        "Lime" to "#B5E853",
        "Lemon" to "#F5E46A",
        "Amber" to "#FFC857",
        "Tangerine" to "#FF9F5A",
        "Coral" to "#FF8A80",
        "Cherry" to "#FF5C6C",     // 1.2: a clear red, bright enough to stay as chosen on every dark theme; Indigo (#8C9EFF) went, it looked like Lavender (audit, 30 Oct 2026)
        "Rose" to "#F48FB1",
        "Orchid" to "#E27AE8",
        "Lavender" to "#B39DFF",
        "Sand" to "#D9C7A7",
        "Ice" to "#E8EAED"
    ).map { it.first to Color.parseColor(it.second) }

    private val baseTones: List<Tone> = listOf(
        Tone("OLED", Color.BLACK, Color.parseColor("#1B1B1F"), Color.parseColor("#111114")),
        Tone("Graphite", Color.parseColor("#0E0E10"), Color.parseColor("#26262B"), Color.parseColor("#1A1A1E")),
        Tone("Midnight", Color.parseColor("#03060C"), Color.parseColor("#172030"), Color.parseColor("#0F1622")),
        Tone("Forest", Color.parseColor("#030805"), Color.parseColor("#16231B"), Color.parseColor("#0E1812")),
        Tone("Mocha", Color.parseColor("#0B0806"), Color.parseColor("#261E19"), Color.parseColor("#1A1511")),
        Tone("Slate", Color.parseColor("#05070A"), Color.parseColor("#1E242C"), Color.parseColor("#14191F")),
        Tone("Plum", Color.parseColor("#08040A"), Color.parseColor("#241A2B"), Color.parseColor("#18111D")),
        Tone("Ocean", Color.parseColor("#02080A"), Color.parseColor("#132429"), Color.parseColor("#0C191D")),
        // Added in 1.1. New ones always go at the end, because settings store the position.
        // Light: like Gboard's light theme, for bright rooms and sunlight
        Tone("Light", Color.parseColor("#D7DAE0"), Color.WHITE, Color.parseColor("#BFC4CC")),
        // Outline: black keys with thin borders (Gboard / HeliBoard "key borders"). Border is 3:1 on black
        Tone("Outline", Color.BLACK, Color.BLACK, Color.parseColor("#0B0B0D"), edge = Color.parseColor("#62656D")),
        // Clear: see-through. Real colours are worked out from the See-through setting
        Tone("Clear", Color.TRANSPARENT, Color.parseColor("#B3000000"), Color.parseColor("#C7000000"),
            edge = Color.parseColor("#26FFFFFF"), clear = true)
    )

    /** Wallpaper colours (Android 12+), like Gboard's Material You theme. Filled in by [init]. */
    private var wallpaperTone: Tone? = null

    /** Reads the wallpaper colours. Call once from the keyboard and from Settings. */
    fun init(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        wallpaperTone = runCatching {
            Tone(
                "Wallpaper",
                context.getColor(android.R.color.system_neutral1_900),
                context.getColor(android.R.color.system_neutral2_700),
                context.getColor(android.R.color.system_neutral2_800)
            )
        }.getOrNull()
    }

    val tones: List<Tone> get() = baseTones + listOfNotNull(wallpaperTone)

    /**
     * The Retro collection, found with the Konami Code: three themes after classic consoles, each with its own
     * highlight and sound pack. Saved as theme number [RETRO_BASE] + its position, so the other themes' numbers
     * never move.
     */
    val retro: List<Tone> = listOf(
        Tone("Grey brick", Color.parseColor("#C8C8C6"), Color.parseColor("#DEDEDC"), Color.parseColor("#5E5E66"),
            specials = mapOf(KeyType.SPACE to Design.RETRO_BRICK_SPECIAL),
            ownHighlight = Design.RETRO_BRICK_HIGHLIGHT, ownStyle = HighlightStyle.OUTLINE, ownSound = SoundPack.RETRO,
            note = "Light grey keys, the four shapes, a blue cursor and 8-bit sounds",
            look = RetroLook(RetroLook.Pattern.RIDGES, Design.RETRO_BRICK_RIDGE, lip = Design.RETRO_BRICK_LIP, shape = KeyShape.SOFT, badges = ButtonStyle.PLAYSTATION)),
        Tone("Indigo", Color.parseColor("#47487A"), Color.parseColor("#585990"), Color.parseColor("#C4C4CC"),
            specials = mapOf(KeyType.DELETE to Design.RETRO_INDIGO_DELETE, KeyType.LAYER to Design.RETRO_INDIGO_LAYER),
            ownHighlight = Design.RETRO_INDIGO_HIGHLIGHT, ownStyle = HighlightStyle.SOLID, ownSound = SoundPack.POP,
            note = "Indigo keys, a big green highlight, round letters and little pops",
            look = RetroLook(lip = Design.RETRO_INDIGO_LIP, gloss = Design.RETRO_INDIGO_GLOSS, rounded = true, shape = KeyShape.ROUND)),
        Tone("Gloss white", Color.parseColor("#F3F3F3"), Color.WHITE, Color.parseColor("#E6E6E8"), edge = Color.parseColor("#CECED2"),
            ownHighlight = Design.RETRO_WHITE_HIGHLIGHT, ownStyle = HighlightStyle.OUTLINE, ownSound = SoundPack.GLASS,
            note = "White tiles on a striped ground, a soft blue glow and glassy taps",
            look = RetroLook(RetroLook.Pattern.STRIPES, Design.RETRO_WHITE_STRIPE, shadow = true, gloss = Design.RETRO_WHITE_GLOSS, rounded = true, shape = KeyShape.ROUND))
    )
    const val RETRO_BASE = 100
    fun toneAt(index: Int): Tone = if (index >= RETRO_BASE) retro[(index - RETRO_BASE).coerceIn(0, retro.lastIndex)]
        else tones[index.coerceIn(0, tones.lastIndex)]

    /** A custom accent from a hue (0..359), kept as bright and soft as the preset colours. */
    /**
     * A custom highlight colour from three sliders: [hue] (0–359, around the colour wheel), [strength]
     * (0–100, soft to vivid) and [brightness] (0–100, deeper to lighter). Made in OKLCH, so every hue is
     * equally bright at the same setting. Brightness only moves within [CUSTOM_L_MIN]–[CUSTOM_L_MAX]: the
     * colour is always light enough to read on the dark backgrounds, and dark or white ink is picked for
     * text on it (Contrast.inkFor). Colours a screen can't show are softened, never shifted in hue.
     * The consistency checks prove this for every setting.
     */
    fun customAccent(hue: Int, strength: Int = DEFAULT_STRENGTH, brightness: Int = DEFAULT_BRIGHTNESS): Int {
        val l = customLightness(brightness); val h = hue.toDouble().mod(360.0)
        return AccentLight.color(l, customChroma(strength, l, h), h)
    }

    fun customLightness(brightness: Int) = CUSTOM_L_MIN + (CUSTOM_L_MAX - CUSTOM_L_MIN) * brightness.coerceIn(0, 100) / 100.0
    /** Strength runs from a hint of colour to the most vivid this hue can be at this brightness (capped), so the whole slider always does something. */
    fun customChroma(strength: Int, lightness: Double, hue: Double): Double {
        val top = minOf(CUSTOM_C_MAX, AccentLight.maxChroma(lightness, hue)).coerceAtLeast(CUSTOM_C_MIN)
        return CUSTOM_C_MIN + (top - CUSTOM_C_MIN) * strength.coerceIn(0, 100) / 100.0
    }

    const val CUSTOM_L_MIN = 0.66       // the deepest a custom colour gets: still 6:1 against black
    const val CUSTOM_L_MAX = 0.92
    const val CUSTOM_C_MIN = 0.03       // soft: a hint of colour
    const val CUSTOM_C_MAX = 0.22       // vivid (or as vivid as the hue allows)
    const val DEFAULT_STRENGTH = 55     // about the presets' strength and brightness
    const val DEFAULT_BRIGHTNESS = 72
}

/** Everything the Settings app can change. Saved on the device, read by the keyboard. */
data class KeyboardSettings(
    // Look
    val buttonStyle: ButtonStyle = ButtonStyle.XBOX,
    val accentIndex: Int = 2,           // -1 = custom colour from customHue. 2 = Inlay teal, the brand colour
    val customHue: Int = 210,           // 0 to 359, around the OKLCH colour wheel
    val customStrength: Int = Palette.DEFAULT_STRENGTH,       // 0 to 100, soft to vivid
    val customBrightness: Int = Palette.DEFAULT_BRIGHTNESS,   // 0 to 100, deeper to lighter (always readable)
    val toneIndex: Int = 0,
    val font: FontChoice = FontChoice.HYPERLEGIBLE,
    val letterWeight: Int = 4,          // 0 to 10
    val sizeLevel: Int = 0,             // 0 normal, 1 large, 2 largest
    val tvMode: TvMode = TvMode.AUTO,
    val layout: KeyLayout = KeyLayout.GRID,
    val language1: Language = Language.ENGLISH,      // main language
    val language2: Language? = null,                 // optional extra languages for the quick switch
    val language3: Language? = null,
    val activeLanguage: Int = 0,                     // which of the three is in use (RT + LT cycles)
    val showLanguageLabel: Boolean = true,           // language name on the space bar
    val keyShape: KeyShape = KeyShape.SOFT,
    val motion: HighlightMotion = HighlightMotion.SUBTLE,
    val highlightStyle: HighlightStyle = HighlightStyle.GLOW,
    val seeThrough: Int = 6,            // 0 to 10, only used by the Clear theme
    // Controls
    val stickSpeed: Int = 2,            // 0 slow to 4 fast
    val rightStick: Boolean = true,     // right stick moves the text cursor
    val showHints: Boolean = true,      // badges on keys
    val comboHints: Boolean = true,     // chips while holding RT
    val selectAllButton: Boolean = false,
    val doubleSpacePeriod: Boolean = true,
    val autoCaps: Boolean = true,
    val learnWords: Boolean = true,      // words you type often come first in the suggestions
    val tidyPunctuation: Boolean = true, // "word ." becomes "word. "        // a capital after . ! ? and at the start of sentences, where the box allows
    val undoCombo: Boolean = true,
    val smartKeys: Boolean = true,      // .com, / and @ where they help, a number pad for numbers
    val wordSuggestions: Boolean = true, // words in the tool strip while typing (R3 takes the first)
    val holdAccents: Boolean = true,     // hold A on a letter for its accents
    val numberRow: Boolean = false,      // a slim row of 1 to 0 above the letters
    val picture: PictureChoice = PictureChoice.AUTO,   // which device the tour, What's new and Button guide show
    val verticalSticks: Boolean = false,  // a vertical handheld that does have sticks
    val swapFaceButtons: Boolean = false, // the device reports A/B and X/Y by printed letter (Nintendo layout): see FaceButtons
    val testFullScreen: Boolean = false,  // Try your buttons shows the device across the whole screen
    val labBuildUp: Int = 0,              // test builds only: which build-up sound the Konami Code plays (see KonamiCode)
    val labBuzzStrength: Int = 1,         // test builds only: Light, Medium, Strong, Max (Emre: Medium)
    val labBuzzStyle: Int = 2,            // test builds only: Calibrated, Taps, Pulses, Rumble (Emre: Pulses or Rumble)
    val secretNew: Boolean = false,       // the Konami Code was found and Look's new themes haven't been seen yet
    val labReward: Int = 0,
    val retroUnlocked: Boolean = false,   // the Konami Code was entered: the Retro collection is in Look
    val themeHighlight: Boolean = true,   // a retro theme's own highlight colour and style
    val themeSound: Boolean = true,       // a retro theme's own sound pack
    val lastInlayTone: Int = 0,           // the Inlay theme to go back to when Collection goes back to Inlay
    val secretSoundNew: Boolean = false,  // a retro theme was picked: its sounds are new in Sound & haptics
    val secretSoundShown: Boolean = false, // ...shown once already (it isn't marked again)               // test builds only: which reward design (see SecretScreen.design)
    val secretRowNew: Boolean = false,    // ...and the Theme row (where they are) hasn't been visited yet
    val hasSticks: Boolean = true,        // worked out from the device, never saved (see Controls)
    // Sound & haptics
    val vibration: Boolean = true,
    val moveStrength: Int = 5,          // 0 to 10
    val pressStrength: Int = 6,         // 0 to 10
    val soundVolume: Int = 7,           // 0 (off) to 10
    val soundPack: SoundPack = SoundPack.SOFT,
    val moveSounds: Boolean = true,
    val soundInSilent: Boolean = false,
    // Clipboard
    val saveClipHistory: Boolean = true,
    // Settings app
    val showPreview: Boolean = true,
    val tutorialDone: Boolean = false,
    val whatsNewSeen: String = ""      // the update whose "What's new" was last shown (WhatsNew.VERSION)
) {
    /** The languages in the quick switch, in order, without repeats. */
    val languages: List<Language> get() = listOfNotNull(language1, language2, language3).distinct()

    /** The language being typed right now. */
    val language: Language get() = languages[activeLanguage.coerceIn(0, languages.lastIndex)]

    /** The next language in the quick switch (wraps round). */
    fun nextLanguage(): KeyboardSettings =
        copy(activeLanguage = (languages.indexOf(language) + 1) % languages.size)

    val accent: Int
        get() = if (accentIndex < 0) Palette.customAccent(customHue, customStrength, customBrightness)
        else Palette.accents[accentIndex.coerceIn(0, Palette.accents.lastIndex)].second
    val tone: Tone get() = Palette.toneAt(toneIndex)
    val retroTheme: Boolean get() = toneIndex >= Palette.RETRO_BASE
    /** What's in use: a retro theme's own highlight and sounds, unless they've been turned off. */
    val activeAccent: Int get() = tone.ownHighlight?.takeIf { themeHighlight } ?: accent
    val activeHighlightStyle: HighlightStyle get() = tone.ownStyle?.takeIf { themeHighlight } ?: highlightStyle
    val activeSoundPack: SoundPack get() = tone.ownSound?.takeIf { themeSound } ?: soundPack

    /**
     * Saved as a small file instead of Android's usual settings store. Settings runs in
     * its own process (so closing it can never take the keyboard down), and a file read
     * fresh each time is the reliable way for both processes to see the same values.
     */
    fun save(context: Context) {
        val json = JSONObject()
            .put(K_STYLE, buttonStyle.name)
            .put(K_ACCENT_2, accentIndex)
            .put(K_HUE, customHue)
            .put(K_STRENGTH, customStrength)
            .put(K_BRIGHTNESS, customBrightness)
            .put(K_TONE, toneIndex)
            .put(K_FONT, font.name)
            .put(K_WEIGHT, letterWeight)
            .put(K_SIZE, sizeLevel)
            .put(K_LAYOUT, layout.name)
            .put(K_LANGUAGE, language1.name)
            .put(K_LANGUAGE2, language2?.name ?: NONE)
            .put(K_LANGUAGE3, language3?.name ?: NONE)
            .put(K_ACTIVE_LANGUAGE, activeLanguage)
            .put(K_LANGUAGE_LABEL, showLanguageLabel)
            .put(K_SMART_KEYS, smartKeys)
            .put(K_WORDS, wordSuggestions)
            .put(K_HOLD_ACCENTS, holdAccents)
            .put(K_NUMBER_ROW, numberRow)
            .put(K_PICTURE, picture.name)
            .put(K_VERTICAL_STICKS, verticalSticks)
            .put(K_SWAP_FACE, swapFaceButtons)
            .put(K_TEST_FULL, testFullScreen)
            .put(K_LAB_BUILDUP, labBuildUp)
            .put(K_LAB_BUZZ, labBuzzStrength)
            .put(K_LAB_BUZZ_STYLE, labBuzzStyle)
            .put(K_SECRET_NEW, secretNew)
            .put(K_SECRET_ROW, secretRowNew)
            .put(K_LAB_REWARD, labReward)
            .put(K_RETRO, retroUnlocked).put(K_THEME_HL, themeHighlight).put(K_THEME_SOUND, themeSound)
            .put(K_LAST_INLAY, lastInlayTone).put(K_SOUND_NEW, secretSoundNew).put(K_SOUND_SHOWN, secretSoundShown)
            .put(K_TV_MODE, tvMode.name)
            .put(K_TUTORIAL, tutorialDone)
            .put(K_WHATS_NEW, whatsNewSeen)
            .put(K_SHAPE, keyShape.name)
            .put(K_MOTION, motion.name)
            .put(K_HIGHLIGHT, highlightStyle.name)
            .put(K_SEE_THROUGH, seeThrough)
            .put(K_STICK, stickSpeed)
            .put(K_RIGHT_STICK, rightStick)
            .put(K_HINTS, showHints)
            .put(K_COMBO_HINTS, comboHints)
            .put(K_SELECT_ALL_BUTTON, selectAllButton)
            .put(K_DOUBLE_SPACE, doubleSpacePeriod)
            .put(K_AUTO_CAPS, autoCaps)
            .put(K_LEARN, learnWords)
            .put(K_TIDY, tidyPunctuation)
            .put(K_UNDO, undoCombo)
            .put(K_VIBRATION, vibration)
            .put(K_MOVE, moveStrength)
            .put(K_PRESS, pressStrength)
            .put(K_VOLUME, soundVolume)
            .put(K_PACK, soundPack.name)
            .put(K_MOVE_SOUNDS, moveSounds)
            .put(K_SILENT, soundInSilent)
            .put(K_SILENT_2, soundInSilent)
            .put(K_CLIP_HISTORY, saveClipHistory)
            .put(K_PREVIEW, showPreview)
        SmallFile.write(context, FILE, json.toString())
    }

    companion object {
        /** Android's settings store, used by version 1.0. Only read once, to bring old settings across. */
        const val PREFS = "gridime"
        private const val FILE = "settings.json"

        private const val K_STYLE = "button_style"
        private const val K_ACCENT = "accent"
        /**
         * From 1.2 the colour list changed (Cherry in, Indigo out), so the choice has a new name and the old one is
         * read once through [ACCENT_1_1]: everyone keeps exactly the colour they had. Indigo becomes the same colour
         * as a custom colour.
         */
        private const val K_ACCENT_2 = "accent_2"
        private val ACCENT_1_1 = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, -2, 14, 15)   // -2: Indigo
        private const val INDIGO = "#8C9EFF"
        private const val K_HUE = "custom_hue"
        private const val K_STRENGTH = "custom_strength"
        private const val K_BRIGHTNESS = "custom_brightness"
        private const val K_PACK = "sound_pack"
        private const val K_SHAPE = "key_shape"
        private const val K_MOTION = "highlight_motion"
        private const val K_HIGHLIGHT = "highlight_style"
        private const val K_TONE = "tone"
        private const val K_FONT = "font"
        private const val K_WEIGHT = "letter_weight"
        private const val K_SIZE = "size_level"
        private const val K_LAYOUT = "layout"
        private const val K_LANGUAGE = "language"
        private const val K_LANGUAGE2 = "language2"
        private const val K_LANGUAGE3 = "language3"
        private const val K_ACTIVE_LANGUAGE = "active_language"
        private const val K_LANGUAGE_LABEL = "language_label"
        private const val K_SMART_KEYS = "smart_keys"
        private const val K_WORDS = "word_suggestions"
        private const val K_HOLD_ACCENTS = "hold_accents"
        private const val K_NUMBER_ROW = "number_row"
        private const val K_PICTURE = "device_picture"
        private const val K_VERTICAL_STICKS = "vertical_sticks"
        private const val K_SWAP_FACE = "swap_face_buttons"
        private const val K_TEST_FULL = "test_full_screen"
        private const val K_LAB_BUILDUP = "lab_build_up"
        private const val K_LAB_BUZZ = "lab_buzz_strength"
        private const val K_LAB_BUZZ_STYLE = "lab_buzz_style"
        private const val K_SECRET_NEW = "secret_new"
        private const val K_SECRET_ROW = "secret_row_new"
        private const val K_LAB_REWARD = "lab_reward"
        private const val K_RETRO = "retro_unlocked"
        private const val K_THEME_HL = "theme_highlight"
        private const val K_THEME_SOUND = "theme_sound"
        private const val K_LAST_INLAY = "last_inlay_tone"
        private const val K_SOUND_NEW = "secret_sound_new"
        private const val K_SOUND_SHOWN = "secret_sound_shown"
        private const val K_TV_MODE = "tv_mode"
        private const val K_TUTORIAL = "tutorial_done"
        private const val K_WHATS_NEW = "whats_new_seen"
        private const val NONE = "NONE"
        private const val K_SEE_THROUGH = "see_through"
        private const val K_STICK = "stick_speed"
        private const val K_RIGHT_STICK = "right_stick"
        private const val K_HINTS = "show_hints"
        private const val K_COMBO_HINTS = "combo_hints"
        private const val K_SELECT_ALL_BUTTON = "select_all_button"
        private const val K_DOUBLE_SPACE = "double_space_period"
        private const val K_AUTO_CAPS = "auto_caps"
        private const val K_LEARN = "learn_words"
        private const val K_TIDY = "tidy_punctuation"
        private const val K_UNDO = "undo_combo"
        private const val K_VIBRATION = "vibration"
        private const val K_MOVE = "move_strength_v7"     // v7: vibration scale changed
        private const val K_PRESS = "press_strength_v7"
        private const val K_VOLUME = "sound_volume_v7"    // v7: sounds remastered
        private const val K_MOVE_SOUNDS = "move_sounds"
        private const val K_SILENT = "sound_in_silent"
        /**
         * From 1.2 the silent-mode choice has a new name, so everyone gets the new starting value once:
         * on (sounds play in silent mode) on anything that isn't a phone, since a handheld or TV is often
         * "silent" with no phone to ring, and the keyboard sounded broken. Off on phones. Anyone who had
         * turned it on keeps it on.
         */
        private const val K_SILENT_2 = "sound_in_silent_2"
        private const val K_CLIP_HISTORY = "save_clip_history"
        private const val K_PREVIEW = "show_preview"

        fun load(context: Context): KeyboardSettings {
            val text = SmallFile.read(context, FILE)
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            val phone = isPhone(context)
            if (json != null) return from(Source.Json(json), phone)
            // First start after updating from 1.0: bring the old settings across once
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val first = from(Source.Prefs(prefs), phone)
            first.save(context)
            return first
        }

        /** Reads saved values from either place, falling back to the defaults. */
        private sealed class Source {
            abstract fun str(k: String): String?
            abstract fun int(k: String, d: Int): Int
            abstract fun bool(k: String, d: Boolean): Boolean

            class Json(val o: JSONObject) : Source() {
                override fun str(k: String) = if (o.has(k)) o.optString(k) else null
                override fun int(k: String, d: Int) = o.optInt(k, d)
                override fun bool(k: String, d: Boolean) = o.optBoolean(k, d)
            }

            class Prefs(val p: SharedPreferences) : Source() {
                override fun str(k: String) = p.getString(k, null)
                override fun int(k: String, d: Int) = p.getInt(k, d)
                override fun bool(k: String, d: Boolean) = p.getBoolean(k, d)
            }
        }

        /** A phone (it can make calls): silent mode there means "be quiet", so sounds respect it by default. */
        fun isPhone(context: Context): Boolean =
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)

        /** The custom colour sliders (hue, strength, brightness) that come closest to [colour]. */
        fun customFor(colour: Int): Triple<Int, Int, Int> {
            var best = Triple(0, 0, 0); var err = Double.MAX_VALUE
            val target = AccentLight.oklch(colour)
            for (h in 0 until 360) for (st in 0..100 step 2) for (b in 0..100 step 2) {
                val c = AccentLight.oklch(Palette.customAccent(h, st, b))
                val dh = Math.toRadians(c[2] - target[2])
                val e = (c[0] - target[0]).let { it * it } + (c[1] - target[1]).let { it * it } +
                    4 * c[1] * target[1] * Math.sin(dh / 2).let { it * it }
                if (e < err) { err = e; best = Triple(h, st, b) }
            }
            return best
        }

        private fun from(p: Source, phone: Boolean = true): KeyboardSettings {
            val d = KeyboardSettings()
            // Custom colours saved before 1.2 had only a hue (a soft pastel): turn that colour into the
            // three sliders, so it looks the same after the update
            val oldHue = p.int(K_HUE, d.customHue)
            val savedStrength = p.int(K_STRENGTH, -1)
            val (hue, strength, brightness) = if (savedStrength >= 0)
                Triple(oldHue, savedStrength, p.int(K_BRIGHTNESS, d.customBrightness))
            else {
                val (l, c, h) = AccentLight.oklch(Color.HSVToColor(floatArrayOf(oldHue.toFloat().mod(360f), 0.48f, 0.98f))).toList()
                val b = ((l - Palette.CUSTOM_L_MIN) / (Palette.CUSTOM_L_MAX - Palette.CUSTOM_L_MIN) * 100).roundToInt().coerceIn(0, 100)
                val lb = Palette.customLightness(b)
                val top = minOf(Palette.CUSTOM_C_MAX, AccentLight.maxChroma(lb, h)).coerceAtLeast(Palette.CUSTOM_C_MIN + 0.001)
                Triple(h.roundToInt() % 360, ((c - Palette.CUSTOM_C_MIN) / (top - Palette.CUSTOM_C_MIN) * 100).roundToInt().coerceIn(0, 100), b)
            }
            // The colour: the 1.2 choice if saved, otherwise the 1.1 one moved to its new place in the list
            val saved2 = p.int(K_ACCENT_2, Int.MIN_VALUE)
            val old = p.int(K_ACCENT, d.accentIndex)
            val accent = when {
                saved2 != Int.MIN_VALUE -> saved2
                old < 0 -> old
                else -> ACCENT_1_1.getOrElse(old) { d.accentIndex }
            }
            val (cHue, cStrength, cBright) = if (accent == -2) customFor(Color.parseColor(INDIGO)) else Triple(hue, strength, brightness)
            return KeyboardSettings(
                buttonStyle = enumOr(p.str(K_STYLE), d.buttonStyle),
                accentIndex = if (accent == -2) -1 else accent,
                customHue = cHue,
                customStrength = cStrength,
                customBrightness = cBright,
                toneIndex = p.int(K_TONE, d.toneIndex),
                font = enumOr(p.str(K_FONT), d.font),
                letterWeight = p.int(K_WEIGHT, d.letterWeight),
                sizeLevel = p.int(K_SIZE, d.sizeLevel).coerceIn(0, 2),     // an early 1.1 build had a 4th "TV" size
                tvMode = enumOr(p.str(K_TV_MODE), d.tvMode),
                layout = enumOr(p.str(K_LAYOUT), d.layout),
                language1 = enumOr(p.str(K_LANGUAGE), d.language1),
                language2 = p.str(K_LANGUAGE2)?.let { n -> Language.values().firstOrNull { it.name == n } },
                language3 = p.str(K_LANGUAGE3)?.let { n -> Language.values().firstOrNull { it.name == n } },
                activeLanguage = p.int(K_ACTIVE_LANGUAGE, d.activeLanguage),
                showLanguageLabel = p.bool(K_LANGUAGE_LABEL, d.showLanguageLabel),
                smartKeys = p.bool(K_SMART_KEYS, d.smartKeys),
                wordSuggestions = p.bool(K_WORDS, d.wordSuggestions),
                holdAccents = p.bool(K_HOLD_ACCENTS, d.holdAccents),
                numberRow = p.bool(K_NUMBER_ROW, d.numberRow),
                picture = enumOr(p.str(K_PICTURE), d.picture),
                verticalSticks = p.bool(K_VERTICAL_STICKS, d.verticalSticks),
                swapFaceButtons = p.bool(K_SWAP_FACE, d.swapFaceButtons),
                testFullScreen = p.bool(K_TEST_FULL, d.testFullScreen),
                labBuildUp = p.int(K_LAB_BUILDUP, d.labBuildUp),
                labBuzzStrength = p.int(K_LAB_BUZZ, d.labBuzzStrength),
                labBuzzStyle = p.int(K_LAB_BUZZ_STYLE, d.labBuzzStyle),
                secretNew = p.bool(K_SECRET_NEW, d.secretNew),
                secretRowNew = p.bool(K_SECRET_ROW, d.secretRowNew),
                labReward = p.int(K_LAB_REWARD, d.labReward),
                retroUnlocked = p.bool(K_RETRO, d.retroUnlocked), themeHighlight = p.bool(K_THEME_HL, d.themeHighlight),
                themeSound = p.bool(K_THEME_SOUND, d.themeSound), lastInlayTone = p.int(K_LAST_INLAY, d.lastInlayTone),
                secretSoundNew = p.bool(K_SOUND_NEW, d.secretSoundNew), secretSoundShown = p.bool(K_SOUND_SHOWN, d.secretSoundShown),
                tutorialDone = p.bool(K_TUTORIAL, d.tutorialDone),
                whatsNewSeen = p.str(K_WHATS_NEW) ?: d.whatsNewSeen,
                keyShape = enumOr(p.str(K_SHAPE), d.keyShape),
                motion = enumOr(p.str(K_MOTION), d.motion),
                highlightStyle = enumOr(p.str(K_HIGHLIGHT), d.highlightStyle),
                seeThrough = p.int(K_SEE_THROUGH, d.seeThrough),
                stickSpeed = p.int(K_STICK, d.stickSpeed),
                rightStick = p.bool(K_RIGHT_STICK, d.rightStick),
                showHints = p.bool(K_HINTS, d.showHints),
                comboHints = p.bool(K_COMBO_HINTS, d.comboHints),
                selectAllButton = p.bool(K_SELECT_ALL_BUTTON, d.selectAllButton),
                doubleSpacePeriod = p.bool(K_DOUBLE_SPACE, d.doubleSpacePeriod),
                autoCaps = p.bool(K_AUTO_CAPS, d.autoCaps),
                learnWords = p.bool(K_LEARN, d.learnWords),
                tidyPunctuation = p.bool(K_TIDY, d.tidyPunctuation),
                undoCombo = p.bool(K_UNDO, d.undoCombo),
                vibration = p.bool(K_VIBRATION, d.vibration),
                moveStrength = p.int(K_MOVE, d.moveStrength),
                pressStrength = p.int(K_PRESS, d.pressStrength),
                soundVolume = p.int(K_VOLUME, d.soundVolume),
                soundPack = SoundPack.fromSaved(p.str(K_PACK)) ?: d.soundPack,
                moveSounds = p.bool(K_MOVE_SOUNDS, d.moveSounds),
                soundInSilent = p.bool(K_SILENT_2, p.bool(K_SILENT, false) || !phone),
                saveClipHistory = p.bool(K_CLIP_HISTORY, d.saveClipHistory),
                showPreview = p.bool(K_PREVIEW, d.showPreview)
            )
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback
    }
}

/** Picks readable colours automatically, using the WCAG contrast formula. */
object Contrast {
    val DARK: Int = Design.INK_DARK

    fun ratio(a: Int, b: Int): Double {
        val la = Color.luminance(a) + 0.05
        val lb = Color.luminance(b) + 0.05
        return max(la, lb) / min(la, lb)
    }

    /** White or near-black, whichever reads better on this background. */
    fun inkFor(background: Int): Int =
        if (ratio(background, Color.WHITE) >= ratio(background, DARK)) Color.WHITE else DARK

    /** The colour you actually see when a see-through colour sits on a solid one. */
    fun over(foreground: Int, background: Int): Int {
        val a = Color.alpha(foreground) / 255f
        return Color.rgb(
            (Color.red(foreground) * a + Color.red(background) * (1 - a)).toInt(),
            (Color.green(foreground) * a + Color.green(background) * (1 - a)).toInt(),
            (Color.blue(foreground) * a + Color.blue(background) * (1 - a)).toInt()
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /**
     * The highlight colour as this theme needs it: the same hue, made just deep enough (or bright enough)
     * to stand out at least [min]:1 against every one of [surfaces] (the keys and the background).
     * Dark themes get the colour untouched; on Light it deepens, so the highlight never disappears.
     */
    /** The highlight colour for words in it on [background]: brightened (dark backgrounds) or deepened (light ones) only as far as 4.5:1 needs. */
    fun accentText(accent: Int, background: Int, min: Double = 4.5): Int = accentOn(accent, background, min = min)

    fun accentOn(accent: Int, vararg surfaces: Int, min: Double = 3.0): Int {
        val solid = surfaces.filter { Color.alpha(it) == 255 }
        if (solid.isEmpty() || solid.all { ratio(accent, it) >= min }) return accent
        val (l0, c, h) = AccentLight.oklch(accent).toList()
        val down = solid.all { Color.luminance(it) > 0.18f }        // light surfaces: go deeper; dark ones: brighter
        var l = l0
        repeat(80) {
            l = if (down) l - 0.01 else l + 0.01
            val col = AccentLight.color(l.coerceIn(0.0, 1.0), minOf(c, AccentLight.maxChroma(l.coerceIn(0.0, 1.0), h)), h)
            if (solid.all { ratio(col, it) >= min }) return col
        }
        return if (down) DARK else Color.WHITE
    }
}

/**
 * A tiny file in the app's private folder, written safely: a half-written file
 * (say, if the battery dies mid-save) is never left behind.
 */
object SmallFile {
    private fun file(context: Context, name: String) = File(context.filesDir, name)

    fun read(context: Context, name: String): String? = runCatching {
        file(context, name).takeIf { it.exists() }?.readText(Charsets.UTF_8)
    }.getOrNull()

    /** Writes a temporary file, then swaps it in with one rename, so a reader in the other
     *  process sees either the old file or the new one, never half of one. */
    fun write(context: Context, name: String, text: String) {
        runCatching {
            val target = file(context, name)
            val temp = File(context.filesDir, "$name.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(target)) { target.delete(); temp.renameTo(target) }
        }
    }
}
