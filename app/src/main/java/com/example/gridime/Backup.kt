package com.example.gridime

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Back up and restore (1.3): your settings and the words Inlay has learnt, in one small file you keep wherever you
 * like (Android's own file picker: Downloads, a memory card, a cloud folder). Nothing leaves the device by itself,
 * and the clipboard history is never in it (copies can be passwords).
 * The file: {"inlay": 1, "settings": {…}, "words": {…}}. A file from a newer Inlay still restores what it knows.
 */
object Backup {
    const val VERSION = 1
    const val MIME = "application/json"
    private const val SETTINGS = "settings.json"
    private const val WORDS = "my_words.json"

    fun suggestedName(): String = "inlay-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()) + ".json"

    /** Everything worth keeping, as the file's text. */
    fun make(context: Context): String {
        KeyboardSettings.load(context).save(context)                 // the newest settings on disk first
        val o = JSONObject().put("inlay", VERSION).put("made", System.currentTimeMillis())
        SmallFile.read(context, SETTINGS)?.let { runCatching { o.put("settings", JSONObject(it)) } }
        SmallFile.read(context, WORDS)?.let { runCatching { o.put("words", JSONObject(it)) } }
        return o.toString(2)
    }

    fun write(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(make(context).toByteArray()) }; true
    }.getOrDefault(false)

    /** What a restore did: the settings back, the words back, or why not. */
    enum class Result { RESTORED, NOT_A_BACKUP, UNREADABLE }

    fun restore(context: Context, text: String): Result {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return Result.UNREADABLE
        if (!o.has("inlay") || !o.has("settings")) return Result.NOT_A_BACKUP
        val settings = o.optJSONObject("settings") ?: return Result.NOT_A_BACKUP
        SmallFile.write(context, SETTINGS, settings.toString())
        o.optJSONObject("words")?.let { SmallFile.write(context, WORDS, it.toString()) }
        KeyboardSettings.load(context).save(context)                 // read back through the settings, so it's tidy
        return Result.RESTORED
    }

    fun read(context: Context, uri: Uri): Result = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        restore(context, text)
    }.getOrDefault(Result.UNREADABLE)
}
