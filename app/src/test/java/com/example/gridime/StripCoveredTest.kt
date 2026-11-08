package com.example.gridime

import android.os.Looper
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
 * Holding RT covers the top bar with the hint chips (Jump word, Capital, Undo). Found on the Odin, 5 Nov 2026: the
 * highlight could still go up there and glide about under the chips, and A would use a tool you couldn't see.
 * - on the top bar when RT goes down: the highlight drops to the row below
 * - while RT is held, up and down step over the top bar (both ways round)
 * - let go of RT and the top bar can be reached again
 * - with the hints turned off the top bar stays reachable (nothing covers it)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class StripCoveredTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun keyboard(s: KeyboardSettings = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION)): KeyGridView {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(host); v.applySettings(s); v.setField(FieldKind.TEXT)
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(300)
        return v
    }
    private fun KeyGridView.upToTheTopBar() { repeat(8) { if (focusRowForCheck() != 0) moveFocus(0, -1) }; idle(200) }

    @Test fun theHighlightLeavesTheTopBarWhenTheHintsCoverIt() {
        val v = keyboard()
        v.upToTheTopBar()
        assertEquals("on the top bar first", 0, v.focusRowForCheck())
        v.setHeldShift(true); idle(400)
        assertEquals("RT down: onto the row below", 1, v.focusRowForCheck())
    }

    @Test fun whileRtIsHeldUpAndDownStepOverTheTopBar() {
        val v = keyboard()
        v.setHeldShift(true); idle(300)
        val seen = mutableSetOf<Int>()
        repeat(12) { v.moveFocus(0, -1); seen += v.focusRowForCheck() }
        repeat(12) { v.moveFocus(0, 1); seen += v.focusRowForCheck() }
        assertTrue("never on the top bar while it's covered: $seen", 0 !in seen)
        assertTrue("but every other row is reached: $seen", seen.size >= 3)
        v.setHeldShift(false); idle(300)
        v.upToTheTopBar()
        assertEquals("RT let go: the top bar is back", 0, v.focusRowForCheck())
    }

    @Test fun withTheHintsOffTheTopBarStaysReachable() {
        val v = keyboard(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, comboHints = false))
        v.setHeldShift(true); idle(300)
        v.upToTheTopBar()
        assertEquals("nothing covers it, so it can be used", 0, v.focusRowForCheck())
    }
}
