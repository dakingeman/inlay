package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Pictures for Emre, 5 Nov: the Your turn look and the Settings top bar with Find. Touch build/visuals-size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class Nov5Export {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val out by lazy { File("build/nov5").apply { mkdirs() } }
    private fun shot(a: android.app.Activity, n: String) { val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); File(out, "$n.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    private fun press(a: android.app.Activity, k: Int, wait: Long = 300) { val t = SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)); idle(wait) }

    @Test fun tour() {
        assumeTrue(File("build/visuals-size").exists())
        TutorialActivity.yourTurn = true
        try {
            KeyboardSettings(whatsNewSeen = WhatsNew.VERSION).save(context)
            val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(1500)
            press(a, KeyEvent.KEYCODE_BUTTON_A, 2500); shot(a, "t1-demo")
            // Typing in four guided turns: the card guides, the button sits on its key, the picture stays
            a.beginTurnForCheck(); idle(700); shot(a, "l1-capital")
            press(a, KeyEvent.KEYCODE_BUTTON_X, 400); shot(a, "l2-not-that-one")
            press(a, KeyEvent.KEYCODE_BUTTON_R2, 500); shot(a, "l3-now-the-z")
            while (a.stepDemoForCheck("x") == false) Unit
            press(a, KeyEvent.KEYCODE_BUTTON_A, 400); shot(a, "l4-slip")
            press(a, KeyEvent.KEYCODE_BUTTON_X, 400)
            press(a, KeyEvent.KEYCODE_BUTTON_R2, 300)
            while (a.stepDemoForCheck("z") == false) Unit
            press(a, KeyEvent.KEYCODE_BUTTON_A, 300); shot(a, "l5-done")
            idle(900); shot(a, "l6-delete")
            while (a.stepDemoForCheck("⌫") == false) Unit
            press(a, KeyEvent.KEYCODE_BUTTON_A, 300); shot(a, "l7-tip")
            idle(2200); shot(a, "l8-space")
            press(a, KeyEvent.KEYCODE_BUTTON_Y, 1000); shot(a, "l9-search")
            idle(13000); shot(a, "l10-need-a-hand")
        } finally { TutorialActivity.yourTurn = false }
    }

    private fun down(a: android.app.Activity, k: Int) { val t = SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k, 0)) }
    private fun up(a: android.app.Activity, k: Int) { val t = SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k, 0)) }

    @Test fun guided() {
        assumeTrue(File("build/visuals-size").exists())
        TutorialActivity.yourTurn = true
        try {
            KeyboardSettings(whatsNewSeen = WhatsNew.VERSION).save(context)
            val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(1500)
            press(a, KeyEvent.KEYCODE_BUTTON_A, 900)
            a.skipLessonsForCheck(); idle(900); a.beginTurnForCheck(); idle(700)
            shot(a, "f1-accent")
            down(a, KeyEvent.KEYCODE_BUTTON_A); idle(1300); shot(a, "f2-accent-open")
            up(a, KeyEvent.KEYCODE_BUTTON_A); idle(250); shot(a, "f3-accent-done")
            idle(900); shot(a, "f4-suggestion")
            press(a, KeyEvent.KEYCODE_BUTTON_THUMBR, 1000); shot(a, "f5-numbers")
            press(a, KeyEvent.KEYCODE_BUTTON_L2, 400); shot(a, "f6-the-5")
            a.skipLessonsForCheck(); idle(900); a.beginTurnForCheck(); idle(700)
            shot(a, "e1-cursor")
            press(a, KeyEvent.KEYCODE_BUTTON_L1, 400); shot(a, "e2-type-the-i")
            a.lessonJumpForCheck(1); idle(600)
            press(a, KeyEvent.KEYCODE_BUTTON_SELECT, 700); press(a, KeyEvent.KEYCODE_BUTTON_L1, 400); shot(a, "e3-selected")
            a.lessonJumpForCheck(2); idle(600); shot(a, "e4-select-all")
            a.lessonJumpForCheck(3); idle(600)
            down(a, KeyEvent.KEYCODE_BUTTON_R2); idle(600); shot(a, "e5-holding-rt")
            press(a, KeyEvent.KEYCODE_BUTTON_X, 300); up(a, KeyEvent.KEYCODE_BUTTON_R2); idle(200); shot(a, "e6-undone")
            idle(900); shot(a, "e7-close")
            press(a, KeyEvent.KEYCODE_BUTTON_B, 120); shot(a, "e8-closing"); idle(500); shot(a, "e9-closed")
            idle(3500); shot(a, "y1-make-it-yours"); idle(2900); shot(a, "y2-make-it-yours")
            press(a, KeyEvent.KEYCODE_BUTTON_A, 900); shot(a, "z1-done")
            press(a, KeyEvent.KEYCODE_BUTTON_Y, 900); shot(a, "z2-again")
        } finally { TutorialActivity.yourTurn = false }
    }

    @Test fun settings() {
        assumeTrue(File("build/visuals-size").exists())
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, showPreview = false).save(context)
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(900)
        shot(a, "s1-top-find")
        press(a, KeyEvent.KEYCODE_BUTTON_Y, 1200); shot(a, "s2-keyboard-up")
    }
}
