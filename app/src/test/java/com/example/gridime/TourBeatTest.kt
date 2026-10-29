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
 * The tour and What's new play their button rows one at a time (TutorialActivity, "Beats"). On every device,
 * in both, for every page with rows:
 *  - every row gets one beat, in the rows' order
 *  - every beat is long enough to follow and not so long it drags (3 to 8 s), and a page's run stays under 30 s
 *  - in its beat, the device lights that row's own buttons: the demo acts out what the words say
 * A failure names the device, the page and the row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class TourBeatTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private val devices = listOf(
        "Xbox controller" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.CONTROLLER, buttonStyle = ButtonStyle.XBOX) },
        "PlayStation handheld" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.HANDHELD, buttonStyle = ButtonStyle.PLAYSTATION) },
        "vertical handheld without sticks" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.VERTICAL, buttonStyle = ButtonStyle.SWITCH, verticalSticks = false) },
        "vertical handheld with sticks" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.VERTICAL, buttonStyle = ButtonStyle.SWITCH, verticalSticks = true) },
        "TV remote" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.REMOTE) }
    )

    @Test fun everyRowPlaysItsOwnBeat() {
        val problems = mutableListOf<String>()
        val report = mutableListOf<String>()
        for ((name, change) in devices) for (whatsNew in listOf(false, true)) {
            change(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION)).save(context)
            val t = Robolectric.buildActivity(TutorialActivity::class.java,
                Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
            val pages = t.pageCheck()
            pages.forEachIndexed { p, (title, rows) ->
                idle(50)
                val where = "$name, ${if (whatsNew) "What's new" else "tour"} page ${p + 1} \"$title\""
                if (rows.isNotEmpty()) {
                    if (t.beatsForCheck != rows.indices.toList()) problems += "$where: beats ${t.beatsForCheck} for ${rows.size} rows"
                    // Lit buttons during each beat (a later run, so the first row's extra page-reading time is out)
                    val lit = mutableMapOf<Int, MutableSet<String>>()
                    t.pictureForCheck.onLit = { now -> lit.getOrPut(t.beatRowForCheck) { mutableSetOf() } += now }
                    val lens = t.beatLensForCheck
                    var waited = 0L
                    while (waited < lens.sum() + 3000) {
                        idle(50); waited += 50
                        lit.getOrPut(t.beatRowForCheck) { mutableSetOf() } += t.pictureForCheck.litNow()   // held ones too (a let-go row)
                    }
                    t.pictureForCheck.onLit = null
                    report += "$where: ${t.beatLensForCheck.joinToString(" + ") { "%.1f".format(it / 1000.0) }} = ${"%.1f".format(t.beatLensForCheck.sum() / 1000.0)} s"
                    t.beatLensForCheck.forEachIndexed { i, ms ->
                        if (ms < TutorialActivity.BEAT_MIN_MS || ms > MAX_BEAT_MS) problems += "$where: row ${i + 1} \"${rows.getOrNull(i)}\" lasts $ms ms"
                    }
                    if (t.beatLensForCheck.sum() > MAX_RUN_MS) problems += "$where: one run takes ${t.beatLensForCheck.sum()} ms"
                    rows.forEachIndexed { i, row ->
                        val keys = DevicePicture.keysIn(row)
                        val seen = lit[i].orEmpty()
                        if (keys.isNotEmpty() && keys.none { it in seen }) problems += "$where: the row \"$row\" never lights ${keys.sorted()} in its beat (lit: ${seen.sorted()})"
                    }
                }
                if (p < pages.lastIndex) {
                    val n = SystemClock.uptimeMillis()
                    t.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0))
                    t.dispatchKeyEvent(KeyEvent(n, n, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
                }
            }
            t.finish()
        }
        report.distinct().forEach { println("BEATS $it") }
        problems.forEach { println("PROBLEM $it") }
        assertTrue("${problems.size} problems:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    companion object {
        const val MAX_BEAT_MS = 8000L
        const val MAX_RUN_MS = 30_000L
    }
}
