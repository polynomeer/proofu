package com.proofu.api.web

import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Keyset cursor for lists ordered by (timestamp desc, id desc): base64url("instant|id").
 *
 * The timestamp is carried whole rather than bucketed into a day: `(ts, id) < (cursor)` matches
 * a plain `(workspace_id, ts desc, id desc)` index, and there is no time zone in the comparison
 * to disagree with the database session (see docs/api/conventions.md).
 */
data class InstantIdCursor(
    val at: Instant,
    val id: UUID,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$at|$id".toByteArray())

    companion object {
        fun decode(raw: String): InstantIdCursor =
            runCatching {
                val (at, id) = String(Base64.getUrlDecoder().decode(raw)).split('|', limit = 2)
                InstantIdCursor(Instant.parse(at), UUID.fromString(id))
            }.getOrElse { throw InvalidRequestException("cursor is not valid") }
    }
}
