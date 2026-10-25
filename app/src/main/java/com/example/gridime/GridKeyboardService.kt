package com.example.gridime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The keyboard itself: turns controller input into typing and editing. */
class GridKeyboardService : InputMethodService(), KeyGridView.Listener {

    private var gridView: KeyGridView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var settings = KeyboardSettings()
    private lateinit var feedback: Feedback
    private lateinit var history: ClipboardHistory

    private var clipboard: ClipboardManager? = null

    // ---- TV mode ---------------------------------------------------------------
    private val displays by lazy { getSystemService(android.hardware.display.DisplayManager::class.java) }

    /** Docking or undocking while the keyboard is up switches TV mode straight away. */
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshTvMode()
        override fun onDisplayRemoved(displayId: Int) = refreshTvMode()
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    /**
     * TV mode is on when set to On, or on Automatic when this is an Android TV device or a
     * TV / monitor is connected (a docked handheld shows its screen there).
     */
    private fun tvModeActive(): Boolean = when (settings.tvMode) {
        TvMode.ON -> true
        TvMode.OFF -> false
        TvMode.AUTO -> {
            val isTv = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            val external = runCatching {
                displays?.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.isNotEmpty() == true
            }.getOrDefault(false)
            isTv || external
        }
    }

    private fun refreshTvMode() {
        gridView?.setTvMode(tvModeActive())
        updatePreview()
    }

    /** Password boxes never show their text on the big screen: dots instead. */
    private fun isPassword(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        val v = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** Feeds the TV preview line with the text around the cursor. */
    private fun updatePreview() {
        val grid = gridView ?: return
        val ic = currentInputConnection ?: return grid.setPreview("", "", "")
        val before = ic.getTextBeforeCursor(PREVIEW_CHARS, 0)?.toString().orEmpty()
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(PREVIEW_CHARS / 2, 0)?.toString().orEmpty()
        fun hide(t: String) = if (isPassword()) "•".repeat(t.length) else t.replace('\n', ' ')
        grid.setPreview(hide(before), hide(selected), hide(after))
    }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipboardChanged() }

    // Cursor / selection position, kept up to date by Android
    private var selStart = 0
    private var selEnd = 0

    // Select mode: L1 / R1 grow or shrink the selection
    private var selecting = false
    private var anchor = 0
    private var movingEnd = 0
    private var lastSelectPress = 0L

    // Held-button repeats
    private val navRepeater = Repeater(handler)
    private val deleteRepeater = Repeater(handler)
    private val cursorRepeater = Repeater(handler)
    private var navDx = 0
    private var navDy = 0

    // D-pad (hat) and left stick
    private val stick = StickDirection()
    private var hatDx = 0
    private var hatDy = 0

    // Triggers. L2 = letters/symbols. R2 tap = shift, R2 hold = modifier.
    private var leftTriggerDown = false
    private var rightTriggerDown = false
    private var lastL2Time = 0L
    private var r2Held = false
    private var r2UsedAsModifier = false
    private var r2LastRelease = 0L

    private var lastSpaceTime = 0L
    private var lastField: FieldKind? = null
    private var bConsumed = false

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    override fun onCreate() {
        super.onCreate()
        Palette.init(this)
        settings = KeyboardSettings.load(this)
        feedback = Feedback(this).apply { update(settings) }
        history = ClipboardHistory(this).apply { reload() }
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.addPrimaryClipChangedListener(clipListener)
        displays?.registerDisplayListener(displayListener, handler)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        displays?.unregisterDisplayListener(displayListener)
        handler.removeCallbacksAndMessages(null)
        feedback.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        // The keyboard window itself has no background, so the Clear theme can show the app
        // behind it. Every other theme paints its own solid background, so nothing changes there.
        window.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        return KeyGridView(this).also { view ->
            view.listener = this
            view.feedback = feedback
            view.applySettings(settings)
            view.setClips(history.all)
            gridView = view
            updateClipPreview()
            view.setTvMode(tvModeActive())
        }
    }

    /** Keep the keyboard visible even though a gamepad counts as a hardware keyboard. */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    // =====================================================================
    // NO FULL-SCREEN TYPING BOX
    // In landscape, Android can cover the app with its own white "extract" text box.
    // We always want to type straight into the app's own field, so this is locked
    // off at three levels, in case a device or app forces it on anyway.
    // =====================================================================

    /** 1. Never ask for full-screen mode. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** 2. Even if full-screen is forced on, never show the white text box. */
    override fun onUpdateExtractingVisibility(ei: EditorInfo?) {
        setExtractViewShown(false)
    }

    /** 3. Keep the keyboard window only as tall as the keyboard, never the whole screen. */
    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, false, isCandidatesOnly)
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateFullscreenMode()                      // re-check full-screen for every new text box
        setExtractViewShown(false)
        // Pick up anything changed in the Settings app
        settings = KeyboardSettings.load(this)
        feedback.update(settings)
        history.reload()
        gridView?.apply {
            applySettings(settings)
            setClips(history.all)
            closeClips(focusOnHistoryButton = false)
            // A restart of the same box (chat apps after Send) keeps the layer and highlight
            val kind = fieldKind(info)
            if (!restarting || kind != lastField) setField(kind)
            lastField = kind
            setTvMode(tvModeActive())
        }
        selStart = info?.initialSelStart?.coerceAtLeast(0) ?: 0
        selEnd = info?.initialSelEnd?.coerceAtLeast(0) ?: 0
        exitSelectMode(collapse = false)
        refreshAutoShift()
        updateClipPreview()
        updatePreview()
    }

    /** Reads what kind of box this is from the app's own description (for smart keys). */
    private fun fieldKind(info: EditorInfo?): FieldKind {
        val type = info?.inputType ?: return FieldKind.TEXT
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> FieldKind.NUMBER
            // Times and dates need ":" and "/", which the number pad doesn't have
            InputType.TYPE_CLASS_DATETIME -> FieldKind.TEXT
            InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                else -> FieldKind.TEXT
            }
            else -> FieldKind.TEXT
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetHeldInputs()
        exitSelectMode(collapse = false)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        resetHeldInputs()
    }

    private fun resetHeldInputs() {
        stopNav()
        deleteRepeater.stop()
        cursorRepeater.stop()
        stick.reset()
        hatDx = 0
        hatDy = 0
        r2Held = false
        rightTriggerDown = false
        leftTriggerDown = false
        gridView?.setHeldShift(false)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (selecting) gridView?.setSelecting(true, abs(selEnd - selStart)) else refreshAutoShift()
        updatePreview()
    }

    // =====================================================================
    // ACTIONS FROM THE KEY GRID (sound and vibration are played by the grid)
    // =====================================================================
    override fun onText(text: String) {
        if (text == " " && tryDoubleSpacePeriod()) return
        if (selecting) exitSelectMode(collapse = false)
        currentInputConnection?.commitText(text, 1)
        refreshAutoShift()
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        if (selecting) exitSelectMode(collapse = false)
        refreshAutoShift()
    }

    override fun onEnter() {
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val actionAllowed = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        if (actionAllowed && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)      // Search / Go / Send
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)              // new line
        }
        refreshAutoShift()
    }

    override fun onTool(tool: Tool) {
        val ic = currentInputConnection ?: return
        when (tool) {
            Tool.SELECT -> toggleSelectMode()
            Tool.SELECT_ALL -> selectAll()
            Tool.COPY -> {
                copySelection()
                exitSelectMode(collapse = false)
            }
            Tool.CUT -> {
                if (copySelection()) ic.commitText("", 1)
                exitSelectMode(collapse = false)
            }
            Tool.PASTE -> {
                val text = clipText()
                if (text != null) ic.commitText(text, 1) else ic.performContextMenuAction(android.R.id.paste)
                exitSelectMode(collapse = false)
                refreshAutoShift()
            }
            Tool.HISTORY -> gridView?.toggleClips()
            Tool.SETTINGS -> {
                feedback.confirm()
                openSettings()
            }
        }
    }

    override fun onClip(index: Int) {
        val text = history.all.getOrNull(index) ?: return
        if (selecting) exitSelectMode(collapse = false)
        currentInputConnection?.commitText(text, 1)
        refreshAutoShift()
    }

    private fun openSettings() {
        requestHideSelf(0)
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    // =====================================================================
    // TYPING HELPERS
    // =====================================================================
    /** Two quick spaces after a word become ". " (when turned on in Settings). */
    private fun tryDoubleSpacePeriod(): Boolean {
        val now = SystemClock.uptimeMillis()
        val quick = now - lastSpaceTime < DOUBLE_PRESS_MS
        lastSpaceTime = now
        if (!settings.doubleSpacePeriod || !quick || selecting) return false
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(2, 0) ?: return false
        if (before.length < 2 || before[1] != ' ' || !before[0].isLetterOrDigit()) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(1, 0)
        ic.commitText(". ", 1)
        ic.endBatchEdit()
        lastSpaceTime = 0L
        refreshAutoShift()
        return true
    }

    /** Hold X: letters speed up, then whole words, like phone keyboards. */
    private fun startDelete() {
        deleteRepeater.start(
            firstDelay = DELETE_DELAY_MS,
            intervalFor = { n -> if (n < DELETE_WORDS_AFTER) max(DELETE_FASTEST_MS, DELETE_START_MS - n * 7L) else DELETE_WORD_MS }
        ) { n ->
            val ic = currentInputConnection ?: return@start
            val hasSelection = selStart != selEnd
            when {
                n == 0 || hasSelection || n < DELETE_WORDS_AFTER -> {
                    if (n == 0) feedback.delete() else feedback.deleteRepeat()
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
                else -> {
                    feedback.delete()
                    TextOps.deleteWordBefore(ic)
                }
            }
            if (selecting) exitSelectMode(collapse = false)
            refreshAutoShift()
        }
    }

    /**
     * Hold RT + Y: the letter just before the cursor steps through its accented
     * forms (e → é → è → ê …), so accents never need a touch screen.
     */
    private fun cycleAccent() {
        val ic = currentInputConnection ?: return
        if (selStart != selEnd) return feedback.off()
        val before = ic.getTextBeforeCursor(1, 0)
        val next = before?.firstOrNull()?.let { Accents.next(it, settings.language) }
        if (next == null) {
            feedback.off()                           // nothing to accent: a soft "no"
            return
        }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(1, 0)
        ic.commitText(next, 1)
        ic.endBatchEdit()
        feedback.key()
    }

    private fun undo() {
        currentInputConnection?.let { TextOps.undo(it) }
        feedback.off()
        refreshAutoShift()
    }

    private fun refreshAutoShift() {
        val grid = gridView ?: return
        val ic = currentInputConnection ?: return
        val inputType = currentInputEditorInfo?.inputType ?: 0
        val caps = if (inputType != 0) ic.getCursorCapsMode(inputType) else 0
        grid.setAutoShift(caps != 0)
    }

    // =====================================================================
    // SELECTION AND CURSOR
    // =====================================================================
    /** Select: one press = select mode on/off, two quick presses = select all. */
    private fun onSelectButton() {
        val now = SystemClock.uptimeMillis()
        if (now - lastSelectPress < DOUBLE_PRESS_MS) {
            lastSelectPress = 0L
            selectAll()
        } else {
            lastSelectPress = now
            toggleSelectMode()
        }
    }

    private fun toggleSelectMode() {
        if (selecting) {
            exitSelectMode(collapse = true)
            feedback.off()
            return
        }
        feedback.on()
        selecting = true
        anchor = selStart
        movingEnd = selEnd
        gridView?.setSelecting(true, abs(selEnd - selStart))
    }

    private fun selectAll() {
        val ic = currentInputConnection ?: return
        ic.performContextMenuAction(android.R.id.selectAll)
        feedback.selectAll()
        selecting = true
        anchor = 0
        movingEnd = TextOps.textLength(ic, selStart, selEnd)
        gridView?.setSelecting(true, movingEnd)
    }

    /** Leaves select mode. [collapse] also clears the highlight, leaving the cursor at its end. */
    private fun exitSelectMode(collapse: Boolean) {
        if (collapse && selecting) {
            val end = max(selStart, selEnd)
            currentInputConnection?.setSelection(end, end)
        }
        selecting = false
        gridView?.setSelecting(false, 0)
    }

    private fun moveCursor(dir: Int) {
        val ic = currentInputConnection ?: return
        if (selecting) {
            movingEnd = (movingEnd + dir).coerceIn(0, TextOps.textLength(ic, selStart, selEnd))
            ic.setSelection(min(anchor, movingEnd), max(anchor, movingEnd))
        } else {
            sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        }
    }

    /** R2 + L1 / R1: jump (or select) a whole word. */
    private fun jumpWord(dir: Int) {
        val ic = currentInputConnection ?: return
        val (text, offset) = TextOps.textAround(ic, selStart, selEnd)
        val from = when {
            selecting -> movingEnd
            dir < 0 -> min(selStart, selEnd)
            else -> max(selStart, selEnd)
        }
        val target = TextOps.wordBoundary(text, from - offset, dir) + offset
        if (selecting) {
            movingEnd = target
            ic.setSelection(min(anchor, target), max(anchor, target))
        } else {
            ic.setSelection(target, target)
        }
    }

    private fun startCursor(dir: Int) {
        cursorRepeater.start(
            firstDelay = CURSOR_DELAY_MS,
            intervalFor = { n -> max(CURSOR_FASTEST_MS, CURSOR_START_MS - n * 8L) }
        ) {
            feedback.move()
            if (r2Held) {
                r2UsedAsModifier = true
                jumpWord(dir)
            } else {
                moveCursor(dir)
            }
        }
    }

    // =====================================================================
    // CLIPBOARD
    // =====================================================================
    private fun copySelection(): Boolean {
        val ic = currentInputConnection ?: return false
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            ic.performContextMenuAction(android.R.id.copy)
            return false
        }
        clipboard?.setPrimaryClip(ClipData.newPlainText("text", selected))
        return true
    }

    private fun clipText(): String? = runCatching {
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
    }.getOrNull()

    private fun onClipboardChanged() {
        val clip = runCatching { clipboard?.primaryClip }.getOrNull()
        // Password managers mark passwords as sensitive: never keep those
        val sensitive = clip?.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true
        if (settings.saveClipHistory && !sensitive) {
            clipText()?.let {
                history.add(it)
                gridView?.setClips(history.all)
            }
        }
        updateClipPreview()
    }

    private fun updateClipPreview() {
        gridView?.setClipPreview(clipText())
    }

    // =====================================================================
    // CONTROLLER BUTTONS
    // =====================================================================
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val grid = gridView
        if (grid == null || !isInputViewShown) return super.onKeyDown(keyCode, event)
        // Held buttons repeat on our own timers, so Android's repeats are ignored
        val first = event.repeatCount == 0

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (first) startNav(0, -1, fromStick = false)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (first) startNav(0, 1, fromStick = false)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (first) startNav(-1, 0, fromStick = false)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (first) startNav(1, 0, fromStick = false)

            KeyEvent.KEYCODE_BUTTON_A -> if (first) {
                if (r2Held) r2UsedAsModifier = true
                grid.pressSelected()
            }
            KeyEvent.KEYCODE_BUTTON_X -> if (first) onXButton(grid)
            KeyEvent.KEYCODE_BUTTON_Y -> if (first) {
                if (r2Held) {
                    r2UsedAsModifier = true
                    cycleAccent()
                } else {
                    feedback.space()
                    onText(" ")
                }
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> if (first) startCursor(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> if (first) startCursor(1)
            KeyEvent.KEYCODE_BUTTON_L2 -> if (first) onL2()
            KeyEvent.KEYCODE_BUTTON_R2 -> if (first) r2Down()
            KeyEvent.KEYCODE_BUTTON_SELECT -> if (first) onSelectButton()
            KeyEvent.KEYCODE_BUTTON_THUMBL -> if (first) cycleSize()
            KeyEvent.KEYCODE_BUTTON_START -> if (first) {
                feedback.confirm()
                onEnter()
            }

            // B closes select mode or the clipboard first; otherwise it's Android's Back
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (!first && bConsumed) return true
                if (selecting || grid.isClipsOpen()) {
                    if (grid.isClipsOpen()) grid.closeClips(focusOnHistoryButton = true)
                    else exitSelectMode(collapse = true)
                    feedback.back()
                    bConsumed = true
                } else {
                    bConsumed = false
                    return super.onKeyDown(keyCode, event)
                }
            }

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (gridView == null || !isInputViewShown) return super.onKeyUp(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> stopNav()
            KeyEvent.KEYCODE_BUTTON_X -> deleteRepeater.stop()
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> cursorRepeater.stop()
            KeyEvent.KEYCODE_BUTTON_R2 -> r2Up()
            KeyEvent.KEYCODE_BUTTON_B -> {
                val consumed = bConsumed
                bConsumed = false
                return if (consumed) true else super.onKeyUp(keyCode, event)
            }
            else -> if (keyCode !in HANDLED_KEYS) return super.onKeyUp(keyCode, event)
        }
        return true
    }

    private fun onXButton(grid: KeyGridView) {
        when {
            grid.isClipsOpen() -> grid.focusedClipIndex()?.let {
                history.removeAt(it)
                grid.setClips(history.all)
                feedback.delete()
            }
            r2Held && settings.undoCombo -> {
                r2UsedAsModifier = true
                undo()
            }
            else -> startDelete()
        }
    }

    private fun onL2() {
        val now = SystemClock.uptimeMillis()
        if (now - lastL2Time < TRIGGER_DEDUPE_MS) return
        lastL2Time = now
        // Hold RT + LT: next language (when more than one is picked in Settings)
        if (r2Held && settings.languages.size > 1) {
            r2UsedAsModifier = true
            switchLanguage()
            return
        }
        gridView?.toggleLayer()
    }

    /** RT + LT: the next language in the quick switch. Saved, so it stays after closing. */
    private fun switchLanguage() {
        settings = KeyboardSettings.load(this).nextLanguage()
        settings.save(this)
        gridView?.apply {
            applySettings(settings)
            flashLanguage()
        }
        feedback.on()
    }

    /** R2 down: show capitals, then wait to see whether it's a tap or a hold. */
    private fun r2Down() {
        if (r2Held) return
        if (SystemClock.uptimeMillis() - r2LastRelease < TRIGGER_DEDUPE_MS) return
        r2Held = true
        r2UsedAsModifier = false
        gridView?.setHeldShift(true)
    }

    /** R2 up: if nothing else was pressed with it, it was a shift tap. */
    private fun r2Up() {
        if (!r2Held) return
        r2Held = false
        r2LastRelease = SystemClock.uptimeMillis()
        gridView?.setHeldShift(false)
        if (!r2UsedAsModifier) gridView?.cycleShift()
    }

    /** L3: normal -> large -> largest -> normal. */
    private fun cycleSize() {
        settings = KeyboardSettings.load(this).let { it.copy(sizeLevel = (it.sizeLevel + 1) % SIZE_COUNT) }
        settings.save(this)
        gridView?.applySettings(settings)
        feedback.on()
    }

    // =====================================================================
    // D-PAD, STICK AND TRIGGERS (joystick signals)
    // =====================================================================
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (gridView == null || !isInputViewShown) return false
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        if (event.action == MotionEvent.ACTION_MOVE) {
            readTriggers(event)
            readDirections(event)
        }
        // Claim every joystick sample. If any slipped through, Android would turn
        // it into its own D-pad presses and the highlight would jump twice.
        return true
    }

    private fun readTriggers(event: MotionEvent) {
        val lt = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        if (!leftTriggerDown && lt > TRIGGER_PRESS) {
            leftTriggerDown = true
            onL2()
        } else if (leftTriggerDown && lt < TRIGGER_RELEASE) {
            leftTriggerDown = false
        }
        if (!rightTriggerDown && rt > TRIGGER_PRESS) {
            rightTriggerDown = true
            r2Down()
        } else if (rightTriggerDown && rt < TRIGGER_RELEASE) {
            rightTriggerDown = false
            r2Up()
        }
    }

    private fun readDirections(event: MotionEvent) {
        val stickChanged = stick.update(
            event.getAxisValue(MotionEvent.AXIS_X),
            event.getAxisValue(MotionEvent.AXIS_Y),
            SystemClock.uptimeMillis()
        )
        val hx = hatValue(event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val hy = hatValue(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        val hatChanged = hx != hatDx || hy != hatDy
        hatDx = hx
        hatDy = hy
        if (!stickChanged && !hatChanged) return
        when {
            hatDx != 0 || hatDy != 0 -> startNav(hatDx, if (hatDx != 0) 0 else hatDy, fromStick = false)
            stick.dx != 0 || stick.dy != 0 -> startNav(stick.dx, stick.dy, fromStick = true)
            else -> stopNav()
        }
    }

    private fun hatValue(v: Float) = if (v > 0.5f) 1 else if (v < -0.5f) -1 else 0

    /** One move right away, a pause, then repeats that gently speed up. */
    private fun startNav(dx: Int, dy: Int, fromStick: Boolean) {
        if (dx == navDx && dy == navDy && navRepeater.isRunning) return
        navDx = dx
        navDy = dy
        val speed = settings.stickSpeed.coerceIn(0, STICK_INTERVAL_MS.lastIndex)
        val base = if (fromStick) STICK_INTERVAL_MS[speed] else DPAD_INTERVAL_MS
        val fastest = (base * 0.55).toLong()
        val delay = if (fromStick) STICK_DELAY_MS[speed] else DPAD_DELAY_MS
        navRepeater.start(delay, { n -> max(fastest, (base * 0.92.pow(n)).toLong()) }) {
            gridView?.moveFocus(dx, dy)
        }
    }

    private fun stopNav() {
        navRepeater.stop()
        navDx = 0
        navDy = 0
    }

    private companion object {
        /** Same key as ClipDescription.EXTRA_IS_SENSITIVE, spelled out so it also works before Android 13. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        // Navigation timing
        const val DPAD_DELAY_MS = 350L
        const val DPAD_INTERVAL_MS = 110L
        val STICK_DELAY_MS = longArrayOf(520L, 460L, 400L, 350L, 300L)      // slow ... fast
        val STICK_INTERVAL_MS = longArrayOf(240L, 190L, 150L, 120L, 95L)

        // Hold-to-delete curve
        const val DELETE_DELAY_MS = 400L
        const val DELETE_START_MS = 100L
        const val DELETE_FASTEST_MS = 50L
        const val DELETE_WORDS_AFTER = 20
        const val DELETE_WORD_MS = 160L

        // Hold L1 / R1
        const val CURSOR_DELAY_MS = 400L
        const val CURSOR_START_MS = 110L
        const val CURSOR_FASTEST_MS = 45L

        const val TRIGGER_PRESS = 0.6f
        const val TRIGGER_RELEASE = 0.3f
        const val TRIGGER_DEDUPE_MS = 80L
        const val DOUBLE_PRESS_MS = 350L
        const val PREVIEW_CHARS = 60
        const val SIZE_COUNT = 3          // Normal, Large, Largest

        val HANDLED_KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_START
        )
    }
}
