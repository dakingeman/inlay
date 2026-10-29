package com.example.gridime

/**
 * The "Wallpaper" theme takes its colours from the user's wallpaper (Android 12+), so they can't be listed.
 * Android picks them at fixed lightness (background tone 10, keys tone 30, special keys tone 20) with a
 * little of the wallpaper's colour; these stand in for every wallpaper: twelve hues, at the strongest
 * colour Android uses. The theme tests run on these as well as the fixed themes.
 */
object TestTones {
    private fun tone(l: Double, h: Double) = AccentLight.color(l, 0.04, h)
    val wallpapers: List<Tone> = (0 until 360 step 30).map { h ->
        Tone("Wallpaper (hue $h)", tone(0.224, h.toDouble()), tone(0.397, h.toDouble()), tone(0.31, h.toDouble()))
    }
    /** Every solid theme a user can end up with. */
    val solid: List<Tone> get() = Palette.tones.filter { !it.clear && it.name != "Wallpaper" } + wallpapers
}
