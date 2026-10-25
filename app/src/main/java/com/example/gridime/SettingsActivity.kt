package com.example.gridime

import android.animation.ValueAnimator
import android.graphics.Rect
import android.app.Activity
import android.media.AudioManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * The Settings app, built for a controller:
 *  - LB / RB switch between the section tabs at the top
 *  - Up / Down moves between settings, Left / Right changes one, A acts
 *  - Y shows or hides the live keyboard preview
 *  - B closes
 * The bar at the bottom always shows these, in your chosen button style.
 */
class SettingsActivity : Activity() {

    private lateinit var settings: KeyboardSettings
    private lateinit var feedback: Feedback
    private lateinit var tabs: TabBar
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var preview: KeyGridView
    private lateinit var controlBar: ControlBar
    private var currentPage = 0
    private var focusableRows: List<SettingRow> = emptyList()

    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * dp).roundToInt()

    private class Section(val title: String, val rows: List<SettingRow>)
    private class Page(val title: String, val build: () -> List<Section>)

    private val pages: List<Page> by lazy {
        listOf(
            Page("Look", this::lookPage),
            Page("Typing", this::typingPage),
            Page("Sound & haptics", this::feedbackPage),
            Page("Clipboard", this::clipboardPage),
            Page("Button guide", this::guidePage),
            Page("About", this::aboutPage)
        )
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Black system bars on Android 14 and older (the Odin 2 Portal runs 13). Android 15+
        // ignores these and draws the app behind the bars, which is fine on a black screen.
        @Suppress("DEPRECATION")
        window.statusBarColor = SettingsStyle.background
        @Suppress("DEPRECATION")
        window.navigationBarColor = SettingsStyle.background
        Palette.init(this)
        settings = KeyboardSettings.load(this)
        SettingsStyle.accent = settings.accent
        // The volume buttons change the same volume the sounds play at (media),
        // so what you set here is exactly what you hear on the keyboard.
        volumeControlStream = AudioManager.STREAM_MUSIC
        feedback = Feedback(this).apply { update(settings) }
        SettingRow.buttonStyle = settings.buttonStyle
        SettingRow.sounds = feedback
        setupState = currentSetupState()
        setContentView(buildScreen().also { muteSystemSounds(it) })
        showPage(0, focusIndex = 0, animate = false)
        // First time ever: the one-minute tour (skippable). Settings is right behind it.
        if (!settings.tutorialDone && savedInstanceState == null) {
            startActivity(Intent(this, TutorialActivity::class.java))
        } else if (WhatsNew.shouldShow(settings) && savedInstanceState == null) {
            // First open after an update: the highlights, once
            startActivity(Intent(this, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true))
        }
        // A soft tick whenever the focus moves to another row (console-style)
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            if (old != null && new is SettingRow && !rebuilding) feedback.move()
        }
    }

    /**
     * Leaving with Home or Recents closes Settings completely, and it never shows up in
     * Recents (see the manifest). Some handhelds (the Odin 2 Portal among them) don't just
     * close an app you swipe away there, they force-stop it, and Android answers a
     * force-stopped keyboard by switching back to the default keyboard. With nothing to
     * swipe away, that can't happen.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finishAndRemoveTask()
    }

    /**
     * Screens Settings opens itself (Android's keyboard list, GitHub, Obtainium) are marked
     * as "not the user leaving", so opening them doesn't close Settings behind them.
     */
    override fun startActivity(intent: Intent, options: Bundle?) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION), options)
    }

    override fun onDestroy() {
        panelAnim?.cancel()
        if (SettingRow.sounds === feedback) SettingRow.sounds = null     // a newer Settings may own it now
        feedback.release()
        super.onDestroy()
    }

    /** Save, then show the change on the preview straight away. */
    private fun update(changed: KeyboardSettings) {
        val styleChanged = changed.buttonStyle != settings.buttonStyle
        val accentChanged = changed.accent != settings.accent
        // Picking or leaving the Clear theme shows or hides its See-through slider
        val clearChanged = changed.tone.clear != settings.tone.clear
        settings = changed
        if (accentChanged) {
            SettingsStyle.accent = settings.accent
            invalidateAll(window.decorView)
        }
        settings.save(this)
        feedback.update(settings)
        preview.applySettings(settings)
        // Show TV mode on the preview when it's set to On, with a sample line of text
        preview.setTvMode(settings.tvMode == TvMode.ON)
        preview.setPreview("See you at 8", "", "")
        if (clearChanged && !styleChanged) {
            val focused = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
            showPage(currentPage, focusIndex = focused, animate = false)
        }
        if (styleChanged) {
            // Button icons appear all over Settings: redraw them in the new style
            SettingRow.buttonStyle = settings.buttonStyle
            tabs.style = settings.buttonStyle
            refreshControlBar()
            val focused = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
            showPage(currentPage, focusIndex = focused, animate = false)
        }
    }

    /** Redraws every view, so a new accent colour shows everywhere at once. */
    private fun invalidateAll(view: View) {
        view.invalidate()
        if (view is ViewGroup) for (i in 0 until view.childCount) invalidateAll(view.getChildAt(i))
    }

    // =====================================================================
    // SET-UP: is Inlay switched on, and is it the keyboard in use?
    // =====================================================================
    private fun inlayEnabled(): Boolean = runCatching {
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == packageName }
    }.getOrDefault(true)

    private fun inlayActive(): Boolean = runCatching {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
    }.getOrDefault(true)

    private var setupState = 0          // 0 = needs turning on, 1 = needs choosing, 2 = ready

    private fun currentSetupState() = when {
        !inlayEnabled() -> 0
        !inlayActive() -> 1
        else -> 2
    }

    /** Coming back from Android's keyboard list or picker: update the card (or let it go). */
    private fun refreshSetup() {
        val now = currentSetupState()
        if (now == setupState) return
        val finished = now == 2
        setupState = now
        if (currentPage == 0) showPage(0, focusIndex = 0, animate = false)
        if (finished) feedback.lock()          // a small "all set" moment
    }

    override fun onResume() {
        super.onResume()
        // The tour saves "seen" itself; take that on board so our next save keeps it
        val saved = KeyboardSettings.load(this)
        val changedElsewhere = saved.sizeLevel != settings.sizeLevel || saved.activeLanguage != settings.activeLanguage
        settings = settings.copy(tutorialDone = saved.tutorialDone, whatsNewSeen = saved.whatsNewSeen, sizeLevel = saved.sizeLevel,
            activeLanguage = saved.activeLanguage)
        if (changedElsewhere) {
            // L3 or RT + LT was pressed on the keyboard meanwhile: show it here too
            preview.applySettings(settings)
            if (currentPage == 1) {
                val focused = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
                showPage(currentPage, focusIndex = focused, animate = false)
            }
        }
        refreshSetup()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshSetup()                     // the keyboard picker is a pop-up, so there's no onResume
            scroll.postDelayed({ refreshSetup() }, 300)    // Android can take a moment to note the new keyboard
        }
    }

    private fun setupSection(): Section? {
        if (setupState == 2) return null
        val enabled = setupState >= 1
        return Section("Get started", listOf(
            SetupRow(this, enabled) {
                if (!enabled) startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                else getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            }
        ))
    }

    // =====================================================================
    // SCREEN
    // =====================================================================
    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SettingsStyle.background)
        }

        tabs = TabBar(this, pages.map { it.title }) { index ->
            slideFrom = if (index > currentPage) 1 else -1
            switchPage(index)
        }.apply {
            style = settings.buttonStyle
        }
        root.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Content column: centred, never wider than a comfortable reading width
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, px(Design.SPACE_XL))
        }
        val sidePadding = px(Design.SPACE_XL)
        val contentWidth = minOf(resources.displayMetrics.widthPixels - 2 * sidePadding, px(MAX_CONTENT_WIDTH))
        val column = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(contentWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
            setPadding(sidePadding, 0, sidePadding, 0)
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            // Content softly fades out at the top and bottom edges instead of being cut off
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(px(Design.SPACE_XL))
            addView(column)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        preview = KeyGridView(this).apply {
            isFocusable = false
            feedback = this@SettingsActivity.feedback
            applySettings(settings)
            visibility = if (settings.showPreview) View.VISIBLE else View.GONE
        }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        controlBar = ControlBar(this)
        root.addView(controlBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refreshControlBar()
        return root
    }

    private fun refreshControlBar() {
        controlBar.show(
            settings.buttonStyle,
            listOf(
                "{L1} {R1} Sections",
                "{A} Select",
                if (settings.showPreview) "{Y} Hide keyboard" else "{Y} Show keyboard",
                "{B} Close"
            )
        )
    }

    private fun switchPage(index: Int) {
        if (index == currentPage) return
        // A page change is a bigger step than moving between rows: the pack's
        // space-bar sound, lower and fuller than the navigation tick.
        feedback.space()
        showPage(index, focusIndex = 0, animate = true)
    }

    private var rebuilding = false
    private var slideFrom = 1

    private fun showPage(index: Int, focusIndex: Int, animate: Boolean) {
        rebuilding = true
        currentPage = index
        tabs.select(index)
        content.removeAllViews()

        val rows = mutableListOf<SettingRow>()
        val sections = pages[index].build().let { if (index == 0) listOfNotNull(setupSection()) + it else it }
        sections.forEach { section ->
            content.addView(SectionHeader(this, section.title))
            val card = SettingsCard(this)
            section.rows.forEach { row ->
                row.id = View.generateViewId()
                card.addRow(row)
                rows += row
            }
            content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Up on the first row / Down on the last stays put instead of leaving the page
        rows.firstOrNull()?.let { it.nextFocusUpId = it.id }
        rows.lastOrNull()?.let { it.nextFocusDownId = it.id }
        focusableRows = rows

        if (animate) scroll.scrollTo(0, 0)
        muteSystemSounds(content)
        rows.getOrNull(focusIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0)))?.requestFocus()
        rebuilding = false

        if (animate) {
            content.alpha = 0f
            // The new page slides in from the side you moved towards
            content.translationX = px(12f * slideFrom).toFloat()
            content.animate().alpha(1f).translationX(0f).setDuration(Design.MOTION_PAGE)
                .setInterpolator(Design.EASE_OUT).start()
        }
    }

    // =====================================================================
    // KEYBOARD PREVIEW: slides up from the bottom and pushes the page up with it,
    // keeping the row you're on in view (like a real keyboard appearing).
    // =====================================================================
    private var panelAnim: ValueAnimator? = null

    private fun togglePreview() {
        val show = !settings.showPreview
        settings = settings.copy(showPreview = show)
        settings.save(this)
        refreshControlBar()
        if (show) feedback.on() else feedback.off()

        val fullHeight = measuredPreviewHeight()
        val from = if (preview.visibility == View.VISIBLE) preview.height else 0
        val to = if (show) fullHeight else 0
        preview.visibility = View.VISIBLE

        panelAnim?.cancel()
        panelAnim = ValueAnimator.ofInt(from, to).apply {
            duration = Design.MOTION_PANEL
            interpolator = Design.EASE_STANDARD
            addUpdateListener {
                val h = it.animatedValue as Int
                setPreviewHeight(h)
                keepFocusVisible(panelHeight = h)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (panelAnim !== animation) return
                    setPreviewHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                    preview.visibility = if (settings.showPreview) View.VISIBLE else View.GONE
                    scroll.post { keepFocusVisible(panelHeight = if (settings.showPreview) preview.height else 0) }
                }
            })
            start()
        }
    }

    private fun measuredPreviewHeight(): Int {
        preview.measure(
            View.MeasureSpec.makeMeasureSpec(scroll.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return preview.measuredHeight
    }

    private fun setPreviewHeight(height: Int) {
        preview.layoutParams = preview.layoutParams.apply { this.height = height }
    }

    /**
     * Scrolls just enough that the focused row sits fully inside the visible area.
     * [panelHeight] is the preview's height this frame, so the page moves in step
     * with the panel instead of one frame behind it.
     */
    private fun keepFocusVisible(panelHeight: Int) {
        val focused = currentFocus ?: return
        val child = scroll.getChildAt(0) ?: return
        val rect = Rect()
        focused.getDrawingRect(rect)
        runCatching { (child as ViewGroup).offsetDescendantRectToMyCoords(focused, rect) }.onFailure { return }
        val margin = px(Design.SPACE_XL)
        val screen = scroll.parent as View
        val visibleHeight = screen.height - tabs.height - controlBar.height - panelHeight
        val top = scroll.scrollY
        val bottom = top + visibleHeight
        val target = when {
            rect.bottom + margin > bottom -> rect.bottom + margin - visibleHeight
            rect.top - margin < top -> rect.top - margin
            else -> return
        }
        scroll.scrollTo(0, target.coerceAtLeast(0))
    }

    // =====================================================================
    // CONTROLLER
    // =====================================================================
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val first = event.repeatCount == 0
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> if (first) { slideFrom = -1; switchPage((currentPage - 1 + pages.size) % pages.size) }
            KeyEvent.KEYCODE_BUTTON_R1 -> if (first) { slideFrom = 1; switchPage((currentPage + 1) % pages.size) }
            KeyEvent.KEYCODE_BUTTON_Y -> if (first) togglePreview()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (first) close()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private var closing = false

    /** Play the pack's "back" sound, then close once it has had time to ring out. */
    private fun close() {
        if (closing) return
        closing = true
        feedback.back()
        scroll.postDelayed({ finish() }, CLOSE_DELAY_MS)
    }

    /** Settings speaks with the keyboard's sound pack only, never Android's own clicks. */
    private fun muteSystemSounds(view: View) {
        view.isSoundEffectsEnabled = false
        if (view is ViewGroup) for (i in 0 until view.childCount) muteSystemSounds(view.getChildAt(i))
    }

    // =====================================================================
    // PAGES
    // =====================================================================
    private fun lookPage(): List<Section> {
        lateinit var swatches: SwatchRow
        lateinit var custom: HueRow
        swatches = SwatchRow(this, "Highlight colour", "The glow that follows your selection, also used all over Settings",
            Palette.accents.map { it.second }, settings.accentIndex) {
            custom.setActive(false)
            update(settings.copy(accentIndex = it))
        }
        custom = HueRow(this, "Custom colour", "Any colour you like. Left and right move around the colour wheel",
            settings.customHue, active = settings.accentIndex < 0) {
            swatches.clearSelection()
            update(settings.copy(accentIndex = -1, customHue = it))
        }
        return listOf(
        Section("Theme", listOf(
            StepperRow(this, "Theme", "Dark themes suit OLED screens. Clear lets the app behind show through",
                Palette.tones.map { it.name }, settings.toneIndex.coerceIn(0, Palette.tones.lastIndex)) {
                update(settings.copy(toneIndex = it))
            }
        ) + listOfNotNull(
            if (settings.tone.clear) SliderRow(this, "See-through", "How much of the app behind shows. Lower is easier to read",
                0, 10, settings.seeThrough, { it.toString() }) {
                update(settings.copy(seeThrough = it))
            } else null
        )),
        Section("Colour", listOf(swatches, custom)),
        Section("Style", listOf(
            ChoiceRow(this, "Button icons", "Match the controller you're holding",
                ButtonStyle.values().map { it.label }, settings.buttonStyle.ordinal) {
                update(settings.copy(buttonStyle = ButtonStyle.values()[it]))
            },
            ChoiceRow(this, "Key shape", "How rounded the keys are",
                KeyShape.values().map { it.label }, settings.keyShape.ordinal) {
                update(settings.copy(keyShape = KeyShape.values()[it]))
            },
            ChoiceRow(this, "Highlight style", "How the selected key is marked. Solid is the simplest",
                HighlightStyle.values().map { it.label }, settings.highlightStyle.ordinal) {
                update(settings.copy(highlightStyle = HighlightStyle.values()[it]))
            },
            ChoiceRow(this, "Highlight motion", "Fluid leans slightly into each move. Instant has no animation",
                HighlightMotion.values().map { it.label }, settings.motion.ordinal) {
                update(settings.copy(motion = HighlightMotion.values()[it]))
            }
        )),
        Section("Letters", listOf(
            ChoiceRow(this, "Font", "Hyperlegible is designed to be the easiest to read",
                FontChoice.values().map { it.label }, settings.font.ordinal) {
                update(settings.copy(font = FontChoice.values()[it]))
            },
            SliderRow(this, "Letter weight", "How bold the letters are", 0, 10, settings.letterWeight,
                { it.toString() }) {
                update(settings.copy(letterWeight = it))
            }
        ))
        )
    }

    /** Language pickers for the quick switch: "None" first, then every language. */
    private fun extraLanguageOptions() = listOf("None") + Language.values().map { it.label }
    private fun extraIndex(l: Language?) = l?.let { it.ordinal + 1 } ?: 0
    private fun extraFrom(index: Int): Language? = if (index == 0) null else Language.values()[index - 1]

    private fun typingPage() = listOf(
        Section("Languages", listOf(
            StepperRow(this, "Language", "Italian, Dutch, Polish and more type on English",
                Language.values().map { it.label }, settings.language1.ordinal) {
                update(settings.copy(language1 = Language.values()[it], activeLanguage = 0))
            },
            StepperRow(this, "Second language", "Hold {R2} and press {L2} to switch while typing",
                extraLanguageOptions(), extraIndex(settings.language2)) {
                update(settings.copy(language2 = extraFrom(it), activeLanguage = 0))
            },
            StepperRow(this, "Third language", "Optional. The switch goes round all of them in order",
                extraLanguageOptions(), extraIndex(settings.language3)) {
                update(settings.copy(language3 = extraFrom(it), activeLanguage = 0))
            },
            ToggleRow(this, "Language on the space bar", "Off keeps the space bar clean. A switch still shows it for a moment",
                settings.showLanguageLabel) {
                update(settings.copy(showLanguageLabel = it))
            }
        )),
        Section("Keys", listOf(
            ChoiceRow(this, "Key layout", "Grid lines keys up in straight columns, so up and down never guess",
                KeyLayout.values().map { it.label }, settings.layout.ordinal) {
                update(settings.copy(layout = KeyLayout.values()[it]))
            },
            ToggleRow(this, "Smart keys", "Adds / and .com for web addresses, @ for email, and a number pad for numbers",
                settings.smartKeys) {
                update(settings.copy(smartKeys = it))
            },
            ChoiceRow(this, "Keyboard size", "Also on the keyboard: press {L3}",
                listOf("Normal", "Large", "Largest"), settings.sizeLevel.coerceIn(0, 2)) {
                update(settings.copy(sizeLevel = it))
            },
            ChoiceRow(this, "TV mode", "A compact floating keyboard with a big line showing what you type. Automatic: when docked to a TV, or on Android TV",
                TvMode.values().map { it.label }, settings.tvMode.ordinal) {
                update(settings.copy(tvMode = TvMode.values()[it]))
            }
        )),
        Section("Navigation", listOf(
            SliderRow(this, "Stick speed", "How fast the highlight moves while you hold the left stick all the way. A lighter push goes more gently",
                0, 4, settings.stickSpeed, { STICK_LABELS[it] }) {
                update(settings.copy(stickSpeed = it))
            },
            ToggleRow(this, "Right stick moves the text cursor", "Push {R3} sideways to move, further is faster. Up and down go a line, or to the start or end. Grows a selection too",
                settings.rightStick) {
                update(settings.copy(rightStick = it))
            }
        )),
        Section("Shortcuts", listOf(
            ToggleRow(this, "Select all key", "When off, double-press {SELECTx2} instead",
                settings.selectAllButton) {
                update(settings.copy(selectAllButton = it))
            },
            ToggleRow(this, "Double space for full stop", "Press {Y} twice after a word to type \". \"",
                settings.doubleSpacePeriod) {
                update(settings.copy(doubleSpacePeriod = it))
            },
            ToggleRow(this, "Undo shortcut", "Hold {R2} and press {X}",
                settings.undoCombo) {
                update(settings.copy(undoCombo = it))
            }
        )),
        Section("Hints", listOf(
            ToggleRow(this, "Button icons on keys", "Small {X} {Y} style badges in the key corners",
                settings.showHints) {
                update(settings.copy(showHints = it))
            },
            ToggleRow(this, "Combo tips", "Shows what you can do while holding {R2}",
                settings.comboHints) {
                update(settings.copy(comboHints = it))
            }
        ))
    )

    private fun feedbackPage() = listOf(
        Section("Vibration", listOf(
            ToggleRow(this, "Vibration", null, settings.vibration) {
                update(settings.copy(vibration = it))
            },
            SliderRow(this, "When moving", "A light tick for each key you pass",
                0, 10, settings.moveStrength, this::offOr, silent = true) {
                update(settings.copy(moveStrength = it))
                feedback.move()
            },
            SliderRow(this, "When pressing", "A firmer tap when you type",
                0, 10, settings.pressStrength, this::offOr, silent = true) {
                update(settings.copy(pressStrength = it))
                feedback.key()
            },
            ActionRow(this, "Try it", "A key press, a trigger and caps lock") {
                playSequence(listOf(feedback::key, feedback::on, feedback::lock))
            }
        )),
        Section("Sound", listOf(
            ActionRow(this, "Sound test", "Plays the keyboard's real sounds at their real volume, in order: " +
                "move, type, space, delete, trigger on, trigger off, caps lock, select all, enter") {
                playSequence(listOf(
                    feedback::move, feedback::key, feedback::space, feedback::delete, feedback::on,
                    feedback::off, feedback::lock, feedback::selectAll, feedback::confirm
                ))
            },
            StepperRow(this, "Sound pack", null,
                SoundPack.values().map { it.label }, settings.soundPack.ordinal,
                descriptions = SoundPack.values().map { it.description }, silent = true) {
                update(settings.copy(soundPack = SoundPack.values()[it]))
                // A short phrase in the new pack: two letters, space, a letter, Enter
                feedback.sample()
            },
            SliderRow(this, "Volume", "Follows your media volume, so turn that up too",
                0, 10, settings.soundVolume, this::offOr, silent = true) {
                update(settings.copy(soundVolume = it))
                feedback.key()
            },
            ToggleRow(this, "Navigation ticks", "A soft tick as the highlight moves",
                settings.moveSounds) {
                update(settings.copy(moveSounds = it))
                feedback.move()
            },
            ToggleRow(this, "Play in silent mode", "When off, sounds mute with your silent mode",
                settings.soundInSilent) {
                update(settings.copy(soundInSilent = it))
            }
        ))
    )

    private fun clipboardPage() = listOf(
        Section("History", listOf(
            ToggleRow(this, "Remember copied text",
                "Your last ${Layouts.MAX_CLIP_CARDS} copies, on this device only. Passwords are never kept.",
                settings.saveClipHistory) {
                update(settings.copy(saveClipHistory = it))
            },
            UndoActionRow(this, "Clear clipboard history", "Removes your saved copies from this device",
                "Clipboard history cleared", "Changed your mind? Press it again to bring it back") {
                ClipboardHistory.clearWithUndo(this)
            }
        ))
    )

    /** Every button and combo, kept in step with the features that are switched on. */
    private fun guidePage(): List<Section> {
        val typing = mutableListOf(
            GuideRow(this, "Type the highlighted key", "{A}"),
            GuideRow(this, "Delete · hold to speed up", "{X}"),
            GuideRow(this, "Space", "{Y}"),
            GuideRow(this, "Shift · tap twice for caps lock", "{R2}"),
            GuideRow(this, "One capital letter", "{R2} + {A}"),
            GuideRow(this, "Accent the last letter · press again for more", "{R2} + {Y}"),
            GuideRow(this, if (settings.smartKeys) "Symbols · in number boxes, letters" else "Letters and symbols", "{L2}"),
            GuideRow(this, "Enter · Search · Go", "{START}")
        )
        if (settings.languages.size > 1) {
            typing += GuideRow(this, "Switch language (${settings.languages.joinToString(" → ") { it.label }})", "{R2} + {L2}")
        }
        val editing = mutableListOf(
            GuideRow(this, "Move the text cursor", "{L1} {R1}"),
            GuideRow(this, "Jump a whole word", "{R2} + {L1} {R1}"),
            GuideRow(this, "Select text", "{SELECT}"),
            GuideRow(this, "Select everything", "{SELECTx2}")
        )
        if (settings.rightStick) {
            editing.add(1, GuideRow(this, "Move the text cursor · push further to go faster", "{R3}"))
            editing.add(3, GuideRow(this, "Jump a whole word with the stick", "{R2} + {R3}"))
            editing += GuideRow(this, "Go to the start or end · a line in long text", "{R3} up or down")
        }
        if (settings.undoCombo) editing += GuideRow(this, "Undo", "{R2} + {X}")
        return listOf(
            Section("Typing", typing),
            Section("Editing", editing),
            Section("Keyboard", listOf(
                GuideRow(this, "Close the keyboard · leave select mode or the clipboard", "{B}"),
                GuideRow(this, "Keyboard size · Normal, Large, Largest", "{L3}")
            ))
        )
    }

    private fun aboutPage() = listOf(
        Section("Setup", listOf(
            ActionRow(this, "Turn on Inlay", "Opens Android's list of keyboards") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            },
            ActionRow(this, "Switch keyboard", "Choose which keyboard is active right now") {
                getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            },
            ActionRow(this, "Replay the tour", "The one-minute walk through the buttons and combos") {
                startActivity(Intent(this, TutorialActivity::class.java))
            },
            ActionRow(this, "What's new in ${WhatsNew.VERSION}", "The highlights of this update, acted out") {
                startActivity(Intent(this, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true))
            },
            ActionRow(this, "Full changelog", "Every change in every version, on GitHub") {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$SOURCE_URL/blob/main/CHANGELOG.md"))) }
            }
        )),
        Section("Inlay", listOfNotNull(
            InfoRow(this, "Version", appVersion()),
            InfoRow(this, "Licence", "Free and open source"),
            SOURCE_URL.takeIf { it.isNotEmpty() }?.let { url ->
                ActionRow(this, "Source code", "See how it's made, report a problem or suggest an idea") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                }
            },
            InfoRow(this, "Fonts: Atkinson Hyperlegible Next and Inter", "Open Font Licence")
        )),
        Section("Updates", listOf(
            ActionRow(this, "Get updates automatically",
                "Adds Inlay to Obtainium, a free app that installs new versions from GitHub") {
                addToObtainium()
            }
        ))
    )

    /**
     * Opens Obtainium (or a fork that understands its links) with Inlay ready to add.
     * Without it, opens the releases page so the update can still be downloaded by hand.
     */
    private fun addToObtainium() {
        val add = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("obtainium://add/$SOURCE_URL"))
        val opened = runCatching { startActivity(add) }.isSuccess
        if (!opened) {
            Toast.makeText(this, "Obtainium isn't installed, so here's the download page instead", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$SOURCE_URL/releases"))) }
        }
    }

    /** The version number set in the app's build file, so it's only ever written in one place. */
    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"

    /** Plays sounds one after another with a calm gap, starting after the tap sound. */
    private fun playSequence(cues: List<() -> Unit>) {
        cues.forEachIndexed { i, cue -> scroll.postDelayed({ cue() }, 350L + i * 450L) }
    }

    private fun offOr(value: Int): String = if (value == 0) "Off" else value.toString()

    private companion object {
        const val MAX_CONTENT_WIDTH = 720f
        const val CLOSE_DELAY_MS = 140L
        /** The project's web page (e.g. on GitHub). Leave empty to hide the "Source code" row. */
        const val SOURCE_URL = "https://github.com/dakingeman/inlay"
        val STICK_LABELS = listOf("Slowest", "Slow", "Medium", "Fast", "Fastest")
    }
}
