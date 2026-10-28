package com.example.gridime

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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
 * Screenshots of the new screens, drawn by Android's real graphics code (Robolectric, native mode),
 * at the Odin 2 Portal's size. Not a pass/fail test: it writes PNGs to app/build/screens to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h464dp-land-xhdpi")
class ScreenshotTest {

    private val out = File("build/screens").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()

    private fun save(view: View, name: String) {
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun press(activity: Activity, keyCode: Int) {
        val t = SystemClock.uptimeMillis()
        activity.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        activity.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        idle(250)
    }

    private fun rows(v: View): List<SettingRow> = when (v) {
        is SettingRow -> listOf(v)
        is android.view.ViewGroup -> (0 until v.childCount).flatMap { rows(v.getChildAt(it)) }
        else -> emptyList()
    }

    /** Puts the focus on a row, as the D-pad would. */
    private fun focus(activity: Activity, which: (SettingRow) -> Boolean) {
        rows(activity.window.decorView).first(which).requestFocusFromTouch()
        idle(600)
    }

    private fun setUp(change: (KeyboardSettings) -> KeyboardSettings) =
        change(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION)).save(context)

    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun deviceTab() {
        listOf(PictureChoice.VERTICAL to false, PictureChoice.VERTICAL to true, PictureChoice.REMOTE to false, PictureChoice.HANDHELD to false).forEach { (pic, sticks) ->
            setUp { it.copy(picture = pic, verticalSticks = sticks, showPreview = false) }
            val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            idle(600)
            focus(a) { it is ChoiceRow }
            save(a.window.decorView, "devtab-${pic.name.lowercase()}${if (sticks) "-sticks" else ""}")
        }
    }

    /** Every What's new and tour page, for the handheld, a vertical handheld without sticks and a remote. */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun releasePages() {
        listOf(Triple(PictureChoice.HANDHELD, ButtonStyle.XBOX, "h"), Triple(PictureChoice.VERTICAL, ButtonStyle.SWITCH, "v"),
            Triple(PictureChoice.REMOTE, ButtonStyle.XBOX, "r")).forEach { (pic, style, tag) ->
            setUp { it.copy(buttonStyle = style, picture = pic) }
            val wn = Robolectric.buildActivity(TutorialActivity::class.java,
                Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
            repeat(wn.pageCheck().size) { i -> idle(if (i == 0) 3200 else 3600); save(wn.window.decorView, "rel-$tag-new-$i"); press(wn, KeyEvent.KEYCODE_BUTTON_A) }
            val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
            repeat(tour.pageCheck().size) { i -> idle(if (i == 0) 1500 else 3000); save(tour.window.decorView, "rel-$tag-tour-$i"); press(tour, KeyEvent.KEYCODE_BUTTON_A) }
        }
    }

    /** The welcome page's device showcase, Hold RT's word jumps, and Copy before the clipboard. */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun tourDetails() {
        setUp { it.copy(buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD) }
        val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        repeat(6) { i -> idle(3500); save(tour.window.decorView, "td-welcome-$i") }
        repeat(3) { press(tour, KeyEvent.KEYCODE_BUTTON_A) }                // to Hold RT for more
        idle(2600); save(tour.window.decorView, "td-holdrt-a"); idle(1800); save(tour.window.decorView, "td-holdrt-b")
        repeat(2) { press(tour, KeyEvent.KEYCODE_BUTTON_A) }                // to Select, copy and paste
        (0 until 14).forEach { i -> idle(700); save(tour.window.decorView, "td-copy-$i") }
    }

    /** The loop bar filling through one demo, then starting over: frames for checking by eye. */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun loopBar() {
        setUp { it.copy(buttonStyle = ButtonStyle.PLAYSTATION, picture = PictureChoice.HANDHELD) }
        val wn = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        repeat(2) { press(wn, KeyEvent.KEYCODE_BUTTON_A) }                // Tidies up as you type
        (0 until 24).forEach { i -> idle(1000); save(wn.window.decorView, "lb-wide-$i") }
        setUp { it.copy(buttonStyle = ButtonStyle.SWITCH, picture = PictureChoice.VERTICAL, verticalSticks = true) }
        val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        idle(1000); save(tour.window.decorView, "lb-vert-welcome")
        press(tour, KeyEvent.KEYCODE_BUTTON_A); idle(2500); save(tour.window.decorView, "lb-vert-type")
    }

    /** Held buttons fill a ring inside, sticks slide or click in: frames for checking by eye. */
    @Test
    fun heldAndSticks() {
        data class Case(val name: String, val kind: DeviceKind, val style: ButtonStyle, val sticks: Boolean, val held: Set<String>, val holding: Set<String>,
                        val push: Map<String, android.graphics.PointF> = emptyMap(), val sway: Map<String, Boolean> = emptyMap(), val waitMs: Long = 800)
        val p = { x: Float, y: Float -> android.graphics.PointF(x, y) }
        val cases = listOf(
            Case("hold-half-r2", DeviceKind.WIDE, ButtonStyle.PLAYSTATION, false, setOf("RT"), setOf("RT"), waitMs = 310),
            Case("hold-r2-r1", DeviceKind.WIDE, ButtonStyle.PLAYSTATION, false, setOf("RT", "RB"), setOf("RT")),
            Case("hold-a", DeviceKind.WIDE, ButtonStyle.PLAYSTATION, false, setOf("A"), setOf("A")),
            Case("hold-ctrl-xbox", DeviceKind.CONTROLLER, ButtonStyle.XBOX, false, setOf("RT", "A"), setOf("RT")),
            Case("hold-vertical-b", DeviceKind.VERTICAL, ButtonStyle.SWITCH, false, setOf("A", "Dleft"), setOf("A", "Dleft")),
            Case("hold-remote-ok", DeviceKind.REMOTE, ButtonStyle.XBOX, false, setOf("A"), setOf("A")),
            Case("hold-vertical-rt-rb", DeviceKind.VERTICAL, ButtonStyle.XBOX, true, setOf("RT", "RB"), setOf("RT", "RB")),
            Case("hold-vertical-half-lt", DeviceKind.VERTICAL, ButtonStyle.PLAYSTATION, false, setOf("LT"), setOf("LT"), waitMs = 310),
            Case("hold-wide-start", DeviceKind.WIDE, ButtonStyle.XBOX, false, setOf("START", "Dleft"), setOf("START", "Dleft")),
            Case("hold-remote-left", DeviceKind.REMOTE, ButtonStyle.XBOX, false, setOf("Dleft"), setOf("Dleft"), waitMs = 330),
            Case("stick-left", DeviceKind.WIDE, ButtonStyle.PLAYSTATION, false, setOf("RS"), emptySet(), push = mapOf("RS" to p(-1f, 0f))),
            Case("stick-up-ls", DeviceKind.CONTROLLER, ButtonStyle.XBOX, false, setOf("LS"), emptySet(), push = mapOf("LS" to p(0f, -1f))),
            Case("stick-sway", DeviceKind.CONTROLLER, ButtonStyle.PLAYSTATION, false, setOf("RS", "RT"), setOf("RT"), sway = mapOf("RS" to true)),
            Case("stick-click", DeviceKind.WIDE, ButtonStyle.PLAYSTATION, false, setOf("RS"), emptySet()),
            Case("stick-click-vertical", DeviceKind.VERTICAL, ButtonStyle.XBOX, true, setOf("LS"), emptySet()),
            Case("stick-right-switch", DeviceKind.CONTROLLER, ButtonStyle.SWITCH, false, setOf("RS"), emptySet(), push = mapOf("RS" to p(0.4f, 0f)))
        )
        cases.forEach { c ->
            val v = DevicePictureView(context).apply {
                accent = android.graphics.Color.parseColor("#72D6E8")
                maxHeightDp = 330f
                show(c.kind, c.style, c.sticks)
                held = c.held; holding = c.holding; stickPush = c.push; stickSway = c.sway
            }
            v.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(c.waitMs))
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply { drawColor(android.graphics.Color.BLACK); v.draw(this) }
            File(out, "held-${c.name}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun devicePictures() {
        // The same buttons as device-kit.png: left trigger, right bumper, A, left stick, D-pad right
        val kit = setOf("LT", "RB", "A", "LS", "Dright")
        val cases = listOf(
            Triple(DeviceKind.CONTROLLER, ButtonStyle.XBOX, kit),
            Triple(DeviceKind.CONTROLLER, ButtonStyle.PLAYSTATION, kit),
            Triple(DeviceKind.CONTROLLER, ButtonStyle.SWITCH, kit),
            Triple(DeviceKind.WIDE, ButtonStyle.PLAYSTATION, kit),
            Triple(DeviceKind.VERTICAL, ButtonStyle.SWITCH, kit),
            Triple(DeviceKind.WIDE, ButtonStyle.XBOX, emptySet())
        )
        cases.forEachIndexed { i, (kind, style, keys) ->
            val v = DevicePictureView(context).apply {
                accent = android.graphics.Color.parseColor("#72D6E8")
                maxHeightDp = 330f
                show(kind, style)
                held = keys
            }
            v.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply { drawColor(android.graphics.Color.BLACK); v.draw(this) }
            File(out, "picture-$i-${kind.file}-${style.name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun buttonGuide() {
        setUp { it.copy(buttonStyle = ButtonStyle.PLAYSTATION, picture = PictureChoice.HANDHELD, showPreview = false) }
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        repeat(4) { press(activity, KeyEvent.KEYCODE_BUTTON_R1) }      // Device → Look → Typing → Sound → Button guide
        idle(400)
        save(activity.window.decorView, "guide-0-top")
        focus(activity) { it is GuideRow && it.buttons == "{R2} + {A}" }       // One capital letter: RT + A
        save(activity.window.decorView, "guide-1-capital")
        focus(activity) { it is GuideRow && it.buttons == "{R2} + {L1} {R1}" } // Jump a whole word
        save(activity.window.decorView, "guide-2-jump")
        repeat(4) { press(activity, KeyEvent.KEYCODE_BUTTON_L1) }      // back to Device
        idle(400)
        save(activity.window.decorView, "device-0-top")
        focus(activity) { it is ChoiceRow }                                     // What you're holding
        save(activity.window.decorView, "device-1-picture")
        focus(activity) { it is ActionRow }                                     // Try your buttons
        save(activity.window.decorView, "device-2-try")
    }

    @Test
    fun settingsWithPreview() {
        setUp { it.copy(showPreview = false, numberRow = true) }
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        repeat(2) { press(activity, KeyEvent.KEYCODE_BUTTON_R1) }      // Typing
        press(activity, KeyEvent.KEYCODE_BUTTON_Y)                     // keyboard preview in
        idle(600)
        rows(activity.window.decorView).getOrNull(8)?.requestFocusFromTouch()  // a row halfway down the page
        idle(800)
        save(activity.window.decorView, "settings-preview-typing")
        // quick presses: out, in, out, in
        press(activity, KeyEvent.KEYCODE_BUTTON_Y); idle(60)
        press(activity, KeyEvent.KEYCODE_BUTTON_Y); idle(60)
        press(activity, KeyEvent.KEYCODE_BUTTON_Y); idle(60)
        press(activity, KeyEvent.KEYCODE_BUTTON_Y); idle(700)
        save(activity.window.decorView, "settings-preview-after-quick")
        // quick presses while the page is mid-scroll: the row must stay put and readable
        press(activity, KeyEvent.KEYCODE_BUTTON_Y); idle(90)
        save(activity.window.decorView, "settings-quick-1")
        press(activity, KeyEvent.KEYCODE_BUTTON_Y); idle(90)
        save(activity.window.decorView, "settings-quick-2")
        idle(700)
        save(activity.window.decorView, "settings-quick-3")
    }

    private fun field(o: Any, name: String): Any? =
        o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o)

    /**
     * Fast Y presses, recorded frame by frame (every 33 ms): a picture of each frame, plus where the
     * keyboard panel and the page are. Good: the panel and page only ever move smoothly, never jump.
     */
    @Test
    fun quickPreviewFrames() {
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        android.animation.ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType).apply { isAccessible = true }.invoke(null, 1f)
        setUp { it.copy(showPreview = false, numberRow = true) }
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        repeat(2) { press(activity, KeyEvent.KEYCODE_BUTTON_R1) }      // Typing
        val target = rows(activity.window.decorView).filter { it.isFocusable }.getOrNull(7)
        target?.requestFocusFromTouch()
        idle(400)
        shadowOf(Looper.getMainLooper()).pause()
        val sheet = field(activity, "sheet") as View
        val scroll = field(activity, "scroll") as android.widget.ScrollView
        val log = StringBuilder("frame,ms,event,focus,sheetY,sheetH,scrollY,focusTopOnScreen\n")
        val frames = mutableListOf<Bitmap>()
        var t = 0
        fun snap(event: String) {
            val v = activity.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
            frames += Bitmap.createScaledBitmap(b, v.width / 4, v.height / 4, true)
            val f = activity.window.decorView.findFocus()
            val loc = IntArray(2); f?.getLocationOnScreen(loc)
            log.append("${frames.size - 1},$t,$event,${f?.javaClass?.simpleName},${sheet.translationY.toInt()},${sheet.height},${scroll.scrollY},${loc[1]}\n")
        }
        var startT = 0
        fun y(event: String) {
            startT = t
            val now = SystemClock.uptimeMillis()
            activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y, 0))
            activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y, 0))
            val a = field(activity, "sheetAnim") as? android.animation.ValueAnimator
            a?.pause()          // only our hand-driven clock moves it, never the test machine's own frames
            snap("$event anim=${a?.isStarted}/${a?.duration}/p=${field(activity, "sheetProgress")}")
        }
        // The animation clock, driven by hand: one 33 ms frame at a time, then a layout pass
        fun run(ms: Int) {
            repeat(ms / 33) {
                t += 33
                (field(activity, "sheetAnim") as? android.animation.ValueAnimator)?.let { a ->
                    val e = (t - startT).toLong()
                    if (e >= a.duration) a.end() else a.currentPlayTime = e
                }
                shadowOf(Looper.getMainLooper()).idle()
                snap("")
            }
        }
        log.append("scale=${android.animation.ValueAnimator.getDurationScale()} enabled=${android.animation.ValueAnimator.areAnimatorsEnabled()}\n")
        snap("start")
        y("Y show"); run(132)
        y("Y hide"); run(99)
        y("Y show"); run(66)
        y("Y hide"); run(165)
        y("Y show"); run(500)
        y("Y hide"); run(500)
        File(out, "quick-frames.csv").writeText(log.toString())
        val cols = 6
        val fw = frames[0].width; val fh = frames[0].height
        val sheetBmp = Bitmap.createBitmap(fw * cols, fh * ((frames.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
        val c = Canvas(sheetBmp)
        frames.forEachIndexed { i, b -> c.drawBitmap(b, (i % cols * fw).toFloat(), (i / cols * fh).toFloat(), null) }
        File(out, "quick-frames.png").outputStream().use { sheetBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * With the keyboard up, every row on every page is focused in turn (as the D-pad would):
     * it must sit whole between the top of the page and the top of the keyboard, fades included.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun everyRowFitsAboveKeyboard() {
        setUp { it.copy(numberRow = true) }
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        press(activity, KeyEvent.KEYCODE_BUTTON_Y)                     // the keyboard (Settings opens without it)
        idle(800)
        val sheet = field(activity, "sheet") as View
        val scroll = field(activity, "scroll") as android.widget.ScrollView
        val report = StringBuilder()
        var bad = 0
        repeat(6) { page ->
            val list = rows(activity.window.decorView).filter { it.isFocusable && it.isShown }
            list.forEachIndexed { i, row ->
                row.requestFocusFromTouch(); idle(50)
                // Draw frames like a real screen, so the smooth glide runs to its end
                val v = activity.window.decorView
                val scratch = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                repeat(40) { idle(16); v.draw(Canvas(scratch)) }
                val r = IntArray(2); row.getLocationOnScreen(r)
                val sc = IntArray(2); scroll.getLocationOnScreen(sc)
                val sh = IntArray(2); sheet.getLocationOnScreen(sh)
                val bar = field(activity, "topBar") as View           // the tabs and their soft edge
                val tb = IntArray(2); bar.getLocationOnScreen(tb)
                val top = maxOf(sc[1], tb[1] + bar.height)
                val bottom = sh[1] + sheet.translationY.toInt() + px(activity, 12f)
                val ok = r[1] >= top - 1 && r[1] + row.height <= bottom + 1
                if (!ok) bad++
                report.append("page $page row $i ${row.javaClass.simpleName} h=${row.height} band=${bottom - top} rowTop=${r[1] - top} rowBottomGap=${bottom - (r[1] + row.height)} ${if (ok) "ok" else "CUT"}\n")
                if (i == list.lastIndex) save(activity.window.decorView, "fit-page$page-last")
                if ((page == 1 && (row.height > 200 || i == 3)) || (page == 0 && i == 2)) save(activity.window.decorView, "fit-page1-row$i")
            }
            press(activity, KeyEvent.KEYCODE_BUTTON_R1); idle(500)
        }
        report.append("cut rows: $bad\n")
        File(out, "fit-report.txt").writeText(report.toString())
    }

    /**
     * The Odin's real Settings height (its status bar and navigation take about 50 dp), the keyboard
     * hidden, the last row of Look focused, then Y: the row must end up whole above the keyboard.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun lastRowThenKeyboard() {
        setUp { it.copy(showPreview = false, numberRow = true) }
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        val v = activity.window.decorView
        val scratch = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        fun frames(n: Int) = repeat(n) { idle(16); v.draw(Canvas(scratch)) }
        val last = rows(v).filter { it.isFocusable && it.isShown }.last()
        last.requestFocusFromTouch(); frames(40)
        save(v, "last-0-before")
        press(activity, KeyEvent.KEYCODE_BUTTON_Y); frames(40)
        save(v, "last-1-keyboard")
        val sheet = field(activity, "sheet") as View
        val r = IntArray(2); last.getLocationOnScreen(r)
        val sh = IntArray(2); sheet.getLocationOnScreen(sh)
        File(out, "last-report.txt").writeText("row ${last.javaClass.simpleName} top=${r[1]} bottom=${r[1] + last.height} keyboardTop=${sh[1] + sheet.translationY.toInt()} fade=${px(activity, 12f)}\n")
    }

    private fun px(a: Activity, dp: Float) = (dp * a.resources.displayMetrics.density).toInt()

    private fun tourShots(prefix: String) {
        val cases = listOf(
            Triple(PictureChoice.HANDHELD, ButtonStyle.PLAYSTATION, "wide"),
            Triple(PictureChoice.CONTROLLER, ButtonStyle.XBOX, "controller"),
            Triple(PictureChoice.VERTICAL, ButtonStyle.SWITCH, "vertical"),
            Triple(PictureChoice.REMOTE, ButtonStyle.XBOX, "remote"))
        cases.forEach { (pic, style, name) ->
            setUp { it.copy(buttonStyle = style, picture = pic) }
            val wn = Robolectric.buildActivity(TutorialActivity::class.java,
                Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
            idle(2600); save(wn.window.decorView, "$prefix-$name-1-words")
            press(wn, KeyEvent.KEYCODE_BUTTON_A); idle(2600); save(wn.window.decorView, "$prefix-$name-2-accents")
            val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
            idle(600); press(tour, KeyEvent.KEYCODE_BUTTON_A); idle(2500); save(tour.window.decorView, "$prefix-$name-3-move")
        }
    }

    /** The remote's own tour, every page, and its Button guide. */
    @Test
    fun remoteTour() {
        setUp { it.copy(buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.REMOTE) }
        val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        repeat(6) { i -> idle(if (i == 2) 5200 else 3400); save(tour.window.decorView, "r-tour-$i"); press(tour, KeyEvent.KEYCODE_BUTTON_A) }
        val guide = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500); repeat(4) { press(guide, KeyEvent.KEYCODE_BUTTON_R1) }; idle(400)
        save(guide.window.decorView, "r-guide")
        rows(guide.window.decorView).first { it.isFocusable && it.isShown }.requestFocusFromTouch(); idle(200)
        press(guide, KeyEvent.KEYCODE_DPAD_UP); press(guide, KeyEvent.KEYCODE_DPAD_RIGHT); idle(400)
        save(guide.window.decorView, "r-tabs")
        press(guide, KeyEvent.KEYCODE_DPAD_RIGHT); idle(300)
        save(guide.window.decorView, "r-kbkey")
    }

    /** A vertical handheld without sticks: no stick rows, pages or badges anywhere. */
    @Test
    fun verticalNoSticks() {
        setUp { it.copy(buttonStyle = ButtonStyle.SWITCH, picture = PictureChoice.VERTICAL, showPreview = false) }
        val wn = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(3300); save(wn.window.decorView, "v-whatsnew-words")
        val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        idle(600); press(tour, KeyEvent.KEYCODE_BUTTON_A); idle(1500); save(tour.window.decorView, "v-tour-move")
        val guide = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500); repeat(4) { press(guide, KeyEvent.KEYCODE_BUTTON_R1) }; idle(400)
        save(guide.window.decorView, "v-guide")
        press(guide, KeyEvent.KEYCODE_BUTTON_L1); press(guide, KeyEvent.KEYCODE_BUTTON_L1); press(guide, KeyEvent.KEYCODE_BUTTON_L1); idle(300)
        val v = guide.window.decorView
        val all = rows(v).joinToString("\n") { r -> r.javaClass.simpleName + ": " + (r as? GuideRow)?.buttons.orEmpty() + " " +
            (0 until (r as android.view.ViewGroup).childCount).mapNotNull { (r.getChildAt(it) as? android.widget.TextView)?.text }.joinToString(" | ") }
        File(out, "v-typing-rows.txt").writeText(all)
        save(v, "v-typing")
    }

    /** Layout C on every device kind: words across the top, device and keyboard side by side. */
    @Test
    fun tourEveryDevice() = tourShots("c")

    /** The same on a portrait phone: everything stacks. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun tourPortrait() = tourShots("p")

    @Test
    fun whatsNewAndTour() {
        setUp { it.copy(buttonStyle = ButtonStyle.XBOX, picture = PictureChoice.HANDHELD) }
        val wn = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(500)
        save(wn.window.decorView, "whatsnew-0-intro")
        idle(4000)
        save(wn.window.decorView, "whatsnew-0-words")
        press(wn, KeyEvent.KEYCODE_BUTTON_A); idle(4600)
        save(wn.window.decorView, "whatsnew-1-accents")
        press(wn, KeyEvent.KEYCODE_BUTTON_A); idle(2500)
        save(wn.window.decorView, "whatsnew-2-numbers")
        press(wn, KeyEvent.KEYCODE_BUTTON_A); idle(1000)
        save(wn.window.decorView, "whatsnew-3-devices-a")
        idle(2800)
        save(wn.window.decorView, "whatsnew-3-devices-b")

        // PlayStation texts: words, then accents (ü) caught open and finished
        setUp { it.copy(buttonStyle = ButtonStyle.PLAYSTATION, picture = PictureChoice.CONTROLLER) }
        val ps = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(900)
        save(ps.window.decorView, "ps-0-intro")
        idle(3500)
        save(ps.window.decorView, "ps-0-words")
        press(ps, KeyEvent.KEYCODE_BUTTON_A); idle(4800)
        save(ps.window.decorView, "ps-1-accents-open")
        idle(2600)
        save(ps.window.decorView, "ps-2-accents-done")
        press(ps, KeyEvent.KEYCODE_BUTTON_A); idle(6000)
        save(ps.window.decorView, "ps-3-numbers")

        setUp { it.copy(buttonStyle = ButtonStyle.SWITCH, picture = PictureChoice.VERTICAL) }
        val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        idle(900)
        save(tour.window.decorView, "tour-0-intro")
        idle(600)
        repeat(4) { press(tour, KeyEvent.KEYCODE_BUTTON_A) }           // to "Accents and word suggestions"
        idle(1200)
        save(tour.window.decorView, "tour-4-intro")
        idle(4200)
        save(tour.window.decorView, "tour-4-accents")
        press(tour, KeyEvent.KEYCODE_BUTTON_A); idle(2000)
        save(tour.window.decorView, "tour-5-select")
        repeat(5) { press(tour, KeyEvent.KEYCODE_BUTTON_B) }           // back to "Move and type"
        idle(5000)
        save(tour.window.decorView, "tour-1-type")
        press(tour, KeyEvent.KEYCODE_BUTTON_A); idle(800)
        save(tour.window.decorView, "tour-2-shift")
        press(tour, KeyEvent.KEYCODE_BUTTON_A); idle(3500)
        save(tour.window.decorView, "tour-3-jump")
    }
}
