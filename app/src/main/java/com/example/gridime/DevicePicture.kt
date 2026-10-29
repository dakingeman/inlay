package com.example.gridime

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.InputDevice
import android.view.View
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Which device picture to show. Automatic works it out from the controller and the screen. */
enum class PictureChoice(val label: String) {
    AUTO("Automatic"),
    CONTROLLER("Controller"),
    HANDHELD("Handheld"),
    VERTICAL("Vertical"),
    REMOTE("Remote")
}

/**
 * What you can press, from the device picked in the Button guide (or found automatically).
 * One place decides it; Settings, the keyboard, the Button guide, the tour and What's new all ask.
 *  - A TV remote has a ring, OK and Back: no controller buttons at all.
 *  - A vertical handheld usually has no sticks (some do: "My handheld has sticks" turns them on).
 *  - Everything else has the lot.
 */
data class Controls(val kind: DeviceKind, val sticks: Boolean) {
    val remote get() = kind == DeviceKind.REMOTE

    companion object {
        fun of(context: Context, settings: KeyboardSettings): Controls {
            val kind = DevicePicture.resolve(context, settings.picture)
            return Controls(kind, when (kind) {
                DeviceKind.REMOTE -> false
                // On Automatic, a recognised upright handheld says whether it has sticks; otherwise the setting does
                DeviceKind.VERTICAL -> (if (settings.picture == PictureChoice.AUTO) DevicePicture.knownHandheld(context)?.second else null)
                    ?: settings.verticalSticks
                else -> true
            })
        }
    }
}

/** The settings as the keyboard uses them with [c]: no controller badges with a remote, no stick hints without sticks. */
fun KeyboardSettings.forControls(c: Controls) = copy(
    showHints = showHints && !c.remote,
    comboHints = comboHints && !c.remote,
    rightStick = rightStick && c.sticks,
    hasSticks = c.sticks)

/**
 * Controller buttons a screen doesn't use. Android re-sends some of these as a "confirm" press
 * when no one takes them (its generic key map: a stick click becomes OK), which clicked rows in
 * Settings and turned tour pages. Screens take these and do nothing with them.
 */
val UNUSED_PAD_KEYS = setOf(
    android.view.KeyEvent.KEYCODE_BUTTON_THUMBL, android.view.KeyEvent.KEYCODE_BUTTON_THUMBR,
    android.view.KeyEvent.KEYCODE_BUTTON_X, android.view.KeyEvent.KEYCODE_BUTTON_Y,
    android.view.KeyEvent.KEYCODE_BUTTON_START, android.view.KeyEvent.KEYCODE_BUTTON_SELECT,
    android.view.KeyEvent.KEYCODE_BUTTON_L1, android.view.KeyEvent.KEYCODE_BUTTON_R1,
    android.view.KeyEvent.KEYCODE_BUTTON_L2, android.view.KeyEvent.KEYCODE_BUTTON_R2,
    android.view.KeyEvent.KEYCODE_BUTTON_C, android.view.KeyEvent.KEYCODE_BUTTON_Z,
    android.view.KeyEvent.KEYCODE_BUTTON_MODE)

/** The three kinds of drawing in the device kit. Each comes in Xbox, PlayStation and Switch style. */
enum class DeviceKind(val file: String) {
    CONTROLLER("controller"),
    WIDE("wide"),
    VERTICAL("vertical"),
    REMOTE("remote")
}

/**
 * The device pictures from the brand kit (tools/video/brand-kit), drawn in the app.
 *
 * Each picture is one SVG in assets/devices, drawn with nothing pressed. Next to it, a small JSON
 * says, for every button, which parts change when it's pressed (as CSS). So a press is drawn by the
 * kit's own rules: accent fill, dark label, the shape-fitting ring. "ACC" in the CSS is the
 * highlight colour, filled in when drawing. Made from the brand kit by tools/video/brand-kit/
 * kitgen.js, kit2android.py and geo.js (see the brand kit's README).
 */
object DevicePicture {

    /** What you're holding: built-in controls mean a handheld (vertical if the screen is nearly square). */
    fun resolve(context: Context, choice: PictureChoice): DeviceKind = when (choice) {
        PictureChoice.CONTROLLER -> DeviceKind.CONTROLLER
        PictureChoice.HANDHELD -> DeviceKind.WIDE
        PictureChoice.VERTICAL -> DeviceKind.VERTICAL
        PictureChoice.REMOTE -> DeviceKind.REMOTE
        PictureChoice.AUTO -> detect(context).first
    }

    /** The device's name in Settings. */
    fun label(kind: DeviceKind) = when (kind) {
        DeviceKind.CONTROLLER -> "Controller"
        DeviceKind.WIDE -> "Handheld"
        DeviceKind.VERTICAL -> "Vertical"
        DeviceKind.REMOTE -> "Remote"
    }

    /** One line for the Device tab: what Automatic chose and why, or that you chose. */
    fun why(context: Context, choice: PictureChoice): String =
        if (choice != PictureChoice.AUTO) "You picked ${choice.label}. Automatic goes by your controller and screen"
        else detect(context).let { (kind, reason) -> "Automatic picked ${label(kind)}: $reason" }

    /**
     * Gaming handhelds Inlay knows by name. Many of them report their own built-in controls as an
     * "external" controller (the AYN Odin 2 Portal does), so the controller alone can't tell a
     * handheld from a phone with a pad plugged in: the maker's name can. Matched as whole words in
     * what the device says about itself and its controllers, so a word inside another never counts.
     * To add a handheld: its maker's word here, and a [VERTICALS] entry if it's held upright.
     */
    private val HANDHELD_MAKERS = setOf("ayn", "odin", "odin2", "odin3", "retroid", "anbernic", "ayaneo", "gpd",
        "abxylute", "mangmi", "powkiddy")
    /** Phrases that only appear on these devices. */
    private val HANDHELD_PHRASES = listOf("g cloud", "razer edge")

    /** Upright handhelds by model, with whether they have sticks. Others fall back on the screen's shape. */
    private val VERTICALS = listOf(
        listOf("pocket", "classic") to false,                // Retroid Pocket Classic: D-pad and buttons only
        listOf("rg405v") to true, listOf("rg406v") to true   // Anbernic: two sticks below the buttons
    )

    /** What this device and its controllers call themselves: the text, and its lower-case words. */
    private fun identity(): Pair<String, Set<String>> {
        val parts = mutableListOf<String?>(android.os.Build.MANUFACTURER, android.os.Build.BRAND, android.os.Build.MODEL,
            android.os.Build.DEVICE, android.os.Build.PRODUCT)
        for (id in InputDevice.getDeviceIds()) InputDevice.getDevice(id)?.let { d ->
            if ((d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) parts += d.name
        }
        val text = parts.filterNotNull().joinToString(" ").lowercase()
        // "RG 405V", "rg-405v" and "RG405V" are the same model: join a model code split by a space or dash
        val joined = text.replace(Regex("\\b(rg)[\\s_-]+(\\d{3}\\w*)"), "$1$2")
        return joined to joined.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
    }

    /** A known handheld: its kind, its sticks (if known) and the name to show. Null for anything else. */
    internal fun knownHandheld(context: Context): Triple<DeviceKind, Boolean?, String>? {
        val (text, words) = identity()
        if (words.none { it in HANDHELD_MAKERS } && HANDHELD_PHRASES.none { it in text }) return null
        val name = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "your handheld"
        VERTICALS.firstOrNull { (need, _) -> need.all { it in words } }?.let { (_, sticks) ->
            return Triple(DeviceKind.VERTICAL, sticks, name)
        }
        return Triple(if (squarish(context)) DeviceKind.VERTICAL else DeviceKind.WIDE, null, name)
    }

    private fun squarish(context: Context): Boolean {
        val m = context.resources.displayMetrics
        val ratio = max(m.widthPixels, m.heightPixels).toFloat() / min(m.widthPixels, m.heightPixels)
        return ratio < VERTICAL_RATIO                       // Retroid Pocket Classic style (about 1.15)
    }

    /** What Automatic finds: the device, and in plain words why. */
    private fun detect(context: Context): Pair<DeviceKind, String> {
        knownHandheld(context)?.let { (kind, _, name) -> return kind to "recognised $name" }
        run {
            val pads = ArrayList<InputDevice>()
            for (id in InputDevice.getDeviceIds()) {
                val device: InputDevice = InputDevice.getDevice(id) ?: continue
                if ((device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) pads += device
            }
            val builtIn = pads.any { !it.isExternal }
            val tv = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            return if (tv && pads.isEmpty()) DeviceKind.REMOTE to "Android TV, and no controller connected"
            else if (!builtIn && pads.isNotEmpty()) DeviceKind.CONTROLLER to "a controller is connected"
            else if (!builtIn) DeviceKind.CONTROLLER to "no built-in controls found"   // a phone: show a controller
            else {
                // 4:3 (1.33) and 16:9 are wide
                if (squarish(context)) DeviceKind.VERTICAL to "built-in controls and a nearly square screen"
                else DeviceKind.WIDE to "built-in controls and a wide screen"
            }
        }
    }

    /** A button's name on the device pictures, from what Android reports (null: not one we draw). */
    fun keyFor(keyCode: Int): String? = when (keyCode) {
        android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_DPAD_CENTER -> "A"
        android.view.KeyEvent.KEYCODE_BUTTON_B, android.view.KeyEvent.KEYCODE_BACK -> "B"
        android.view.KeyEvent.KEYCODE_BUTTON_X -> "X"
        android.view.KeyEvent.KEYCODE_BUTTON_Y -> "Y"
        android.view.KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
        android.view.KeyEvent.KEYCODE_BUTTON_R1 -> "RB"
        android.view.KeyEvent.KEYCODE_BUTTON_L2 -> "LT"
        android.view.KeyEvent.KEYCODE_BUTTON_R2 -> "RT"
        android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> "LS"
        android.view.KeyEvent.KEYCODE_BUTTON_THUMBR -> "RS"
        android.view.KeyEvent.KEYCODE_BUTTON_START -> "START"
        android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
        android.view.KeyEvent.KEYCODE_DPAD_UP -> "Dup"
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "Ddown"
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "Dleft"
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "Dright"
        else -> null
    }

    private const val VERTICAL_RATIO = 1.25f

    fun styleName(style: ButtonStyle) = when (style) {
        ButtonStyle.XBOX -> "xbox"
        ButtonStyle.PLAYSTATION -> "ps"
        ButtonStyle.SWITCH -> "switch"
    }

    /** The kit's name for each button (positional, like Inlay's: A = bottom face button). */
    fun keyFor(button: ControllerButton): String = when (button) {
        ControllerButton.A -> "A"
        ControllerButton.B -> "B"
        ControllerButton.X -> "X"
        ControllerButton.Y -> "Y"
        ControllerButton.L1 -> "LB"
        ControllerButton.R1 -> "RB"
        ControllerButton.L2 -> "LT"
        ControllerButton.R2 -> "RT"
        ControllerButton.L3 -> "LS"
        ControllerButton.R3 -> "RS"
        ControllerButton.SELECT -> "SELECT"
        ControllerButton.START -> "START"
    }

    /** A controller button on the TV remote: A is OK, B is Back, the D-pad is the ring. Others it doesn't have. */
    fun onRemote(key: String): String? = when (key) {
        "A" -> "OK"
        "B" -> "BACK"
        "Dup", "Ddown", "Dleft", "Dright" -> key
        else -> null
    }

    /** The buttons in a hint like "{R2} + {L1} {R1}". */
    fun keysIn(hint: String): Set<String> {
        val badges = HintText.parse(hint).filterIsInstance<HintPart.Badge>().map { keyFor(it.button) }.toSet()
        if (badges.isNotEmpty()) return badges
        // The TV remote's rows are words, not badges: OK, Back, the ring
        return when {
            "OK" in hint -> setOf("A")
            "Back" in hint -> setOf("B")
            "ring" in hint -> setOf("Dup", "Ddown", "Dleft", "Dright")
            else -> emptySet()
        }
    }

    /** The buttons a hint holds down: the ones in a HOLD capsule ("{R2} + {A}": RT; "hold OK": A). */
    fun holdKeysIn(hint: String): Set<String> = HintText.parse(hint).mapNotNull {
        when {
            it is HintPart.Badge && it.held -> keyFor(it.button)
            it is HintPart.HeldWord -> if (it.word.equals("OK", true)) "A" else "B"
            else -> null
        }
    }.toSet()

    /** Sticks a hint moves rather than clicks: "{R3} left or right" sways sideways, "{R3} up or down" up and down. */
    fun swayIn(hint: String): Map<String, Boolean> {
        val sideways = when {
            "left or right" in hint -> true
            "up or down" in hint -> false
            else -> return emptyMap()
        }
        return HintText.parse(hint).filterIsInstance<HintPart.Badge>().filter { it.button.isStick }
            .associate { keyFor(it.button) to sideways }
    }

    // Pictures are read once and kept: parsing is the slow part, drawing is quick
    /**
     * The least room (picture units) between the outlines of two ringed buttons that can be lit at the same time:
     * not the sticks (drawn their own way) and not two D-pad directions (a remote's ring can't press two at once,
     * and the cross's arms have no ring). Circles are measured exactly, everything else by its box.
     */
    fun tightestGap(geo: JSONObject): Float {
        data class B(val k: String, val x: Float, val y: Float, val w: Float, val h: Float, val round: Boolean)
        val dpad = setOf("Dup", "Ddown", "Dleft", "Dright")      // the arms have a ring now too
        val bs = geo.keys().asSequence().mapNotNull { k ->
            val g = geo.optJSONObject(k) ?: return@mapNotNull null
            if ((!g.has("ring") && k !in dpad) || g.has("stick")) return@mapNotNull null
            B(k, g.getDouble("x").toFloat(), g.getDouble("y").toFloat(), g.getDouble("w").toFloat(), g.getDouble("h").toFloat(), g.optString("tag") == "circle")
        }.toList()
        var least = Float.MAX_VALUE
        for (i in bs.indices) for (j in i + 1 until bs.size) {
            val a = bs[i]; val b = bs[j]
            if (a.k in dpad && b.k in dpad) continue
            val d = if (a.round && b.round) hypot((a.x + a.w / 2) - (b.x + b.w / 2), (a.y + a.h / 2) - (b.y + b.h / 2)) - a.w / 2 - b.w / 2
            else if (a.round || b.round) {
                val c = if (a.round) a else b; val r = if (a.round) b else a
                val cx = c.x + c.w / 2; val cy = c.y + c.h / 2
                hypot(cx - cx.coerceIn(r.x, r.x + r.w), cy - cy.coerceIn(r.y, r.y + r.h)) - c.w / 2
            } else max(max(a.x - (b.x + b.w), b.x - (a.x + a.w)), max(a.y - (b.y + b.h), b.y - (a.y + a.h)))
            least = min(least, d)
        }
        return least
    }

    private val pictures = HashMap<String, Pair<SVG, JSONObject>>()

    fun load(context: Context, kind: DeviceKind, style: ButtonStyle, sticks: Boolean = false): Pair<SVG, JSONObject>? {
        // The remote is the same for everyone; the others come in Xbox, PlayStation and Switch style,
        // and the vertical handheld also with sticks
        val name = when {
            kind == DeviceKind.REMOTE -> kind.file
            kind == DeviceKind.VERTICAL && sticks -> "${kind.file}-sticks-${styleName(style)}"
            else -> "${kind.file}-${styleName(style)}"
        }
        pictures[name]?.let { return it }
        return runCatching {
            val assets = context.applicationContext.assets
            val svg = assets.open("devices/$name.svg").use { SVG.getFromInputStream(it) }
            svg.setDocumentWidth("100%")
            svg.setDocumentHeight("100%")
            val css = JSONObject(assets.open("devices/$name.json").bufferedReader().use { it.readText() })
            (svg to css).also { pictures[name] = it }
        }.getOrNull()
    }
}

/** How a held button looks (picked in the design review; the tests draw all of them). */
internal object HoldStyle {
    enum class Look { FILL, SWELL, TIMER }
    @Volatile var look = Look.TIMER     // picked 28 Oct 2026: a second ring runs round, then stays
}

/**
 * Draws one device picture, with the pressed buttons lit in the highlight colour.
 * [held] stays lit until changed; [flash] lights a button for a moment (a press in a demo).
 */
class DevicePictureView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private var svg: SVG? = null
    private var css: JSONObject? = null

    var accent: Int = SettingsStyle.accent
        set(v) { field = v; invalidate() }

    /** Buttons lit right now (a Button guide row, or RT held in a demo). */
    var held: Set<String> = emptySet()
        set(v) { if (field != v) { field = v; invalidate(); notifyLit() } }

    /**
     * Buttons being held down (not just lit): a ring fills inside each one, then stays closed.
     * Only buttons that are also lit show it.
     */
    var holding: Set<String> = emptySet()
        set(v) {
            if (field == v) return
            val now = SystemClock.uptimeMillis()
            (v - field).forEach { holdSince[it] = now }
            (field - v).forEach { holdSince.remove(it) }
            field = v
            invalidate()
        }
    private val holdSince = HashMap<String, Long>()

    /** Sticks pushed right now (LS / RS), each way from -1 to 1. */
    var stickPush: Map<String, PointF> = emptyMap()
        set(v) { field = v; invalidate() }

    /** Sticks that sway on their own to show a direction: true = left and right, false = up and down. */
    var stickSway: Map<String, Boolean> = emptyMap()
        set(v) { if (field != v) { field = v; swayStart = SystemClock.uptimeMillis(); invalidate() } }
    private var swayStart = 0L

    private val flashes = HashMap<String, Long>()

    /** Tallest the picture may be; it keeps its own shape inside. */
    var maxHeightDp = 140f
        set(v) { field = v; requestLayout() }

    /** Which drawing is showing. */
    var kind: DeviceKind = DeviceKind.CONTROLLER
        private set

    fun show(kind: DeviceKind, style: ButtonStyle, sticks: Boolean = false) {
        this.kind = kind
        this.sticks = sticks
        val loaded = DevicePicture.load(context, kind, style, sticks)
        svg = loaded?.first
        css = loaded?.second
        tightest = css?.optJSONObject("_geo")?.let { DevicePicture.tightestGap(it) } ?: Float.MAX_VALUE
        leastRoom = css?.optJSONObject("_geo")?.let { g -> g.keys().asSequence().mapNotNull { g.optJSONObject(it)?.takeIf { o -> o.has("room") }?.getDouble("room")?.toFloat() }.minOrNull() } ?: Float.MAX_VALUE
        requestLayout()
        invalidate()
    }

    /** Whether the picture shown is the one with sticks (the vertical handheld has both). */
    var sticks = false
        private set

    /**
     * Changes to a picture that's nearly the same (the vertical handheld with and without sticks)
     * in place: the body, screen and shoulder keys stay still, the D-pad, face buttons and pills glide
     * to where they sit on the new one, and what only one of them has (the sticks) fades.
     */
    fun morphTo(kind: DeviceKind, style: ButtonStyle, sticks: Boolean, ms: Long) {
        val oldGeo = css?.optJSONObject("_geo")
        val oldBox = svg?.documentViewBox
        if (width <= 0 || height <= 0 || !animationsOn() || oldGeo == null || oldBox == null) { show(kind, style, sticks); return }
        val before = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        drawPicture(Canvas(before))
        morph?.before?.recycle()
        morph = Morph(before, oldGeo, mapping(oldBox), SystemClock.uptimeMillis(), ms)
        show(kind, style, sticks)
    }

    /** Picture units to view pixels: scale, then the offset that centres the picture. */
    private fun mapping(box: RectF): FloatArray {
        val scale = min(width / box.width(), height / box.height())
        return floatArrayOf(scale, (width - box.width() * scale) / 2f - box.left * scale, (height - box.height() * scale) / 2f - box.top * scale)
    }

    private class Morph(val before: android.graphics.Bitmap, val geo: org.json.JSONObject, val map: FloatArray, val start: Long, val ms: Long) {
        var after: android.graphics.Bitmap? = null
    }
    private var morph: Morph? = null
    private var cache: android.graphics.Bitmap? = null      // the picture as last drawn (see drawPicture)
    private var cacheKey = ""

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cache?.recycle(); cache = null; cacheKey = ""
        morph?.let { it.before.recycle(); it.after?.recycle() }; morph = null
    }
    private val morphPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** The part that moves: everything the controls cover on either picture, a little air round it. */
    private fun controlsArea(geo: org.json.JSONObject, map: FloatArray, into: RectF) {
        for (k in MORPH_KEYS) {
            val g = geo.optJSONObject(k) ?: continue
            val x = map[1] + g.getDouble("x").toFloat() * map[0]; val y = map[2] + g.getDouble("y").toFloat() * map[0]
            into.union(x, y, x + g.getDouble("w").toFloat() * map[0], y + g.getDouble("h").toFloat() * map[0])
        }
    }

    private fun drawMorph(canvas: Canvas, m: Morph, now: Long): Boolean {
        val geo = css?.optJSONObject("_geo") ?: return false
        val box = svg?.documentViewBox ?: return false
        val t = ((now - m.start).toFloat() / m.ms).coerceIn(0f, 1f)
        if (t >= 1f) return false
        val e = Design.EASE_STANDARD.getInterpolation(t)
        val map = mapping(box)
        // How far the controls travel: from the A button's place on the old picture to the new
        val a0 = m.geo.optJSONObject("A"); val a1 = geo.optJSONObject("A")
        if (a0 == null || a1 == null) return false
        val shift = (m.map[2] + a0.getDouble("y").toFloat() * m.map[0]) - (map[2] + a1.getDouble("y").toFloat() * map[0])
        val area = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        controlsArea(m.geo, m.map, area); controlsArea(geo, map, area)
        area.inset(-MORPH_AIR * map[0], -MORPH_AIR * map[0])
        val now1 = m.after?.takeIf { it.width == width && it.height == height }
            ?: android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { m.after?.recycle(); m.after = it }
        now1.eraseColor(0)
        drawPicture(Canvas(now1))                                   // the new picture as it is right now (lit keys and all)
        // Everything that stays: the new picture, outside the moving part
        canvas.save(); canvas.clipOutRect(area); canvas.drawBitmap(now1, 0f, 0f, null); canvas.restore()
        // The moving part: the body's own colour, then the old controls going and the new ones arriving, in step
        val src = Rect(area.left.toInt(), area.top.toInt(), area.right.toInt(), area.bottom.toInt())
        canvas.save(); canvas.clipRect(area)
        morphPaint.alpha = 255
        morphPaint.color = now1.getPixel(src.left.coerceIn(0, width - 1), src.top.coerceIn(0, height - 1))
        canvas.drawRect(area, morphPaint)
        morphPaint.alpha = ((1f - e) * 255).toInt()
        canvas.drawBitmap(m.before, src, RectF(src).apply { offset(0f, -shift * e) }, morphPaint)
        morphPaint.alpha = (e * 255).toInt()
        canvas.drawBitmap(now1, src, RectF(src).apply { offset(0f, shift * (1f - e)) }, morphPaint)
        canvas.restore()
        return true
    }

    /** Lights [key] for a moment, like a quick press. */
    fun flash(key: String, ms: Long = FLASH_MS) {
        flashes[key] = SystemClock.uptimeMillis() + ms
        invalidate()
        notifyLit()
        postDelayed({ invalidate(); notifyLit() }, ms + 16)
    }

    /** What's lit right now (in controller names: A, RT, Dup…). */
    fun litNow(): Set<String> {
        val now = SystemClock.uptimeMillis()
        return held + flashes.filterValues { it >= now }.keys
    }

    /** The picture's button measurements, and its picture-units-to-pixels scale (for the layout audit). */
    internal fun geoForTest(): JSONObject? = css?.optJSONObject("_geo")
    internal fun pictureScaleForTest(): Float { val b = svg?.documentViewBox ?: return 1f; return min(width / b.width(), height / b.height()) }

    /** Told whenever what's lit changes. The consistency checks listen here. */
    internal var onLit: ((Set<String>) -> Unit)? = null
    private fun notifyLit() { onLit?.invoke(litNow()) }

    private fun aspect(): Float {
        val box = svg?.documentViewBox ?: return 1.6f
        return box.width() / box.height()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // A tall picture (the vertical handheld) may be taller, so it's never drawn narrower
        // than the kit allows; the parent's limit still applies
        val limit = maxHeightDp * dp * (if (tallBoost && aspect() < 0.8f) TALL_BOOST else 1f)
        var h = min(w / aspect(), limit)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) h = min(h, MeasureSpec.getSize(heightMeasureSpec).toFloat() - roomBelowPx)
        setMeasuredDimension(w, h.coerceAtLeast(0f).toInt())
    }

    /** Room to leave under the picture for what sits below it in the same column (the tour's loop bar). */
    var roomBelowPx = 0

    /** A tall picture may grow past [maxHeightDp] (the Button guide); off where the space sets the size. */
    var tallBoost = true


    override fun onDraw(canvas: Canvas) {
        val m = morph
        if (m != null) {
            if (drawMorph(canvas, m, SystemClock.uptimeMillis())) { postInvalidateOnAnimation(); return }
            m.before.recycle(); m.after?.recycle(); morph = null
        }
        drawPicture(canvas)
    }

    private fun drawPicture(canvas: Canvas) {
        val picture = svg ?: return
        if (width <= 0 || height <= 0) return
        val now = SystemClock.uptimeMillis()
        flashes.entries.removeAll { it.value < now }
        // The remote only lights what it has (A shows as OK, B as Back)
        val remote = kind == DeviceKind.REMOTE
        fun onThis(keys: Set<String>) = if (remote) keys.mapNotNull { DevicePicture.onRemote(it) }.toSet() else keys
        val lit = onThis(held + flashes.keys)
        val geo = css?.optJSONObject("_geo")
        // Sticks that move or click in are drawn by hand on top, so their own drawing steps aside
        val sticks = lit.filter { geo?.optJSONObject(it)?.has("stick") == true }
        val colour = String.format("#%06X", accent and 0xFFFFFF)
        // The press rings are drawn by hand below, one fixed size on every picture, so the picture's own ring stays hidden
        fun withoutRing(k: String, rule: String): String {
            val cls = geo?.optJSONObject(k)?.optJSONObject("ring")?.optString("cls") ?: return rule
            return rule.lines().filterNot { it.startsWith(".$cls{") }.joinToString("\n")
        }
        val rules = (lit - sticks.toSet()).mapNotNull { k -> css?.optString(k)?.takeIf { s -> s.isNotEmpty() }?.let { withoutRing(k, it) } } +
            sticks.flatMap { k -> geo!!.getJSONObject(k).getJSONObject("stick").getJSONArray("hide").let { a -> (0 until a.length()).map { ".${a.getString(it)}{display:none}" } } }
        val sheet = rules.joinToString("\n").replace("ACC", colour)
        // The drawing only changes when what's lit, the colour or the size does: keep the last one,
        // so moving sticks and filling rings don't redraw the whole picture every frame
        val key = "${System.identityHashCode(picture)}|$width|$height|$sheet"
        if (key != cacheKey || cache == null) {
            if (cache?.width != width || cache?.height != height) { cache?.recycle(); cache = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888) }
            val c = cache!!
            c.eraseColor(0)
            val options = RenderOptions.create().viewPort(0f, 0f, width.toFloat(), height.toFloat())
            if (sheet.isNotEmpty()) options.css(sheet)
            picture.renderToCanvas(Canvas(c), options)
            cacheKey = key
        }
        canvas.drawBitmap(cache!!, 0f, 0f, null)
        if (geo == null) return

        // Picture units to view pixels (the picture is centred and keeps its shape)
        val box = picture.documentViewBox ?: return
        val scale = min(width / box.width(), height / box.height())
        pictureScale = scale
        val ox = (width - box.width() * scale) / 2f - box.left * scale
        val oy = (height - box.height() * scale) / 2f - box.top * scale
        var animating = false

        sticks.forEach { k ->
            val g = geo.getJSONObject(k)
            val push = stickNow(k, now).also { if (stickSway.containsKey(k) && animationsOn()) animating = true }
            drawStick(canvas, g, push, ox, oy, scale, moving = stickSway.containsKey(k) || stickPush.containsKey(k))
        }

        val held = onThis(holding)
        (lit - sticks.toSet()).forEach { k ->
            val g = geo.optJSONObject(k) ?: return@forEach
            var p = 0f
            if (k in held) {
                val since = holdSince[k] ?: holdSince[holding.firstOrNull { DevicePicture.onRemote(it) == k }] ?: now
                val t = if (!animationsOn()) 1f else ((now - since - HOLD_DELAY_MS).toFloat() / HOLD_FILL_MS).coerceIn(0f, 1f)
                if (t < 1f) animating = true
                p = if (t <= 0f || t >= 1f) t else Design.EASE_STANDARD.getInterpolation(t)
            }
            val avoid = if (p > 0f) zonesExcept(k, lit - sticks.toSet(), geo, ox, oy, scale) else null
            // the remote's arrows are separate curved shapes close together: every ring keeps to its own side
            val sides = if (k in DPAD_ARMS && g.has("ring")) halfwayExcept(k, lit.filter { it in DPAD_ARMS }, geo, ox, oy, scale) else null
            if (sides != null) { val saved = canvas.save(); canvas.clipOutPath(sides); drawRing(canvas, g, p, ox, oy, scale, avoid); canvas.restoreToCount(saved) }
            else if (g.has("ring")) drawRing(canvas, g, p, ox, oy, scale, avoid)
            else if (k in DPAD_ARMS) drawArmRing(canvas, k, g, p, ox, oy, scale, avoid)
        }
        // Swaying sticks and filling rings: 60 times a second looks the same as a 120 Hz screen's every frame, at half the work
        if (animating) postInvalidateDelayed(16)
    }

    private fun animationsOn() = ValueAnimator.areAnimatorsEnabled()

    /** Where a stick is pushed: set by hand, or swaying to show its direction. */
    private fun stickNow(key: String, now: Long): PointF {
        stickPush[key]?.let { return it }
        val sideways = stickSway[key] ?: return PointF(0f, 0f)
        // Rests at each end, glides between them; still pictures show the first end
        val v = if (!animationsOn()) -1f else (-sin(2 * PI * ((now - swayStart) % SWAY_MS) / SWAY_MS).toFloat() * 2.2f).coerceIn(-1f, 1f)
        return if (sideways) PointF(v, 0f) else PointF(0f, v)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    /** The least room between two buttons that can light up together, in picture units (see [DevicePicture.tightestGap]). */
    private var tightest = Float.MAX_VALUE
    private var pictureScale = 1f
    /** The least free space round any button in this picture (picture units; measured by the kit's room.py). */
    private var leastRoom = Float.MAX_VALUE
    /**
     * How big the rings are in this picture (1 = full size). Every ring in a picture is the same size, and the
     * picture's tightest spot decides it: the widest look (a held Timer ring) plus a little air must fit in the
     * least room any button has, and two lit neighbours' press rings must keep clear of each other.
     */
    private val ringF: Float get() {
        val widest = (RING_GAP_DP + RING_W_DP + TIMER_GAP_DP + TIMER_W_DP) * dp
        val byRoom = (leastRoom * pictureScale - ROOM_AIR_DP * dp) / widest
        val byPairs = (tightest * pictureScale - RING_AIR_DP * dp) / (2 * (RING_GAP_DP + RING_W_DP) * dp)
        return min(1f, min(byRoom, byPairs)).coerceAtLeast(0f)
    }
    private val ringPx get() = (RING_GAP_DP + RING_W_DP) * dp * ringF
    private val gapPx get() = RING_GAP_DP * dp * ringF
    private val bandPx get() = RING_W_DP * dp * ringF

    /**
     * A pressed button's ring: outside the button, following its exact outline, a small even gap away,
     * the same size on every picture (like the outlines, it doesn't grow with the picture).
     * Held ([p] 0 to 1, already eased), [HoldStyle.look] adds the held look on top of the same ring.
     * Everything sits outside the button, so a label is never covered, however small the picture.
     */
    private fun drawRing(canvas: Canvas, g: org.json.JSONObject, p: Float, ox: Float, oy: Float, s: Float, avoid: Path? = null) {
        val shape = outline(g, ox, oy, s)
        ringBands(p).forEachIndexed { i, b ->
            val saved = canvas.save()
            if (i > 0 && avoid != null) canvas.clipOutPath(avoid)          // the held look steps aside for a lit neighbour
            band(canvas, shape, b[0], b[1], b[2])
            canvas.restoreToCount(saved)
        }
    }

    /** The far side of the line halfway between [key] and each of [others] (less the air): where their rings go. */
    private fun halfwayExcept(key: String, others: Collection<String>, geo: org.json.JSONObject, ox: Float, oy: Float, s: Float): Path? {
        val me = geo.optJSONObject(key) ?: return null
        fun mid(o: org.json.JSONObject) = PointF(ox + (o.getDouble("x") + o.getDouble("w") / 2).toFloat() * s, oy + (o.getDouble("y") + o.getDouble("h") / 2).toFloat() * s)
        var out: Path? = null
        for (k in others) {
            if (k == key) continue
            val g = geo.optJSONObject(k) ?: continue
            val a = mid(me); val b = mid(g); val dx = b.x - a.x; val dy = b.y - a.y; val len = hypot(dx, dy)
            if (len <= 0f) continue
            val nx = dx / len; val ny = dy / len; val far = 10000f
            val hx = (a.x + b.x) / 2 - nx * RING_AIR_DP * dp * ringF / 2; val hy = (a.y + b.y) / 2 - ny * RING_AIR_DP * dp * ringF / 2
            val h = Path().apply {
                moveTo(hx - ny * far, hy + nx * far); lineTo(hx + ny * far, hy - nx * far)
                lineTo(hx + ny * far + nx * far, hy - nx * far + ny * far); lineTo(hx - ny * far + nx * far, hy + nx * far + ny * far); close()
            }
            out = out?.also { it.op(h, Path.Op.UNION) } ?: h
        }
        return out
    }

    /** Everything lit except [key]: each button with its press ring and a little air round it (held rings stay out). */
    private fun zonesExcept(key: String, lit: Collection<String>, geo: org.json.JSONObject, ox: Float, oy: Float, s: Float): Path? {
        val zone = ringPx + RING_AIR_DP * dp * ringF
        var out: Path? = null
        for (k in lit) {
            if (k == key || (k in DPAD_ARMS && key in DPAD_ARMS)) continue
            val g = geo.optJSONObject(k) ?: continue
            if (g.has("stick")) continue
            val shape = outline(g, ox, oy, s)
            val z = Path(); Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2 * zone; strokeJoin = Paint.Join.ROUND }.getFillPath(shape, z)
            z.op(shape, Path.Op.UNION)
            // and the far side of the line halfway between the two (less the air), where the neighbour's held ring goes
            val me = geo.optJSONObject(key)
            if (me != null) {
                fun mid(o: org.json.JSONObject) = PointF(ox + (o.getDouble("x") + o.getDouble("w") / 2).toFloat() * s, oy + (o.getDouble("y") + o.getDouble("h") / 2).toFloat() * s)
                val a = mid(me); val b = mid(g); val dx = b.x - a.x; val dy = b.y - a.y; val len = hypot(dx, dy)
                if (len > 0f) {
                    val nx = dx / len; val ny = dy / len; val far = 10000f
                    val hx = (a.x + b.x) / 2 - nx * RING_AIR_DP * dp * ringF; val hy = (a.y + b.y) / 2 - ny * RING_AIR_DP * dp * ringF
                    z.op(Path().apply {
                        moveTo(hx - ny * far, hy + nx * far); lineTo(hx + ny * far, hy - nx * far)
                        lineTo(hx + ny * far + nx * far, hy - nx * far + ny * far); lineTo(hx - ny * far + nx * far, hy + nx * far + ny * far); close()
                    }, Path.Op.UNION)
                }
            }
            out = out?.also { it.op(z, Path.Op.UNION) } ?: z
        }
        return out
    }

    /**
     * The bands round a lit key, as (from, to, how much of the way round) in px out from its edge:
     * the press ring first, then the held look. One list, so buttons, D-pad arms and sticks all match.
     */
    private fun ringBands(p: Float): List<FloatArray> {
        val gap = gapPx; val w = bandPx; val f = ringPx / ((RING_GAP_DP + RING_W_DP) * dp)   // < 1 on a tight picture
        val out = mutableListOf(floatArrayOf(gap, gap + w, 1f))
        if (p > 0f) when (HoldStyle.look) {
            HoldStyle.Look.FILL -> out += floatArrayOf(0f, gap, p)                                   // the gap closes, clockwise
            HoldStyle.Look.SWELL -> out += floatArrayOf(gap + w, gap + w + SWELL_DP * dp * f * p, 1f)  // the ring grows bolder
            HoldStyle.Look.TIMER -> { val tg = TIMER_GAP_DP * dp * f; out += floatArrayOf(gap + w + tg, gap + w + tg + TIMER_W_DP * dp * f, p) }  // a second ring runs round
        }
        return out
    }

    /** Paints the band [from]..[to] px outside [shape], the first [p] of the way round from the top, clockwise. */
    private fun band(canvas: Canvas, shape: Path, from: Float, to: Float, p: Float) {
        if (to <= from || p <= 0f) return
        val near = Path()
        if (from > 0f) { Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2 * from; strokeJoin = Paint.Join.ROUND }.getFillPath(shape, near); near.op(shape, Path.Op.UNION) }
        else near.set(shape)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.BUTT; paint.color = accent; paint.strokeWidth = 2 * to
        val saved = canvas.save()
        canvas.clipOutPath(near)
        canvas.drawPath(if (p >= 1f) shape else sweep(shape, p), paint)
        canvas.restoreToCount(saved)
        paint.strokeCap = Paint.Cap.ROUND
    }

    /** The first [p] of [shape]'s outline, from the top middle, clockwise whichever way it was drawn. */
    private fun sweep(shape: Path, p: Float): Path {
        val m = PathMeasure(shape, true); val len = m.length
        val part = Path(); if (len <= 0f) return part
        var start = 0f; var best = Float.MAX_VALUE
        val pos = FloatArray(2)
        val bounds = RectF().also { shape.computeBounds(it, true) }
        var area = 0f; var px = 0f; var py = 0f
        val samples = 96
        for (k in 0..samples) {
            val d = len * k / samples
            m.getPosTan(d, pos, null)
            if (k > 0) area += px * pos[1] - pos[0] * py
            px = pos[0]; py = pos[1]
            if (pos[1] < bounds.centerY()) {
                val score = abs(pos[0] - bounds.centerX()) + 0.25f * (pos[1] - bounds.top)
                if (score < best) { best = score; start = d }
            }
        }
        fun piece(from: Float, to: Float) {
            val a = ((from % len) + len) % len; val b = a + (to - from)
            if (b <= len) m.getSegment(a, b, part, true) else { m.getSegment(a, len, part, true); m.getSegment(0f, b - len, part, true) }
        }
        if (area > 0f) piece(start, start + len * p) else piece(start - len * p, start)
        return part
    }

    /**
     * A lit D-pad arm gets the same bands as a button, round its three outer sides (the fourth side is the
     * middle of the cross). Each side stops short of the neighbouring arm's bands, so two lit arms never touch.
     */
    private fun drawArmRing(canvas: Canvas, key: String, g: org.json.JSONObject, p: Float, ox: Float, oy: Float, s: Float, avoid: Path? = null) {
        val x = ox + g.getDouble("x").toFloat() * s; val y = oy + g.getDouble("y").toFloat() * s
        val w = g.getDouble("w").toFloat() * s; val h = g.getDouble("h").toFloat() * s
        val turn = when (key) { "Dup" -> 0f; "Dright" -> 90f; "Ddown" -> 180f; else -> 270f }
        val across = if (turn % 180f == 0f) w else h; val along = if (turn % 180f == 0f) h else w
        val r = min(DPAD_CORNER * s, min(across, along) / 2)             // the cross's own corner
        val bands = ringBands(p)
        val outer = bands.maxOf { it[1] }
        val end = along / 2 - outer - gapPx                              // stop before the neighbouring arm's bands
        val l = -across / 2; val rt = across / 2; val top = -along / 2
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.BUTT; paint.color = accent
        for ((i, b) in bands.withIndex()) {
            val saved = canvas.save()
            if (i > 0 && avoid != null) canvas.clipOutPath(avoid)
            val o = (b[0] + b[1]) / 2                                    // the band's middle line, this far out
            val u = Path().apply {                                       // drawn for the up arm, then turned
                moveTo(l - o, end)
                lineTo(l - o, top + r)
                arcTo(RectF(l + r - (r + o), top + r - (r + o), l + r + (r + o), top + r + (r + o)), 180f, 90f)
                lineTo(rt - r, top - o)
                arcTo(RectF(rt - r - (r + o), top + r - (r + o), rt - r + (r + o), top + r + (r + o)), 270f, 90f)
                lineTo(rt + o, end)
                transform(Matrix().apply { setRotate(turn); postTranslate(x + w / 2, y + h / 2) })
            }
            paint.strokeWidth = b[1] - b[0]
            if (b[2] >= 1f) canvas.drawPath(u, paint)
            else { val m = PathMeasure(u, false); val part = Path(); m.getSegment(0f, m.length * b[2], part, true); canvas.drawPath(part, paint) }
            canvas.restoreToCount(saved)
        }
        paint.strokeCap = Paint.Cap.ROUND
    }

    /** A button's outline on screen: a circle, a rounded rectangle, or the kit's own path for it. */
    private fun outline(g: org.json.JSONObject, ox: Float, oy: Float, s: Float): Path {
        val x = ox + g.getDouble("x").toFloat() * s; val y = oy + g.getDouble("y").toFloat() * s
        val w = g.getDouble("w").toFloat() * s; val h = g.getDouble("h").toFloat() * s
        if (g.has("d") && g.has("m")) {
            val path = androidx.core.graphics.PathParser.createPathFromPathData(g.getString("d"))
            val mv = g.getJSONArray("m")
            val v = FloatArray(6) { mv.getDouble(it).toFloat() }
            path.transform(Matrix().apply { setValues(floatArrayOf(v[0], v[2], v[4], v[1], v[3], v[5], 0f, 0f, 1f)) })
            path.transform(Matrix().apply { setScale(s, s); postTranslate(ox, oy) })
            return path
        }
        val path = Path()
        val rx = if (g.has("rx")) g.getDouble("rx").toFloat() * s else null
        if (g.optString("tag") == "circle" || (rx == null || rx >= 0.45f * min(w, h)) && abs(w - h) < 0.15f * max(w, h)) {
            path.addOval(RectF(x, y, x + w, y + h), Path.Direction.CW)
        } else {
            val r = (rx ?: (min(w, h) * 0.35f)).coerceAtMost(min(w, h) / 2f)
            path.addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW)
        }
        return path
    }

    /** A lit stick: slid the way it's pushed with an arrow, or (not pushed) clicked in. */
    private fun drawStick(canvas: Canvas, g: org.json.JSONObject, push: PointF, ox: Float, oy: Float, s: Float, moving: Boolean) {
        val st = g.getJSONObject("stick")
        val cx = ox + (g.getDouble("x") + g.getDouble("w") / 2).toFloat() * s
        val cy = oy + (g.getDouble("y") + g.getDouble("h") / 2).toFloat() * s
        val r = st.getDouble("r").toFloat() * s
        val k = r / 23f                                   // the kit's sizes are for a stick of radius 23
        val wellR = st.optDouble("well", 0.0).toFloat() * s
        // Where the well is drawn, cover the stick's dark edge so nothing follows the stick around
        if (wellR > 0f) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor(st.optString("wellFill", "#0D0E12"))
            canvas.drawCircle(cx, cy, min(st.getDouble("halo").toFloat() * s + 0.6f * k, wellR - 0.5f * k), paint)
        }
        // A stick that's moving (swaying in the guide, or pushed) never shows the clicked-in look,
        // not even for the moment it passes the middle
        val pushed = moving || hypot(push.x, push.y) > 0.05f
        val showArrow = hypot(push.x, push.y) > 0.05f
        val inner = st.optDouble("inner", 0.0).toFloat() * s
        fun cap(x: Float, y: Float, f: Float) {
            paint.style = Paint.Style.FILL; paint.color = accent
            canvas.drawCircle(x, y, r * f, paint)
            if (inner > 0f) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f * dp; paint.color = Color.argb(115, 11, 15, 23)
                canvas.drawCircle(x, y, inner * f, paint)
            }
        }
        paint.strokeCap = Paint.Cap.ROUND
        if (pushed) {
            // A real stick tilts inside its well: the cap slides only as far as the well allows
            val d = if (wellR > 0f) min(10f * k, max(0f, wellR - r - 0.75f * dp)) else 10f * k
            val px = cx + push.x * d; val py = cy + push.y * d
            cap(px, py, 1f)
            if (showArrow) {
                // One arrow on the cap, pointing the way it's pushed: dark on the accent, like a button's label.
                // It always fits, whatever sits round the stick
                val a = atan2(push.y, push.x); val c = cos(a); val sn = sin(a)
                val g0 = 0.42f * r                                   // the arrow's size, from the cap's size
                val nx = -sn; val ny = c
                paint.style = Paint.Style.STROKE; paint.strokeWidth = max(1.4f * dp, 0.16f * r); paint.color = STICK_INK
                val arrow = Path().apply {
                    moveTo(px - c * g0 * 0.35f + nx * g0 * 0.6f, py - sn * g0 * 0.35f + ny * g0 * 0.6f)
                    lineTo(px + c * g0 * 0.35f, py + sn * g0 * 0.35f)
                    lineTo(px - c * g0 * 0.35f - nx * g0 * 0.6f, py - sn * g0 * 0.35f - ny * g0 * 0.6f)
                }
                canvas.drawPath(arrow, paint)
            }
        } else {
            // Clicked in: the stick sinks a little (smaller) and gets the same press ring as every button
            cap(cx, cy, CLICKED_IN)
            band(canvas, Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }, gapPx, gapPx + bandPx, 1f)
        }
    }

    private companion object {
        const val FLASH_MS = 260L
        const val TALL_BOOST = 1.45f
        const val HOLD_DELAY_MS = 120L     // a tap is over before the ring starts
        const val HOLD_FILL_MS = 380L      // then it fills round in this long
        const val SWAY_MS = 1800L          // a showing-the-direction stick: one full left-right
        val MORPH_KEYS = listOf("A", "B", "X", "Y", "Dup", "Ddown", "Dleft", "Dright", "LS", "RS", "SELECT", "START")
        const val MORPH_AIR = 6f          // picture units round the moving part
        // The press ring at full size: this far out from the button's edge, this thick. A tight picture shrinks both
        // together, just enough that two lit neighbours keep RING_AIR_DP of clear air between their rings.
        const val RING_GAP_DP = 1f
        const val RING_W_DP = 1.25f
        const val RING_AIR_DP = 0.75f
        const val ROOM_AIR_DP = 0.5f      // between the widest ring and the nearest other part of the drawing
        const val TIMER_GAP_DP = 0.75f    // HoldStyle TIMER: the second ring, this far out from the press ring
        const val TIMER_W_DP = 1f         // and this thick
        const val DPAD_CORNER = 6f        // the cross's corner radius in the kit (picture units)
        const val SWELL_DP = 1.5f         // HoldStyle SWELL: how much bolder a held ring grows
        const val CLICKED_IN = 0.86f      // a clicked-in stick's size
        val STICK_INK = Color.parseColor("#06121A")   // the kit's ink on a lit part (premium.js inkLit)
        val DPAD_ARMS = setOf("Dup", "Ddown", "Dleft", "Dright")
    }
}
