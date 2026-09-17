package com.proofu.domain.career

import com.proofu.domain.common.CareerEntryId
import com.proofu.domain.common.Revision
import com.proofu.domain.common.Visibility
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.common.domainRequire
import java.time.LocalDate

enum class CareerEntryStatus {
    ACTIVE,
    ARCHIVED,
    DELETED,
}

data class CareerEntry(
    val id: CareerEntryId,
    val workspaceId: WorkspaceId,
    val type: CareerEntryType,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val organization: String? = null,
    val location: String? = null,
    val description: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
    val status: CareerEntryStatus = CareerEntryStatus.ACTIVE,
    val revision: Revision = Revision.INITIAL,
) {
    init {
        domainRequire(title.isNotBlank()) { "career entry title must not be blank" }
        domainRequire(endDate == null || !endDate.isBefore(startDate)) {
            "career entry end date $endDate must not be before start date $startDate"
        }
    }

    val isOngoing: Boolean get() = endDate == null
}
