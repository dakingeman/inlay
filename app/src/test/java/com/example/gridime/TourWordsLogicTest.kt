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
 * The word demos follow the keyboard's own logic, so they never confuse (Odin test, 30 Oct 2026: "republic" was taken
 * without ever showing, because the suggestions ran a letter behind):
 *  - every suggestion on show starts with the word being typed
 *  - a word that's taken was on show, complete, for at least [LOOK_MS] first
 * Played for two full loops on every button style, on the tour's accents page and What's new's words page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class TourWordsLogicTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun play(style: ButtonStyle, whatsNew: Boolean, title: String, problems: MutableList<String>) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = PictureChoice.HANDHELD).save(context)
        val ctl = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup()
        val t = ctl.get()
        val at = t.pageCheck().indexOfFirst { it.first == title }
        if (at < 0) { problems += "$style: no page \"$title\""; ctl.pause().stop().destroy(); return }
        repeat(at) { idle(200); val n = SystemClock.uptimeMillis(); t.dispatchKeyEvent(KeyEvent(n, n, 0, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(n, n, 1, KeyEvent.KEYCODE_BUTTON_A, 0)) }
        val where = "$style, ${if (whatsNew) "What's new" else "tour"} \"$title\""
        var shownSince = mutableMapOf<String, Long>()
        var lastWord = t.typedForCheck.trimEnd().substringAfterLast(' ')
        var taken = 0
        var lastShown = emptyList<String>()
        var clock = 0L
        while (clock < 40_000) {
            idle(STEP_MS); clock += STEP_MS
            val typing = t.typedForCheck.substringAfterLast(' ')               // the word being typed ("" after a space)
            val word = t.typedForCheck.trimEnd().substringAfterLast(' ')       // the last word, typed or taken
            val shown = t.wordsForCheck
            if (shown.isNotEmpty() && typing.isNotEmpty()) shown.filterNot { it.startsWith(typing, ignoreCase = true) }.forEach {
                problems += "$where: \"$it\" shows while \"$typing\" is typed" }
            // A word taken whole: with suggestions on show, the typed start became a longer word in one go
            if (word != lastWord && lastShown.isNotEmpty() && lastWord.isNotEmpty() && word.startsWith(lastWord, ignoreCase = true) && word.length > lastWord.length + 1) {
                taken++
                val since = shownSince[word]
                if (since == null) problems += "$where: \"$word\" was taken without showing"
                else if (clock - STEP_MS - since < LOOK_MS) problems += "$where: \"$word\" showed only ${clock - STEP_MS - since} ms before it was taken"
            }
            shownSince = shown.associateWith { w -> shownSince[w] ?: clock }.toMutableMap()
            lastWord = word
            lastShown = shown
        }
        if (taken == 0) problems += "$where: no word was taken in 40 s"
        ctl.pause().stop().destroy()                 // closed for good: its demo never runs on beside the next one
    }

    @Test fun wordsFollowTheTyping() {
        val problems = mutableListOf<String>()
        for (style in listOf(ButtonStyle.XBOX, ButtonStyle.PLAYSTATION, ButtonStyle.SWITCH)) {
            play(style, false, "Accents and word suggestions", problems)
            play(style, true, "Word suggestions that learn", problems)
        }
        problems.distinct().forEach { println("PROBLEM $it") }
        assertTrue("${problems.distinct().size} problems:\n" + problems.distinct().take(30).joinToString("\n"), problems.isEmpty())
    }

    companion object {
        const val STEP_MS = 20L
        const val LOOK_MS = 500L
    }
}
