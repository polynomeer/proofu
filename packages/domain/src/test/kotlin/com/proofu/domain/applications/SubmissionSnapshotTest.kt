package com.proofu.domain.applications

import com.proofu.domain.common.ApplicationId
import com.proofu.domain.common.ClaimId
import com.proofu.domain.common.DocumentVersionId
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.Fixtures
import com.proofu.domain.common.JobPostingSnapshotId
import com.proofu.domain.common.SubmissionSnapshotId
import com.proofu.domain.common.UnapprovedBlocksInExport
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.documents.Certainty
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.GeneratedOutput
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SubmissionSnapshotTest {
    private val now = Instant.parse("2026-09-21T09:00:00Z")
    private val application =
        Application(
            id = ApplicationId(Fixtures.uuid(1)),
            workspaceId = WorkspaceId(Fixtures.uuid(2)),
            snapshotId = JobPostingSnapshotId(Fixtures.uuid(3)),
            company = "ABC",
            roleTitle = "PM",
            status = ApplicationStatus.PREPARING,
        )
    private val versionId = DocumentVersionId(Fixtures.uuid(4))
    private val supported =
        GeneratedBlock(
            "experience-1",
            "sentence",
            claimRefs = setOf(ClaimId(Fixtures.uuid(5))),
            certainty = Certainty.SUPPORTED,
        )

    private fun freeze(
        app: Application = application,
        content: GeneratedOutput = GeneratedOutput(listOf(supported)),
        documentApplicationId: ApplicationId = application.id,
        submittedAt: Instant = now,
    ) = SubmissionSnapshot.freeze(
        id = SubmissionSnapshotId(Fixtures.uuid(9)),
        application = app,
        documentVersionId = versionId,
        documentApplicationId = documentApplicationId,
        content = content,
        contentJson = """{"blocks":[]}""",
        postingContentHash = "abc",
        submittedAt = submittedAt,
        now = now,
    )

    @Test
    fun `freezes the posting snapshot of the application and a hash over the exact content`() {
        val snapshot = freeze()
        assertThat(snapshot.postingSnapshotId).isEqualTo(application.snapshotId)
        assertThat(
            snapshot.hash,
        ).hasSize(64).isEqualTo(SubmissionSnapshot.hashOf(versionId, """{"blocks":[]}""", "abc"))
        assertThat(SubmissionSnapshot.hashOf(versionId, """{"blocks":[]} """, "abc")).isNotEqualTo(snapshot.hash)
    }

    @Test
    fun `unapproved inferred or unsupported blocks block the submission`() {
        val content =
            GeneratedOutput(
                listOf(
                    supported,
                    GeneratedBlock("motivation-1", "x", certainty = Certainty.UNSUPPORTED),
                    GeneratedBlock("motivation-2", "y", certainty = Certainty.INFERRED, approvedByUser = true),
                ),
            )
        assertThatThrownBy { freeze(content = content) }
            .isInstanceOf(UnapprovedBlocksInExport::class.java)
            .extracting("blockIds")
            .isEqualTo(listOf("motivation-1"))
        assertThat(freeze(content = GeneratedOutput(listOf(supported, content.blocks[2]))).hash).isNotBlank()
    }

    @Test
    fun `only applications that still accept a submission, and only their own documents`() {
        assertThat(freeze(app = application.copy(status = ApplicationStatus.INTERESTED))).isNotNull
        assertThat(freeze(app = application.copy(status = ApplicationStatus.SUBMITTED))).isNotNull
        assertThatThrownBy { freeze(app = application.copy(status = ApplicationStatus.DOCUMENT_REJECTED)) }
            .isInstanceOf(DomainRuleViolation::class.java)
        assertThatThrownBy { freeze(documentApplicationId = ApplicationId(Fixtures.uuid(77))) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }

    @Test
    fun `submission time may not be in the future beyond clock skew`() {
        assertThat(freeze(submittedAt = now.plus(Duration.ofMinutes(4)))).isNotNull
        assertThatThrownBy { freeze(submittedAt = now.plus(Duration.ofMinutes(6))) }
            .isInstanceOf(DomainRuleViolation::class.java)
    }
}
