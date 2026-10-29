package com.example.gridime

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.fail
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
 * The tour and What's new page template, on every screen shape and every device:
 *  - the title is one line, the sentence at most two lines, and every button row one line
 *  - the keyboard window keeps one place and one size on every page (only the words change)
 *  - the keyboard is drawn at least at [StagePanel.MIN_FIT] of its real size
 * A new page that's too long for the template fails here, with its name: shorten its words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourTemplateTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun press(a: android.app.Activity, keyCode: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        idle(250)
    }

    private fun check() {
        val problems = linkedSetOf<String>()
        val devices = listOf(PictureChoice.HANDHELD to false, PictureChoice.VERTICAL to false, PictureChoice.VERTICAL to true,
            PictureChoice.CONTROLLER to false, PictureChoice.REMOTE to false)
        for ((pic, sticks) in devices) for (style in ButtonStyle.values()) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = pic, verticalSticks = sticks).save(context)
            for (whatsNew in listOf(false, true)) {
                val t = Robolectric.buildActivity(TutorialActivity::class.java,
                    Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
                val pages = t.pageCheck()
                var first: android.graphics.Rect? = null
                val onTv = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                pages.forEachIndexed { i, (title, _) ->
                    idle(500)
                    val tag = "${if (whatsNew) "What's new" else "tour"} page ${i + 1} \"$title\" ($pic${if (sticks) "+sticks" else ""}, $style)"
                    if (t.titleWrapped()) problems += "$tag: the title takes ${t.titleLines()} lines (${t.titleLinesAllowed} at most)"
                    t.rowsWrapped().forEach { problems += "$tag: the row \"$it\" doesn't fit on one line" }
                    val (rect, fit) = t.stageForCheck()
                    val floor = t.minFitForCheck * (if (title.startsWith("A number row")) 0.88f else 1f)   // the extra row of keys
                    if (!onTv && fit < floor - 0.001f) problems += "$tag: the keyboard is drawn at ${"%.2f".format(fit)} of its size (at least ${"%.2f".format(floor)})"
                    val numberRowPage = title.startsWith("A number row")
                    if (first == null) first = rect
                    else if (numberRowPage) { if (kotlin.math.abs(rect.centerY() - first!!.centerY()) > 1 && rect.bottom != first!!.bottom || kotlin.math.abs(rect.centerX() - first!!.centerX()) > 1) problems += "$tag: the keyboard window moved ($first → $rect)" }
                    else if (rect != first) problems += "$tag: the keyboard window moved or changed size ($first → $rect)"
                    t.captionOverflow().forEach { problems += "$tag: the row \"$it\" takes more than two lines in the caption" }
                    if (t.stageOverflowPx() > 0) problems += "$tag: ${t.overflowWhat()} run past their room by ${t.stageOverflowPx()} px"
                    if (i < pages.size - 1) press(t, KeyEvent.KEYCODE_BUTTON_A)
                }
                t.finish()
            }
        }
        if (problems.isNotEmpty()) fail("${problems.size} problems\n" + problems.joinToString("\n"))
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin2Portal() = check()
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone20by9() = check()
    @Test @Config(sdk = [34], qualifiers = "w732dp-h412dp-land-xxhdpi") fun phone16by9() = check()
    @Test @Config(sdk = [34], qualifiers = "w960dp-h600dp-land-xhdpi") fun tablet() = check()
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = check()
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThreeLarge() = check()
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = check()
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocketClassic() = check()
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun fourThreeSmall() = check()
}
