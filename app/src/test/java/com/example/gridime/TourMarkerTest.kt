package com.example.gridime

import android.content.Intent
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.math.abs

/**
 * Balance, not only overlaps. On every screen the tour runs on, for every page with button rows:
 *  - one current-row marker shows (the same in the list and the caption), drawn whole: nothing cuts it off
 *  - it is exactly as tall as its row's buttons, centred on them, and the same step before them everywhere
 *  - the buttons and the words line up with the title, and the words down the list line up with each other
 *  - small screens: the device beside the sentence and caption is drawn whole, centred on them, with the same margin on its
 *    right as the words have on their left, and at least [TutorialActivity.DEVICE_HEADER_GAP_DP] from the words
 * Measured in pixels, with a pixel to spare for rounding.
 */
@RunWith(RobolectricTestRunner::class)
class TourMarkerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun win(v: View): Rect { val l = IntArray(2); v.getLocationInWindow(l); return Rect(l[0], l[1], l[0] + v.width, l[1] + v.height) }
    private fun shown(v: View): Boolean { var x: View? = v; while (x != null) { if (x.visibility != View.VISIBLE) return false; x = x.parent as? View }; return true }
    private fun markers(v: View, out: MutableList<View> = mutableListOf()): List<View> {
        if (v.tag == TutorialActivity.MARKER_TAG) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) markers(v.getChildAt(i), out)
        return out
    }

    private fun run(screen: String) {
        val problems = mutableListOf<String>()
        val d = context.resources.displayMetrics.density
        val slack = 1.5f
        for ((dev, pic) in listOf("handheld" to PictureChoice.HANDHELD, "vertical" to PictureChoice.VERTICAL, "vertical with sticks" to PictureChoice.VERTICAL,
                "controller" to PictureChoice.CONTROLLER, "remote" to PictureChoice.REMOTE))
            for (whatsNew in listOf(false, true)) {
                KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, picture = pic, buttonStyle = ButtonStyle.XBOX, verticalSticks = dev.endsWith("sticks")).save(context)
                val t = Robolectric.buildActivity(TutorialActivity::class.java,
                    Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
                val pages = t.pageCheck()
                // The pages that show every device get a wider column than the others (a controller shows at a good size)
                val (regular, showcase, wanted) = t.columnsForCheck()
                if (wanted > regular && regular > 0 && showcase <= regular)
                    problems += "$screen, $dev, ${if (whatsNew) "What's new" else "tour"}: the showcase column ($showcase px) is no wider than the others ($regular px)"
                pages.forEachIndexed { p, (title, rows) ->
                    idle(2600)                                         // past the first row's fade
                    val where = "$screen, $dev, ${if (whatsNew) "What's new" else "tour"} page ${p + 1} \"$title\""
                    val c = t.layoutForCheck()
                    if (rows.isNotEmpty()) {
                        val all = markers(t.window.decorView).filter { shown(it) }
                        val lit = all.filter { it.alpha > 0.99f }
                        if (lit.size != 1) problems += "$where: ${lit.size} current-row markers showing"
                        for (m in lit) {
                            val r = win(m)
                            val seen = Rect()
                            if (!m.getGlobalVisibleRect(seen) || seen.width() < m.width || seen.height() < m.height) problems += "$where: the marker is cut off (${seen.width()}x${seen.height()} of ${m.width}x${m.height})"
                            val parent = m.parent as ViewGroup
                            val next = parent.getChildAt(parent.indexOfChild(m) + 1)
                            val badge = (next as? BadgeLineView) ?: ((next as? ViewGroup)?.getChildAt(0) as? BadgeLineView)
                            if (badge == null) { problems += "$where: the marker isn't before its buttons"; continue }
                            val b = win(badge)
                            val gap = b.left - r.right
                            if (abs(gap - TutorialActivity.MARKER_GAP_DP * d) > slack) problems += "$where: marker ${gap / d} dp before its buttons (should be ${TutorialActivity.MARKER_GAP_DP})"
                            if (abs(r.exactCenterY() - b.exactCenterY()) > slack) problems += "$where: marker off centre on its buttons by ${(r.exactCenterY() - b.exactCenterY()) / d} dp"
                            if (r.height() > b.height()) problems += "$where: marker taller than its buttons"
                            // The buttons line up with the title (the widest; narrower ones sit right next to their words)
                            val titleLeft = win(c.title).left
                            if (b.left < titleLeft - slack) problems += "$where: the buttons start ${(titleLeft - b.left) / d} dp before the title"
                        }
                    }
                    // Small screens: the device beside the words
                    if (c.header != null && shown(c.picture) && c.pictureCol.parent === c.header) {
                        val pr = win(c.picture); val col = win(c.pictureCol); val beside = win((c.header as ViewGroup).getChildAt(0)); val h = win(c.header)
                        val seen = Rect()
                        if (!c.picture.getGlobalVisibleRect(seen) || seen.height() < c.picture.height - 1) problems += "$where: the device is cut off"
                        val screenW = t.window.decorView.width
                        val right = screenW - col.right
                        if (abs(right - c.side) > slack) problems += "$where: the device's right margin is ${right / d} dp, the words' left is ${c.side / d} dp"
                        if (abs(pr.exactCenterY() - h.exactCenterY()) > 2 * d) problems += "$where: the device is ${(pr.exactCenterY() - h.exactCenterY()) / d} dp off the centre of the words beside it"
                        if (col.left - beside.right < TutorialActivity.DEVICE_HEADER_GAP_DP * d - slack) problems += "$where: the device is ${(col.left - beside.right) / d} dp from the words"
                    }
                    if (c.header != null && !shown(c.picture)) problems += "$where: the device had to step out (no room beside the words)"
                    if (p < pages.lastIndex) {
                        val n = SystemClock.uptimeMillis()
                        t.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0))
                        t.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
                    }
                }
                t.finish()
            }
        problems.distinct().forEach { println("PROBLEM $it") }
        assertTrue("${problems.distinct().size} problems:\n" + problems.distinct().take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("Odin 2 Portal")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone() = run("phone sideways")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phoneUpright() = run("phone upright")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = run("TV")
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThree() = run("4:3")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = run("square")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocket() = run("small handheld")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = run("small 4:3")
}
