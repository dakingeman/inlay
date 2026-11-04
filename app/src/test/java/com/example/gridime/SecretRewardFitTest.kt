package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every reward design, on every screen shape it can get (wide, square-ish, tall, the smallest), frame by frame:
 * the words always lie fully inside the screen with at least a pixel to spare (Emre, 4 Nov 2026: text was cut
 * off on the vertical handheld).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretRewardFitTest {
    @Test fun wordsAlwaysFit() {
        val v = SecretScreen(RuntimeEnvironment.getApplication())
        // real screens: the handheld and vertical handheld at full screen on the Odin, a phone, the lifted-out screen
        val screens = listOf(RectF(0f, 0f, 1000f, 560f), RectF(0f, 0f, 520f, 445f), RectF(0f, 0f, 300f, 420f), RectF(0f, 0f, 400f, 300f),
            RectF(0f, 0f, 1400f, 600f), RectF(0f, 0f, 260f, 520f))
        val bad = mutableListOf<String>()
        for (r in screens) {
            val (w, h) = v.artSize(r)
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bm)
            for (d in SecretScreen.DESIGNS.indices) {
                v.design = d
                var t = 0f
                while (t < SecretScreen.TOTAL_S) {
                    v.scene(c, w, h, t)
                    val b = v.textBounds
                    if (!b.isEmpty && (b.left < 1 || b.top < 1 || b.right > w - 1 || b.bottom > h - 1))
                        bad += "${SecretScreen.DESIGNS[d]} on ${w}x$h at ${"%.2f".format(t)} s: words at $b"
                    val ls = v.textLines
                    for (i in ls.indices) for (j in i + 1 until ls.size) if (android.graphics.Rect.intersects(ls[i], ls[j]) && ls[i] != ls[j])
                        bad += "${SecretScreen.DESIGNS[d]} on ${w}x$h at ${"%.2f".format(t)} s: words ${ls[i]} and ${ls[j]} overlap"
                    for (l in v.textLines) for (k in v.boxes) if (android.graphics.Rect.intersects(l, k))
                        bad += "${SecretScreen.DESIGNS[d]} on ${w}x$h at ${"%.2f".format(t)} s: words $l touch a keyboard $k"
                    t += 0.05f
                }
            }
        }
        assertTrue(bad.take(12).joinToString("\n"), bad.isEmpty())
    }
}
