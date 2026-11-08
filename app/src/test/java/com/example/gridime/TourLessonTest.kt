package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * The tour's guided turns (1.3, Emre 6 Nov): learn the controller by typing. The rules, from the user's side:
 * - done when the text is right, by the button or by the key on the keyboard (the key earns a tip)
 * - a wrong letter really types, and the card says how to fix it; too far off, the turn starts again
 * - the same useless button again and again gets a gentle word; help only after a long quiet, then a way to skip
 * - B goes back a page, holding Start skips the tour; each turn notes whether it went cleanly
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class TourLessonTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @Before fun on() { TutorialActivity.yourTurn = true }
    @After fun off() { TutorialActivity.yourTurn = false }

    private fun open(): TutorialActivity {
        KeyboardSettings(whatsNewSeen = WhatsNew.VERSION).save(context)
        return Robolectric.buildActivity(TutorialActivity::class.java).setup().get().also { idle(1500); it.press(KeyEvent.KEYCODE_BUTTON_A, 700) }
    }
    private fun TutorialActivity.press(k: Int, wait: Long = 250) { val t = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)); idle(wait) }
    private fun TutorialActivity.to(label: String) { while (stepDemoForCheck(label) == false) Unit }
    /** The card's answer line. */
    private fun TutorialActivity.note(): String {
        val rows = TutorialActivity::class.java.getDeclaredField("rows").apply { isAccessible = true }.get(this) as LinearLayout
        fun all(v: android.view.View): List<TextView> = if (v is TextView) listOf(v) else if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
        return all(rows).filter { it.visibility == android.view.View.VISIBLE }.joinToString(" | ") { it.text.toString() }
    }
    private fun TutorialActivity.pageNow() = TutorialActivity::class.java.getDeclaredField("page").apply { isAccessible = true }.getInt(this)

    @Test fun theCapitalZWithASlipPutRight() {
        val a = open()
        assertEquals("the first turn starts by itself", "Add the capital Z", a.lessonForCheck)
        assertEquals("elda", a.fieldForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_R2)
        a.to("x"); a.press(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("a wrong letter really types", "Xelda", a.fieldForCheck)
        assertTrue("and the card says how to fix it: ${a.note()}", "X deletes it" in a.note())
        a.press(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("elda", a.fieldForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_R2); a.to("z"); a.press(KeyEvent.KEYCODE_BUTTON_A, 100)
        assertEquals("Zelda", a.fieldForCheck)
        idle(1000)
        assertEquals("then the next turn", "Delete the extra d", a.lessonForCheck)
        assertEquals("with the slip noted", "Add the capital Z" to false, a.learnt.first())
    }

    @Test fun aSmallLetterIsCaught() {
        val a = open()
        a.to("z"); a.press(KeyEvent.KEYCODE_BUTTON_A)
        assertTrue("zelda isn't Zelda: ${a.note()}", "Small z" in a.note())
    }

    @Test fun theKeyOnTheKeyboardCountsTooAndEarnsATip() {
        val a = open()
        a.press(KeyEvent.KEYCODE_BUTTON_R2); a.to("z"); a.press(KeyEvent.KEYCODE_BUTTON_A, 1000)
        assertEquals("Delete the extra d", a.lessonForCheck)
        a.to("⌫"); a.press(KeyEvent.KEYCODE_BUTTON_A, 200)
        assertEquals("Metroid", a.fieldForCheck)
        assertTrue("a tip about X: ${a.note()}", "X does this from anywhere" in a.note())
        idle(2200)
        assertEquals("Add the space", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_Y, 1000)
        assertEquals("Search for it", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_START, 1000)
        assertEquals("all four done: the next page's turns start", "Add the é", a.lessonForCheck)
        assertEquals("all clean", listOf(true, true, true, true), a.learnt.map { it.second })
    }

    @Test fun theSameUselessButtonGetsAGentleWord() {
        val a = open()
        repeat(3) { a.press(KeyEvent.KEYCODE_BUTTON_L1) }
        assertTrue("spamming: ${a.note()}", "does nothing here" in a.note())
    }

    @Test fun tooFarOffStartsTheTurnAgain() {
        val a = open()
        repeat(3) { a.press(KeyEvent.KEYCODE_BUTTON_Y) }
        idle(1600)
        assertEquals("back to the start of the turn", "elda", a.fieldForCheck)
        assertEquals("Add the capital Z", a.lessonForCheck)
    }

    @Test fun helpOnlyAfterALongQuietThenAWayToSkip() {
        val a = open()
        idle(8000)
        assertTrue("no help yet: ${a.note()}", "ringed key" !in a.note())
        idle(5000)
        assertTrue("help after a long quiet: ${a.note()}", "ringed key" in a.note())
        idle(19000)
        assertTrue("then the way to skip: ${a.note()}", "Tap Start to skip" in a.note())
        a.press(KeyEvent.KEYCODE_BUTTON_START, 600)
        assertEquals("skipped, and noted", "Delete the extra d" to false, a.lessonForCheck to a.learnt.first().second)
    }

    @Test fun theDpadMovesTheHighlight() {
        val a = open()
        val demo = TutorialActivity::class.java.getDeclaredField("demo").apply { isAccessible = true }.get(a) as KeyGridView
        val before = demo.focusColForCheck()
        a.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue("the D-pad moves on the keyboard", demo.focusColForCheck() != before)
        // a hat D-pad sends motion: the lesson must not swallow it (Android turns it into D-pad presses)
        val e = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_MOVE, 0f, 0f, 0)
        assertEquals("motion it doesn't use isn't taken", false, a.onGenericMotionEvent(e))
    }

    private fun TutorialActivity.down(k: Int) { val t = SystemClock.uptimeMillis(); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)) }
    private fun TutorialActivity.up(k: Int) { val t = SystemClock.uptimeMillis(); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)) }
    private fun TutorialActivity.toFaster() { skipLessonsForCheck(); idle(900); beginTurnForCheck(); idle(600) }

    @Test fun theAccentStaysOnTheEAndKeepsTheRestOfTheWord() {
        val a = open(); a.toFaster()
        assertEquals("Add the é", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue("locked on the e: ${a.note()}", "Just the e" in a.note())
        a.down(KeyEvent.KEYCODE_BUTTON_A); idle(80); a.up(KeyEvent.KEYCODE_BUTTON_A); idle(900)
        assertEquals("too short a hold: put back", "Pokmon", a.fieldForCheck)
        a.down(KeyEvent.KEYCODE_BUTTON_A); idle(1300)
        a.up(KeyEvent.KEYCODE_BUTTON_A); idle(200)                         // let go on é: kept
        assertEquals("the accent goes in, and the rest of the word stays (Piñata, 6 Nov)", "Pokémon", a.fieldForCheck)
        idle(900)
        assertEquals("Take the suggestion", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_THUMBR, 900)
        assertEquals("Add the 5", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_L2); a.to("5"); a.press(KeyEvent.KEYCODE_BUTTON_A, 900)
        assertEquals("the group done: the next one starts", "Fix the typo", a.lessonForCheck)
    }

    @Test fun fixingSelectCopyClearUndoClose() {
        val a = open(); a.toFaster(); a.skipLessonsForCheck(); idle(900); a.beginTurnForCheck(); idle(600)
        assertEquals("Fix the typo", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_L1); a.to("i"); a.press(KeyEvent.KEYCODE_BUTTON_A, 900)
        assertEquals("Copy the 3", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_SELECT, 700); a.press(KeyEvent.KEYCODE_BUTTON_L1)
        assertEquals("3", a.selectedForCheck)
        a.lessonJumpForCheck(2); idle(400)
        a.press(KeyEvent.KEYCODE_BUTTON_SELECT, 100); a.press(KeyEvent.KEYCODE_BUTTON_SELECT, 200)
        assertEquals("View twice: everything", "Dark Souls 3", a.selectedForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_X, 900)
        assertEquals("Bring it back", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_X)
        assertTrue("X alone doesn't undo: ${a.note()}", "first, then X" in a.note() && a.fieldForCheck == "Hollow ")
        a.down(KeyEvent.KEYCODE_BUTTON_R2); idle(300); a.press(KeyEvent.KEYCODE_BUTTON_X); a.up(KeyEvent.KEYCODE_BUTTON_R2); idle(200)
        assertEquals("Hollow Knight", a.fieldForCheck)
        idle(900)
        assertEquals("Close the keyboard", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_B, 300)
        assertEquals("B closes here", true, a.learnt.last().first == "Close the keyboard")
    }

    @Test fun bGoesBackATurnThenAPage() {
        val a = open()
        a.press(KeyEvent.KEYCODE_BUTTON_R2); a.to("z"); a.press(KeyEvent.KEYCODE_BUTTON_A, 1000)
        assertEquals("Delete the extra d", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_B, 400)
        assertEquals("B: the turn before", "Add the capital Z", a.lessonForCheck)
    }

    @Test fun viewTwiceButTooSlowGetsAHint() {
        val a = open(); a.toFaster(); a.skipLessonsForCheck(); idle(900); a.beginTurnForCheck(); idle(600)
        a.lessonJumpForCheck(2); idle(400)
        a.press(KeyEvent.KEYCODE_BUTTON_SELECT, 700); a.press(KeyEvent.KEYCODE_BUTTON_SELECT, 200)
        assertTrue("twice, too slowly: ${a.note()}", "a little quicker" in a.note() && a.selectedForCheck.isEmpty())
        a.press(KeyEvent.KEYCODE_BUTTON_SELECT, 100); a.press(KeyEvent.KEYCODE_BUTTON_SELECT, 200)
        assertEquals("then quickly: everything", "Dark Souls 3", a.selectedForCheck)
    }

    @Test fun theLastPageOffersASecondGoAtWhatTookAFewTries() {
        val a = open()
        a.to("x"); a.press(KeyEvent.KEYCODE_BUTTON_A); a.press(KeyEvent.KEYCODE_BUTTON_X)      // a slip, put right
        a.press(KeyEvent.KEYCODE_BUTTON_R2); a.to("z"); a.press(KeyEvent.KEYCODE_BUTTON_A, 900)
        repeat(3) { a.skipLessonsForCheck(); idle(900); a.beginTurnForCheck(); idle(600) }
        a.press(KeyEvent.KEYCODE_BUTTON_A, 900)                                                    // Make it yours: on
        val title = { (TutorialActivity::class.java.getDeclaredField("title").apply { isAccessible = true }.get(a) as TextView).text.toString() }
        val body = { (TutorialActivity::class.java.getDeclaredField("body").apply { isAccessible = true }.get(a) as TextView).text.toString() }
        assertTrue("the summary names it: ${body()}", title() == "Nicely done" && "add the capital Z" in body())
        a.press(KeyEvent.KEYCODE_BUTTON_Y, 600)
        assertEquals("Y: that turn again", "Add the capital Z", a.lessonForCheck)
        a.press(KeyEvent.KEYCODE_BUTTON_R2); a.to("z"); a.press(KeyEvent.KEYCODE_BUTTON_A, 1200)
        assertEquals("then back to the summary, all clean now", "You're all set", title())
    }

    @Test fun bGoesBackAPage() {
        val a = open()
        a.press(KeyEvent.KEYCODE_BUTTON_B, 600)
        assertEquals(0 to null, a.pageNow() to a.lessonForCheck)
    }
}
