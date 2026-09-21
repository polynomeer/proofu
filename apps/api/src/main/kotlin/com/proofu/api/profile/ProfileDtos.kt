package com.proofu.api.profile

import com.proofu.domain.common.UserId
import com.proofu.domain.identity.Profile
import com.proofu.domain.identity.ProfileLink
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ProfileLinkDto(
    @field:NotBlank @field:Size(max = 80) val label: String?,
    @field:NotBlank @field:Size(max = 2048) val url: String?,
)

data class ProfileRequest(
    @field:NotBlank @field:Size(max = 120) val fullName: String?,
    @field:Size(max = 200) val headline: String? = null,
    @field:Size(max = 320) val email: String? = null,
    @field:Size(max = 40) val phone: String? = null,
    @field:Size(max = 120) val location: String? = null,
    @field:Size(max = 5) @field:Valid val links: List<ProfileLinkDto> = emptyList(),
    val version: Long? = null,
) {
    fun toDomain(
        userId: UserId,
        version: Long,
    ) = Profile(
        userId = userId,
        fullName = requireNotNull(fullName).trim(),
        headline = headline?.trim()?.ifEmpty { null },
        email = email?.trim()?.ifEmpty { null },
        phone = phone?.trim()?.ifEmpty { null },
        location = location?.trim()?.ifEmpty { null },
        links = links.map { ProfileLink(requireNotNull(it.label).trim(), requireNotNull(it.url).trim()) },
        version = version,
    )
}

data class ProfileResponse(
    val userId: UUID,
    val fullName: String,
    val headline: String?,
    val email: String?,
    val phone: String?,
    val location: String?,
    val links: List<ProfileLinkDto>,
    val contactLine: String,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            p: Profile,
            createdAt: Instant,
            updatedAt: Instant,
        ) = ProfileResponse(
            userId = p.userId.value,
            fullName = p.fullName,
            headline = p.headline,
            email = p.email,
            phone = p.phone,
            location = p.location,
            links = p.links.map { ProfileLinkDto(it.label, it.url) },
            contactLine = p.contactLine,
            version = p.version,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
