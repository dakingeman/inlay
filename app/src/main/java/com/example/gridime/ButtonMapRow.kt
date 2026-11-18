package com.example.gridime

import android.content.Context
import android.widget.TextView

/**
 * One function in Button mapping (ButtonRemap.kt): its name, what it does, and the button it's on now as a badge.
 * A starts listening ([listen]): the next button pressed gets the function (SettingsActivity does the rest).
 */
class ButtonMapRow(
    context: Context,
    /** The function, as the code of the button that has it out of the box. */
    val job: Int,
    /** The button that does it now. */
    private val button: Int,
    private val onPress: (ButtonMapRow) -> Unit
) : SettingRow(context, ButtonRemap.rowTitle(job), detail(job)) {

    private val title = ButtonRemap.rowTitle(job)

    init { showButton() }

    private fun showButton() = setControl(BadgeLineView(context, HintText.parse(ButtonRemap.badge(button)), buttonStyle,
        Design.TYPE_CAPTION, SettingsStyle.textSecondary, badgeScale = Design.BADGE_ROW))

    /** Waiting for a button (true), or back to showing the one it's on (false). */
    fun listen(on: Boolean) {
        if (on) {
            setTexts(title, "Press the button for ${ButtonRemap.name(job).lowercase()}. The D-pad cancels")
            setControl(TextView(context).apply {
                text = "Press a button"; textSize = Design.TYPE_ROW; typeface = SettingsStyle.medium
                setTextColor(SettingsStyle.textPrimary)
            })
        } else {
            setTexts(title, detail(job))
            showButton()
        }
    }

    override fun activate() {
        sounds?.confirm()
        onPress(this)
    }

    private companion object {
        fun detail(job: Int) = ButtonRemap.DETAILS.getOrNull(ButtonRemap.BUTTONS.indexOf(job)) ?: ""
    }
}
