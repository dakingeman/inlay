package com.example.gridime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last few copied texts, newest first, stored only on this device. Pinned clips (1.3) sit first, in the order
 * they were pinned, and never drop off; new copies go under them. At most [MAX_PINS], so there's always room for new.
 * Kept in its own small file, read fresh each time, because Settings (which can
 * clear it) runs in a separate process from the keyboard.
 */
class ClipboardHistory(private val context: Context) {

    private val items = ArrayList<String>()
    private var pins = 0                          // the first [pins] items are pinned

    val all: List<String> get() = items
    /** How many of [all], from the top, are pinned. */
    val pinnedCount: Int get() = pins
    fun isPinned(index: Int) = index in 0 until pins

    fun reload() {
        items.clear(); pins = 0
        val raw = SmallFile.read(context, FILE) ?: migrateFromOldStore() ?: return
        runCatching {
            // 1.0–1.2: a plain list; 1.3: {"clips": [...], "pinned": n}
            val trimmed = raw.trim()
            val array = if (trimmed.startsWith("{")) JSONObject(trimmed).also { pins = it.optInt("pinned", 0) }.getJSONArray("clips") else JSONArray(trimmed)
            for (i in 0 until array.length()) items.add(array.getString(i))
            pins = pins.coerceIn(0, minOf(MAX_PINS, items.size))
        }.onFailure { items.clear(); pins = 0 }
    }

    fun add(text: String) {
        if (text.isBlank()) return
        reload()                                   // pick up a clear made in Settings
        val item = text.take(MAX_LENGTH)
        val at = items.indexOf(item)
        if (at in 0 until pins) return             // already pinned: it stays where it is
        if (at >= 0) items.removeAt(at)
        items.add(pins, item)                      // newest of the unpinned, under the pins
        while (items.size > Layouts.MAX_CLIP_CARDS) items.removeAt(items.lastIndex)
        save()
    }

    fun removeAt(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        if (index < pins) pins--
        save()
    }

    enum class Pin { PINNED, UNPINNED, FULL }
    /** Pins clip [index] (it moves to the end of the pinned ones) or unpins it (it goes just under them). */
    fun togglePin(index: Int): Pin? {
        reload()
        if (index !in items.indices) return null
        val item = items[index]
        return if (index < pins) {
            items.removeAt(index); pins--; items.add(pins, item); save(); Pin.UNPINNED
        } else if (pins >= MAX_PINS) Pin.FULL
        else { items.removeAt(index); items.add(pins, item); pins++; save(); Pin.PINNED }
    }

    private fun save() = SmallFile.write(context, FILE,
        if (pins == 0) JSONArray(items).toString() else JSONObject().put("clips", JSONArray(items)).put("pinned", pins).toString())

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
        /** Pinned clips at most: of the six places, two always stay for new copies. */
        const val MAX_PINS = 4
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
