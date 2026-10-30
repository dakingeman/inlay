package com.example.gridime

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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

/**
 * The whole app, every screen, in all three button styles, for the design audit.
 * Only runs when asked: touch app/build/visuals-audit. Output: app/build/audit/<style>/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
class AuditShots {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun save(dir: File, v: View, name: String) {
        val b = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b)); File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }
    private fun press(a: Activity, code: Int, wait: Long = 300) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0)); idle(wait)
    }
    private fun rows(v: View): List<SettingRow> = when (v) {
        is SettingRow -> listOf(v)
        is android.view.ViewGroup -> (0 until v.childCount).flatMap { rows(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun scrollers(v: View): List<android.view.ViewGroup> = when (v) {
        is android.widget.ScrollView -> listOf(v)
        is android.view.ViewGroup -> (0 until v.childCount).flatMap { scrollers(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun everything(style: ButtonStyle, pic: PictureChoice, tag: String = style.name.lowercase()) {
        assumeTrue(File("build/visuals-audit").exists())
        val dir = File("build/audit/$tag").apply { mkdirs() }
        KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style, picture = pic, showPreview = false).save(context)
        // Settings: every tab, at the top and with the first and last row focused
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(800)
        for (tab in 0 until 6) {
            val r = rows(s.window.decorView)
            r.firstOrNull()?.requestFocusFromTouch(); idle(500)
            save(dir, s.window.decorView, "settings-$tab-a-top")
            r.lastOrNull()?.requestFocusFromTouch(); idle(700)
            save(dir, s.window.decorView, "settings-$tab-b-bottom")
            // the whole tab in one tall picture (the scrolling list drawn at its full length), for page-by-page audits
            scrollers(s.window.decorView).maxByOrNull { it.width * it.height }?.getChildAt(0)?.let { list ->
                val b = Bitmap.createBitmap(list.width.coerceAtLeast(1), list.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                Canvas(b).apply { drawColor(Design.BACKDROP_BOTTOM); list.draw(this) }
                File(dir, "settings-$tab-c-full.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
            }
            press(s, KeyEvent.KEYCODE_BUTTON_R1, 600)
        }
        // Settings with the keyboard up (Y)
        rows(s.window.decorView).firstOrNull()?.requestFocusFromTouch(); idle(400)
        press(s, KeyEvent.KEYCODE_BUTTON_Y, 1500)
        save(dir, s.window.decorView, "settings-kb-up")
        // What's new and the tour, every page
        val wn = Robolectric.buildActivity(TutorialActivity::class.java,
            Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        repeat(wn.pageCheck().size) { i -> idle(if (i == 0) 3200 else 3600); save(dir, wn.window.decorView, "whatsnew-$i"); press(wn, KeyEvent.KEYCODE_BUTTON_A) }
        val tour = Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        repeat(tour.pageCheck().size) { i -> idle(if (i == 0) 1500 else 3000); save(dir, tour.window.decorView, "tour-$i"); press(tour, KeyEvent.KEYCODE_BUTTON_A) }
    }

    @Test fun xbox() = everything(ButtonStyle.XBOX, PictureChoice.HANDHELD)
    @Test fun playstation() = everything(ButtonStyle.PLAYSTATION, PictureChoice.CONTROLLER)
    @Test fun switch() = everything(ButtonStyle.SWITCH, PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun switchVertical() = everything(ButtonStyle.SWITCH, PictureChoice.VERTICAL, "vertical")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun phone() = everything(ButtonStyle.XBOX, PictureChoice.AUTO, "phone")

    /** The tour and What's new backdrop with every preset colour, and the first page of each in a few telling colours. */
    @Test
    fun backdrops() {
        assumeTrue(File("build/visuals-audit").exists())
        val dir = File("build/audit/backdrops").apply { mkdirs() }
        for ((i, pair) in Palette.accents.withIndex()) for (wn in listOf(false, true)) {
            val b = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
            Backdrop(pair.second, wn).apply { setBounds(0, 0, 480, 270) }.draw(Canvas(b))
            File(dir, "%02d-%s-%s.png".format(i, pair.first, if (wn) "whatsnew" else "tour")).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
        for ((name, idx) in listOf("lemon" to 6, "coral" to 9, "orchid" to 11, "ice" to 15)) {
            KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, accentIndex = idx, picture = PictureChoice.HANDHELD, showPreview = false).save(context)
            for (wn in listOf(false, true)) {
                val a = Robolectric.buildActivity(TutorialActivity::class.java,
                    Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, wn)).setup().get()
                if (!wn) press(a, KeyEvent.KEYCODE_BUTTON_A)
                idle(2500); save(dir, a.window.decorView, "screen-$name-${if (wn) "whatsnew" else "tour"}")
                a.finish()
            }
        }
    }

    /**
     * The keyboard in every language, for looking at (CombinationsTest measures the same keyboards): letters with
     * the language on the space bar, shift, a held letter's accents, and the Classic layout. build/audit/languages/<size>/
     */
    private fun languages(size: String) {
        assumeTrue(File("build/visuals-audit").exists())
        val dir = File("build/audit/languages/$size").apply { mkdirs() }
        val width = context.resources.displayMetrics.widthPixels
        val set = android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
        for (lang in Language.values()) {
            val other = if (lang == Language.ENGLISH) Language.GERMAN else Language.ENGLISH
            val vowel = if (lang == Language.RUSSIAN || lang == Language.UKRAINIAN) "е" else "e"
            fun kb(state: String, s0: KeyboardSettings.() -> KeyboardSettings = { this }, act: KeyGridView.() -> Unit = {}) {
                val host = Robolectric.buildActivity(Activity::class.java).setup().get()
                val frame = android.widget.FrameLayout(host); host.setContentView(frame)
                val v = KeyGridView(context)
                v.applySettings(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, language1 = lang, language2 = other,
                    showHints = true, comboHints = true, wordSuggestions = true, holdAccents = true).s0())
                v.setField(FieldKind.TEXT)
                v.listener = object : KeyGridView.Listener {
                    override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
                    override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
                    override fun onReplaceLast(expected: String, replacement: String) = true
                }
                frame.addView(v, android.widget.FrameLayout.LayoutParams(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                idle(500)
                set.invoke(null, 0f)
                try { v.act(); idle(500) } finally { set.invoke(null, 1f) }
                save(dir, v, "${lang.name.lowercase()}-$state")
            }
            kb("letters")
            kb("shift") { cycleShift() }
            kb("accents") { repeat(30) { if (stepTowards(vowel) == true) return@repeat }; pressA(); idle(400) }
            kb("classic", { copy(layout = KeyLayout.CLASSIC) })
            kb("largest-shift", { copy(sizeLevel = 2) }) { cycleShift() }
        }
    }
    @Test fun languagesHandheld() = languages("handheld")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun languagesVertical() = languages("vertical")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun languagesPhone() = languages("phone")

    /**
     * Every keyboard state at one screen size, for the visual audit (30 Oct 2026). build/audit/states/<size>/
     */
    private fun states(size: String) {
        assumeTrue(File("build/visuals-audit").exists())
        val dir = File("build/audit/states/$size").apply { mkdirs() }
        val width = context.resources.displayMetrics.widthPixels
        val set = android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
        fun kb(state: String, field: FieldKind = FieldKind.TEXT, s0: KeyboardSettings.() -> KeyboardSettings = { this }, act: KeyGridView.() -> Unit = {}) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().get()
            val frame = android.widget.FrameLayout(host); host.setContentView(frame)
            val v = KeyGridView(context)
            v.applySettings(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, language2 = Language.GERMAN,
                showHints = true, comboHints = true, wordSuggestions = true, holdAccents = true).s0())
            v.setField(field)
            v.listener = object : KeyGridView.Listener {
                override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
                override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
                override fun onReplaceLast(expected: String, replacement: String) = true
            }
            frame.addView(v, android.widget.FrameLayout.LayoutParams(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            idle(500)
            set.invoke(null, 0f)
            try { v.act(); idle(500) } finally { set.invoke(null, 1f) }
            save(dir, v, state)
        }
        val clips = listOf("metal gear solid", "AYN Odin 2 Portal", "https://github.com/dakingeman/inlay", "see you at 8", "Wien", "1234 5678")
        kb("01-letters")
        kb("02-words") { setSuggestions(listOf("republic", "remember", "reply"), "re") }
        kb("03-rt-held") { setHeldShift(true) }
        kb("04-caps-lock") { cycleShift(); cycleShift() }
        kb("05-symbols") { toggleLayer(fromKey = true) }
        kb("06-symbols-tr", s0 = { copy(language1 = Language.TURKISH) }) { toggleLayer(fromKey = true) }
        kb("07-symbols-ru", s0 = { copy(language1 = Language.RUSSIAN) }) { toggleLayer(fromKey = true) }
        kb("08-numpad", FieldKind.NUMBER)
        kb("09-url", FieldKind.URL)
        kb("10-email", FieldKind.EMAIL)
        kb("11-clips") { setClips(clips); toggleClips() }
        kb("12-clips-empty") { setClips(emptyList()); toggleClips() }
        kb("13-select") { setSelecting(true, 30) }
        kb("14-paste-preview") { setClips(clips) }
        kb("15-accents") { repeat(30) { if (stepTowards("e") == true) return@repeat }; pressA(); idle(400) }
        kb("16-number-row", s0 = { copy(numberRow = true) })
        kb("17-largest", s0 = { copy(sizeLevel = 2) })
        kb("18-light", s0 = { copy(toneIndex = Palette.tones.indexOfFirst { it.name == "Light" }.coerceAtLeast(0)) })
        kb("19-tv") { setTvMode(true) }
        kb("20-no-sticks", s0 = { copy(hasSticks = false) }) { setSuggestions(listOf("republic", "remember", "reply"), "re") }
    }
    @Test fun statesHandheld() = states("handheld")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun statesVertical() = states("vertical")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun statesPhone() = states("phone")

    /**
     * The accent tray and number row ("Keys", 30 Oct 2026) in every theme, highlight style, key shape, font and size,
     * with the tray open on e and the number row on. build/audit/tray/<size>/
     */
    private fun tray(size: String) {
        assumeTrue(File("build/visuals-audit").exists())
        val dir = File("build/audit/tray/$size").apply { mkdirs() }
        val width = context.resources.displayMetrics.widthPixels
        val set = android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
        val base = KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, numberRow = true,
            showHints = true, comboHints = true, wordSuggestions = true, holdAccents = true)
        val looks = Palette.tones.indices.map { "theme-${Palette.tones[it].name.lowercase()}" to base.copy(toneIndex = it) } +
            HighlightStyle.values().map { "style-${it.name.lowercase()}" to base.copy(highlightStyle = it) } +
            KeyShape.values().map { "shape-${it.name.lowercase()}" to base.copy(keyShape = it) } +
            FontChoice.values().map { "font-${it.name.lowercase()}" to base.copy(font = it) } +
            (0..2).map { "size-$it" to base.copy(sizeLevel = it) } +
            listOf("lang-russian" to base.copy(language1 = Language.RUSSIAN), "lang-turkish" to base.copy(language1 = Language.TURKISH),
                "accent-cherry" to base.copy(accentIndex = Palette.accents.indexOfFirst { it.first == "Cherry" }),
                "accent-lemon" to base.copy(accentIndex = Palette.accents.indexOfFirst { it.first == "Lemon" }),
                "tv" to base.copy(tvMode = TvMode.ON))
        for ((name, s0) in looks) for (state in listOf("accents", "numbers")) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().get()
            val frame = android.widget.FrameLayout(host); host.setContentView(frame)
            val v = KeyGridView(context)
            v.applySettings(s0)
            if (s0.tvMode == TvMode.ON) v.setTvMode(true)
            v.setField(FieldKind.TEXT)
            v.listener = object : KeyGridView.Listener {
                override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
                override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
                override fun onReplaceLast(expected: String, replacement: String) = true
            }
            frame.addView(v, android.widget.FrameLayout.LayoutParams(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            idle(500)
            set.invoke(null, 0f)
            val held = if (s0.language1 == Language.RUSSIAN) "е" else "e"
            try {
                if (state == "accents") { repeat(30) { if (v.stepTowards(held) == true) return@repeat }; v.pressA(); idle(400); v.moveFocus(1, 0); idle(300) }
                else { repeat(30) { if (v.stepTowards("t") == true) return@repeat }; v.moveFocus(0, -1); idle(300) }
                idle(400)
            } finally { set.invoke(null, 1f) }
            save(dir, v, "$name-$state")
        }
    }
    @Test fun trayHandheld() = tray("handheld")
    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi") fun trayVertical() = tray("vertical")
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xxhdpi") fun trayPhone() = tray("phone")

    /** The keyboard in each layer and state, per style. */
    @Test
    fun keyboards() {
        assumeTrue(File("build/visuals-audit").exists())
        for (style in ButtonStyle.values()) {
            val dir = File("build/audit/${style.name.lowercase()}").apply { mkdirs() }
            fun kb(name: String, field: FieldKind = FieldKind.TEXT, s0: KeyboardSettings.() -> KeyboardSettings = { this }, setup: KeyGridView.() -> Unit = {}) {
                val host = Robolectric.buildActivity(Activity::class.java).setup().get()
                val frame = android.widget.FrameLayout(host); host.setContentView(frame)
                val v = KeyGridView(context)
                v.applySettings(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style,
                    showHints = true, comboHints = true, wordSuggestions = true, holdAccents = true).s0())
                v.setField(field)
                val dp = context.resources.displayMetrics.density
                frame.addView(v, android.widget.FrameLayout.LayoutParams((827 * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                idle(100); v.setup(); idle(600); save(dir, v, "kb-$name")
            }
            kb("letters")
            kb("url", FieldKind.URL)
            kb("symbols") { toggleLayer(fromKey = true) }
            kb("numpad", FieldKind.NUMBER)
            kb("suggest") { setSuggestions(listOf("republic", "report", "reply"), "rep") }
            kb("clips") { setClips(listOf("metal gear solid", "AYN Odin 2 Portal", "https://github.com/dakingeman/inlay")); toggleClips() }
            kb("select") { setSelecting(true, 30) }
            kb("holdrt") { setHeldShift(true) }
            kb("lift-url", FieldKind.URL, { copy(highlightStyle = HighlightStyle.LIFT) })
            kb("outline-round", s0 = { copy(highlightStyle = HighlightStyle.OUTLINE, keyShape = KeyShape.ROUND) })
            kb("light", s0 = { copy(toneIndex = Palette.tones.indexOfFirst { it.name == "Light" }.coerceAtLeast(0)) })
            kb("tv") { setTvMode(true) }
        }
    }

    /** Phase 2: the keyboard on every theme, in every highlight style, plus hint chips (the accent tray is in VisualsExport's studio shots). build/audit/themes/ */
    @Test
    fun themes() {
        assumeTrue(File("build/visuals-audit").exists())
        val dir = File("build/audit/themes").apply { mkdirs() }
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host); host.setContentView(frame)
        val dp = context.resources.displayMetrics.density
        Palette.tones.forEachIndexed { ti, tone ->
            for ((name, style, extra) in listOf(
                Triple("glow", HighlightStyle.GLOW, ""), Triple("solid", HighlightStyle.SOLID, ""), Triple("outline", HighlightStyle.OUTLINE, ""),
                Triple("lift", HighlightStyle.LIFT, ""), Triple("chips", HighlightStyle.GLOW, "chips"))) {
                frame.removeAllViews()
                val v = KeyGridView(context)
                v.applySettings(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = ButtonStyle.XBOX,
                    showHints = true, comboHints = true, wordSuggestions = true, holdAccents = true, toneIndex = ti, highlightStyle = style, seeThrough = 3))
                v.setField(FieldKind.TEXT)
                frame.setBackgroundColor(if (tone.clear) android.graphics.Color.parseColor("#3A6E8F") else android.graphics.Color.BLACK)
                frame.addView(v, android.widget.FrameLayout.LayoutParams((827 * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                idle(400)
                repeat(30) { if (v.stepTowards("n") == true) return@repeat }
                when (extra) {
                    "chips" -> v.setHeldShift(true)
                    else -> v.setSuggestions(listOf("nintendo", "nine", "night"), "ni")
                }
                idle(600)
                val b = Bitmap.createBitmap(frame.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(b); frame.background?.draw(c); v.draw(c)
                File(dir, "${ti.toString().padStart(2, '0')}-${tone.name.lowercase()}-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                b.recycle()
            }
        }
    }

    /** Every badge in every style, on a key and on the dark background, real size and 4x. */
    @Test
    fun badgeSheet() {
        assumeTrue(File("build/visuals-audit").exists())
        val dir = File("build/audit").apply { mkdirs() }
        val dp = context.resources.displayMetrics.density
        for (zoom in listOf(1f, 4f)) {
            val p = BadgePainter(dp).apply { scale = zoom; gapBadgeToText = 4f; gapBadgeToBadge = 2f }
            val cell = 40f * dp * zoom
            val buttons = ControllerButton.values()
            val w = (cell * (buttons.size + 7)).toInt(); val h = (cell * 3 * 2).toInt()
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
            c.drawColor(android.graphics.Color.parseColor("#0B0B0E"))
            val key = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#1B1B1F") }
            ButtonStyle.values().forEachIndexed { si, st ->
                p.style = st
                for (onKey in listOf(false, true)) {
                    val y = cell * (si * 2 + (if (onKey) 1 else 0)) + cell / 2
                    if (onKey) c.drawRoundRect(0f, y - cell * 0.42f, w.toFloat(), y + cell * 0.42f, 9f * dp * zoom, 9f * dp * zoom, key)
                    var x = cell * 0.3f
                    buttons.forEach { bt -> p.draw(c, bt, x, y); x += cell }
                    x = p.draw(c, ControllerButton.A, x, y, presses = 2) + cell * 0.3f
                    x = p.drawHeld(c, ControllerButton.R2, x, y) + cell * 0.3f
                    for (d in "UDLR") x = p.drawDpad(c, d, x, y) + cell * 0.3f
                }
            }
            File(dir, "badges-${zoom.toInt()}x.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
