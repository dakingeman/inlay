package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * What's new, "Show Inlay your buttons" (Emre, 8 Nov): the setup card plays itself on every size the slide can get,
 * from the upright handhelds' short card to a wide TV. At the end (every job showing, a controller's marks on the
 * buttons) nothing overlaps and nothing leaves the card. Writes pictures to build/setup-slide when that folder exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class SetupSlideTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val dp get() = context.resources.displayMetrics.density
    private val sizes = listOf(340 to 150, 300 to 140, 320 to 180, 420 to 180, 480 to 300, 560 to 340, 320 to 260, 330 to 420, 700 to 380)

    private fun played(w: Int, h: Int, marks: Int?): ButtonSetupView = ButtonSetupView(context).apply {
        stageOnly = true
        listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A).forEach { press(it) }
        press(KeyEvent.KEYCODE_BUTTON_A); press(KeyEvent.KEYCODE_BUTTON_B)
        if (marks != null) { captionTitle = ButtonSetupView.ANY; this.marks = listOf(listOf("X", "Y", "B", "A"), listOf("square", "triangle", "circle", "cross"), listOf("Y", "X", "A", "B"))[marks]; marksShown = 1f }
        val pw = (w * dp).toInt(); val ph = (h * dp).toInt()
        measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        layout(0, 0, pw, ph)
    }

    @Test fun nothingOverlapsAtAnySize() {
        val dir = File("build/setup-slide").takeIf { it.exists() }
        for ((w, h) in sizes) for (m in listOf(null, 1)) {
            val v = played(w, h, m)
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            dir?.let { d -> File(d, "${w}x$h-${m ?: "ticks"}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            val card = RectF(0f, 0f, v.width.toFloat(), v.height.toFloat())
            assertEquals("${w}x$h: four buttons", 4, v.buttonsForCheck.size)
            assertEquals("${w}x$h: four jobs", 4, v.tagsForCheck.size)
            val big = v.buttonsForCheck.first().width() / dp
            assertTrue("${w}x$h: the buttons are big enough to read (${"%.0f".format(big)} dp)", big >= 24f)
            v.tagsForCheck.forEachIndexed { i, t ->
                assertTrue("${w}x$h: tag $i inside the card ($t)", card.contains(t))
                v.buttonsForCheck.forEachIndexed { j, c -> assertTrue("${w}x$h: tag $i clear of button $j", !RectF.intersects(t, c)) }
                v.tagsForCheck.forEachIndexed { j, o -> if (j > i) assertTrue("${w}x$h: tags $i and $j apart", !RectF.intersects(t, o)) }
            }
            v.buttonsForCheck.forEach { c -> assertTrue("${w}x$h: the buttons sit under the title ($c, room ${v.stageForCheck})", c.top >= v.stageForCheck.top - 1f) }
        }
    }
}
