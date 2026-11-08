package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Your buttons (1.3): the button setup, connected.
 * - Settings: one row; A opens the setup; finishing saves it, and the keyboard, badges and picture follow at once
 * - holding Start leaves it, nothing changed; the old rows are gone
 * - old settings (Swap, Type with: Right) read across as the same setup, nobody's buttons change
 * - the tour asks first, on a controller, once; done there, it isn't asked again
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class YourButtonsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val A = KeyEvent.KEYCODE_BUTTON_A; private val B = KeyEvent.KEYCODE_BUTTON_B
    private val X = KeyEvent.KEYCODE_BUTTON_X; private val Y = KeyEvent.KEYCODE_BUTTON_Y
    @After fun reset() { ButtonRoles.setForCheck(false); TutorialActivity.askSetup = false }

    private fun android.app.Activity.press(k: Int, wait: Long = 150) {
        val t = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)); idle(wait)
    }
    private fun openSettings(s: KeyboardSettings = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION)): SettingsActivity {
        s.save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(600)
        a.tabsForCheck(a.findIndex().first { it.title == ButtonRoles.ROW }.page); idle(600)
        @Suppress("UNCHECKED_CAST")
        val rows = SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(a) as List<View>
        rows.first { (it as? SettingRow)?.rowTitle == ButtonRoles.ROW }.requestFocusFromTouch(); idle(200)
        return a
    }

    @Test fun settingsSetsItUpAndEverythingFollows() {
        val a = openSettings()
        assertTrue("the old rows are gone", a.findIndex().none { it.title in listOf("Type with", "Button check", "Button layout", "Typing button") || it.title.startsWith("Swap A/B") })
        a.press(A, 400)
        assertTrue("A opens the setup", a.setupForCheck != null)
        listOf(Y, X, A, B).forEach { a.press(it) }                      // a handheld that sends them swapped
        a.press(A); a.press(B)                                           // right (sends A) selects, bottom (sends B) closes
        a.press(A, 1200)                                                 // Select at the end: finished
        assertNull("it closes", a.setupForCheck)
        val saved = KeyboardSettings.load(context)
        assertTrue("saved", saved.faceSetUp)
        assertEquals("the right button selects on the keyboard", FaceSetup.SELECT, FaceButtons.code(A, deviceId = -1))
        assertEquals("the bottom one closes", FaceSetup.CLOSE, FaceButtons.code(B, deviceId = -1))
        assertEquals("{A} draws the right position", ControllerButton.B, ButtonRoles.position(ControllerButton.A))
    }

    @Test fun holdingStartLeavesNothingChanged() {
        val a = openSettings()
        a.press(A, 400); a.press(X); a.press(Y)
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START, 0)); idle(1000)
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_START, 0)); idle(600)
        assertNull("left", a.setupForCheck)
        assertTrue("nothing saved", !KeyboardSettings.load(context).faceSetUp)
    }

    @Test fun oldSettingsReadAcross() {
        val old = KeyboardSettings(swapFaceButtons = true, typeOnRight = true)
        assertEquals("the same buttons as before", FaceSetup.fromOld(true, true), old.face)
        assertTrue("not counted as set up", !old.faceSetUp)
        val done = old.withFace(FaceSetup(), "pad-1")
        assertTrue("after a setup, the old switches are cleared", !done.swapFaceButtons && !done.typeOnRight && done.faceSetUp)
        assertEquals("and the controller has its own", FaceSetup(), done.faceByController["pad-1"])
    }

    @Test fun theTourAsksFirstOnceOnly() {
        TutorialActivity.askSetup = true
        KeyboardSettings(whatsNewSeen = WhatsNew.VERSION).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(800)
        assertTrue("a new user on a controller: the setup first", t.setupForCheck != null)
        listOf(X, Y, B, A, A, B).forEach { t.press(it) }; t.press(A, 1200)
        assertNull("done, the tour goes on", t.setupForCheck)
        assertTrue("saved", KeyboardSettings.load(context).faceSetUp)
        val again = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(800)
        assertNull("not asked again", again.setupForCheck)
    }
}
