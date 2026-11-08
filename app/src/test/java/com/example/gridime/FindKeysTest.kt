package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.assertEquals
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
 * Find a setting, typing (found on the Odin, 6 Nov 2026):
 * - a held D-pad keeps moving, at the keyboard's pace (it moved once and stopped)
 * - holding A for accents keeps them open, and the one you pick replaces the letter (they closed at once)
 * - it always types in English (the settings are named in English); your languages are back after, unchanged
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class FindKeysTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun open(s: KeyboardSettings = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION)): Pair<SettingsActivity, KeyGridView> {
        s.save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(600)
        a.openFindForCheck(); idle(800)
        return a to (SettingsActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(a) as KeyGridView)
    }
    private fun SettingsActivity.down(k: Int) { val t = SystemClock.uptimeMillis(); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)) }
    private fun SettingsActivity.up(k: Int) { val t = SystemClock.uptimeMillis(); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)) }

    @Test fun aHeldDpadKeepsMoving() {
        val (a, kb) = open()
        repeat(40) { if (kb.stepTowards("q") == true) return@repeat }        // the start of a row, so it can't wrap
        val from = kb.focusColForCheck()
        a.down(KeyEvent.KEYCODE_DPAD_RIGHT); idle(600)                    // held: one now, then a move every 80 ms
        val moved = kb.focusColForCheck() - from
        a.up(KeyEvent.KEYCODE_DPAD_RIGHT); val stopped = kb.focusColForCheck(); idle(400)
        assertTrue("held, it keeps going: moved $moved", moved >= 3)
        assertEquals("let go, it stops", stopped, kb.focusColForCheck())
    }

    @Test fun accentsStayOpenAndThePickReplacesTheLetter() {
        val (a, kb) = open()
        repeat(40) { if (kb.stepTowards("e") == true) return@repeat }
        a.down(KeyEvent.KEYCODE_BUTTON_A); idle(900)                      // held until the accents rise
        assertTrue("the accents are open", kb.isAccentsOpen())
        idle(400)
        assertTrue("and they stay open while A is held", kb.isAccentsOpen())
        assertEquals("opening picks the first accent, as on the keyboard", "é", a.findQueryForCheck)
        a.down(KeyEvent.KEYCODE_DPAD_RIGHT); a.up(KeyEvent.KEYCODE_DPAD_RIGHT); idle(200)
        a.up(KeyEvent.KEYCODE_BUTTON_A); idle(300)
        assertTrue("the next one replaced it: ${a.findQueryForCheck}", a.findQueryForCheck.length == 1 && a.findQueryForCheck !in listOf("e", "é"))
    }

    @Test fun itTypesInEnglishAndYourLanguagesComeBack() {
        val (a, kb) = open(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION,
            language1 = Language.GERMAN, language2 = Language.FRENCH))
        assertEquals("English while finding", Language.ENGLISH, kb.languageForCheck())
        a.closeFindForCheck(); idle(400)
        assertEquals("your first language is back on the keyboard", Language.GERMAN, kb.languageForCheck())
        val saved = KeyboardSettings.load(context)
        assertEquals("and nothing about your languages was saved differently", listOf(Language.GERMAN, Language.FRENCH), saved.languages)
    }
}
