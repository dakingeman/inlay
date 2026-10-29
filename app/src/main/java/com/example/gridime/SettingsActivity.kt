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
    /** What the picked device can press (see [Controls]): rows, hints and the guide follow it. */
    private val controls get() = Controls.of(this, settings)
    private lateinit var sheet: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomFade: View
    private lateinit var dock: FrameLayout
    private lateinit var controlBar: ControlBar
    // NEW: the Button guide's device picture. Beside the page on wide screens, above it on narrow ones.
    private lateinit var guidePicture: DevicePictureView
    private lateinit var picturePanel: LinearLayout
    private lateinit var pictureBlock: LinearLayout
    private lateinit var pictureTitle: android.widget.TextView
    private lateinit var pictureCaption: android.widget.TextView
    private val guideIndex get() = pages.indexOfFirst { it.title == "Button guide" }
    private val deviceIndex get() = pages.indexOfFirst { it.title == "Device" }
    /** Landscape with room for the page and the picture side by side. */
    /** The picture's column beside the page: narrower on small screens, so the rows keep room. */
    private val pictureColumnDp get() = minOf(PICTURE_PANEL_DP, resources.configuration.screenWidthDp * 0.36f)
    private val wideScreen get() = resources.configuration.screenWidthDp >= SIDE_PICTURE_MIN_DP &&
        resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private var currentPage = 0
    private var focusableRows: List<SettingRow> = emptyList()

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
        SettingsStyle.accent = settings.accent
        // The volume buttons change the same volume the sounds play at (media),
        // so what you set here is exactly what you hear on the keyboard.
        volumeControlStream = AudioManager.STREAM_MUSIC
        feedback = Feedback(this).apply { update(settings) }
        SettingRow.buttonStyle = settings.buttonStyle
        SettingRow.sounds = feedback
        setupState = currentSetupState()
        setContentView(buildScreen().also { muteSystemSounds(it); SettingsStyle.useFont(it) })
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

    override fun onDestroy() {
        sheetAnim?.cancel()
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
        // Picking the remote (or leaving it) changes the Button guide's sections
        val pictureChanged = changed.picture != settings.picture || changed.verticalSticks != settings.verticalSticks
        settings = changed
        if (accentChanged) {
            SettingsStyle.accent = settings.accent
            invalidateAll(window.decorView)
        }
        settings.save(this)
        feedback.update(settings)
        preview.applySettings(settings.forControls(controls))
        refreshGuidePicture()
        // Show TV mode on the preview when it's set to On, with a sample line of text
        preview.setTvMode(settings.tvMode == TvMode.ON)
        preview.setPreview("ocarina of time", "", "")
        if (pictureChanged) { tabs.remote = controls.remote; refreshControlBar() }
        if ((clearChanged || pictureChanged) && !styleChanged) {
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
            preview.applySettings(settings.forControls(controls))
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
            id = View.generateViewId()
            remote = controls.remote
            onKeyboard = { togglePreview() }
            onDown = { focusableRows.firstOrNull()?.requestFocus() }
            // While the tabs have the focus they stay in view, even with the keyboard up
            setOnFocusChangeListener { _, _ -> if (sheetAnim == null) applySheet(sheetProgress) }
        }
        tabs.setBackgroundColor(SettingsStyle.background)       // it lies over the page's top, which scrolls under it

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
            accent = settings.accent
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        pictureBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(guidePicture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        return root
    }

    private fun refreshControlBar() {
        if (testing) {
            controlBar.show(settings.buttonStyle, listOf(
                "Testing your buttons: press anything",
                if (controls.remote) "Hold Back · Stop" else "Hold {B} · Stop"))
            return
        }
        if (controls.remote) {
            controlBar.show(settings.buttonStyle, listOf("Ring · Move, up for sections", "OK · Select", "Back · Close"))
            return
        }
        controlBar.show(
            settings.buttonStyle,
            listOf(
                "{L1} {R1} Sections",                          // with the keyboard up, the strip at the top names them
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

        placeGuidePicture(index)
        val rows = mutableListOf<SettingRow>()
        val sections = pages[index].build().let { if (index == 0) listOfNotNull(setupSection()) + it else it }
            .filter { it.rows.isNotEmpty() }
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
        rows.firstOrNull()?.let { it.nextFocusUpId = tabs.id }
        rows.lastOrNull()?.let { it.nextFocusDownId = it.id }
        focusableRows = rows

        if (animate) scroll.scrollTo(0, 0)
        muteSystemSounds(content)
        // Moving through the tabs (a remote): the focus stays on them; otherwise the page's row takes it
        if (!tabs.hasFocus()) rows.getOrNull(focusIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0)))?.requestFocus()
        rebuilding = false
        // Keyboard up: the new page's row sits above it too (once the page has its size)
        if (settings.showPreview && !sliding) scroll.post { placeFocused(animate = false) }

        if (animate) {
            content.alpha = 0f
            // The new page slides in from the side you moved towards
            content.translationX = px(12f * slideFrom).toFloat()
            content.animate().alpha(1f).translationX(0f).setDuration(Design.MOTION_PAGE)
                .setInterpolator(Design.EASE_OUT).start()
        }
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
        // On the Device tab the words under the picture need room too, so a tall picture stays smaller there
        val device = index == deviceIndex
        guidePicture.maxHeightDp = if (beside) (if (device) PICTURE_DEVICE_DP else PICTURE_WIDE_DP) else PICTURE_NARROW_DP
        guidePicture.tallBoost = !device
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
        guidePicture.accent = settings.accent
        guidePicture.show(controls.kind, settings.buttonStyle, controls.sticks)
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
        return fit(rect, top = px(TabBar.FOLDED_DP + EDGE_FADE_DP), bottom = scroll.height - sheetFullHeight(), maxScroll = maxScroll)
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

    private fun togglePreview() {
        val show = !settings.showPreview
        settings = settings.copy(showPreview = show)
        settings.save(this)
        refreshControlBar()
        if (show) feedback.on() else feedback.off()

        val full = sheetFullHeight()
        sheet.visibility = View.VISIBLE
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
    private fun fit(rect: Rect, top: Int, bottom: Int, maxScroll: Int): Int {
        val target = if (rect.height() <= bottom - top) rect.centerY() - (top + bottom) / 2 else rect.bottom - bottom
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
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (first) close()
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
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (testing && testKey(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN &&
            focusableRows.firstOrNull()?.hasFocus() == true) {
            tabs.requestFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
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
        val custom = settings.accentIndex < 0
        // The custom colour: three sliders, shown once "Custom" is picked (the presets stay one simple row).
        // Each slider's track shows the colours it makes with the other two as they are now.
        fun colour(h: Int = settings.customHue, st: Int = settings.customStrength, b: Int = settings.customBrightness) =
            Palette.customAccent(h, st, b)
        val customRows = mutableListOf<ColourSliderRow>()
        fun refreshTracks() = customRows.forEach { it.slider.invalidate() }
        swatches = SwatchRow(this, "Highlight colour", "The glow that follows your selection, also used all over Settings",
            Palette.accents.map { it.second }, settings.accentIndex) {
            customRows.forEach { r -> r.setActive(false) }
            update(settings.copy(accentIndex = it))
            // Leaving the custom colour: its Strength and Brightness rows go away again
            if (custom) showPage(currentPage, focusIndex = focusableRows.indexOfFirst { r -> r.hasFocus() }.coerceAtLeast(0), animate = false)
        }
        val hueRow = ColourSliderRow(this, "Custom colour", "Any colour you like. Left and right move around the colour wheel",
            settings.customHue, 359, 10, active = custom, colourAt = { colour(h = it) }) {
            swatches.clearSelection()
            update(settings.copy(accentIndex = -1, customHue = it)); refreshTracks()
            if (!custom) showPage(currentPage, focusIndex = focusableRows.indexOfFirst { r -> r.hasFocus() }.coerceAtLeast(0), animate = false)
        }
        customRows += hueRow
        if (custom) {
            customRows += ColourSliderRow(this, "Strength", "Soft to vivid", settings.customStrength, 100, 5, active = true,
                colourAt = { colour(st = it) }) { update(settings.copy(customStrength = it)); refreshTracks() }
            customRows += ColourSliderRow(this, "Brightness", "Deeper to lighter. Always light enough to read", settings.customBrightness, 100, 5,
                active = true, colourAt = { colour(b = it) }) { update(settings.copy(customBrightness = it)); refreshTracks() }
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
        Section("Colour", listOf<SettingRow>(swatches) + customRows),
        Section("Keys", listOfNotNull(
            ChoiceRow(this, "Keyboard size", say("Also on the keyboard: press {L3}", "How big the keys are"),
                listOf("Normal", "Large", "Largest"), settings.sizeLevel.coerceIn(0, 2)) {
                update(settings.copy(sizeLevel = it))
            },
            ChoiceRow(this, "Key layout", "Grid lines keys up in straight columns, so up and down never guess",
                KeyLayout.values().map { it.label }, settings.layout.ordinal) {
                update(settings.copy(layout = KeyLayout.values()[it]))
            },
            ToggleRow(this, "Number row", "A slim row of 1 to 0 above the letters",
                settings.numberRow) {
                update(settings.copy(numberRow = it))
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

    /** The words for a setting, by what you hold: a controller with sticks, one without, or a remote. */
    private fun say(sticks: String, noSticks: String = sticks, remote: String = noSticks) =
        if (controls.remote) remote else if (controls.sticks) sticks else noSticks

    private fun typingPage() = listOf(
        Section("Languages", listOf(
            StepperRow(this, "Language", "Italian, Dutch, Polish and more type on English",
                Language.values().map { it.label }, settings.language1.ordinal) {
                update(settings.copy(language1 = Language.values()[it], activeLanguage = 0))
            },
            StepperRow(this, "Second language", say("Hold {R2} and press {L2} to switch while typing",
                    remote = "Optional. Switching between languages needs a controller"),
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
        Section("Help while typing", listOf(
            ToggleRow(this, "Word suggestions", say(
                    "Words you might mean show in the tool strip, most likely first. Press the right stick in {R3} to take it, or move up and pick one",
                    "Words you might mean show in the tool strip, most likely first. Move up to one and press {A} to take it",
                    "Words you might mean show in the tool strip, most likely first. Move up to one and press OK to take it"),
                settings.wordSuggestions) {
                update(settings.copy(wordSuggestions = it))
            },
            ToggleRow(this, "Learn my words", "Words you use often come first. New ones, like names, join after you use them in three different places. On this device only",
                settings.learnWords) {
                update(settings.copy(learnWords = it))
            },
            UndoActionRow(this, "Forget my words", "Clears the words Inlay has learned from you",
                "Your words are forgotten", "Changed your mind? Press it again to bring them back") {
                UserWords.forgetWithUndo(this)
            },
            ToggleRow(this, "Hold for accents", say("Hold {A} on a letter for é, ñ, ü and more. Let go and they stay open: {A} types, {B} cancels",
                    remote = "Hold OK on a letter for é, ñ, ü and more. Let go and they stay open: OK types, Back cancels"),
                settings.holdAccents) {
                update(settings.copy(holdAccents = it))
            },
            ToggleRow(this, "Automatic capitals", "A capital to start each sentence, and I on its own. Never in web addresses, email or passwords",
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
            ToggleRow(this, "Smart keys", "Adds / and .com for web addresses, @ for email, and a number pad for numbers",
                settings.smartKeys) {
                update(settings.copy(smartKeys = it))
            }
        )),
        Section("Shortcuts", listOfNotNull(
            ToggleRow(this, "Select all key", say("When off, double-press {SELECTx2} instead", remote = "A key in the top row that selects everything"),
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
                "Your last ${Layouts.MAX_CLIP_CARDS} copies, on this device only. Passwords are never kept.",
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
            ToggleRow(this, "Play in silent mode",
                // Muted right now by silent mode: say so here, so no sound never looks like a fault
                if (!settings.soundInSilent && Feedback.silentNow(this)) "Your device is on silent, so the sounds are muted. Turn this on to hear them"
                else "When off, sounds mute with your silent mode",
                settings.soundInSilent) {
                update(settings.copy(soundInSilent = it))
            },
            ActionRow(this, "Try it", "Every sound at its real volume: move, type, space, delete, triggers, caps lock, select all, enter") {
                playSequence(listOf(
                    feedback::move, feedback::key, feedback::space, feedback::delete, feedback::on,
                    feedback::off, feedback::lock, feedback::selectAll, feedback::confirm
                ))
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
        if (settings.holdAccents) typing.add(5, GuideRow(this, "Accents · hold, then choose", "hold {A}"))
        if (settings.wordSuggestions) typing += if (controls.sticks) GuideRow(this, "Take the first suggested word · press the stick in", "{R3}")
            else GuideRow(this, "Take a suggested word", "up, then {A}")
        val editing = mutableListOf(
            GuideRow(this, "Move the text cursor", "{L1} {R1}"),
            GuideRow(this, "Jump a whole word", "{R2} + {L1} {R1}"),
            GuideRow(this, "Select text", "{SELECT}"),
            GuideRow(this, "Select everything", "{SELECTx2}")
        )
        if (settings.rightStick && controls.sticks) {
            editing.add(1, GuideRow(this, "Move the text cursor · push further to go faster", "{R3} left or right"))
            editing.add(3, GuideRow(this, "Jump a whole word with the stick", "{R2} + {R3} left or right"))
            editing += GuideRow(this, "Go to the start or end · a line in long text", "{R3} up or down")
        }
        if (settings.undoCombo) editing += GuideRow(this, "Undo", "{R2} + {X}")
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
            Section("Typing", typing),
            Section("Editing", editing),
            Section("Keyboard", listOf(
                GuideRow(this, "Close the keyboard · leave select mode or the clipboard", "{B}"),
            ) + if (controls.sticks) listOf(GuideRow(this, "Keyboard size · Normal, Large, Largest", "{L3}")) else emptyList())
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
                if (c.remote) null else ChoiceRow(this, "Button icons", "Match the labels on your buttons",
                    ButtonStyle.values().map { it.label }, settings.buttonStyle.ordinal) {
                    update(settings.copy(buttonStyle = ButtonStyle.values()[it]))
                },
                ActionRow(this, "Try your buttons", if (c.remote) "Press OK, Back or the ring: they light up on the picture. Hold Back to stop"
                    else "Press any button: it lights up on the picture. Hold {B} to stop") { startButtonTest() }
            )),
            Section("Controls", (if (c.sticks) listOf(
            SliderRow(this, "Stick speed", "How fast the highlight moves while you hold the left stick all the way. A lighter push goes more gently",
                0, 4, settings.stickSpeed, { STICK_LABELS[it] }) {
                update(settings.copy(stickSpeed = it))
            },
            ToggleRow(this, "Right stick moves the text cursor", "Push {R3} sideways to move, further is faster. Up and down go a line, or to the start or end. Grows a selection too",
                settings.rightStick) {
                update(settings.copy(rightStick = it))
            }
            ) else emptyList()) + listOf(
            ChoiceRow(this, "TV mode", "A compact floating keyboard with a big line showing what you type. Automatic: when docked to a TV, or on Android TV",
                TvMode.values().map { it.label }, settings.tvMode.ordinal) {
                update(settings.copy(tvMode = TvMode.values()[it]))
            }
            )),
            // Button badges and combo tips are about a controller's buttons: a remote has none
            Section("Hints on the keys", if (c.remote) emptyList() else listOf(
            ToggleRow(this, "Button hints on keys", "Small {X} {Y} style badges in the key corners",
                settings.showHints) {
                update(settings.copy(showHints = it))
            },
            ToggleRow(this, "Combo tips", "Shows what you can do while holding {R2}",
                settings.comboHints) {
                update(settings.copy(comboHints = it))
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
            "Everything works with the ring, OK and Back. Button hints are hidden."
        c.kind == DeviceKind.VERTICAL && !c.sticks -> "Set up for a vertical handheld" to
            "Your button icons on the keys. Without sticks, pick suggested words with the D-pad."
        else -> "Set up for ${when (c.kind) { DeviceKind.CONTROLLER -> "a controller"; DeviceKind.WIDE -> "a handheld"; else -> "a vertical handheld with sticks" }}" to
            "Your button icons on the keys. The right stick moves the text cursor and picks words."
    }

    // ---- Try your buttons: every press lights up on the picture -------------------------
    private var testing = false
    private var backDownAt = 0L
    private val stopTest = Runnable { endButtonTest() }

    private fun startButtonTest() {
        testing = true
        guidePicture.held = emptySet(); guidePicture.holding = emptySet(); guidePicture.stickSway = emptyMap(); guidePicture.stickPush = emptyMap()
        refreshControlBar()
        scroll.removeCallbacks(stopTest)
        scroll.postDelayed(stopTest, BUTTON_TEST_IDLE_MS)
    }

    private fun endButtonTest() {
        if (!testing) return
        testing = false
        guidePicture.held = emptySet(); guidePicture.holding = emptySet(); guidePicture.stickSway = emptyMap(); guidePicture.stickPush = emptyMap()
        scroll.removeCallbacks(stopTest)
        refreshControlBar()
    }

    /** While testing, every button only lights up. Holding B (Back) stops; so does a quiet spell. */
    private fun testKey(event: KeyEvent): Boolean {
        val key = DevicePicture.keyFor(event.keyCode) ?: return false
        val back = event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK
        scroll.removeCallbacks(stopTest)
        scroll.postDelayed(stopTest, BUTTON_TEST_IDLE_MS)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                guidePicture.held = guidePicture.held + key
                guidePicture.holding = guidePicture.holding + key
                if (back) backDownAt = event.eventTime
            }
            if (back && event.eventTime - backDownAt >= BUTTON_TEST_STOP_MS) endButtonTest()
        } else if (event.action == KeyEvent.ACTION_UP) {
            guidePicture.held = guidePicture.held - key
            guidePicture.holding = guidePicture.holding - key
            if (back && event.eventTime - backDownAt >= BUTTON_TEST_STOP_MS) endButtonTest()
        }
        return true
    }

    /** Triggers and D-pads that report as axes light up too. */
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (!testing) return super.dispatchGenericMotionEvent(event)
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
        guidePicture.stickPush = pushes
        val clicked = guidePicture.held.filter { it == "LS" || it == "RS" }.filter { it !in pushes }.filter { it in guidePicture.holding }
        guidePicture.held = guidePicture.held - axisKeys - setOf("LS", "RS") + axes + pushes.keys + clicked
        guidePicture.holding = guidePicture.holding - axisKeys + axes.filter { it == "LT" || it == "RT" }
        return true
    }

    /** For the consistency checks: the Button guide's rows (what, buttons) for the device picked. */
    internal fun guideCheck(): List<Pair<String, String>> = guidePage().flatMap { it.rows }.filterIsInstance<GuideRow>().map { it.title to it.buttons }

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
            InfoRow(this, "Fonts: Atkinson Hyperlegible Next and Inter", "Open Font Licence"),
            InfoRow(this, "PlayStation drawing: traced from \"Dualshock 4 Layout.svg\" by Tokyoship", "CC BY 3.0 · controller names belong to their makers")
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
        const val PICTURE_PANEL_DP = 300f
        /** The device picture sits beside the page only when the page keeps at least ~380 dp (DESIGN_GUIDE: narrow under 600 dp). */
        const val SIDE_PICTURE_MIN_DP = 600
        const val SHEET_FADE_DP = 12f
        const val EDGE_FADE_DP = 16f        // the page's soft top and bottom edges
        const val BUTTON_TEST_IDLE_MS = 8000L    // the button test ends on its own after this long without a press
        const val BUTTON_TEST_STOP_MS = 700L     // holding B (Back) this long ends it
        const val TOP_SNAP_DP = 72f         // closer to the top than this, a scroll goes right to it
        const val PICTURE_WIDE_DP = 300f
        const val PICTURE_DEVICE_DP = 165f    // the Device tab's picture, with its caption below
        const val PICTURE_NARROW_DP = 150f
        const val CLOSE_DELAY_MS = 140L
        /** The project's web page (e.g. on GitHub). Leave empty to hide the "Source code" row. */
        const val SOURCE_URL = "https://github.com/dakingeman/inlay"
        val STICK_LABELS = listOf("Slowest", "Slow", "Medium", "Fast", "Fastest")
    }
}
