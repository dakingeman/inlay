package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
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

/**
 * The tour's guide badges (the button drawn big on the key a turn is about) are always whole: never past the
 * keyboard's edge, the top strip or a split window (Emre, 6 Nov: "some of them are still being cut off").
 * Every step of every guided turn, on a wide handheld, a 4:3 one and an upright phone, Split off and on.
 * With build/visuals-size there, it also saves each keyboard for a look.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourGuideBadgeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    @Before fun on() { TutorialActivity.yourTurn = true }
    @After fun off() { TutorialActivity.yourTurn = false }
    private val out = File("build/guide-badges")
    private val save = File("build/visuals-size").exists()

    private fun run(tag: String, split: Boolean) {
        if (save) out.mkdirs()
        KeyboardSettings(whatsNewSeen = WhatsNew.VERSION, split = split).save(context)
        val a = Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(1500)
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0))
        idle(900)
        val problems = mutableListOf<String>()
        for (group in 0 until 3) {
            if (group > 0) { a.skipLessonsForCheck(); idle(900); a.beginTurnForCheck(); idle(700) }
            for (l in 0 until a.lessonCountForCheck) {
                a.lessonJumpForCheck(l); idle(500)
                for (s in 0 until a.stepCountForCheck) {
                    a.lessonStepForCheck(s); idle(450)
                    val demo = a.demoForCheck
                    if (demo.width <= 0) continue
                    val bm = Bitmap.createBitmap(demo.width, demo.height, Bitmap.Config.ARGB_8888)
                    demo.draw(Canvas(bm))
                    val r = demo.guideBadgeForCheck ?: continue
                    val name = "${a.lessonForCheck} step ${s + 1}"
                    if (r.left < 0f || r.top < 0f || r.right > demo.width || r.bottom > demo.height)
                        problems += "$tag: $name: the badge $r goes past the keyboard (${demo.width}x${demo.height})"
                    if (save) File(out, "$tag-g$group-l$l-s$s.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun wide() = run("wide", false)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun wideSplit() = run("wide-split", true)
    @Test @Config(sdk = [34], qualifiers = "w640dp-h480dp-land-xhdpi") fun fourByThree() = run("43", false)
    @Test @Config(sdk = [34], qualifiers = "w360dp-h780dp-port-xhdpi") fun phone() = run("phone", false)
}
