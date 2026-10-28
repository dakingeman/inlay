package com.example.gridime

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared look for the Settings app: black, calm, one white focus ring. */
object SettingsStyle {
    val background: Int = Design.SURFACE_BG
    val surface: Int = Design.SURFACE_CARD
    val surfaceFocused: Int = Design.SURFACE_RAISED
    val track: Int = Color.parseColor("#2E2E35")
    val textPrimary: Int = Design.TEXT_PRIMARY
    val textSecondary: Int = Design.TEXT_SECONDARY
    val divider: Int = Design.EDGE_DIVIDER
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /**
     * Your highlight colour. Settings uses it for everything that shows "you are here" or
     * "this is on": the active tab, the focus ring, chosen options, switches and sliders.
     * Text stays white, so the colour only appears where it means something.
     */
    var accent: Int = Color.parseColor("#8AB4F8")
        set(value) {
            field = value
            focusRings.forEach { it.setStroke(ringWidth, value) }
        }
    /** Readable text on top of the accent (white or near-black, picked by contrast). */
    val onAccent: Int get() = Contrast.inkFor(accent)

    /** Every focus ring made so far, so a new colour reaches them all at once. */
    private val focusRings = java.util.Collections.newSetFromMap(java.util.WeakHashMap<GradientDrawable, Boolean>())
    private var ringWidth = 0

    /**
     * True on narrow screens (portrait, small handhelds). Wide controls then sit under
     * their title instead of beside it, so nothing gets squeezed.
     */
    fun isNarrow(context: Context): Boolean = context.resources.configuration.screenWidthDp < 600

    private val argb = ArgbEvaluator()
    fun blend(from: Int, to: Int, t: Float): Int = argb.evaluate(t.coerceIn(0f, 1f), from, to) as Int

    /** Rounded background: filled + white ring when focused, soft fill when pressed. */
    fun focusBackground(context: Context, radiusDp: Float): Drawable {
        val dp = context.resources.displayMetrics.density
        ringWidth = (Design.STROKE_FOCUS * dp).roundToInt()
        fun shape(fill: Int, ring: Boolean) = GradientDrawable().apply {
            cornerRadius = radiusDp * dp
            setColor(fill)
            if (ring) {
                setStroke(ringWidth, accent)
                focusRings.add(this)
            }
        }
        return StateListDrawable().apply {
            setEnterFadeDuration(Design.MOTION_FADE.toInt())
            setExitFadeDuration(Design.MOTION_CONTROL.toInt())
            addState(intArrayOf(android.R.attr.state_focused), shape(surfaceFocused, ring = true))
            addState(intArrayOf(android.R.attr.state_pressed), shape(surfaceFocused, ring = false))
            addState(intArrayOf(), shape(Color.TRANSPARENT, ring = false))
        }
    }
}

// =========================================================================
// ROWS: Up/Down moves between rows, Left/Right changes the value, A acts
// =========================================================================

/**
 * One settings row. [stacked] puts the control under the text instead of beside it,
 * for controls that need the full width.
 */
abstract class SettingRow(
    context: Context,
    title: String,
    subtitle: String?,
    stacked: Boolean = false
) : LinearLayout(context) {

    companion object {
        /** Button icons used in subtitles. Set by the Settings screen before building rows. */
        var buttonStyle: ButtonStyle = ButtonStyle.XBOX
        /** Sounds and vibration for changes made in Settings. */
        var sounds: Feedback? = null
    }

    /** Called when Left is pressed on a row that has nothing to change. */
    var onLeaveLeft: (() -> Unit)? = null

    protected val dp = resources.displayMetrics.density
    protected fun px(v: Float) = (v * dp).roundToInt()
    private val stackedLayout = stacked
    private val titleView = TextView(context)
    private var subtitleView: View? = null
    private var control: View? = null

    /** Changes the row's words in place (for rows that change state, like "Cleared · Undo"). */
    protected fun setTexts(title: String, subtitle: String) {
        titleView.text = title
        (subtitleView as? TextView)?.text = subtitle
    }

    init {
        orientation = if (stacked) VERTICAL else HORIZONTAL
        gravity = if (stacked) Gravity.START else Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        // Android's own click sound would play on top of the keyboard's sound pack
        isSoundEffectsEnabled = false
        minimumHeight = px(64f)
        background = SettingsStyle.focusBackground(context, Design.RADIUS_ROW)
        setPadding(px(Design.SPACE_L), px(Design.SPACE_M), px(Design.SPACE_L), px(Design.SPACE_M))

        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(titleView.apply {
                text = title
                textSize = Design.TYPE_ROW
                typeface = SettingsStyle.medium
                setTextColor(SettingsStyle.textPrimary)
            })
            subtitle?.let {
                addView(subtitleView(context, it).also { v -> subtitleView = v },
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = px(Design.SPACE_XS / 2) })
            }
        }
        addView(texts, if (stacked) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        else LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener { activate() }
    }

    private fun subtitleView(context: Context, subtitle: String): View {
        val parts = HintText.parse(subtitle)
        return if (parts.none { it is HintPart.Badge }) {
            TextView(context).apply {
                text = subtitle
                textSize = Design.TYPE_CAPTION
                setTextColor(SettingsStyle.textSecondary)
                setLineSpacing(0f, 1.15f)
            }
        } else {
            BadgeLineView(context, parts, buttonStyle, Design.TYPE_CAPTION, SettingsStyle.textSecondary, badgeScale = 1.25f)
        }
    }

    protected fun setControl(view: View) {
        control?.let { removeView(it) }
        control = view
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        if (stackedLayout) params.topMargin = px(Design.SPACE_M) else params.marginStart = px(Design.SPACE_L)
        addView(view, params)
    }

    /** Left (-1) or Right (+1). Returns true if this row used the press. */
    protected open fun step(dir: Int): Boolean = false

    /** A button or a tap. */
    protected open fun activate() = Unit

    /** False for rows that only show information: A does nothing there, so it makes no sound. */
    protected open val interactive: Boolean get() = true

    /** Redraw anything that looks different while focused. */
    protected open fun onFocusLook(focused: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!step(-1)) onLeaveLeft?.invoke()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                step(1)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0 && interactive) {
                    pressBounce()
                    activate()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        onFocusLook(gainFocus)
    }

    /** A small press-in and release, so every action feels physical. */
    protected fun pressBounce() {
        animate().cancel()
        scaleX = 0.985f
        scaleY = 0.985f
        animate().scaleX(1f).scaleY(1f).setDuration(Design.MOTION_PRESS).setInterpolator(Design.EASE_OUT).start()
    }
}

/** Pick one of a few options. Left/Right or A to change, or tap an option. */
class ChoiceRow(
    context: Context,
    title: String,
    subtitle: String?,
    options: List<String>,
    selected: Int,
    private val onChange: (Int) -> Unit
) : SettingRow(context, title, subtitle, stacked = options.size >= 3 || SettingsStyle.isNarrow(context)) {

    private val segments = SegmentedView(context, options, selected) {
        sounds?.key()
        onChange(it)
    }
    private val count = options.size

    init {
        setControl(segments)
    }

    override fun step(dir: Int): Boolean {
        choose((segments.selectedIndex + dir).coerceIn(0, count - 1))
        return true
    }

    override fun activate() = choose((segments.selectedIndex + 1) % count)

    private fun choose(index: Int) {
        if (index == segments.selectedIndex) return
        segments.select(index)
        sounds?.key()
        onChange(index)
    }
}

/**
 * Pick one of many options (too many for a segmented pill): the current one sits
 * between two arrows. Left/Right step through them, A moves to the next one.
 * The live keyboard preview shows the result, so no list is needed.
 */
class StepperRow(
    context: Context,
    private val title: String,
    subtitle: String?,
    options: List<String>,
    selected: Int,
    /** One line per option, shown as the subtitle while that option is chosen (optional). */
    private val descriptions: List<String>? = null,
    /** True when [onChange] makes its own sound (e.g. a sound-pack sample). */
    private val silent: Boolean = false,
    private val onChange: (Int) -> Unit
) : SettingRow(context, title, descriptions?.getOrNull(selected) ?: subtitle, stacked = SettingsStyle.isNarrow(context)) {

    private val stepper = StepperView(context, options, selected) { onChange(it) }

    init {
        setControl(stepper)
    }

    override fun step(dir: Int): Boolean {
        choose(stepper.selectedIndex + dir)
        return true
    }

    override fun activate() = choose((stepper.selectedIndex + 1) % stepper.count)

    private fun choose(index: Int) {
        if (index !in 0 until stepper.count || index == stepper.selectedIndex) return
        stepper.select(index)
        if (!silent) sounds?.key()
        descriptions?.getOrNull(index)?.let { setTexts(title, it) }
        onChange(index)
    }

    override fun onFocusLook(focused: Boolean) {
        stepper.focused = focused
    }
}

/** On/off switch. Left = off, Right = on, A = flip. */
class ToggleRow(
    context: Context,
    title: String,
    subtitle: String?,
    on: Boolean,
    private val onChange: (Boolean) -> Unit
) : SettingRow(context, title, subtitle) {

    private val switch = SwitchView(context, on)

    init {
        setControl(switch)
    }

    override fun step(dir: Int): Boolean {
        set(dir > 0)
        return true
    }

    override fun activate() = set(!switch.isOn)

    private fun set(on: Boolean) {
        if (on == switch.isOn) return
        switch.set(on)
        if (on) sounds?.on() else sounds?.off()
        onChange(on)
    }
}

/** A value on a scale. Left/Right to adjust, or drag. */
class SliderRow(
    context: Context,
    title: String,
    subtitle: String?,
    min: Int,
    max: Int,
    value: Int,
    private val label: (Int) -> String,
    private val silent: Boolean = false,       // true when onChange plays its own demo
    private val onChange: (Int) -> Unit
) : SettingRow(context, title, subtitle, stacked = SettingsStyle.isNarrow(context)) {

    private val valueText = TextView(context).apply {
        textSize = 15f
        typeface = SettingsStyle.medium
        setTextColor(SettingsStyle.textPrimary)
        gravity = Gravity.END
        text = label(value)
    }
    private val slider = SliderView(context, min, max, value) { update(it) }

    init {
        val narrow = SettingsStyle.isNarrow(context)
        val control = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Under its title (narrow screens) the slider spans the row and the value sits at its end,
            // instead of floating in the middle (audit, 28 Oct 2026)
            if (narrow) addView(slider, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) else addView(slider)
            addView(valueText, LayoutParams(if (narrow) LayoutParams.WRAP_CONTENT else px(72f), LayoutParams.WRAP_CONTENT)
                .apply { if (narrow) marginStart = px(Design.SPACE_L) })
        }
        setControl(control)
        if (narrow) control.layoutParams = (control.layoutParams as LayoutParams).apply { width = LayoutParams.MATCH_PARENT }
    }

    override fun step(dir: Int): Boolean {
        val next = (slider.value + dir).coerceIn(slider.min, slider.max)
        if (next != slider.value) {
            slider.value = next
            if (!silent) sounds?.move()
            update(next)
        }
        return true
    }

    private fun update(v: Int) {
        valueText.text = label(v)
        onChange(v)
    }
}

/** Colour dots. Left/Right walks through them live; a ring marks the one in focus. */
class SwatchRow(
    context: Context,
    title: String,
    subtitle: String?,
    colours: List<Int>,
    selected: Int,
    private val onChange: (Int) -> Unit
) : SettingRow(context, title, subtitle, stacked = true) {

    private val grid = SwatchGridView(context, colours, selected) { onChange(it) }

    init {
        setControl(grid)
    }

    /** Clears the tick when a custom colour is chosen instead. */
    fun clearSelection() {
        grid.selectedIndex = -1
    }

    override fun step(dir: Int): Boolean {
        val current = grid.selectedIndex
        val next = if (current < 0) (if (dir > 0) 0 else grid.count - 1)
        else (current + dir).coerceIn(0, grid.count - 1)
        if (next != current) {
            grid.selectedIndex = next
            sounds?.move()
            onChange(next)
        }
        return true
    }

    override fun onFocusLook(focused: Boolean) {
        grid.showCursor = focused
    }
}

/** Does something when pressed. */
class ActionRow(
    context: Context,
    title: String,
    subtitle: String?,
    private val onPress: () -> Unit
) : SettingRow(context, title, subtitle) {
    init {
        setControl(TextView(context).apply {
            text = "›"
            textSize = 26f
            setTextColor(SettingsStyle.textSecondary)
        })
    }

    override fun activate() {
        sounds?.confirm()
        onPress()
    }
}

/**
 * Pick any colour: a rainbow track, Left/Right steps 10° around the colour wheel.
 * The thumb shows the colour itself.
 */
/**
 * One of the custom colour's sliders (colour, strength, brightness). Its track shows the colours it
 * really makes with the other two as they are now, and its knob is filled with the colour you'll get.
 * Left and right move it a step; held, the row repeats like every other.
 */
class ColourSliderRow(
    context: Context,
    title: String,
    subtitle: String?,
    initial: Int,
    private val max: Int,
    private val stepBy: Int,
    active: Boolean,
    colourAt: (Int) -> Int,
    private val onChange: (Int) -> Unit
) : SettingRow(context, title, subtitle, stacked = SettingsStyle.isNarrow(context)) {

    val slider = ColourSliderView(context, initial, max, active, colourAt) { onChange(it) }

    init {
        setControl(slider)
    }

    fun setActive(active: Boolean) {
        slider.active = active
    }

    override fun step(dir: Int): Boolean {
        val v = (slider.value + dir * stepBy).let { if (max == 359) (it + 360) % 360 else it.coerceIn(0, max) }
        if (v == slider.value && slider.active) return true
        slider.value = v
        slider.active = true
        sounds?.move()
        onChange(v)
        return true
    }
}

/** Read-only row with a value on the right. */
/** Long values (credits, licences) sit under the title, so neither gets squeezed (audit, 28 Oct 2026). */
class InfoRow(context: Context, title: String, value: String) : SettingRow(context, title, null, stacked = value.length > 28) {
    override val interactive get() = false

    init {
        setControl(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(SettingsStyle.textSecondary)
        })
    }
}

/** One line of the button guide: what it does, and the buttons that do it. */
class GuideRow(
    context: Context,
    val title: String,
    /** The buttons, as hint text ("{R2} + {X}"): the Button guide's picture lights them up. */
    val buttons: String
) : SettingRow(context, title, null) {
    override val interactive get() = false

    init {
        minimumHeight = px(52f)
        setControl(BadgeLineView(context, HintText.parse(buttons), buttonStyle, 14f, SettingsStyle.textSecondary, badgeScale = 1.45f))
    }
}

// =========================================================================
// CONTROLS (drawn by hand so they look the same everywhere)
// =========================================================================

/** Pill-shaped option picker with a sliding accent indicator. */
class SegmentedView(
    context: Context,
    private val options: List<String>,
    initial: Int,
    private val onTap: (Int) -> Unit
) : View(context) {

    private val dp = resources.displayMetrics.density
    var selectedIndex = initial
        private set
    private var indicator = initial.toFloat()
    private var anim: ValueAnimator? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        typeface = SettingsStyle.medium
    }
    private val pad = 4f * dp
    private val baseTextSize = text.textSize
    /** Room each option would like: its longest label plus comfortable padding. */
    private val naturalSegment: Float get() = options.maxOf { text.measureText(it) } + 32f * dp
    /** Room each option actually gets (less on narrow screens). */
    private val segmentWidth: Float get() = (width - 2 * pad) / options.size

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        text.textSize = baseTextSize
        val natural = (naturalSegment * options.size + 2 * pad).roundToInt()
        // On a narrow screen, shrink to the space there is instead of running off the edge
        setMeasuredDimension(resolveSize(natural, widthSpec), (40f * dp).roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // If the labels no longer fit, make the text a little smaller (never below 80 %)
        text.textSize = baseTextSize
        val widest = options.maxOf { text.measureText(it) }
        val room = segmentWidth - 12f * dp
        if (widest > room && widest > 0f) text.textSize = baseTextSize * maxOf(0.8f, room / widest)
    }

    fun select(index: Int) {
        if (index == selectedIndex) return
        selectedIndex = index
        anim?.cancel()
        anim = ValueAnimator.ofFloat(indicator, index.toFloat()).apply {
            duration = Design.MOTION_CONTROL
            interpolator = Design.EASE_OUT
            addUpdateListener {
                indicator = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val r = h / 2f
        fill.color = SettingsStyle.track
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, r, r, fill)

        val left = pad + indicator * segmentWidth
        fill.color = SettingsStyle.accent
        canvas.drawRoundRect(left, pad, left + segmentWidth, h - pad, r - pad, r - pad, fill)

        val baseline = h / 2f - (text.descent() + text.ascent()) / 2f
        options.forEachIndexed { i, label ->
            val onIndicator = 1f - min(1f, abs(indicator - i))
            text.color = SettingsStyle.blend(SettingsStyle.textPrimary, SettingsStyle.onAccent, onIndicator)
            canvas.drawText(label, pad + segmentWidth * (i + 0.5f), baseline, text)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val index = ((e.x - pad) / segmentWidth).toInt().coerceIn(0, options.lastIndex)
            if (index != selectedIndex) {
                select(index)
                onTap(index)
            }
        }
        return true
    }
}

/** On/off switch with a sliding knob. */
class SwitchView(context: Context, initial: Boolean) : View(context) {
    private val dp = resources.displayMetrics.density
    var isOn = initial
        private set
    private var position = if (initial) 1f else 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension((50f * dp).roundToInt(), (30f * dp).roundToInt())
    }

    fun set(on: Boolean) {
        isOn = on
        anim?.cancel()
        anim = ValueAnimator.ofFloat(position, if (on) 1f else 0f).apply {
            duration = Design.MOTION_CONTROL
            interpolator = Design.EASE_OUT
            addUpdateListener {
                position = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val r = h / 2f
        paint.color = SettingsStyle.blend(Color.parseColor("#3A3A42"), SettingsStyle.accent, position)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, r, r, paint)
        val knobR = r - 4f * dp
        val x = r + (width - 2 * r) * position
        paint.color = SettingsStyle.blend(Color.parseColor("#C8C8CE"), SettingsStyle.onAccent, position)
        canvas.drawCircle(x, r, knobR, paint)
    }
}

/** Slider: accent fill on a dark track, draggable by touch. */
class SliderView(
    context: Context,
    val min: Int,
    val max: Int,
    initial: Int,
    private val onDrag: (Int) -> Unit
) : View(context) {

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbR = 9f * dp

    var value = initial.coerceIn(min, max)
        set(v) {
            field = v.coerceIn(min, max)
            invalidate()
        }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(resolveSize((200f * dp).roundToInt(), widthSpec), (32f * dp).roundToInt())
    }

    private fun xFor(v: Int): Float {
        val span = width - 2 * thumbR
        return thumbR + span * (v - min) / (max - min).coerceAtLeast(1)
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val t = 2f * dp
        paint.color = SettingsStyle.track
        canvas.drawRoundRect(thumbR, cy - t, width - thumbR, cy + t, t, t, paint)
        val x = xFor(value)
        paint.color = SettingsStyle.accent
        canvas.drawRoundRect(thumbR, cy - t, x, cy + t, t, t, paint)
        canvas.drawCircle(x, cy, thumbR, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val span = (width - 2 * thumbR).coerceAtLeast(1f)
                val fraction = ((e.x - thumbR) / span).coerceIn(0f, 1f)
                val v = (min + fraction * (max - min)).roundToInt()
                if (v != value) {
                    value = v
                    onDrag(v)
                }
            }
        }
        return true
    }
}

/**
 * "‹  Norsk  ›" in a pill, sized for the longest option so it never jumps.
 * An arrow fades out at either end of the list, so you can see there's no more.
 */
class StepperView(
    context: Context,
    private val options: List<String>,
    initial: Int,
    private val onTap: (Int) -> Unit
) : View(context) {

    private val dp = resources.displayMetrics.density
    val count get() = options.size
    var selectedIndex = initial.coerceIn(0, options.lastIndex)
        private set
    var focused = false
        set(v) {
            field = v
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Design.STROKE_ICON * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        typeface = SettingsStyle.medium
        color = SettingsStyle.textPrimary
    }
    private val arrowZone = 36f * dp
    private var slide = 0f            // -1..1 while the new label slides in
    private var anim: ValueAnimator? = null

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val natural = (options.maxOf { text.measureText(it) } + 2 * arrowZone + 16f * dp).roundToInt()
        setMeasuredDimension(resolveSize(natural, widthSpec), (40f * dp).roundToInt())
    }

    fun select(index: Int) {
        val dir = if (index > selectedIndex) 1f else -1f
        selectedIndex = index
        anim?.cancel()
        anim = ValueAnimator.ofFloat(dir, 0f).apply {
            duration = Design.MOTION_CONTROL
            interpolator = Design.EASE_OUT
            addUpdateListener {
                slide = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        fill.color = SettingsStyle.track
        canvas.drawRoundRect(0f, 0f, w, h, r, r, fill)

        // Label: slides a little and fades in from the side you moved towards
        val shift = slide * 10f * dp
        text.alpha = ((1f - abs(slide)) * 255).roundToInt()
        val baseline = h / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(options[selectedIndex], w / 2f + shift, baseline, text)
        text.alpha = 255

        // Arrows: bright when focused, dim otherwise, gone at the ends of the list
        val s = 5f * dp
        val bright = if (focused) SettingsStyle.accent else SettingsStyle.textSecondary
        fun chevron(cx: Float, left: Boolean, visible: Boolean) {
            arrow.color = if (visible) bright else Contrast.withAlpha(SettingsStyle.textSecondary, 0x40)
            val d = if (left) 1f else -1f
            val path = Path().apply {
                moveTo(cx + d * s * 0.5f, r - s)
                lineTo(cx - d * s * 0.5f, r)
                lineTo(cx + d * s * 0.5f, r + s)
            }
            canvas.drawPath(path, arrow)
        }
        chevron(arrowZone / 2f + 4f * dp, left = true, visible = selectedIndex > 0)
        chevron(w - arrowZone / 2f - 4f * dp, left = false, visible = selectedIndex < options.lastIndex)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val next = when {
                e.x < arrowZone -> selectedIndex - 1
                e.x > width - arrowZone -> selectedIndex + 1
                else -> (selectedIndex + 1) % options.size
            }
            if (next in options.indices && next != selectedIndex) {
                select(next)
                onTap(next)
            }
        }
        return true
    }
}

/** Colour dots in rows of eight (fewer on narrow screens). */
class SwatchGridView(
    context: Context,
    private val colours: List<Int>,
    initial: Int,
    private val onTap: (Int) -> Unit
) : View(context) {

    private val dp = resources.displayMetrics.density
    private val cell = 40f * dp
    private var columns = MAX_COLUMNS
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val count: Int get() = colours.size

    var selectedIndex = initial.coerceIn(-1, colours.lastIndex)       // -1 = none chosen
        set(v) {
            field = v
            invalidate()
        }

    var showCursor = false
        set(v) {
            field = v
            invalidate()
        }

    private val rows get() = (colours.size + columns - 1) / columns

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val room = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE
        else MeasureSpec.getSize(widthSpec)
        columns = (room / cell).toInt().coerceIn(1, MAX_COLUMNS)
        setMeasuredDimension((cell * columns).roundToInt(), (cell * rows).roundToInt())
    }

    private companion object {
        const val MAX_COLUMNS = 8
    }

    override fun onDraw(canvas: Canvas) {
        val r = 13f * dp
        colours.forEachIndexed { i, colour ->
            val cx = cell * (i % columns) + cell / 2f
            val cy = cell * (i / columns) + cell / 2f
            paint.style = Paint.Style.FILL
            paint.color = colour
            canvas.drawCircle(cx, cy, r, paint)
            if (i == selectedIndex) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * dp
                paint.color = Contrast.inkFor(colour)
                val g = r * 0.45f
                canvas.drawPath(Path().apply {
                    moveTo(cx - g, cy)
                    lineTo(cx - g * 0.25f, cy + g * 0.7f)
                    lineTo(cx + g, cy - g * 0.6f)
                }, paint)
                if (showCursor) {
                    paint.strokeWidth = 2f * dp
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx, cy, r + 4.5f * dp, paint)
                }
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val index = (e.y / cell).toInt() * columns + (e.x / cell).toInt()
            if (index in colours.indices && index != selectedIndex) {
                selectedIndex = index
                SettingRow.sounds?.move()
                onTap(index)
            }
        }
        return true
    }
}

/** Draws a line of controller badges and words, e.g. "Hold [RT] and press [X]". */
class BadgeLineView(
    context: Context,
    private val parts: List<HintPart>,
    style: ButtonStyle,
    textSizeSp: Float,
    colour: Int,
    badgeScale: Float,
    gapBadgeToBadge: Float = 2f,
    gapBadgeToText: Float = 4f
) : View(context) {

    private val painter = BadgePainter(resources.displayMetrics.density).apply {
        this.style = style
        scale = badgeScale
        this.gapBadgeToBadge = gapBadgeToBadge
        this.gapBadgeToText = gapBadgeToText
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp, resources.displayMetrics)
        color = colour
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val textHeight = text.descent() - text.ascent()
        // Honour a fixed width from the layout (the tour lines badges up in a column), otherwise wrap
        setMeasuredDimension(
            resolveSize(painter.measureLine(parts, text).roundToInt() + 2, widthSpec),
            resolveSize((maxOf(painter.lineHeight(parts), textHeight) + 4).roundToInt(), heightSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        painter.drawLine(canvas, parts, 1f, height / 2f, text)
    }
}

/** Rainbow slider for a custom colour. The thumb is filled with the chosen colour. */
class ColourSliderView(
    context: Context,
    initial: Int,
    private val max: Int,
    initialActive: Boolean,
    /** The colour this slider makes at a value (with the other sliders as they are). */
    var colourAt: (Int) -> Int,
    private val onDrag: (Int) -> Unit
) : View(context) {

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbR = 11f * dp
    private val trackH = 6f * dp

    var value = initial.coerceIn(0, max)
        set(v) { field = v; invalidate() }

    /** Dimmed while a preset colour is chosen. */
    var active = initialActive
        set(v) { field = v; invalidate() }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(resolveSize((272f * dp).roundToInt(), widthSpec), (32f * dp).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val left = thumbR
        val right = width - thumbR
        // Thirteen stops: smooth enough for the colour wheel, and every stop a colour you can really pick
        val stops = IntArray(13) { colourAt((max * it / 12f).roundToInt()) }
        paint.shader = android.graphics.LinearGradient(left, 0f, right, 0f, stops, null, android.graphics.Shader.TileMode.CLAMP)
        paint.alpha = if (active) 255 else 110
        canvas.drawRoundRect(left, cy - trackH / 2, right, cy + trackH / 2, trackH / 2, trackH / 2, paint)
        paint.shader = null
        paint.alpha = 255

        val x = left + (right - left) * value / max.toFloat()
        paint.color = colourAt(value)
        canvas.drawCircle(x, cy, thumbR, paint)
        ring.color = if (active) Color.WHITE else Contrast.withAlpha(Color.WHITE, 90)
        ring.strokeWidth = 2f * dp
        canvas.drawCircle(x, cy, thumbR, ring)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val span = (width - 2 * thumbR).coerceAtLeast(1f)
                val v = (((e.x - thumbR) / span).coerceIn(0f, 1f) * max).roundToInt()
                if (v != value || !active) {
                    value = v
                    active = true
                    onDrag(v)
                }
            }
        }
        return true
    }
}

// =========================================================================
// SET-UP CARD AND UNDO ROW
// =========================================================================

/**
 * Shown at the top of Settings until Inlay is ready: two steps with ticks and one
 * accent button for whatever comes next. It disappears once both are done.
 */
class SetupRow(
    context: Context,
    enabled: Boolean,
    private val onAct: () -> Unit
) : SettingRow(context, "Set up Inlay", "Two quick steps, then you can type with your controller anywhere", stacked = true) {

    init {
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(step(1, "Turn on Inlay",
                if (enabled) "Done" else "Switch Inlay on in the list that opens",
                done = enabled, current = !enabled), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(step(2, "Make it your keyboard", "Pick Inlay in the list that opens",
                done = false, current = enabled), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(Design.SPACE_M) })
            addView(AccentButton(context).apply {
                text = if (enabled) "Choose Inlay" else "Turn on Inlay"
                textSize = Design.TYPE_ROW
                typeface = SettingsStyle.medium
                gravity = Gravity.CENTER
                setPadding(px(Design.SPACE_XL), px(Design.SPACE_M), px(Design.SPACE_XL), px(Design.SPACE_M))
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(Design.SPACE_L) })
        }
        setControl(body)
        // The steps use the card's full width (wrapping to the button's width cut their text off, audit 28 Oct 2026)
        body.layoutParams = (body.layoutParams as LayoutParams).apply { width = LayoutParams.MATCH_PARENT }
    }

    private fun step(n: Int, title: String, subtitle: String, done: Boolean, current: Boolean): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = if (done) 0.6f else 1f
            addView(StepDot(context, n, done, current), LayoutParams(px(28f), px(28f)))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 15f
                    typeface = SettingsStyle.medium
                    setTextColor(SettingsStyle.textPrimary)
                })
                addView(TextView(context).apply {
                    text = subtitle
                    textSize = Design.TYPE_CAPTION
                    setTextColor(SettingsStyle.textSecondary)
                })
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(Design.SPACE_M) })
        }

    override fun activate() {
        sounds?.confirm()
        onAct()
    }
}

/** A step marker: a tick when done, the number in an accent ring when it's next, grey after that. */
class StepDot(context: Context, private val n: Int, private val done: Boolean, private val current: Boolean) : View(context) {
    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        typeface = SettingsStyle.medium
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - dp
        if (done) {
            paint.style = Paint.Style.FILL
            paint.color = SettingsStyle.accent
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * dp
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = SettingsStyle.onAccent
            val g = r * 0.42f
            canvas.drawPath(Path().apply {
                moveTo(cx - g, cy)
                lineTo(cx - g * 0.25f, cy + g * 0.7f)
                lineTo(cx + g, cy - g * 0.6f)
            }, paint)
        } else {
            val colour = if (current) SettingsStyle.accent else SettingsStyle.textSecondary
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f * dp
            paint.color = colour
            canvas.drawCircle(cx, cy, r, paint)
            text.color = colour
            canvas.drawText(n.toString(), cx, cy - (text.descent() + text.ascent()) / 2f, text)
        }
    }
}

/**
 * An action that can be taken back: after pressing, the row itself says what happened
 * and offers Undo (no pop-up, no "Are you sure?"). [onDo] does the action and returns
 * how to undo it, or null if there was nothing to do.
 */
class UndoActionRow(
    context: Context,
    private val title: String,
    private val subtitle: String,
    private val doneTitle: String,
    private val doneSubtitle: String,
    private val onDo: () -> (() -> Unit)?
) : SettingRow(context, title, subtitle) {

    private var undo: (() -> Unit)? = null

    init {
        showReady()
    }

    private fun showReady() {
        setTexts(title, subtitle)
        setControl(TextView(context).apply {
            text = "›"
            textSize = 26f
            setTextColor(SettingsStyle.textSecondary)
        })
    }

    private fun showDone() {
        setTexts(doneTitle, doneSubtitle)
        setControl(BadgeLineView(context,
            listOf(HintPart.Badge(ControllerButton.A), HintPart.Text("Undo")),
            buttonStyle, 15f, SettingsStyle.accent, badgeScale = 1.35f))
    }

    override fun activate() {
        val pending = undo
        if (pending != null) {
            pending()
            undo = null
            sounds?.off()
            showReady()
        } else {
            undo = onDo()
            if (undo == null) {
                setTexts(title, "Nothing to clear right now")    // say so, rather than doing nothing silently
                sounds?.off()
                return
            }
            sounds?.confirm()
            showDone()
        }
    }
}

/** A pill button filled with the current accent; follows colour changes live. */
class AccentButton(context: Context) : TextView(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        fill.color = SettingsStyle.accent
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2f, h / 2f, fill)
        if (currentTextColor != SettingsStyle.onAccent) setTextColor(SettingsStyle.onAccent)
        super.onDraw(canvas)
    }
}
