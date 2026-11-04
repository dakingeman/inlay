package com.example.gridime

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

/**
 * The tour and What's new are designed on the Odin 2 Portal's screen (827 × 412 dp, landscape).
 * On a screen with more room the whole page grows as one unit: words, step card, device picture, keyboard,
 * margins and gaps together, so they stay in the same balance as on the Odin (a 1080p handheld at a small
 * display size showed tiny words in big, empty boxes; Reddit video, 3 Nov 2026).
 *
 * How: the activity runs at a higher density, as if the screen were "Odin-sized" in dp. Android then draws
 * everything larger itself, sharp, with the same layout rules. The user's font size (fontScale) is left alone,
 * so it still applies on top.
 *
 * The scale never goes below 1 (a smaller screen keeps the sizes it was designed and checked at) and never
 * above [MAX], so a tablet or TV doesn't turn the page into a poster.
 */
object TourScale {
    /** The Odin's height in landscape: every landscape page is drawn as if the screen were about this tall. */
    const val REF_H_DP = 412f
    /** The narrowest the page is ever laid out at: the side-by-side layout needs 760 dp, the stacked one 480 dp. */
    val SPLIT_W_DP = TutorialActivity.SPLIT_MIN_DP.toFloat()
    const val STACK_W_DP = 480f
    /** The side-by-side layout is kept while the canvas stays within this much of the Odin's height. */
    const val SPLIT_TALL = 1.1f
    const val MIN = 1f
    const val MAX = 1.5f
    /** Below this the page is left exactly as it is (no point redrawing for a 2 % change). */
    const val SNAP = 1.03f

    /**
     * The scale for a landscape screen [wDp] × [hDp] dp: as close to the Odin's height as the width allows.
     *  1. Side by side (words beside the device and keyboard, like the Odin) when that canvas stays Odin-shaped.
     *  2. Otherwise stacked (words above), the layout small and 4:3 screens use, at the Odin's height.
     * Upright screens (phones) and TVs keep the sizes they were designed at (a TV has its own layout, safe margins
     * and viewing distance, checked at 960 × 540 dp).
     */
    fun of(wDp: Int, hDp: Int, tv: Boolean = false): Float {
        if (tv || wDp < hDp) return 1f
        val split = minOf(hDp / REF_H_DP, wDp / SPLIT_W_DP)
        val s = if (wDp / split >= SPLIT_W_DP - 0.5f && hDp / split <= REF_H_DP * SPLIT_TALL) split
            else minOf(hDp / REF_H_DP, wDp / STACK_W_DP)
        val c = s.coerceIn(MIN, MAX)
        return if (c < SNAP) 1f else c
    }
    /** The scale for the screen [c] describes. */
    fun of(c: Configuration): Float = of(c.screenWidthDp, c.screenHeightDp,
        (c.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION)

    /** [base] with the tour's scale applied (the same context when no scaling is needed). */
    fun wrap(base: Context): Context {
        val c = base.resources.configuration
        if (c.screenWidthDp <= 0 || c.screenHeightDp <= 0) return base
        val s = of(c)
        if (s == 1f) return base
        val o = Configuration(c)
        o.densityDpi = (c.densityDpi * s).roundToInt()
        val k = c.densityDpi.toFloat() / o.densityDpi
        o.screenWidthDp = (c.screenWidthDp * k).toInt()
        o.screenHeightDp = (c.screenHeightDp * k).toInt()
        o.smallestScreenWidthDp = (c.smallestScreenWidthDp * k).toInt()
        return base.createConfigurationContext(o)
    }
}
