package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.assertEquals
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
 * The left stick resizes the keyboard shown in Settings (Y), as it does in an app. Found on the Odin, 5 Nov 2026
 * with Split on; it never worked here, Split or not.
 * - keyboard up: Normal -> Large -> Largest -> Normal, saved, and the keyboard really grows
 * - the same with Split on
 * - keyboard down: nothing changes (you'd be resizing something you can't see)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class SettingsResizeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun open(split: Boolean, keyboardUp: Boolean): Pair<SettingsActivity, KeyGridView> {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, split = split).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        if (keyboardUp) { val t = SystemClock.uptimeMillis()                 // Y shows the keyboard, as on the Odin
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 0))
            a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 0)); idle(1200) }
        val preview = SettingsActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(a) as KeyGridView
        return a to preview
    }
    private fun SettingsActivity.leftStick() {
        val t = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_THUMBL, 0))
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBL, 0)); idle(600)
    }

    private fun cycles(split: Boolean) {
        val (a, preview) = open(split, keyboardUp = true)
        val heights = mutableListOf(preview.height)
        for (want in listOf(1, 2, 0)) {
            a.leftStick()
            assertEquals("split $split: the size moves on", want, preview.shownSizeLevel())
            assertEquals("split $split: and it's saved", want, KeyboardSettings.load(context).sizeLevel)
            heights += preview.height
        }
        assertEquals("split $split: the keyboard really grows, then comes back: $heights", true,
            heights[1] > heights[0] && heights[2] > heights[1] && heights[3] == heights[0])
    }

    @Test fun theLeftStickResizesTheKeyboardShownHere() = cycles(split = false)
    @Test fun withSplitOnToo() = cycles(split = true)

    @Test fun withTheKeyboardDownNothingChanges() {
        val (a, _) = open(split = true, keyboardUp = false)
        a.leftStick()
        assertEquals("nothing to see, nothing changes", 0, KeyboardSettings.load(context).sizeLevel)
    }
}
