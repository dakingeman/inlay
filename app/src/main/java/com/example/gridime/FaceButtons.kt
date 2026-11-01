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

    /** The position Inlay should act on for a key the device sent. */
    fun code(keyCode: Int, swap: Boolean): Int = if (!swap) keyCode else when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
        KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
        else -> keyCode
    }

    /** The same event with its face button turned into its position (the event itself when nothing changes). */
    fun event(event: KeyEvent, swap: Boolean): KeyEvent {
        val mapped = code(event.keyCode, swap)
        if (mapped == event.keyCode) return event
        return KeyEvent(
            event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState,
            event.deviceId, event.scanCode, event.flags, event.source
        )
    }
}
