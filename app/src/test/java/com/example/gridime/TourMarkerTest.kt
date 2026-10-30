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
 * Balance, not only overlaps. On every screen the tour runs on, for every page with steps (the card, 30 Oct 2026):
 *  - one step card shows, drawn whole: nothing cuts it off, and it stays clear of the button bar, the keyboard window and the device
 *  - a scrolling area keeps its edge: words scrolled past it never draw over the device (a small upright phone did, 30 Oct 2026)
 *  - the card's edge sits exactly on the title's left edge, inside its column (it never hangs past it: a fading page cut
 *    the overhang off on the Odin, 30 Oct 2026)
 *  - beside the keyboard (the split), the card shares the keyboard window's foot, and its top sits on the line its screen
 *    chose ([TutorialActivity.CardTop]): the window's top, or the keyboard's top under the text box
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
        if (v.tag == TutorialActivity.CARD_TAG) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) markers(v.getChildAt(i), out)
        return out
    }

    private fun scrolls(v: View, out: MutableList<android.widget.ScrollView> = mutableListOf()): List<android.widget.ScrollView> {
        if (v is android.widget.ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    /** The card's first row of buttons (a held button at the top counts too: it starts on the same edge). */
    private fun firstBadge(v: View): BadgeLineView? {
        if (v is BadgeLineView && v.isShown) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstBadge(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun run(screen: String) {
        val problems = mutableListOf<String>()
        val cardTops = mutableListOf<String>()
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
                        val cards = markers(t.window.decorView).filter { shown(it) }
                        if (cards.size != 1) problems += "$where: ${cards.size} step cards showing"
                        for (card in cards) {
                            val r = win(card)
                            val seen = Rect()
                            if (!card.getGlobalVisibleRect(seen) || seen.width() < card.width - 1 || seen.height() < card.height - 1) problems += "$where: the card is cut off (${seen.width()}x${seen.height()} of ${card.width}x${card.height})"
                            val bar = win(c.controls); val panel = win(c.panel)
                            if (r.bottom > bar.top + slack) problems += "$where: the card runs ${(r.bottom - bar.top) / d} dp into the button bar"
                            if (Rect.intersects(r, panel)) problems += "$where: the card overlaps the keyboard window"
                            if (shown(c.picture) && Rect.intersects(r, win(c.picture))) problems += "$where: the card overlaps the device picture"
                            val badge = firstBadge(card)
                            val titleLeft = win(c.title).left
                            if (badge == null) problems += "$where: the card has no buttons"
                            if (abs(r.left - titleLeft) > slack) problems += "$where: the card's edge is ${(r.left - titleLeft) / d} dp off the title's edge"
                            if (c.split && abs(r.bottom - panel.bottom) > 2 * d) problems += "$where: the card's foot is ${(r.bottom - panel.bottom) / d} dp off the keyboard window's foot"
                            if (c.split) {
                                val line = when (t.cardTop) {
                                    TutorialActivity.CardTop.WINDOW -> panel.top
                                    TutorialActivity.CardTop.KEYBOARD -> win(c.keyboard).top
                                    TutorialActivity.CardTop.ROOM -> null
                                }
                                if (line != null && abs(r.top - line) > 2 * d) problems += "$where: the card's top is ${(r.top - line) / d} dp off its line (${t.cardTop})"
                                if (r.top < panel.top - slack) problems += "$where: the card reaches ${(panel.top - r.top) / d} dp above the keyboard window"
                                cardTops += "$screen ${t.cardTop}"
                            }
                        }
                    }
                    // A scrolling area keeps its edge: what scrolls past it never draws over the device or the keyboard
                    scrolls(t.window.decorView).filter { (it.parent as? ViewGroup)?.clipChildren != true }.forEach { problems += "$where: a scrolling area draws past its own edge" }
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
        cardTops.distinct().forEach { println("CARD-TOP $it") }
        problems.distinct().forEach { println("PROBLEM $it") }
        assertTrue("${problems.distinct().size} problems:\n" + problems.distinct().take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() = run("Odin 2 Portal")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phone() = run("phone sideways")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phoneUpright() = run("phone upright")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun smallPhoneUpright() = run("small phone upright")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun tv() = run("TV")
    @Test @Config(sdk = [34], qualifiers = "w640dp-h432dp-land-xhdpi") fun fourThree() = run("4:3")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun square() = run("square")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun pocket() = run("small handheld")
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun small43() = run("small 4:3")
}
