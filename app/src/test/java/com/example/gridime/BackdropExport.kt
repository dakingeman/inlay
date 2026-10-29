package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The backdrop at every screen shape Inlay meets, full size, for the backdrop audit. Touch build/visuals-backdrop. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BackdropExport {
    @Test fun shapes() {
        assumeTrue(File("build/visuals-backdrop").exists())
        val dir = File("build/backdrop").apply { mkdirs() }
        val shapes = listOf("odin-1920x1080" to (1920 to 1080), "phone-portrait-1080x2400" to (1080 to 2400), "phone-land-2400x1080" to (2400 to 1080),
            "fourthree-1024x768" to (1024 to 768), "square-1240x1080" to (1240 to 1080), "tablet-2560x1600" to (2560 to 1600), "tv4k-3840x2160" to (3840 to 2160))
        for ((name, wh) in shapes) for (wn in listOf(false, true)) {
            val b = Bitmap.createBitmap(wh.first, wh.second, Bitmap.Config.ARGB_8888)
            Backdrop(Design.BRAND_ACCENT, wn).apply { setBounds(0, 0, wh.first, wh.second) }.draw(Canvas(b))
            File(dir, "$name-${if (wn) "whatsnew" else "tour"}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }
    }
}
