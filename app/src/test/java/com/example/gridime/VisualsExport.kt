package com.example.gridime

import android.graphics.Bitmap
import android.graphics.Canvas
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
 * Renders the real keyboard and device pictures for the GitHub images and videos
 * (docs/visuals-src composes them). Only runs when asked:
 *   touch app/build/visuals-request && ./gradlew testDebugUnitTest --tests '*VisualsExport*'
 * Output: app/build/visuals/<name>.png, drawn by the app's own code at 3× (xxhdpi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h560dp-land-xxhdpi")
class VisualsExport {
    private val TEAL = android.graphics.Color.parseColor("#72D6E8")
    private val context get() = RuntimeEnvironment.getApplication()
    private val out = File("build/visuals").apply { mkdirs() }
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun save(v: View, name: String) {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
    }

    private fun base(style: ButtonStyle = ButtonStyle.XBOX, f: (KeyboardSettings) -> KeyboardSettings = { it }) =
        f(KeyboardSettings(tutorialDone = true, whatsNewSeen = WhatsNew.VERSION, buttonStyle = style,
            showHints = true, comboHints = true, wordSuggestions = true, holdAccents = true))

    /** A keyboard [widthDp] wide in the state [setup] puts it in. */
    private fun kb(name: String, s: KeyboardSettings, widthDp: Int = 640, setup: KeyGridView.() -> Unit = {}) {
        val v = KeyGridView(context)
        v.applySettings(s)
        v.setField(FieldKind.TEXT)
        v.listener = object : KeyGridView.Listener {
            override fun onText(text: String) {}; override fun onDelete() {}; override fun onEnter() {}
            override fun onTool(tool: Tool) {}; override fun onClip(index: Int) {}
            override fun onReplaceLast(expected: String, replacement: String) = true
        }
        attach(v, widthDp)
        // For the images only: hint chips get a clearer fill and an edge in the highlight colour, so they read on a phone
        fun set(n: String, c: Int) = KeyGridView::class.java.getDeclaredField(n).apply { isAccessible = true }.setInt(v, c)
        set("chipFill", Contrast.withAlpha(android.graphics.Color.WHITE, 0x22))
        set("chipEdge", Contrast.withAlpha(s.accent, 0xB0))
        v.setup()
        idle(600)
        save(v, name)
    }

    /** A device picture [heightDp] tall with [held] lit (and [holding] showing the full hold ring). */
    private fun pic(name: String, kind: DeviceKind, style: ButtonStyle, sticks: Boolean = false, held: Set<String> = emptySet(),
                    holding: Set<String> = emptySet(), widthDp: Int = 600, heightDp: Float = 330f, accent: Int = TEAL) {
        val v = DevicePictureView(context).apply {
            this.accent = accent
            maxHeightDp = heightDp
            tallBoost = false
            show(kind, style, sticks)
            this.held = held; this.holding = holding
        }
        attach(v, widthDp)
        idle(900)
        save(v, name)
    }

    /** On a real window, so its timers run (a view that isn't on screen never runs them). */
    private fun attach(v: View, widthDp: Int) {
        val host = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val frame = android.widget.FrameLayout(host)
        host.setContentView(frame)
        val dp = context.resources.displayMetrics.density
        frame.addView(v, android.widget.FrameLayout.LayoutParams((widthDp * dp).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        idle(100)
    }

    /** Runs [block] with Android's animations off, so things that slide in are simply there (as with "Remove animations"). */
    private fun instantAnimations(block: () -> Unit) {
        val set = android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
        set.invoke(null, 0f)
        try { block() } finally { set.invoke(null, 1f) }
    }

    private fun KeyGridView.walkTo(label: String) { repeat(30) { if (stepTowards(label) == true) return } }


    /** The hero banner's keyboard moments, each in the icons and games of a different platform. */
    @Test
    fun hero() {
        assumeTrue(File("build/visuals-hero").exists())
        // For the banner only: the hint chips get a clearer fill and a teal edge, so they read at a glance
        fun KeyGridView.boost(edge: Int = TEAL) {
            fun set(n: String, c: Int) = KeyGridView::class.java.getDeclaredField(n).apply { isAccessible = true }.setInt(this, c)
            set("chipFill", Contrast.withAlpha(android.graphics.Color.WHITE, 0x22))
            set("chipEdge", Contrast.withAlpha(edge, 0xB0))
        }
        val w = 560
        // One moment per device, all in Xbox icons (the device pictures are Xbox too); the typed games come from every platform
        kb("hero-solid", base(), w) { boost(); walkTo("l"); setSuggestions(listOf("solid", "sold", "solo"), "sol") }
        kb("hero-holdrt", base(), w) { boost(); walkTo("g"); setHeldShift(true) }
        kb("hero-select", base(), w) { boost(); walkTo("d"); setSelecting(true, 30) }
        // The same moment in other highlight colours, for the backdrop that follows the user's colour
        for (i in listOf(9, 4, 12, 1, 7, 10)) kb("hero-holdrt-a$i", base { it.copy(accentIndex = i) }, w) { boost(Palette.accents[i].second); walkTo("g"); setHeldShift(true) }
        // A colour from the slider (custom hue), with its hex written next to it for the mock-ups
        for (hue in listOf(330, 45)) {
            val c = Palette.customAccent(hue)
            File(out, "hero-holdrt-h$hue.txt").writeText("%06X".format(c and 0xFFFFFF))
            kb("hero-holdrt-h$hue", base { it.copy(accentIndex = -1, customHue = hue) }, w) { boost(c); walkTo("g"); setHeldShift(true) }
        }
        // With a remote the controller hints are hidden, as in the app
        kb("hero-tv", base { it.copy(tvMode = TvMode.ON, showHints = false, comboHints = false) }, widthDp = 520) { boost(); setTvMode(true); walkTo("h"); setPreview("stranger t", "", "") }
    }

    /** The keyboard at each device's real screen width, for the studio hero (handheld + vertical). */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun studioOdin() {
        assumeTrue(File("build/visuals-studio").exists())
        val s = base { it.copy(picture = PictureChoice.HANDHELD) }
        kb("studio-odin-solid", s, 827) { walkTo("l"); setSuggestions(listOf("solid", "sold", "solo"), "sol") }
        kb("studio-odin-rep", s, 827) { walkTo("p"); setSuggestions(listOf("republic", "report", "reply"), "rep") }
    }

    @Test @Config(sdk = [34], qualifiers = "w472dp-h363dp-land-420dpi")
    fun studioPocketClassic() {
        assumeTrue(File("build/visuals-studio").exists())
        val s = base { it.copy(picture = PictureChoice.VERTICAL) }
        kb("studio-rpc-select", s, 472) { walkTo("d"); setSelecting(true, 30) }
        kb("studio-rpc-solid", s, 472) { walkTo("l"); setSuggestions(listOf("solid", "sold", "solo"), "sol") }
    }

    @Test
    fun stills() {
        assumeTrue(File("build/visuals-request").exists())
        val x = base()
        // The keyboard in every state the images show (Xbox icons unless the image is about icons)
        kb("kb-suggest", x) { walkTo("p"); setSuggestions(listOf("republic", "report", "reply"), "rep") }
        kb("kb-type", x) { walkTo("i") }
        kb("kb-accents", x) { walkTo("n"); instantAnimations { pressA(); idle(400) } }
        kb("kb-holdrt", x) { walkTo("g"); setHeldShift(true) }
        kb("kb-select", x) { walkTo("g"); setSelecting(true, 24) }
        kb("kb-clips", x) { setClips(listOf("conker live and reloaded", "jet set radio future", "github.com/dakingeman/inlay", "ninja gaiden")); toggleClips() }
        kb("kb-numrow", base { it.copy(numberRow = true) }) { walkTo("2") }
        kb("kb-tidy", x) { walkTo(".") }
        kb("kb-tv", base { it.copy(tvMode = TvMode.ON) }, widthDp = 520) { setTvMode(true); walkTo("h"); setPreview("stranger t", "", "") }
        // Looks
        kb("look-light", base { it.copy(toneIndex = 8) }) { walkTo("g") }
        kb("look-outline", base { it.copy(toneIndex = 9, accentIndex = 7) }) { walkTo("g") }
        kb("look-midnight-coral", base { it.copy(toneIndex = 2, accentIndex = 9, keyShape = KeyShape.ROUND) }) { walkTo("g") }
        kb("look-forest-mint", base { it.copy(toneIndex = 3, accentIndex = 4, highlightStyle = HighlightStyle.SOLID) }) { walkTo("g") }
        kb("look-plum-lavender", base { it.copy(toneIndex = 6, accentIndex = 12, layout = KeyLayout.CLASSIC) }) { walkTo("g") }
        kb("look-numrow", base { it.copy(numberRow = true, keyShape = KeyShape.SHARP) }) { walkTo("g") }
        // the looks image: four very different set-ups (README "Make it look how you want")
        kb("look-light-ocean", base { it.copy(toneIndex = 8, accentIndex = 1) }) { walkTo("g") }
        kb("look-forest-lime", base { it.copy(toneIndex = 3, accentIndex = 5, keyShape = KeyShape.ROUND, highlightStyle = HighlightStyle.SOLID) }) { walkTo("g") }
        kb("look-mocha-tangerine", base { it.copy(toneIndex = 4, accentIndex = 8, keyShape = KeyShape.SHARP, numberRow = true) }) { walkTo("g") }
        kb("look-outline-orchid", base { it.copy(toneIndex = 9, accentIndex = 11, layout = KeyLayout.CLASSIC, highlightStyle = HighlightStyle.OUTLINE) }) { walkTo("g") }
        // Icons
        for (st in ButtonStyle.values()) kb("icons-${st.name.lowercase()}", base(st)) { walkTo("g") }
        // Languages
        for (l in listOf(Language.GERMAN, Language.FRENCH, Language.RUSSIAN, Language.TURKISH))
            kb("lang-${l.name.lowercase()}", base { it.copy(language1 = l) }) { walkTo(l.name.lowercase().take(1)) }

        // Device pictures: lit for each image (the hold ring shows a held button)
        val w = DeviceKind.WIDE
        pic("pic-hero", w, ButtonStyle.XBOX, held = setOf("A"))
        pic("pic-type", w, ButtonStyle.XBOX, held = setOf("A", "Dright"))
        pic("pic-words", w, ButtonStyle.XBOX, held = setOf("RS"))
        pic("pic-accents", w, ButtonStyle.XBOX, held = setOf("A"), holding = setOf("A"))
        pic("pic-holdrt", w, ButtonStyle.XBOX, held = setOf("RT", "RB"), holding = setOf("RT"))
        pic("pic-select", w, ButtonStyle.XBOX, held = setOf("SELECT", "RB"))
        pic("pic-tidy", w, ButtonStyle.XBOX, held = setOf("A"))
        pic("pic-numrow", w, ButtonStyle.XBOX, held = setOf("Dup"))
        pic("pic-tv", DeviceKind.REMOTE, ButtonStyle.XBOX, held = setOf("A"))
        pic("dev-controller", DeviceKind.CONTROLLER, ButtonStyle.XBOX, held = setOf("A"))
        pic("dev-wide", w, ButtonStyle.PLAYSTATION, held = setOf("A"))
        pic("dev-vertical", DeviceKind.VERTICAL, ButtonStyle.SWITCH, held = setOf("A"))
        pic("dev-vertical-sticks", DeviceKind.VERTICAL, ButtonStyle.SWITCH, sticks = true, held = setOf("A"))
        pic("dev-remote", DeviceKind.REMOTE, ButtonStyle.XBOX, held = setOf("A"))
        for (st in ButtonStyle.values()) pic("icons-pic-${st.name.lowercase()}", DeviceKind.CONTROLLER, st, held = setOf("A"), heightDp = 250f)
    }

    /** The Button guide's badges, drawn by the app (the words go next to them in the image). */
    @Test
    fun badges() {
        assumeTrue(File("build/visuals-request").exists())
        SettingsStyle.accent = TEAL
        val hints = listOf("{A}", "{X}", "{Y}", "{R2}", "{L2}", "{START}", "{B}", "hold {A}", "{R3}", "{L3}", "{R2} + {A}", "{R2} + {Y}",
            "{R2} + {L2}", "{L1} {R1}", "{R3} left or right", "{R2} + {L1} {R1}", "{SELECT}", "{SELECTx2}", "{R3} up or down", "{R2} + {X}")
        hints.forEachIndexed { i, h ->
            val v = BadgeLineView(context, HintText.parse(h), ButtonStyle.XBOX, Design.TYPE_CAPTION, SettingsStyle.textSecondary, badgeScale = 1.45f, gapBadgeToBadge = 6f, gapBadgeToText = 8f)
            v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth.coerceAtLeast(1), v.measuredHeight.coerceAtLeast(1))
            idle(50)
            save(v, "badge-%02d".format(i))
        }
        File(out, "badges.json").writeText(org.json.JSONArray(hints).toString())
    }

    /** Real screens, at the Odin 2 Portal's size. */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun screens() {
        assumeTrue(File("build/visuals-request").exists())
        fun press(a: android.app.Activity, code: Int) {
            val t = android.os.SystemClock.uptimeMillis()
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0))
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0))
            idle(300)
        }
        base { it.copy(picture = PictureChoice.HANDHELD, showPreview = false) }.save(context)
        val s = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(800)
        val names = listOf("device", "look", "typing", "sound", "guide", "about")
        names.forEachIndexed { i, n -> save(s.window.decorView, "screen-$n")
            // Sound & haptics again with the Sound pack row in view (the sound-packs image shows this, not vibration)
            if (n == "sound") {
                fun has(v: android.view.View, t: String): Boolean = (v is android.widget.TextView && v.text.toString() == t) ||
                    (v is android.view.ViewGroup && (0 until v.childCount).any { has(v.getChildAt(it), t) })
                @Suppress("UNCHECKED_CAST")
                val rows = SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(s) as List<android.view.View>
                rows.first { has(it, "Sound pack") }.requestFocusFromTouch(); idle(1200)
                save(s.window.decorView, "screen-soundpack")
                rows.first().requestFocusFromTouch(); idle(800) }
            if (i < names.lastIndex) { press(s, android.view.KeyEvent.KEYCODE_BUTTON_R1); idle(500) } }
        // Button guide, further down
        repeat(5) { press(s, android.view.KeyEvent.KEYCODE_BUTTON_R1) }
        repeat(4) { press(s, android.view.KeyEvent.KEYCODE_BUTTON_L1) }
        repeat(6) { press(s, android.view.KeyEvent.KEYCODE_DPAD_DOWN) }
        idle(600); save(s.window.decorView, "screen-guide-2")
        val wn = org.robolectric.Robolectric.buildActivity(TutorialActivity::class.java,
            android.content.Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
        idle(3500); save(wn.window.decorView, "screen-whatsnew-1")
        val tour = org.robolectric.Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
        idle(2500); press(tour, android.view.KeyEvent.KEYCODE_BUTTON_A); press(tour, android.view.KeyEvent.KEYCODE_BUTTON_A); press(tour, android.view.KeyEvent.KEYCODE_BUTTON_A)
        idle(2600); save(tour.window.decorView, "screen-tour-holdrt")
    }

    /** Preview of the tour / What's new background in several highlight colours (presets and the slider). */
    @Test
    @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun tourLooks() {
        assumeTrue(File("build/visuals-tour").exists())
        fun press(a: android.app.Activity, code: Int) {
            val t = android.os.SystemClock.uptimeMillis()
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0))
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0))
            idle(300)
        }
        val looks = listOf("inlay" to { k: KeyboardSettings -> k.copy(accentIndex = 2) }, "coral" to { k: KeyboardSettings -> k.copy(accentIndex = 9) },
            "lime" to { k: KeyboardSettings -> k.copy(accentIndex = 5) }, "slider300" to { k: KeyboardSettings -> k.copy(accentIndex = -1, customHue = 300) },
            "ice" to { k: KeyboardSettings -> k.copy(accentIndex = 15) })
        for ((n, f) in looks) {
            base { f(it.copy(picture = PictureChoice.HANDHELD)) }.save(context)
            val tour = org.robolectric.Robolectric.buildActivity(TutorialActivity::class.java).setup().get()
            idle(2500); save(tour.window.decorView, "tourlook-$n-tour1")
            press(tour, android.view.KeyEvent.KEYCODE_BUTTON_A); press(tour, android.view.KeyEvent.KEYCODE_BUTTON_A); press(tour, android.view.KeyEvent.KEYCODE_BUTTON_A)
            idle(2600); save(tour.window.decorView, "tourlook-$n-tour4")
            val wn = org.robolectric.Robolectric.buildActivity(TutorialActivity::class.java,
                android.content.Intent(context, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true)).setup().get()
            idle(3500); save(wn.window.decorView, "tourlook-$n-new1")
            press(wn, android.view.KeyEvent.KEYCODE_BUTTON_A); idle(2950); save(wn.window.decorView, "tourlook-$n-new2")
        }
    }

    /** Settings with the keyboard up: the slim section strip, on each screen shape. */
    private fun miniTabsShots(tag: String, pic: PictureChoice) {
        assumeTrue(File("build/visuals-tour").exists())
        fun press(a: android.app.Activity, code: Int) {
            val t = android.os.SystemClock.uptimeMillis()
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0))
            a.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0))
            idle(300)
        }
        base { it.copy(picture = pic, showPreview = false) }.save(context)
        val s = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(800)
        val remote = pic == PictureChoice.REMOTE
        @Suppress("UNCHECKED_CAST")
        fun focusRow() { (SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(s) as List<android.view.View>).first().requestFocusFromTouch(); idle(300) }
        focusRow()
        if (!remote) {
            press(s, android.view.KeyEvent.KEYCODE_BUTTON_R1); press(s, android.view.KeyEvent.KEYCODE_BUTTON_R1); idle(500)
            focusRow()
            press(s, android.view.KeyEvent.KEYCODE_BUTTON_Y); idle(1500)
        } else {
            val m = SettingsActivity::class.java.getDeclaredMethod("togglePreview").apply { isAccessible = true }
            m.invoke(s); idle(1500)
        }
        save(s.window.decorView, "minitabs-$tag")
        if (tag == "odin") {
            val m = SettingsActivity::class.java.getDeclaredMethod("applySheet", Float::class.javaPrimitiveType).apply { isAccessible = true }
            listOf(0f, 0.15f, 0.3f, 0.5f, 0.75f, 1f).forEach { f -> m.invoke(s, f); idle(50); save(s.window.decorView, "fold-${(f * 100).toInt()}") }
        }
    }
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi") fun miniTabsOdin() = miniTabsShots("odin", PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w480dp-h312dp-land-xhdpi") fun miniTabsSmall43() = miniTabsShots("small43", PictureChoice.HANDHELD)
    @Test @Config(sdk = [34], qualifiers = "w480dp-h432dp-land-hdpi") fun miniTabsSquare() = miniTabsShots("square", PictureChoice.VERTICAL)
    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi") fun miniTabsTv() = miniTabsShots("tv", PictureChoice.REMOTE)
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun miniTabsPhone() = miniTabsShots("phone", PictureChoice.CONTROLLER)

    /** The Look page with a custom colour: the three sliders. */
    @Test @Config(sdk = [34], qualifiers = "w827dp-h412dp-land-xhdpi")
    fun customColourLook() {
        assumeTrue(File("build/visuals-tour").exists())
        for ((n, set) in listOf("teal" to Triple(200, 55, 72), "vivid" to Triple(15, 100, 30), "soft" to Triple(140, 10, 85))) {
            base { it.copy(picture = PictureChoice.HANDHELD, accentIndex = -1, customHue = set.first, customStrength = set.second, customBrightness = set.third) }.save(context)
            val s = org.robolectric.Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            idle(800)
            val t = android.os.SystemClock.uptimeMillis()
            s.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_R1, 0))
            s.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_R1, 0))
            idle(800)
            fun has(v: android.view.View, txt: String): Boolean = (v is android.widget.TextView && v.text.toString() == txt) ||
                (v is android.view.ViewGroup && (0 until v.childCount).any { has(v.getChildAt(it), txt) })
            @Suppress("UNCHECKED_CAST")
            val rows = SettingsActivity::class.java.getDeclaredField("focusableRows").apply { isAccessible = true }.get(s) as List<android.view.View>
            rows.firstOrNull { has(it, "Strength") }?.requestFocusFromTouch(); idle(1200)
            save(s.window.decorView, "custom-colour-$n")
        }
    }
}
