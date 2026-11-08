package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
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

/** Stills of the button setup, step by step, on the Odin's screen, for three controllers. Touch build/visuals-size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ButtonSetupExport {
    private val out = File("build/button-setup")
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private val A = KeyEvent.KEYCODE_BUTTON_A; private val B = KeyEvent.KEYCODE_BUTTON_B
    private val X = KeyEvent.KEYCODE_BUTTON_X; private val Y = KeyEvent.KEYCODE_BUTTON_Y

    private fun run(name: String, presses: List<Pair<Int, String>>, wDp: Int = 960, hDp: Int = 540) {
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val v = ButtonSetupView(host)
        host.setContentView(v)
        val w = (wDp * host.resources.displayMetrics.density).toInt(); val h = (hDp * host.resources.displayMetrics.density).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        fun shot(label: String) {
            // the host's window lays it out again at its own size: put it back to the screen's
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); v.draw(Canvas(bm))
            File(out, "$name-$label.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        idle(300); shot("0-start")
        presses.forEachIndexed { i, (code, label) -> v.press(code); idle(400); shot("${i + 1}-$label") }
    }

    @Test fun export() {
        assumeTrue(File("build/visuals-size").exists())
        out.mkdirs()
        // The Odin: Xbox letters, sent the usual way; bottom selects, right closes, then tries Delete and finishes
        run("odin", listOf(X to "left", Y to "top", B to "right", A to "bottom", A to "select", B to "close", X to "try-delete", A to "finish"))
        // A handheld set to a Nintendo layout (Retroid style): it sends them swapped; this person selects with the right one
        run("retroid", listOf(Y to "left", X to "top", A to "right", B to "bottom", A to "select", B to "close"))
        // Mistakes: the same button twice, a D-pad press twice, close = select
        run("oops", listOf(X to "left", X to "same-again", KeyEvent.KEYCODE_DPAD_UP to "dpad", KeyEvent.KEYCODE_DPAD_UP to "dpad-twice",
            Y to "top", B to "right", A to "bottom", A to "select", A to "close-same"))
        // Other screens: a small 4:3 handheld, and an upright phone
        run("small43", listOf(X to "left", Y to "top", B to "right", A to "bottom", A to "select", B to "close"), 640, 480)
        run("phone", listOf(X to "left", Y to "top", B to "right", A to "bottom", A to "select", B to "close"), 360, 780)
    }
}
