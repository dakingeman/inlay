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
 * WHAT YOU SEE IS WHAT HAPPENS, on every page of the tour and What's new (Odin test, 30 Oct 2026: "really" was on show
 * while "republic" was typed). Every page plays for [PLAY_MS] on every device and button style, and the keyboard reports
 * ([KeyGridView.truthCheck]) anything the screen doesn't show:
 *  - a key pressed, or an accent kept, while the highlight on screen is somewhere else
 *  - a word taken that isn't on show, or R3 taking a word that isn't the first one
 *  - a word taken less than [KeyGridView]'s WORD_SEEN_MS after it showed
 * A new What's new page is checked by this the moment it's added: nothing to write for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class DemoTruthTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun play(pic: PictureChoice, style: ButtonStyle, sticks: Boolean, whatsNew: Boolean, problems: MutableSet<String>) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = pic, verticalSticks = sticks).save(context)
        val ctl = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup()
        val t = ctl.get()
        val pages = t.pageCheck()
        var page = 0
        t.watchTruth { problems += "$pic${if (sticks) "+sticks" else ""}, $style, ${if (whatsNew) "What's new" else "tour"} \"${pages[page].first}\": $it" }
        val frame = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888))
        for (p in pages.indices) {
            page = p
            // Played in frames, as on a screen: the keyboard is drawn every [FRAME_MS]
            var played = 0L
            while (played < PLAY_MS) { idle(FRAME_MS); played += FRAME_MS; t.drawDemoForCheck(frame) }
            if (p < pages.lastIndex) { val n = SystemClock.uptimeMillis(); t.dispatchKeyEvent(KeyEvent(n, n, 0, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(n, n, 1, KeyEvent.KEYCODE_BUTTON_A, 0)) }
        }
        ctl.pause().stop().destroy()
    }

    private fun check(cases: List<Triple<PictureChoice, ButtonStyle, Boolean>>) {
        val problems = linkedSetOf<String>()
        for ((pic, style, sticks) in cases) for (whatsNew in listOf(false, true)) play(pic, style, sticks, whatsNew, problems)
        problems.forEach { println("PROBLEM $it") }
        assertTrue("${problems.size} problems:\n" + problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test fun handheldEveryStyle() = check(ButtonStyle.values().map { Triple(PictureChoice.HANDHELD, it, false) })
    @Test fun otherDevices() = check(listOf(Triple(PictureChoice.VERTICAL, ButtonStyle.SWITCH, false), Triple(PictureChoice.VERTICAL, ButtonStyle.XBOX, true),
        Triple(PictureChoice.CONTROLLER, ButtonStyle.PLAYSTATION, false), Triple(PictureChoice.REMOTE, ButtonStyle.XBOX, false)))

    companion object { const val PLAY_MS = 24_000L; const val FRAME_MS = 50L }
}
