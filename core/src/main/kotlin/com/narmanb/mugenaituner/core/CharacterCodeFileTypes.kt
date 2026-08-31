package com.narmanb.mugenaituner.core

/** Text-based character code formats that MUGEN/IKEMEN can load as commands, constants, or states. */
object CharacterCodeFileTypes {
    val supportedExtensions: Set<String> = setOf(
        "def",
        "cmd",
        "cns",
        "const",
        "st",
        "states",
        "zss",
        "cds",
    )

    fun isSupportedPath(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in supportedExtensions
}
