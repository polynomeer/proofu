package com.proofu.api.career

import com.proofu.api.web.InvalidRequestException
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/** Opaque keyset cursor: base64url("startDate|id"). Stable under inserts, unlike offsets. */
data class CareerEntryCursor(
    val startDate: LocalDate,
    val id: UUID,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$startDate|$id".toByteArray())

    companion object {
        fun decode(raw: String): CareerEntryCursor =
            runCatching {
                val (date, id) = String(Base64.getUrlDecoder().decode(raw)).split('|', limit = 2)
                CareerEntryCursor(LocalDate.parse(date), UUID.fromString(id))
            }.getOrElse { throw InvalidRequestException("cursor is not valid") }

        fun of(entity: CareerEntryEntity) = CareerEntryCursor(entity.startDate, entity.id)
    }
}
