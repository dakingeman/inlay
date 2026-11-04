package com.example.gridime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The Konami Code, entered only in Try your buttons: up, up, down, down, left, right, left, right, B, A.
 * Each press must be let go of before the next one (two buttons down at once is mashing), at a human pace
 * (no more than [MAX_GAP_MS] between presses). A wrong press starts over silently; a press that is the first step
 * starts over at step one. Buttons are matched by position, as the picture shows them.
 *
 * The build-up: presses 1 and 2 are only a very light double buzz, 3 and 4 a slightly stronger one, and a sound
 * fades in that climbs with every press to a short climax on the last one. Test builds can pick between four
 * build-up sounds (lab_<n>_<step>.ogg in the preview build's own resources); the release build has none of them
 * and stays silent, with the buzz only, until a sound is chosen for 1.3.
 */
class KonamiCode(private val context: Context, private val onFound: () -> Unit) {
    private var step = 0
    private var lastAt = 0L
    private val down = HashSet<String>()
    var sound = 0
        set(v) { if (field != v) { field = v; loaded.clear() } }

    /** A button went down (picture key names: Dup, Ddown, Dleft, Dright, A, B, …). */
    fun press(key: String) {
        if (key in down) return                           // the same press again from a second source (a hat and a key)
        val now = SystemClock.uptimeMillis()
        val mashing = down.isNotEmpty()
        down += key
        if (step > 0 && (now - lastAt > MAX_GAP_MS || mashing)) step = 0
        lastAt = now
        step = when {
            key == CODE[step] && !mashing -> step + 1
            key == CODE[0] && !mashing -> 1
            else -> 0
        }
        if (step == 0) return
        try { feel(step) } catch (e: Throwable) { android.util.Log.e(TAG, "buzz failed", e); onProblem("buzz: $e") }
        try { play(step) } catch (e: Throwable) { android.util.Log.e(TAG, "sound failed", e); onProblem("sound: $e") }
        if (step == CODE.size) { step = 0; onFound() }
    }

    fun release(key: String) { down -= key }
    /** Test builds show what went wrong (a buzz or sound the device refused), instead of failing silently. */
    var onProblem: (String) -> Unit = {}
    fun reset() { step = 0; down.clear() }

    // ---- The buzz ---------------------------------------------------------------------------------------------
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    /** Whether to buzz at all: follows Inlay's own Vibration switch (Sound & haptics). */
    var buzz: () -> Boolean = { true }
    /** Test builds tune the buzz by feel: [strength] 0..3 (Light, Medium, Strong, Max), [style] see [BUZZ_STYLES]. */
    var strength = 1
    var style = 2

    /**
     * The buzz builds with the code: press 1 a light tick, press 2 a clear double tick, then every press a bit
     * stronger than the last, and a rising triple pulse on the last one.
     *  - Calibrated: Android's haptic primitives (made to feel alike on every motor), where the device has them.
     *  - Taps: short, sharp pulses. Pulses: longer ones. Rumble: a fast on-off pattern, like a game controller.
     * Strength scales both how hard (with amplitude control) and how long; without amplitude control, length is
     * all there is, so it grows more. Nothing is shorter than 30 ms.
     */
    private fun feel(n: Int) {
        if (!buzz()) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val f = (n - 1f) / (CODE.size - 1)                   // 0 on the first press, 1 on the last
        val effect = (if (style == 0) primitives(v, n, f) else null) ?: waveform(v, n, f)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_MEDIA))
            else @Suppress("DEPRECATION") v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
        }.onFailure { android.util.Log.w(TAG, "buzz failed", it) }
        android.util.Log.d(TAG, "step $n buzz: ${if (usedPrimitives) "primitives" else BUZZ_STYLES[style.coerceIn(0, 3)].lowercase()}, " +
            "strength ${BUZZ_STRENGTHS[strength.coerceIn(0, 3)]}, amplitude control ${v.hasAmplitudeControl()}")
    }
    private var usedPrimitives = false
    private val power get() = floatArrayOf(0.45f, 0.65f, 0.85f, 1f)[strength.coerceIn(0, 3)]
    private val stretch get() = floatArrayOf(0.8f, 1f, 1.4f, 2f)[strength.coerceIn(0, 3)]
    private fun primitives(v: Vibrator, n: Int, f: Float): VibrationEffect? {
        usedPrimitives = false
        if (Build.VERSION.SDK_INT < 30) return null
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val thud = VibrationEffect.Composition.PRIMITIVE_THUD
        val rise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        if (!v.areAllPrimitivesSupported(click, thud, rise)) return null
        usedPrimitives = true
        val p = power
        fun s(x: Float) = (x * p / 0.85f).coerceIn(0.05f, 1f)
        val c = VibrationEffect.startComposition()
        when {
            n == 1 -> c.addPrimitive(click, s(0.55f))
            n == 2 -> { c.addPrimitive(click, s(0.7f)); c.addPrimitive(click, s(0.7f), 70) }
            n < CODE.size -> { c.addPrimitive(thud, s(0.6f + 0.4f * f)); if (strength >= 3) c.addPrimitive(click, 1f, 20) }
            else -> { c.addPrimitive(rise, 1f); c.addPrimitive(thud, 1f, 40); c.addPrimitive(click, 1f, 60) }
        }
        return c.compose()
    }
    private fun waveform(v: Vibrator, n: Int, f: Float): VibrationEffect {
        val amp = v.hasAmplitudeControl()
        val p = power
        // without amplitude control every pulse is full strength, so strength and the build-up live in the length
        val k = if (amp) stretch else stretch * 1.3f
        fun a(x: Float) = if (amp) (x * p / 0.85f * 255).toInt().coerceIn(30, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        fun ms(x: Float) = (x * k).toLong().coerceAtLeast(30)
        val level = 0.7f + 0.3f * f
        if (n == CODE.size) {
            val on = ms(55f)
            return VibrationEffect.createWaveform(longArrayOf(0, on, 60, on, 60, on * 2),
                if (amp) intArrayOf(0, a(0.85f), 0, a(0.95f), 0, 255) else intArrayOf(0, 255, 0, 255, 0, 255), -1)
        }
        return when (style) {
            2 -> if (n <= 2) twice(ms(40f), a(level), amp, n) else VibrationEffect.createOneShot(ms(50f + 50f * f), a(level))      // Pulses
            3 -> {                                                                                                                // Rumble
                val total = ms(70f + 90f * f); val times = mutableListOf(0L); val amps = mutableListOf(0)
                var t = 0L; while (t < total) { times += 22L; amps += a(level); times += 14L; amps += 0; t += 36L }
                VibrationEffect.createWaveform(times.toLongArray(), if (amp) amps.toIntArray() else amps.map { if (it > 0) 255 else 0 }.toIntArray(), -1)
            }
            else -> if (n <= 2) twice(ms(30f), a(level), amp, n) else VibrationEffect.createOneShot(ms(32f + 24f * f), a(level))  // Taps (and Calibrated without primitives)
        }
    }
    /** Press 1: one tick; press 2: two. */
    private fun twice(on: Long, amplitude: Int, amp: Boolean, n: Int): VibrationEffect =
        if (n == 1) VibrationEffect.createOneShot(on, amplitude)
        else VibrationEffect.createWaveform(longArrayOf(0, on, 70, on), if (amp) intArrayOf(0, amplitude, 0, amplitude) else intArrayOf(0, 255, 0, 255), -1)

    // ---- The sound (test builds) --------------------------------------------------------------------------------
    private val pool: SoundPool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val loaded = HashMap<Int, Int>()
    /** Loads this build-up's clips ahead of time, so the first press doesn't wait. */
    fun prepare() { for (n in 2..CODE.size) clip(n) }
    private fun clip(n: Int): Int? {
        loaded[n]?.let { return it }
        val id = context.resources.getIdentifier("lab_${sound}_$n", "raw", context.packageName)
        if (id == 0) return null
        return pool.load(context, id, 1).also { loaded[n] = it }
    }
    private fun play(n: Int) {
        if (n < 2) return                                  // the first press is only the buzz; the sound starts, softly, on the second
        val id = clip(n) ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }
    fun release() = pool.release()

    /**
     * When the last press's clip reaches its final note (ms): the reward's words land on it. Measured from the clips
     * (where the loudest, last note starts). Without a sound, a short beat.
     */
    val finalNoteMs: Long get() = if (clip(CODE.size) == null) 600L else FINAL_NOTE_MS.getOrElse(sound) { 600L }

    companion object {
        val CODE = listOf("Dup", "Dup", "Ddown", "Ddown", "Dleft", "Dright", "Dleft", "Dright", "B", "A")
        const val MAX_GAP_MS = 3000L
        private const val TAG = "InlayKonami"
        /** The build-up sounds a test build can pick from (their clips exist only in the preview build). */
        val SOUNDS = listOf("Original", "Shapes", "Melody", "Arpeggio")
        private val FINAL_NOTE_MS = listOf(730L, 730L, 460L, 450L)
        val BUZZ_STRENGTHS = listOf("Light", "Medium", "Strong", "Max")
        val BUZZ_STYLES = listOf("Calibrated", "Taps", "Pulses", "Rumble")
        fun hasLab(context: Context) = context.packageName.endsWith(".preview")
    }
}
