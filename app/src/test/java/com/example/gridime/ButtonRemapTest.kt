package com.example.gridime

import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * Button mapping's rules (ButtonRemap.kt):
 * - nothing moved reads, saves and behaves exactly as before
 * - giving a button a function swaps it with the button that had it, so every function stays on exactly one button
 * - the twelve keyboard buttons can move; the D-pad never does
 * - the keyboard's badges show the button a function is on now
 * - saved and read back (the earlier shoulders-and-triggers text too); odd saved text reads as nothing moved
 * - in Settings: A on a function, then a button, maps it (and that button press does nothing else there)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class ButtonRemapTest {
    private val A = KeyEvent.KEYCODE_BUTTON_A; private val B = KeyEvent.KEYCODE_BUTTON_B
    private val X = KeyEvent.KEYCODE_BUTTON_X; private val Y = KeyEvent.KEYCODE_BUTTON_Y
    private val L1 = KeyEvent.KEYCODE_BUTTON_L1; private val R1 = KeyEvent.KEYCODE_BUTTON_R1
    private val L2 = KeyEvent.KEYCODE_BUTTON_L2; private val R2 = KeyEvent.KEYCODE_BUTTON_R2
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun nothingMovedChangesNothing() {
        val r = ButtonRemap()
        assertTrue(r.isIdentity)
        assertEquals("", r.save())
        for (code in ButtonRemap.BUTTONS + listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK)) {
            assertEquals(code, r.role(code)); assertEquals(code, r.buttonFor(code))
        }
        for (b in ControllerButton.values()) assertEquals(b, r.shownButton(b))
    }

    @Test fun backspaceOnL1SwapsWithX() {
        val r = ButtonRemap().with(L1, X)
        assertEquals("L1 now deletes", X, r.role(L1))
        assertEquals("and X takes L1's cursor left", L1, r.role(X))
        assertEquals(L1, r.buttonFor(X)); assertEquals(X, r.buttonFor(L1))
        assertEquals("the delete badge shows L1", ControllerButton.L1, r.shownButton(ControllerButton.X))
        assertEquals(ControllerButton.X, r.shownButton(ControllerButton.L1))
        for (other in listOf(A, B, Y, R1, L2, R2)) assertEquals(other, r.role(other))
    }

    @Test fun triggersSwapForTheRetroidCase() {
        val r = ButtonRemap().with(L2, R2)
        assertEquals("the left trigger does shift", R2, r.role(L2))
        assertEquals("the right trigger holds the symbols", L2, r.role(R2))
    }

    @Test fun everyFunctionStaysOnExactlyOneButton() {
        var r = ButtonRemap()
        for (b in ButtonRemap.BUTTONS) for (j in ButtonRemap.BUTTONS.reversed()) {
            r = r.with(b, j)
            assertEquals("after $b -> $j", ButtonRemap.BUTTONS.toSet(), ButtonRemap.BUTTONS.map { r.role(it) }.toSet())
            assertEquals(j, r.role(b))
            for (job in ButtonRemap.BUTTONS) assertEquals(job, r.role(r.buttonFor(job)))
        }
    }

    @Test fun swappingBackIsNothingMoved() {
        val r = ButtonRemap().with(L1, X).with(X, X)
        assertTrue(r.isIdentity); assertEquals("", r.save())
    }

    @Test fun theDpadNeverMoves() {
        val up = KeyEvent.KEYCODE_DPAD_UP
        assertEquals(ButtonRemap(), ButtonRemap().with(up, X))
        assertEquals(ButtonRemap(), ButtonRemap().with(X, up))
        assertEquals(up, ButtonRemap().with(L1, X).role(up))
    }

    @Test fun everyFunctionHasANameAndDetail() {
        assertEquals(ButtonRemap.BUTTONS.size, ButtonRemap.NAMES.size)
        assertEquals(ButtonRemap.BUTTONS.size, ButtonRemap.DETAILS.size)
        assertEquals(ButtonRemap.BUTTONS.size, ButtonRemap.BUTTONS.mapNotNull { ButtonRemap.BUTTON_OF[it] }.toSet().size)
        assertEquals("Backspace button", ButtonRemap.rowTitle(X))
    }

    @Test fun savedAndReadBack() {
        val r = ButtonRemap().with(L2, R2).with(L1, X).with(KeyEvent.KEYCODE_BUTTON_START, Y)
        assertEquals(r, ButtonRemap.parse(r.save()))
        assertEquals(r, ButtonRemap.cachedParse(r.save()))
        assertEquals(ButtonRemap(), ButtonRemap.cachedParse(""))
        assertEquals("the earlier shoulders-and-triggers text still reads", ButtonRemap().with(L2, R2), ButtonRemap.parse("104:105,105:104"))
    }

    @Test fun oddSavedTextReadsAsNothingMoved() {
        for (bad in listOf(null, "", " ", "x", "104:105", "102:103,102:104", "102:98,98:102", "1:2:3", "102:103,103:", "9999:102", "19:99,99:19"))
            assertTrue("\"$bad\"", ButtonRemap.parse(bad).isIdentity)
    }

    @Test fun survivesTheSettingsFile() {
        val r = ButtonRemap().with(L1, X)
        KeyboardSettings(buttonRemap = r.save()).save(context)
        assertEquals(r, KeyboardSettings.load(context).remap)
        KeyboardSettings().save(context)
        assertTrue(KeyboardSettings.load(context).remap.isIdentity)
    }

    @Test fun findKnowsTheMappingRows() {
        for (b in ButtonRemap.BUTTONS) assertTrue(ButtonRemap.rowTitle(b) in SettingsFind.WORDS)
        assertTrue(ButtonRemap.RESET_ROW in SettingsFind.WORDS)
    }

    // ---- In Settings: pick Backspace, press L1 ------------------------------------------------------------------

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun rows(v: View): List<ButtonMapRow> = when (v) {
        is ButtonMapRow -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { rows(v.getChildAt(it)) }
        else -> emptyList()
    }
    private fun press(a: SettingsActivity, code: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); idle(50)
        a.dispatchKeyEvent(KeyEvent(t, t + 50, KeyEvent.ACTION_UP, code, 0)); idle(50)
    }
    private fun openOnMappingRows(): Pair<SettingsActivity, () -> List<ButtonMapRow>> {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(600)
        val found = { rows(a.window.decorView) }
        var tries = 0
        while (found().isEmpty() && tries++ < 12) { press(a, R1); idle(400) }
        assertTrue("Settings has a page with the Button mapping rows", found().isNotEmpty())
        return a to found
    }

    @Test fun pickAFunctionThenPressItsButton() {
        val (a, found) = openOnMappingRows()
        assertEquals(ButtonRemap.BUTTONS.size, found().size)
        val backspace = found().first { it.job == X }
        backspace.performClick(); idle(50)
        press(a, L1)                                   // taken by the mapping: the page doesn't change
        idle(400)
        val saved = KeyboardSettings.load(context).remap
        assertEquals("L1 deletes", X, saved.role(L1))
        assertEquals("X moves the cursor left", L1, saved.role(X))
        assertNotNull("still on the page with the mapping rows", found().firstOrNull { it.job == X })
        a.finish(); idle(200)
    }

    @Test fun theDpadCancels() {
        val (a, found) = openOnMappingRows()
        found().first { it.job == Y }.performClick(); idle(50)
        press(a, KeyEvent.KEYCODE_DPAD_DOWN)
        press(a, L1)                                   // after cancelling, L1 is Settings' own again (next page)
        idle(400)
        assertTrue(KeyboardSettings.load(context).remap.isIdentity)
        a.finish(); idle(200)
    }
}
