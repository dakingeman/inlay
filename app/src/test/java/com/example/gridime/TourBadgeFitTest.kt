package com.example.gridime

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
 * No button badge in the tour or What's new is cut off: every badge line gets at least the room it needs, margins
 * included. The tour sized its badge column by the badges alone, rounded down, and their right edges were cut off on
 * the Odin (30 Oct 2026). Every page, both guides, all three button styles, on a handheld, a vertical handheld, a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourBadgeFitTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun check(where: String) {
        val problems = linkedSetOf<String>(); var lines = 0
        for (style in ButtonStyle.values()) for (whatsNew in listOf(false, true)) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = PictureChoice.HANDHELD).save(context)
            val t = Robolectric.buildActivity(TutorialActivity::class.java,
                Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
            for (page in 0 until 8) {
                idle(700)
                for (v in all(t.window.decorView)) if (v is BadgeLineView && v.isShown && v.width > 0) {
                    lines++
                    if (!v.fits) problems += "$where, $style, ${if (whatsNew) "What's new" else "tour"} page ${page + 1}: a badge line is cut off"
                }
                val n = SystemClock.uptimeMillis()
                t.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0))
                t.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
                if (t.isFinishing) break
            }
        }
        println("tour badges, $where: $lines badge lines checked, ${problems.size} cut off")
        assertTrue(problems.joinToString("\n"), problems.isEmpty() && lines > 0)
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun handheld() = check("handheld")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun vertical() = check("vertical")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun phone() = check("phone")
}
