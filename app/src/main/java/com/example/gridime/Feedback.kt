package com.example.gridime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.random.Random

/**
 * Sound and vibration for every keyboard action, in one place.
 *
 * Nine sound packs share the same ten cues and the same meanings (see SoundPack):
 * physical ones (Soft, Thock, Tactile, Typewriter, Felt) and tonal ones (Chime, Glass,
 * Pop, Retro). Every pack has four letter sounds, rotated so the same one never plays
 * twice in a row. Tonal packs use C major pentatonic in pure ratios, so sounds never clash.
 * The files are made and checked by tools/sounds.py and tools/soundcheck.py, with the
 * rules in docs/brand/SOUND.md: one loudness order for all packs, held on any speaker.
 * The rule in every pack: ONE action = ONE hit, and one vibration pulse.
 *
 * Vibration and sound always fire together, and every pulse starts with a short
 * full-power kick so small vibration motors spin up and the pulse is actually felt.
 */
class Feedback(context: Context) {

    private var settings = KeyboardSettings()

    // ---- Vibration -----------------------------------------------------------
    // Android 12+ has a VibratorManager; older versions hand out the Vibrator directly.
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    // ---- Sound ---------------------------------------------------------------
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)                 // follows media volume
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** One sound per event, loaded for the chosen pack. */
    private enum class Cue { MOVE, KEY, SPACE, DELETE, ON, OFF, LOCK, SELECT_ALL, CONFIRM, BACK }

    private val appContext = context.applicationContext

    /**
     * Sounds load in the background. A sound asked for before it's ready waits here and
     * plays the moment it is, instead of being silently skipped (the first key press after
     * switching packs used to make no sound).
     */
    private val ready = HashSet<Int>()
    private val waiting = HashMap<Int, Float>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Speed: starting a sound or a vibration means a short call into Android's sound and
     * vibration services. On the keyboard's main thread every one of those calls made the
     * next button wait. They now run on their own little helper thread, so a press is
     * drawn straight away and the click and buzz follow in parallel.
     */
    private val worker = android.os.HandlerThread("inlay-feedback", android.os.Process.THREAD_PRIORITY_DISPLAY)
        .apply { start() }
    private val workerHandler = android.os.Handler(worker.looper)
    private var loadedPack: SoundPack? = null
    private val ids = IntArray(Cue.values().size)

    /** Extra "takes" for letters: real keys never sound identical twice. */
    private val keyTakes = mutableListOf<Int>()
    private var lastTake = -1

    /** Loads the pack's sounds, releasing the old ones. Only one pack is in memory at a time. */
    private fun loadPack(pack: SoundPack) {
        if (pack == loadedPack) return
        ids.forEach { if (it != 0) pool.unload(it) }
        keyTakes.forEach { pool.unload(it) }
        ready.clear()
        waiting.clear()
        keyTakes.clear()
        resourcesFor(pack).forEachIndexed { i, res -> ids[i] = pool.load(appContext, res, 1) }
        keyTakes += ids[Cue.KEY.ordinal]
        takesFor(pack).forEach { keyTakes += pool.load(appContext, it, 1) }
        lastTake = -1
        loadedPack = pack
    }

    /** A different take from last time, so fast typing never sounds machine-gunned. */
    private fun nextKeyTake(): Int {
        if (keyTakes.size <= 1) return ids[Cue.KEY.ordinal]
        var i = Random.nextInt(keyTakes.size - 1)
        if (i >= lastTake && lastTake >= 0) i++
        lastTake = i
        return keyTakes[i]
    }

    fun update(newSettings: KeyboardSettings) {
        settings = newSettings
        loadPack(newSettings.soundPack)
    }

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready += id
            waiting.remove(id)?.let { volume -> playNow(id, volume) }
        }
        loadPack(settings.soundPack)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        workerHandler.removeCallbacksAndMessages(null)
        workerHandler.post {
            pool.release()
            worker.quitSafely()
        }
    }

    /**
     * A short phrase in the current pack, for trying packs in Settings:
     * two letters, a space, a letter, Enter. Sound only, no vibration.
     */
    fun sample() {
        handler.removeCallbacksAndMessages(null)
        val phrase = listOf(Cue.KEY to 0L, Cue.KEY to 170L, Cue.SPACE to 340L, Cue.KEY to 520L, Cue.CONFIRM to 760L)
        phrase.forEach { (cue, at) ->
            handler.postDelayed({
                if (cue == Cue.KEY) playId(nextKeyTake(), Design.SOUND_KEY) else play(cue, Design.SOUND_KEY)
            }, at)
        }
    }

    // ---- The What's new sound showcase: any pack, sound only (no vibration), never saved ----
    /** Loads [pack] for the showcase. Call [update] with the real settings afterwards to go back to yours. */
    fun showcasePack(pack: SoundPack) = loadPack(pack)
    fun showcaseKey() = playId(nextKeyTake(), Design.SOUND_KEY)
    fun showcaseConfirm() = play(Cue.CONFIRM, Design.SOUND_KEY)

    // =====================================================================
    // EVENTS
    // =====================================================================

    /** The highlight moved to another key. */
    fun move() {
        tick()
        if (settings.moveSounds) play(Cue.MOVE, Design.SOUND_MOVE)
    }

    /** A letter, number or symbol was typed. */
    fun key() {
        click()
        playId(nextKeyTake(), Design.SOUND_KEY)
    }

    fun space() {
        click()
        play(Cue.SPACE, Design.SOUND_KEY)
    }

    fun delete() {
        click()
        play(Cue.DELETE, Design.SOUND_KEY)
    }

    /** Held delete: lighter, so a long run of deletes isn't tiring. */
    fun deleteRepeat() {
        tick()
        play(Cue.DELETE, Design.SOUND_KEY * 0.6f)
    }

    /** Something switched on: shift, symbols, select mode, clipboard. */
    fun on() {
        latch(heavy = false)
        play(Cue.ON, Design.SOUND_MODIFIER)
    }

    /** Something switched off. */
    fun off() {
        click()
        play(Cue.OFF, Design.SOUND_MODIFIER)
    }

    /** Caps lock. */
    fun lock() {
        latch(heavy = true)
        play(Cue.LOCK, Design.SOUND_MODIFIER)
    }

    fun selectAll() {
        latch(heavy = false)
        play(Cue.SELECT_ALL, Design.SOUND_MODIFIER)
    }

    /** Enter, paste, picking a clipboard item. */
    fun confirm() {
        click()
        play(Cue.CONFIRM, Design.SOUND_CONFIRM)
    }

    /** Leaving a mode or a view with B. */
    fun back() {
        tick()
        play(Cue.BACK, Design.SOUND_CONFIRM)
    }

    // =====================================================================
    // VIBRATION PATTERNS
    // =====================================================================
    // Strength 0..10 sets both how long and how hard the motor runs.

    private fun tick() {
        val level = settings.moveStrength
        if (level <= 0) return
        pulse(bodyMs = 10L + level * 3L, amplitude = 120 + level * 13)
    }

    private fun click() {
        val level = settings.pressStrength
        if (level <= 0) return
        pulse(bodyMs = 18L + level * 4L, amplitude = 140 + level * 11)
    }

    /**
     * A single, weightier pulse for switches and triggers: longer and stronger than a
     * key press, with a gentle fade at the end instead of a second bump.
     * Rule: one action = one pulse. Actions differ by weight, never by counting pulses.
     */
    private fun latch(heavy: Boolean) {
        val level = settings.pressStrength
        if (level <= 0) return
        val body = 24L + level * 4L + if (heavy) 14L else 0L
        val amp = amplitude(165 + level * 8 + if (heavy) 15 else 0)
        waveform(
            longArrayOf(Design.HAPTIC_KICK_MS, body, 14L),
            intArrayOf(255, amp, amp / 3)                       // settle, don't stop dead
        )
    }

    /** One pulse: a full-power kick, then the body at the chosen strength. */
    private fun pulse(bodyMs: Long, amplitude: Int) {
        waveform(longArrayOf(Design.HAPTIC_KICK_MS, bodyMs), intArrayOf(255, amplitude(amplitude)))
    }

    private fun amplitude(value: Int) = value.coerceIn(1, 255)

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        if (!settings.vibration) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                // Motor can only switch on and off: use the same timing, full power
                VibrationEffect.createWaveform(timings, IntArray(amplitudes.size) { if (amplitudes[it] > 0) 255 else 0 }, -1)
            }
            workerHandler.post { runCatching { vibrateAsTouch(v, effect) } }
        }
    }

    /**
     * Tag the pulse as touch feedback so it keeps working in silent mode.
     * Android 13+ has a dedicated "touch" tag; older versions use the closest audio tag.
     */
    private fun vibrateAsTouch(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }
    }

    // =====================================================================
    // SOUND
    // =====================================================================
    private fun play(cue: Cue, gain: Float) = playId(ids[cue.ordinal], gain)

    companion object {
        /** The ten cues of a pack, in [Cue] order. */
        internal fun resourcesFor(pack: SoundPack): IntArray = when (pack) {
            SoundPack.SOFT -> intArrayOf(
                R.raw.soft_move, R.raw.soft_type, R.raw.soft_space, R.raw.soft_delete, R.raw.soft_on,
                R.raw.soft_off, R.raw.soft_lock, R.raw.soft_select_all, R.raw.soft_confirm, R.raw.soft_back
            )
            SoundPack.THOCK -> intArrayOf(
                R.raw.thock_move, R.raw.thock_type, R.raw.thock_space, R.raw.thock_delete, R.raw.thock_on,
                R.raw.thock_off, R.raw.thock_lock, R.raw.thock_select_all, R.raw.thock_confirm, R.raw.thock_back
            )
            SoundPack.TACTILE -> intArrayOf(
                R.raw.tactile_move, R.raw.tactile_type, R.raw.tactile_space, R.raw.tactile_delete, R.raw.tactile_on,
                R.raw.tactile_off, R.raw.tactile_lock, R.raw.tactile_select_all, R.raw.tactile_confirm, R.raw.tactile_back
            )
            SoundPack.TYPEWRITER -> intArrayOf(
                R.raw.typewriter_move, R.raw.typewriter_type, R.raw.typewriter_space, R.raw.typewriter_delete, R.raw.typewriter_on,
                R.raw.typewriter_off, R.raw.typewriter_lock, R.raw.typewriter_select_all, R.raw.typewriter_confirm, R.raw.typewriter_back
            )
            SoundPack.FELT -> intArrayOf(
                R.raw.felt_move, R.raw.felt_type, R.raw.felt_space, R.raw.felt_delete, R.raw.felt_on,
                R.raw.felt_off, R.raw.felt_lock, R.raw.felt_select_all, R.raw.felt_confirm, R.raw.felt_back
            )
            SoundPack.CHIME -> intArrayOf(
                R.raw.chime_move, R.raw.chime_type, R.raw.chime_space, R.raw.chime_delete, R.raw.chime_on,
                R.raw.chime_off, R.raw.chime_lock, R.raw.chime_select_all, R.raw.chime_confirm, R.raw.chime_back
            )
            SoundPack.GLASS -> intArrayOf(
                R.raw.glass_move, R.raw.glass_type, R.raw.glass_space, R.raw.glass_delete, R.raw.glass_on,
                R.raw.glass_off, R.raw.glass_lock, R.raw.glass_select_all, R.raw.glass_confirm, R.raw.glass_back
            )
            SoundPack.POP -> intArrayOf(
                R.raw.pop_move, R.raw.pop_type, R.raw.pop_space, R.raw.pop_delete, R.raw.pop_on,
                R.raw.pop_off, R.raw.pop_lock, R.raw.pop_select_all, R.raw.pop_confirm, R.raw.pop_back
            )
            SoundPack.RETRO -> intArrayOf(
                R.raw.retro_move, R.raw.retro_type, R.raw.retro_space, R.raw.retro_delete, R.raw.retro_on,
                R.raw.retro_off, R.raw.retro_lock, R.raw.retro_select_all, R.raw.retro_confirm, R.raw.retro_back
            )
        }

        /** The three extra letter sounds of a pack (with the main one, four in all). */
        internal fun takesFor(pack: SoundPack): IntArray = when (pack) {
            SoundPack.SOFT -> intArrayOf(R.raw.soft_type1, R.raw.soft_type2, R.raw.soft_type3)
            SoundPack.THOCK -> intArrayOf(R.raw.thock_type1, R.raw.thock_type2, R.raw.thock_type3)
            SoundPack.TACTILE -> intArrayOf(R.raw.tactile_type1, R.raw.tactile_type2, R.raw.tactile_type3)
            SoundPack.TYPEWRITER -> intArrayOf(R.raw.typewriter_type1, R.raw.typewriter_type2, R.raw.typewriter_type3)
            SoundPack.FELT -> intArrayOf(R.raw.felt_type1, R.raw.felt_type2, R.raw.felt_type3)
            SoundPack.CHIME -> intArrayOf(R.raw.chime_type1, R.raw.chime_type2, R.raw.chime_type3)
            SoundPack.GLASS -> intArrayOf(R.raw.glass_type1, R.raw.glass_type2, R.raw.glass_type3)
            SoundPack.POP -> intArrayOf(R.raw.pop_type1, R.raw.pop_type2, R.raw.pop_type3)
            SoundPack.RETRO -> intArrayOf(R.raw.retro_type1, R.raw.retro_type2, R.raw.retro_type3)
        }

        /**
         * Volume slider 1..10 as a gain: every step is the same 2 dB, so each one sounds like
         * the same change. 10 = the files as mastered, the default 7 sits 6 dB below that
         * (room to go louder), and 1 is 18 dB below. 0 is off.
         */
        /** True when the device is on silent or vibrate (sounds then play only with "Play in silent mode" on). */
        fun silentNow(context: Context): Boolean =
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode.let { it != null && it != AudioManager.RINGER_MODE_NORMAL }

        fun volumeGain(step: Int): Float =
            if (step <= 0) 0f else Math.pow(10.0, (Design.SOUND_STEP_DB * (step.coerceAtMost(10) - 10)) / 20.0).toFloat()
    }

    private fun playId(soundId: Int, gain: Float) {
        if (settings.soundVolume <= 0) return
        val level = volumeGain(settings.soundVolume)
        // A tiny loudness change keeps repeats from sounding robotic. Pitch never
        // changes: every sound is tuned to the same scale and must stay in tune.
        val jitter = 1f + (Random.nextFloat() * 2f - 1f) * Design.SOUND_GAIN_JITTER
        val volume = (level * gain * jitter).coerceIn(0f, 1f)
        if (soundId !in ready) {
            waiting[soundId] = volume                // plays as soon as it has loaded
            return
        }
        playNow(soundId, volume)
    }

    /** Plays on the helper thread. Checking silent mode is itself a call into Android, so it happens there too. */
    private fun playNow(soundId: Int, volume: Float) {
        val playInSilent = settings.soundInSilent
        workerHandler.post {
            val silent = audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL
            if (!silent || playInSilent) pool.play(soundId, volume, volume, 1, 0, 1f)
        }
    }
}
