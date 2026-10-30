package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Every word on every Settings tab shows in full: no hint or label is cut at its row's edge (a hint with a button
 * badge in it was cut after "Up and down" on the Device tab; audit, 30 Oct 2026), and none is squeezed into a narrow
 * column of a word or two per line (the Button guide's "Jump a whole word with the stick" ran over seven lines on a
 * phone, beside its badges). Checked on a wide handheld, a vertical handheld and a phone-sized screen, in all three
 * button styles.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsTextFitTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun check(where: String) {
        val problems = mutableListOf<String>()
        for (style in ButtonStyle.values()) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, showPreview = false).save(context)
            val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            idle(600)
            for (tab in 0 until 6) {
                for (v in all(s.window.decorView)) {
                    if (v.width == 0 || !v.isShown) continue
                    when {
                        v is BadgeLineView && !v.fits -> problems += "$where, $style, tab $tab: a hint with badges is cut off"
                        v is TextView && v.layout != null && (0 until v.layout.lineCount).any { v.layout.getEllipsisCount(it) > 0 } ->
                            problems += "$where, $style, tab $tab: \"${v.text}\" is cut off"
                        v is TextView && v.layout != null && v.layout.height > v.height - v.totalPaddingTop - v.totalPaddingBottom + 1 ->
                            problems += "$where, $style, tab $tab: \"${v.text}\" doesn't fit its height"
                        // squeezed: three or more lines averaging under 14 letters (about two words) each
                        v is TextView && v.layout != null && v.layout.lineCount >= 3 && v.text.length / v.layout.lineCount < 14 ->
                            problems += "$where, $style, tab $tab: \"${v.text}\" is squeezed into ${v.layout.lineCount} short lines"
                    }
                }
                val t = SystemClock.uptimeMillis()
                s.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1, 0))
                s.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R1, 0))
                idle(500)
            }
        }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun handheld() = check("handheld")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun vertical() = check("vertical")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun phone() = check("phone")
}
