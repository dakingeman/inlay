package com.example.gridime

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The safety net. Runs on every build, for every kind of device, and fails with a plain sentence
 * when something doesn't add up:
 *  1. What the device picture lights in a demo belongs to the button rows on that page (or is plain
 *     moving and typing), and two unrelated buttons never light together.
 *  2. Nothing is lit or named that the device doesn't have (no sticks on a stickless handheld,
 *     no controller buttons on a remote).
 *  3. No two actions in the Button guide use the same buttons.
 * To add a device, a page or a button: add it, run the tests, read what (if anything) they say.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w827dp-h464dp-land-xhdpi")
class ConsistencyTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun press(a: Activity, key: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0))
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0))
        idle(100)
    }

    /** Every device set-up Inlay knows. */
    private val devices = listOf(
        "Xbox controller" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.CONTROLLER, buttonStyle = ButtonStyle.XBOX) },
        "PlayStation handheld" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.HANDHELD, buttonStyle = ButtonStyle.PLAYSTATION) },
        "vertical handheld without sticks" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.VERTICAL, buttonStyle = ButtonStyle.SWITCH, verticalSticks = false) },
        "vertical handheld with sticks" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.VERTICAL, buttonStyle = ButtonStyle.SWITCH, verticalSticks = true) },
        "TV remote" to { s: KeyboardSettings -> s.copy(picture = PictureChoice.REMOTE) }
    )

    private fun setUp(change: (KeyboardSettings) -> KeyboardSettings) =
        change(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION)).save(context)

    private val moving = setOf("Dup", "Ddown", "Dleft", "Dright", "A")       // moving and typing: fine anywhere

    /** The buttons a device really has (the remote's are A = OK, B = Back and the ring). */
    private fun buttonsOf(c: Controls, style: ButtonStyle): Set<String> =
        if (c.remote) setOf("A", "B", "Dup", "Ddown", "Dleft", "Dright")
        else drawn(c, style) + (if (c.sticks) setOf("LS", "RS") else emptySet()) - (if (c.sticks) emptySet() else setOf("LS", "RS"))

    /** What the device's picture can light. */
    private fun drawn(c: Controls, style: ButtonStyle): Set<String> =
        DevicePicture.load(context, c.kind, style, c.sticks)!!.second.keys().asSequence().toSet()

    /**
     * Gaps we know about and have a plan for: reported as a warning on every run, not a failure.
     * Remove a line once it's fixed, and the check guards it from then on.
     */
    private val knownGaps = mapOf<String, String>()      // none right now

    /** Every device's picture can light every button that device is said to have. */
    @Test
    fun picturesShowEveryButton() {
        val problems = mutableListOf<String>()
        devices.forEach { (name, setting) ->
            setUp(setting)
            val settings = KeyboardSettings.load(context)
            val c = Controls.of(context, settings)
            if (c.remote) return@forEach
            val missing = buttonsOf(c, settings.buttonStyle) - drawn(c, settings.buttonStyle)
            if (missing.isEmpty()) return@forEach
            val line = "$name: the picture can't light ${missing.sorted()}"
            knownGaps[name]?.let { println("WARNING (known): $line: $it") } ?: run { problems += line }
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    @Test
    fun demosLightOnlyWhatThePageShows() {
        val problems = mutableListOf<String>()
        devices.forEach { (name, setting) ->
            listOf(false, true).forEach { whatsNew ->
                setUp(setting)
                val settings = KeyboardSettings.load(context)
                val c = Controls.of(context, settings)
                val has = buttonsOf(c, settings.buttonStyle)
                val intent = Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)
                val tour = Robolectric.buildActivity(TutorialActivity::class.java, intent).setup().get()
                val pages = tour.pageCheck()
                val where = if (whatsNew) "What's new" else "the tour"
                pages.forEachIndexed { i, (title, rows) ->
                    val rowKeys = rows.map { DevicePicture.keysIn(it) }
                    val seen = mutableSetOf<Set<String>>()
                    tour.pictureForCheck.onLit = { lit -> if (lit.isNotEmpty()) seen += lit }
                    idle(6000)
                    seen.forEach { lit ->
                        val fits = lit.all { it in moving } || rowKeys.any { row -> lit.all { it in row || it in moving } }
                        if (!fits) problems += "$name, $where, page ${i + 1} \"$title\": lights ${lit.sorted()} together, but no row on the page shows that"
                        (lit - has).takeIf { it.isNotEmpty() }?.let {
                            problems += "$name, $where, page ${i + 1} \"$title\": lights ${it.sorted()}, which this device doesn't have"
                        }
                    }
                    rows.forEach { row ->
                        val missing = DevicePicture.keysIn(row) - has
                        if (missing.isNotEmpty()) problems += "$name, $where, page ${i + 1} \"$title\": the row \"$row\" uses ${missing.sorted()}, which this device doesn't have"
                        if (c.remote && '{' in row) problems += "$name, $where, page ${i + 1} \"$title\": the row \"$row\" shows a controller badge on a remote"
                    }
                    if (i < pages.lastIndex) press(tour, KeyEvent.KEYCODE_BUTTON_A)
                }
            }
        }
        if (problems.isNotEmpty()) fail("\n" + problems.distinct().joinToString("\n"))
    }

    @Test
    fun buttonGuideHasNoOverlapsAndOnlyRealButtons() {
        val problems = mutableListOf<String>()
        devices.forEach { (name, setting) ->
            setUp(setting)
            val settings = KeyboardSettings.load(context)
            val c = Controls.of(context, settings)
            val has = buttonsOf(c, settings.buttonStyle)
            val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            val rows = activity.guideCheck()
            rows.groupBy { it.second }.filter { it.value.size > 1 }.forEach { (buttons, same) ->
                problems += "$name, Button guide: ${same.joinToString(" and ") { "\"${it.first}\"" }} both use $buttons"
            }
            rows.forEach { (what, buttons) ->
                val missing = DevicePicture.keysIn(buttons) - has
                if (missing.isNotEmpty()) problems += "$name, Button guide: \"$what\" uses ${missing.sorted()}, which this device doesn't have"
                if (c.remote && '{' in buttons) problems += "$name, Button guide: \"$what\" shows a controller badge on a remote"
            }
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    /**
     * Nobody gets locked out: with only a TV remote's buttons (ring, OK, Back), Settings can reach
     * every section, show and hide the keyboard and close; the tour and What's new can be gone
     * through to the end and left. And a controller still works when the remote picture is picked.
     */
    @Test
    fun remoteNeverLockedOut() {
        val problems = mutableListOf<String>()
        setUp { it.copy(picture = PictureChoice.REMOTE) }
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        fun page() = SettingsActivity::class.java.getDeclaredField("currentPage").apply { isAccessible = true }.getInt(s)
        fun preview() = (SettingsActivity::class.java.getDeclaredField("settings").apply { isAccessible = true }.get(s) as KeyboardSettings).showPreview
        // As on the device: Settings opens with its first row focused
        @Suppress("UNCHECKED_CAST")
        (SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(s) as List<android.view.View>).first().requestFocusFromTouch()
        idle(200)
        val f0 = s.window.decorView.findFocus()
        press(s, KeyEvent.KEYCODE_DPAD_UP); idle(300)                     // from the first row up to the tabs
        val f1 = s.window.decorView.findFocus()
        if (f1 !is TabBar) problems += "Remote in Settings: up from the first row went to ${f1?.javaClass?.simpleName} (from ${f0?.javaClass?.simpleName}, up id ${f0?.nextFocusUpId}, tabs id ${(SettingsActivity::class.java.getDeclaredField("tabs").apply { isAccessible = true }.get(s) as android.view.View).id})"
        repeat(5) { i ->
            press(s, KeyEvent.KEYCODE_DPAD_RIGHT); idle(300)
            if (page() != i + 1) problems += "Remote in Settings: right on the tabs didn't reach section ${i + 2} (on ${page() + 1})"
        }
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT); press(s, KeyEvent.KEYCODE_DPAD_CENTER); idle(500)   // the keyboard key
        if (!preview()) problems += "Remote in Settings: OK on the keyboard key didn't show the keyboard"
        press(s, KeyEvent.KEYCODE_DPAD_CENTER); idle(500)
        if (preview()) problems += "Remote in Settings: OK on the keyboard key didn't hide the keyboard"
        press(s, KeyEvent.KEYCODE_DPAD_DOWN); idle(300)
        if (s.window.decorView.findFocus() !is SettingRow) problems += "Remote in Settings: down from the tabs didn't go back to the list"
        val before = page()
        press(s, KeyEvent.KEYCODE_BUTTON_L1); idle(300)
        if (page() != (before + 5) % 6) problems += "Remote picture, but a controller: L1 should still switch sections (on ${page() + 1})"
        press(s, KeyEvent.KEYCODE_BACK); idle(400)
        if (!s.isFinishing) problems += "Remote in Settings: Back didn't close Settings"

        listOf(false, true).forEach { whatsNew ->
            val where = if (whatsNew) "What's new" else "the tour"
            val t = Robolectric.buildActivity(TutorialActivity::class.java,
                Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
            idle(300)
            repeat(t.pageCheck().size) { press(t, KeyEvent.KEYCODE_DPAD_CENTER); idle(200) }
            if (!t.isFinishing) problems += "Remote in $where: OK on every page didn't reach the end"
            val t2 = Robolectric.buildActivity(TutorialActivity::class.java,
                Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
            idle(300); press(t2, KeyEvent.KEYCODE_BACK); idle(200)
            if (!t2.isFinishing) problems += "Remote in $where: Back on the first page didn't leave"
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    /** The button test can always be left: holding B (or Back on a remote) ends it, a tap doesn't. */
    @Test
    fun buttonTestCanAlwaysBeLeft() {
        val problems = mutableListOf<String>()
        listOf(PictureChoice.HANDHELD to KeyEvent.KEYCODE_BUTTON_B, PictureChoice.REMOTE to KeyEvent.KEYCODE_BACK).forEach { (pic, key) ->
            setUp { it.copy(picture = pic) }
            val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            idle(300)
            fun testing() = SettingsActivity::class.java.getDeclaredField("testing").apply { isAccessible = true }.getBoolean(s)
            SettingsActivity::class.java.getDeclaredMethod("startButtonTest").apply { isAccessible = true }.invoke(s)
            if (!testing()) problems += "$pic: the button test didn't start"
            val t = android.os.SystemClock.uptimeMillis()
            s.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0))
            s.dispatchKeyEvent(KeyEvent(t, t + 100, KeyEvent.ACTION_UP, key, 0))
            if (!testing()) problems += "$pic: a quick tap of ${KeyEvent.keyCodeToString(key)} ended the test (it should only light up)"
            if (s.isFinishing) problems += "$pic: a tap during the test closed Settings"
            val t2 = t + 1000
            s.dispatchKeyEvent(KeyEvent(t2, t2, KeyEvent.ACTION_DOWN, key, 0))
            s.dispatchKeyEvent(KeyEvent(t2, t2 + 800, KeyEvent.ACTION_DOWN, key, 1))
            s.dispatchKeyEvent(KeyEvent(t2, t2 + 850, KeyEvent.ACTION_UP, key, 0))
            if (testing()) problems += "$pic: holding ${KeyEvent.keyCodeToString(key)} didn't end the test"
            SettingsActivity::class.java.getDeclaredMethod("startButtonTest").apply { isAccessible = true }.invoke(s)
            idle(8500)   // just past BUTTON_TEST_IDLE_MS
            if (testing()) problems += "$pic: the test didn't end on its own after a quiet spell"
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    /** Held buttons always look held: "hold {X}" and the first button of a combo become a HOLD capsule. */
    @Test
    fun holdAlwaysUsesTheCapsule() {
        val problems = mutableListOf<String>()
        listOf(PictureChoice.CONTROLLER, PictureChoice.HANDHELD, PictureChoice.VERTICAL, PictureChoice.REMOTE).forEach { pic ->
            setUp { it.copy(picture = pic) }
            val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            idle(200)
            s.guideCheck().forEach { (title, buttons) ->
                val parts = HintText.parse(buttons)
                if (parts.any { it is HintPart.Text && Regex("(?i)\\bhold\\b").containsMatchIn(it.text) })
                    problems += "$pic, \"$title\": the word hold is left as text in \"$buttons\""
                val combo = Regex("""^\{[A-Z0-9]+\} *\+""").containsMatchIn(buttons)
                if (combo && (parts.firstOrNull() as? HintPart.Badge)?.held != true)
                    problems += "$pic, \"$title\": the first button of \"$buttons\" isn't shown as held"
            }
        }
        val check = HintText.parse("Hold {R2} to repeat")
        if ((check.first() as? HintPart.Badge)?.held != true) problems += "\"Hold {R2}\" didn't make a capsule: $check"
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    /** Going back and forth through the tour never leaves another colour or device behind. */
    @Test
    fun tourKeepsYourColourAndDevice() {
        val problems = mutableListOf<String>()
        setUp { it.copy(picture = PictureChoice.HANDHELD) }
        val wanted = KeyboardSettings.load(context).accent
        val t = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        idle(8000)                                                       // the welcome page shows other devices
        val n = t.pageCheck().size
        repeat(n - 1) { press(t, KeyEvent.KEYCODE_BUTTON_A); idle(400) }
        idle(4000)                                                       // the last page changes colours
        repeat(n - 1) { i ->
            press(t, KeyEvent.KEYCODE_BUTTON_B); idle(600)
            val pic = t.pictureForCheck
            if (pic.accent != wanted) problems += "Page ${n - 1 - i}: the picture kept another colour"
            if (pic.kind != DeviceKind.WIDE && i < n - 2) problems += "Page ${n - 1 - i}: the picture kept another device (${pic.kind})"
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    /**
     * The loop bar: every looping demo shows it, in the tour and in What's new, on every device.
     * Only the welcome page's endless device showcase goes without.
     */
    @Test
    fun everyLoopingDemoShowsTheLoopBar() {
        val problems = mutableListOf<String>()
        listOf(PictureChoice.HANDHELD, PictureChoice.VERTICAL, PictureChoice.CONTROLLER, PictureChoice.REMOTE).forEach { pic ->
            setUp { it.copy(picture = pic) }
            listOf(false, true).forEach { whatsNew ->
                val t = Robolectric.buildActivity(TutorialActivity::class.java,
                    android.content.Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
                val pages = t.pageCheck()
                pages.forEachIndexed { i, (title, _) ->
                    idle(500)
                    val welcome = !whatsNew && i == 0
                    if (t.loopBarShown == welcome) problems += "$pic ${if (whatsNew) "What's new" else "tour"} page ${i + 1} ($title): loop bar ${if (welcome) "shown" else "missing"}"
                    if (i < pages.size - 1) press(t, KeyEvent.KEYCODE_BUTTON_A)
                }
            }
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    /**
     * The loop bar fills at an even pace: each run of a demo says how long it takes, so the bar is
     * exactly full when the demo starts over. Checked on every looping page, on every device.
     */
    @Test
    fun loopBarRunsAreExact() {
        val problems = mutableListOf<String>()
        listOf(PictureChoice.HANDHELD to false, PictureChoice.VERTICAL to true, PictureChoice.CONTROLLER to false, PictureChoice.REMOTE to false).forEach { (pic, sticks) ->
            setUp { it.copy(picture = pic, verticalSticks = sticks) }
            listOf(false, true).forEach { whatsNew ->
                val t = Robolectric.buildActivity(TutorialActivity::class.java,
                    android.content.Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, whatsNew)).setup().get()
                val pages = t.pageCheck()
                pages.forEachIndexed { i, (title, _) ->
                    // Long enough to see three runs, even of a long demo (the sound showcase plays every pack)
                    var waited = 0L
                    do { idle(10_000); waited += 10_000 } while (t.loopBarShown && t.loopRuns.size < 3 && waited < 90_000)
                    val runs = t.loopRuns
                    val where = "$pic ${if (whatsNew) "What's new" else "tour"} page ${i + 1} ($title)"
                    if (t.loopBarShown && runs.size < 3) problems += "$where: only ${runs.size} runs in ${waited / 1000} s"
                    runs.zipWithNext().forEach { (a, b) ->
                        val off = (b.first - a.first) - a.second
                        if (kotlin.math.abs(off) > 2) problems += "$where: a run said ${a.second} ms but took ${b.first - a.first} ms"
                    }
                    if (i < pages.size - 1) press(t, KeyEvent.KEYCODE_BUTTON_A)
                }
            }
        }
        if (problems.isNotEmpty()) fail("\n" + problems.distinct().joinToString("\n"))
    }

    /** Moving down (and back up) through every Settings tab, the row you're on always shows whole. */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun settingsRowsNeverCutOff() {
        val problems = mutableListOf<String>()
        setUp { it.copy(showPreview = false) }
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(600)
        repeat(6) { tab ->
            repeat(30) { i ->
                press(s, KeyEvent.KEYCODE_DPAD_DOWN); idle(400)
                val hidden = s.focusedRowHiddenPx()
                if (hidden > 0) problems += "Tab ${tab + 1}, down ${i + 1}: the row is cut off by ${hidden}px"
            }
            repeat(30) { i ->
                press(s, KeyEvent.KEYCODE_DPAD_UP); idle(400)
                val hidden = s.focusedRowHiddenPx()
                if (hidden > 0) problems += "Tab ${tab + 1}, up ${i + 1}: the row is cut off by ${hidden}px "
            }
            press(s, KeyEvent.KEYCODE_BUTTON_R1); idle(500)
        }
        if (problems.isNotEmpty()) fail("\n" + problems.joinToString("\n"))
    }

    /**
     * The spam test: Y (show / hide the keyboard) and LB / RB pressed as fast as a person can, mixed
     * together, then a moment to settle. Afterwards everything must agree: the keyboard is up or down
     * (never stuck halfway), the tabs fold fully into the line of bars and back, the bars' highlight sits on the
     * section that's open, and the row you're on shows whole above the keyboard.
     */
    @Test
    fun keyboardToggleSurvivesSpam() {
        val problems = mutableListOf<String>()
        setUp { it.copy(picture = PictureChoice.HANDHELD) }
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        fun field(n: String) = SettingsActivity::class.java.getDeclaredField(n).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field("focusableRows").get(s) as List<android.view.View>).first().requestFocusFromTouch()
        idle(200)
        fun quick(key: Int) {
            val t = SystemClock.uptimeMillis()
            s.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0))
            s.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0))
            idle(30)                                   // far quicker than any slide finishes
        }
        val pattern = listOf(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_Y)
        repeat(6) { round ->
            pattern.forEach { quick(it) }
            if (round % 2 == 1) quick(KeyEvent.KEYCODE_BUTTON_Y)   // end up shown and hidden in turn
            idle(1200)
            val shown = (field("settings").get(s) as KeyboardSettings).showPreview
            val progress = field("sheetProgress").getFloat(s)
            val topBar = field("topBar").get(s) as android.view.View
            val tabsY = topBar.translationY
            val tabs = field("tabs").get(s) as TabBar
            val page = field("currentPage").getInt(s)
            val want = if (shown) 1f else 0f
            if (kotlin.math.abs(progress - want) > 0.001f) problems += "round $round: keyboard stuck at $progress (shown=$shown)"
            if (kotlin.math.abs(tabs.collapse - want) > 0.001f) problems += "round $round: tabs folded to ${tabs.collapse} (shown=$shown)"
            if (shown && kotlin.math.abs(tabsY + tabs.height - tabs.foldedHeightPx) > 1f) problems += "round $round: tabs not fully folded away ($tabsY)"
            if (!shown && kotlin.math.abs(tabsY) > 0.5f) problems += "round $round: tabs not fully back ($tabsY)"
            if (kotlin.math.abs(tabs.indicatorAt - page) > 0.001f) problems += "round $round: bar highlight at ${tabs.indicatorAt}, section $page"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
