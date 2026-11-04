package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Mock-up material for the 1.3 Split layout and the Konami Code ideas: the real keyboard, drawn by the app's own
 * code, with its letter rows split by a gap in the middle, plus where every key landed (JSON) so the mock-ups can
 * place the halves exactly. Touch build/visuals-split. Output: build/split/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class SplitMockExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private val out = File("build/split").apply { mkdirs() }
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun ch(c: Char, w: Float) = Key(KeyType.CHAR, c.toString(), w)
    private fun gap(w: Float) = Key(KeyType.GAP, "", w)

    /** The English letter rows with a gap of [g] units between the halves (a row is 10 units). */
    private fun splitRows(g: Float): List<List<Key>> {
        val a = (10f - g) / 10f
        val r = Language.ENGLISH.rows
        val top = r[0].take(5).map { ch(it, a) } + gap(g) + r[0].drop(5).map { ch(it, a) }
        val mid = r[1].take(5).map { ch(it, a) } + gap(g) + r[1].drop(5).map { ch(it, a) }
        val low = listOf(Key(KeyType.SHIFT, weight = a, hint = ControllerButton.R2)) + r[2].take(4).map { ch(it, a) } + gap(g) +
            r[2].drop(4).map { ch(it, a) } + Key(KeyType.DELETE, weight = 2 * a, hint = ControllerButton.X)
        val bottom = listOf(Key(KeyType.LAYER, "?123", 2 * a, hint = ControllerButton.L2), ch(',', a),
            Key(KeyType.SPACE, "", 2 * a, hint = ControllerButton.Y), gap(g), Key(KeyType.SPACE, "", 2 * a),
            ch('.', a), Key(KeyType.ENTER, "", 2 * a, hint = ControllerButton.START))
        return listOf(top, mid, low, bottom)
    }

    private fun render(name: String, rows: List<List<Key>>?, focus: Pair<Int, Int>, widthDp: Int = 827, s: KeyboardSettings) {
        val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(host)
        v.applySettings(s); v.setField(FieldKind.TEXT)
        v.listener = object : KeyGridView.Listener {
            override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
            override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
            override fun onReplaceLast(expected: String, replacement: String) = true
        }
        if (rows != null) KeyGridView::class.java.getDeclaredField("letterRows").apply { isAccessible = true }.set(v, rows)
        val dp = context.resources.displayMetrics.density
        frame.addView(v, android.widget.FrameLayout.LayoutParams((widthDp * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        idle(200)
        v.requestLayout(); idle(200)
        if (v.focusForTest(focus.first, focus.second) == null)        // no key there: hide the highlight
            KeyGridView::class.java.getDeclaredField("selRow").apply { isAccessible = true }.setInt(v, 99)
        v.invalidate()
        idle(900)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val keys = v.keysForTest().joinToString(",\n") { (k, r) ->
            "{\"key\":\"${k.replace("\"", "\\\"")}\",\"l\":${r.left},\"t\":${r.top},\"r\":${r.right},\"b\":${r.bottom}}" }
        File(out, "$name.json").writeText("{\"w\":${v.width},\"h\":${v.height},\"keys\":[\n$keys\n]}")
    }

    @Test fun export() {
        assumeTrue(File("build/visuals-split").exists())
        val s = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, showHints = true)
        render("full", null, 2 to 4, s = s)                   // the keyboard as it is, G lit
        render("split2", splitRows(2f), 2 to 4, s = s)        // a 2-unit gap, G lit
        render("split3", splitRows(3f), 2 to 4, s = s)        // a wider gap
        render("split2-h", splitRows(2f), 2 to 6, s = s)      // H lit: the right half's own highlight
        render("full-nohl", null, 0 to 0, s = s)              // highlight on the tool strip, for effect frames
        render("full-e", null, 1 to 2, s = s)                 // E lit (typing "te" for Tekken)
        render("split2-e", splitRows(2f), 1 to 2, s = s)
        render("split3-e", splitRows(3f), 1 to 2, s = s)
        render("split2-j", splitRows(2f), 2 to 7, s = s)       // two cursors: the right half's own highlight on J
        for ((c, at) in listOf('t' to (1 to 4), 'h' to (2 to 5), 'a' to (2 to 0), 'n' to (3 to 6), 'k' to (2 to 7), 's' to (2 to 1)))
            render("hl-$c", null, at, s = s)                  // each letter of "thanks" lit, for the Konami clips
        render("full-ru", null, 2 to 4, s = s.copy(language1 = Language.RUSSIAN))   // for the retro themes in other scripts
        render("full-tr", null, 2 to 4, s = s.copy(language1 = Language.TURKISH))
        render("tools-narrow", emptyList(), 9 to 0, widthDp = 300, s = s)   // the tool strip alone, no highlight
    }
}
