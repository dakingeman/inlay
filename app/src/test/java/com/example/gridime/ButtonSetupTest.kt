package com.example.gridime

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The button setup's rules (1.3), apart from any screen:
 * - left, top, right, bottom learn what each really sends; Select and Close are any two; Delete and Space the rest
 * - every controller: the usual, swapped (Nintendo layout), one pair swapped, numbered buttons
 * - mistakes never count: the same button twice, the D-pad, Close = Select
 * - nothing is kept until it finishes; Close at the end starts again
 * - saved and read back; odd saved text reads as nothing (the standard setup stays)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ButtonSetupTest {
    private val A = KeyEvent.KEYCODE_BUTTON_A; private val B = KeyEvent.KEYCODE_BUTTON_B
    private val X = KeyEvent.KEYCODE_BUTTON_X; private val Y = KeyEvent.KEYCODE_BUTTON_Y

    private fun setUp(vararg presses: Int): SetupFlow = SetupFlow(FaceSetup()).apply { presses.forEach { press(it) } }

    @Test fun theUsualControllerChangesNothing() {
        val r = setUp(X, Y, B, A, A, B).result!!
        assertTrue("same as Android's own rule", r.standard)
        assertEquals(listOf(FaceSetup.SELECT, FaceSetup.CLOSE, FaceSetup.DELETE, FaceSetup.SPACE), listOf(A, B, X, Y).map { r.job(it) })
    }

    @Test fun aSwappedHandheldIsSwappedBack() {
        val r = setUp(Y, X, A, B, B, A).result!!                         // sends them swapped; bottom selects
        assertEquals("the bottom one (sends B) selects", FaceSetup.SELECT, r.job(B))
        assertEquals("the right one (sends A) closes", FaceSetup.CLOSE, r.job(A))
        assertEquals("the left one (sends Y) deletes", FaceSetup.DELETE, r.job(Y))
        assertEquals("same as the old Swap", FaceSetup.fromOld(swap = true, typeOnRight = false), r)
    }

    @Test fun selectingWithTheRightButtonOnAnyController() {
        val r = setUp(X, Y, B, A, B, A).result!!                         // the Odin, right selects
        assertEquals(FaceSetup.SELECT, r.job(B)); assertEquals(FaceSetup.CLOSE, r.job(A))
        assertEquals("delete and space stay put", FaceSetup.DELETE, r.job(X))
    }

    @Test fun onlyOnePairSwappedAndNumberedButtons() {
        val one = setUp(X, Y, A, B, B, A).result!!                       // A/B swapped, X/Y not
        assertEquals(FaceSetup.SELECT, one.job(B)); assertEquals(FaceSetup.DELETE, one.job(X))
        val n = (KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_4).toList()
        val generic = setUp(n[0], n[3], n[2], n[1], n[1], n[2]).result!!  // a pad that sends 1-4
        assertEquals(FaceSetup.SELECT, generic.job(n[1])); assertEquals("4", FaceSetup.letter(n[3]))
    }

    @Test fun anyTwoForSelectAndClose() {
        val r = setUp(X, Y, B, A, Y, X).result!!                         // select top, close left
        assertEquals(FaceSetup.SELECT, r.job(Y)); assertEquals(FaceSetup.CLOSE, r.job(X))
        assertEquals("delete takes the next free, bottom", FaceSetup.DELETE, r.job(A))
        assertEquals("space the last one", FaceSetup.SPACE, r.job(B))
    }

    @Test fun mistakesNeverCount() {
        val f = setUp(X)
        assertEquals(SetupFlow.Heard.AGAIN, f.press(X)); assertEquals("still asking for the top one", SetupFlow.Step.TOP, f.step)
        assertEquals(SetupFlow.Heard.NOT_FACE, f.press(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(SetupFlow.Heard.NOT_FACE, f.press(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(SetupFlow.Heard.NOT_FACE, f.press(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(2 + 1, f.strayPresses)
        f.press(Y); f.press(B); f.press(A); f.press(A)
        assertEquals("close can't be select", SetupFlow.Heard.SAME_AS_SELECT, f.press(A))
        assertEquals(SetupFlow.Step.CLOSE, f.step)
    }

    @Test fun nothingIsKeptUntilItFinishesAndCloseStartsAgain() {
        val before = FaceSetup.fromOld(swap = true, typeOnRight = true)
        val f = SetupFlow(before); f.press(X); f.press(Y)
        assertNull("no result half way", f.result)
        assertEquals("leaving keeps what was there", before, f.keep())
        f.press(B); f.press(A); f.press(A); f.press(B)
        assertEquals("trying delete", SetupFlow.Heard.TRIED, f.press(X))
        assertEquals("close at the end starts again", SetupFlow.Heard.START_OVER, f.press(B))
        assertEquals(SetupFlow.Step.LEFT, f.step)
    }

    @Test fun savedAndReadBack() {
        val r = setUp(Y, X, A, B, A, B).result!!
        assertEquals(r, FaceSetup.parse(r.save()))
        assertNull("odd text reads as nothing", FaceSetup.parse("rubbish"))
        assertNull("two the same isn't a setup", FaceSetup.parse("96,96,97,99:3,2"))
    }
}
