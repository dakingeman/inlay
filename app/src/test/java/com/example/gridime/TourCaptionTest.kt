package com.example.gridime

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.fail
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
 * The caption (small and narrow screens: one row at a time): within two runs of each demo,
 * every button row of every page has had its turn, on every device. A new page whose demo never gives a
 * row its moment, and is never quiet long enough for it, fails here with the row's name.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourCaptionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun check() {
        val problems = mutableListOf<String>()
        val devices = listOf(PictureChoice.HANDHELD to false, PictureChoice.VERTICAL to false, PictureChoice.VERTICAL to true,
            PictureChoice.CONTROLLER to false, PictureChoice.REMOTE to false)
        for ((pic, sticks) in devices) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, picture = pic, verticalSticks = sticks).save(context)
            for (whatsNew in listOf(false, true)) {
                val t = Robolectric.buildActivity(TutorialActivity::class.java,
                    Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
                val pages = t.pageCheck()
                pages.forEachIndexed { i, (title, rows) ->
                    // two runs of the demo (or 20 s for a demo without runs)
                    var waited = 0L
                    do { idle(1000); waited += 1000 } while (waited < 60_000 && (t.loopRuns.size < 3 || waited < 4000) && !(t.loopRuns.isEmpty() && waited >= 20_000))
                    val missing = (0 until t.rowCountForCheck) - t.rowsShownForCheck()
                    missing.forEach { problems += "$pic${if (sticks) "+sticks" else ""} ${if (whatsNew) "What's new" else "tour"} page ${i + 1} \"$title\": the row \"${rows[it]}\" never had its turn" }
                    if (i < pages.size - 1) { val n = SystemClock.uptimeMillis(); t.dispatchKeyEvent(KeyEvent(n, n, 0, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(n, n, 1, KeyEvent.KEYCODE_BUTTON_A, 0)); idle(100) }
                }
                t.finish()
            }
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun smallScreenCaption() = check()
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThreeCaption() = check()
}
