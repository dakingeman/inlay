package com.example.gridime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * WHAT'S NEW: EACH PAGE SHOWS ITS FEATURE WITH THE REAL APP (1.3, Emre 7 Nov). The keys, icons, highlight, accent tray
 * and device are Inlay's own views, in the user's theme and colour, laid out at this screen's keyboard width and scaled
 * to fit, so a page looks like the app on every screen.
 *
 *  - TOUR: the tour's three chapters, one real turn each, named on the text box: a capital Z for Zelda (RT held),
 *    é for Pokémon (A held, the accent tray), and Sonc fixed to Sonic (LB moves the cursor back, A types the i).
 *  - SPLIT: the keyboard over a demo app (the game library of docs/visuals-src/VISUAL_SYSTEM.md 6b). A line is drawn down
 *    its middle, the window parts in two, and every key glides and resizes into its place in the real split layout.
 *  - BUTTONS: your device; the camera moves in on the face buttons (the rest dims), each lights in the setup's order and
 *    its job arrives beside it.
 *  - SYMBOLS: hold LT, the camera moves in on the money sign and its relatives (the rest dims), the first is typed, back.
 *  - MORE: the small things as tiles ([WhatsNewShowcase]): they arrive once and keep going.
 *
 * Motion follows the studio rules (App Studio → Motion): arriving eases out, leaving eases in, moving eases in-out; the
 * focus zoom goes in over 500 ms and out over 650 ms on (0.4, 0, 0.2, 1), never past 250 %, and the dim (40 %, soft edge)
 * follows it. With "Remove animations" on, each scene shows its finished moment.
 */
class WhatsNewStage(context: Context, private val base: KeyboardSettings, private val controls: Controls) : FrameLayout(context) {

    var scene: WhatsNewShowcase.Scene? = null
        set(value) { if (field != null && value != field) crossFrom(); field = value; start(value) }

    /**
     * Page to page, the close-up crossfades (Emre, 8 Nov: the next page's picture cut in suddenly). The last frame of
     * the page you leave is kept and fades out over the new one in [CROSS_MS]; what both pages share (the device, the
     * window) doesn't blink, what changes dissolves. With "Remove animations" on, the new page simply shows.
     */
    private var crossShot: Bitmap? = null
    private var crossStart = 0L
    private val crossPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    /** Checks: how much of the page you left still shows (0 when the crossfade is over). */
    internal val crossLeftForCheck: Float get() = crossShot?.let { if (crossStart < 0) 1f else 1f - ((SystemClock.uptimeMillis() - crossStart).toFloat() / CROSS_MS).coerceIn(0f, 1f) } ?: 0f
    private fun crossFrom() {
        crossShot?.recycle(); crossShot = null
        if (!moving || width <= 0 || height <= 0) return
        crossShot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        crossStart = -1L                      // the clock starts on the first frame the new page is drawn (laying it out can take a frame)
    }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val shot = crossShot ?: return
        if (crossStart < 0) crossStart = SystemClock.uptimeMillis()
        val t = (SystemClock.uptimeMillis() - crossStart).toFloat() / CROSS_MS
        if (t >= 1f) { shot.recycle(); crossShot = null; return }
        crossPaint.alpha = (255 * (1f - t * t * (3f - 2f * t))).toInt()     // eased in and out: a real dissolve, not a flick
        canvas.drawBitmap(shot, 0f, 0f, crossPaint)
        postInvalidateOnAnimation()
    }

    private val dp = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val moving get() = WhatsNew.motionAllowed()
    private val accent = base.activeAccent
    private val style = base.shownButtonStyle

    // ================================================================ the real parts
    private val tiles = WhatsNewShowcase(context, accent, style)
    private val window = FrameLayout(context).apply {                                // the keyboard's window (clips the zoom)
        clipChildren = true
        // the zoom is cut off by the window's own round corners, never a square
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, windowRadius) }
        }
        clipToOutline = true
    }
    private val field = DemoFieldView(context).apply { accent = this@WhatsNewStage.accent }
    private val kb = keyboard().apply {
        // the tour clips its keyboard to 10 dp corners inside the window; so does this (in its own units, so it scales with it)
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, TutorialActivity.INNER_RADIUS_DP * resources.displayMetrics.density) }
        }
        clipToOutline = true
    }
    /**
     * The keyboard as the real one is drawn: at the device's own scale. The tour and What's new draw a little bigger on
     * some screens (TourScale: 10 % on the Odin), and split's halves keep their size in dp, so a split keyboard built in
     * that bigger scale had halves 10 % wider and a gap that much narrower (Emre, 8 Nov: 11 % against the real 18 %).
     */
    private val realContext: Context by lazy { context.createConfigurationContext(android.content.res.Resources.getSystem().configuration) }
    private val realDp get() = realContext.resources.displayMetrics.density
    /** Split: the real split keyboard, live while the halves are apart (typing hops between them). */
    private val kbSplit = keyboard(realContext).apply { allowSplit = true; applySettings(demoSettings(split = true)); visibility = GONE }
    private val picture = DevicePictureView(context).apply {
        accent = this@WhatsNewStage.accent
        tallBoost = false
        show(controls.kind, style, controls.sticks)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    /** The secret's teaser: the reward's own retro screen, "? ? ?" over drifting stars. */
    private val teaser = SecretScreen(context).apply { visibility = GONE; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    /** The spotlight's dim and the callouts, over everything. */
    private val spot = Spot()

    // what the demo text box holds: [before] the cursor and [after] it
    private var before = ""
    private var after = ""

    init {
        setWillNotDraw(false)
        clipChildren = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(tiles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        window.addView(field); window.addView(kb); window.addView(kbSplit)
        addView(window)
        addView(picture)
        addView(teaser, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(spot, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun keyboard(ctx: Context = context) = KeyGridView(ctx).apply {
        clearsBehind = false
        isFocusable = false
        allowSplit = false
        keepProportions = true
        // The device lights whatever the keyboard acts out (A, RT, LT), as in the tour
        onDemoInput = { key, held ->
            when (held) {
                null -> picture.flash(key)
                true -> { picture.held = picture.held + key; picture.holding = picture.holding + key }
                false -> { picture.held = picture.held - key; picture.holding = picture.holding - key }
            }
        }
        listener = object : KeyGridView.Listener {
            override fun onText(text: String) { before += text; showText() }
            override fun onDelete() { before = before.dropLast(1); showText() }
            override fun onEnter() {}
            override fun onTool(tool: Tool) {}
            override fun onClip(index: Int) {}
            override fun onReplaceLast(expected: String, replacement: String): Boolean {
                if (!before.endsWith(expected)) return false
                before = before.dropLast(expected.length) + replacement; showText(); return true
            }
        }
        applySettings(demoSettings())
    }

    /** The user's own keyboard look, at the normal size, no number row, as the tour shows it. */
    private fun demoSettings(split: Boolean = false) = base.copy(showHints = true, comboHints = true, holdAccents = true)
        .forControls(controls).copy(sizeLevel = 0, numberRow = false, wordSuggestions = false, split = split)

    private fun showText() = field.set(before, "", after)

    // ================================================================ layout
    private val stage = RectF()
    private val panel = RectF()
    private var unitK = 1f
    /** Split's own scale and left edge in the window: its keyboard is wider than the tour's, fitted into the same window. */
    private var splitK = 1f
    private var splitLeft = 0f
    /**
     * The window's corner: the text box's corner (scaled with everything inside) plus the gap round it, so the corners
     * stay parallel, as the tour's do (18 = 10 + 8) and as the last page's cards do (Emre, 8 Nov: the window's corners
     * looked off: they kept 18 dp while everything inside shrank).
     */
    private var windowRadius = 0f
    private var kbTop = 0f
    private var roomy = true

    /** The width the keyboard is laid out at: this screen's own keyboard width, up to [KEYBOARD_DP]. */
    private val keyboardWidthPx get() = (min(KEYBOARD_DP, resources.configuration.screenWidthDp.toFloat()) * dp).toInt()
    /**
     * Split is laid out exactly as wide as the real keyboard is on this device: the display's full width, which the
     * keyboard's window gets. An app's own idea of the screen's width can leave part of the edge out (on the Odin it
     * said 772 dp of 835), and as the halves keep their size, all of that came out of the gap (Emre, 8 Nov: the gap
     * looked too small). Never narrower than [KEYBOARD_DP], which split needs to split at all.
     */
    private val splitWidthPx get() = max((KEYBOARD_DP * realDp).toInt(), keyboardWindowWidthPx())
    private fun keyboardWindowWidthPx(): Int = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30) context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds.width() else null
    }.getOrNull() ?: resources.displayMetrics.widthPixels
    /** The window's padding: the tour's 8 dp, scaled with everything inside (so the window is the tour's, only smaller). */
    private var pad = PANEL_PAD_DP * 2f

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightSpec)
        else (w * WhatsNewShowcase.H / WhatsNewShowcase.W).toInt().let { if (MeasureSpec.getMode(heightSpec) == MeasureSpec.AT_MOST) min(it, MeasureSpec.getSize(heightSpec)) else it }
        setMeasuredDimension(w, h)
        tiles.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        spot.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = (r - l).toFloat(); val h = (b - t).toFloat()
        val s = min(w / WhatsNewShowcase.W, h / WhatsNewShowcase.H)
        stage.set((w - WhatsNewShowcase.W * s) / 2f, (h - WhatsNewShowcase.H * s) / 2f, (w + WhatsNewShowcase.W * s) / 2f, (h + WhatsNewShowcase.H * s) / 2f)
        tiles.layout(0, 0, r - l, b - t)
        spot.layout(0, 0, r - l, b - t)

        // The keyboard and its text box: laid out at a real keyboard's width, then scaled into the window. The tour's
        // arrangement: the device above, the window below; short rooms give the window everything
        // Split lays its keyboard out at the width split needs, and makes room for whichever layout is taller
        // The window is always the tour's (Emre, 8 Nov: going to Split, the window changed size and the device jumped):
        // Split's wider keyboard is fitted inside that same window instead of growing its own
        val split = scene == WhatsNewShowcase.Scene.SPLIT
        val kw = keyboardWidthPx
        kb.measure(MeasureSpec.makeMeasureSpec(kw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        if (split && (whole == null || shotWidth != splitWidthPx)) buildShots(SPLIT_FROM)
        val kh = kb.measuredHeight
        val fh = (FIELD_DP * dp).toInt()
        val gap = (Design.SPACE_S * dp).toInt()
        val natural = fh + gap + kh
        roomy = h / dp >= ROOM_FOR_DEVICE_DP
        val withDevice = scene == WhatsNewShowcase.Scene.TOUR || scene == WhatsNewShowcase.Scene.SYMBOLS || scene == WhatsNewShowcase.Scene.SPLIT
        val windowRoom = if (roomy && withDevice) h * WINDOW_SHARE else h
        // The tour's window as one piece: padding 8, corner 18, text box and keyboard 10 (DESIGN_GUIDE: nested corners
        // share a centre), all scaled by k; only the edge line keeps its real width
        val padDp = PANEL_PAD_DP * dp
        val k = min(w / (kw + 2 * padDp), windowRoom / (natural + 2 * padDp))
        pad = padDp * k
        val pw = kw * k + 2 * pad; val ph = natural * k + 2 * pad
        // the window stands on the floor under the device (the tour's arrangement); a short room has no device: centred
        val panelTop = h - ph                                      // the same on every page, Split included
        panel.set(w / 2f - pw / 2f, panelTop, w / 2f + pw / 2f, panelTop + ph)
        window.layout(panel.left.toInt(), panel.top.toInt(), panel.right.toInt(), panel.bottom.toInt())
        field.pivotX = 0f; field.pivotY = 0f; field.scaleX = k; field.scaleY = k
        field.layout(0, 0, kw, fh); field.translationX = pad; field.translationY = pad
        kb.layout(0, 0, kw, kb.measuredHeight)
        if (split) {
            // the split keyboard is laid out at the width it really has (the gap is the real one), then fitted into the
            // room under the text box, standing on the window's floor, centred
            val sw = splitWidthPx
            val room = ph - 2 * pad - (fh + gap) * k
            splitK = min((pw - 2 * pad) / sw, room / splitHeight)
            splitLeft = pad + ((pw - 2 * pad) - sw * splitK) / 2f
            kbSplit.measure(MeasureSpec.makeMeasureSpec(sw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            kbSplit.layout(0, 0, sw, kbSplit.measuredHeight)
            kbSplit.pivotX = 0f; kbSplit.pivotY = 0f; kbSplit.scaleX = splitK; kbSplit.scaleY = splitK
            kbSplit.translationX = splitLeft; kbSplit.translationY = panel.height() - pad - kbSplit.measuredHeight * splitK
        }
        kbTop = pad + (fh + gap) * k
        unitK = k
        windowRadius = PANEL_RADIUS_DP * dp * k                       // 18 = 8 + 10, scaled
        window.invalidateOutline()
        applyKbZoom()

        teaser.measure(MeasureSpec.makeMeasureSpec((r - l), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((b - t), MeasureSpec.EXACTLY))
        teaser.layout(0, 0, r - l, b - t)
        if (scene == WhatsNewShowcase.Scene.SECRET) layoutSecret(w, h)
        else if (scene == WhatsNewShowcase.Scene.BUTTONS) { picture.layout(0, 0, 0, 0); layoutSetup() }
        else if (roomy) placePicture(0f, panel.top - Design.SPACE_L * dp, 0f, w)
        else picture.layout(0, 0, 0, 0)

    }

    private fun placePicture(top: Float, bottom: Float, left: Float, right: Float) {
        val room = max(1, (bottom - top).toInt())
        picture.maxHeightDp = max(40f, room / dp)
        picture.measure(MeasureSpec.makeMeasureSpec((right - left).toInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST))
        val pH = picture.measuredHeight
        val pTop = (top + (room - pH) / 2f).toInt()
        picture.layout(left.toInt(), pTop, right.toInt(), pTop + pH)
    }

    // ================================================================ checks
    /** Checks: where the page's close-up sits. */
    internal val drawnForCheck: RectF get() = when (scene) {
        in TILES -> tiles.drawnForCheck
        WhatsNewShowcase.Scene.TOUR, WhatsNewShowcase.Scene.SYMBOLS -> RectF(panel)
        else -> RectF(stage)
    }
    internal val unitPxForCheck: Float get() = if (scene in TILES) tiles.unitPxForCheck else stage.width() / WhatsNewShowcase.W
    var minFit = 0.5f
    /** Checks: null when the close-up is big enough to read, otherwise why not. */
    internal fun tooSmallForCheck(): String? = when (scene) {
        in TILES -> (tiles.unitPxForCheck / dp).let { u -> val need = if (tiles.compactForCheck) 0.4f else 0.55f
            if (u < need) "tiles at ${"%.2f".format(u)} dp a unit (at least $need)" else null }
        WhatsNewShowcase.Scene.TOUR, WhatsNewShowcase.Scene.SYMBOLS ->
            if (unitK < minFit) "the keyboard at ${"%.2f".format(unitK)} of its size (at least $minFit)" else null
        else -> null
    }
    /** Checks: where the window and the device picture sit right now (null when hidden), in this view. */
    internal fun steadyForCheck(): List<String> = listOf(
        if (window.visibility == VISIBLE) "window ${window.left},${window.top},${window.right},${window.bottom}" else "window -",
        if (picture.visibility == VISIBLE && picture.width > 0) "device ${picture.left},${picture.top},${picture.right},${picture.bottom}" else "device -",
        "k ${"%.3f".format(unitK)}")
    /** Checks: what the tour scene's text box says now. */
    internal fun typedForCheck() = before + after

    // ================================================================ drawing
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        when (scene) {
            WhatsNewShowcase.Scene.TOUR, WhatsNewShowcase.Scene.SYMBOLS -> windowFrame(canvas, panel, windowRadius)
            WhatsNewShowcase.Scene.SPLIT -> { windowFrame(canvas, panel, windowRadius); drawSplit(canvas) }
            else -> Unit
        }
    }

    /** The tour's window: black, rounded, one solid edge (the tour's own: 1 dp of the track grey, never under 2 px), drawn inside it. */
    private fun windowFrame(canvas: Canvas, r: RectF, radius: Float) {
        fill.color = Design.SURFACE_BG; canvas.drawRoundRect(r, radius, radius, fill)
        line.color = Design.TRACK; line.strokeWidth = max(2f, Design.STROKE_HAIRLINE * dp)
        val hi = line.strokeWidth / 2f
        canvas.drawRoundRect(RectF(r).apply { inset(hi, hi) }, radius - hi, radius - hi, line)
    }

    // ================================================================ running a scene
    private fun start(s: WhatsNewShowcase.Scene?) {
        handler.removeCallbacksAndMessages(null)
        tweens.clear()
        zoom = 1f; zoomAt = null; spot.clear()
        if (s != WhatsNewShowcase.Scene.BUTTONS) setup?.let { removeView(it); setup = null }
        splitT = 0f; seam = 0f
        tiles.visibility = if (s in TILES) VISIBLE else GONE
        tiles.scene = if (s in TILES) s else null
        window.visibility = if (s == WhatsNewShowcase.Scene.TOUR || s == WhatsNewShowcase.Scene.SYMBOLS || s == WhatsNewShowcase.Scene.SPLIT) VISIBLE else GONE
        kb.visibility = if (s == WhatsNewShowcase.Scene.SPLIT) INVISIBLE else VISIBLE
        kbSplit.visibility = GONE
        window.alpha = 1f; field.alpha = 1f
        picture.visibility = if (s == WhatsNewShowcase.Scene.TOUR || s == WhatsNewShowcase.Scene.SYMBOLS || s == WhatsNewShowcase.Scene.SPLIT || s == WhatsNewShowcase.Scene.SECRET) VISIBLE else GONE
        if (s != WhatsNewShowcase.Scene.SECRET) teaser.visibility = GONE
        picture.held = emptySet(); picture.holding = emptySet()
        picture.scaleX = 1f; picture.scaleY = 1f; picture.translationX = 0f; picture.translationY = 0f
        requestLayout()
        when (s) {
            WhatsNewShowcase.Scene.TOUR -> chapter(0)
            WhatsNewShowcase.Scene.SYMBOLS -> symbols()
            WhatsNewShowcase.Scene.BUTTONS -> post { buttons() }
            WhatsNewShowcase.Scene.SPLIT -> splitScene()
            WhatsNewShowcase.Scene.SECRET -> secretScene()
            else -> Unit
        }
        invalidate()
    }

    /**
     * Tweens ticking about 60 times a second, each from its start time: they keep exact time on the device
     * and in the film checks alike (a ValueAnimator runs to its end at once in the checks, so motion couldn't be reviewed).
     */
    private class Tween(val start: Long, val ms: Long, val curve: android.animation.TimeInterpolator, val set: (Float) -> Unit)
    private val tweens = mutableListOf<Tween>()
    private val anims = object { fun forEach(f: (Any) -> Unit) {}; fun clear() { tweens.clear() } }
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.uptimeMillis()
            val done = mutableListOf<Tween>()
            tweens.toList().forEach { tw ->
                val t = ((now - tw.start).toFloat() / tw.ms).coerceIn(0f, 1f)
                tw.set(tw.curve.getInterpolation(t))
                if (t >= 1f) done += tw
            }
            tweens.removeAll(done)
            invalidate(); spot.invalidate()
            if (tweens.isNotEmpty()) frames.postDelayed(this, FRAME_MS) else ticking = false
        }
    }
    /** Its own clock for the tweens, so cancelling a scene's timetable never stops a tween half way. */
    private val frames = Handler(Looper.getMainLooper())
    private fun animate(ms: Long, curve: android.animation.TimeInterpolator, set: (Float) -> Unit) {
        if (!moving) { set(1f); invalidate(); spot.invalidate(); return }
        tweens += Tween(android.os.SystemClock.uptimeMillis(), ms, curve, set)
        if (!ticking) { ticking = true; frames.post(tick) }
    }

    /**
     * A little timetable: [then] steps run one after another, each when the one before is done, so every D-pad walk
     * starts from where the highlight really is (planned all at once, a walk counted its steps from the wrong key and
     * typed letters that weren't asked for: Emre, 7 Nov, "Zelda" came out garbled).
     */
    private inner class Script {
        private val steps = mutableListOf<Pair<Long, (() -> Unit) -> Unit>>()
        fun wait(ms: Long) = apply { steps += ms to { done -> done() } }
        fun then(ms: Long, run: () -> Unit) = apply { steps += ms to { done -> run(); done() } }
        fun now(run: () -> Unit) = then(0L, run)
        /** Walk the highlight to [label] a step at a time, then press A (held for [hold]). */
        fun type(label: String, hold: Long = PRESS_MS, on: KeyGridView = kb) = apply {
            steps += 0L to { done -> walkThen(label, on) { on.pressA(); handler.postDelayed({ on.releaseA(); done() }, hold) } }
        }
        fun typeAll(word: String, on: KeyGridView = kb) = apply { word.forEach { type(it.toString(), on = on); wait(TYPE_GAP_MS) } }
        fun run() = step(0)
        private fun step(i: Int) {
            val (ms, run) = steps.getOrNull(i) ?: return
            handler.postDelayed({ run { step(i + 1) } }, ms)
        }
    }

    // ---------------------------------------------------------------- TOUR: the three chapters
    private fun chapter(n: Int) {
        kb.guide = null; kb.resetAccents(); kb.resetShift(); kb.resetLayer()
        field.alpha = 1f
        val next = { fadeThrough { chapter((n + 1) % 3) } }
        when (n) {
            0 -> {   // Move and type: a capital Z with RT, then the rest
                field.tag = "Move and type"
                before = ""; after = ""; field.ghost = "Zelda"; showText()
                kb.focusKey(KeyType.SHIFT)
                kb.guide = KeyGridView.Guide(ControllerButton.R2) { it.type == KeyType.SHIFT }
                if (!moving) { kb.setHeldShift(true); return }
                Script().wait(1100).now { kb.setHeldShift(true) }
                    .then(500) { kb.guide = KeyGridView.Guide(ControllerButton.A) { it.type == KeyType.CHAR && it.label.equals("z", true) } }
                    .type("z").then(200) { kb.setHeldShift(false); kb.guide = null }
                    .typeAll("elda").then(1600) { next() }.run()
            }
            1 -> {   // Typing faster: é for Pokémon, held A and the accent tray
                field.tag = "Typing faster"
                before = "Pok"; after = ""; field.ghost = "Pokémon"; showText()
                kb.focusKey(KeyType.SPACE)
                if (!moving) return
                Script().wait(700).now { kb.guide = KeyGridView.Guide(ControllerButton.A) { it.type == KeyType.CHAR && it.label.equals("e", true) } }
                    .type("e", hold = ACCENT_HOLD_MS).then(150) { kb.guide = null }
                    .typeAll("mon").then(1600) { next() }.run()
            }
            else -> {   // Fixing and editing: LB moves the cursor back one, A types the missing i
                field.tag = "Fixing and editing"
                before = "Sonc"; after = ""; field.ghost = null; showText()
                kb.focusKey(KeyType.SPACE)
                if (!moving) { before = "Son"; after = "c"; showText(); return }
                Script().wait(900).now { picture.flash("LB"); before = "Son"; after = "c"; showText() }
                    .then(500) { kb.guide = KeyGridView.Guide(ControllerButton.A) { it.type == KeyType.CHAR && it.label.equals("i", true) } }
                    .type("i").then(150) { kb.guide = null }.then(1800) { next() }.run()
            }
        }
    }

    /** Between moments: a short fade through, never a jump. */
    private fun fadeThrough(again: () -> Unit) {
        animate(Design.MOTION_SHOWCASE_OUT, Design.EASE_IN) { window.alpha = 1f - it }
        handler.postDelayed({
            again()
            animate(Design.MOTION_SHOWCASE_IN, Design.EASE_OUT) { window.alpha = it }
        }, Design.MOTION_SHOWCASE_OUT + 20L)
    }

    // ---------------------------------------------------------------- SYMBOLS: a spotlight on the money sign
    private var zoom = 1f
    private var zoomAt: RectF? = null

    private fun symbols() {
        kb.guide = null; kb.resetAccents(); kb.resetShift(); kb.resetLayer()
        field.tag = null; field.ghost = null
        before = "Ticket: 20"; after = ""; showText()
        kb.focusKey(KeyType.SPACE)
        val sign = base.language.currency
        if (!moving) { kb.quickLayerStart(); repeat(12) { kb.stepTowards(sign) }; return }
        Script().wait(700).now { kb.quickLayerStart(); picture.held = picture.held + "LT"; picture.holding = picture.holding + "LT" }
            .then(300) {}
            .now { walkThen(sign) {
                // A held: its relatives rise; then the camera moves in on the key and its row of relatives, the rest dims
                val key = kb.focusTargetForCheck()          // the money sign (once the tray opens, the highlight moves into it)
                kb.pressA()
                handler.postDelayed({
                    val cells = kb.accentCellsForCheck()
                    val focus = RectF(key).apply { cells.forEach { union(it) } }
                    focus.inset(-key.width() * 0.35f, -key.height() * 0.35f)
                    zoomTo(focus, ZOOM_IN_MS, SYMBOLS_ZOOM)
                    handler.postDelayed({ spot.area = RectF(panel); spot.areaRadius = windowRadius
                        animate(ZOOM_IN_MS, ZOOM_CURVE) { spot.dim = it; spot.holes = listOf(hostRect(focus)) } }, DIM_LAG_MS)
                }, TRAY_RISE_MS)
                handler.postDelayed({ kb.releaseA() }, TRAY_RISE_MS + ZOOM_IN_MS + TRAY_HOLD_MS)
                handler.postDelayed({
                    kb.quickLayerEnd(used = true); picture.held = picture.held - "LT"; picture.holding = picture.holding - "LT"
                    animate(ZOOM_OUT_MS - DIM_LAG_MS, Design.EASE_IN) { spot.dim = 1f - it }
                    zoomTo(null, ZOOM_OUT_MS)
                }, TRAY_RISE_MS + ZOOM_IN_MS + TRAY_HOLD_MS + 600L)
                handler.postDelayed({ fadeThrough { symbols() } }, TRAY_RISE_MS + ZOOM_IN_MS + TRAY_HOLD_MS + 600L + ZOOM_OUT_MS + 2200L)
            } }.run()
    }

    private fun walkThen(label: String, on: KeyGridView = kb, done: () -> Unit) {
        val r = on.stepTowards(label)
        if (r == true || r == null) handler.postDelayed(done, STEP_MS) else handler.postDelayed({ walkThen(label, on, done) }, STEP_MS)
    }

    /** The focus zoom: the camera moves in on [target] (keyboard coordinates) until it fills the window, never past 250 %. */
    private var zoomCap = MAX_ZOOM
    private fun zoomTo(target: RectF?, ms: Long, cap: Float = MAX_ZOOM) {
        val from = zoom
        if (target != null) zoomCap = cap
        val depth = if (target == null) 1f else {
            zoomAt = RectF(target)
            min(zoomCap, min((panel.width() - 2 * pad) / (target.width() * unitK), (panel.height() - 2 * pad) / (target.height() * unitK))).coerceAtLeast(1f)
        }
        val fieldFrom = field.alpha; val fieldTo = if (target == null) 1f else 0f
        animate(ms, ZOOM_CURVE) { zoom = from + (depth - from) * it; field.alpha = fieldFrom + (fieldTo - fieldFrom) * it; applyKbZoom() }
        if (target == null) handler.postDelayed({ zoomAt = null; applyKbZoom() }, ms + 20)
    }

    /** The keyboard's place and size in the window, zoomed in on [zoomAt] (it ends up in the window's middle). */
    private fun applyKbZoom() {
        val at = zoomAt
        val z = unitK * zoom
        kb.pivotX = 0f; kb.pivotY = 0f; kb.scaleX = z; kb.scaleY = z
        if (at == null) { kb.translationX = pad; kb.translationY = kbTop; return }
        val p = if (zoomDepthMax() <= 1f) 0f else ((zoom - 1f) / (zoomDepthMax() - 1f)).coerceIn(0f, 1f)
        val fromX = pad + at.centerX() * unitK; val fromY = kbTop + at.centerY() * unitK
        val toX = fromX + (panel.width() / 2f - fromX) * p; val toY = fromY + (panel.height() / 2f - fromY) * p
        kb.translationX = toX - at.centerX() * z
        kb.translationY = toY - at.centerY() * z
    }
    private fun zoomDepthMax(): Float { val t = zoomAt ?: return 1f
        return min(zoomCap, min((panel.width() - 2 * pad) / (t.width() * unitK), (panel.height() - 2 * pad) / (t.height() * unitK))).coerceAtLeast(1f) }

    /** A rectangle in the keyboard's own coordinates, where it is on this view now. */
    private fun hostRect(r: RectF): RectF {
        val z = kb.scaleX
        val ox = window.left + kb.translationX; val oy = window.top + kb.translationY
        return RectF(ox + r.left * z, oy + r.top * z, ox + r.right * z, oy + r.bottom * z)
    }

    // ---------------------------------------------------------------- BUTTONS: the setup, played
    /**
     * The real setup screen's card plays itself (Emre, 8 Nov, direction A): the four buttons are pressed in turn and
     * get their tick, the bottom one is chosen to select and the right one to go back, and every button shows its job.
     * Then the ticks become a controller's marks, which change from one controller style to the next while the jobs
     * stay where they are: Inlay learns places, not letters. Drawn by the setup's own code, so it is sharp at any size.
     */
    private var setup: ButtonSetupView? = null

    private fun buttons() {
        spot.clear()
        setup?.let { removeView(it) }
        val v = ButtonSetupView(context).apply {
            stageOnly = true; accent = this@WhatsNewStage.accent
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(v, indexOfChild(spot))
        setup = v
        layoutSetup()
        // the four places, as an Odin sends them: X left, Y top, B right, A bottom
        val codes = listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_A)
        if (!moving) {
            codes.forEach { v.press(it) }; v.press(codes[3]); v.press(codes[2])
            v.captionTitle = ANY_CONTROLLER; v.marks = MARKS[0]; v.marksShown = 1f; v.ticksGone = 1f
            return
        }
        v.alpha = 0f
        animate(Design.MOTION_SHOWCASE_IN, Design.EASE_OUT) { v.alpha = it }
        var at = SETUP_LEAD_MS
        codes.forEach { c -> handler.postDelayed({ v.press(c) }, at); at += SETUP_PRESS_MS }
        at += SETUP_CHOOSE_MS - SETUP_PRESS_MS
        handler.postDelayed({ v.press(codes[3]) }, at)            // the bottom one selects
        at += SETUP_CHOOSE_MS
        handler.postDelayed({ v.press(codes[2]) }, at)            // the right one goes back
        at += SETUP_JOBS_MS
        // any controller: the ticks become marks, then the marks change style; the jobs stay put
        handler.postDelayed({ v.captionTitle = ANY_CONTROLLER; v.marks = MARKS[0]
            animate(Design.MOTION_SHOWCASE_IN, Design.EASE_OUT) { v.marksShown = it; v.ticksGone = it } }, at)
        for (i in 1 until MARKS.size) {
            at += SETUP_STYLE_MS
            handler.postDelayed({
                animate(MARK_SWAP_MS, Design.EASE_IN) { v.marksShown = 1f - it }
                handler.postDelayed({ v.marks = MARKS[i]; animate(Design.MOTION_SHOWCASE_IN, Design.EASE_OUT) { v.marksShown = it } }, MARK_SWAP_MS + 20L)
            }, at)
        }
        at += SETUP_STYLE_MS + SETUP_HOLD_MS
        handler.postDelayed({ animate(Design.MOTION_SHOWCASE_OUT, Design.EASE_IN) { v.alpha = 1f - it } }, at)
        handler.postDelayed({ buttons() }, at + Design.MOTION_SHOWCASE_OUT + 120L)
    }

    /** The setup's card: the whole room (its title moves beside the buttons when the room is short), never wider than useful. */
    private fun layoutSetup() {
        val v = setup ?: return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cw = min(w, h * SETUP_MAX_RATIO)
        val l = ((w - cw) / 2f).toInt(); val r = l + cw.toInt()
        v.measure(MeasureSpec.makeMeasureSpec(r - l, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h.toInt(), MeasureSpec.EXACTLY))
        v.layout(l, 0, r, h.toInt())
    }

    /** Checks: the setup card on the buttons page. */
    internal val setupForCheck: ButtonSetupView? get() = setup

    // ---------------------------------------------------------------- SECRET: a teaser, never the answer
    /**
     * The device, big, with the reward's retro screen showing "? ? ?" on its own screen (a controller gets a little
     * screen above it, as the reward does). Up on the D-pad lights twice, then nothing: a nudge for those who know.
     */
    private fun layoutSecret(w: Float, h: Float) {
        val m = Design.SPACE_S * dp                                   // the device is the whole close-up: let it fill the room
        placePicture(m, h - m, 0f, w)
        var s = picture.screenInView()
        val framed = s == null
        if (framed) {
            // no screen of its own: the controller stands lower, a 4:3 screen with the reward's bezel above it
            val bez = TEASER_BEZEL_DP * dp
            placePicture(h * TEASER_SPLIT, h - m, 0f, w)
            val sh = max(1f, picture.top + (picture.drawnRect()?.top ?: 0f) - m - 2 * bez - m)
            val sw = min(sh * 4f / 3f, w - 2 * (m + bez)); val shh = sw * 3f / 4f
            val top = m + bez + (sh - shh) / 2f
            s = RectF(w / 2f - sw / 2f, top, w / 2f + sw / 2f, top + shh)
        } else {
            s!!.offset(picture.left.toFloat(), picture.top.toFloat()); val i = 3f * dp; s.inset(i, i)
        }
        teaserRect = RectF(s)
        teaser.showTeaser(s, framed, still = !moving)
    }
    /** Checks: where the teaser's screen sits on the stage. */
    internal var teaserRect: RectF? = null
    private fun secretScene() {
        if (!moving) return
        Script().then(TEASER_FIRST_MS) { picture.flash("Dup", TEASER_FLASH_MS) }
            .then(TEASER_GAP_MS) { picture.flash("Dup", TEASER_FLASH_MS) }
            .then(TEASER_PAUSE_MS) { if (scene == WhatsNewShowcase.Scene.SECRET) secretScene() }.run()
    }

    // ---------------------------------------------------------------- SPLIT: the keyboard parts in two
    /**
     * Direction A (Emre, 8 Nov): the tour's window and text box, nothing else. A line draws down the keyboard's middle,
     * the two halves glide apart and every key moves and resizes into its place in the real split layout (keys near the
     * middle a touch later, so the parting runs outwards). Then the live split keyboard types the rest of the word,
     * the highlight hopping between the halves, and the halves come back together.
     */
    private var whole: Bitmap? = null
    private var halves: Bitmap? = null
    private var wholeHeight = 0
    private var splitHeight = 0
    private var shotWidth = 0
    private var splitPanels = emptyList<RectF>()
    private var panelColour = Color.BLACK
    private var splitCorner = 0f
    private var splitRing = Color.TRANSPARENT to 0f
    private var splitT = 0f          // 0 = whole, 1 = split
    private var seam = 0f
    /** Each split key and the whole key it comes from (unmatched ones fade in and out where they are). */
    private var pairs = emptyList<Pair<RectF?, RectF>>()
    private var lonely = emptyList<RectF>()
    /** The live split keyboard is showing (the pictures of the two layouts are not drawn meanwhile). */
    private var live = false

    /** Pictures of the whole and the split keyboard, both with the highlight on [focus], and where every key is in each. */
    private fun buildShots(focus: String) {
        // The whole keyboard is the tour's own (its width and look), so the page opens on exactly the keyboard the page
        // before showed; the split one is the real split keyboard at the width it really has
        val kw = splitWidthPx
        fun build(split: Boolean) = KeyGridView(if (split) realContext else context).apply {
            clearsBehind = false; isFocusable = false; allowSplit = split; keepProportions = true
            applySettings(demoSettings(split))
            val bw = if (split) kw else keyboardWidthPx
            measure(MeasureSpec.makeMeasureSpec(bw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            layout(0, 0, bw, measuredHeight)
            focusLabelNow(focus)
        }
        fun shot(v: KeyGridView) = Bitmap.createBitmap(max(1, v.width), max(1, v.height), Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        val a = build(false); val b = build(true)
        whole?.recycle(); halves?.recycle()
        whole = shot(a); halves = shot(b)
        wholeHeight = a.height; splitHeight = b.height; shotWidth = kw
        val wholeBoxes = a.keyBoxesForCheck(); val splitBoxes = b.keyBoxesForCheck()
        splitPanels = b.splitPanelsForCheck()
        splitCorner = b.splitPanelCornerForDemo(); splitRing = b.panelRingForDemo()
        panelColour = whole!!.getPixel(max(0, (2 * dp).toInt()), wholeHeight / 2)
        // Match each split key to the whole key it was: same row, same key; a key the split doubles (the space bar)
        // comes from the one whole key
        val used = HashSet<Int>()
        val p = mutableListOf<Pair<RectF?, RectF>>()
        splitBoxes.forEach { (r, key, box) ->
            val same = wholeBoxes.withIndex().filter { (_, w) -> w.first == r && w.second.type == key.type && w.second.label == key.label && w.second.tool == key.tool }
            val pick = same.firstOrNull { it.index !in used } ?: same.lastOrNull()
            if (pick != null) used += pick.index
            p += pick?.value?.third to box
        }
        pairs = p
        lonely = wholeBoxes.withIndex().filter { it.index !in used }.map { it.value.third }
    }

    /** Checks: the gap between the split halves as a share of the keyboard's width (null before the pictures exist). */
    internal fun splitGapShareForCheck(): Float? = splitPanels.takeIf { it.size == 2 }?.let { (l, r) -> (r.left - l.right) / shotWidth }

    private fun showLive(on: Boolean) {
        live = on
        kbSplit.visibility = if (on) VISIBLE else GONE
        invalidate()
    }

    private fun splitScene() {
        showLive(false)
        splitT = 0f; seam = 0f
        field.tag = null; field.ghost = SPLIT_WORD
        before = SPLIT_WORD.dropLast(SPLIT_TYPED.length); after = ""; showText()
        if (whole != null && shotWidth == splitWidthPx) buildShots(SPLIT_FROM)
        if (!moving) { splitT = 1f; invalidate(); return }
        Script().wait(SPLIT_LEAD_MS)
            .now { animate(SEAM_MS, Design.EASE_OUT) { seam = it } }                  // the line arrives: eased out
            .then(SEAM_MS + SEAM_REST_MS) { animate(SPLIT_MS, SPLIT_CURVE) { splitT = it } }   // the halves move: in and out
            .then(SPLIT_MS + 40L) { kbSplit.resetShift(); kbSplit.focusLabelNow(SPLIT_FROM); showLive(true) }
            .wait(SPLIT_TYPE_LEAD_MS)
            .typeAll(SPLIT_TYPED, on = kbSplit)
            .then(SPLIT_READ_MS) { buildShots(SPLIT_TYPED.last().toString()); showLive(false); seam = 0f
                animate(JOIN_MS, SPLIT_CURVE) { splitT = 1f - it } }
            .then(JOIN_MS + SPLIT_REST_MS) { fadeThrough { splitScene() } }
            .run()
    }

    private fun lerp(a: RectF, b: RectF, t: Float) = RectF(a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t, a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t)

    private fun drawSplit(c: Canvas) {
        if (live) return
        val w = whole ?: return; val h = halves ?: return
        val k = splitK
        val fade = window.alpha
        val left = panel.left + splitLeft; val floor = panel.bottom - pad
        // the whole keyboard where the tour's stands (its scale), the split one at its own
        val wk = unitK; val wl = panel.left + pad
        fun mapWhole(r: RectF) = RectF(wl + r.left * wk, floor - wholeHeight * wk + r.top * wk, wl + r.right * wk, floor - wholeHeight * wk + r.bottom * wk)
        fun mapSplit(r: RectF) = RectF(left + r.left * k, floor - splitHeight * k + r.top * k, left + r.right * k, floor - splitHeight * k + r.bottom * k)
        val wholeRect = RectF(wl, floor - wholeHeight * wk, wl + w.width * wk, floor)
        val mid = wholeRect.centerX()
        // The keyboard's panel parts in two: each half becomes a split panel
        val (pl, pr) = if (splitPanels.size == 2) mapSplit(splitPanels[0]) to mapSplit(splitPanels[1]) else wholeRect to wholeRect
        val leftHalf = RectF(wholeRect.left, wholeRect.top, mid, wholeRect.bottom)
        val rightHalf = RectF(mid, wholeRect.top, wholeRect.right, wholeRect.bottom)
        // the halves' corners grow from the window's own (parallel to it) into the real split panels' corners, and their
        // hairline comes in as they part: at the end they are exactly what the live keyboard draws
        val radius = (windowRadius - pad) + (splitCorner * k - (windowRadius - pad)) * splitT
        fill.color = Contrast.withAlpha(panelColour, ((panelColour ushr 24) * fade).toInt())
        val a = lerp(leftHalf, pl, splitT); val b = lerp(rightHalf, pr, splitT)
        c.drawRoundRect(a, radius, radius, fill)
        c.drawRoundRect(b, radius, radius, fill)
        if (splitT < 0.02f) c.drawRect(RectF(mid - radius, wholeRect.top, mid + radius, wholeRect.bottom), fill)   // one panel until it parts
        else {
            val (ink, width) = splitRing
            line.color = Contrast.withAlpha(ink, ((ink ushr 24) * splitT * fade).toInt()); line.strokeWidth = width * k
            val hi = line.strokeWidth / 2f
            listOf(a, b).forEach { r -> c.drawRoundRect(RectF(r).apply { inset(hi, hi) }, radius - hi, radius - hi, line) }
        }
        // Every key glides and resizes into its place, its look turning from the whole keyboard's into the split one's
        val crop = Rect(); val bleed = (3 * dp).toInt()
        pairs.forEach { (from, to) ->
            val dest = mapSplit(to)
            val src = from?.let { mapWhole(it) } ?: dest
            val lag = 1f - (abs(src.centerX() - mid) / (wholeRect.width() / 2f)).coerceIn(0f, 1f)
            val q = ((splitT - lag * KEY_STAGGER) / (1f - KEY_STAGGER)).coerceIn(0f, 1f)
            val r = lerp(src, dest, q)
            if (from != null && q < 1f) {
                bmp.alpha = (255 * (1f - q) * fade).toInt()
                crop.set((from.left - bleed).toInt(), (from.top - bleed).toInt(), (from.right + bleed).toInt(), (from.bottom + bleed).toInt())
                c.drawBitmap(w, crop, outset(r, bleed * k), bmp)
            }
            if (q > 0f || from == null) {
                bmp.alpha = (255 * (if (from == null) splitT else q) * fade).toInt()
                crop.set((to.left - bleed).toInt(), (to.top - bleed).toInt(), (to.right + bleed).toInt(), (to.bottom + bleed).toInt())
                c.drawBitmap(h, crop, outset(r, bleed * k), bmp)
            }
        }
        lonely.forEach { r -> bmp.alpha = (255 * (1f - splitT) * fade).toInt(); val m = mapWhole(r)
            crop.set((r.left - bleed).toInt(), (r.top - bleed).toInt(), (r.right + bleed).toInt(), (r.bottom + bleed).toInt()); c.drawBitmap(w, crop, outset(m, bleed * k), bmp) }
        bmp.alpha = 255
        // the line drawn down the middle, top to bottom, before it parts; it fades as the halves leave it
        if (seam > 0f && splitT < SEAM_FADE) {
            val a = (1f - splitT / SEAM_FADE) * fade
            val half = SEAM_DP * dp / 2f
            val top = wholeRect.top + Design.SPACE_S * dp * k
            val bottom = top + (wholeRect.height() - 2 * Design.SPACE_S * dp * k) * seam
            fill.color = Contrast.withAlpha(accent, (Design.ALPHA_TINT_TAG * a).toInt())          // a soft glow round it
            c.drawRoundRect(RectF(mid - half * 3f, top, mid + half * 3f, bottom), half * 3f, half * 3f, fill)
            fill.color = Contrast.withAlpha(accent, (255 * a).toInt())
            c.drawRoundRect(RectF(mid - half, top, mid + half, bottom), half, half, fill)
        }
    }
    private fun outset(r: RectF, by: Float) = RectF(r.left - by, r.top - by, r.right + by, r.bottom + by)

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); handler.removeCallbacksAndMessages(null); tweens.clear(); crossShot?.recycle(); crossShot = null }

    // ================================================================ the spotlight: dim, holes and callouts
    private class Callout(val text: String, val at: android.graphics.PointF, val dir: Int, var appear: Float, val clearance: Float = 0f)

    private inner class Spot : View(context) {
        var dim = 0f
        var holes = emptyList<RectF>()
        var circles = emptyList<Pair<android.graphics.PointF, Float>>()
        var callouts = emptyList<Callout>()
        /** Where the dim goes (round corners); null dims the whole stage. Quick symbols dims the keyboard's window only. */
        var area: RectF? = null
        var areaRadius = 0f
        private val veil = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        private val chip = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }      // the soft edge (a blur mask) is drawn in software
        fun clear() { dim = 0f; area = null; holes = emptyList(); circles = emptyList(); callouts = emptyList(); invalidate() }

        override fun onDraw(c: Canvas) {
            if (dim > 0f) {
                val save = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                veil.color = Contrast.withAlpha(Color.BLACK, (Design.SPOTLIGHT_DIM * dim).toInt())
                area?.let { c.drawRoundRect(it, areaRadius, areaRadius, veil) } ?: c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), veil)
                clear.maskFilter = BlurMaskFilter(SPOT_EDGE_DP * dp, BlurMaskFilter.Blur.NORMAL)
                holes.forEach { r -> c.drawRoundRect(outset(r, SPOT_EDGE_DP * dp * 0.5f), 14 * dp, 14 * dp, clear) }
                circles.forEach { (p, rad) -> c.drawCircle(p.x, p.y, rad, clear) }
                c.restoreToCount(save)
            }
            callouts.forEach { draw(c, it) }
        }

        /** A job beside its button: Inlay's chip (dark plate, hairline edge), arriving from the button outwards. */
        private fun draw(c: Canvas, k: Callout) {
            if (k.appear <= 0f) return
            words.typeface = SettingsStyle.medium; words.textSize = CALLOUT_SP * resources.displayMetrics.scaledDensity
            words.color = Contrast.withAlpha(Color.WHITE, (255 * k.appear).toInt())
            val tw = words.measureText(k.text); val ph = words.textSize * 2.1f; val pw = tw + ph
            val gap = k.clearance + CALLOUT_GAP_DP * dp - (1f - k.appear) * 6 * dp
            val (dx, dy) = when (k.dir) { 0 -> 0f to -1f; 1 -> 1f to 0f; 2 -> 0f to 1f; else -> -1f to 0f }
            var cx = k.at.x + dx * (gap + pw / 2f); var cy = k.at.y + dy * (gap + ph / 2f)
            cx = cx.coerceIn(pw / 2f + 4 * dp, width - pw / 2f - 4 * dp); cy = cy.coerceIn(ph / 2f + 4 * dp, height - ph / 2f - 4 * dp)
            val r = RectF(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)
            chip.color = Contrast.withAlpha(Design.BADGE_PLATE, (255 * k.appear).toInt()); c.drawRoundRect(r, ph / 2f, ph / 2f, chip)
            edge.color = Contrast.withAlpha(Color.WHITE, (Design.ALPHA_FAINT_STRONG * 2 * k.appear).toInt()); edge.strokeWidth = max(1f, Design.STROKE_HAIRLINE * dp)
            c.drawRoundRect(r, ph / 2f, ph / 2f, edge)
            c.drawText(k.text, cx, cy - (words.descent() + words.ascent()) / 2f, words)
        }
    }

    private companion object {
        val TILES = setOf(WhatsNewShowcase.Scene.MORE, WhatsNewShowcase.Scene.MORE_REMOTE)
        // the teaser: up, up, then a pause (the code's first two presses, and nothing more)
        const val TEASER_FIRST_MS = 1000L
        const val TEASER_GAP_MS = 380L
        const val TEASER_FLASH_MS = 200L
        const val TEASER_PAUSE_MS = 3200L
        const val TEASER_BEZEL_DP = 10f      // the reward's own bezel around a screen of its own
        const val TEASER_SPLIT = 0.5f        // a controller stands in the lower half
        const val KEYBOARD_DP = 620f          // the widest the scene's keyboard is laid out (Split fits every language at it)
        const val FIELD_DP = 44f
        const val PANEL_PAD_DP = 8f
        const val PANEL_RADIUS_DP = 18f
        const val ROOM_FOR_DEVICE_DP = 300f   // under this the device steps out of the tour and symbols pages
        const val WINDOW_SHARE = 0.62f        // the keyboard's window takes up to this share of the height; the device the rest
        const val STEP_MS = 170L              // a D-pad step, as the tour walks
        const val PRESS_MS = 110L
        const val TYPE_GAP_MS = 120L
        const val ACCENT_HOLD_MS = 1300L      // A held on e: the accent tray rises and é is kept on letting go
        const val TRAY_HOLD_MS = 1500L
        const val TRAY_RISE_MS = 450L         // A held: the relatives are up before the camera moves in
        const val SYMBOLS_ZOOM = 1.7f
        const val SETUP_LEAD_MS = 900L          // the card settles before the first press
        const val SETUP_PRESS_MS = 850L         // one button learnt every this long (its tick draws in, the next one breathes)
        const val SETUP_CHOOSE_MS = 1300L       // "Which one selects?" stays up this long before the choice
        const val SETUP_JOBS_MS = 1700L         // every button with its job, before the controller styles
        const val SETUP_STYLE_MS = 1800L        // each controller style shows this long
        const val SETUP_HOLD_MS = 600L
        const val MARK_SWAP_MS = 160L           // marks out, then the next style's in
        const val SETUP_MAX_RATIO = 2.1f        // the card is at most this much wider than tall
        const val ANY_CONTROLLER = ButtonSetupView.ANY
        /** Each style's marks by place (left, top, right, bottom): Xbox, PlayStation, Nintendo. */
        val MARKS = listOf(listOf("X", "Y", "B", "A"), listOf("square", "triangle", "circle", "cross"), listOf("Y", "X", "A", "B"))
        const val SEAM_MS = 420L
        const val SEAM_REST_MS = 120L         // the line holds a moment before the halves move
        const val SEAM_DP = 3f
        const val SEAM_FADE = 0.3f            // the line is gone by this share of the parting
        const val SPLIT_MS = 1100L
        const val JOIN_MS = 900L
        const val SPLIT_LEAD_MS = 1100L
        const val SPLIT_TYPE_LEAD_MS = 450L
        const val SPLIT_READ_MS = 1500L
        const val SPLIT_REST_MS = 1300L
        const val SPLIT_WORD = "Super Metroid"
        const val SPLIT_TYPED = "roid"        // r and d on the left half, o and i on the right: the highlight hops across
        const val SPLIT_FROM = "g"
        const val KEY_STAGGER = 0.18f         // keys nearer the middle start this share of the move later
        const val MAX_ZOOM = 2.5f
        const val ZOOM_IN_MS = 500L
        const val ZOOM_OUT_MS = 650L
        const val DIM_LAG_MS = 100L
        const val SPOT_EDGE_DP = 14f          // the spotlight's soft edge (28 px at 2x)
        const val CALLOUT_SP = 13f
        const val CALLOUT_GAP_DP = 8f
        const val FRAME_MS = 16L
        const val CROSS_MS = 240L             // page to page, the close-up crossfades
        const val COVER_STRIP_ALPHA = 0x8C    // 55 % black behind a cover's title
        val ZOOM_CURVE = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        val SPLIT_CURVE = PathInterpolator(0.65f, 0f, 0.35f, 1f)     // a move on screen: ease in-out
        /** The demo covers' flat art (VISUAL_SYSTEM 6): base and shape tones. */
        val COVER_TONES = listOf(Design.COVER_BLUE to Design.COVER_BLUE_SHAPE, Design.COVER_TERRACOTTA to Design.COVER_TERRACOTTA_SHAPE,
            Design.COVER_GREEN to Design.COVER_GREEN_SHAPE, Design.COVER_PURPLE to Design.COVER_PURPLE_SHAPE)
        val COVER_TITLES = listOf("SNES" to "Super Metroid", "GBA" to "Metroid Fusion", "GAMECUBE" to "Metroid Prime", "SWITCH" to "Metroid Dread")
    }
}
