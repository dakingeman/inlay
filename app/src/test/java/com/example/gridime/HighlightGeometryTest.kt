package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
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
 * The guard for "nothing clips, touches or overlaps" on the keyboard (design audit, 28 Oct 2026).
 * Every key is highlighted in turn, in every highlight style, key shape, button platform, field kind,
 * layer and in TV mode, and the drawing is measured:
 *  - the highlight stays within a quarter of the key gap of its own key (never reaches a neighbour)
 *  - every corner badge sits inside its key, and on the highlighted key inside the highlight
 * Found by Emre on the Odin: Lift on Chrome's address bar pushed the space bar's Y badge past the key.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class HighlightGeometryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun view(s: KeyboardSettings, field: FieldKind, tv: Boolean, widthDp: Int = 827): KeyGridView {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host)
        host.setContentView(frame)
        val v = KeyGridView(context)
        v.applySettings(s)
        v.setField(field)
        if (tv) v.setTvMode(true)
        val dp = context.resources.displayMetrics.density
        frame.addView(v, android.widget.FrameLayout.LayoutParams((widthDp * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        idle(100)
        return v
    }

    private fun RectF.within(o: RectF, slack: Float) =
        left >= o.left - slack && top >= o.top - slack && right <= o.right + slack && bottom <= o.bottom + slack

    private fun checkAll(v: KeyGridView, tag: String, problems: MutableList<String>) {
        val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val reach = v.gapForTest / 4f + 0.5f
        for (row in 0 until v.rowCountForTest()) for (col in 0 until v.rowSizeForTest(row)) {
            val key = v.focusForTest(row, col) ?: continue
            val log = mutableListOf<Pair<String, RectF>>()
            v.drawLog = log
            v.draw(canvas)
            v.drawLog = null
            val pill = log.firstOrNull { it.first == "pill" }?.second ?: continue
            if (!pill.within(key, reach)) problems += "$tag r$row c$col: highlight $pill reaches past its key $key"
            log.filter { it.first == "badge" }.map { it.second }
                .filter { key.contains(it.centerX(), it.centerY()) }
                .forEach { b ->
                    if (!b.within(key, 0.5f)) problems += "$tag r$row c$col: badge $b outside its key $key"
                    if (!b.within(pill, 0.5f)) problems += "$tag r$row c$col: badge $b outside the highlight $pill"
                }
            // The key's icon keeps clear of its badge
            val badge = log.filter { it.first == "badge" }.map { it.second }.firstOrNull { key.contains(it.centerX(), it.centerY()) }
            val icon = log.filter { it.first == "icon" }.map { it.second }.firstOrNull { key.contains(it.centerX(), it.centerY()) }
            if (badge != null && icon != null && RectF.intersects(badge, icon)) problems += "$tag r$row c$col: icon $icon runs into badge $badge"
        }
        bmp.recycle()
    }

    @Test
    fun highlightAndBadgesStayInsideTheirKey() = runAll(827)

    /** The Retroid Pocket Classic's 3.92-inch, 31:27 screen: the narrowest keys there are. */
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi")
    fun onASmallVerticalHandheld() = runAll(472)

    private fun runAll(widthDp: Int) {
        val problems = mutableListOf<String>()
        for (style in HighlightStyle.values()) for (shape in KeyShape.values()) for (buttons in ButtonStyle.values())
            for (field in listOf(FieldKind.TEXT, FieldKind.URL, FieldKind.NUMBER)) for (tv in listOf(false, true)) {
                val s = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = buttons,
                    showHints = true, comboHints = true, wordSuggestions = true, holdAccents = true,
                    highlightStyle = style, keyShape = shape)
                val v = view(s, field, tv, widthDp)
                val tag = "$style/$shape/$buttons/$field${if (tv) "/TV" else ""}"
                checkAll(v, tag, problems)
                if (!tv) { v.toggleLayer(fromKey = true); idle(50); checkAll(v, "$tag/next layer", problems) }
            }
        assertTrue("${problems.size} problems:\n" + problems.take(25).joinToString("\n"), problems.isEmpty())
    }
}
