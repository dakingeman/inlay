package com.example.gridime

import android.animation.ValueAnimator
import android.os.SystemClock

/**
 * "What's new": a short screen that shows the highlights of an update, once.
 *
 * It opens by itself the first time Settings opens after an update (never on the keyboard,
 * so it never interrupts typing). New users get the tour instead, which also counts as having
 * seen this. Updates with only fixes leave [VERSION] alone, so no screen appears for them.
 * It can be opened again from About → What's new.
 *
 * The pages themselves live in [TutorialActivity] (same layout, same badges, same acted-out
 * demos as the tour), so the two always look and behave alike.
 *
 * Every update with something new: bump [VERSION], write a page per highlight (keep it to the few that matter), and make the matching
 * card and short clip for GitHub from the same storyboard (see docs/UPDATING.md).
 * Each page's demo loops, and every looping demo shows its loop on the keyboard window's edge ([LoopClock]:
 * the demo reports its runs; see TutorialActivity.every and runSteps). No new page goes without it.
 *
 * The look comes for free: a new page is only its Page entry (title, one sentence, button rows, demo).
 * The background ([Backdrop], lit from the top right for What's new, top left for the tour), the
 * "NEW IN [VERSION]" tag or "WELCOME TOUR" label, the window around the text box and keyboard, and the
 * user's highlight colour (presets and the slider alike) are all applied by TutorialActivity.
 * The sound showcase (Demo.SOUNDS) is a template too: it plays every pack in SoundPack, with its name tag. Long words
 * shrink step by step to fit, and the consistency checks catch anything that still doesn't.
 */
object WhatsNew {
    /** The update these highlights belong to. Change it only when there's something to show. */
    const val VERSION = "1.2.0"

    fun shouldShow(settings: KeyboardSettings) = settings.tutorialDone && settings.whatsNewSeen != VERSION

    /**
     * Android's "Remove animations" (accessibility) sets the animation speed to zero.
     * Our demos then show one still moment instead of moving.
     */
    fun motionAllowed(): Boolean = ValueAnimator.areAnimatorsEnabled()
}

/**
 * The loop clock: the timing behind the loop indicator (the keyboard window's bottom edge filling, drawn by
 * StagePanel). Every looping demo tells it when each run begins and how long it takes, worked out from the
 * demo's own timetable before it plays (TutorialActivity.every and runSteps), so the edge is exactly full the
 * moment the demo starts over. It reads the clock on every frame, so it never drifts or stutters.
 *
 * The rule for new pages: every looping demo gives the length of its runs, and the indicator shows by itself.
 * ConsistencyTest checks every page: each run takes exactly as long as it said. Only the welcome page's
 * device showcase, which has no beginning or end, goes without. With animations turned off, nothing moves.
 */
class LoopClock {
    private var loopStart = 0L
    private var loopMs = 0L            // 0: no run yet
    /** When the previous run ended, so its full edge can fade as the next begins (-1: nothing fading). */
    var fadeFrom = -1L
        private set

    /** For the checks: when each run began and how long it said it would take. */
    internal val runsForCheck = mutableListOf<Pair<Long, Long>>()

    /** A new page: nothing until its demo begins its first run. */
    fun restart() { loopMs = 0L; fadeFrom = -1L; runsForCheck.clear() }

    /** A run of the demo begins [at] (its timetable) and takes [runMs]. */
    /** Told when each run begins (the live caption starts its round of rows again). */
    var onRun: (() -> Unit)? = null

    fun begin(runMs: Long, at: Long = SystemClock.uptimeMillis()) {
        onRun?.invoke()
        if (loopMs > 0L) fadeFrom = at
        loopStart = at
        loopMs = runMs.coerceAtLeast(1L)
        if (runsForCheck.size < 64) runsForCheck += at to loopMs
    }

    val running get() = loopMs > 0L

    /** This run's progress now, 0 to 1. */
    fun progress(now: Long = SystemClock.uptimeMillis()): Float = if (loopMs > 0L) ((now - loopStart) / loopMs.toFloat()).coerceIn(0f, 1f) else 0f

    /** How strongly the previous run's full edge still shows (1 fading to 0 over [Design.MOTION_LOOP_RESET]). */
    fun fade(now: Long = SystemClock.uptimeMillis()): Float =
        if (fadeFrom < 0L) 0f else (1f - (now - fadeFrom) / Design.MOTION_LOOP_RESET.toFloat()).coerceIn(0f, 1f)

    companion object {
        /** How often the edge redraws while it fills: about 30 times a second is smooth for something that moves over seconds. */
        const val FRAME_MS = 33L
    }
}
