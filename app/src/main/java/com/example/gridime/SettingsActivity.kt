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
    /** Back up and restore (1.3): Android's file picker hands back where to write, or what to read. */
    @Deprecated("Activity results, the plain way: SettingsActivity is a plain Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_BACKUP -> Toast.makeText(this, if (Backup.write(this, uri)) "Backed up" else "Couldn't save the backup there", Toast.LENGTH_SHORT).show()
            REQUEST_RESTORE -> when (Backup.read(this, uri)) {
                Backup.Result.RESTORED -> { Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show(); recreate() }
                Backup.Result.NOT_A_BACKUP -> Toast.makeText(this, "That file isn't an Inlay backup", Toast.LENGTH_LONG).show()
                Backup.Result.UNREADABLE -> Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_LONG).show()
            }
        }
    }


    private lateinit var settings: KeyboardSettings
    private lateinit var feedback: Feedback
    private lateinit var tabs: TabBar
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var preview: KeyGridView
    /** What the picked device can press (see [Controls]): rows, hints and the guide follow it. */
    private val controls get() = Controls.of(this, settings)
    private lateinit var sheet: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomFade: View
    private lateinit var dock: FrameLayout
    private lateinit var findPanel: FindPanel
    private lateinit var controlBar: ControlBar
    // NEW: the Button guide's device picture. Beside the page on wide screens, above it on narrow ones.
    private lateinit var guidePicture: DevicePictureView
    // Try your buttons, full screen: a second picture drawn at full size (never the small one stretched)
    private lateinit var fullOverlay: FrameLayout
    private lateinit var fullBackdrop: View
    private lateinit var fullPicture: DevicePictureView
    /** The picture and its caption as one group, centred on the screen (the caption never sits on the picture). */
    private lateinit var fullColumn: LinearLayout
    private var fullShown = false
    private var keyboardWasUp = false
    // The Konami Code's reward (on the device's screen, or lifted out of it)
    private lateinit var secretScreen: SecretScreen
    private val lookIndex get() = pages.indexOfFirst { it.title == "Look" }
    private val konami by lazy { KonamiCode(this) { secretFound() }.also { k -> k.buzz = { settings.vibration }
        k.loudness = {
            val am = getSystemService(android.media.AudioManager::class.java)
            val silent = am != null && am.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL
            if (silent && !settings.soundInSilent) 0f else settings.soundVolume / 10f
        }
        if (KonamiCode.hasLab(this)) k.onProblem = { controlBar.show(settings.shownButtonStyle, listOf(it.take(120))) } } }
    private lateinit var picturePanel: LinearLayout
    private lateinit var pictureBlock: LinearLayout
    private lateinit var pictureStage: FrameLayout
    /** Beside the page: the picture stands on a fixed-height stage (see pictureStage). */
    private var pictureOnStage = false
    private lateinit var pictureTitle: android.widget.TextView
    private lateinit var pictureCaption: android.widget.TextView
    /** Try your buttons, live: what the last button does, under the picture (and at the bottom in full screen). */
    private lateinit var liveCaption: LiveCaption
    private lateinit var fullCaption: LiveCaption
    private val guideIndex get() = pages.indexOfFirst { it.title == "Button guide" }
    private val deviceIndex get() = pages.indexOfFirst { it.title == "Device" }
    /** Landscape with room for the page and the picture side by side. */
    /** The picture's column beside the page: narrower on small screens, so the rows keep room. */
    private val pictureColumnDp get() = minOf(PICTURE_PANEL_DP * TourScale.of(resources.configuration), resources.configuration.screenWidthDp * 0.36f)
    private val wideScreen get() = resources.configuration.screenWidthDp >= SIDE_PICTURE_MIN_DP &&
        resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private var currentPage = 0
    private var focusableRows: List<SettingRow> = emptyList()
    /** Where each group on the page starts, as an index into [focusableRows] (LT / RT jump between them). */
    private var sectionStarts: List<Int> = emptyList()

    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * dp).roundToInt()

    private class Section(val title: String, val rows: List<SettingRow>)
    private class Page(val title: String, val build: () -> List<Section>)

    private val pages: List<Page> by lazy {
        listOf(
            Page("Device", this::devicePage),
            Page("Look", this::lookPage),
            Page("Typing", this::typingPage),
            Page("Sound & haptics", this::feedbackPage),
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
        SettingsStyle.init(this)
        settings = KeyboardSettings.load(this).copy(showPreview = false)    // opens with the keyboard put away
        SettingsStyle.accent = settings.activeAccent
        // The volume buttons change the same volume the sounds play at (media),
        // so what you set here is exactly what you hear on the keyboard.
        volumeControlStream = AudioManager.STREAM_MUSIC
        feedback = Feedback(this).apply { update(settings) }
        SettingRow.buttonStyle = settings.shownButtonStyle
        SettingRow.sounds = feedback
        // Updating from before 1.3 (Emre, 8 Nov): the button setup is new, and a Swap A/B and X/Y choice simply carried
        // over, so people who wanted their own buttons may never hear of it. The Your buttons row (on Device, the page
        // Settings opens on) carries the spark and says so until it's visited. Controllers only: a remote has no setup.
        if (savedInstanceState == null && WhatsNew.shouldShow(settings) && WhatsNew.isBefore(settings.whatsNewSeen, 1, 3) && !controls.remote) {
            settings = settings.copy(buttonsRowNew = true); KeyboardSettings.load(this).copy(buttonsRowNew = true).save(this)
        }
        setupState = currentSetupState()
        setContentView(buildScreen().also { muteSystemSounds(it); SettingsStyle.useFont(it) })
        showPage(0, focusIndex = 0, animate = false)
        intent?.getStringExtra(EXTRA_GO_TO)?.let { goToNamed(it) }
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
            // Button guide: the picture lights the buttons of the row you're on
            val hint = (new as? GuideRow)?.buttons
            guidePicture.held = hint?.let { DevicePicture.keysIn(it) } ?: emptySet()
            guidePicture.holding = hint?.let { DevicePicture.holdKeysIn(it) } ?: emptySet()
            guidePicture.stickSway = hint?.let { DevicePicture.swayIn(it) } ?: emptyMap()
            // Keyboard up: the row you moved to glides to the middle of the space above it
            if (new is SettingRow && settings.showPreview && !sliding && !rebuilding) scroll.post { placeFocused(animate = true) }
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
    /** The same for screens that hand something back: the file picker for Back up and Restore. Without the mark,
     *  opening the picker counted as leaving, so Settings closed and took the picker with it (Emre, 7 Nov). */
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION), requestCode, options)
    }


    override fun onDestroy() {
        sheetAnim?.cancel()
        if (SettingRow.sounds === feedback) SettingRow.sounds = null     // a newer Settings may own it now
        feedback.release()
        super.onDestroy()
    }

    /**
     * What the keyboard here shows. While finding, it types in English, whatever your languages: the settings are
     * named in English, so that's what Find can match (Emre, 6 Nov). Only the keyboard here: nothing is saved, and
     * your languages are back the moment Find closes.
     */
    private fun previewSettings(): KeyboardSettings = if (!finding) settings
        else settings.copy(language1 = Language.ENGLISH, language2 = null, language3 = null, activeLanguage = 0)

    /** Save, then show the change on the preview straight away. */
    private fun update(changed: KeyboardSettings) {
        // The typing button moving right redraws every {A} and {B} too (they show the button you really press)
        val styleChanged = changed.shownButtonStyle != settings.shownButtonStyle ||
            changed.face != settings.face
        val accentChanged = changed.activeAccent != settings.activeAccent   // a retro theme's own colour counts too
        // Picking or leaving the Clear theme shows or hides its See-through slider
        val clearChanged = changed.tone.clear != settings.tone.clear
        // Picking the remote (or leaving it) changes the Button guide's sections
        val pictureChanged = changed.picture != settings.picture || changed.verticalSticks != settings.verticalSticks
        // A second language brings "Remember each app's language" (and one alone takes it away), straight away
        val languagesChanged = (changed.languages.size >= 2) != (settings.languages.size >= 2)
        settings = changed
        ButtonRoles.apply(settings)
        if (accentChanged) {
            SettingsStyle.accent = settings.activeAccent
            invalidateAll(window.decorView)
        }
        settings.save(this)
        feedback.update(settings)
        preview.applySettings(previewSettings().forControls(controls))
        refreshGuidePicture()
        // Show TV mode on the preview when it's set to On, with a sample line of text
        preview.setTvMode(settings.tvMode == TvMode.ON)
        preview.setPreview("ocarina of time", "", "")
        if (pictureChanged) { tabs.remote = controls.remote; tabs.findKey = !controls.remote; refreshControlBar() }
        if ((clearChanged || pictureChanged || languagesChanged) && !styleChanged) {
            val focused = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
            showPage(currentPage, focusIndex = focused, animate = false)
        }
        if (styleChanged) {
            // Button icons appear all over Settings: redraw them in the new style
            SettingRow.buttonStyle = settings.shownButtonStyle
            tabs.style = settings.shownButtonStyle
            refreshControlBar()
            val focused = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
            showPage(currentPage, focusIndex = focused, animate = false)
        }
    }

    // ---- Your buttons (1.3): the button setup (ButtonSetup.kt) ---------------------------------------------------
    // One row: a small picture of what each button does now, and A to set up again. The setup covers the whole page:
    // left, top, right, bottom, then Select and Close. Hold Start leaves it; nothing changes until it finishes.
    private var setup: ButtonSetupView? = null
    private var setupDevice = -1
    internal val setupForCheck get() = setup

    private fun yourButtonsRow(): SettingRow {
        val f = settings.face
        val newOne = lastFaceDevice >= 0 && ButtonRoles.isNewController(lastFaceDevice)
        val hint = if (newOne) "A new controller: show Inlay its buttons" else f.summary() + ". Press {A} to set up again"
        val told = settings.buttonsRowNew && !newOne
        return ActionRow(this, ButtonRoles.ROW, if (told) "New: show Inlay your four face buttons and pick which one types" else hint) { startSetup() }.apply {
            if (newOne || told) newMark = true
            if (told) onFound = { newMark = false; settings = settings.copy(buttonsRowNew = false); settings.save(this@SettingsActivity) }
        }
    }

    /** The last controller a face button came from (so a new one can be offered its own setup). */
    private var lastFaceDevice = -1

    private fun startSetup() {
        if (setup != null || controls.remote) return
        keyboardWasUp = settings.showPreview
        if (keyboardWasUp) togglePreview()
        val v = ButtonSetupView(this, settings.face).apply { accent = settings.activeAccent }
        v.onDone = { result -> finishSetup(result) }
        setup = v
        // The whole screen, over the bottom bar too (its Select and Close would only confuse while you choose them)
        (findViewById<View>(android.R.id.content) as ViewGroup).addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        v.alpha = 0f; v.animate().alpha(1f).setDuration(Design.MOTION_FADE).start()
        refreshControlBar()
    }

    private fun finishSetup(result: FaceSetup?) {
        val v = setup ?: return
        setup = null
        if (result != null) { update(settings.withFace(result, ButtonRoles.descriptor(setupDevice)).copy(buttonsRowNew = false)); feedback.confirm() } else feedback.back()
        v.animate().alpha(0f).setDuration(Design.MOTION_FADE).withEndAction { (v.parent as? ViewGroup)?.removeView(v) }.start()
        val bringBack = keyboardWasUp; keyboardWasUp = false
        if (bringBack && !settings.showPreview) togglePreview()
        showRowAgain(ButtonRoles.ROW, flash = result != null)
        refreshControlBar()
    }

    /** A press while the setup shows: raw, as the device sent it. Hold Start leaves (never a face button: they're answers). */
    private fun setupKey(raw: KeyEvent): Boolean {
        val v = setup ?: return false
        if (raw.keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            if (raw.action == KeyEvent.ACTION_DOWN && raw.repeatCount == 0) {
                scroll.postDelayed(leaveSetup, Design.SETUP_LEAVE_HOLD_MS)
            } else if (raw.action == KeyEvent.ACTION_UP) scroll.removeCallbacks(leaveSetup)
            return true
        }
        if (raw.action == KeyEvent.ACTION_DOWN && raw.repeatCount == 0) {
            if (FaceSetup.isFace(raw.keyCode)) setupDevice = raw.deviceId
            v.press(raw.keyCode)
            feedback.move()
        }
        return true
    }
    private val leaveSetup = Runnable { setup?.leave() }

    /** The page again with the highlight on [title] (its answer and sentence just changed). */
    private fun showRowAgain(title: String, flash: Boolean) {
        showPage(currentPage, focusIndex = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0), animate = false)
        val i = focusableRows.indexOfFirst { (it as? SettingRow)?.rowTitle == title }
        (focusableRows.getOrNull(i) as? SettingRow)?.let { row -> row.post { row.requestFocus(); if (flash) row.flash() } }
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
            preview.applySettings(previewSettings().forControls(controls))
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
            style = settings.shownButtonStyle
            id = View.generateViewId()
            remote = controls.remote
            onKeyboard = { togglePreview() }
            onDown = { focusableRows.firstOrNull()?.requestFocus() }
            findKey = !controls.remote
            onFind = { openFind() }
            // While the tabs have the focus they stay in view, even with the keyboard up
            setOnFocusChangeListener { _, _ -> if (sheetAnim == null) applySheet(sheetProgress) }
        }
        tabs.setBackgroundColor(SettingsStyle.background)       // it lies over the page's top, which scrolls under it
        // new things not seen yet: Look's themes, Sound & haptics' theme sounds
        tabs.marks = setOfNotNull(lookIndex.takeIf { settings.secretNew }, soundIndex.takeIf { settings.secretSoundNew })

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
        scroll = object : ScrollView(this) {
            /**
             * Where a focused row scrolls to. With the keyboard preview open the visible area is
             * small, so the row you're on sits in the middle of it (like Android TV menus): its
             * words are never cut off or lost in the soft fade at the edges. Without the preview,
             * Android's usual "just enough" scrolling, which never makes the page jump.
             */
            override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
                // While the keyboard slides, togglePreview moves the page in step with it.
                // Android's own scroll would react a frame late and fight it (the jumping on quick presses).
                // With the keyboard up (or sliding), placeFocused decides where the row goes: one voice only
                if (sheetAnim != null || settings.showPreview || childCount == 0) return 0
                // A row that shows whole, below the tabs and clear of the soft fades, stays put. One that
                // would be cut off (even a little) brings the page round so it sits in the middle of the
                // view, whole, with room to see what's next. Never just part of a row.
                val edge = px(Design.SPACE_XL)
                val top = scrollY + paddingTop + edge
                val bottom = scrollY + height - edge
                if (rect.top >= top && rect.bottom <= bottom) return 0
                val room = bottom - top
                val delta = if (rect.height() > room) {
                    if (rect.top < top) rect.top - top else rect.bottom - bottom   // taller than the view: its nearer edge
                } else rect.centerY() - (top + bottom) / 2
                val maxScroll = ((getChildAt(0)?.height ?: 0) + paddingTop - height).coerceAtLeast(0)
                val wanted = (scrollY + delta).coerceIn(0, maxScroll)
                // Going up near the top, go all the way (the page ends exactly as it opened, headings in
                // view), as long as the row still shows whole there
                return if (delta < 0 && wanted < px(TOP_SNAP_DP) && rect.bottom <= height - edge) -scrollY else wanted - scrollY
            }
            // A smooth scroll still running from before the slide must not pull the page back
            override fun computeScroll() { if (sheetAnim == null) super.computeScroll() }
            // Where you are on the page: drawn by the page itself, so it moves in the same frame as the rows
            override fun dispatchDraw(canvas: android.graphics.Canvas) { super.dispatchDraw(canvas); drawPagePosition(this, canvas) }
            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) { super.onScrollChanged(l, t, oldl, oldt); if (t != oldt) pagePositionMoved(this) }
        }.apply {
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            // Content softly fades out at the top and bottom edges instead of being cut off
            // The soft edges are our own (under the tabs and at the bottom): Android's would draw a band
            // across the rows now that the page scrolls under the tabs
            isVerticalFadingEdgeEnabled = false
            addView(column)
        }
        guidePicture = DevicePictureView(this).apply {
            accent = settings.activeAccent
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Beside the page the picture stands on a stage of fixed height, sized for the tallest picture (the vertical
        // handheld) and the room left above the words. Every picture sits on the stage's floor, so switching between
        // them never moves the words under them or the block as a whole: only the drawing changes.
        pictureStage = FrameLayout(this).apply {
            addView(guidePicture, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
        pictureBlock = object : LinearLayout(this) {
            private var stageKey = ""
            private var belowMost = 0
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val w = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST)
                val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                var below = 0
                for (i in 1 until childCount) {
                    val c = getChildAt(i)
                    if (c.visibility == View.GONE) continue
                    c.measure(w, free)
                    below += c.measuredHeight + ((c.layoutParams as? LinearLayout.LayoutParams)?.topMargin ?: 0)
                }
                val lp = pictureStage.layoutParams
                if (pictureOnStage && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    // The words are measured first; the most they've needed at this size is kept, so a caption one
                    // line longer for another picture doesn't nudge the stage either
                    val key = "${MeasureSpec.getSize(widthMeasureSpec)}x${MeasureSpec.getSize(heightMeasureSpec)}"
                    if (key != stageKey) { stageKey = key; belowMost = 0 }
                    belowMost = maxOf(belowMost, below)
                    val tallest = (guidePicture.maxHeightDp * DevicePictureView.TALL_BOOST * resources.displayMetrics.density).toInt()
                    lp.height = minOf(tallest, MeasureSpec.getSize(heightMeasureSpec) - belowMost).coerceAtLeast(0)
                } else lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(pictureStage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // Under the picture: on the Device tab, what Inlay is set up for (plain words, nothing to press)
            pictureTitle = android.widget.TextView(context).apply {
                textSize = Design.TYPE_ROW
                typeface = SettingsStyle.medium
                setTextColor(SettingsStyle.textPrimary)
                gravity = Gravity.CENTER
                visibility = View.GONE
            }
            addView(pictureTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(Design.SPACE_M) })
            pictureCaption = android.widget.TextView(context).apply {
                text = "The picture lights up the row you're on"
                textSize = Design.TYPE_CAPTION
                setTextColor(SettingsStyle.textSecondary)
                gravity = Gravity.CENTER
                setLineSpacing(0f, Design.LINE_CAPTION)
            }
            addView(pictureCaption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(Design.SPACE_S) })
            liveCaption = LiveCaption(context).apply { visibility = View.GONE }
            addView(liveCaption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(Design.SPACE_M) })
        }
        picturePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, px(Design.SPACE_L), px(Design.SPACE_XL), px(Design.SPACE_L))
            visibility = View.GONE
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(picturePanel, LinearLayout.LayoutParams(px(pictureColumnDp), ViewGroup.LayoutParams.MATCH_PARENT))
        }
        refreshGuidePicture()

        preview = KeyGridView(this).apply {
            isFocusable = false
            clearsBehind = false                     // inside the page: never cut a hole through it to the empty window
            feedback = this@SettingsActivity.feedback
            applySettings(settings.forControls(controls))
        }
        // The keyboard panel: solid (so nothing shows through, even with the Clear theme),
        // with a soft fade on top where the page meets it
        val bg = SettingsStyle.background
        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bg and 0x00FFFFFF, bg))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(SHEET_FADE_DP)))
            addView(FrameLayout(context).apply {
                setBackgroundColor(bg)
                addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            visibility = if (settings.showPreview) View.VISIBLE else View.INVISIBLE
        }
        dock = FrameLayout(this).apply {
            clipChildren = true
            addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // The page's soft bottom edge, which hands over to the keyboard's own as it comes up
            bottomFade = View(context).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bg and 0x00FFFFFF, bg))
            }
            addView(bottomFade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(EDGE_FADE_DP), Gravity.BOTTOM))
            addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            // The tabs lie over the top of the page, so they can slide away while the keyboard is up.
            // A soft edge hangs under them and travels with them.
            topBar = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bg, bg and 0x00FFFFFF))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(EDGE_FADE_DP)))
            }
            addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            // Find covers the whole page and the tabs above the keyboard (its height is set as it opens)
            findPanel = FindPanel(context).apply { visibility = View.GONE }
            addView(findPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            fullBackdrop = View(context).apply { setBackgroundColor(SettingsStyle.background) }
            fullPicture = DevicePictureView(context).apply {
                maxHeightDp = 4000f; tallBoost = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            fullCaption = LiveCaption(context).apply { visibility = View.GONE; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
            // The picture, then its caption under it, centred together. The caption's slot is as tall as its longest
            // words (set as the test starts), so the picture never moves when the words change (Emre, 8 Nov: the words
            // sat on the bottom of the upright handhelds)
            fullColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(px(Design.SPACE_XL), px(Design.SPACE_L), px(Design.SPACE_XL), px(Design.SPACE_L))
                addView(fullPicture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(fullCaption, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = px(Design.SPACE_L) })
            }
            fullOverlay = FrameLayout(context).apply {
                visibility = View.GONE
                addView(fullBackdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(fullColumn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            addView(fullOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            secretScreen = SecretScreen(context).apply { visibility = View.INVISIBLE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            addView(secretScreen, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        scroll.clipToPadding = false
        tabs.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            // The page starts below the tabs, and scrolls up underneath them
            scroll.setPadding(0, bottom - top, 0, 0)
            picturePanel.setPadding(0, bottom - top + px(Design.SPACE_L), px(Design.SPACE_XL), px(Design.SPACE_L))
            applySheet(sheetProgress)
        }
        root.addView(dock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        sheetProgress = if (settings.showPreview) 1f else 0f
        // Whenever the panel's size changes (first layout, number row on or off), keep it and the page's room in step
        sheet.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop || sheetAnim != null) return@addOnLayoutChangeListener
            applySheet(sheetProgress)
            setPageRoom(if (settings.showPreview) bottom - top else 0)
        }

        controlBar = ControlBar(this)
        root.addView(controlBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refreshControlBar()
        labCrashReport(root)
        return root
    }

    private fun refreshControlBar() {
        if (testing) {
            controlBar.show(settings.shownButtonStyle, listOf("Testing your buttons", if (controls.remote) "Hold Back · Stop" else "Hold {B} · Stop"))
            return
        }
        if (finding) {
            controlBar.show(settings.shownButtonStyle, if (findBrowsing) listOf("{A} Go there", "{Y} Type", "{B} Close")
                else listOf("{A} Type", "{L1} {R1} Choose", "{START} Go there", "{B} Hide keyboard"))
            return
        }
        if (controls.remote) {
            controlBar.show(settings.shownButtonStyle, listOf("Ring · Move, up for sections", "OK · Select", "Back · Close"))
            return
        }
        // The tabs at the top show {L1} {R1} and {START} Find. With the keyboard up they fold into a thin line,
        // so then the bar says both again
        val up = settings.showPreview
        controlBar.show(
            settings.shownButtonStyle,
            listOfNotNull(
                if (up) "{L1} {R1} Sections" else null,
                "{L2} {R2} Jump",                              // up and down one group on the page
                "{A} Select",
                if (up) "{START} Find" else null,
                if (up) "{Y} Hide keyboard" else "{Y} Show keyboard",
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
        // Look opened: the new themes have been seen, the sign goes
        if (index in tabs.marks) {
            tabs.marks = tabs.marks - index
            settings = if (index == lookIndex) settings.copy(secretNew = false) else settings.copy(secretSoundNew = false)
            settings.save(this)
        }
        content.removeAllViews()

        placeGuidePicture(index)
        val rows = mutableListOf<SettingRow>()
        val starts = mutableListOf<Int>()
        val sections = pages[index].build().let { if (index == 0) listOfNotNull(setupSection()) + it else it }
            .filter { it.rows.isNotEmpty() }
        sections.forEach { section ->
            content.addView(SectionHeader(this, section.title))
            starts += rows.size
            val card = SettingsCard(this)
            section.rows.forEach { row ->
                row.id = View.generateViewId()
                card.addRow(row)
                rows += row
            }
            content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Up on the first row / Down on the last stays put instead of leaving the page
        rows.firstOrNull()?.let { it.nextFocusUpId = tabs.id }
        rows.lastOrNull()?.let { it.nextFocusDownId = it.id }
        focusableRows = rows
        sectionStarts = starts
        if (settings.showPreview && scroll.height > 0 && shortWithKeyboard()) rows.forEach { (it as? SettingRow)?.compact = true }

        if (animate) scroll.scrollTo(0, 0)
        muteSystemSounds(content)
        // Moving through the tabs (a remote): the focus stays on them; otherwise the page's row takes it
        if (!tabs.hasFocus()) rows.getOrNull(focusIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0)))?.requestFocus()
        rebuilding = false
        // Keyboard up: the new page's row sits above it too (once the page has its size)
        if (settings.showPreview && !sliding) scroll.post { placeFocused(animate = false) }

        if (animate) {
            // The new page comes up from 40 %, never from blank, so moving through tabs quickly doesn't flash dark
            // between them (Emre, 8 Nov: changing tabs was a strain on the eyes); a change mid-way picks up from there
            content.animate().cancel()
            content.alpha = PAGE_FROM_ALPHA
            content.translationX = px(8f * slideFrom).toFloat()
            content.animate().alpha(1f).translationX(0f).setDuration(Design.MOTION_PAGE)
                .setInterpolator(Design.EASE_OUT).start()
        } else { content.animate().cancel(); content.alpha = 1f; content.translationX = 0f }
    }

    /**
     * The Button guide's picture: beside the page on wide screens (it stays put while the rows
     * scroll), at the top of the page on narrow ones. Other pages get the full width back.
     */
    private fun placeGuidePicture(index: Int) {
        (pictureBlock.parent as? ViewGroup)?.removeView(pictureBlock)
        val onGuide = index == guideIndex || index == deviceIndex     // both show the device picture
        val beside = onGuide && wideScreen
        picturePanel.visibility = if (beside) View.VISIBLE else View.GONE
        // On the Device tab the words under the picture need room too: the block measures them first (roomBelowPx)
        val device = index == deviceIndex
        // On a screen with more room than the Odin the picture grows with it, by the tour's own rule, so it never
        // looks lost on a big screen (a vertical handheld on a 1080p screen at a small display size was tiny)
        val grow = TourScale.of(resources.configuration)
        guidePicture.maxHeightDp = grow * if (beside) (if (device) PICTURE_DEVICE_DP else PICTURE_WIDE_DP) else PICTURE_NARROW_DP
        guidePicture.tallBoost = !device || beside   // beside the page a tall picture (vertical) may grow, as far as the room above its words allows
        pictureOnStage = beside
        guidePicture.held = emptySet(); guidePicture.holding = emptySet(); guidePicture.stickSway = emptyMap(); guidePicture.stickPush = emptyMap()
        refreshPictureCaption(index)
        if (beside) picturePanel.addView(pictureBlock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        else if (onGuide) content.addView(pictureBlock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(Design.SPACE_L)
        })
        // The page column narrows to make room for the picture, and widens again without it
        val side = px(Design.SPACE_XL)
        val available = resources.displayMetrics.widthPixels - (if (beside) px(pictureColumnDp) else 0)
        content.layoutParams = (content.layoutParams as FrameLayout.LayoutParams).apply {
            width = minOf(available - 2 * side, px(MAX_CONTENT_WIDTH))
        }
        scroll.translationX = if (beside) sheetProgress * px(pictureColumnDp) / 2f else 0f
    }

    private fun refreshGuidePicture() {
        guidePicture.accent = settings.activeAccent
        guidePicture.show(controls.kind, settings.shownButtonStyle, controls.sticks)
    }

    // =====================================================================
    // KEYBOARD PREVIEW: a solid panel that slides up over the bottom of the page
    // (like a real keyboard appearing), while the page scrolls the row you're on into view.
    //
    // Nothing is re-measured or re-laid-out while it moves: the panel is laid out once at full
    // size and only slides (a translation), and the page only scrolls. Resizing views every
    // frame is what made quick presses ghost and jump. This is how Android's own keyboard
    // animation and bottom sheets do it. A second press turns round from wherever it is.
    // =====================================================================
    private var sheetAnim: ValueAnimator? = null
    /** True while the keyboard slides in or out. */
    private var sliding = false
    /** 0 = hidden, 1 = fully up. Also halfway through a slide. */
    private var sheetProgress = 0f

    /** The panel's full height (keyboard plus the soft fade above it), even before it's laid out. */
    private fun sheetFullHeight(): Int {
        if (sheet.height > 0) return sheet.height
        sheet.measure(View.MeasureSpec.makeMeasureSpec(dock.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return sheet.measuredHeight
    }

    /** Room below the last row, so every row can scroll up above the keyboard. */
    private fun setPageRoom(px: Int) {
        val column = scroll.getChildAt(0) ?: return
        if (column.paddingBottom != px) column.setPadding(column.paddingLeft, column.paddingTop, column.paddingRight, px)
    }

    /**
     * Puts the panel where [p] says. The keyboard is always shown at its real size; the tabs slide
     * up out of the way instead, giving the page their room (L1 and R1 still switch sections, and
     * the control bar names the one you're in). Cheap: no layout, only moves.
     */
    private fun applySheet(p: Float) {
        sheetProgress = p
        val full = sheetFullHeight()
        sheet.translationY = (1f - p) * full
        // With the keyboard up the tabs fold into a slim line of bars (TabBar.collapse) as the bar slides up,
        // leaving just that line and its soft edge in view. One movement, one element: nothing appears from
        // behind anything. While the tabs have the focus (a remote moving through them) they stay whole.
        val fold = if (tabs.hasFocus()) 0f else p
        tabs.collapse = fold
        topBar.translationY = -fold * (tabs.height - tabs.foldedHeightPx)
        bottomFade.alpha = 1f - p
        // The Button guide's picture keeps to the space above the keyboard, and fades as the keyboard takes it
        picturePanel.translationY = -p * full / 2f
        picturePanel.alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
        // ...and the page glides to the middle of the screen, so no empty half is left behind (audit, 28 Oct 2026)
        scroll.translationX = if (picturePanel.visibility == View.VISIBLE) p * px(pictureColumnDp) / 2f else 0f
    }

    /**
     * Where the page scrolls so the focused row sits whole in the middle of the space above the
     * keyboard (the tabs are out of the way then). A row taller than all of that space keeps its
     * bottom, where its switch, chips or slider are, in view. Null when nothing is focused.
     */
    private fun keyboardTarget(maxScroll: Int): Int? {
        val rect = focusedRect() ?: return null
        rect.offset(0, scroll.paddingTop)                       // page coordinates -> the scroll's
        return fit(rect, top = px(TabBar.FOLDED_DP + EDGE_FADE_DP), bottom = scroll.height - sheetFullHeight(), maxScroll = maxScroll, fade = px(EDGE_FADE_DP))
    }

    /** A screen too short for whole rows above the keyboard: rows hide their hints while it's open. */
    private fun shortWithKeyboard(): Boolean =
        scroll.height - sheetFullHeight() - px(TabBar.FOLDED_DP + EDGE_FADE_DP) < px(COMPACT_ROOM_DP)

    /** Hints in or out on every row of the page, then lays the page out at once, so the next scroll target is right. */
    private fun compactRows(on: Boolean) {
        var changed = false
        focusableRows.forEach { r -> (r as? SettingRow)?.let { if (it.compact != on) { it.compact = on; changed = true } } }
        if (!changed || scroll.width == 0) return
        scroll.measure(View.MeasureSpec.makeMeasureSpec(scroll.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(scroll.height, View.MeasureSpec.EXACTLY))
        scroll.layout(scroll.left, scroll.top, scroll.right, scroll.bottom)
    }

    /** One animation moves the panel, the tabs and the page together, from wherever they are. */
    private fun animateSheet(p1: Float, scroll1: Int, ms: Long, end: (() -> Unit)? = null) {
        sheetAnim?.let { it.removeAllListeners(); it.cancel() }
        // Stop any scroll still gliding, so only this moves the page. The zero-speed fling replaces
        // the glide, and computeScroll uses it up at once (else it would fire afterwards: a jump)
        scroll.fling(0)
        scroll.computeScroll()
        val p0 = sheetProgress
        val s0 = scroll.scrollY
        sheetAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = Design.EASE_STANDARD
            addUpdateListener {
                val f = it.animatedValue as Float
                applySheet(p0 + (p1 - p0) * f)
                scroll.scrollTo(0, (s0 + (scroll1 - s0) * f).roundToInt())
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (sheetAnim !== animation) return
                    sheetAnim = null
                    end?.invoke()
                }
            })
            start()
        }
    }

    /** L3 with the keyboard up: the next size, saved, with the Size row showing it (normal -> large -> largest -> normal). */
    private fun cycleSizeHere() {
        update(settings.copy(sizeLevel = (settings.sizeLevel + 1) % 3))   // Normal, Large, Largest (the Size row)
        feedback.on()
        if (currentPage == 1) {
            val focused = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
            showPage(currentPage, focusIndex = focused, animate = false)
        }
    }

    private fun togglePreview() {
        val show = !settings.showPreview
        settings = settings.copy(showPreview = show)
        settings.save(this)
        refreshControlBar()
        if (show) feedback.on() else feedback.off()

        val full = sheetFullHeight()
        sheet.visibility = View.VISIBLE
        // Hints step aside (or come back) first, so every position below is worked out on the final layout
        compactRows(show && shortWithKeyboard())
        val column = scroll.getChildAt(0) ?: return
        val contentHeight = column.height - column.paddingBottom       // the page without the keyboard room
        val pad = scroll.paddingTop
        // A shorter trip takes less time, so turning round mid-slide feels as quick as it looks
        val ms = (Design.MOTION_PANEL * kotlin.math.abs((if (show) 1f else 0f) - sheetProgress).coerceIn(0.35f, 1f)).toLong()
        sliding = true
        if (show) {
            setPageRoom(full)
            val target = keyboardTarget(maxScroll = contentHeight + full + pad - scroll.height) ?: scroll.scrollY
            animateSheet(1f, target, ms) {
                sliding = false
                // The room below the page exists now: settle exactly (a scroll can't go past the old end)
                scroll.post { placeFocused(animate = false) }
            }
        } else {
            // The tabs come back and the page gets its room back: keep the row you're on in view, just enough
            val fade = px(Design.SPACE_XL)
            var target = scroll.scrollY
            focusedRect()?.let { rect ->
                rect.offset(0, pad)
                target = target.coerceAtMost(rect.top - pad - fade).coerceAtLeast(rect.bottom - scroll.height + fade)
            }
            target = target.coerceIn(0, (contentHeight + pad - scroll.height).coerceAtLeast(0))
            animateSheet(0f, target, ms) {
                sliding = false
                sheet.visibility = View.INVISIBLE
                setPageRoom(0)
            }
        }
    }

    /** Keyboard up: the focused row goes to the middle of the space above it. */
    private fun placeFocused(animate: Boolean) {
        if (!settings.showPreview || sliding) return
        val column = scroll.getChildAt(0) ?: return
        val target = keyboardTarget(maxScroll = column.height + scroll.paddingTop - scroll.height) ?: return
        if (animate) animateSheet(1f, target, Design.MOTION_CONTROL)
        else {
            sheetAnim?.let { it.removeAllListeners(); it.cancel() }
            sheetAnim = null
            applySheet(1f)
            scroll.scrollTo(0, target)
        }
    }

    /**
     * For the checks: how much of the focused row is hidden under the tabs, the soft fades or the
     * screen's edge, in pixels (0 when it shows whole). Only without the keyboard preview.
     */
    internal fun focusedRowHiddenPx(): Int {
        val rect = focusedRect() ?: return 0
        rect.offset(0, scroll.paddingTop)                    // the page starts below the tabs
        val fade = px(EDGE_FADE_DP)
        val top = scroll.scrollY + scroll.paddingTop + fade
        val bottom = scroll.scrollY + scroll.height - fade
        if (rect.height() > bottom - top) return 0
        // At the very top the page is as it opened: its first card starts right under the tabs
        return maxOf(0, if (scroll.scrollY == 0) 0 else top - rect.top, rect.bottom - bottom)
    }

    /** The focused row, in page coordinates. */
    private fun focusedRect(): Rect? {
        val focused = currentFocus ?: window.decorView.findFocus() ?: return null
        val column = scroll.getChildAt(0) as? ViewGroup ?: return null
        val rect = Rect()
        focused.getDrawingRect(rect)
        return runCatching { column.offsetDescendantRectToMyCoords(focused, rect); rect }.getOrNull()
    }

    /**
     * The scroll position that shows [rect] whole, centred between [top] and [bottom] (on screen,
     * relative to the page's top edge). A row taller than even that space keeps its bottom, where
     * its switch, chips or slider are, in view.
     */
    private fun fit(rect: Rect, top: Int, bottom: Int, maxScroll: Int, fade: Int = 0): Int {
        val target = when {
            rect.height() <= bottom - top -> rect.centerY() - (top + bottom) / 2
            rect.height() <= bottom - (top - fade) -> rect.top - (top - fade)    // whole, using the soft edge: flush under the tabs
            else -> rect.bottom - bottom
        }
        return target.coerceIn(0, maxScroll.coerceAtLeast(0))
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
            KeyEvent.KEYCODE_BUTTON_START -> if (first) openFind()
            KeyEvent.KEYCODE_BUTTON_L2 -> if (first) jumpSection(-1)
            KeyEvent.KEYCODE_BUTTON_R2 -> if (first) jumpSection(1)
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (first) close()
            // The left stick resizes the keyboard shown here, as it does in an app (Emre, 5 Nov: it did nothing here)
            KeyEvent.KEYCODE_BUTTON_THUMBL -> if (first && settings.showPreview) cycleSizeHere()
            in UNUSED_PAD_KEYS -> Unit          // taken, so Android doesn't turn it into an A press
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode in UNUSED_PAD_KEYS) true else super.onKeyUp(keyCode, event)

    /**
     * Up from the first row goes to the tabs (how a remote switches sections). The page's scroll
     * view would otherwise take the key and only look for a row inside the page.
     */
    override fun dispatchKeyEvent(raw: KeyEvent): Boolean {
        // Face buttons by position, before anything else sees them (FaceButtons)
        // The button setup reads every press as it arrives, before anything turns it into a job
        if (setup != null) return setupKey(raw)
        if (FaceSetup.isFace(raw.keyCode) && raw.action == KeyEvent.ACTION_DOWN) lastFaceDevice = raw.deviceId
        val event = FaceButtons.event(raw)
        if (testing && testKey(event)) return true
        if (finding) { findKey(event); return true }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN &&
            focusableRows.firstOrNull()?.hasFocus() == true) {
            tabs.requestFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private var closing = false

    // ---- Find a setting (Start) -------------------------------------------------------------------------
    // Two ways to use it: typing (keyboard up; the buttons work as on the keyboard, Y is space) and browsing (keyboard
    // down, the D-pad goes through the matches; Y brings the keyboard back). B goes back one step: typing -> browsing -> closed.
    private var finding = false
    private var findBrowsing = false
    private var findQuery = ""
    private var findEntries: List<SettingsFind.Entry> = emptyList()
    private var keyboardBeforeFind = false
    private var findHat = 0 to 0

    /** Every row Find can go to, on every tab (built fresh each time, so it matches what's switched on). */
    internal fun findIndex(): List<SettingsFind.Entry> = pages.indices.flatMap { i ->
        val sections = pages[i].build().let { if (i == 0) listOfNotNull(setupSection()) + it else it }.filter { it.rows.isNotEmpty() }
        var n = 0
        sections.flatMap { section -> section.rows.mapNotNull { row ->
            val at = n++
            if (!SettingsFind.searchable(row)) null
            else SettingsFind.Entry(i, at, row.rowTitle, "${pages[i].title} › ${section.title}", row.rowHint) } }
    }

    private fun openFind() {
        if (finding || controls.remote) return
        finding = true; findBrowsing = false; findQuery = ""
        findEntries = findIndex()
        keyboardBeforeFind = settings.showPreview
        preview.applySettings(previewSettings().forControls(controls))           // English while finding
        if (!settings.showPreview) togglePreview()
        preview.listener = object : KeyGridView.Listener {
            override fun onText(text: String) = findType(findQuery + text)
            override fun onDelete() = findType(findQuery.dropLast(1))
            override fun onEnter() = findGo()
            override fun onTool(tool: Tool) {}
            override fun onClip(index: Int) {}
            // Accents: the keyboard swaps the letter it just typed for the one you pick (it used to say no, so the
            // accents closed as soon as they opened: Emre, 6 Nov)
            override fun onReplaceLast(expected: String, replacement: String): Boolean {
                if (!findQuery.endsWith(expected)) return false
                findType(findQuery.dropLast(expected.length) + replacement); return true
            }
        }
        feedback.on()
        findPanel.field.typing = true
        findPanel.chips = true
        sizeFindPanel()
        findType("")
        findPanel.alpha = 0f; findPanel.visibility = View.VISIBLE
        findPanel.animate().alpha(1f).setDuration(Design.MOTION_FADE).start()
        scroll.animate().alpha(0f).setDuration(Design.MOTION_FADE).start()          // the page steps back while you search
        picturePanel.animate().alpha(0f).setDuration(Design.MOTION_FADE).start()
        refreshControlBar()
    }

    /** The panel covers everything above the keyboard (or the whole page while browsing). */
    private fun sizeFindPanel() {
        val room = (dock.height - if (findBrowsing) 0 else sheetFullHeight()).coerceAtLeast(0)
        findPanel.layoutParams = findPanel.layoutParams.apply { height = room }
        findPanel.fit(room)
    }

    /** As many matches as fit: above the keyboard while typing (at least one), more while browsing (at most eight). */
    private fun findRoom() = if (!findBrowsing) findPanel.chipsThatFit(dock.width) else ((dock.height - px(110f)) / px(44f)).coerceIn(3, 8)

    private fun findType(q: String, keepPick: Boolean = false) {
        findQuery = q
        findPanel.show(q, SettingsFind.search(findEntries, q, findRoom()), keepPick)
    }

    /** Keyboard down to browse the matches, or up again to type. */
    private fun findBrowse(browse: Boolean) {
        if (findBrowsing == browse) return
        findBrowsing = browse
        stopFindNav()
        if (settings.showPreview == browse) togglePreview()
        findPanel.field.typing = !browse
        findPanel.chips = !browse
        sizeFindPanel()
        findType(findQuery, keepPick = true)
        refreshControlBar()
    }

    private fun closeFind(thenShow: Boolean = keyboardBeforeFind) {
        if (!finding) return
        finding = false; findBrowsing = false
        stopFindNav()
        preview.applySettings(previewSettings().forControls(controls))           // your languages again
        preview.listener = null
        preview.setHeldShift(false); preview.resetShift(); preview.resetLayer(); preview.cancelAccents()
        findPanel.animate().alpha(0f).setDuration(Design.MOTION_FADE).withEndAction { findPanel.visibility = View.GONE }.start()
        scroll.animate().alpha(1f).setDuration(Design.MOTION_FADE).start()
        picturePanel.animate().alpha(1f).setDuration(Design.MOTION_FADE).start()
        if (settings.showPreview != thenShow) togglePreview()
        refreshControlBar()
    }

    /**
     * Goes to the match that's picked: its tab, then the page glides (eased, not a jump) until the row sits in the
     * middle of the view, and the row lights up softly in your colour, holds, and fades.
     */
    private fun findGo() {
        val e = findPanel.results.getOrNull(findPanel.picked) ?: return
        closeFind()
        feedback.confirm()
        goTo(e)
    }

    /** Opened at a row by name (the keyboard's microphone with no voice app: "Voice typing"). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_GO_TO)?.let { goToNamed(it) }
    }

    private fun goToNamed(title: String) {
        val e = findIndex().firstOrNull { it.title == title } ?: return
        scroll.post { goTo(e) }
    }

    private fun goTo(e: SettingsFind.Entry) {
        slideFrom = if (e.page >= currentPage) 1 else -1
        showPage(e.page, focusIndex = e.row, animate = e.page != currentPage)
        scroll.postDelayed({
            val row = focusableRows.getOrNull(e.row) ?: return@postDelayed
            // Opened from the keyboard's microphone the window may still think it's being touched: the row takes the
            // highlight either way, so it's lit and one A away
            if (!row.requestFocus()) row.requestFocusFromTouch()
            if (!settings.showPreview) focusedRect()?.let { r ->
                val max = ((scroll.getChildAt(0)?.height ?: 0) + scroll.paddingTop - scroll.height).coerceAtLeast(0)
                val to = (r.centerY() + scroll.paddingTop - (scroll.paddingTop + scroll.height) / 2).coerceIn(0, max)
                val from = scroll.scrollY
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = FIND_GLIDE_MS; interpolator = Design.EASE_STANDARD
                    addUpdateListener { scroll.scrollTo(0, (from + (to - from) * (it.animatedValue as Float)).roundToInt()) }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) { row.flash() }
                    })
                    start()
                }
            } else { placeFocused(animate = true); row.flash() }
        }, maxOf(Design.MOTION_PAGE, Design.MOTION_PANEL) + FIND_SETTLE_MS)
    }

    private fun findKey(event: KeyEvent) {
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0
        if (findBrowsing) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> if (down && findPanel.pick(-1)) feedback.move()
                KeyEvent.KEYCODE_DPAD_DOWN -> if (down && findPanel.pick(1)) feedback.move()
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_START -> if (first) findGo()
                KeyEvent.KEYCODE_BUTTON_Y -> if (first) findBrowse(false)
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (first) { feedback.back(); closeFind() }
            }
            return
        }
        when (event.keyCode) {
            // Held, it repeats at the keyboard's own pace (it used to move once and stop: Emre, 6 Nov)
            KeyEvent.KEYCODE_DPAD_UP -> findNav(0, -1, down, first)
            KeyEvent.KEYCODE_DPAD_DOWN -> findNav(0, 1, down, first)
            KeyEvent.KEYCODE_DPAD_LEFT -> findNav(-1, 0, down, first)
            KeyEvent.KEYCODE_DPAD_RIGHT -> findNav(1, 0, down, first)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> if (first) preview.pressA() else if (!down) preview.releaseA()
            KeyEvent.KEYCODE_BUTTON_X -> if (down) { feedback.delete(); findType(findQuery.dropLast(1)) }
            KeyEvent.KEYCODE_BUTTON_R2 -> preview.setHeldShift(down)
            KeyEvent.KEYCODE_BUTTON_L2 -> if (first) preview.toggleLayer()
            KeyEvent.KEYCODE_BUTTON_L1 -> if (first && findPanel.pick(-1)) feedback.move()
            KeyEvent.KEYCODE_BUTTON_R1 -> if (first && findPanel.pick(1)) feedback.move()
            KeyEvent.KEYCODE_BUTTON_START -> if (first) findGo()
            // While typing, the buttons do what they do on the keyboard: Y is space (Emre, 5 Nov), and B hides the
            // keyboard, as it does everywhere you type, so the matches can be browsed with the D-pad
            KeyEvent.KEYCODE_BUTTON_Y -> if (first) { feedback.space(); findType("$findQuery ") }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (first) { feedback.off(); findBrowse(true) }
        }
    }

    private val findRepeater by lazy { Repeater(android.os.Handler(mainLooper)) }
    private var findNavAt = 0 to 0
    /** One move now, then repeats while held; let go (or another direction) stops it. */
    private fun findNav(dx: Int, dy: Int, down: Boolean, first: Boolean) {
        if (!down) { if (findNavAt == dx to dy) stopFindNav(); return }
        if (!first && findRepeater.isRunning) return                // Android's own repeats: ours already run
        findNavAt = dx to dy
        findRepeater.start(Design.dpadDelay(settings.dpadSpeed), { Design.dpadRepeat(settings.dpadSpeed) }) { preview.moveFocus(dx, dy) }
    }
    private fun stopFindNav() { findRepeater.stop(); findNavAt = 0 to 0 }

    /** A D-pad that reports as a hat, and triggers that report as axes, while finding (every bundled sample, in order). */
    private fun findMotion(event: android.view.MotionEvent) {
        if (!event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)) return
        fun hat(v: Float) = if (v > 0.5f) 1 else if (v < -0.5f) -1 else 0
        for (i in (0 until event.historySize).map<Int, Int?> { it } + listOf(null)) {
            fun v(a: Int) = if (i == null) event.getAxisValue(a) else event.getHistoricalAxisValue(a, i)
            val now = hat(v(android.view.MotionEvent.AXIS_HAT_X)) to hat(v(android.view.MotionEvent.AXIS_HAT_Y))
            if (now != findHat) {
                if (now == (0 to 0)) stopFindNav()
                else if (findBrowsing) { if (now.second != 0 && findPanel.pick(now.second)) feedback.move() }
                else findNav(now.first, if (now.first != 0) 0 else now.second, down = true, first = true)
            }
            findHat = now
            if (findBrowsing) continue
            val rt = maxOf(v(android.view.MotionEvent.AXIS_RTRIGGER), v(android.view.MotionEvent.AXIS_GAS))
            val lt = maxOf(v(android.view.MotionEvent.AXIS_LTRIGGER), v(android.view.MotionEvent.AXIS_BRAKE))
            if (!findRt && rt > 0.45f) { findRt = true; preview.setHeldShift(true) } else if (findRt && rt < 0.3f) { findRt = false; preview.setHeldShift(false) }
            if (!findLt && lt > 0.45f) { findLt = true; preview.toggleLayer() } else if (findLt && lt < 0.3f) findLt = false
        }
    }
    private var findRt = false
    private var findLt = false

    /**
     * LT / RT: up or down one group on the page (LB / RB already change tabs), to the group's first row. From the
     * tabs, RT goes into the page. The first and last groups stay put, with no wrap, so it never surprises.
     */
    internal fun jumpSection(dir: Int) {
        // Some pads send a trigger as a button and as an axis at once: one pull, one jump (as on the keyboard)
        val now = android.os.SystemClock.uptimeMillis()
        if (dir == lastJumpDir && now - lastJumpAt < JUMP_DEDUPE_MS) return
        lastJumpDir = dir; lastJumpAt = now
        val rows = focusableRows
        if (rows.isEmpty() || sectionStarts.isEmpty()) return
        val at = rows.indexOfFirst { it.hasFocus() }
        val target = if (at < 0) (if (dir > 0) 0 else return)
        else {
            val current = sectionStarts.indexOfLast { it <= at }
            // Inside a group, LT first goes to the top of that group, as Page Up does
            if (dir < 0 && at > sectionStarts[current]) sectionStarts[current]
            else sectionStarts.getOrNull(current + dir) ?: return
        }
        feedback.space()
        rows[target].requestFocus()
    }

    /** Triggers that report as axes (the Odin's), with the same press and release points as the keyboard. */
    private fun readJumpTriggers(event: android.view.MotionEvent) {
        if (!event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)) return
        fun read(i: Int?) : Pair<Float, Float> {
            fun v(a: Int) = if (i == null) event.getAxisValue(a) else event.getHistoricalAxisValue(a, i)
            return maxOf(v(android.view.MotionEvent.AXIS_LTRIGGER), v(android.view.MotionEvent.AXIS_BRAKE)) to
                maxOf(v(android.view.MotionEvent.AXIS_RTRIGGER), v(android.view.MotionEvent.AXIS_GAS))
        }
        for (i in (0 until event.historySize).map<Int, Int?> { it } + listOf(null)) {
            val (lt, rt) = read(i)
            if (!jumpLt && lt > 0.45f) { jumpLt = true; jumpSection(-1) } else if (jumpLt && lt < 0.3f) jumpLt = false
            if (!jumpRt && rt > 0.45f) { jumpRt = true; jumpSection(1) } else if (jumpRt && rt < 0.3f) jumpRt = false
        }
    }
    private var jumpLt = false
    private var jumpRt = false
    private var lastJumpDir = 0
    private var lastJumpAt = 0L

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
        val custom = settings.accentIndex < 0
        // The custom colour: three sliders, shown once "Custom" is picked (the presets stay one simple row).
        // Each slider's track shows the colours it makes with the other two as they are now.
        fun colour(h: Int = settings.customHue, st: Int = settings.customStrength, b: Int = settings.customBrightness) =
            Palette.customAccent(h, st, b)
        val customRows = mutableListOf<ColourSliderRow>()
        fun refreshTracks() = customRows.forEach { it.slider.invalidate() }
        // A retro theme brings its own colour: "Match theme" is the first choice (picked by default), any colour after it mixes
        val themeColour = settings.tone.ownHighlight
        val matched = themeColour != null && settings.themeHighlight
        fun stay() = showPage(currentPage, focusIndex = focusableRows.indexOfFirst { r -> r.hasFocus() }.coerceAtLeast(0), animate = false)
        swatches = SwatchRow(this, "Highlight colour", "The glow that follows your selection, in Settings too",
            Palette.accents.map { it.second }, if (matched) -1 else settings.accentIndex,
            lead = themeColour?.let { "Match theme" to it }, leadSelected = matched,
            onLead = { update(settings.copy(themeHighlight = true)); stay() }) {
            customRows.forEach { r -> r.setActive(false) }
            update(settings.copy(accentIndex = it, themeHighlight = false))
            // Leaving the custom colour (or Match theme): its rows go away again
            if (custom || matched) stay()
        }
        val hueRow = ColourSliderRow(this, "Custom colour", "Left and right move around the colour wheel",
            settings.customHue, 359, 10, active = custom, colourAt = { colour(h = it) }) {
            swatches.clearSelection()
            update(settings.copy(accentIndex = -1, customHue = it, themeHighlight = false)); refreshTracks()
            if (!custom || matched) showPage(currentPage, focusIndex = focusableRows.indexOfFirst { r -> r.hasFocus() }.coerceAtLeast(0), animate = false)
        }
        customRows += hueRow
        if (custom) {
            customRows += ColourSliderRow(this, "Strength", "Soft to vivid", settings.customStrength, 100, 5, active = true,
                colourAt = { colour(st = it) }) { update(settings.copy(customStrength = it)); refreshTracks() }
            customRows += ColourSliderRow(this, "Brightness", "Deeper to lighter. Always light enough to read", settings.customBrightness, 100, 5,
                active = true, colourAt = { colour(b = it) }) { update(settings.copy(customBrightness = it)); refreshTracks() }
        }
        return listOf(
        // One group: a "Theme" heading over a single "Theme" row said the same word twice (audit, 30 Oct 2026)
        Section("Theme and colour", themeRows() + listOfNotNull(
            if (settings.tone.clear) SliderRow(this, "See-through", "How much of the app behind shows. Lower is easier to read",
                0, 10, settings.seeThrough, { it.toString() }) {
                update(settings.copy(seeThrough = it))
            } else null
        ) + listOf<SettingRow>(swatches) + (if (matched) emptyList() else customRows)),
        Section("Keys", listOfNotNull(
            ChoiceRow(this, "Keyboard size", say("Also on the keyboard: press {L3}", "How big the keys are"),
                listOf("Normal", "Large", "Largest"), settings.sizeLevel.coerceIn(0, 2)) {
                update(settings.copy(sizeLevel = it))
            },
            ChoiceRow(this, "Key layout", "Grid lines keys up in straight columns, so up and down never guess",
                KeyLayout.values().map { it.label }, settings.layout.ordinal) {
                update(settings.copy(layout = KeyLayout.values()[it]))
            },
            // 1.3: Words stays the default, so nobody's keyboard changes until they pick (Emre, 8 Nov)
            ChoiceRow(this, "Top row", "How Select, Cut, Copy and Paste show. Where the words don't fit, just the icons",
                listOf("Words", "Icons", "Icons and words"), settings.toolStyle.coerceIn(0, 2)) {
                update(settings.copy(toolStyle = it))
            },
            ToggleRow(this, "Number row", "1 to 0 in a slim row above the letters",
                settings.numberRow) {
                update(settings.copy(numberRow = it))
            },
            // Only where it fits: wide screens held sideways. TV mode keeps its own floating keyboard.
            if (!SplitRoom.splitPossible(this, settings.language)) null else ToggleRow(this, "Split",
                "Two halves, one under each thumb. Not in TV mode", settings.split) {
                update(settings.copy(split = it))
            },
            // Android 13 and newer draw an empty strip under keyboards (a hide arrow and a keyboard switcher)
            if (android.os.Build.VERSION.SDK_INT < FullHeight.FROM_SDK) null else ToggleRow(this, FullHeight.ROW,
                "Hides Android's empty strip under the keyboard. Hold the settings key to switch keyboards", settings.fullHeight) {
                update(settings.copy(fullHeight = it))
            },

            ChoiceRow(this, "Key shape", "How rounded the keys are",
                KeyShape.values().map { it.label }, settings.keyShape.ordinal) {
                update(settings.copy(keyShape = KeyShape.values()[it]))
            },
            // A retro theme's own style comes first as "Match theme", like its colour and sounds
            settings.tone.ownStyle.let { own -> val lead = if (own != null) 1 else 0
                ChoiceRow(this, "Highlight style", "How the selected key is marked. Solid is the simplest",
                    listOfNotNull(if (own != null) "Match theme" else null) + HighlightStyle.values().map { it.label },
                    if (own != null && settings.themeStyle) 0 else settings.highlightStyle.ordinal + lead) {
                    update(if (own != null && it == 0) settings.copy(themeStyle = true)
                        else settings.copy(highlightStyle = HighlightStyle.values()[it - lead], themeStyle = false))
                } },
            ChoiceRow(this, "Highlight motion", "Subtle leaves a quick trace, Fluid glides, Instant just jumps",
                HighlightMotion.values().map { it.label }, settings.motion.ordinal) {
                update(settings.copy(motion = HighlightMotion.values()[it]))
            }
        )),
        Section("Letters", WeightSampleView(this) { settings.font }.let { sample -> listOf(
            ChoiceRow(this, "Font", "Hyperlegible is designed to be the easiest to read",
                FontChoice.values().map { it.label }, settings.font.ordinal) {
                update(settings.copy(font = FontChoice.values()[it]))
                sample.requestLayout(); sample.invalidate()
            },
            SliderRow(this, "Letter weight", "How bold the letters are", 0, 10, settings.letterWeight,
                { it.toString() }, sample = sample) {
                update(settings.copy(letterWeight = it))
            }
        ) })
        )
    }

    /** Language pickers for the quick switch: "None" first, then every language. */
    private fun extraLanguageOptions() = listOf("None") + Language.values().map { it.label }
    private fun extraIndex(l: Language?) = l?.let { it.ordinal + 1 } ?: 0
    private fun extraFrom(index: Int): Language? = if (index == 0) null else Language.values()[index - 1]

    /** The words for a setting, by what you hold: a controller with sticks, one without, or a remote. */
    private fun say(sticks: String, noSticks: String = sticks, remote: String = noSticks) =
        if (controls.remote) remote else if (controls.sticks) sticks else noSticks

    /**
     * Voice typing: whether the microphone will show, and what to do if it won't. Ready (a voice app is on),
     * installed but off (A opens Android's keyboard list), or none yet (A opens FUTO Voice Input's page).
     */
    private fun voiceRow(): SettingRow {
        VoiceInput.readyName(this)?.let { return InfoRow(this, "Voice typing", "Ready: the microphone in the top row uses $it") }
        VoiceInput.installedOnly(this)?.let { info ->
            val name = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault("your voice app")
            return ActionRow(this, "Voice typing", "Turn on $name in Android's keyboard list, and the microphone shows up") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }
        return ActionRow(this, "Voice typing", "Install a voice app such as FUTO Voice Input (free, works offline), and the microphone shows up") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(VoiceInput.GET_URL))) }
        }
    }

    private fun typingPage() = listOf(
        Section("Languages", listOfNotNull(
            StepperRow(this, "Language", "For Italian, Dutch, Polish and more, pick English and hold a letter for accents",
                Language.values().map { it.label }, settings.language1.ordinal) {
                update(settings.copy(language1 = Language.values()[it], activeLanguage = 0))
            },
            StepperRow(this, "Second language", say("Switch while typing: hold {R2} and press {L2}",
                    remote = "Optional. Switching between languages needs a controller"),
                extraLanguageOptions(), extraIndex(settings.language2)) {
                update(settings.copy(language2 = extraFrom(it), activeLanguage = 0))
            },
            StepperRow(this, "Third language", "Optional. The switch goes through them in turn",
                extraLanguageOptions(), extraIndex(settings.language3)) {
                update(settings.copy(language3 = extraFrom(it), activeLanguage = 0))
            },
            if (settings.languages.size < 2) null else ToggleRow(this, "Remember each app's language",
                "Each app opens in the language you last used there", settings.languagePerApp) {
                update(settings.copy(languagePerApp = it, appLanguages = if (it) settings.appLanguages else ""))
            },
            ToggleRow(this, "Language on the space bar", "When off, it still shows briefly after you switch",
                settings.showLanguageLabel) {
                update(settings.copy(showLanguageLabel = it))
            },
            voiceRow(),
            ToggleRow(this, "Microphone in the top row", "Hands over to your voice app. Without one, it brings you here", settings.voiceKey) {
                update(settings.copy(voiceKey = it))
            }
        )),
        Section("Help while typing", listOf(
            ToggleRow(this, "Word suggestions", say(
                    "Likely words in the top row. {R3} takes the first, or move up to pick one",
                    "Words you might mean show in the top row, most likely first. Move up to one and press {A} to take it",
                    "Words you might mean show in the top row, most likely first. Move up to one and press OK to take it"),
                settings.wordSuggestions) {
                update(settings.copy(wordSuggestions = it))
            },
            ToggleRow(this, "Learn my words", "Your common words come first. New ones join after three uses. Stays on this device",
                settings.learnWords) {
                update(settings.copy(learnWords = it))
            },
            UndoActionRow(this, "Forget my words", "Clears the words Inlay has learnt",
                "Your words are forgotten", "Changed your mind? Press it again to bring them back") {
                UserWords.forgetWithUndo(this)
            },
            ToggleRow(this, "Hold for accents", say("Hold {A} for é, ñ and more, punctuation on . and web endings on .com. Let go on the one you want",
                    remote = "Hold OK on a letter for é, ñ, ü and more. Let go and they stay open: OK types, Back cancels"),
                settings.holdAccents) {
                update(settings.copy(holdAccents = it))
            },
            ToggleRow(this, "Automatic capitals", "Capitals for new sentences and I. Never in web addresses, email or passwords",
                settings.autoCaps) {
                update(settings.copy(autoCaps = it))
            },
            ToggleRow(this, "Double space for full stop", say("Press {Y} twice after a word to type \". \"", remote = "Press Space twice after a word to type \". \""),
                settings.doubleSpacePeriod) {
                update(settings.copy(doubleSpacePeriod = it))
            },
            ToggleRow(this, "Tidy spaces", "A space typed just before . , ! or ? moves after it: \"word .\" becomes \"word. \"",
                settings.tidyPunctuation) {
                update(settings.copy(tidyPunctuation = it))
            },
            ToggleRow(this, "Smart keys", "/ and .com in web addresses, @ in email, a number pad for numbers",
                settings.smartKeys) {
                update(settings.copy(smartKeys = it))
            }
        )),
        Section("Shortcuts", listOfNotNull(
            ToggleRow(this, "Select all key", say("When off, press {SELECTx2} instead", remote = "A key in the top row that selects everything"),
                settings.selectAllButton) {
                update(settings.copy(selectAllButton = it))
            },
            if (controls.remote) null else ToggleRow(this, "Undo shortcut", "Hold {R2} and press {X}",
                settings.undoCombo) {
                update(settings.copy(undoCombo = it))
            }
        )),
        Section("Clipboard", listOf(
            ToggleRow(this, "Remember copied text",
                // pins are a controller's Y; a remote has no Y (the clipboard there is paste only)
                if (Controls.of(this, settings).remote) "Your last ${Layouts.MAX_CLIP_CARDS} copies, on this device only. Passwords are never kept"
                else "Your last ${Layouts.MAX_CLIP_CARDS} copies, on this device only. {Y} on one pins it. Passwords are never kept",
                settings.saveClipHistory) {
                update(settings.copy(saveClipHistory = it))
            },
            UndoActionRow(this, "Clear clipboard history", "Removes your saved copies from this device",
                "Clipboard history cleared", "Changed your mind? Press it again to bring it back") {
                ClipboardHistory.clearWithUndo(this)
            }
        )),
    )

    private fun feedbackPage() = listOf(
        Section("Vibration", listOf(
            ToggleRow(this, "Vibration", "A light buzz as you move and type", settings.vibration) {
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
            ActionRow(this, "Try the vibration", "Plays a key press, a trigger and caps lock") {
                playSequence(listOf(feedback::key, feedback::on, feedback::lock))
            }
        )),
        Section("Sound", listOfNotNull(
            // One row, no switch: a theme with sounds of its own starts on them (the pack says so), and picking
            // any other pack is mixing and matching. Picking the theme's own again goes back to following the theme.
            settings.tone.ownSound.let { own -> val lead = if (own != null) 1 else 0
                StepperRow(this, "Sound pack", null,
                listOfNotNull(if (own != null) "Match theme" else null) + SoundPack.listed(settings.retroUnlocked).map { it.label },
                if (own != null && settings.themeSound) 0 else SoundPack.listed(settings.retroUnlocked).indexOf(settings.soundPack).coerceAtLeast(0) + lead,
                descriptions = listOfNotNull(own?.description) + SoundPack.listed(settings.retroUnlocked).map { it.description }, silent = true,
                lead = if (settings.secretSoundRowNew) "New: three retro sound packs, one for each retro theme" else null) {
                update(if (own != null && it == 0) settings.copy(themeSound = true)
                    else settings.copy(soundPack = SoundPack.listed(settings.retroUnlocked)[it - lead], themeSound = false))
                // A short phrase in the new pack: two letters, space, a letter, Enter
                feedback.sample()
            }.apply {
                // like the Theme row: the spark waits on the row until the highlight reaches it
                newMark = settings.secretSoundRowNew
                onFound = { newMark = false; settings = settings.copy(secretSoundRowNew = false); settings.save(this@SettingsActivity) } } },
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
            ToggleRow(this, "Play in silent mode",
                // Muted right now by silent mode: say so here, so no sound never looks like a fault
                if (!settings.soundInSilent && Feedback.silentNow(this)) "Your device is on silent, so the sounds are muted. Turn this on to hear them"
                else "Sounds play even when your device is on silent",
                settings.soundInSilent) {
                update(settings.copy(soundInSilent = it))
            },
            ActionRow(this, "Try every sound", "Plays each one at its real volume") {
                playSequence(listOf(
                    feedback::move, feedback::key, feedback::space, feedback::delete, feedback::on,
                    feedback::off, feedback::lock, feedback::selectAll, feedback::confirm
                ))
            }
        ))
    )

    /**
     * Every button and combo, kept in step with the features that are switched on, grouped by what you're trying to do
     * (Emre picked this, 5 Nov): the six you need first at the top, then capitals and accents, words, fixing a typo,
     * copy and paste, and the keyboard itself.
     */
    private fun guidePage(): List<Section> {
        val basics = listOf(
            GuideRow(this, "Type the highlighted key", "{A}"),
            GuideRow(this, "Delete · hold to speed up", "{X}"),
            GuideRow(this, "Space", "{Y}"),
            GuideRow(this, "Shift · tap twice for caps lock", "{R2}"),
            GuideRow(this, "Enter · Search · Go", "{START}"),
            GuideRow(this, "Close the keyboard · leave select mode or the clipboard", "{B}")
        )
        val letters = mutableListOf(GuideRow(this, "One capital letter", "{R2} + {A}"))
        if (settings.holdAccents) letters += GuideRow(this, "Accents · hold, then choose", "hold {A}")
        letters += GuideRow(this, "Accent the last letter · press again for more", "{R2} + {Y}")
        letters += GuideRow(this, if (settings.smartKeys) "Numbers and symbols · letters in number-only boxes" else "Numbers and symbols", "{L2}")
        if (settings.languages.size > 1)
            letters += GuideRow(this, "Switch language (${settings.languages.joinToString(" → ") { it.label }})", "{R2} + {L2}")
        val words = if (!settings.wordSuggestions) emptyList() else listOf(if (controls.sticks)
            GuideRow(this, "Take the first suggested word · press the stick in", "{R3}")
            else GuideRow(this, "Take a suggested word", "up, then {A}"))
        val typo = mutableListOf(
            GuideRow(this, "Move the text cursor", "{L1} {R1}"),
            GuideRow(this, "Jump a whole word", "{R2} + {L1} {R1}")
        )
        if (settings.rightStick && controls.sticks) {
            typo.add(1, GuideRow(this, "Move the text cursor with the stick · push further to go faster", "{R3} left or right"))
            typo += GuideRow(this, "Jump a whole word with the stick", "{R2} + {R3} left or right")
            typo += GuideRow(this, "Go to the start or end · a line in long text", "{R3} up or down")
        }
        if (settings.undoCombo) typo += GuideRow(this, "Undo", "{R2} + {X}")
        val copy = listOf(
            GuideRow(this, "Select text", "{SELECT}"),
            GuideRow(this, "Select everything", "{SELECTx2}")
        )
        return if (DevicePicture.resolve(this, settings.picture) == DeviceKind.REMOTE) listOf(
            // A TV remote has a ring, OK and Back: the keyboard's own keys do the rest
            Section("TV remote", listOf(
                GuideRow(this, "Move the highlight", "the ring"),
                GuideRow(this, "Type the highlighted key", "OK"),
                GuideRow(this, "On a letter: its accents (é, è, ê…)", "hold OK"),
                GuideRow(this, "Capitals, numbers and symbols", "Shift or ?123 key, then OK"),
                GuideRow(this, "Close the keyboard", "Back"),
                InfoRow(this, "Space, Delete and Enter are keys on the keyboard itself, so it works with any remote", "")
            ))
        ) else listOf(
            Section("The basics", basics),
            Section("Capitals, accents and symbols", letters),
            Section("Words", words),
            Section("Fixing a typo", typo),
            Section("Copy and paste", copy),
            Section("Keyboard", if (controls.sticks) listOf(GuideRow(this, "Keyboard size · Normal, Large, Largest", "{L3}")) else emptyList())
        )
    }

    /**
     * The Device tab: first, since what you hold shapes the rest. What you're holding (and why
     * Automatic chose it), its button icons, the sticks switch for vertical handhelds, a live
     * button test, and a short list of what it changes. Room for more device features later.
     */
    private fun devicePage(): List<Section> {
        val c = controls
        return listOf(
            Section("Your device", listOfNotNull(
                ChoiceRow(this, "What you're holding", DevicePicture.why(this, settings.picture),
                    PictureChoice.values().map { it.label }, settings.picture.ordinal) {
                    update(settings.copy(picture = PictureChoice.values()[it]))
                },
                // Only for a vertical handheld, right under the choice that brings it up
                if (c.kind != DeviceKind.VERTICAL) null else ToggleRow(this, "My handheld has sticks",
                    "Most vertical handhelds don't. Turn on for the stick features and a picture with sticks", settings.verticalSticks) {
                    update(settings.copy(verticalSticks = it))
                },
                // A theme with button shapes of its own (Grey brick) adds "Match theme": never chosen for you, since
                // your icons say which real button to press
                if (c.remote) null else settings.tone.look?.badges.let { themed -> val lead = if (themed != null) 1 else 0
                    ChoiceRow(this, "Button icons", "Match the labels on your buttons",
                    listOfNotNull(if (themed != null) "Match theme" else null) + ButtonStyle.values().map { it.label },
                    if (themed != null && settings.themeIcons) 0 else settings.buttonStyle.ordinal + lead) {
                    update(if (themed != null && it == 0) settings.copy(themeIcons = true)
                        else settings.copy(buttonStyle = ButtonStyle.values()[it - lead], themeIcons = false))
                } },
                // Your buttons: where each is and what it does, set up by pressing them (ButtonSetup.kt)
                if (c.remote) null else yourButtonsRow(),
                // One row: left and right pick where the test shows, A starts it (the separate Full screen row is gone)
                ChoiceRow(this, "Try your buttons", if (c.remote) "Press OK, Back or the ring: they light up on the picture. Hold Back to stop"
                    else "{A} starts. Each press lights up and says what it does",
                    listOf("In the page", "Full screen"), if (settings.testFullScreen) 1 else 0, onActivate = { startButtonTest() }) {
                    update(settings.copy(testFullScreen = it == 1))
                },
            )),
            Section("Controls", (if (c.sticks) listOf(
            SliderRow(this, "Stick speed", "How fast the left stick moves the highlight. A\u00A0lighter push goes slower",
                0, 4, settings.stickSpeed, { STICK_LABELS[it] }) {
                update(settings.copy(stickSpeed = it))
            },
            ToggleRow(this, "Right stick moves the text cursor", "Push {R3} sideways to move it, further for faster. Up and down go a line",
                settings.rightStick) {
                update(settings.copy(rightStick = it))
            }
            ) else emptyList()) + listOf(
            // How fast a held D-pad keeps going (asked for in a fork, and by fast D-pad typists); Medium is the old pace
            SliderRow(this, if (c.remote) "Arrow speed" else "D-pad speed",
                if (c.remote) "How fast a held arrow keeps moving the highlight" else "How fast a held D-pad keeps moving the highlight",
                0, 4, settings.dpadSpeed, { STICK_LABELS[it] }) {
                update(settings.copy(dpadSpeed = it))
            },
            ChoiceRow(this, "TV mode", "A compact floating keyboard with large text. Automatic on Android TV or when docked",
                TvMode.values().map { it.label }, settings.tvMode.ordinal) {
                update(settings.copy(tvMode = TvMode.values()[it]))
            }
            )),
            // Button badges and combo tips are about a controller's buttons: a remote has none. One choice, since the
            // tips only make sense with the badges on (two switches said that less clearly)
            Section("Hints", if (c.remote) emptyList() else listOf(
            ChoiceRow(this, "Hints on keys", "Badges like {X} {Y} in the key corners. With combos: tips while you hold {R2}",
                listOf("Off", "Buttons", "Buttons and combos"), if (!settings.showHints) 0 else if (settings.comboHints) 2 else 1) {
                update(settings.copy(showHints = it > 0, comboHints = it == 2))
            }
            ))
        )
    }

    /** Under the picture: on the Device tab, what Inlay is set up for; on the Button guide, how the picture works. */
    private fun refreshPictureCaption(index: Int = currentPage) {
        if (!::pictureCaption.isInitialized) return
        val device = index == deviceIndex
        pictureTitle.visibility = if (device) View.VISIBLE else View.GONE
        if (!device) { pictureCaption.text = "The picture lights up the row you're on"; return }
        val c = controls
        val (title, body) = setUpFor(c)
        pictureTitle.text = title
        pictureCaption.text = body
    }

    /** What Inlay is set up for, and what that changes, in plain words. */
    private fun setUpFor(c: Controls): Pair<String, String> = when {
        c.remote -> "Set up for a TV remote" to
            "Everything works with the ring, OK and Back. Button hints are hidden"
        c.kind == DeviceKind.VERTICAL && !c.sticks -> "Set up for a vertical handheld" to
            "Your button icons on the keys. Without sticks, pick suggested words with the D-pad"
        else -> "Set up for ${when (c.kind) { DeviceKind.CONTROLLER -> "a controller"; DeviceKind.WIDE -> "a handheld"; else -> "a vertical handheld with sticks" }}" to
            "Your button icons on the keys. The right stick moves the text cursor and picks words"
    }

    // ---- Try your buttons: every press lights up on the picture -------------------------
    /** Try your buttons, live: what [key] does while typing, under the picture (from the Button guide's own rows). */
    private fun showWhatItDoes(key: String) {
        val rtHeld = "RT" in guidePicture.holding && key != "RT"
        val (what, line) = LiveCaption.describe(key, rtHeld, guideCheck())
        listOf(liveCaption, fullCaption).forEach { it.show(what, line, settings.shownButtonStyle) }
    }

    private var testing = false
    private var guideShown: Set<String> = emptySet()
    private var backDownAt = 0L
    private val stopTest = Runnable { endButtonTest() }

    private fun startButtonTest() {
        testing = true
        guideShown = emptySet()
        pictureTitle.visibility = View.GONE; pictureCaption.visibility = View.GONE
        listOf(liveCaption, fullCaption).forEach {
            it.show("Press any button", if (controls.remote) "Hold Back to stop" else "Hold {B} to stop", settings.shownButtonStyle)
            it.visibility = View.VISIBLE
        }
        guidePicture.held = emptySet(); guidePicture.holding = emptySet(); guidePicture.stickSway = emptyMap(); guidePicture.stickPush = emptyMap()
        konami.reset(); konami.prepare()
        konami.strength = settings.labBuzzStrength; konami.style = settings.labBuzzStyle
        // The preview keyboard would cover the picture: it steps aside for the test and comes back after
        keyboardWasUp = settings.showPreview && !controls.remote
        if (keyboardWasUp) togglePreview()
        if (settings.testFullScreen) showFullTest()
        mirrorPicture()
        refreshControlBar()
        scroll.removeCallbacks(stopTest)
        scroll.postDelayed(stopTest, BUTTON_TEST_IDLE_MS)
    }

    private fun endButtonTest() {
        if (!testing) return
        testing = false
        liveCaption.visibility = View.GONE; fullCaption.visibility = View.GONE
        pictureCaption.visibility = View.VISIBLE; refreshPictureCaption()
        guidePicture.held = emptySet(); guidePicture.holding = emptySet(); guidePicture.stickSway = emptyMap(); guidePicture.stickPush = emptyMap()
        konami.reset()
        mirrorPicture()
        val bringBack = keyboardWasUp; keyboardWasUp = false
        // The picture lands first, then the keyboard slides back up: one movement at a time
        if (bringBack && fullShown) hideFullTestUnderKeyboard()
        else hideFullTest { if (bringBack && !settings.showPreview) togglePreview() }
        scroll.removeCallbacks(stopTest)
        refreshControlBar()
    }

    /** The full-screen picture shows exactly what the small one shows. */
    private fun mirrorPicture() {
        if (!fullShown) return
        fullPicture.held = guidePicture.held; fullPicture.holding = guidePicture.holding
        fullPicture.stickSway = guidePicture.stickSway; fullPicture.stickPush = guidePicture.stickPush
    }

    /**
     * Full screen: the page fades away under a second picture, drawn at the full size (so it's as sharp as the
     * screen allows), that grows out of where the small one is (400 ms, eased in and out). Back the same way.
     * The two drawings are matched exactly (not their boxes, which have different shapes), so nothing jumps at
     * either end.
     */
    private fun showFullTest() {
        fullShown = true
        fullPicture.accent = settings.activeAccent
        fullBackdrop.background = Backdrop(settings.activeAccent, false)   // the tour's and What's new's own background
        fullPicture.show(controls.kind, settings.shownButtonStyle, controls.sticks)
        fitFullColumn()
        fullOverlay.visibility = View.VISIBLE
        fullBackdrop.alpha = 0f; fullPicture.alpha = 0f
        fullOverlay.post {
            fullPicture.pivotX = 0f; fullPicture.pivotY = 0f
            // lines and rings grow with the picture, so the big one is the small one made bigger
            val sr = guidePicture.drawnRect(); val br = fullPicture.drawnRect()
            fullPicture.inkScale = if (sr != null && br != null && sr.width() > 0f) br.width() / sr.width() else 1f
            val m = smallFromFull()
            if (m != null) {
                fullPicture.scaleX = m[0]; fullPicture.scaleY = m[0]; fullPicture.translationX = m[1]; fullPicture.translationY = m[2]
                fullPicture.alpha = 1f; guidePicture.alpha = 0f
            }
            fullBackdrop.animate().alpha(1f).setDuration(FULL_TEST_MS).setInterpolator(Design.EASE_STANDARD).start()
            fullPicture.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                .setDuration(FULL_TEST_MS).setInterpolator(Design.EASE_STANDARD).start()
        }
    }

    // ---- Where you are on the page (1.3, Emre, 8 Nov) ---------------------------------------------------------------
    // A slim bar in the page's right margin: the lit part is how much of the page shows and where. It lights in your
    // colour while the page moves (finger, D-pad or a trigger's jump alike: it's drawn with the page, in the same frame),
    // then settles to a quiet grey. Only on pages longer than the screen. Never over a row: it lives in the margin.
    private var positionLit = 0f
    private var positionAnim: android.animation.ValueAnimator? = null
    private val positionPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val settlePosition = Runnable {
        positionAnim?.cancel()
        positionAnim = android.animation.ValueAnimator.ofFloat(positionLit, 0f).apply {
            duration = Design.MOTION_PAGE; interpolator = Design.EASE_IN
            addUpdateListener { positionLit = it.animatedValue as Float; scroll.invalidate() }
            start()
        }
    }

    private fun pagePositionMoved(v: View) {
        positionAnim?.cancel(); positionLit = 1f
        v.removeCallbacks(settlePosition); v.postDelayed(settlePosition, POSITION_SETTLE_MS)
    }

    private fun drawPagePosition(v: ScrollView, c: android.graphics.Canvas) {
        positionForCheck = null
        val page = v.getChildAt(0) ?: return
        val view = v.height - v.paddingTop
        if (page.height <= view + px(Design.SPACE_S)) return              // it all fits: nothing to show
        val w = px(POSITION_WIDTH_DP).toFloat()
        // in the margin beside the page column: halfway between the cards' edge and the screen's
        val cardsRight = content.right + (content.parent as View).left
        val x = (cardsRight + v.width) / 2f
        val top = v.scrollY + v.paddingTop + px(Design.SPACE_L).toFloat()
        val bottom = v.scrollY + v.height - px(Design.SPACE_L).toFloat()
        val track = bottom - top
        if (track <= w * 4) return
        positionPaint.color = Contrast.withAlpha(android.graphics.Color.WHITE, Design.ALPHA_FAINT_LINE)
        c.drawRoundRect(x - w / 2f, top, x + w / 2f, bottom, w / 2f, w / 2f, positionPaint)
        val thumb = (track * view / page.height).coerceAtLeast(px(POSITION_MIN_DP).toFloat())
        val travel = (page.height - view).coerceAtLeast(1)
        val at = top + (track - thumb) * (v.scrollY.toFloat() / travel).coerceIn(0f, 1f)
        val quiet = Contrast.withAlpha(android.graphics.Color.WHITE, Design.ALPHA_INACTIVE)
        positionPaint.color = SettingsStyle.blend(quiet, settings.activeAccent, positionLit)
        c.drawRoundRect(x - w / 2f, at, x + w / 2f, at + thumb, w / 2f, w / 2f, positionPaint)
        positionForCheck = android.graphics.RectF(x - w / 2f, at - v.scrollY, x + w / 2f, at + thumb - v.scrollY)
    }
    /** Checks: where the lit part of the position bar was last drawn (on screen), or null when there was none. */
    internal var positionForCheck: android.graphics.RectF? = null

    /**
     * The caption's slot: as tall as the longest thing it says (RT's line with its combos), measured at this width;
     * the picture gets the rest of the height. Then nothing moves while you press buttons.
     */
    private fun fitFullColumn() {
        val room = fullOverlay.parent as? View ?: return
        val w = room.width - fullColumn.paddingLeft - fullColumn.paddingRight
        val h = room.height - fullColumn.paddingTop - fullColumn.paddingBottom
        if (w <= 0 || h <= 0) return
        val style = settings.shownButtonStyle
        val samples = listOf("Press any button" to (if (controls.remote) "Hold Back to stop" else "Hold {B} to stop"),
            LiveCaption.describe("RT", false, guideCheck()), LiveCaption.describe("A", false, guideCheck()), LiveCaption.describe("X", false, guideCheck()))
        val slot = samples.maxOf { (what, line) ->
            fullCaption.show(what, line, style)
            fullCaption.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            fullCaption.measuredHeight
        }
        fullCaption.show("Press any button", samples[0].second, style)
        (fullCaption.layoutParams as LinearLayout.LayoutParams).height = slot
        val gap = (fullCaption.layoutParams as LinearLayout.LayoutParams).topMargin
        fullPicture.maxHeightDp = maxOf(40f, (h - slot - gap) / resources.displayMetrics.density)
        fullColumn.requestLayout()
    }

    /**
     * The scale and offset (pivot at the top left) that put the full-screen drawing exactly over the small one,
     * or null when the small one isn't on screen (then it simply fades).
     */
    internal fun smallFromFull(): FloatArray? {
        if (!guidePicture.isShown || guidePicture.width <= 0 || fullPicture.width <= 0) return null
        val small = guidePicture.drawnRect() ?: return null
        val big = fullPicture.drawnRect() ?: return null
        val s = IntArray(2); guidePicture.getLocationInWindow(s)
        // where the full picture sits without any transform: its parent's place plus its own layout position
        val o = IntArray(2); fullColumn.getLocationInWindow(o)
        val fx = o[0] + fullPicture.left; val fy = o[1] + fullPicture.top
        val k = small.width() / big.width()
        return floatArrayOf(k, s[0] + small.left - fx - big.left * k, s[1] + small.top - fy - big.top * k)
    }

    /** Back to the page; [then] runs once the picture has landed (the keyboard comes back only after that). */
    private fun hideFullTest(then: () -> Unit = {}) {
        if (!fullShown) { then(); return }
        fullShown = false
        val m = smallFromFull()
        fullBackdrop.animate().alpha(0f).setDuration(FULL_TEST_MS).setInterpolator(Design.EASE_STANDARD).start()
        fullPicture.animate().apply {
            if (m != null) { scaleX(m[0]); scaleY(m[0]); translationX(m[1]); translationY(m[2]) } else alpha(0f)
        }.setDuration(FULL_TEST_MS).setInterpolator(Design.EASE_STANDARD).withEndAction {
            // the small picture is exactly where the big one landed: swap in the same frame, then tidy up
            guidePicture.alpha = 1f
            fullOverlay.visibility = View.GONE
            fullPicture.scaleX = 1f; fullPicture.scaleY = 1f; fullPicture.translationX = 0f; fullPicture.translationY = 0f
            then()
        }.start()
    }

    /**
     * The way back when the keyboard was open before the test: the keyboard slides straight back up, in front, while
     * the big picture shrinks towards its place and fades behind it, and the page fades in. One movement, nothing
     * lands on the side first.
     */
    private fun hideFullTestUnderKeyboard() {
        fullShown = false
        val m = smallFromFull()
        sheet.translationZ = 1f                                   // the keyboard draws in front of the big picture
        if (!settings.showPreview) togglePreview()
        val ms = Design.MOTION_PANEL
        fullBackdrop.animate().alpha(0f).setDuration(ms).setInterpolator(Design.EASE_STANDARD).start()
        fullPicture.animate().apply {
            if (m != null) { scaleX(m[0]); scaleY(m[0]); translationX(m[1]); translationY(m[2]) }
            alpha(0f)
        }.setDuration(ms).setInterpolator(Design.EASE_STANDARD).withEndAction {
            guidePicture.alpha = 1f
            fullOverlay.visibility = View.GONE
            fullPicture.alpha = 1f; fullPicture.scaleX = 1f; fullPicture.scaleY = 1f; fullPicture.translationX = 0f; fullPicture.translationY = 0f
            sheet.translationZ = 0f
        }.start()
    }

    /**
     * The code was entered: the reward plays (on the device's own screen in full screen, otherwise on a small retro
     * screen lifted out of the picture), then the test ends by itself and a spark flies from the picture to Look,
     * where it stays as a sign until Look is opened. Any press skips the reward.
     */
    internal var secretsFound = 0          // for the checks
    private fun secretFound() {
        if (!testing || secretScreen.showing || revealing) return
        secretsFound++
        scroll.removeCallbacks(stopTest)                          // the reward ends the test itself
        val d = IntArray(2); dock.getLocationInWindow(d)
        fun inDock(v: View, r: android.graphics.RectF) = android.graphics.RectF(r).also {
            val l = IntArray(2); v.getLocationInWindow(l); it.offset((l[0] - d[0]).toFloat(), (l[1] - d[1]).toFloat())
        }
        val picture = if (fullShown) fullPicture else guidePicture
        val screen = if (fullShown) fullPicture.screenInView() else null
        // Found before: a victory lap (Emre, 8 Nov), the same moment to record and share, but nothing is new again:
        // the last card drops "new" and its sparks, and no tab or setting changes
        val again = settings.retroUnlocked
        secretScreen.again = again
        secretScreen.onDone = {
            val keyboardComesBack = keyboardWasUp
            endButtonTest()
            // the spark only flies over a calm page: after the picture has landed, and not with the keyboard up
            // the sign appears once the page has settled (after the picture has landed, or the keyboard is back)
            if (!again) dock.postDelayed({ markLook() }, if (keyboardComesBack) Design.MOTION_PANEL + 120 else FULL_TEST_MS + 120)
        }
        revealing = true
        // the code's last press doesn't stay lit under the reward
        guidePicture.held = emptySet(); guidePicture.holding = emptySet(); mirrorPicture()
        // a beat after the last press: the words land together with the build-up's final note
        dock.postDelayed({ revealing = false; if (testing) reveal(screen, picture, ::inDock) }, (konami.finalNoteMs - SOUND_LATENCY_MS).coerceAtLeast(300L))
    }
    private var revealing = false
    private fun reveal(screen: android.graphics.RectF?, picture: DevicePictureView, inDock: (View, android.graphics.RectF) -> android.graphics.RectF) {
        try {
            if (screen != null) secretScreen.showOn(inDock(fullPicture, screen))
            else secretScreen.liftOut(picture.drawnRect()?.let { inDock(picture, it) })
        } catch (e: Throwable) {
            android.util.Log.e("InlayKonami", "reward failed", e)
            if (KonamiCode.hasLab(this)) controlBar.show(settings.shownButtonStyle, listOf(e.toString().take(120)))
            secretScreen.onDone()
        }
    }

    /**
     * Test builds only: if the app crashed last time, the cause is shown in small red text at the top (and the
     * next crash is written down for next time). Nothing of this exists in the release build.
     */
    private fun labCrashReport(root: LinearLayout) {
        if (!KonamiCode.hasLab(this)) return
        val file = java.io.File(filesDir, "last-crash.txt")
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { file.writeText(android.util.Log.getStackTraceString(e).lines().take(14).joinToString("\n")) }
            before?.uncaughtException(t, e)
        }
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrNull(); file.delete()
        root.addView(android.widget.TextView(this).apply {
            setText("Last crash (test build):\n$text"); setTextColor(Design.LAB_PROBLEM); textSize = Design.TYPE_CAPTION
            setPadding(px(16f), px(4f), px(16f), px(4f))
        }, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * Look gets its sign: the Look tab makes a little room, then a small spark grows in after its title with one
     * soft ring opening around it. With the keyboard up (tabs folded into a line) it's a dot by Look's bar.
     */
    private fun markLook() {
        if (lookIndex < 0 || isFinishing) return
        // the reward said "3 sound packs in Sound": Sound & haptics carries the same sign (once, as for a first retro theme)
        val sounds = !settings.secretSoundShown
        settings = settings.copy(secretNew = true, secretRowNew = true, retroUnlocked = true,
            secretSoundNew = settings.secretSoundNew || sounds, secretSoundRowNew = settings.secretSoundRowNew || sounds, secretSoundShown = true); settings.save(this)
        if (sounds) markTab(lookIndex, soundIndex) else markTab(lookIndex)
    }
    /** Tabs get their signs, grown in together (not on the page you're on: there's nothing to find there). */
    private fun markTab(vararg tab: Int) {
        val new = tab.filter { it >= 0 && it != currentPage }.toSet()
        if (new.isEmpty() || isFinishing) return
        tabs.marks = tabs.marks + new; tabs.growing = new; tabs.markAlpha = 0f; tabs.signAlpha = 0f
        android.animation.ValueAnimator.ofFloat(0f, 2f).apply {
            duration = Design.MOTION_PANEL + SIGN_IN_MS; interpolator = android.view.animation.LinearInterpolator()
            val split = Design.MOTION_PANEL.toFloat() / duration
            addUpdateListener {
                val f = it.animatedFraction
                tabs.markAlpha = Design.EASE_STANDARD.getInterpolation((f / split).coerceAtMost(1f))
                tabs.signAlpha = ((f - split) / (1f - split)).coerceIn(0f, 1f)
            }
            start()
        }
    }

    /**
     * Theme, in collections once the Retro collection has been found: "Collection" (Inlay or Retro) picks which
     * themes the Theme row steps through, so new collections never make the list long. A retro theme brings its
     * own highlight (a switch under Theme turns it back to yours). The spark marks Collection until it's visited.
     */
    private fun themeRows(): List<SettingRow> {
        val retro = settings.retroTheme
        val rows = mutableListOf<SettingRow>()
        if (settings.retroUnlocked || retro) rows += ChoiceRow(this, "Collection",
            if (settings.secretRowNew) "New: Retro, three themes after classic consoles" else "Inlay's own themes, or the Retro set",
            listOf("Inlay", "Retro"), if (retro) 1 else 0) {
            val toRetro = it == 1
            if (toRetro == settings.retroTheme) return@ChoiceRow
            pickTheme(if (toRetro) Palette.RETRO_BASE else settings.lastInlayTone.coerceIn(0, Palette.tones.lastIndex))
            reshowPage()
        }.apply {
            newMark = settings.secretRowNew
            onFound = { newMark = false; settings = settings.copy(secretRowNew = false); settings.save(this@SettingsActivity) }
        }
        rows += if (retro) StepperRow(this, "Theme", null, Palette.retroShown.map { it.name }, settings.toneIndex - Palette.RETRO_BASE,
            descriptions = Palette.retroShown.map { it.note ?: "" }) { pickTheme(Palette.RETRO_BASE + it) }
        else StepperRow(this, "Theme", "Dark themes suit OLED screens. Clear shows the app behind",
            Palette.tones.map { it.name }, settings.toneIndex.coerceIn(0, Palette.tones.lastIndex)) { pickTheme(it) }
        return rows
    }

    /** A theme was picked: the Clear slider or the retro rows may come or go, and a first retro theme marks its sounds. */
    private fun pickTheme(index: Int) {
        val wasRetro = settings.retroTheme
        val nowRetro = index >= Palette.RETRO_BASE
        var next = settings.copy(toneIndex = index, lastInlayTone = if (nowRetro) settings.lastInlayTone else index)
        // Into the Retro collection: it starts as designed, on Match theme (a mix made between retro themes stays)
        if (nowRetro && !wasRetro) next = next.copy(themeHighlight = true, themeStyle = true, themeSound = true)
        val firstRetro = nowRetro && !wasRetro && Palette.toneAt(index).ownSound != null && next.themeSound && !settings.secretSoundShown
        if (firstRetro) next = next.copy(secretSoundNew = true, secretSoundRowNew = true, secretSoundShown = true)
        update(next)
        if (firstRetro) markTab(soundIndex)
        // A retro theme's own colour, style and sounds sit in rows on this page (Match theme): rebuild them whenever a
        // retro theme is involved, so the Match theme dot is always the new theme's colour (it kept the old one)
        if (nowRetro || wasRetro) reshowPage()
    }
    /** A retro theme's own highlight is on: the highlight colour and style rows step aside (they'd change nothing). */
    private val soundIndex get() = pages.indexOfFirst { it.title == "Sound & haptics" }
    private fun reshowPage() {
        val focused = focusableRows.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
        showPage(currentPage, focusIndex = focused, animate = false)
    }

    /** While testing, every button only lights up. Holding B (Back) stops; so does a quiet spell. */
    private fun testKey(event: KeyEvent): Boolean {
        // The reward is showing: any press skips it (and is used up by that)
        if (revealing) return true
        if (secretScreen.showing) { if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) secretScreen.finish(); return true }
        val key = DevicePicture.keyFor(event.keyCode) ?: return false
        val back = event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK
        scroll.removeCallbacks(stopTest)
        scroll.postDelayed(stopTest, BUTTON_TEST_IDLE_MS)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                guidePicture.held = guidePicture.held + key
                guidePicture.holding = guidePicture.holding + key
                if (back) backDownAt = event.eventTime
                konami.press(ButtonRoles.position(key))      // the code is a sequence of positions
                showWhatItDoes(key)
            }
            mirrorPicture()
            if (back && event.eventTime - backDownAt >= BUTTON_TEST_STOP_MS) endButtonTest()
        } else if (event.action == KeyEvent.ACTION_UP) {
            guidePicture.held = guidePicture.held - key
            guidePicture.holding = guidePicture.holding - key
            konami.release(ButtonRoles.position(key))
            mirrorPicture()
            if (back && event.eventTime - backDownAt >= BUTTON_TEST_STOP_MS) endButtonTest()
        }
        return true
    }

    /** Triggers and D-pads that report as axes light up too. */
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (finding) { findMotion(event); return true }
        if (!testing) { readJumpTriggers(event); return super.dispatchGenericMotionEvent(event) }
        if (revealing) return true
        if (secretScreen.showing) {
            if (kotlin.math.abs(event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)) > 0.5f || kotlin.math.abs(event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)) > 0.5f) secretScreen.finish()
            return true
        }
        // D-pads that report as a hat, sticks and triggers count as using it too: the quiet-spell timer starts over
        scroll.removeCallbacks(stopTest)
        scroll.postDelayed(stopTest, BUTTON_TEST_IDLE_MS)
        val hx = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)
        val lt = maxOf(event.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER), event.getAxisValue(android.view.MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER), event.getAxisValue(android.view.MotionEvent.AXIS_GAS))
        val axes = buildSet {
            if (hx < -0.5f) add("Dleft"); if (hx > 0.5f) add("Dright"); if (hy < -0.5f) add("Dup"); if (hy > 0.5f) add("Ddown")
            if (lt > 0.5f) add("LT"); if (rt > 0.5f) add("RT")
        }
        val axisKeys = setOf("Dleft", "Dright", "Dup", "Ddown", "LT", "RT")
        // Sticks pushed past a small dead zone slide on the picture, the way they're pushed
        fun stick(x: Float, y: Float) = if (kotlin.math.hypot(x, y) > 0.25f) android.graphics.PointF(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f)) else null
        val pushes = buildMap {
            stick(event.getAxisValue(android.view.MotionEvent.AXIS_X), event.getAxisValue(android.view.MotionEvent.AXIS_Y))?.let { put("LS", it) }
            stick(event.getAxisValue(android.view.MotionEvent.AXIS_Z), event.getAxisValue(android.view.MotionEvent.AXIS_RZ))?.let { put("RS", it) }
        }
        // A D-pad that reports as a hat counts for the code too: a direction appearing is a press
        val dpad = setOf("Dleft", "Dright", "Dup", "Ddown")
        // Android bundles samples per frame: a quick tap can start and end inside one bundle, so the samples before
        // the last one are read in order too, or the code would miss fast presses
        fun hatAt(x: Float, y: Float) = buildSet { if (x < -0.5f) add("Dleft"); if (x > 0.5f) add("Dright"); if (y < -0.5f) add("Dup"); if (y > 0.5f) add("Ddown") }
        var before = guidePicture.held.filter { it in dpad }.toSet()
        for (i in 0 until event.historySize) {
            val at = hatAt(event.getHistoricalAxisValue(android.view.MotionEvent.AXIS_HAT_X, i), event.getHistoricalAxisValue(android.view.MotionEvent.AXIS_HAT_Y, i))
            (before - at).forEach { konami.release(it) }
            (at - before).forEach { konami.press(it) }
            before = at
        }
        guidePicture.stickPush = pushes
        val clicked = guidePicture.held.filter { it == "LS" || it == "RS" }.filter { it !in pushes }.filter { it in guidePicture.holding }
        guidePicture.held = guidePicture.held - axisKeys - setOf("LS", "RS") + axes + pushes.keys + clicked
        guidePicture.holding = guidePicture.holding - axisKeys + axes.filter { it == "LT" || it == "RT" }
        val nowDpad = axes.filter { it in dpad }.toSet()
        (before - nowDpad).forEach { konami.release(it) }
        (nowDpad - before).forEach { konami.press(it) }
        (nowDpad - before).firstOrNull()?.let { showWhatItDoes(it) }
        // triggers and stick pushes that report as axes show what they do too, once as they arrive
        (axes.filter { it == "LT" || it == "RT" } - guideShown).firstOrNull()?.let { showWhatItDoes(it) }
        guideShown = axes.filter { it == "LT" || it == "RT" }.toSet()
        mirrorPicture()
        return true
    }

    /** For the checks: open a tab as a press would. */
    internal fun tabsForCheck(index: Int) = showPage(index, focusIndex = 0, animate = false)
    internal val findQueryForCheck get() = findQuery
    internal fun openFindForCheck() = openFind()
    internal fun closeFindForCheck() = closeFind()

    /** For the consistency checks: the Button guide's rows (what, buttons) for the device picked. */
    internal fun guideCheck(): List<Pair<String, String>> = guidePage().flatMap { it.rows }.filterIsInstance<GuideRow>().map { it.title to it.buttons }

    /** Test builds only: the Konami Code's buzz, to compare by feel on the device. Folded at the end of About. */
    private fun labSection(): Section? {
        if (controls.remote || !KonamiCode.hasLab(this)) return null
        return Section("Test build", listOfNotNull(
                ChoiceRow(this, "Test build: buzz strength",
                    "How strong the Konami Code's buzz is", KonamiCode.BUZZ_STRENGTHS, settings.labBuzzStrength.coerceIn(0, 3)) {
                    update(settings.copy(labBuzzStrength = it))
                },
                ChoiceRow(this, "Test build: buzz style",
                    "Calibrated uses Android's own haptics where the device has them", KonamiCode.BUZZ_STYLES, settings.labBuzzStyle.coerceIn(0, 3)) {
                    update(settings.copy(labBuzzStyle = it))
                }
        ))
    }

    /** Is Inlay turned on, and is it the keyboard in use? One row that says which, and fixes it with A. */
    private enum class KeyboardState { OFF, OTHER, READY }
    private fun keyboardState(): KeyboardState {
        val imm = getSystemService(InputMethodManager::class.java) ?: return KeyboardState.READY
        val mine = imm.enabledInputMethodList.firstOrNull { it.packageName == packageName } ?: return KeyboardState.OFF
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return if (current == mine.id) KeyboardState.READY else KeyboardState.OTHER
    }
    private fun keyboardCheckRow() = when (keyboardState()) {
        KeyboardState.OFF -> ActionRow(this, "Keyboard doesn't show up?", "Inlay isn't turned on yet. Press {A} to turn it on") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        KeyboardState.OTHER -> ActionRow(this, "Keyboard doesn't show up?", "Another keyboard is in use. Press {A} to pick Inlay") {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() }
        KeyboardState.READY -> ActionRow(this, "Keyboard doesn't show up?", "Inlay is on and in use. If an app still hides it, check Android's keyboard settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
    }

    private fun isAyn(): Boolean = listOf(android.os.Build.MANUFACTURER, android.os.Build.BRAND, android.os.Build.MODEL)
        .any { it?.lowercase()?.let { v -> "ayn" in v.split(' ', '_', '-') || v.startsWith("odin") } == true }

    private fun aboutPage() = listOfNotNull(
        Section("Setup and help", listOfNotNull(
            ActionRow(this, "Turn on Inlay", "Opens Android's list of keyboards") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            },
            ActionRow(this, "Switch keyboard", "Choose the keyboard to use now. Also: hold the settings key") {
                getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            },
            // "The keyboard doesn't show up" (1.3): it says what's actually wrong, and A goes straight to the fix
            keyboardCheckRow(),
            // AYN handhelds: holding LB + RB for a second turns on the Odin's own virtual mouse, which looks as if
            // Inlay broke the buttons (Emre, 7 Nov). Only shown on AYN devices.
            if (isAyn()) InfoRow(this, "A mouse cursor showed up?",
                "Holding LB and RB together for a second turns on the Odin's virtual mouse. Do the same again to turn it off.") else null,
            ActionRow(this, "Replay the tour", "The one-minute walk through the buttons and combos") {
                startActivity(Intent(this, TutorialActivity::class.java))
            },
            ActionRow(this, "What's new in ${WhatsNew.VERSION}", "The highlights of this update, acted out") {
                startActivity(Intent(this, TutorialActivity::class.java).putExtra(TutorialActivity.EXTRA_WHATS_NEW, true))
            },
            ActionRow(this, "Back up your settings", "Your settings and learnt words, in a file you choose") {
                runCatching { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(Backup.MIME).putExtra(Intent.EXTRA_TITLE, Backup.suggestedName()), REQUEST_BACKUP) }
            },
            ActionRow(this, "Restore from a backup", "Brings back settings and words from a backup file") {
                runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"), REQUEST_RESTORE) }
            },
            ActionRow(this, "Full changelog", "Every change in every version, on GitHub") {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$SOURCE_URL/blob/main/CHANGELOG.md"))) }
            }
        )),
        Section("Inlay", listOfNotNull(
            InfoRow(this, "Version", appVersion()),
            InfoRow(this, "Licence and credits", "Free and open source. Fonts: Atkinson Hyperlegible Next, Inter, M PLUS Rounded 1c and Press Start 2P (Open Font Licence). PlayStation drawing traced from \"Dualshock 4 Layout.svg\" by Tokyoship (CC BY 3.0). Full screen height after an idea from Odin KB. Controller names belong to their makers"),
            SOURCE_URL.takeIf { it.isNotEmpty() }?.let { url ->
                ActionRow(this, "Source code", "See how it's made, report a problem or suggest an idea") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                }
            },
        )),
        Section("Updates", listOf(
            ActionRow(this, "Get updates automatically",
                "Adds Inlay to Obtainium, a free app that installs new versions from GitHub") {
                addToObtainium()
            }
        )),
        labSection()
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

    companion object {
        /** Open Settings at the row with this title (the keyboard's microphone, with no voice app yet). */
        const val EXTRA_GO_TO = "go_to"
        private const val MAX_CONTENT_WIDTH = 720f
        const val PICTURE_PANEL_DP = 300f
        /** The device picture sits beside the page only when the page keeps at least ~380 dp (DESIGN_GUIDE: narrow under 600 dp). */
        const val SIDE_PICTURE_MIN_DP = 600
        const val SHEET_FADE_DP = 12f
        const val EDGE_FADE_DP = 16f        // the page's soft top and bottom edges
        const val COMPACT_ROOM_DP = 150f    // less room than this above the keyboard: rows hide their hints while it's open
        const val BUTTON_TEST_IDLE_MS = 8000L    // the button test ends on its own after this long without a press
        const val BUTTON_TEST_STOP_MS = 700L     // holding B (Back) this long ends it
        const val FULL_TEST_MS = 400L            // Try your buttons growing to full screen, and back
        const val PAGE_FROM_ALPHA = 0.4f         // a new Settings page fades up from here
        const val POSITION_WIDTH_DP = 3f         // the page position bar
        const val POSITION_MIN_DP = 24f          //   its lit part is never shorter than this
        const val POSITION_SETTLE_MS = 700L      //   lit this long after the page last moved, then it settles
        const val SOUND_LATENCY_MS = 30L         // how long a sound takes to be heard after it's played, about
        const val SIGN_IN_MS = Design.MOTION_SIGN   // Look's sign growing in, with its ring
        const val TOP_SNAP_DP = 72f         // closer to the top than this, a scroll goes right to it
        const val PICTURE_WIDE_DP = 300f
        const val PICTURE_DEVICE_DP = 165f    // the Device tab's picture, with its caption below
        const val PICTURE_NARROW_DP = 150f
        const val CLOSE_DELAY_MS = 140L
        const val JUMP_DEDUPE_MS = 80L
        const val FIND_SETTLE_MS = 60L
        const val FIND_GLIDE_MS = 420L      // the page's glide to a found row: calm enough to follow with the eye     // after the page and the keyboard have moved, before Find's row is brought into view       // a trigger sent as a button and an axis counts once (the keyboard uses the same)
        /** The project's web page (e.g. on GitHub). Leave empty to hide the "Source code" row. */
        const val SOURCE_URL = "https://github.com/dakingeman/inlay"
        val STICK_LABELS = listOf("Slowest", "Slow", "Medium", "Fast", "Fastest")
    }
}

private const val REQUEST_BACKUP = 41
private const val REQUEST_RESTORE = 42
