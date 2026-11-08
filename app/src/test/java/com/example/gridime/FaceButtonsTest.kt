package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
 * "Swap A/B and X/Y" turns face buttons back into positions on handhelds that report them by printed letter
 * (a Retroid Pocket 6 in its retro style: every press landed on the opposite partner, in every button style;
 * the user's video, 1 Nov 2026). With it off, nothing changes for anyone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaceButtonsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val face = listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y)
    private val others = listOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR
    )

    /** Android's positions: A bottom, B right, X left, Y top. */
    private fun position(code: Int) = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> "bottom"; KeyEvent.KEYCODE_BUTTON_B -> "right"
        KeyEvent.KEYCODE_BUTTON_X -> "left"; KeyEvent.KEYCODE_BUTTON_Y -> "top"; else -> "other"
    }

    @Test fun offChangesNothing() {
        for (c in face + others) assertEquals(c, FaceButtons.code(c, swap = false))
    }

    @Test fun onSwapsOnlyTheFourFaceButtons() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, FaceButtons.code(KeyEvent.KEYCODE_BUTTON_A, true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, FaceButtons.code(KeyEvent.KEYCODE_BUTTON_B, true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, FaceButtons.code(KeyEvent.KEYCODE_BUTTON_X, true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, FaceButtons.code(KeyEvent.KEYCODE_BUTTON_Y, true))
        for (c in others) assertEquals("only face buttons move", c, FaceButtons.code(c, swap = true))
    }

    /** The user's test: left, up, right, down on a device that sends the printed letters Y, X, A, B. */
    @Test fun aNintendoLayoutDeviceLandsOnTheRightPositions() {
        val sent = listOf(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)
        assertEquals("the bug, as in the video", listOf("top", "left", "bottom", "right"), sent.map { position(FaceButtons.code(it, false)) })
        assertEquals("fixed", listOf("left", "top", "right", "bottom"), sent.map { position(FaceButtons.code(it, true)) })
    }

    /** A standard device (positions already) stays right with the setting off, in the same test. */
    @Test fun aStandardDeviceStaysRight() {
        val sent = listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(listOf("left", "top", "right", "bottom"), sent.map { position(FaceButtons.code(it, false)) })
    }

    @Test fun theEventKeepsEverythingButItsButton() {
        val e = KeyEvent(100L, 200L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 3, 0, 7, 42, 0, android.view.InputDevice.SOURCE_GAMEPAD)
        ButtonRoles.apply(KeyboardSettings(swapFaceButtons = true))     // a handheld that sends them swapped
        val m = FaceButtons.event(e)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, m.keyCode)
        assertEquals(e.action, m.action); assertEquals(e.repeatCount, m.repeatCount)
        assertEquals(e.downTime, m.downTime); assertEquals(e.eventTime, m.eventTime)
        assertEquals(e.deviceId, m.deviceId); assertEquals(e.scanCode, m.scanCode); assertEquals(e.source, m.source)
        val dpad = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        assertSame("not a face button: the very same event", dpad, FaceButtons.event(dpad))
        ButtonRoles.apply(KeyboardSettings())
        assertSame("the usual setup: the very same event", e, FaceButtons.event(e))
    }

    @Test fun theSettingIsSavedAndOffByDefault() {
        assertFalse(KeyboardSettings().swapFaceButtons)
        KeyboardSettings(swapFaceButtons = true).save(context)
        assertTrue(KeyboardSettings.load(context).swapFaceButtons)
        KeyboardSettings(swapFaceButtons = false).save(context)
        assertFalse(KeyboardSettings.load(context).swapFaceButtons)
    }

    /** In Settings, B (the right button) closes. On a swapped device the right button sends A. */
    @Test fun settingsClosesOnTheRightButtonEitherWay() {
        fun closesWith(swap: Boolean, sent: Int): Boolean {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false, swapFaceButtons = swap).save(context)
            val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            val t = SystemClock.uptimeMillis()
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, sent, 0))
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, sent, 0))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
            return a.isFinishing
        }
        assertTrue("standard device: B closes", closesWith(swap = false, sent = KeyEvent.KEYCODE_BUTTON_B))
        assertTrue("swapped device: its right button sends A, and closes", closesWith(swap = true, sent = KeyEvent.KEYCODE_BUTTON_A))
        assertFalse("swapped device: its bottom button sends B, and doesn't close", closesWith(swap = true, sent = KeyEvent.KEYCODE_BUTTON_B))
    }
}
