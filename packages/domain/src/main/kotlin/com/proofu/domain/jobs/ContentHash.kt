package com.proofu.domain.jobs

import java.security.MessageDigest

/**
 * Deterministic hash of posting text so re-imports of the same content are idempotent and
 * any real change produces a new snapshot. Whitespace-only differences (line endings,
 * trailing spaces, surrounding blank lines) do not count as change.
 */
object ContentHash {
    fun normalize(text: String): String =
        text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()

    /** Lowercase SHA-256 hex of the normalised text. */
    fun of(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(normalize(text).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
