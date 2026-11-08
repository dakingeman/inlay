package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import org.junit.After
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
 * Typing button (1.3). With Swap A/B and X/Y on, the right button can type and select and the bottom one go back.
 * - every press lands on the right job, in all four combinations, and X and Y never move
 * - off by default, and it works on any controller (with Swap or without)
 * - {A} draws the button you really press (the right one), in every style
 * - the picture lights the right button for a typing press
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class ButtonRolesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @After fun reset() = ButtonRoles.setForCheck(false)

    @Test fun everyPressLandsOnTheRightJob() {
        val a = KeyEvent.KEYCODE_BUTTON_A; val b = KeyEvent.KEYCODE_BUTTON_B
        val x = KeyEvent.KEYCODE_BUTTON_X; val y = KeyEvent.KEYCODE_BUTTON_Y
        // (swap, typeOnRight) -> what A, B, X, Y as sent become
        val want = mapOf(
            (false to false) to listOf(a, b, x, y),
            (true to false) to listOf(b, a, y, x),      // positions again: the bottom types
            (true to true) to listOf(a, b, y, x),       // the right button (sent as A on a Nintendo layout) types
            (false to true) to listOf(b, a, x, y),      // an Xbox-style handheld, typing on the right: B (right) types
        )
        for ((k, out) in want) assertEquals("swap ${k.first}, type on right ${k.second}", out,
            listOf(a, b, x, y).map { FaceButtons.code(it, k.first, k.second) })
        assertEquals("other buttons never change", KeyEvent.KEYCODE_BUTTON_START, FaceButtons.code(KeyEvent.KEYCODE_BUTTON_START, true, true))
    }

    @Test fun offByDefaultAndForAnyController() {
        assertTrue("off by default", !KeyboardSettings().typeOnRight)
        ButtonRoles.apply(KeyboardSettings(typeOnRight = true, swapFaceButtons = false))
        assertTrue("works without Swap too (an Xbox-style handheld, typing on the right)", ButtonRoles.typeOnRight)
        ButtonRoles.apply(KeyboardSettings(typeOnRight = true, swapFaceButtons = true))
        assertTrue("and with it", ButtonRoles.typeOnRight)
        // Saved and read back
        val s = KeyboardSettings(tutorialDone = true, swapFaceButtons = true, typeOnRight = true)
        s.save(context)
        assertTrue("saved", KeyboardSettings.load(context).typeOnRight)
    }

    @Test fun theBadgeShowsTheButtonYouPress() {
        val dp = context.resources.displayMetrics.density
        fun pixels(button: ControllerButton, style: ButtonStyle): IntArray {
            val p = BadgePainter(dp).apply { this.style = style }
            val bm = Bitmap.createBitmap((40 * dp).toInt(), (30 * dp).toInt(), Bitmap.Config.ARGB_8888)
            p.draw(Canvas(bm), button, 4 * dp, 15 * dp)
            return IntArray(bm.width * bm.height).also { bm.getPixels(it, 0, bm.width, 0, 0, bm.width, bm.height) }
        }
        for (style in ButtonStyle.values()) {
            ButtonRoles.setForCheck(false)
            val right = pixels(ControllerButton.B, style)
            val bottom = pixels(ControllerButton.A, style)
            ButtonRoles.setForCheck(true)
            assertTrue("$style: {A} should draw the right button", pixels(ControllerButton.A, style).contentEquals(right))
            assertTrue("$style: {B} should draw the bottom button", pixels(ControllerButton.B, style).contentEquals(bottom))
        }
    }

    @Test fun thePictureLightsTheRightButton() {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val pic = DevicePictureView(host)
        pic.show(DeviceKind.WIDE, ButtonStyle.SWITCH)
        host.setContentView(pic)
        pic.measure(View.MeasureSpec.makeMeasureSpec((400 * context.resources.displayMetrics.density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((240 * context.resources.displayMetrics.density).toInt(), View.MeasureSpec.EXACTLY))
        pic.layout(0, 0, pic.measuredWidth, pic.measuredHeight)
        fun draw(): IntArray {
            val bm = Bitmap.createBitmap(pic.width, pic.height, Bitmap.Config.ARGB_8888); pic.draw(Canvas(bm))
            return IntArray(bm.width * bm.height).also { bm.getPixels(it, 0, bm.width, 0, 0, bm.width, bm.height) }
        }
        ButtonRoles.setForCheck(false); pic.held = setOf("B"); val rightLit = draw()
        ButtonRoles.setForCheck(true); pic.held = emptySet(); pic.held = setOf("A"); val typingLit = draw()
        assertTrue("a typing press should light the right button", typingLit.contentEquals(rightLit))
    }
}
