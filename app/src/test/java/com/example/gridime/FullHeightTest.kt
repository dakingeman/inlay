package com.example.gridime

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Android's band under keyboards, as it's named (FullHeight recognises it by name). */
class NavigationBarFrame(context: android.content.Context) : FrameLayout(context)

/**
 * "Use the full screen height" (1.3, idea from Odin KB):
 *  - on by default, kept when saved;
 *  - the band is hidden and its room taken back only when the band is there; with no band (three-button navigation,
 *    other Android versions) nothing is touched, so nothing can end up under a real system bar;
 *  - the settings key: a tap still opens Settings, a hold opens the keyboard picker (the switcher the band had).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class FullHeightTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun onByDefaultAndKept() {
        assertTrue(KeyboardSettings().fullHeight)
        KeyboardSettings(fullHeight = false).save(context)
        assertFalse(KeyboardSettings.load(context).fullHeight)
        KeyboardSettings().save(context)
        assertTrue(KeyboardSettings.load(context).fullHeight)
    }

    private fun window(withBand: Boolean): Triple<FrameLayout, View, View?> {
        val decor = FrameLayout(context).apply { setPadding(0, 0, 0, 48) }
        val frame = FrameLayout(context).apply { setPadding(0, 0, 0, 30) }
        val keyboard = View(context)
        frame.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200).apply { bottomMargin = 12 })
        decor.addView(frame)
        val band = if (withBand) NavigationBarFrame(context).also { decor.addView(it) } else null
        return Triple(decor, keyboard, band)
    }

    @Test fun theBandGoesAndItsRoomWithIt() {
        val (decor, keyboard, band) = window(withBand = true)
        assertEquals("the band is found, and hidden", true, FullHeight.hideBand(decor))
        assertEquals("already hidden: nothing more to change", false, FullHeight.hideBand(decor))
        assertEquals(View.GONE, band!!.visibility)
        assertTrue(FullHeight.dropRoomBelow(keyboard, decor))
        assertFalse("done: nothing more", FullHeight.dropRoomBelow(keyboard, decor))
        assertEquals(0, (keyboard.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
        assertEquals(0, (keyboard.parent as View).paddingBottom)
        assertEquals(0, decor.paddingBottom)
        FullHeight.showBand(decor)
        assertEquals("turned off: the band comes back", View.VISIBLE, band.visibility)
    }

    @Test fun noBandNothingTouched() {
        val (decor, _, _) = window(withBand = false)
        assertEquals("no band: the service then leaves the room alone", null, FullHeight.hideBand(decor))
        assertEquals(48, decor.paddingBottom)
    }

    @Test fun settingsKeyTapStillOpensSettingsAndHoldOpensThePicker() {
        val screen = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val kb = KeyGridView(screen).apply { applySettings(KeyboardSettings()) }
        screen.setContentView(kb, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); idle(300)
        val tools = mutableListOf<Tool>(); val holds = mutableListOf<Tool>()
        kb.listener = object : KeyGridView.Listener {
            override fun onText(text: String) {}
            override fun onDelete() {}
            override fun onEnter() {}
            override fun onTool(tool: Tool) { tools += tool }
            override fun onToolHold(tool: Tool) { holds += tool }
            override fun onClip(index: Int) {}
        }
        val gear = kb.keyBoxesForCheck().first { it.second.tool == Tool.SETTINGS }.third
        fun touch(action: Int) = kb.onTouchEvent(android.view.MotionEvent.obtain(0, 0, action, gear.centerX(), gear.centerY(), 0))
        // a finger: a tap opens Settings, a hold the keyboard picker
        touch(android.view.MotionEvent.ACTION_DOWN); idle(100); touch(android.view.MotionEvent.ACTION_UP)
        assertEquals("a tap opens Settings", listOf(Tool.SETTINGS), tools)
        assertTrue(holds.isEmpty())
        touch(android.view.MotionEvent.ACTION_DOWN); idle(700); touch(android.view.MotionEvent.ACTION_UP)
        assertEquals("a hold opens the picker", listOf(Tool.SETTINGS), holds)
        assertEquals("and not Settings as well", 1, tools.size)
        // A on the settings key (the highlight is on it after the touches): the same
        kb.pressA(); idle(100); kb.releaseA()
        assertEquals("A tapped: Settings", 2, tools.size)
        kb.pressA(); idle(700); kb.releaseA()
        assertEquals("A held: the picker", 2, holds.size)
        assertEquals(2, tools.size)
    }
}
