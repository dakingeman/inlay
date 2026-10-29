package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Every device picture with every button pressed, half held and fully held, drawn by the app's own code,
 * for the design audit of the press look. Only runs when asked: touch app/build/visuals-press.
 * Output: app/build/press/<device>-<state>-<size>.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class PressAudit {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private val all = listOf("A", "B", "X", "Y", "LB", "RB", "LT", "RT", "Dup", "START", "SELECT", "LS", "RS")
    private val remoteKeys = listOf("A", "B", "Dright")

    @Test fun everything() {
        assumeTrue(File("build/visuals-press").exists())
        val out = File("build/press").apply { mkdirs() }
        val pics = mutableListOf<Triple<String, DeviceKind, Pair<ButtonStyle, Boolean>>>()
        for (st in listOf(ButtonStyle.XBOX, ButtonStyle.PLAYSTATION, ButtonStyle.SWITCH)) {
            val n = st.name.lowercase()
            pics += Triple("controller-$n", DeviceKind.CONTROLLER, st to false)
            pics += Triple("wide-$n", DeviceKind.WIDE, st to false)
            pics += Triple("vertical-$n", DeviceKind.VERTICAL, st to false)
            pics += Triple("vertical-sticks-$n", DeviceKind.VERTICAL, st to true)
        }
        pics += Triple("remote", DeviceKind.REMOTE, ButtonStyle.XBOX to false)
        // The Device tab's size (165 dp tall), and a big one to read the details
        for ((label, h) in listOf("app" to 165f, "big" to 520f)) for ((name, kind, opt) in pics) {
            val keys = (if (kind == DeviceKind.REMOTE) remoteKeys else all).toSet()
            // pressed (before the ring starts), the ring half way, the ring closed
            for ((state, wait) in listOf("idle" to 60L, "pressed" to 60L, "half" to 120L + 190L, "full" to 900L)) {
                val v = DevicePictureView(context).apply {
                    accent = SettingsStyle.accent
                    maxHeightDp = h; tallBoost = false
                    show(kind, opt.first, opt.second)
                }
                val frame = attach(v, if (h > 300) 1400 else 600)
                if (state != "idle") { v.held = keys; v.holding = keys - setOf("LS", "RS") }
                idle(wait)
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.parseColor("#0B0B0E")); v.draw(Canvas(b))
                File(out, "$name-$state-$label.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                b.recycle(); frame.removeAllViews()
            }
        }
    }

    private fun attach(v: View, widthDp: Int): android.widget.FrameLayout {
        val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host)
        host.setContentView(frame)
        val dp = context.resources.displayMetrics.density
        frame.addView(v, android.widget.FrameLayout.LayoutParams((widthDp * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        idle(50)
        return frame
    }
}
