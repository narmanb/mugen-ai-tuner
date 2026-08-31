package com.narmanb.mugenaituner

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DecodedCharacterText(
    val text: String,
    val charset: Charset,
)

/**
 * MUGEN characters are frequently distributed with old ANSI-family text files. Analysis only
 * needs the ASCII syntax, but writing must not silently corrupt non-ASCII comments or metadata.
 *
 * UTF-8/UTF-16 are decoded normally. Unknown legacy byte streams use ISO-8859-1 as a byte-for-byte
 * reversible transport encoding: ASCII MUGEN syntax remains readable and every untouched byte is
 * reproduced exactly when the file is written back.
 */
object CharacterTextCodec {
    fun decode(bytes: ByteArray): DecodedCharacterText {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return DecodedCharacterText(String(bytes, StandardCharsets.UTF_16LE), StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return DecodedCharacterText(String(bytes, StandardCharsets.UTF_16BE), StandardCharsets.UTF_16BE)
        }

        if (isStrictUtf8(bytes)) {
            return DecodedCharacterText(String(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        }

        return DecodedCharacterText(String(bytes, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1)
    }

    fun encode(text: String, charset: Charset): ByteArray = text.toByteArray(charset)

    private fun isStrictUtf8(bytes: ByteArray): Boolean {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }
}
