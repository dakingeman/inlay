package com.example.gridime
import android.content.Intent
import android.os.Looper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourBudgetProbe {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun run(name: String) {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, picture = PictureChoice.VERTICAL, verticalSticks = true, buttonStyle = ButtonStyle.XBOX).save(context)
        for (wn in listOf(false, true)) {
            val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, wn)).setup().get()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            val d = context.resources.displayMetrics.density
            val (avail, stage) = t.budgetForCheck()
            t.layoutForCheck().let { lc -> println("WELCOME $name: column ${(lc.pictureCol.width / d).toInt()}x${(lc.pictureCol.height / d).toInt()}dp picture ${(lc.picture.width / d).toInt()}x${(lc.picture.height / d).toInt()} header ${lc.header?.let { (it.width / d).toInt().toString() + "x" + (it.height / d).toInt() }} words h ${(lc.words.height / d).toInt()} ${t.reserveDebug}") }
            repeat(3) { val n = android.os.SystemClock.uptimeMillis(); t.dispatchKeyEvent(android.view.KeyEvent(n, n, 0, android.view.KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(android.view.KeyEvent(n, n, 1, android.view.KeyEvent.KEYCODE_BUTTON_A, 0)); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400)) }
            val (r, fit) = t.stageForCheck()
            println("STAGE $name: keyboard window ${(r.width() / d).toInt()}x${(r.height() / d).toInt()}dp at y ${(r.top / d).toInt()}, keys drawn at ${"%.2f".format(fit)}, screen ${(context.resources.displayMetrics.widthPixels / d).toInt()}dp wide")
            val lc = t.layoutForCheck()
            println("DEVICE $name: column ${(lc.pictureCol.width / d).toInt()}dp visible ${lc.pictureCol.visibility} picture ${(lc.picture.width / d).toInt()}x${(lc.picture.height / d).toInt()} words ${(lc.words.width / d).toInt()}x${(lc.words.height / d).toInt()}")
            println("RESERVE $name ${t.reserveDebug}; now words overflow ${t.wordsOverflowPx()}")
            println("BUDGET $name ${if (wn) "whatsnew" else "tour"}: words+stage ${(avail / d).toInt()}dp, stage needs ${(stage / d).toInt()}dp, so words may take ${((avail - stage) / d).toInt()}dp; pages: " +
                t.measuredPages.toSortedMap().values.joinToString { "${(it / d).toInt()}" } + " dp")
        }
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("odin")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone() = run("phone 20:9")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = run("tv")
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThree() = run("4:3")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = run("square")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocket() = run("pocket")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = run("4:3 small")
}
