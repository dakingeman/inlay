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
 * The tour and What's new, side by side: on every page, for every device, the device picture,
 * the loop bar, the text box and the keyboard all show in full (nothing cut off at the top or
 * bottom), on the screen sizes handhelds and phones really have.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StageFitsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun press(a: android.app.Activity, keyCode: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        idle(250)
    }

    private fun check() {
        val problems = mutableListOf<String>()
        val devices = listOf(PictureChoice.HANDHELD to false, PictureChoice.VERTICAL to false, PictureChoice.VERTICAL to true,
            PictureChoice.CONTROLLER to false, PictureChoice.REMOTE to false)
        devices.forEach { (pic, sticks) ->
            listOf(ButtonStyle.XBOX, ButtonStyle.PLAYSTATION).forEach { style ->
                KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style,
                    picture = pic, verticalSticks = sticks).save(context)
                listOf(false, true).forEach { whatsNew ->
                    val t = Robolectric.buildActivity(TutorialActivity::class.java,
                        Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
                    val pages = t.pageCheck()
                    pages.forEachIndexed { i, (title, _) ->
                        idle(700)
                        val over = t.stageOverflowPx()
                        if (over > 0) problems += "$pic${if (sticks) "+sticks" else ""} $style ${if (whatsNew) "What's new" else "tour"} page ${i + 1} ($title): cut off by ${over}px"
                        if (i < pages.size - 1) press(t, KeyEvent.KEYCODE_BUTTON_A)
                    }
                }
            }
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin2Portal() = check()
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone20by9() = check()
    @Test @Config(sdk = [34], qualifiers = "w732dp-h412dp-land-xxhdpi") fun phone16by9() = check()
    @Test @Config(sdk = [34], qualifiers = "w960dp-h600dp-land-xhdpi") fun tablet() = check()
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThreeLarge() = check()
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = check()
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocketClassic() = check()
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun fourThreeSmall() = check()
}
