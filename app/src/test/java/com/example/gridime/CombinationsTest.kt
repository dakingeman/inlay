package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Every combination of what a user can change on the keyboard keeps every word and letter readable and
 * in its place: each label stays inside its key, never runs into the key's button badge, never overlaps
 * another label, and a letter is never cut short ("…").
 *
 * Covered: every language × both layouts × all three sizes × number row on and off × TV mode on and off,
 * and (on English) every button style × font × key shape × highlight style × lightest and boldest letters ×
 * with and without sticks × hints on and off. Each in six states: letters with word suggestions, shift,
 * RT held (its tips), symbols, selecting, and the number pad. On four screens: the Odin 2 Portal, a small
 * vertical handheld, a phone held upright and a tablet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CombinationsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin2Portal() = run()
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun smallVerticalHandheld() = run()
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phoneUpright() = run()
    @Test @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi") fun tablet() = run()

    private fun combos(): List<KeyboardSettings> {
        val base = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, wordSuggestions = true, holdAccents = true)
        val out = mutableListOf<KeyboardSettings>()
        var i = 0
        for (lang in Language.values()) for (layout in KeyLayout.values()) for (size in 0..2) for (numbers in listOf(false, true)) for (tv in listOf(TvMode.OFF, TvMode.ON)) {
            // what doesn't change the letters' room rotates, so every value shows up in every company
            out += base.copy(language1 = lang, layout = layout, sizeLevel = size, numberRow = numbers, tvMode = tv,
                buttonStyle = ButtonStyle.values()[i % 3], font = FontChoice.values()[(i / 3) % 3],
                keyShape = KeyShape.values()[(i / 2) % KeyShape.values().size], highlightStyle = HighlightStyle.values()[i % HighlightStyle.values().size],
                hasSticks = i % 2 == 0, letterWeight = if (i % 5 == 0) 10 else 4)
            i++
        }
        for (b in ButtonStyle.values()) for (f in FontChoice.values()) for (shape in KeyShape.values()) for (h in HighlightStyle.values())
            for (weight in listOf(0, 10)) for (sticks in listOf(true, false)) for (hints in listOf(true, false))
                out += base.copy(buttonStyle = b, font = f, keyShape = shape, highlightStyle = h, letterWeight = weight, hasSticks = sticks, showHints = hints)
        return out
    }

    private fun run() {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val width = context.resources.displayMetrics.widthPixels
        val problems = linkedSetOf<String>()
        var drawn = 0
        for (s in combos()) {
            val v = KeyGridView(context)
            v.applySettings(s)
            if (s.tvMode == TvMode.ON) v.setTvMode(true)
            frame.removeAllViews()
            frame.addView(v, android.widget.FrameLayout.LayoutParams(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            idle(16)
            val tag = "${s.language1}/${s.layout}/size ${s.sizeLevel}${if (s.numberRow) "/number row" else ""}${if (s.tvMode == TvMode.ON) "/TV" else ""}" +
                "/${s.buttonStyle}/${s.font}/${s.keyShape}/${s.highlightStyle}/weight ${s.letterWeight}${if (s.hasSticks) "" else "/no sticks"}${if (s.showHints) "" else "/no hints"}"
            val states = listOf<Pair<String, () -> Unit>>(
                "words" to { v.setSuggestions(listOf("republic", "remember", "reply"), "re") },
                "shift" to { v.setSuggestions(emptyList(), ""); v.cycleShift() },
                "RT held" to { v.resetShift(); v.setHeldShift(true) },
                "symbols" to { v.setHeldShift(false); v.resetShift(); v.toggleLayer() },
                "selecting" to { v.toggleLayer(); v.setSelecting(true, 128) },
                "number pad" to { v.setSelecting(false, 0); v.setField(FieldKind.NUMBER) })
            for ((state, act) in states) {
                act(); idle(220)                        // let fades finish
                check(v, "$tag/$state", problems); drawn++
            }
        }
        frame.removeAllViews()
        println("combinations: $drawn keyboards drawn, ${problems.size} problems")
        problems.forEach { println("PROBLEM $it") }
        yields.forEach { (k, n) -> println("BADGE STEPS ASIDE for $k: $n") }
        assertTrue("${problems.size} problems:\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    /** Where a key's badge had to step aside for its word (a fallback, reported, not a failure). */
    private val yields = sortedMapOf<String, Int>()

    private fun check(v: KeyGridView, tag: String, problems: MutableSet<String>) {
        if (v.width <= 0 || v.height <= 0) { problems += "$tag: the keyboard has no size"; return }
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val log = mutableListOf<Pair<String, RectF>>()
        v.drawLog = log; v.draw(Canvas(bmp)); v.drawLog = null; bmp.recycle()
        log.filter { it.first.startsWith("yield:") }.forEach { yields.merge(it.first.removePrefix("yield:") + " " + tag.substringBefore("/").let { "" } + tag.split("/").filter { p -> p.startsWith("size") || p == "TV" }.joinToString("/"), 1, Int::plus) }
        val keys = v.keysForTest()
        val badges = log.filter { it.first == "badge" }.map { it.second }
        val labels = log.filter { it.first.startsWith("label:") }.map { it.first.removePrefix("label:") to it.second }
        val slack = 1f
        for ((text, r) in labels) {
            val (kind, key) = keys.firstOrNull { it.second.contains(r.centerX(), r.centerY()) } ?: continue
            val inside = RectF(key).apply { inset(-slack, -slack) }
            if (!inside.contains(r)) problems += "$tag: \"$text\" spills out of its key ($kind)"
            badges.filter { key.contains(it.centerX(), it.centerY()) && RectF.intersects(it, RectF(r).apply { inset(slack, slack) }) }
                .forEach { _ -> problems += "$tag: \"$text\" runs into its key's badge ($kind)" }
            if (text.endsWith("…") && (kind.startsWith("CHAR") || kind.startsWith("SPACE")))
                problems += "$tag: \"$text\" is cut short ($kind)"
        }
        for (i in labels.indices) for (j in i + 1 until labels.size) {
            val a = RectF(labels[i].second).apply { inset(slack, slack) }; val b = labels[j].second
            if (RectF.intersects(a, b)) problems += "$tag: \"${labels[i].first}\" and \"${labels[j].first}\" overlap"
        }
    }
}
