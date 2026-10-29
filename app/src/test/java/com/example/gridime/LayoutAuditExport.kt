package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.os.Looper
import android.view.View
import org.json.JSONObject
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
import kotlin.math.max

/**
 * Every device picture, every key lit and fully held, neighbouring keys held together, sticks clicked in
 * and pushed each way, at the Device tab's size and at the smallest size the tour uses. Drawn by the app's
 * own code for the layout audit (tools/audit/layout_audit.py measures the clearances).
 * Only runs when asked: touch app/build/visuals-layout. Output: app/build/layout/<size>/<picture>/...
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h600dp-land-xxhdpi")
class LayoutAuditExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun export() {
        assumeTrue(File("build/visuals-layout").exists())
        val pics = mutableListOf<Triple<String, DeviceKind, Pair<ButtonStyle, Boolean>>>()
        for (st in listOf(ButtonStyle.XBOX, ButtonStyle.PLAYSTATION, ButtonStyle.SWITCH)) {
            val n = st.name.lowercase()
            pics += Triple("controller-$n", DeviceKind.CONTROLLER, st to false)
            pics += Triple("wide-$n", DeviceKind.WIDE, st to false)
            pics += Triple("vertical-$n", DeviceKind.VERTICAL, st to false)
            pics += Triple("vertical-sticks-$n", DeviceKind.VERTICAL, st to true)
        }
        pics += Triple("remote", DeviceKind.REMOTE, ButtonStyle.XBOX to false)
        val dp = context.resources.displayMetrics.density
        for (h in listOf(165, 120)) for ((name, kind, opt) in pics) {
            val dir = File("build/layout/$h/$name").apply { mkdirs() }
            val probe = view(kind, opt, h.toFloat())
            val f0 = attach(probe)
            val geo = probe.geoForTest() ?: continue
            save(probe, File(dir, "idle.png"))
            f0.removeAllViews()
            val keys = geo.keys().asSequence().filter { !it.startsWith("_") }.toList()
            val apiKeys = if (kind == DeviceKind.REMOTE) remoteKeys(keys) else keys.associateWith { it }
            val held = apiKeys.filterValues { it != "LS" && it != "RS" }
            // each key, fully held
            for ((pk, k) in held) shot(kind, opt, h, setOf(k), File(dir, "held-$pk.png"))
            // neighbours held together: any two keys whose shapes are within 12 dp at this size
            val s = probe.pictureScaleForTest()
            val ks = held.keys.toList()
            for (i in ks.indices) for (j in i + 1 until ks.size) {
                if (gap(geo.getJSONObject(ks[i]), geo.getJSONObject(ks[j])) * s < 12 * dp)
                    shot(kind, opt, h, setOf(held[ks[i]]!!, held[ks[j]]!!), File(dir, "pair-${ks[i]}+${ks[j]}.png"))
            }
            // sticks: clicked in, and pushed each way
            for (st in listOf("LS", "RS").filter { it in keys }) {
                shot(kind, opt, h, setOf(st), File(dir, "stick-$st-in.png"), hold = false)
                for ((d, p) in listOf("left" to PointF(-1f, 0f), "right" to PointF(1f, 0f), "up" to PointF(0f, -1f), "down" to PointF(0f, 1f)))
                    shot(kind, opt, h, setOf(st), File(dir, "stick-$st-$d.png"), hold = false, push = mapOf(st to p))
            }
        }
    }

    /** The remote lights OK for A and Back for B; the D-pad arms keep their names. */
    private fun remoteKeys(keys: List<String>): Map<String, String> = keys.associateWith { when (it) { "OK" -> "A"; "BACK" -> "B"; else -> it } }

    private fun gap(a: JSONObject, b: JSONObject): Float {
        fun f(o: JSONObject, k: String) = o.getDouble(k).toFloat()
        return max(max(f(a, "x") - (f(b, "x") + f(b, "w")), f(b, "x") - (f(a, "x") + f(a, "w"))), max(f(a, "y") - (f(b, "y") + f(b, "h")), f(b, "y") - (f(a, "y") + f(a, "h"))))
    }

    private fun view(kind: DeviceKind, opt: Pair<ButtonStyle, Boolean>, h: Float) = DevicePictureView(context).apply {
        accent = SettingsStyle.accent; maxHeightDp = h; tallBoost = false; show(kind, opt.first, opt.second)
    }

    private fun shot(kind: DeviceKind, opt: Pair<ButtonStyle, Boolean>, h: Int, keys: Set<String>, out: File, hold: Boolean = true, push: Map<String, PointF> = emptyMap()) {
        val v = view(kind, opt, h.toFloat()); val f = attach(v)
        v.held = keys; if (hold) v.holding = keys; if (push.isNotEmpty()) v.stickPush = push
        idle(900); save(v, out); f.removeAllViews()
    }

    private fun save(v: View, f: File) {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.parseColor("#0B0B0E")); v.draw(Canvas(b))
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }

    // one screen for every shot (a new one each time runs out of memory)
    private val frame by lazy {
        val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        android.widget.FrameLayout(host).also { host.setContentView(it) }
    }
    private fun attach(v: View): android.widget.FrameLayout {
        frame.removeAllViews()
        val dp = context.resources.displayMetrics.density
        frame.addView(v, android.widget.FrameLayout.LayoutParams((800 * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        idle(50); return frame
    }
}
