package com.example.gridime

import android.view.KeyEvent

/**
 * Face buttons by position, whatever the device reports.
 *
 * Android's rule: BUTTON_A is the bottom face button, B the right, X the left, Y the top, whatever is
 * printed on them. Inlay is built on that rule. Some handhelds can be set to a Nintendo button layout
 * and then report by the printed letter instead (a Retroid Pocket 6 in its retro style: the right
 * button, printed A, sends BUTTON_A). Every press then lands on the opposite partner: left <-> top,
 * right <-> bottom, in every button style (Retroid report and video, 1 Nov 2026).
 *
 * With "Swap A/B and X/Y" on, each press is turned back into its position once, as it arrives, so
 * the keyboard, Settings, the tour and Try your buttons all see positions again. Only the four face
 * buttons change; nothing else, and never what other apps receive.
 */
object FaceButtons {

    /** A press as the job Inlay acts on, by the setup of the controller it came from (one lookup: [ButtonRoles]). */
    fun code(keyCode: Int, deviceId: Int): Int = ButtonRoles.setupFor(deviceId).job(keyCode)

    /** The same event as its job (the event itself when nothing changes). */
    fun event(event: KeyEvent): KeyEvent {
        val mapped = code(event.keyCode, event.deviceId)
        if (mapped == event.keyCode) return event
        return KeyEvent(
            event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState,
            event.deviceId, event.scanCode, event.flags, event.source
        )
    }

    /** The old two switches (Swap, Type with: Right), as a setup: kept for checks and for moving old settings across. */
    fun code(keyCode: Int, swap: Boolean, typeOnRight: Boolean = ButtonRoles.typeOnRight): Int =
        FaceSetup.fromOld(swap, typeOnRight).job(keyCode)
}

/**
 * Each controller's button setup (1.3, ButtonSetup.kt): where its four face buttons are and which one selects,
 * closes, deletes and is space. Everywhere else in Inlay, ControllerButton.A, {A} and "A" mean the job (select) and
 * B, X, Y the jobs close, delete, space. Only three things deal in positions, and they ask [position]: the badge
 * drawing ({A} shows the button you really press), the controller picture and the Konami Code.
 *
 * Kept small and fast: the setups are read from settings once ([apply]); a press looks up its controller in a tiny
 * cache, then its job in a four-entry list. A controller without its own setup uses the last one made.
 */
object ButtonRoles {
    /** The Settings row (Device tab). */
    const val ROW = "Your buttons"

    /** The setup in use now (the last controller pressed): what the badges, the picture and Konami follow. */
    @Volatile var current: FaceSetup = FaceSetup()
        private set
    private var main: FaceSetup = FaceSetup()
    private var byController: Map<String, FaceSetup> = emptyMap()
    private val byDevice = java.util.concurrent.ConcurrentHashMap<Int, FaceSetup>()

    /** True when the right button selects (the tour's words follow it). */
    val typeOnRight: Boolean get() = current.select == FaceSetup.RIGHT

    fun apply(s: KeyboardSettings) {
        main = s.face; byController = s.faceByController; byDevice.clear(); current = main
    }

    /** The setup for a press from [deviceId]: its own if it was set up, otherwise the last one made. */
    fun setupFor(deviceId: Int): FaceSetup {
        val found = byDevice[deviceId] ?: run {
            val key = descriptor(deviceId)
            (key?.let { byController[it] } ?: main).also { byDevice[deviceId] = it }
        }
        current = found
        return found
    }

    /** A controller's lasting name (the same after a restart), or null for none. */
    fun descriptor(deviceId: Int): String? =
        if (deviceId <= 0) null else runCatching { android.view.InputDevice.getDevice(deviceId)?.descriptor }.getOrNull()

    /** True when this controller hasn't been set up but another one has (Settings offers it once). */
    fun isNewController(deviceId: Int): Boolean {
        if (byController.isEmpty()) return false
        val key = descriptor(deviceId) ?: return false
        return key !in byController
    }

    /** Where a job's button is, as a position: A bottom, B right, X left, Y top. */
    fun position(button: ControllerButton): ControllerButton {
        val job = when (button) {
            ControllerButton.A -> current.select; ControllerButton.B -> current.close
            ControllerButton.X -> current.delete; ControllerButton.Y -> current.space
            else -> return button
        }
        return when (job) { FaceSetup.BOTTOM -> ControllerButton.A; FaceSetup.RIGHT -> ControllerButton.B; FaceSetup.LEFT -> ControllerButton.X; else -> ControllerButton.Y }
    }

    /** The same for the picture's names ("A", "B", "RT"...). */
    fun position(key: String): String = when (key) {
        "A" -> position(ControllerButton.A).name; "B" -> position(ControllerButton.B).name
        "X" -> position(ControllerButton.X).name; "Y" -> position(ControllerButton.Y).name
        else -> key
    }

    /** For a check: the right button selects (true) or the usual setup (false). */
    internal fun setForCheck(on: Boolean) { current = FaceSetup.fromOld(false, on); main = current; byDevice.clear(); byController = emptyMap() }
}
