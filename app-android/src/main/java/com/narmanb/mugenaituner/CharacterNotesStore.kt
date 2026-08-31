package com.narmanb.mugenaituner

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/** Local per-character notes. Notes never alter MUGEN/IKEMEN character files. */
object CharacterNotesStore {
    private const val preferencesName = "mugen_ai_tuner_character_notes"
    private const val maxNoteLength = 8000

    fun load(context: Context, treeUri: Uri, characterName: String): String {
        return preferences(context).getString(key(treeUri, characterName), "").orEmpty()
    }

    fun save(context: Context, treeUri: Uri, characterName: String, note: String) {
        val normalized = note.replace("\u0000", "").take(maxNoteLength)
        preferences(context).edit()
            .putString(key(treeUri, characterName), normalized)
            .apply()
    }

    fun clear(context: Context, treeUri: Uri, characterName: String) {
        preferences(context).edit().remove(key(treeUri, characterName)).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun key(treeUri: Uri, characterName: String): String {
        val identity = "${treeUri}|${characterName.trim().lowercase()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
