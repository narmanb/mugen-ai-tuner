package com.narmanb.mugenaituner.core

import java.security.MessageDigest

data class FileFingerprint(
    val path: String,
    val sha256: String,
)

data class CharacterFingerprint(
    val files: List<FileFingerprint>,
) {
    fun differsFrom(other: CharacterFingerprint): Boolean = files != other.files
}

object CharacterFingerprinter {
    fun fingerprint(files: List<SourceFile>): CharacterFingerprint = CharacterFingerprint(
        files = files
            .sortedBy { it.path.lowercase() }
            .map { file ->
                FileFingerprint(
                    path = file.path,
                    sha256 = sha256(file.content),
                )
            },
    )

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
