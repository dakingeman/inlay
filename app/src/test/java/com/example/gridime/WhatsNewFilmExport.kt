package com.example.gridime

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
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

/** What's new as films: every page, a frame every [STEP] ms, for review (touch build/visuals-wn). Output: build/wn-film/<page>/ */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhatsNewFilmExport {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))


    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun odin() {
        assumeTrue(File("build/visuals-wn").exists())
        val only = File("build/visuals-wn").readText().trim().split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, picture = PictureChoice.HANDHELD).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(300)
        repeat(t.pageCheck().size) { i ->
            if (only.isEmpty() || i in only) {
                val dir = File("build/wn-film/$i").apply { deleteRecursively(); mkdirs() }
                val v = t.window.decorView
                for (f in 0 until FRAMES) {
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
                    File(dir, "f%03d.png".format(f)).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle()
                    idle(STEP)
                }
            }
            val now = SystemClock.uptimeMillis()
            t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
            idle(200)
        }
    }

    /**
     * The release clip (touch build/visuals-wn-clip): What's new as a player sees it on an AYN Odin 2 Portal (1920 x 1080),
     * 30 frames a second, each page played once through its scene, then A to the next page, transitions included.
     * Output: build/wn-clip/%04d.png and pages.json (title and first frame of each page).
     */
    @Test @Config(sdk = [34], qualifiers = "w835dp-h469dp-land-368dpi") fun clip() {
        assumeTrue(File("build/visuals-wn-clip").exists())
        val dir = File("build/wn-clip").apply { deleteRecursively(); mkdirs() }
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD).save(context)
        val t = Robolectric.buildActivity(TutorialActivity::class.java, Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        val pages = t.pageWordsCheck()
        t.hideControlsForFilm(); idle(300)                      // a film, not the app: no Next / Back / Close prompts
        val v = t.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        var f = 0; var clock = 0L
        fun frame() { b.eraseColor(android.graphics.Color.BLACK); v.draw(c)
            File(dir, "%04d.png".format(f)).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f++; val next = f * 1000L / 30; idle(next - clock); clock = next }
        val log = org.json.JSONArray()
        pages.forEachIndexed { i, (title, _, scene) ->
            log.put(org.json.JSONObject().put("title", title).put("scene", scene).put("from", f))
            val loop = scene?.let { WhatsNewShowcase.Scene.valueOf(it).loopMs } ?: 3000L
            val dwell = loop + if (i == 0) 1400L else 900L
            repeat((dwell * 30 / 1000).toInt()) { frame() }
            if (i < pages.size - 1) { val now = SystemClock.uptimeMillis()
                t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); t.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0)) }
        }
        File(dir, "pages.json").writeText(org.json.JSONObject().put("w", v.width).put("h", v.height).put("frames", f).put("pages", log).toString(1))
        t.finish()
    }

    private companion object { const val STEP = 100L; const val FRAMES = 140 }
}
