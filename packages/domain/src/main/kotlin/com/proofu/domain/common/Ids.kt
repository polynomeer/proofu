package com.proofu.domain.common

import java.util.UUID

/** Externally visible identifiers are UUIDv7 (time-ordered, non-guessable). */
@JvmInline value class WorkspaceId(
    val value: UUID,
)

@JvmInline value class UserId(
    val value: UUID,
)

@JvmInline value class CareerEntryId(
    val value: UUID,
)

@JvmInline value class ProjectId(
    val value: UUID,
)

@JvmInline value class AchievementId(
    val value: UUID,
)

@JvmInline value class SkillId(
    val value: UUID,
)

@JvmInline value class CapabilityId(
    val value: UUID,
)

@JvmInline value class ClaimId(
    val value: UUID,
)

@JvmInline value class EvidenceId(
    val value: UUID,
)

@JvmInline value class JobPostingId(
    val value: UUID,
)

@JvmInline value class JobPostingSnapshotId(
    val value: UUID,
)

@JvmInline value class RequirementId(
    val value: UUID,
)

@JvmInline value class ApplicationId(
    val value: UUID,
)

@JvmInline value class DocumentId(
    val value: UUID,
)

@JvmInline value class DocumentVersionId(
    val value: UUID,
)

@JvmInline value class SubmissionSnapshotId(
    val value: UUID,
)

@JvmInline value class ReviewId(
    val value: UUID,
)

@JvmInline value class JobId(
    val value: UUID,
)
