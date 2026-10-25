package com.example.gridime

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

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
 * so switching never changes what a sound tells you. New packs go at the end.
 */
enum class SoundPack(val label: String, val description: String) {
    SOFT("Soft", "A gentle thud with a crisp click. The default"),
    SOFT_DEEP("Soft Deep", "Like Soft, darker and weightier"),
    TACTILE("Tactile", "Dry mechanical clicks, no notes"),
    CHIME("Chime", "Soft mallet notes"),
    RETRO("Retro", "Short 8-bit blips, like a handheld's menus"),
    TYPEWRITER("Typewriter", "A crisp metal strike, with a small bell on Enter"),
    FELT("Felt", "Very soft taps for playing at night")
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
enum class ControllerButton { A, B, X, Y, L1, R1, L2, R2, SELECT, START, L3 }

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
    val clear: Boolean = false
)

object Palette {
    val accents: List<Pair<String, Int>> = listOf(
        "Sky" to "#8AB4F8",
        "Ocean" to "#4FA8FF",
        "Inlay" to "#72D6E8",      // the brand teal, same as the app icon (was Cyan #4DD9E8)
        "Teal" to "#3FD1B0",
        "Mint" to "#7EE6A8",
        "Lime" to "#B5E853",
        "Lemon" to "#F5E46A",
        "Amber" to "#FFC857",
        "Tangerine" to "#FF9F5A",
        "Coral" to "#FF8A80",
        "Rose" to "#F48FB1",
        "Orchid" to "#E27AE8",
        "Lavender" to "#B39DFF",
        "Indigo" to "#8C9EFF",
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

    /** A custom accent from a hue (0..359), kept as bright and soft as the preset colours. */
    fun customAccent(hue: Int): Int = Color.HSVToColor(floatArrayOf(hue.toFloat().mod(360f), 0.48f, 0.98f))
}

/** Everything the Settings app can change. Saved on the device, read by the keyboard. */
data class KeyboardSettings(
    // Look
    val buttonStyle: ButtonStyle = ButtonStyle.XBOX,
    val accentIndex: Int = 2,           // -1 = custom colour from customHue. 2 = Inlay teal, the brand colour
    val customHue: Int = 210,           // 0 to 359
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
    val showHints: Boolean = true,      // badges on keys
    val comboHints: Boolean = true,     // chips while holding RT
    val selectAllButton: Boolean = false,
    val doubleSpacePeriod: Boolean = true,
    val undoCombo: Boolean = true,
    val smartKeys: Boolean = true,      // .com, / and @ where they help, a number pad for numbers
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
    val tutorialDone: Boolean = false
) {
    /** The languages in the quick switch, in order, without repeats. */
    val languages: List<Language> get() = listOfNotNull(language1, language2, language3).distinct()

    /** The language being typed right now. */
    val language: Language get() = languages[activeLanguage.coerceIn(0, languages.lastIndex)]

    /** The next language in the quick switch (wraps round). */
    fun nextLanguage(): KeyboardSettings =
        copy(activeLanguage = (languages.indexOf(language) + 1) % languages.size)

    val accent: Int
        get() = if (accentIndex < 0) Palette.customAccent(customHue)
        else Palette.accents[accentIndex.coerceIn(0, Palette.accents.lastIndex)].second
    val tone: Tone get() = Palette.tones[toneIndex.coerceIn(0, Palette.tones.lastIndex)]

    /**
     * Saved as a small file instead of Android's usual settings store. Settings runs in
     * its own process (so closing it can never take the keyboard down), and a file read
     * fresh each time is the reliable way for both processes to see the same values.
     */
    fun save(context: Context) {
        val json = JSONObject()
            .put(K_STYLE, buttonStyle.name)
            .put(K_ACCENT, accentIndex)
            .put(K_HUE, customHue)
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
            .put(K_TV_MODE, tvMode.name)
            .put(K_TUTORIAL, tutorialDone)
            .put(K_SHAPE, keyShape.name)
            .put(K_MOTION, motion.name)
            .put(K_HIGHLIGHT, highlightStyle.name)
            .put(K_SEE_THROUGH, seeThrough)
            .put(K_STICK, stickSpeed)
            .put(K_HINTS, showHints)
            .put(K_COMBO_HINTS, comboHints)
            .put(K_SELECT_ALL_BUTTON, selectAllButton)
            .put(K_DOUBLE_SPACE, doubleSpacePeriod)
            .put(K_UNDO, undoCombo)
            .put(K_VIBRATION, vibration)
            .put(K_MOVE, moveStrength)
            .put(K_PRESS, pressStrength)
            .put(K_VOLUME, soundVolume)
            .put(K_PACK, soundPack.name)
            .put(K_MOVE_SOUNDS, moveSounds)
            .put(K_SILENT, soundInSilent)
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
        private const val K_HUE = "custom_hue"
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
        private const val K_TV_MODE = "tv_mode"
        private const val K_TUTORIAL = "tutorial_done"
        private const val NONE = "NONE"
        private const val K_SEE_THROUGH = "see_through"
        private const val K_STICK = "stick_speed"
        private const val K_HINTS = "show_hints"
        private const val K_COMBO_HINTS = "combo_hints"
        private const val K_SELECT_ALL_BUTTON = "select_all_button"
        private const val K_DOUBLE_SPACE = "double_space_period"
        private const val K_UNDO = "undo_combo"
        private const val K_VIBRATION = "vibration"
        private const val K_MOVE = "move_strength_v7"     // v7: vibration scale changed
        private const val K_PRESS = "press_strength_v7"
        private const val K_VOLUME = "sound_volume_v7"    // v7: sounds remastered
        private const val K_MOVE_SOUNDS = "move_sounds"
        private const val K_SILENT = "sound_in_silent"
        private const val K_CLIP_HISTORY = "save_clip_history"
        private const val K_PREVIEW = "show_preview"

        fun load(context: Context): KeyboardSettings {
            val text = SmallFile.read(context, FILE)
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json != null) return from(Source.Json(json))
            // First start after updating from 1.0: bring the old settings across once
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val first = from(Source.Prefs(prefs))
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

        private fun from(p: Source): KeyboardSettings {
            val d = KeyboardSettings()
            return KeyboardSettings(
                buttonStyle = enumOr(p.str(K_STYLE), d.buttonStyle),
                accentIndex = p.int(K_ACCENT, d.accentIndex),
                customHue = p.int(K_HUE, d.customHue),
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
                tutorialDone = p.bool(K_TUTORIAL, d.tutorialDone),
                keyShape = enumOr(p.str(K_SHAPE), d.keyShape),
                motion = enumOr(p.str(K_MOTION), d.motion),
                highlightStyle = enumOr(p.str(K_HIGHLIGHT), d.highlightStyle),
                seeThrough = p.int(K_SEE_THROUGH, d.seeThrough),
                stickSpeed = p.int(K_STICK, d.stickSpeed),
                showHints = p.bool(K_HINTS, d.showHints),
                comboHints = p.bool(K_COMBO_HINTS, d.comboHints),
                selectAllButton = p.bool(K_SELECT_ALL_BUTTON, d.selectAllButton),
                doubleSpacePeriod = p.bool(K_DOUBLE_SPACE, d.doubleSpacePeriod),
                undoCombo = p.bool(K_UNDO, d.undoCombo),
                vibration = p.bool(K_VIBRATION, d.vibration),
                moveStrength = p.int(K_MOVE, d.moveStrength),
                pressStrength = p.int(K_PRESS, d.pressStrength),
                soundVolume = p.int(K_VOLUME, d.soundVolume),
                soundPack = enumOr(p.str(K_PACK), d.soundPack),
                moveSounds = p.bool(K_MOVE_SOUNDS, d.moveSounds),
                soundInSilent = p.bool(K_SILENT, d.soundInSilent),
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
    val DARK: Int = Color.parseColor("#0B0F17")

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

    fun delete(context: Context, name: String) {
        file(context, name).delete()
    }
}
