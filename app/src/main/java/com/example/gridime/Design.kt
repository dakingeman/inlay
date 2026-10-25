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
    const val STROKE_ICON = 1.8f       // every line icon on the keyboard
    const val STROKE_FOCUS = 2f        // the white focus ring in Settings
    const val STROKE_HAIRLINE = 1f     // card edges and dividers

    // ---- Surfaces (Settings). On OLED, depth comes from light, not shadow. ----
    val SURFACE_BG: Int = Color.BLACK
    val SURFACE_CARD: Int = Color.parseColor("#111114")
    val SURFACE_RAISED: Int = Color.parseColor("#1C1C21")          // focused row
    val EDGE_CARD: Int = Color.parseColor("#14FFFFFF")             // 8 % white hairline
    val EDGE_DIVIDER: Int = Color.parseColor("#0FFFFFFF")          // 6 % white hairline
    val TEXT_PRIMARY: Int = Color.WHITE
    val TEXT_SECONDARY: Int = Color.parseColor("#9A9DA5")          // 7:1 on the card colour

    // ---- Type (sp in Settings, dp on the keyboard) ---------------------------
    const val TYPE_DISPLAY = 28f    // one big line per screen (the tour)
    const val TYPE_TITLE = 22f
    const val TYPE_ROW = 16f
    const val TYPE_CAPTION = 13f
    const val TYPE_SECTION = 12f
    const val TYPE_KEY = 19f
    const val TYPE_KEY_LABEL = 13f
    const val TYPE_CHIP = 10.5f

    // ---- Motion (ms). Fast enough to never make you wait. ------------------
    const val MOTION_FOCUS = 120L      // highlight glide between keys
    const val MOTION_FOCUS_MIN = 55L   // while a direction is held and repeating
    const val MOTION_PRESS = 170L      // key squeeze and release
    const val MOTION_FADE = 140L       // hint chips appearing
    const val MOTION_CONTROL = 200L    // switches, option pickers, tabs
    const val MOTION_PAGE = 200L       // settings page change
    const val MOTION_PANEL = 260L      // keyboard preview sliding in and out

    /** Emphasized decelerate: a quick start that settles softly (Material 3). */
    val EASE_OUT: Interpolator get() = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    /** Standard: for things that move on screen and stay (panels, pages). */
    val EASE_STANDARD: Interpolator get() = PathInterpolator(0.2f, 0f, 0f, 1f)

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

    // ---- Haptics -------------------------------------------------------------
    // Small vibration motors need a short full-power "kick" to spin up, or a
    // light pulse is never felt. Every pulse starts with this kick.
    const val HAPTIC_KICK_MS = 8L
}
