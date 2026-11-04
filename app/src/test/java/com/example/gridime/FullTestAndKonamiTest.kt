package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * Try your buttons, full screen, and the Konami Code:
 *  - the full-screen picture fills the room and is drawn at its own size (not the small one stretched),
 *  - the preview keyboard steps aside for the test and comes back after,
 *  - the code is found only when entered as it should be (no mashing, no long pauses, wrong presses start over).
 * Writes screenshots to build/fulltest when that folder exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullTestAndKonamiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun field(a: Any, n: String) = SettingsActivity::class.java.getDeclaredField(n).apply { isAccessible = true }.get(a)
    private fun call(a: Any, n: String) = SettingsActivity::class.java.getDeclaredMethod(n).apply { isAccessible = true }.invoke(a)
    private fun tap(a: android.app.Activity, code: Int, holdMs: Long = 80, gapMs: Long = 300) {
        val t = android.os.SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); idle(holdMs)
        val u = android.os.SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, u, KeyEvent.ACTION_UP, code, 0)); idle(gapMs)
    }
    private val code = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A)
    private fun shot(a: android.app.Activity, name: String) {
        val dir = File("build/fulltest"); if (!dir.exists()) return
        val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun open(full: Boolean, picture: PictureChoice = PictureChoice.HANDHELD): SettingsActivity {
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = picture,
            testFullScreen = full, showPreview = false).save(context)
        return Robolectric.buildActivity(SettingsActivity::class.java).setup().get().also { idle(1000) }
    }

    private fun fullScreen(name: String, picture: PictureChoice) {
        val a = open(full = true, picture = picture)
        call(a, "startButtonTest"); idle(900)
        val overlay = field(a, "fullOverlay") as View; val big = field(a, "fullPicture") as DevicePictureView
        val small = field(a, "guidePicture") as DevicePictureView
        assertEquals(View.VISIBLE, overlay.visibility)
        assertEquals(1f, big.scaleX, 0.001f)                 // settled at its own size: drawn sharp, not scaled
        assertTrue("full picture ${big.height} vs small ${small.height}", big.height > small.height * 1.2f)
        tap(a, KeyEvent.KEYCODE_BUTTON_A, holdMs = 50, gapMs = 0);
        val t = android.os.SystemClock.uptimeMillis(); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X, 0)); idle(200)
        assertTrue("the full picture lights what's pressed", "X" in big.held)
        shot(a, name)
        a.dispatchKeyEvent(KeyEvent(t, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_X, 0))
        call(a, "endButtonTest"); idle(900)
        assertEquals(View.GONE, overlay.visibility); assertEquals(1f, small.alpha, 0.001f)
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun fullOdinHandheld() = fullScreen("odin-handheld", PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun fullOdinVertical() = fullScreen("odin-vertical", PictureChoice.VERTICAL)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun fullOdinController() = fullScreen("odin-controller", PictureChoice.CONTROLLER)
    @Test @Config(sdk = [34], qualifiers = "w1097dp-h617dp-land-280dpi") fun full1080Vertical() = fullScreen("1080p-vertical", PictureChoice.VERTICAL)
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun fullPhone() = fullScreen("phone-handheld", PictureChoice.HANDHELD)

    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun keyboardStepsAside() {
        val a = open(full = false)
        call(a, "togglePreview"); idle(900)
        assertTrue((field(a, "settings") as KeyboardSettings).showPreview)
        call(a, "startButtonTest"); idle(900)
        assertFalse("keyboard away during the test", (field(a, "settings") as KeyboardSettings).showPreview)
        shot(a, "keyboard-aside")
        call(a, "endButtonTest"); idle(900)
        assertTrue("keyboard back after", (field(a, "settings") as KeyboardSettings).showPreview)
    }

    private fun foundAfter(presses: SettingsActivity.() -> Unit): Boolean {
        val a = open(full = false); call(a, "startButtonTest"); idle(300)
        a.presses(); idle(200)
        return a.secretsFound > 0
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun codeFound() =
        assertTrue(foundAfter { code.forEach { tap(this, it) } })
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun wrongPressStartsOver() =
        assertFalse(foundAfter { code.take(5).forEach { tap(this, it) }; tap(this, KeyEvent.KEYCODE_BUTTON_X); code.drop(5).forEach { tap(this, it) } })
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun longPauseStartsOver() =
        assertFalse(foundAfter { code.take(4).forEach { tap(this, it) }; idle(3500); code.drop(4).forEach { tap(this, it) } })
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun mashingDoesNotCount() = assertFalse(foundAfter {
        // up held while the rest is pressed
        val t = android.os.SystemClock.uptimeMillis(); dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 0))
        code.forEach { tap(this, it) }
    })
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun startsOverAfterAFalseStart() =
        assertTrue(foundAfter { tap(this, KeyEvent.KEYCODE_DPAD_UP); tap(this, KeyEvent.KEYCODE_DPAD_LEFT); code.forEach { tap(this, it) } })

    /** The reward plays, ends the test by itself, and Look gets its sign (drawn at each step for Emre). */
    private fun reward(name: String, full: Boolean, picture: PictureChoice) {
        val a = open(full = full, picture = picture); call(a, "startButtonTest"); idle(900)
        code.forEach { tap(a, it) }
        assertEquals(1, a.secretsFound)
        idle(1500); shot(a, "reward-$name-1")
        idle(2500); shot(a, "reward-$name-2")
        idle((SecretScreen.TOTAL_S * 1000).toLong() - 2500)
        assertFalse("the test ended by itself", field(a, "testing") as Boolean)
        val tabs = field(a, "tabs") as TabBar
        assertEquals("Look is marked", 1, tabs.marked)
        idle(300); shot(a, "reward-$name-3a")
        idle(2000); shot(a, "reward-$name-3")
        assertEquals(1f, tabs.signAlpha, 0.001f)
        // with the keyboard up the sign is a dot by Look's folded bar
        call(a, "togglePreview"); idle(1200); shot(a, "reward-$name-4")
        assertTrue((field(a, "settings") as KeyboardSettings).secretNew)
        call(a, "endButtonTest")
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun rewardOnScreen() = reward("onscreen", true, PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun rewardLifted() = reward("lifted", false, PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun rewardController() = reward("controller", true, PictureChoice.CONTROLLER)
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun rewardPhone() = reward("phone", true, PictureChoice.VERTICAL)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun anyPressSkipsReward() {
        val a = open(full = true); call(a, "startButtonTest"); idle(900)
        code.forEach { tap(a, it) }; idle(800)
        tap(a, KeyEvent.KEYCODE_BUTTON_X); idle(1500)
        assertFalse("skipped: the test is over", field(a, "testing") as Boolean)
    }

    /** Frames of each reward design at 20 fps, drawn by the real view (for Emre's comparison sheet). */
    private fun rewardFrames(name: String, full: Boolean, picture: PictureChoice, designs: List<Int> = SecretScreen.DESIGNS.indices.toList()) {
        val root = File("build/rewardframes"); if (!root.exists()) return
        for (d in designs) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = picture,
                testFullScreen = full, showPreview = false, labReward = d).save(context)
            val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get().also { idle(1000) }
            call(a, "startButtonTest"); idle(900)
            code.forEach { tap(a, it) }
            val dir = File(root, "$name-$d"); dir.deleteRecursively(); dir.mkdirs()
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); val bc = Canvas(b)
            for (f in 0 until ((SecretScreen.TOTAL_S + 1.2f) * 20).toInt()) {
                b.eraseColor(0); v.draw(bc)
                File(dir, "%04d.png".format(f)).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                idle(50)
            }
            call(a, "endButtonTest"); b.recycle(); a.finish(); idle(500)
        }
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun rewardFramesOdin() = rewardFrames("odin", true, PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun rewardFramesVertical() = rewardFrames("vertical", true, PictureChoice.VERTICAL)

    /** Look and Sound & haptics with the Retro collection found, each retro theme on the keyboard (for Emre). */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun retroLookShots() {
        val dir = File("build/fulltest"); if (!dir.exists()) return
        for ((k, tone) in listOf(0 to 0, 1 to 100, 2 to 101, 3 to 102)) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD,
                showPreview = false, retroUnlocked = true, secretNew = k == 0, secretRowNew = k == 0, secretSoundNew = k == 1, toneIndex = tone).save(context)
            val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get().also { idle(1200) }
            val tabs = field(a, "tabs") as TabBar
            tabs.onTapForTest(1); idle(1500)
            shot(a, "retro-look-$k")
            call(a, "togglePreview"); idle(1200); shot(a, "retro-kb-$k"); call(a, "togglePreview"); idle(900)
            if (k == 1) { tabs.onTapForTest(3); idle(1500); shot(a, "retro-sound-$k") }
            a.finish(); idle(300)
        }
    }

    /** What the new rows look like on the Device tab (for Emre). */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun rowsShot() {
        if (!File("build/fulltest").exists()) return
        val a = open(full = false)
        @Suppress("UNCHECKED_CAST") val rows = field(a, "focusableRows") as List<View>
        fun texts(v: View): List<String> = if (v is android.widget.TextView) listOf(v.text.toString()) else if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
        val row = rows.first { texts(it).any { t -> t == "Full screen" } }
        row.requestFocusFromTouch(); idle(1200); shot(a, "rows")
    }

    /**
     * The way back, frame by frame, drawn by the app's real views at the animation's own values (Robolectric
     * doesn't step view animations, so the frames are set here with the same end values and curve): the big drawing
     * lands exactly on the small one, so swapping them changes no pixel of the picture, and the keyboard waits.
     */
    private fun wayBack(name: String, keyboard: Boolean) {
        val a = open(full = true)
        if (keyboard) { call(a, "togglePreview"); idle(900) }
        val overlay = field(a, "fullOverlay") as View; val backdrop = field(a, "fullBackdrop") as View
        val big = field(a, "fullPicture") as DevicePictureView; val small = field(a, "guidePicture") as DevicePictureView
        val dir = File("build/fulltest/frames-$name"); val record = File("build/fulltest").exists()
        if (record) { dir.deleteRecursively(); dir.mkdirs() }
        var frame = 0
        fun grab(): Bitmap { val v = a.window.decorView; val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); return b }
        fun save(b: Bitmap = grab()) { if (record) File(dir, "%04d.png".format(frame++)).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        call(a, "startButtonTest"); idle(900)
        assertFalse("keyboard away while testing", (field(a, "settings") as KeyboardSettings).showPreview)
        repeat(10) { save() }
        val m = a.smallFromFull()!!
        big.animate().cancel(); backdrop.animate().cancel()
        val curve = Design.EASE_STANDARD; val frames = 400 / 16     // FULL_TEST_MS, 60 frames a second
        var last: Bitmap? = null
        for (f in 1..frames) {
            val u = curve.getInterpolation(f / frames.toFloat())
            big.pivotX = 0f; big.pivotY = 0f
            big.scaleX = 1f + (m[0] - 1f) * u; big.scaleY = big.scaleX; big.translationX = m[1] * u; big.translationY = m[2] * u
            backdrop.alpha = 1f - u
            last = grab(); save(last)
        }
        // the swap, as the app does it at the end of the animation
        small.alpha = 1f; overlay.visibility = View.GONE
        val after = grab(); save(after)
        // how many picture pixels change at the swap (the bounce Emre saw would show up here)
        val sr = small.drawnRect()!!; val loc = IntArray(2); small.getLocationInWindow(loc)
        // compared in 3 x 3 blocks: a line that lands a fraction of a pixel differently (anti-aliasing) isn't a jump
        var changed = 0; var total = 0
        fun block(bm: Bitmap, x: Int, y: Int): Int { var t = 0
            for (j in 0 until 3) for (i in 0 until 3) { val p = bm.getPixel(x + i, y + j); t += android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p) }
            return t / 9 }
        var y = (loc[1] + sr.top).toInt()
        while (y < (loc[1] + sr.bottom).toInt() - 3) { var x = (loc[0] + sr.left).toInt()
            while (x < (loc[0] + sr.right).toInt() - 3) { total++; if (kotlin.math.abs(block(last!!, x, y) - block(after, x, y)) > 90) changed++; x += 3 }
            y += 3 }
        File("build/fulltest").takeIf { it.exists() }?.let { File(it, "swap-$name.txt").writeText("$changed of $total picture pixels change at the swap\n") }
        assertTrue("$changed of $total picture blocks jump at the swap", changed < total * 0.005)
        assertFalse("the keyboard waits for the picture", (field(a, "settings") as KeyboardSettings).showPreview)
        call(a, "endButtonTest"); idle(1200)     // the app's own way back: the keyboard comes back after it
        repeat(10) { save() }
        if (keyboard) assertTrue("keyboard back after", (field(a, "settings") as KeyboardSettings).showPreview)
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun wayBackPlain() = wayBack("plain", false)
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun wayBackKeyboard() = wayBack("keyboard", true)

    /** A D-pad that reports as a hat keeps the test going, like any button (it used to time out mid-code). */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun hatKeepsTestAlive() {
        val a = open(full = false); call(a, "startButtonTest"); idle(300)
        fun hat(x: Float, y: Float) {
            val p = android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = android.view.MotionEvent.TOOL_TYPE_UNKNOWN }
            val c = android.view.MotionEvent.PointerCoords().apply { setAxisValue(android.view.MotionEvent.AXIS_HAT_X, x); setAxisValue(android.view.MotionEvent.AXIS_HAT_Y, y) }
            val t = android.os.SystemClock.uptimeMillis()
            val e = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_MOVE, 1, arrayOf(p), arrayOf(c), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_JOYSTICK, 0)
            a.dispatchGenericMotionEvent(e); e.recycle()
        }
        repeat(14) { hat(0f, -1f); idle(150); hat(0f, 0f); idle(850) }    // 14 s of D-pad presses, longer than the 8 s timeout
        assertTrue("still testing after 14 s of D-pad presses", field(a, "testing") as Boolean)
        idle(8500)
        assertFalse("ends on its own after a quiet spell", field(a, "testing") as Boolean)
    }
}
