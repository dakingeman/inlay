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
 * Design review of the held-button look: every [HoldStyle.Look], filmed frame by frame from the app's own
 * drawing code at the Device tab's size (165 dp tall), plus the fully held state on every device.
 * Only runs when asked: touch app/build/visuals-hold. Output: app/build/hold/<look>/...
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h500dp-land-xxhdpi")
class HoldOptionsExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun film() {
        assumeTrue(File("build/visuals-hold").exists())
        val before = HoldStyle.look
        try {
            for (look in HoldStyle.Look.values()) {
                HoldStyle.look = look
                val dir = File("build/hold/${look.name.lowercase()}").apply { mkdirs() }
                // A short loop: rest, then A + LB + D-pad right go down and stay held, then let go
                val v = picture(DeviceKind.CONTROLLER, ButtonStyle.XBOX, false)
                val frame = attach(v)
                val keys = setOf("A", "LB", "Dright")
                val fps = 30; val total = 2100L; val down = 300L; val up = 1650L
                var t = 0L; var n = 0
                while (t < total) {
                    if (t == down - down % (1000L / fps) || (t >= down && v.held.isEmpty() && t < up)) { v.held = keys; v.holding = keys }
                    if (t >= up && v.held.isNotEmpty()) { v.held = emptySet(); v.holding = emptySet() }
                    save(v, File(dir, "f%03d.png".format(n++)))
                    idle(1000L / fps); t += 1000L / fps
                }
                frame.removeAllViews()
                // Fully held, on every device
                val pics = listOf(
                    Triple("controller-xbox", DeviceKind.CONTROLLER, ButtonStyle.XBOX), Triple("controller-playstation", DeviceKind.CONTROLLER, ButtonStyle.PLAYSTATION),
                    Triple("wide-switch", DeviceKind.WIDE, ButtonStyle.SWITCH), Triple("vertical-xbox", DeviceKind.VERTICAL, ButtonStyle.XBOX),
                    Triple("remote", DeviceKind.REMOTE, ButtonStyle.XBOX))
                for ((name, kind, st) in pics) for (state in listOf("pressed", "held")) {
                    val p = picture(kind, st, false); val f = attach(p)
                    val k = if (kind == DeviceKind.REMOTE) setOf("A", "Dright") else setOf("A", "B", "LB", "RT", "Dright", "START")
                    p.held = k; if (state == "held") p.holding = k
                    idle(900)
                    save(p, File(dir, "$name-$state.png")); f.removeAllViews()
                }
            }
        } finally { HoldStyle.look = before }
    }

    /** The chosen look at every size the app can show a picture, pressed vs held: build/hold/sizes/<size>-<picture>-<state>.png */
    @Test fun sizes() {
        assumeTrue(File("build/visuals-hold").exists())
        val dir = File("build/hold/sizes").apply { mkdirs() }
        val pics = listOf(Triple("controller-xbox", DeviceKind.CONTROLLER, ButtonStyle.XBOX), Triple("wide-playstation", DeviceKind.WIDE, ButtonStyle.PLAYSTATION),
            Triple("vertical-switch", DeviceKind.VERTICAL, ButtonStyle.SWITCH), Triple("remote", DeviceKind.REMOTE, ButtonStyle.XBOX))
        for (h in listOf(165f, 120f, 90f)) for ((name, kind, st) in pics) for (state in listOf("pressed", "held")) {
            val p = DevicePictureView(context).apply { accent = SettingsStyle.accent; maxHeightDp = h; tallBoost = false; show(kind, st, false) }
            val f = attach(p)
            val k = if (kind == DeviceKind.REMOTE) setOf("A", "Dright") else setOf("A", "LB", "Dright")
            p.held = k; if (state == "held") p.holding = k
            idle(900); save(p, File(dir, "${h.toInt()}-$name-$state.png")); f.removeAllViews()
        }
    }

    private fun picture(kind: DeviceKind, style: ButtonStyle, sticks: Boolean) = DevicePictureView(context).apply {
        accent = SettingsStyle.accent; maxHeightDp = 165f; tallBoost = false; show(kind, style, sticks)
    }

    private fun save(v: View, f: File) {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.parseColor("#0B0B0E")); v.draw(Canvas(b))
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }

    private fun attach(v: View): android.widget.FrameLayout {
        val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val dp = context.resources.displayMetrics.density
        frame.addView(v, android.widget.FrameLayout.LayoutParams((400 * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        idle(50); return frame
    }
}
