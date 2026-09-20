package com.proofu.api.document

import com.proofu.api.application.ApplicationService
import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.documents.ProvenanceSourceType
import com.proofu.domain.documents.VersionAuthor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class DocumentService(
    private val repository: DocumentRepository,
    private val versions: DocumentVersionStore,
    private val applications: ApplicationService,
    private val jobs: JobService,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun listForApplication(
        workspace: WorkspaceContext,
        applicationId: UUID,
    ): DocumentList {
        applications.get(workspace, applicationId)
        val docs =
            repository.findAllByApplicationIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                applicationId,
                workspace.workspaceId.value,
            )
        val latest = versions.latestIds(docs.map { it.id })
        return DocumentList(docs.map { it.toResponse(latest[it.id]) })
    }

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        applicationId: UUID,
        request: CreateDocumentRequest,
    ): DocumentResponse {
        applications.get(workspace, applicationId)
        val entity =
            DocumentEntity(
                id = ids.next(),
                workspaceId = workspace.workspaceId.value,
                applicationId = applicationId,
                type = requireNotNull(request.type),
                title = requireNotNull(request.title).trim(),
                language = request.language?.trim()?.ifEmpty { null } ?: "ko",
                version = 1,
            )
        val response = repository.saveAndFlush(entity).toResponse(null)
        audit.record(workspace, "document.created", TARGET, entity.id, after = response)
        return response
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): DocumentDetailResponse {
        val entity = find(workspace, id)
        val latest = versions.latestId(entity.id)?.let { versions.find(it, workspace.workspaceId.value) }
        val template =
            latest?.let { DocumentTemplate.find(it.templateVersion, entity.type) }
                ?: DocumentTemplate.latest(entity.type)
        val pending =
            latest?.let { GeneratedOutput(it.blocks.map(DocumentBlockDto::toDomain)).blocksPendingApproval().size } ?: 0
        val base = entity.toResponse(latest?.let { it.id to it.createdBy })
        return DocumentDetailResponse(
            id = base.id,
            applicationId = base.applicationId,
            type = base.type,
            title = base.title,
            language = base.language,
            latestVersionId = base.latestVersionId,
            latestVersionCreatedBy = base.latestVersionCreatedBy,
            version = base.version,
            createdAt = base.createdAt,
            updatedAt = base.updatedAt,
            latestVersion = latest,
            sections = template.sections.map(TemplateSectionResponse::from),
            pendingApprovalCount = pending,
        )
    }

    @Transactional(readOnly = true)
    fun listVersions(
        workspace: WorkspaceContext,
        documentId: UUID,
    ): DocumentVersionList = DocumentVersionList(versions.listForDocument(find(workspace, documentId).id))

    @Transactional(readOnly = true)
    fun getVersion(
        workspace: WorkspaceContext,
        versionId: UUID,
    ): DocumentVersionResponse =
        versions.find(versionId, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException("document version", versionId)

    /**
     * A user-saved revision (R01 "저장"). The domain decides what a user may change; provenance of
     * blocks that keep their references is carried over so the lineage survives edits.
     */
    @Transactional
    fun createUserVersion(
        workspace: WorkspaceContext,
        documentId: UUID,
        request: CreateVersionRequest,
    ): DocumentVersionResponse {
        val entity =
            repository.lockByIdInWorkspace(documentId, workspace.workspaceId.value)
                ?: throw ResourceNotFoundException(TARGET, documentId)
        val latestId = versions.latestId(entity.id)
        if (request.parentVersionId != latestId) {
            throw StaleVersionException(
                "document $documentId latest version is $latestId, request carried ${request.parentVersionId}",
            )
        }
        val parent = latestId?.let { versions.find(it, workspace.workspaceId.value) }
        val parentOutput = GeneratedOutput(parent?.blocks.orEmpty().map(DocumentBlockDto::toDomain))
        val revised = parentOutput.userRevision(requireNotNull(request.blocks).map(DocumentBlockDto::toDomain))

        val kept = revised.blocks.associateBy { it.blockId }
        val provenance =
            parent?.provenance.orEmpty().filter { link ->
                val block = kept[link.blockId] ?: return@filter false
                when (link.sourceType) {
                    ProvenanceSourceType.CLAIM -> block.claimRefs.any { it.value == link.sourceId }
                    ProvenanceSourceType.EVIDENCE -> block.evidenceRefs.any { it.value == link.sourceId }
                    ProvenanceSourceType.REQUIREMENT -> block.requirementRefs.any { it.value == link.sourceId }
                    else -> block.claimRefs.isNotEmpty()
                }
            }
        val version =
            DocumentVersionResponse(
                id = ids.next(),
                documentId = entity.id,
                parentId = latestId,
                label = request.label?.trim()?.ifEmpty { null },
                blocks = revised.blocks.map(DocumentBlockDto::from),
                templateVersion = parent?.templateVersion ?: DocumentTemplate.latest(entity.type).version,
                promptVersion = null,
                modelRef = null,
                createdBy = VersionAuthor.USER,
                createdAt = Instant.now(clock),
                provenance = provenance,
            )
        versions.insert(version)
        entity.version += 1
        repository.saveAndFlush(entity)
        val saved = requireNotNull(versions.find(version.id, workspace.workspaceId.value))
        audit.record(workspace, "document.version_created", TARGET, entity.id, after = saved)
        return saved
    }

    /** F06: enqueue drafting. Fails fast (422) when the template is unknown or nothing was accepted for the application. */
    @Transactional
    fun startGeneration(
        workspace: WorkspaceContext,
        documentId: UUID,
        request: GenerationRequest,
    ): UUID {
        val entity = find(workspace, documentId)
        val templateVersion = request.templateVersion?.trim()?.ifEmpty { null } ?: DocumentTemplate.KO_V1
        DocumentTemplate.find(templateVersion, entity.type)
            ?: throw DomainRuleViolation("template $templateVersion does not exist for ${entity.type}")
        val accepted =
            jdbc.queryForObject(
                "select count(*) from requirement_matches where application_id = ? and user_decision = 'ACCEPTED'",
                Long::class.java,
                entity.applicationId,
            ) ?: 0L
        if (accepted == 0L) {
            throw DomainRuleViolation(
                "no accepted match candidates: accept at least one candidate on the match report first",
            )
        }
        val jobId =
            jobs.enqueue(
                workspace,
                JobTypes.DOCUMENT_GENERATION,
                mapOf(
                    "documentId" to entity.id.toString(),
                    "templateVersion" to templateVersion,
                    "parentVersionId" to request.parentVersionId?.toString(),
                    "claimIds" to request.claimIds?.map { it.toString() },
                    "requirementIds" to request.requirementIds?.map { it.toString() },
                ),
                dedupeKey = entity.id.toString(),
            )
        audit.record(workspace, "document.generation_requested", TARGET, entity.id, after = mapOf("jobId" to jobId))
        return jobId
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): DocumentEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun DocumentEntity.toResponse(latest: Pair<UUID, VersionAuthor>?) =
        DocumentResponse(
            id = id,
            applicationId = applicationId,
            type = type,
            title = title,
            language = language,
            latestVersionId = latest?.first,
            latestVersionCreatedBy = latest?.second,
            version = version,
            createdAt = checkNotNull(createdAt),
            updatedAt = checkNotNull(updatedAt),
        )

    private companion object {
        const val TARGET = "document"
    }
}
