package com.example.gridime

import android.graphics.Color
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Inlay design tokens: the single source of truth for how the app looks,
 * moves, sounds and feels. Every screen reads from here instead of inventing numbers.
 * See DESIGN_GUIDE.md for the reasoning behind each value.
 */
object Design {

    // ---- Spacing: everything sits on a 4 dp grid ---------------------------
    const val SPACE_XS = 4f
    const val SPACE_S = 8f
    const val SPACE_M = 12f
    const val SPACE_L = 16f
    const val SPACE_XL = 24f
    const val SPACE_XXL = 32f

    // ---- Corners: nested shapes share a centre -----------------------------
    // Inner radius = outer radius - the gap between them, so curves stay parallel.
    const val RADIUS_CARD = 22f
    const val CARD_INSET = 4f
    const val RADIUS_ROW = RADIUS_CARD - CARD_INSET      // 18: rows sit inside cards
    const val RADIUS_PILL = 999f

    // ---- Strokes -------------------------------------------------------------
    const val STROKE_ICON = 1.8f       // every line icon in the app (keyboard, Settings, tour)
    const val STROKE_BADGE = 1.3f      // button badges' glyphs, times the badge's size
    const val STROKE_FOCUS = 2f        // the one focus ring: Settings rows and tabs, in the highlight colour
    const val STROKE_HAIRLINE = 1f     // card edges, dividers, every hairline

    // ---- Icons (Icons.kt): one family, one line --------------------------------
    const val ICON_CHEVRON = 4.5f      // half height of every chevron (rows that open, option pickers)
    const val ICON_CHECK_WEIGHT = 1.2f // a tick on a filled circle reads a touch heavier than a plain line icon

    // ---- Button badges: three sizes, one spacing (dp, times the badge size) ----
    const val BADGE_INLINE = 1.25f     // inside a sentence: subtitles, the control bar, "Undo"
    const val BADGE_ROW = 1.45f        // hint lists: the Button guide, tabs, the tour's rows
    const val BADGE_COMPACT = 1.1f     // the tour on short screens
    const val BADGE_GAP = 3f           // between two badges in one group
    const val BADGE_TEXT_GAP = 6f      // between a badge and its words (or a "+")

    // ---- Surfaces (Settings). On OLED, depth comes from light, not shadow. ----
    val SURFACE_BG: Int = Color.BLACK
    val SURFACE_CARD: Int = Color.parseColor("#111114")
    val SURFACE_RAISED: Int = Color.parseColor("#1C1C21")          // focused row
    val EDGE_CARD: Int = Color.parseColor("#14FFFFFF")             // 8 % white hairline
    val EDGE_DIVIDER: Int = Color.parseColor("#0FFFFFFF")          // 6 % white hairline
    val TEXT_PRIMARY: Int = Color.WHITE
    val TEXT_SECONDARY: Int = Color.parseColor("#9A9DA5")          // 6.95:1 on the card, 6.25:1 on a focused row
    val TRACK: Int = Color.parseColor("#2E2E35")                   // control tracks, dots, the tour window's edge
    val SWITCH_OFF_TRACK: Int = Color.parseColor("#4A4B55")      // visible against the card; the light knob says "off"
    val DOT_IDLE: Int = Color.parseColor("#76797F")                // progress dots not yet reached: 3:1 on the brightest point of the tour's backdrop (TourContrastTest)
    val SWITCH_OFF_KNOB: Int = Color.parseColor("#C8C8CE")
    /** The one dark ink: text and marks on the highlight colour and on light surfaces. */
    val INK_DARK: Int = Color.parseColor("#0B0F17")
    /** The one light ink for drawn letters (device labels, Switch's face letters): the brand's "paper". */
    val INK_LIGHT: Int = Color.parseColor("#F2F3F5")
    /** Inlay teal: the brand colour, the app icon's colour and the default highlight. */
    val BRAND_ACCENT: Int = Color.parseColor("#72D6E8")

    // ---- The tour and What's new backdrop (Backdrop.kt) --------------------------
    val BACKDROP_TOP: Int = Color.parseColor("#16171C")      // graphite, never flat black
    val BACKDROP_MID: Int = Color.parseColor("#101115")
    val BACKDROP_BOTTOM: Int = Color.parseColor("#0B0B0E")   // "Ink", the brand's darkest
    const val BACKDROP_VIGNETTE = 0x3D                        // 24 % at the corners
    const val BACKDROP_GRAIN = 6                              // fine grain against banding: white at up to 6/255 per pixel

    // ---- Button badges (BadgePainter.kt) ---------------------------------------
    val BADGE_PLATE: Int = Color.parseColor("#F20A0A0D")     // the dark plate every badge sits on
    val BADGE_EDGE: Int = Color.parseColor("#4DFFFFFF")      // its 30 % white edge
    val BADGE_INK: Int = Color.WHITE                         // glyphs and words on the plate
    const val BADGE_DPAD_REST = 0x52                          // the D-pad badge's quiet cross (32 %); the pressed arm is solid
    /** Face button colours, as the controllers print them (badges and device pictures alike). */
    val FACE_XBOX = mapOf("A" to Color.parseColor("#6CC24A"), "B" to Color.parseColor("#F0503C"),
        "X" to Color.parseColor("#3E9BFF"), "Y" to Color.parseColor("#F5C518"))
    val FACE_PLAYSTATION = mapOf("A" to Color.parseColor("#8FB4FF"), "B" to Color.parseColor("#FF6B6B"),
        "X" to Color.parseColor("#F59BD5"), "Y" to Color.parseColor("#4DD6B5"))   // cross, circle, square, triangle

    // ---- Keyboard veils (alpha, 0-255) -------------------------------------------
    const val SCRIM_ACCENTS = 0xA6        // the keyboard dims behind the accent tray (65 %)
    const val SCRIM_ACCENTS_CLEAR = 0x8C  //   on the see-through theme, with black (55 %)
    const val TRAY_CLEAR = 0xE6           // the accent tray itself on the see-through theme (90 %)
    const val PANEL_TV = 0xF2             // the floating TV panel over the app or game (95 %)
    const val SHADOW_CLEAR = 0x99         // letters' soft shadow on the see-through theme (60 %)
    const val ALPHA_INACTIVE = 90         // a control that isn't being used: the colour slider's resting ring
    // The see-through theme: 0 = a dark veil behind the keys, 10 = fully clear. Keys keep a dark tint at every level.
    const val CLEAR_VEIL = 0.6f           // the veil's strength at 0
    const val CLEAR_KEY = 0.88f           // letter keys' darkness at 0 ...
    const val CLEAR_SPECIAL = 0.96f       // ... and the special keys'
    const val CLEAR_KEY_FADE = 0.30f      // how much lighter both get at 10: letters stay 5:1 even on a white app behind
    const val CLEAR_FOCUS_EDGE = 0xCC     // the highlight's dark edge on the see-through theme (80 %)

    // ---- The keyboard highlight's rings (design units) --------------------------
    const val RING_GLOW = 2.5f            // Glow: the soft ring on the key's edge
    const val RING_GLOW_TV = 3f           //   in TV mode, read from across the room
    const val RING_GAP = 1.5f             //   the hairline of key between the ring and the fill
    const val RING_OUTLINE = 3f           // Outline: the crisp ring
    const val ALPHA_GLOW_TV = 0x8C        // the TV glow ring's strength (55 %)

    // ---- Faint and tinted (alpha, 0-255). Three faint steps, three accent strengths. ----
    const val ALPHA_FAINT_LINE = 0x0F  //  6 %: dividers
    const val ALPHA_FAINT = 0x14       //  8 %: edges, quiet fills
    const val ALPHA_FAINT_STRONG = 0x1F // 12 %: lifted fills, strong dividers
    const val ALPHA_TINT_TAG = 0x26    // 15 %: "this is yours" (tags: tint, never a ring)
    const val ALPHA_TINT_SOFT = 0x40   // 25 %: shift once, the active tool
    const val ALPHA_TINT_GLOW = 0x59   // 35 %: glow, lift, text selection
    const val ALPHA_LIFT_MAX = 0xB3    // 70 %: the most the Lift highlight's tint may take to reach 3:1
    const val ALPHA_POINTED = 0xB3     // a device button pointed at, not pressed: its ring alone at 70 %
    const val ALPHA_STICK_RIM = 115    // the clicked-in stick's inner rim, dark ink on your colour (45 %)
    const val ALPHA_INK_SOFT = 170     // quiet labels and icons (all measured above 4.5:1)
    const val ALPHA_INK_STRONG = 210   // chip text, digits

    // ---- Type (sp in Settings and the tour, design units on the keyboard) -------
    // One scale: 12 · 14 · 16 · 20 · 24 · 28. Nothing anywhere is ever smaller than TYPE_MIN
    // (Android TV's minimum), so text never shrinks below it to fit; layouts make room instead.
    const val TYPE_MIN = 12f
    const val TYPE_SECTION = 12f    // section labels, eyebrows (capitals, tracked)
    const val TYPE_CAPTION = 14f    // subtitles, captions, tabs, option pickers
    const val TYPE_ROW = 16f        // row titles, values, body text
    const val TYPE_TITLE = 20f      // titles on short screens
    const val TYPE_HEADER = 24f     // titles on wide screens, the row chevron
    const val TYPE_DISPLAY = 28f    // one big line per screen (the tour)
    // Keyboard (design units, times the keyboard's size): its own small scale
    const val TYPE_KEY = 19f        // letters
    const val TYPE_KEY_DIGIT = 22f  // the number pad
    const val TYPE_KEY_ACCENT = 16f // accent tray letters, number row digits
    const val TYPE_KEY_WORD = 14f   // word suggestions
    const val TYPE_KEY_LABEL = 13f  // ?123, space bar, tools, clipboard
    const val TYPE_CHIP = 12f       // hint chips

    // ---- Motion (ms). Fast enough to never make you wait. ------------------
    const val MOTION_FOCUS = 70L       // highlight glide between keys (only the last 25 %, see below)
    const val MOTION_FOCUS_MIN = 35L   // while a direction is held and repeating
    const val MOTION_FOCUS_JUMP = 0.75f // share of the way the highlight jumps on the very first frame
    const val MOTION_PRESS = 110L      // key squeeze and release
    const val MOTION_FADE = 140L       // hint chips appearing
    /** Landscape screens at least this wide put things side by side (small 4:3 handhelds are about 480). */
    const val WIDE_MIN_DP = 440
    const val MOTION_CONTROL = 200L    // switches, option pickers, tabs
    const val MOTION_PAGE = 200L       // settings page change
    const val MOTION_PANEL = 260L      // keyboard preview sliding in and out
    const val MOTION_REVEAL_WAIT = 150L // the accent tray waits this long (a tap never opens it), then
    const val MOTION_REVEAL = 200L     //   rises in this long
    const val MOTION_POP = 180L        // a tag popping in
    const val MOTION_FLASH = 260L      // a device button lighting for a moment
    const val MOTION_SHOWCASE_OUT = 160L // the welcome showcase: old device leaves (EASE_IN)
    const val MOTION_SHOWCASE_IN = 340L  //   new one arrives (EASE_OUT)
    const val MOTION_MORPH = 420L      // the vertical handheld changing into its stick version (under 500: no drag)
    const val MOTION_HOLD_WAIT = 120L  // a held button: a tap is over before the hold mark starts
    const val MOTION_HOLD = 380L       //   then it sweeps round in this long (EASE_STANDARD)
    const val MOTION_LOOP_RESET = 260L // the tour's loop bar: the full bar fades as the next run begins
    const val MOTION_BLINK = 530L      // the demo text box's cursor: on, off (Android's own blink rate)
    const val MOTION_BLINK_HOLD = 400L //   and it stays lit this long after every change, like a real one
    const val MOTION_STICK_SWAY = 1800L // a device picture's stick showing its direction: one full left-right

    /** Emphasized decelerate: a quick start that settles softly (Material 3). */
    val EASE_OUT: Interpolator get() = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    /** Emphasized accelerate: for things leaving the screen (Material 3). */
    val EASE_IN: Interpolator get() = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    /** Standard: for things that move on screen and stay (panels, pages, a switch's thumb, a tab's fill). */
    val EASE_STANDARD: Interpolator get() = PathInterpolator(0.2f, 0f, 0f, 1f)
    /**
     * The one rule for fades and slides: arriving eases out (fast, then settles), leaving eases in (gathers, then
     * goes), moving around eases in and out (EASE_STANDARD), looping is linear. The keyboard's highlight is the one
     * exception: it jumps most of the way at once and only the end glides, so typing never waits for it.
     */
    fun ease(arriving: Boolean): Interpolator = if (arriving) EASE_OUT else EASE_IN

    // ---- Type details ------------------------------------------------------------
    /** The interface's font (Settings, the tour, What's new). The keyboard has its own choice in Settings. */
    val UI_FONT = R.font.atkinson_next
    const val WEIGHT_TEXT = 400
    const val WEIGHT_STRONG = 600
    /** Big titles sit a little tighter (about -1.5 %): large bold letters look loose at their default spacing. */
    const val TRACKING_TITLE = -0.015f
    /** Small grey text that may run to two lines: room between the lines to read it. */
    const val LINE_CAPTION = 1.3f
    /** Numbers that change in place (a slider's value, "2 OF 7") get even-width digits, so they don't wobble. */
    const val NUMBERS_TABULAR = "tnum"

    // ---- Sound -----------------------------------------------------------------
    // Loudness lives in the sound files: every pack is mastered to one hierarchy,
    // measured as felt loudness (small-speaker weighting). The app plays them 1:1,
    // so a sound is exactly as loud in Settings as on the keyboard.
    // Frequent = lighter; triggers and lock carry a little more weight.
    const val SOUND_MOVE = 1f
    const val SOUND_KEY = 1f
    const val SOUND_MODIFIER = 1f
    const val SOUND_CONFIRM = 1f
    const val SOUND_GAIN_JITTER = 0.05f   // ±5 % loudness per play; pitch never moves
    const val SOUND_STEP_DB = 2.0         // each volume step; the default 7 is 6 dB under the top

    // ---- Haptics -------------------------------------------------------------
    // Small vibration motors need a short full-power "kick" to spin up, or a
    // light pulse is never felt. Every pulse starts with this kick.
    const val HAPTIC_KICK_MS = 8L
}
