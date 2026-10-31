package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.json.JSONArray
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

/**
 * Records the keyboard and device picture, frame by frame, for the GitHub videos. The app's own
 * code draws every frame; docs/visuals-src/video.html adds the game library, captions and layout.
 * Only runs when asked (see VisualsExport). Output: app/build/visuals/video/<name>/
 *   kb-0000.png, pic-0000.png … and frames.json (text box, caption and device per frame).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h560dp-land-xxhdpi")
class VisualsVideo {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private inner class Rec(name: String, val style: ButtonStyle, kind: DeviceKind, sticks: Boolean = false,
                            val kbWidthDp: Int = 640, numberRow: Boolean = false,
                            prep: KeyboardSettings.() -> KeyboardSettings = { this }) : KeyGridView.Listener {
        val dir = File("build/visuals/video/$name").apply { deleteRecursively(); mkdirs() }
        val frames = JSONArray()
        var before = ""; var sel = ""; var after = ""
        var cap = ""; var dev = "${kind.name.lowercase()}${if (sticks) "-sticks" else ""}"
        var prefix = ""
        var extra = JSONObject()
        /** What happened on the next frame, for the cue sheet (key presses, highlight moves). */
        var ev = ""
        fun mark(e: String) { ev = if (ev.isEmpty()) e else "$ev+$e" }
        val dp = context.resources.displayMetrics.density
        var settings = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style,
            wordSuggestions = true, holdAccents = true, numberRow = numberRow).prep()
        val kb = KeyGridView(context).apply { applySettings(settings); setField(FieldKind.TEXT); listener = this@Rec }
        val pic = DevicePictureView(context).apply {
            accent = android.graphics.Color.parseColor("#72D6E8"); maxHeightDp = 300f; tallBoost = false; show(kind, style, sticks)
        }
        init {
            kb.onDemoInput = { key, held ->
                when (held) {
                    null -> pic.flash(key)
                    true -> { pic.held = pic.held + key; pic.holding = pic.holding + key }
                    false -> { pic.held = pic.held - key; pic.holding = pic.holding - key }
                }
            }
            // On a real window, so the keyboard's own timers (the accents rising) run
            val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            val frame = android.widget.LinearLayout(host).apply { orientation = android.widget.LinearLayout.VERTICAL }
            host.setContentView(frame)
            frame.addView(kb, android.widget.LinearLayout.LayoutParams((kbWidthDp * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            idle(100)
            pic.measure(View.MeasureSpec.makeMeasureSpec((360 * dp).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            pic.layout(0, 0, pic.measuredWidth, pic.measuredHeight)
        }
        // One bitmap and canvas per view, reused for every frame (fresh ones each frame run the test out of memory)
        private val buffers = HashMap<View, Pair<Bitmap, Canvas>>()
        private fun shot(v: View, file: String) {
            var buf = buffers[v]
            if (buf == null || buf.first.width != v.width || buf.first.height != v.height) {
                buf?.first?.recycle()
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                buf = b to Canvas(b); buffers[v] = buf
            }
            buf.first.eraseColor(0)
            v.draw(buf.second)
            File(dir, file).outputStream().use { buf.first.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        /** Records [ms] of video at 30 fps. */
        fun hold(ms: Long) {
            val n = Math.round(ms * 30 / 1000.0).toInt()
            repeat(n) {
                idle(33)
                val i = frames.length()
                // Android's drawing objects free their native memory only when collected: collect often
                if (i % 15 == 0) { System.gc(); System.runFinalization() }
                val name = String.format("%04d", i)
                shot(kb, "kb-$name.png"); shot(pic, "pic-$name.png")
                frames.put(JSONObject().put("before", before).put("sel", sel).put("after", after).put("cap", cap)
                    .put("dev", dev).put("kbh", kb.height).put("extra", JSONObject(extra.toString())).put("ev", ev))
                ev = ""
            }
        }
        /** Walks to and presses each letter of [text], like the tour, recording every step. */
        fun type(text: String, stepMs: Long = 110, pressMs: Long = 230) {
            for (ch in text) {
                var guard = 0
                while (guard++ < 40) {
                    when (kb.stepTowards(ch.toString())) {
                        true -> { mark(pressName); kb.pressSelected(); hold(pressMs); break }
                        false -> { mark("move"); hold(stepMs) }
                        null -> break
                    }
                }
            }
        }
        var pressName = "A"
        fun walkTo(label: String, stepMs: Long = 110) { var g = 0; while (g++ < 40 && kb.stepTowards(label) == false) { mark("move"); hold(stepMs) } }
        fun show(text: String) { before = text; sel = ""; after = ""; kb.setPreview(before, sel, after) }
        /** The same three fixes the keyboard makes (as in the tour's demo). */
        fun smartFix(text: String): String {
            var t = text
            t = Regex("(^|[ ])i(?= )").replace(t) { it.groupValues[1] + "I" }
            t = Regex("([\\p{L}\\d]) ([.,!?])").replace(t) { it.groupValues[1] + it.groupValues[2] + " " }.replace("  ", " ")
            t = Regex("(^|[.!?] )(\\p{Ll})").replace(t) { it.groupValues[1] + it.groupValues[2].uppercase() }
            return t
        }
        fun save() { File(dir, "frames.json").writeText(frames.toString()) }

        var tidy = false
        override fun onText(text: String) { before += text; if (tidy) before = smartFix(before); kb.setPreview(before, sel, after) }
        override fun onDelete() { before = before.dropLast(1) }
        override fun onEnter() {}
        override fun onTool(tool: Tool) {}
        override fun onClip(index: Int) {}
        override fun onReplaceLast(expected: String, replacement: String): Boolean {
            if (!before.endsWith(expected)) return false
            before = before.dropLast(expected.length) + replacement; return true
        }
        override fun onWord(word: String) { before = before.dropLast(prefix.length) + word + " "; prefix = ""; kb.setSuggestions(emptyList(), "") }
    }

    private val X = ButtonStyle.XBOX
    private val W = DeviceKind.WIDE
    private fun scene(name: String, numberRow: Boolean = false, kind: DeviceKind = W, kbWidthDp: Int = 640,
                      prep: KeyboardSettings.() -> KeyboardSettings = { this }, body: Rec.() -> Unit) {
        assumeTrue(File("build/visuals-request").exists())
        val r = Rec(name, X, kind, numberRow = numberRow, kbWidthDp = kbWidthDp, prep = prep)
        r.body()
        r.save()
    }

    /** Move and type: walk to each letter, press A. */
    @Test fun sType() = scene("type") { cap = "type"; hold(900); type("ninja gaiden", 75, 170); hold(1800) }

    /** Words as you type, from the app's own word list; a title used in three places comes first. */
    @Test fun sWords() = scene("words") {
        cap = "words"
        val lang = Language.ENGLISH
        val mine = UserWords(context)
        for (box in 1L..3L) mine.learn(lang, "conker", box)       // used in three different text boxes, so it's learned
        var ready = false
        val suggester = WordSuggester(context, lang) { ready = true }
        var g = 0; while (!ready && g++ < 200) { Thread.sleep(20); idle(10) }
        show(""); hold(900)
        for (ch in "con") {
            type(ch.toString())
            prefix = before.substringAfterLast(' ')
            kb.setSuggestions(suggester.suggest(prefix, 3, mine.matching(lang, prefix.lowercase(), suggester::knows)), prefix)
            mark("words"); hold(420)
        }
        hold(1000)
        mark("RS"); pic.flash("RS", 600); kb.bestWord()?.let { onWord(it) }; hold(2200)
    }

    /**
     * The hero (30 Oct 2026): the tagline typed into the text box with the app's own word suggester. Automatic capitals put
     * the T in, the right stick takes "your" and then "controller", the line rests, then clears. Paced for reading.
     */
    @Test fun sHeroTag() = heroTag("herotag", walk = false)
    @Test fun sHeroTagWalk() = heroTag("herotag-walk", walk = true)

    /**
     * The README hero: the tagline typed at an even pace, each word taken from the suggestions with the right stick,
     * then X held to clear it (the app's real hold-to-delete: letters speeding up, then whole words) so it loops.
     * [walk] true: the highlight travels key to key (several steps a frame on long moves, so the pace stays even);
     * false: the highlight is simply on each key as it's pressed (Emre, 30 Oct 2026).
     */
    private fun heroTag(name: String, walk: Boolean) = scene(name) {
        cap = "hero"; tidy = true
        val lang = Language.ENGLISH
        var ready = false
        val suggester = WordSuggester(context, lang) { ready = true }
        var g = 0; while (!ready && g++ < 200) { Thread.sleep(20); idle(10) }
        // The app's own suggestions at every letter; a row is only set by hand where the app would rank the word
        // second, and then it is the app's own three words, reordered
        val mine = UserWords(context)
        val firstAt = mapOf("yo" to listOf("your", "you", "you're"), "contr" to listOf("controller", "control", "contract"))
        fun suggest() { prefix = before.substringAfterLast(' '); val low = prefix.lowercase(); kb.setSuggestions(when {
            prefix.isEmpty() -> emptyList()
            low in firstAt -> firstAt.getValue(low)
            else -> suggester.suggest(prefix, 3, mine.matching(lang, low, suggester::knows)) }, prefix) }
        val frame = 1000L / 30
        val letterFrames = 6                       // 200 ms a letter, every letter
        fun pressLetter(ch: Char) {
            val label = ch.toString()
            val n = kb.stepsTo(label)
            if (!walk || n == 0) {
                var guard = 0; while (guard++ < 60 && kb.stepTowards(label) == false) { }
                hold((letterFrames - 1) * frame)
            } else {
                val moveFrames = minOf(n, letterFrames - 2)
                for (f in 0 until moveFrames) {
                    val steps = (n * (f + 1)) / moveFrames - (n * f) / moveFrames
                    repeat(steps) { kb.stepTowards(label) }; mark("move"); hold(frame) }
                hold((letterFrames - 1 - moveFrames) * frame)
            }
            mark(pressName); kb.pressSelected(); suggest(); mark("words"); hold(frame)
        }
        fun typeUntilOffered(word: String, rest: Long) {
            for (ch in word) { pressLetter(ch); if (kb.bestWord()?.lowercase() == word) break }
            hold(450); mark("RS"); pic.flash("RS", 400); kb.bestWord()?.let { onWord(it) }; hold(rest)
        }
        // Hold X, exactly as GridKeyboardService does: one delete, a 320 ms wait, letters from 100 ms speeding up by
        // 7 ms a time to 50 ms, and from the 20th repeat whole words every 160 ms
        fun holdDelete() {
            kb.holdButton("X", true); mark("X")
            fun del(n: Int) {
                before = if (n < 20) before.dropLast(1) else before.trimEnd().let { t -> t.dropLast(t.length - (t.lastIndexOf(' ') + 1)) }
                suggest(); kb.setPreview(before, sel, after)
            }
            del(0); hold(320); var n = 1
            while (before.isNotEmpty()) { del(n); hold(if (n < 20) maxOf(50L, 100L - n * 7L) else 160L); n++ }
            kb.holdButton("X", false)
        }
        // The loop starts where it ends: an empty box, the highlight on "r" (the last key pressed)
        show(""); var guard = 0; while (guard++ < 40 && kb.stepTowards("r") == false) { }
        // settle the keyboard into the exact state the loop ends in (the same calls the clearing makes)
        kb.holdButton("X", true); kb.setSuggestions(listOf("type"), "t"); kb.setSuggestions(emptyList(), ""); kb.holdButton("X", false)
        idle(300)
        hold(500)
        for (w in listOf("type", "with", "your")) typeUntilOffered(w, 150)
        typeUntilOffered("controller", 1800)
        mark("clear"); holdDelete()
        kb.setSuggestions(emptyList(), ""); hold(frame)
    }

    /**
     * Hero round L9 (Emre, 30 Oct 2026): the keyboard types the name, in time with the logo animation
     * (docs/brand/motion, 5.16 s loop): i n l a y pressed at the moments the letters appear, then X taps as they go.
     * The highlight is simply on each key as it's pressed. The app's own suggestions follow every letter.
     */
    @Test fun sLogoKeys() = logoKeys("logokeys", words = true)
    @Test fun sLogoKeysQuiet() = logoKeys("logokeys-quiet", words = false)

    /** [words] false: the top row stays on its tools, as with word suggestions turned off in Settings. */
    private fun logoKeys(name: String, words: Boolean) = scene(name) {
        cap = "hero"
        val lang = Language.ENGLISH
        var ready = false
        val suggester = WordSuggester(context, lang) { ready = true }
        var g = 0; while (!ready && g++ < 200) { Thread.sleep(20); idle(10) }
        val mine = UserWords(context)
        fun suggest() { prefix = before.substringAfterLast(' '); val low = prefix.lowercase(); kb.setSuggestions(
            if (!words || prefix.isEmpty()) emptyList() else suggester.suggest(prefix, 3, mine.matching(lang, low, suggester::knows)), prefix) }
        // where the loop starts and ends: empty, the highlight on "y" (the last key pressed), the keyboard settled
        show(""); var guard = 0; while (guard++ < 40 && kb.stepTowards("y") == false) { }
        kb.holdButton("X", true); kb.setSuggestions(listOf("in"), "i"); kb.setSuggestions(emptyList(), ""); kb.holdButton("X", false)
        idle(300)
        val events = listOf(0.86 to "i", 1.16 to "n", 1.62 to "l", 1.86 to "a", 2.08 to "y",
            4.66 to "X", 4.70 to "X", 4.76 to "X", 4.82 to "X", 4.86 to "X")
        val total = Math.round(5.16 * 30).toInt()
        var f = 0
        for ((t, k) in events) {
            val at = Math.round(t * 30).toInt()
            if (at > f) { hold((at - f) * 1000L / 30); f = at }
            if (k == "X") { kb.pressShortcut("X", KeyType.DELETE) }
            else { var gg = 0; while (gg++ < 40 && kb.stepTowards(k) == false) { }; mark(pressName); kb.pressSelected() }
            suggest(); mark("words")
        }
        hold((total - f) * 1000L / 30)
    }

    /**
     * Hero L9-A, final (Emre, 30 Oct 2026): the keyboard types "inlay" the way you really would, the highlight
     * travelling key to key and A pressing each letter at the moment the logo animation shows it; then the highlight
     * goes to the Delete key and A taps it five times; the loop starts where it ends (empty, highlight on Delete).
     * Times in seconds; the logo is retimed to match afterwards (logo-keys.py).
     */
    @Test fun sLogoKeysTravel() = scene("logokeys-travel") {
        // the highlight wraps round the row's edges (as it does on the keyboard): the shortest way to every key
        cap = "hero"
        val frame = 1000L / 30
        show(""); var guard = 0; while (guard++ < 40 && kb.stepToKey(KeyType.DELETE) == false) { }
        kb.holdButton("X", true); kb.setSuggestions(listOf("in"), "i"); kb.setSuggestions(emptyList(), ""); kb.holdButton("X", false)
        idle(300)
        var f = 0
        fun until(t: Double) { val at = Math.round(t * 30).toInt(); if (at > f) { hold((at - f) * frame); f = at } }
        // Walk with the stick so the highlight lands one frame before [t]: a step a frame, two when the way is long
        fun walkAndPress(t: Double, n: Int, step: () -> Unit, press: () -> Unit) {
            val at = Math.round(t * 30).toInt()
            val room = maxOf(1, at - f - 2)
            val frames = minOf(n, room)
            until((at - frames - 1) / 30.0)
            for (k in 0 until frames) { repeat((n * (k + 1)) / frames - (n * k) / frames) { step() }; mark("move"); hold(frame); f++ }
            until(t); press()
        }
        for ((t, ch) in listOf(0.86 to "i", 1.16 to "n", 1.62 to "l", 1.86 to "a", 2.08 to "y"))
            walkAndPress(t, kb.stepsTo(ch, wrap = true), { kb.stepTowards(ch, wrap = true) }) { mark(pressName); kb.pressSelected(); kb.setSuggestions(emptyList(), "") }
        val dels = (0 until 5).map { 4.0 + it * 0.16 }
        walkAndPress(dels[0], kb.stepsToKey(KeyType.DELETE, wrap = true), { kb.stepToKey(KeyType.DELETE, wrap = true) }) { mark(pressName); kb.pressSelected() }
        for (t in dels.drop(1)) { until(t); mark(pressName); kb.pressSelected() }
        until(dels.last() + 0.28)
    }

    /** Hold A on a letter: the accents rise, move to one, let go. */
    @Test fun sAccents() = scene("accents") {
        cap = "accents"; show("viva pi"); hold(800)
        walkTo("n"); hold(300)
        mark("holdA"); kb.pressA(); hold(1500)
        mark("move"); kb.moveFocus(1, 0); hold(500); mark("move"); kb.moveFocus(-1, 0); hold(600)
        mark("releaseA"); kb.releaseA(); hold(600)
        before += "ata"; kb.setPreview(before, "", ""); hold(2000)
    }

    /** Hold RT: tips on the keys, RB jumps a word. */
    @Test fun sHoldRt() = scene("holdrt") {
        cap = "holdrt"
        val words = listOf("fable", " the", " lost", " chapters")
        before = ""; after = words.joinToString(""); kb.setHeldShift(true); pic.held = setOf("RT"); pic.holding = setOf("RT"); hold(1200)
        for (i in 1..words.size) { mark("RB"); pic.flash("RB", 320); before = words.take(i).joinToString(""); after = words.drop(i).joinToString(""); hold(850) }
        hold(1200)
    }

    /** Select with View, grow with RB, Copy, then the clipboard. */
    @Test fun sCopy() = scene("copy") {
        cap = "copy"
        val words = listOf("conker", " live", " and", " reloaded")
        before = ""; sel = ""; after = words.joinToString(""); hold(900)
        for (i in 1..words.size) {
            pic.flash(if (i == 1) "SELECT" else "RB", 320)
            sel = words.take(i).joinToString(""); after = words.drop(i).joinToString(""); kb.setSelecting(true, sel.length); hold(700)
        }
        hold(500)
        var g = 0; while (g++ < 30 && kb.stepToTool(Tool.COPY) == false) hold(170)
        kb.pressSelected(); kb.setSelecting(false, 0); before = words.joinToString(""); sel = ""; after = ""
        kb.setClips(listOf(before, "jet set radio future", "github.com/dakingeman/inlay", "ninja gaiden")); hold(800)
        g = 0; while (g++ < 30 && kb.stepToTool(Tool.HISTORY) == false) hold(170)
        cap = "clips"; kb.pressSelected(); hold(2400)
    }

    /** Tidies up as you type. */
    @Test fun sTidy() = scene("tidy") {
        cap = "tidy"; tidy = true; hold(800); type("i think i beat it . what a game", 55, 130); hold(2200)
    }

    /** The number row. */
    @Test fun sNumbers() = scene("numbers", numberRow = true) { cap = "numbers"; hold(800); type("mechassault 2", 70, 160); hold(2000) }

    /** Made for what you hold: the picture goes round every device, the keyboard keeps working. */
    @Test fun sDevices() = scene("devices") {
        cap = "devices"; hold(600)
        val path = listOf(1 to 0, 0 to 1, 1 to 0, 0 to -1)
        for ((d, kind, sticks) in listOf(Triple("controller", DeviceKind.CONTROLLER, false), Triple("wide", W, false),
                Triple("vertical", DeviceKind.VERTICAL, false), Triple("vertical-sticks", DeviceKind.VERTICAL, true), Triple("remote", DeviceKind.REMOTE, false))) {
            dev = d; pic.show(kind, X, sticks); mark("device")
            path.forEachIndexed { i, (dx, dy) -> if (i > 0) mark("move"); kb.moveFocus(dx, dy); hold(330) }
        }
    }

    /** TV mode: the compact keyboard with its big line, typed with a remote. */
    @Test fun sTv() = scene("tv", kind = DeviceKind.REMOTE, kbWidthDp = 520,
        prep = { copy(tvMode = TvMode.ON, showHints = false, comboHints = false, wordSuggestions = false) }) {
        cap = "tv"; pressName = "OK"; kb.setTvMode(true); show(""); hold(900); type("stranger t", 120, 260); hold(2000)
    }

    // ---- Whole app screens (Settings, the tour, What's new), recorded at the Odin 2 Portal's size ----

    private inner class ScreenRec(name: String) {
        val dir = File("build/visuals/video/$name").apply { deleteRecursively(); mkdirs() }
        val frames = JSONArray()
        var cap = ""; var ev = ""
        lateinit var a: android.app.Activity
        var probe: () -> JSONObject = { JSONObject() }
        private var buf: Pair<Bitmap, Canvas>? = null
        fun mark(e: String) { ev = if (ev.isEmpty()) e else "$ev+$e" }
        fun hold(ms: Long) {
            repeat(Math.round(ms * 30 / 1000.0).toInt()) {
                idle(33)
                val i = frames.length()
                if (i % 15 == 0) { System.gc(); System.runFinalization() }
                val v = a.window.decorView
                var b = buf
                if (b == null || b.first.width != v.width || b.first.height != v.height) {
                    b?.first?.recycle(); val bm = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); b = bm to Canvas(bm); buf = b
                }
                b.first.eraseColor(android.graphics.Color.BLACK); v.draw(b.second)
                File(dir, "scr-%04d.png".format(i)).outputStream().use { b.first.compress(Bitmap.CompressFormat.PNG, 100, it) }
                frames.put(probe().put("cap", cap).put("ev", ev)); ev = ""
            }
        }
        fun press(code: Int, name: String) {
            mark(name)
            val t = android.os.SystemClock.uptimeMillis()
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0))
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0))
        }
        fun save() { File(dir, "frames.json").writeText(frames.toString()) }
    }

    /** Settings as it is on a handheld that has Inlay switched on and in use. */
    private fun ready(s: KeyboardSettings, odin: Boolean = false) {
        if (odin) { org.robolectric.shadows.ShadowBuild.setManufacturer("AYN"); org.robolectric.shadows.ShadowBuild.setModel("Odin2 Portal") }
        val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        val info = android.view.inputmethod.InputMethodInfo(context.packageName, "${context.packageName}.GridKeyboardService", "Inlay", null)
        shadowOf(imm).setEnabledInputMethodInfoList(listOf(info))
        android.provider.Settings.Secure.putString(context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
            "${context.packageName}/.GridKeyboardService")
        s.save(context)
    }
    private fun screenBase(f: KeyboardSettings.() -> KeyboardSettings = { this }) = KeyboardSettings(tutorialDone = true,
        whatsNewSeen = WhatsNew.VERSION, buttonStyle = X, showHints = true, comboHints = true, wordSuggestions = true,
        holdAccents = true, showPreview = false).f()
    private fun screen(name: String, body: ScreenRec.() -> Unit) {
        assumeTrue(File("build/visuals-request").exists())
        val r = ScreenRec(name); r.body(); r.save()
    }
    @Suppress("UNCHECKED_CAST")
    private fun rows(s: SettingsActivity) =
        SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(s) as List<View>
    private fun has(v: View, t: String): Boolean = (v is android.widget.TextView && v.text.toString() == t) ||
        (v is android.view.ViewGroup && (0 until v.childCount).any { has(v.getChildAt(it), t) })

    /** Automatic recognises the handheld by name (here an AYN Odin 2 Portal). */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun sAuto() = screen("auto") {
        ready(screenBase { copy(picture = PictureChoice.AUTO) }, odin = true)
        a = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle(800)
        cap = "auto"; rows(a as SettingsActivity).first().requestFocusFromTouch(); hold(3200)
    }

    /** What's new, page 2: every sound pack types its own name while the name pops up. */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun sSounds() = screen("sounds") {
        ready(screenBase { copy(picture = PictureChoice.HANDHELD, whatsNewSeen = "1.1.1") })
        a = org.robolectric.Robolectric.buildActivity(TutorialActivity::class.java,
            android.content.Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(1500)
        val fieldF = TutorialActivity::class.java.getDeclaredField("field").apply { isAccessible = true }
        val typedF = TutorialActivity::class.java.getDeclaredField("typed").apply { isAccessible = true }
        probe = { val f = fieldF.get(a) as DemoFieldView; JSONObject().put("tag", f.tag ?: "").put("typed", typedF.get(a) as String) }
        cap = "sounds"
        press(android.view.KeyEvent.KEYCODE_BUTTON_A, "A")
        hold(21500)
    }

    /** The custom colour: Colour, Strength and Brightness, moved with the D-pad. */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun sColour() = screen("colour") {
        ready(screenBase { copy(picture = PictureChoice.HANDHELD, accentIndex = -1, customHue = 200, customStrength = 55, customBrightness = 72) })
        val s = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); a = s; idle(800)
        press(android.view.KeyEvent.KEYCODE_BUTTON_R1, "RB"); idle(800)
        rows(s).first { has(it, "Custom colour") }.requestFocusFromTouch(); idle(1000)
        cap = "colour"; hold(700)
        repeat(17) { press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, "right"); hold(130) }
        hold(500)
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN, "down"); hold(600)
        repeat(6) { press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, "right"); hold(130) }
        hold(500)
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN, "down"); hold(600)
        repeat(4) { press(android.view.KeyEvent.KEYCODE_DPAD_LEFT, "left"); hold(130) }
        hold(1200)
    }

    /** The tour's welcome page in the colour picked above: the backdrop's glow follows it. */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun sTourLook() = screen("tourlook") {
        ready(screenBase { copy(picture = PictureChoice.HANDHELD, tutorialDone = false, accentIndex = -1, customHue = 10, customStrength = 85, customBrightness = 52) })
        a = org.robolectric.Robolectric.buildActivity(TutorialActivity::class.java).setup().get(); idle(600)
        cap = "tourlook"; hold(4500)
    }

    /** Settings made for the controller: LB and RB through the tabs, Y brings the keyboard up and the tabs fold. */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun sSettings() = screen("settings") {
        ready(screenBase { copy(picture = PictureChoice.HANDHELD) })
        val s = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); a = s; idle(800)
        rows(s).first().requestFocusFromTouch(); idle(400)
        cap = "settings"; hold(800)
        repeat(3) { press(android.view.KeyEvent.KEYCODE_BUTTON_R1, "RB"); hold(750) }
        rows(s).first().requestFocusFromTouch()
        press(android.view.KeyEvent.KEYCODE_BUTTON_Y, "Y"); hold(1600)
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, "move"); hold(300)
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, "move"); hold(1000)
    }

    /** Settings → Sound & haptics: the Sound pack row, stepped through every pack with the D-pad. */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun sPackRow() = screen("packrow") {
        ready(screenBase { copy(picture = PictureChoice.HANDHELD) })
        val s = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); a = s; idle(800)
        repeat(3) { press(android.view.KeyEvent.KEYCODE_BUTTON_R1, "RB"); idle(500) }
        rows(s).first { has(it, "Sound pack") }.requestFocusFromTouch(); idle(1200)
        cap = "packrow"; hold(900)
        repeat(8) { press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, "right"); hold(900) }
        hold(600)
    }
}
