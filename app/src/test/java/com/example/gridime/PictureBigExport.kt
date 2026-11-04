package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Mock-up material: the real device pictures, drawn by the app's own code at a large size on a clear background,
 * so the Konami reward can show the picture "stepping forward" without blurring. Touch build/visuals-split.
 * Output: build/split/big/<kind>.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1200dp-h900dp-land-xxhdpi")
class PictureBigExport {
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun export() {
        assumeTrue(File("build/visuals-split").exists())
        val out = File("build/split/big").apply { mkdirs() }
        for ((kind, sticks) in listOf(DeviceKind.WIDE to true, DeviceKind.VERTICAL to false, DeviceKind.CONTROLLER to true)) {
            val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            val frame = android.widget.FrameLayout(host); host.setContentView(frame)
            val v = DevicePictureView(host).apply { maxHeightDp = 5000f; tallBoost = false }
            v.show(kind, ButtonStyle.XBOX, sticks)
            val w = if (kind == DeviceKind.VERTICAL) 1500 else 2600
            frame.addView(v, android.widget.FrameLayout.LayoutParams(w, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            idle(1500); v.requestLayout(); idle(500)
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            File(out, "${kind.file}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
