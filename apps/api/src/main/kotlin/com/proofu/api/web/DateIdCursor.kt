package com.proofu.api.web

import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/**
 * Opaque keyset cursor for lists ordered by (date desc, id desc): base64url("date|id").
 * Stable under concurrent inserts, unlike offsets.
 */
data class DateIdCursor(
    val date: LocalDate,
    val id: UUID,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$date|$id".toByteArray())

    companion object {
        fun decode(raw: String): DateIdCursor =
            runCatching {
                val (date, id) = String(Base64.getUrlDecoder().decode(raw)).split('|', limit = 2)
                DateIdCursor(LocalDate.parse(date), UUID.fromString(id))
            }.getOrElse { throw InvalidRequestException("cursor is not valid") }
    }
}
