package com.example.gridime

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.fail
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

/**
 * The tour and What's new keep the Odin's balance on every screen, not just fit (StageFitsTest proves things fit; this
 * proves they're in proportion). On every page of both:
 *  1. Words in proportion: the title's size against the screen's short side stays within [BAND_LOW]–[BAND_HIGH] of the
 *     Odin's (a 1080p handheld at a small display size showed tiny words; Reddit video, 3 Nov 2026).
 *  2. No empty boxes: the step card's steps fill at least [FILL_MIN] of the card's inside.
 *  3. No empty bands: no stretch right across the screen with nothing on it (words, card, device, keyboard window,
 *     controls) is taller than [BAND_MAX] of the screen's height. The Odin's biggest is 4 %.
 * Every screen also saves a screenshot with its numbers, for review: build/balance/<screen>/ (touch build/visuals-balance).
 * Font size: the 1080p handheld also runs at the largest common font size (1.3×).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourBalanceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun check(screen: String, fontScale: Float = 1f) {
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        val shots = File("build/visuals-balance").exists()
        val dir = File("build/balance/$screen").apply { if (shots) mkdirs() }
        val problems = mutableListOf<String>()
        val report = StringBuilder()
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.AUTO).save(context)
        // Every page of both: a page added later is checked the day it's written
        for (whatsNew in listOf(false, true)) {
        val kind = if (whatsNew) "whatsnew" else "tour"
        val t = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
        val pages = t.pageCheck()
        pages.forEachIndexed { i, (title, _) ->
            idle(if (i == 0) 2500 else 2800)
            val b = t.balanceForCheck()
            val words = b.titlePx / b.screenShortPx / ODIN_RATIO
            val line = "$kind page ${i + 1} ($title): scale ${"%.2f".format(b.scale)}, words ${"%.2f".format(words)}× the Odin's, " +
                "card ${b.cardFill?.let { "%.0f %%".format(it * 100) } ?: "none"} filled, biggest empty band ${"%.0f %%".format(b.biggestBand * 100)}"
            report.appendLine(line); report.appendLine("    " + b.pieces)
            if (words < BAND_LOW || words > BAND_HIGH) problems += "$screen $line: words out of proportion"
            b.cardFill?.let { if (it < FILL_MIN) problems += "$screen $line: card mostly empty" }
            if (b.biggestBand > BAND_MAX) problems += "$screen $line: empty band"
            if (shots) {
                val v = t.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(bmp))
                File(dir, "$kind-$i.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (i < pages.size - 1) {
                val now = SystemClock.uptimeMillis()
                t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0))
                t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
            }
        }
        t.finish()
        }
        if (shots) File(dir, "numbers.txt").writeText(report.toString())
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    // The current eight
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin2Portal() = check("odin")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone20by9() = check("phone-20x9")
    @Test @Config(sdk = [34], qualifiers = "w732dp-h412dp-land-xxhdpi") fun phone16by9() = check("phone-16x9")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h600dp-land-xhdpi") fun tablet() = check("tablet")
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThreeLarge() = check("43-large")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = check("square")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocketClassic() = check("pocket-classic")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun fourThreeSmall() = check("43-small")
    // 1080p handhelds (7", 1920 × 1080) at small, default and large display size
    @Test @Config(sdk = [34], qualifiers = "w1097dp-h617dp-land-280dpi") fun hdSmall() = check("1080p-small")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi") fun hdDefault() = check("1080p-default")
    @Test @Config(sdk = [34], qualifiers = "w853dp-h480dp-land-360dpi") fun hdLarge() = check("1080p-large")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi") fun hdDefaultBigFont() = check("1080p-default-font130", 1.3f)
    // Other shapes
    @Test @Config(sdk = [34], qualifiers = "w810dp-h540dp-land-xhdpi") fun threeTwo() = check("3x2")
    @Test @Config(sdk = [34], qualifiers = "w683dp-h512dp-land-xhdpi") fun fourThree() = check("4x3")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = check("tv")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun phoneUpright() = check("phone-upright")
    @Test @Config(sdk = [34], qualifiers = "w412dp-h915dp-port-xxhdpi") fun phoneUprightTall() = check("phone-upright-tall")

    companion object {
        /** The Odin's title size against its screen's short side: 24 sp on 412 dp. */
        const val ODIN_RATIO = Design.TYPE_HEADER / 412f
        const val BAND_LOW = 0.75f
        const val BAND_HIGH = 1.35f
        const val FILL_MIN = 0.6f
        const val BAND_MAX = 0.2f
    }
}
