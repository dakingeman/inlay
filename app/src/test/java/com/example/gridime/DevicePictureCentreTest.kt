package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * Every device picture sits in the middle of its space, measured on what's drawn: the Xbox controller's traced body
 * sat 28 units right of the others and looked off-centre on the Device tab (Odin test, 30 Oct 2026). The kit now
 * frames each controller round its own outline (kit.js); this keeps it so, for every device in every button style.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class DevicePictureCentreTest {
    @Test fun everyPictureIsCentred() {
        val context = RuntimeEnvironment.getApplication()
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val problems = mutableListOf<String>()
        for (kind in DeviceKind.values()) for (style in ButtonStyle.values()) {
            val v = DevicePictureView(context).apply { maxHeightDp = 200f; tallBoost = false; show(kind, style) }
            frame.removeAllViews(); frame.addView(v, android.widget.FrameLayout.LayoutParams(1000, -2))
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); b.eraseColor(0); v.draw(Canvas(b))
            var l = b.width; var r = -1
            for (y in 0 until b.height step 2) for (x in 0 until b.width) if (Color.alpha(b.getPixel(x, y)) > 40) { if (x < l) l = x; if (x > r) r = x }
            val off = (l + r) / 2f - b.width / 2f
            // within 0.5 % of the picture's own width: a pixel or two, the soft edges' rounding
            if (r < 0 || kotlin.math.abs(off) > (r - l) * 0.005f + 1f) problems += "$kind/$style is ${off.toInt()} px off centre"
            b.recycle()
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
