package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/** A clip waiting to be removed keeps its place, says "Removed", pastes nothing, and the guide offers X Undo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class RemovedClipViewTest {
    @Test fun theRemovedPlace() {
        val context = RuntimeEnvironment.getApplication()
        val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val v = KeyGridView(context)
        var pasted = -1
        v.listener = object : KeyGridView.Listener {
            override fun onText(text: String) {}
            override fun onDelete() {}
            override fun onEnter() {}
            override fun onTool(tool: Tool) {}
            override fun onClip(index: Int) { pasted = index }
        }
        v.applySettings(KeyboardSettings(tutorialDone = true))
        frame.addView(v, android.widget.FrameLayout.LayoutParams(context.resources.displayMetrics.widthPixels, -2))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        v.setClips(listOf("conker live and reloaded", "jet set radio future", "github.com/dakingeman/inlay", "ninja gaiden"))
        v.toggleClips()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        val index = v.focusedClipIndex()!!
        v.removedClip = index
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val log = mutableListOf<Pair<String, RectF>>(); v.drawLog = log; v.draw(Canvas(b)); v.drawLog = null
        File("build/ex").mkdirs(); File("build/ex/removed-clip.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("says Removed", log.any { it.first == "label:Removed" })
        v.pressSelected()
        assertEquals("a removed place pastes nothing", -1, pasted)
        assertTrue("the clipboard stays open", v.isClipsOpen())
    }
}
