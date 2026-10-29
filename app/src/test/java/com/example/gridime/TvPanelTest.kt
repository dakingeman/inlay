package com.example.gridime

import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * TV mode draws the keyboard as a floating panel with rounded corners and one edge. The whole panel,
 * edge included, must sit inside the keyboard's own view, or the screen cuts its top off.
 * Checked at every keyboard size, with and without the number row, on a 1080p and a 4K TV.
 */
@RunWith(RobolectricTestRunner::class)
class TvPanelTest {
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun panelInsideOn1080p() = check()

    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xxxhdpi")
    fun panelInsideOn4k() = check()

    private fun check() {
        val context = RuntimeEnvironment.getApplication()
        val problems = mutableListOf<String>()
        for (size in 0..2) for (numberRow in listOf(false, true)) {
            val v = KeyGridView(context)
            v.applySettings(KeyboardSettings(tvMode = TvMode.ON, sizeLevel = size, numberRow = numberRow))
            v.setTvMode(true)
            val w = context.resources.displayMetrics.widthPixels
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val p = v.tvPanelForTest()
            val edge = context.resources.displayMetrics.density   // the 1 dp edge, drawn half outside the panel
            if (p.top - edge / 2 < 0f || p.left < 0f || p.right > v.width || p.bottom + edge / 2 > v.height)
                problems += "size $size, number row $numberRow: panel $p in a ${v.width} x ${v.height} view"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
