package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
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
 * The tour's "Your turn" (1.3). On a page with a try:
 * - the demo plays once, then it's your turn by itself: the card asks, the picture lights the button
 * - another button doesn't count, and the card says so; the right one does it and moves on
 * - tap Start skips this step, hold Start skips the tour, B goes back to the demo
 * - a hold (accents) only counts once the accents are up
 * - the typing button moved right (ButtonRoles): that button is the one that counts
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class TourYourTurnTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Before fun on() { TutorialActivity.yourTurn = true }
    @After fun off() { TutorialActivity.yourTurn = false; ButtonRoles.setForCheck(false) }

    private fun open(s: KeyboardSettings = KeyboardSettings(whatsNewSeen = WhatsNew.VERSION)): TutorialActivity {
        s.save(context)
        return Robolectric.buildActivity(TutorialActivity::class.java).setup().get().also { idle(1500) }
    }
    private fun TutorialActivity.down(k: Int) { val t = SystemClock.uptimeMillis(); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)) }
    private fun TutorialActivity.up(k: Int) { val t = SystemClock.uptimeMillis(); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)) }
    private fun TutorialActivity.press(k: Int, wait: Long = 200) { down(k); up(k); idle(wait) }
    private fun TutorialActivity.pageNow() = TutorialActivity::class.java.getDeclaredField("page").apply { isAccessible = true }.getInt(this)
    private fun TutorialActivity.card() = (TutorialActivity::class.java.getDeclaredField("body").apply { isAccessible = true }.get(this) as TextView).text.toString()
    private fun TutorialActivity.titleOf(i: Int) = pageCheck()[i].first

    @Test fun startSkipsAStepHoldingItSkipsTheTour() {
        val a = open()
        a.press(KeyEvent.KEYCODE_BUTTON_START, 600)
        assertEquals("a tap on Start skips this step (the welcome page)", 1, a.pageNow())
        a.down(KeyEvent.KEYCODE_BUTTON_START); idle(900); a.up(KeyEvent.KEYCODE_BUTTON_START); idle(400)
        assertTrue("holding Start skips the tour, even in a typing turn", a.isFinishing)
        assertTrue("and it counts as seen", KeyboardSettings.load(context).tutorialDone)
    }

}
