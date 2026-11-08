package com.example.gridime

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/**
 * Voice typing (1.3): Inlay never listens itself. The microphone hands over to a voice app that offers itself to other
 * keyboards (a "voice" input method, like FUTO Voice Input), the way HeliBoard and FlorisBoard do. Nothing added to
 * Inlay's size or memory, and no microphone permission.
 */
object VoiceInput {
    /** Where to get one: FUTO Voice Input works fully on the device, free to use (pay if you like it). */
    const val GET_URL = "https://voiceinput.futo.tech"

    /** A voice app that's switched on in Android's keyboard list: its id and voice subtype, or null. */
    fun ready(context: Context): Pair<String, InputMethodSubtype?>? {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        return runCatching {
            imm.shortcutInputMethodsAndSubtypes.forEach { (info, subtypes) ->
                if (info.packageName == context.packageName) return@forEach
                val voice = subtypes.firstOrNull { it.mode == "voice" }
                if (voice != null || subtypes.isEmpty()) return@runCatching info.id to voice
            }
            imm.enabledInputMethodList.firstNotNullOfOrNull { info -> voiceOf(info, context)?.let { info.id to it } }
        }.getOrNull()
    }

    /** A voice app that's installed but not switched on yet (Android's keyboard list), or null. */
    fun installedOnly(context: Context): InputMethodInfo? {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        return runCatching {
            val on = imm.enabledInputMethodList.map { it.id }.toSet()
            imm.inputMethodList.firstOrNull { it.id !in on && voiceOf(it, context) != null }
        }.getOrNull()
    }

    /** The name to show for the voice app in use (for Settings), or null. */
    fun readyName(context: Context): String? {
        val id = ready(context)?.first ?: return null
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        return runCatching { imm.inputMethodList.firstOrNull { it.id == id }?.loadLabel(context.packageManager)?.toString() }.getOrNull()
    }

    private fun voiceOf(info: InputMethodInfo, context: Context): InputMethodSubtype? {
        if (info.packageName == context.packageName) return null
        for (i in 0 until info.subtypeCount) info.getSubtypeAt(i).let { if (it.mode == "voice") return it }
        return null
    }
}
