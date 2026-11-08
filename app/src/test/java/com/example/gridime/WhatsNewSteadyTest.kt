package com.example.gridime

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
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
 * What's new, page to page (Emre, 8 Nov: going to Split, the keyboard changed size a touch and the device above it
 * jumped). Once a page is up, its stage, window and device picture stay exactly where they are while it settles:
 * the same place and size from the first frame the page shows to half a second later. Only the scene's own
 * animations move things, and none of them start that early.
 */
@RunWith(RobolectricTestRunner::class)
class WhatsNewSteadyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Moves the clock about [ms], one due task at a time (idleFor can run a chain of frames far past its end on a page change). */
    private fun step(ms: Long) {
        val sh = shadowOf(Looper.getMainLooper()); val end = SystemClock.uptimeMillis() + ms; var guard = 0
        while (guard++ < 1000) { val next = sh.nextScheduledTaskTime.toMillis(); if (next == 0L || next > end) break; sh.runOneTask() }
        val left = end - SystemClock.uptimeMillis()
        if (left > 0) org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(left))
    }

    private fun check(name: String, pic: PictureChoice) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = pic).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        val pages = t.pageWordsCheck()
        val bad = mutableListOf<String>()
        idle(2000)
        for (i in 1 until pages.size) {
            val now = SystemClock.uptimeMillis()
            t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
            idle(16)
            val first = t.stageSteadyForCheck()
            for (f in 1..30) {
                idle(16)
                val g = t.stageSteadyForCheck()
                if (g != first) { bad += "\"${pages[i].first}\" moved ${f * 16} ms after it showed: $first → $g"; break }
            }
            idle(1500)
        }
        t.finish()
        assertTrue("$name: " + bad.joinToString("; "), bad.isEmpty())
    }

    /** The pages that show the device over the keyboard window keep both in one place and one size (the template). */
    private fun samePlace(name: String, pic: PictureChoice) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = pic).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        val pages = t.pageWordsCheck()
        val seen = mutableListOf<Pair<String, List<String>>>()
        idle(300)
        pages.forEachIndexed { i, (title, _, scene) ->
            if (scene in setOf("TOUR", "SPLIT", "SYMBOLS")) seen += title to t.stageSteadyForCheck()!!
            val now = SystemClock.uptimeMillis()
            if (i < pages.lastIndex) { t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0)) }
            idle(300)
        }
        t.finish()
        val bad = seen.drop(1).filter { it.second != seen.first().second }.map { "\"${it.first}\" ${it.second} vs \"${seen.first().first}\" ${seen.first().second}" }
        assertTrue("$name: " + bad.joinToString("; "), bad.isEmpty())
    }
    /** Page to page the close-up dissolves (Emre, 8 Nov: sudden cuts): the page you left fades out in small steps. */
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun dissolves() {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        val pages = t.pageWordsCheck()
        val bad = mutableListOf<String>()
        idle(1500)
        for (i in 1 until pages.size) {
            val now = SystemClock.uptimeMillis()
            t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
            // frames as the screen draws them: the dissolve's clock runs from the first one
            val v = t.window.decorView
            val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888); val c = android.graphics.Canvas(b)
            val seen = (0 until 12).map { v.draw(c); step(16); t.crossLeftForCheck() }
            b.recycle()
            // a dissolve, not a cut: most of the old page is still there on the first frame, it leaves in small steps, and it's gone
            val steps = (listOf(1f) + seen).zipWithNext { a, b -> a - b }
            if (seen.first() < 0.6f || steps.any { it > 0.3f } || seen.last() > 0f) bad += "\"${pages[i].first}\" doesn't dissolve: $seen"
            idle(1500)
        }
        t.finish()
        assertTrue(bad.joinToString("; "), bad.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odinSamePlace() = samePlace("odin", PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun controllerSamePlace() = samePlace("controller", PictureChoice.CONTROLLER)
    @Test @Config(sdk = [34], qualifiers = "w620dp-h540dp-land-xhdpi") fun verticalSamePlace() = samePlace("vertical", PictureChoice.VERTICAL)

    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun odin() = check("odin", PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun controller() = check("controller", PictureChoice.CONTROLLER)
    @Test @Config(sdk = [34], qualifiers = "w620dp-h540dp-land-xhdpi") fun vertical() = check("vertical", PictureChoice.VERTICAL)
}
