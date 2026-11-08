package com.example.gridime

import android.os.Build
import android.view.View
import android.view.ViewGroup

/**
 * "Use the full screen height" (1.3). With gesture navigation, Android 13 and newer draw a band of their own inside a
 * keyboard's window: a hide arrow and a keyboard switcher on an otherwise empty strip. With this on, Inlay hides that
 * band and takes its room back. B already hides the keyboard, and holding the settings key opens the keyboard picker.
 *
 * The idea comes from Odin KB (github.com/duhmojo/odin-kb); this is Inlay's own code. Android doesn't offer it
 * officially, so it is careful: it only touches the band it recognises, and only takes back room once that band was
 * found. Anywhere it finds nothing (three-button navigation, another Android version), nothing changes at all.
 */
object FullHeight {
    const val FROM_SDK = 33
    const val ROW = "Use the full screen height"
    /** The band's own view, by its class name (the only way to recognise it). */
    private const val BAND = "NavigationBarFrame"

    fun wanted(settings: KeyboardSettings) = Build.VERSION.SDK_INT >= FROM_SDK && settings.fullHeight

    /** Hides the system's band inside [decor]. Null when there is none; otherwise whether anything had to change. */
    fun hideBand(decor: View): Boolean? {
        var found = false; var changed = false
        walk(decor) { v -> if (v.javaClass.simpleName == BAND) { found = true; if (v.visibility != View.GONE) { v.visibility = View.GONE; changed = true } } }
        return if (found) changed else null
    }

    /** The setting turned off: the band comes back. */
    fun showBand(decor: View) = walk(decor) { v -> if (v.javaClass.simpleName == BAND && v.visibility == View.GONE) v.visibility = View.VISIBLE }

    /** The room kept for the band: no bottom padding or margin anywhere from the keyboard up to the window. True if any had to go. */
    fun dropRoomBelow(from: View, decor: View): Boolean {
        var v: View? = from; var changed = false
        while (v != null) {
            if (v.paddingBottom != 0) { v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0); changed = true }
            val lp = v.layoutParams
            if (lp is ViewGroup.MarginLayoutParams && lp.bottomMargin != 0) { lp.bottomMargin = 0; v.layoutParams = lp; changed = true }
            if (v === decor) break
            v = v.parent as? View
        }
        return changed
    }

    private fun walk(v: View, f: (View) -> Unit) {
        f(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }
}
