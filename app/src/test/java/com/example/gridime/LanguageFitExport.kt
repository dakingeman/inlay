package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Every language: Split on the Odin, and the normal keyboard on a small upright phone at Largest. Touch build/visuals-size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageFitExport {
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun render(name: String, s: KeyboardSettings) {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val v = KeyGridView(host).apply { clearsBehind = false }
        v.applySettings(s); v.setField(FieldKind.TEXT)
        val w = RuntimeEnvironment.getApplication().resources.displayMetrics.widthPixels
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight); idle(200)
        val bm = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); Canvas(bm).drawColor(Design.SURFACE_BG); v.draw(Canvas(bm))
        File("build/language-fit").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun splitOnTheOdin() {
        assumeTrue(File("build/visuals-size").exists())
        for (l in Language.values()) for (size in listOf(0, 2)) render("split-${l.name.lowercase()}-$size", KeyboardSettings(tutorialDone = true, split = true, language1 = l, sizeLevel = size))
    }
    @Test @Config(sdk = [34], qualifiers = "w360dp-h740dp-port-xxhdpi")
    fun uprightPhoneLargest() {
        assumeTrue(File("build/visuals-size").exists())
        for (l in Language.values()) render("phone-${l.name.lowercase()}", KeyboardSettings(tutorialDone = true, split = true, language1 = l, sizeLevel = 2))
    }
}
