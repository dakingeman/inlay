package com.example.gridime

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Flipping pages fast, forwards and back, never leaves the card half-faded or showing another page's step
 * (Odin test, 30 Oct 2026). While pages change, the card only ever shows a step of the page on show; after every burst,
 * once a page settles, it's fully shown and says the step the demo is on. (The test clock can't see a page's first
 * moments, so the card arriving with its page, never fading in a second time, is checked by eye on the device.)
 */
@RunWith(RobolectricTestRunner::class)
class TourFastFlipTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun press(a: android.app.Activity, key: Int) {
        val n = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_DOWN, key, 0)); a.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_UP, key, 0))
    }

    private fun run(screen: String) {
        val problems = mutableListOf<String>()
        for (whatsNew in listOf(false, true)) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD).save(context)
            val ctl = Robolectric.buildActivity(TutorialActivity::class.java,
                Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup()
            val t = ctl.get()
            val pages = t.pageCheck().size
            // Bursts: forward through every page, back, forward two and back one, with gaps shorter than any fade
            val bursts = listOf(List(pages - 1) { KeyEvent.KEYCODE_BUTTON_A }, List(pages - 2) { KeyEvent.KEYCODE_BUTTON_B },
                listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B), List(3) { KeyEvent.KEYCODE_BUTTON_A })
            for ((b, keys) in bursts.withIndex()) for (gap in listOf(15L, 45L, 90L)) {
                keys.forEach { key ->
                    press(t, key)
                    // While the pages change: the card always shows a step of the page on show
                    var waited = 0L
                    while (waited < gap) {
                        idle(5); waited += 5
                        val now = t.cardNowForCheck() ?: continue
                        if (now.first !in now.second) problems += "$screen, ${if (whatsNew) "What's new" else "tour"}: the card shows \"${now.first}\", a step from another page"
                    }
                }
                idle(1200)                                                // settled
                val where = "$screen, ${if (whatsNew) "What's new" else "tour"}, burst ${b + 1} at $gap ms"
                val c = t.cardForCheck() ?: continue                      // a page without steps
                if (!c.third) problems += "$where: the card isn't fully shown after the page settled"
                if (c.first != c.second) problems += "$where: the card says \"${c.first}\" while the demo shows \"${c.second}\""
            }
            ctl.pause().stop().destroy()
        }
        problems.distinct().forEach { println("PROBLEM $it") }
        assertTrue("${problems.distinct().size} problems:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("Odin 2 Portal")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocket() = run("small handheld")
}
