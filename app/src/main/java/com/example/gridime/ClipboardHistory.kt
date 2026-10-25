package com.example.gridime

import android.content.Context
import org.json.JSONArray

/**
 * The last few copied texts, newest first, stored only on this device.
 * Kept in its own small file, read fresh each time, because Settings (which can
 * clear it) runs in a separate process from the keyboard.
 */
class ClipboardHistory(private val context: Context) {

    private val items = ArrayList<String>()

    val all: List<String> get() = items

    fun reload() {
        items.clear()
        val raw = SmallFile.read(context, FILE) ?: migrateFromOldStore() ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) items.add(array.getString(i))
        }.onFailure { items.clear() }
    }

    fun add(text: String) {
        if (text.isBlank()) return
        reload()                                   // pick up a clear made in Settings
        val item = text.take(MAX_LENGTH)
        items.remove(item)
        items.add(0, item)
        while (items.size > Layouts.MAX_CLIP_CARDS) items.removeAt(items.lastIndex)
        save()
    }

    fun removeAt(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        save()
    }

    private fun save() = SmallFile.write(context, FILE, JSONArray(items).toString())

    /** Version 1.0 kept the history in Android's settings store. Move it over once. */
    private fun migrateFromOldStore(): String? {
        val prefs = context.getSharedPreferences(KeyboardSettings.PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_OLD, null) ?: "[]"
        SmallFile.write(context, FILE, raw)
        prefs.edit().remove(KEY_OLD).apply()
        return raw
    }

    companion object {
        private const val MAX_LENGTH = 5000
        private const val FILE = "clipboard.json"
        private const val KEY_OLD = "clip_history"

        /**
         * Empties the history and returns a way to put it back, or null if it was already empty.
         * Writes an empty list (not "no file") so nothing old comes back.
         */
        fun clearWithUndo(context: Context): (() -> Unit)? {
            val before = SmallFile.read(context, FILE) ?: return null.also { clear(context) }
            if (before.trim() == "[]") return null
            clear(context)
            return { SmallFile.write(context, FILE, before) }
        }

        fun clear(context: Context) {
            SmallFile.write(context, FILE, "[]")
            context.getSharedPreferences(KeyboardSettings.PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_OLD).apply()
        }
    }
}
