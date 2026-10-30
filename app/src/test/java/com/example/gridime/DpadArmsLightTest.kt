package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * Every lit D-pad arm shows its light, whichever other arms are lit with it, on every device picture. Each arm's
 * light came with its own dark copy of the cross, so a later arm's copy covered an earlier arm's light and left only
 * its ring (Odin test, 30 Oct 2026). And a new D-pad flash ends the last one: a D-pad presses one way at a time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class DpadArmsLightTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val arms = listOf("Dup", "Ddown", "Dleft", "Dright")
    private val green = Color.rgb(0, 255, 0)

    @Test fun everyLitArmShowsItsLight() {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val problems = mutableListOf<String>(); var checked = 0
        val pictures = listOf(DeviceKind.CONTROLLER, DeviceKind.WIDE, DeviceKind.VERTICAL).flatMap { k -> ButtonStyle.values().map { k to it } }
        // every set of two or more arms (single arms always worked)
        val sets = (1 until 16).map { m -> arms.filterIndexed { i, _ -> m shr i and 1 == 1 }.toSet() }.filter { it.size >= 2 }
        for ((kind, style) in pictures) {
            val v = DevicePictureView(context).apply { accent = green; maxHeightDp = 300f; tallBoost = false; show(kind, style) }
            frame.removeAllViews(); frame.addView(v, android.widget.FrameLayout.LayoutParams(1200, -2)); idle(100)
            val geo = v.geoForTest() ?: continue
            for (lit in sets) {
                v.held = lit; idle(50)
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); b.eraseColor(Color.BLACK); v.draw(Canvas(b))
                val s = v.pictureScaleForTest()
                val box = v.boxForTest()
                for (arm in lit) {
                    val g = geo.getJSONObject(arm)
                    val x0 = box.first + g.getDouble("x").toFloat() * s; val y0 = box.second + g.getDouble("y").toFloat() * s
                    val w = g.getDouble("w").toFloat() * s; val h = g.getDouble("h").toFloat() * s
                    // the middle half of the arm must be mostly your colour
                    var hit = 0; var all = 0
                    for (y in (y0 + h / 4).toInt() until (y0 + 3 * h / 4).toInt()) for (x in (x0 + w / 4).toInt() until (x0 + 3 * w / 4).toInt()) {
                        val c = b.getPixel(x.coerceIn(0, b.width - 1), y.coerceIn(0, b.height - 1)); all++
                        if (Color.green(c) > 200 && Color.red(c) < 80 && Color.blue(c) < 80) hit++
                    }
                    checked++
                    if (all == 0 || hit < all * 0.6) problems += "$kind/$style, lit $lit: $arm shows ${if (all == 0) 0 else hit * 100 / all} % of its light"
                }
                b.recycle()
            }
        }
        println("d-pad arms: $checked lit arms checked, ${problems.size} without their light")
        assertTrue(problems.joinToString("\n"), problems.isEmpty() && checked > 0)
    }

    @Test fun aNewDirectionEndsTheLast() {
        val v = DevicePictureView(context).apply { show(DeviceKind.WIDE, ButtonStyle.XBOX) }
        v.flash("Dup"); v.flash("Dright")
        assertTrue("lit: ${v.litNow()}", v.litNow() == setOf("Dright"))
    }
}
