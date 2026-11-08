package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.view.View
import org.junit.Assert.assertTrue
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
 * Split (1.3), measured on what the keyboard really draws. On three wide screens (Odin 2 Portal, a 7" 1080p
 * handheld, a 4:3 handheld), for every language, every size, with and without the number row, on every page
 * (letters, word suggestions, RT held, symbols, number pad, empty and full clipboard):
 * - every key, label and badge sits inside one of the two windows, never in the gap, and no two keys overlap
 * - the windows stay inside the view, apart by at least the minimum gap, and every size keeps the normal size's gap
 * - moving: right along a row visits every key and crosses between the windows; up and down stay on the same side
 * - the accent tray stays inside the window its letter is in
 * And where Split mustn't show (upright screens, TV mode, switched off), the keyboard is the normal one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplitLayoutTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val base = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, split = true)

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin2Portal() = checkAll("Odin")
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xxhdpi") fun handheld1080p() = checkAll("1080p")
    @Test @Config(sdk = [34], qualifiers = "w640dp-h480dp-land-xhdpi") fun handheld4by3() = checkAll("4:3")

    @Test @Config(sdk = [34], qualifiers = "w412dp-h827dp-port-xhdpi")
    fun uprightStaysNormal() {
        val v = make(base)
        assertTrue("Split showed on an upright screen", !v.splitOnForCheck())
        assertTrue("Settings offered Split on an upright screen", !SplitRoom.splitPossible(context, Language.ENGLISH))
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun offAndTvStayNormal() {
        assertTrue("Split showed while switched off", !make(base.copy(split = false)).splitOnForCheck())
        val tv = make(base.copy(tvMode = TvMode.ON)).also { it.setTvMode(true); measure(it) }
        assertTrue("Split showed in TV mode", !tv.splitOnForCheck())
        assertTrue("Settings didn't offer Split on the Odin", SplitRoom.splitPossible(context, Language.ENGLISH))
    }

    private val host by lazy { Robolectric.buildActivity(android.app.Activity::class.java).setup().get() }

    private val frame by lazy { android.widget.FrameLayout(host).also { host.setContentView(it) } }

    /** A real, attached keyboard (the hold-A timer only runs on an attached view), measured at the screen's width. */
    private fun make(s: KeyboardSettings): KeyGridView {
        val v = KeyGridView(host); v.applySettings(s); v.setField(FieldKind.TEXT)
        v.listener = object : KeyGridView.Listener {
            override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
            override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
            override fun onReplaceLast(expected: String, replacement: String) = true
        }
        frame.removeAllViews()
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        idle(50); measure(v); return v
    }

    private fun measure(v: KeyGridView) {
        val w = context.resources.displayMetrics.widthPixels
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun drawn(v: KeyGridView): List<Pair<String, RectF>> {
        val log = mutableListOf<Pair<String, RectF>>(); v.drawLog = log
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); b.recycle()
        v.drawLog = null; return log
    }

    private fun inside(inner: RectF, outer: RectF, slack: Float = 0.5f) =
        inner.left >= outer.left - slack && inner.top >= outer.top - slack && inner.right <= outer.right + slack && inner.bottom <= outer.bottom + slack

    private fun checkAll(screen: String) {
        val problems = mutableListOf<String>()
        val dp = context.resources.displayMetrics.density
        var cases = 0
        val normalGap = HashMap<Language, Float>()      // the gap at the normal size: every size keeps it
        for (lang in Language.values()) for (size in 0..2) for (numberRow in listOf(false, true)) {
            val v = make(base.copy(language1 = lang, sizeLevel = size, numberRow = numberRow))
            val tag = "$screen/${lang.label}/size $size${if (numberRow) "/number row" else ""}"
            if (!v.splitOnForCheck()) { problems += "$tag: Split didn't show"; continue }
            val panels = v.splitPanelsForCheck()
            val (l, r) = panels
            if (l.left < 0 || r.right > v.width || l.top < 0 || l.bottom > v.height || r.bottom > v.height) problems += "$tag: a window leaves the view"
            if (r.left - l.right < SplitRoom.MIN_GAP_DP * dp - 1) problems += "$tag: windows only ${(r.left - l.right) / dp} dp apart"
            val g = r.left - l.right
            if (size == 0 && !numberRow) normalGap[lang] = g
            normalGap[lang]?.let { if (kotlin.math.abs(g - it) > 2 * dp) problems += "$tag: the gap is ${g / dp} dp, the normal size has ${it / dp}" }

            val pages = listOf<Pair<String, () -> Unit>>(
                "letters" to {},
                "words" to { v.setSuggestions(listOf("zelda", "zero", "zone"), "ze") },
                "RT held" to { v.setSuggestions(emptyList(), ""); v.setHeldShift(true) },
                "symbols" to { v.setHeldShift(false); v.resetShift(); v.toggleLayer() },
                "number pad" to { v.resetLayer(); v.setField(FieldKind.NUMBER) },
                "empty clipboard" to { v.setField(FieldKind.TEXT); v.resetLayer(); v.setClips(emptyList()); v.toggleClips() },
                "clipboard" to { v.closeClips(false); v.setClips(listOf("Chrono Trigger", "Metroid Fusion", "Tekken 3", "Ico", "Okami")); v.toggleClips() })
            for ((page, act) in pages) {
                act(); idle(400); cases++
                val keys = v.keysForTest()
                for ((k, rect) in keys) {
                    if (panels.none { inside(rect, it) }) problems += "$tag/$page: $k outside the windows at $rect"
                }
                for (i in keys.indices) for (j in i + 1 until keys.size) {
                    val a = keys[i].second; val b = keys[j].second
                    if (RectF.intersects(RectF(a).apply { inset(0.5f, 0.5f) }, RectF(b).apply { inset(0.5f, 0.5f) }))
                        problems += "$tag/$page: ${keys[i].first} overlaps ${keys[j].first}"
                }
                for ((what, rect) in drawn(v)) {
                    if (what == "pill") continue
                    if (panels.none { inside(rect, it) }) problems += "$tag/$page: $what drawn outside the windows at $rect"
                }
                // Moving: right along every row visits each key once, and up/down never jump sides
                for (row in 0 until v.rowCountForTest()) {
                    val n = v.rowSizeForTest(row)
                    if (v.focusForTest(row, 0) == null) continue
                    val seen = HashSet<String>()
                    repeat(n) { seen += v.focusTargetForCheck().toShortString(); v.moveFocus(1, 0) }
                    if (seen.size < keys.count { it.second.top.toInt() == (v.focusForTest(row, 0)?.top?.toInt() ?: -1) })
                        problems += "$tag/$page: moving right along row $row skipped keys"
                }
            }
            v.closeClips(false); v.resetLayer()
            // Up and down from every letter stay in the same window
            for (row in 1 until v.rowCountForTest() - 1) for (col in 0 until v.rowSizeForTest(row)) {
                val start = v.focusForTest(row, col) ?: continue
                val leftSide = start.centerX() < v.width / 2f
                for (dy in listOf(-1, 1)) {
                    v.focusForTest(row, col); v.moveFocus(0, dy)
                    val t = v.focusTargetForCheck()
                    if ((t.centerX() < v.width / 2f) != leftSide) problems += "$tag: up/down from row $row col $col jumped to the other window"
                }
            }
            // The accent tray stays inside its letter's window (and must really open, so this can't pass by skipping)
            var trays = 0
            // letters with accents in this language, from both halves of the keyboard
            val accented = lang.rows.joinToString("").map { it.toString() }.filter { Accents.variants(it[0], lang) != null }
            for (letter in (accented.take(3) + accented.takeLast(3)).distinct()) {
                v.resetAccents()
                var guard = 0
                while (v.stepTowards(letter) == false && guard++ < 60) idle(5)
                if (v.stepTowards(letter) != true) continue
                val keyBox = v.focusTargetForCheck()
                v.pressA(); idle(1200)
                if (!v.isAccentsOpen()) { v.cancelAccents(); continue }
                trays++
                val panel = panels.first { inside(keyBox, it) }
                for ((what, rect) in drawn(v)) if (what == "cell" && !inside(rect, panel))
                    problems += "$tag: an accent of \"$letter\" left its window at $rect"
                v.cancelAccents(); v.releaseA(); idle(400)
            }
            if (trays == 0) problems += "$tag: no accent tray opened, so the accents weren't checked"
        }
        println("Split, $screen: $cases pages checked, ${problems.size} problems")
        problems.take(40).forEach { println("PROBLEM $it") }
        assertTrue("${problems.size} Split problems on $screen\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }
}
