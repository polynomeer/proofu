package com.proofu.api.document

import com.proofu.api.application.ApplicationService
import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.job.JobService
import com.proofu.api.job.JobTypes
import com.proofu.api.web.DateIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.applications.ApplicationStatus
import com.proofu.domain.common.DomainRuleViolation
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.GeneratedOutput
import com.proofu.domain.documents.ProvenanceSourceType
import com.proofu.domain.documents.VersionAuthor
import com.proofu.domain.documents.VersionDiff
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    /** Workspace-wide list (IA "지원 문서"), keyset on (updated date, id) like the other lists. */
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        type: DocumentType?,
        q: String?,
        cursor: DateIdCursor?,
        limit: Int,
    ): DocumentPage {
        val rows =
            jdbc.query(
                """
                select d.id, d.application_id, d.type, d.title, d.language, d.version, d.created_at, d.updated_at,
                       a.company, a.role_title, a.status as application_status,
                       (select count(*) from submission_snapshots s join document_versions v on v.id = s.document_version_id
                         where v.document_id = d.id) as submission_count,
                       lv.id as latest_version_id, lv.created_by as latest_created_by
                from documents d
                join applications a on a.id = d.application_id
                left join lateral (
                    select v.id, v.created_by from document_versions v where v.document_id = d.id
                    order by v.created_at desc, v.id desc limit 1
                ) lv on true
                where d.workspace_id = ? and d.deleted_at is null
                  and (cast(? as varchar) is null or d.type = cast(? as varchar))
                  and (cast(? as varchar) is null
                       or d.title ilike '%' || cast(? as varchar) || '%'
                       or a.company ilike '%' || cast(? as varchar) || '%'
                       or a.role_title ilike '%' || cast(? as varchar) || '%')
                  and (cast(? as date) is null
                       or cast(d.updated_at as date) < cast(? as date)
                       or (cast(d.updated_at as date) = cast(? as date) and d.id < cast(? as uuid)))
                order by cast(d.updated_at as date) desc, d.id desc
                limit ?
                """.trimIndent(),
                { rs, _ ->
                    DocumentResponse(
                        id = rs.getObject("id", UUID::class.java),
                        applicationId = rs.getObject("application_id", UUID::class.java),
                        type = DocumentType.valueOf(rs.getString("type")),
                        title = rs.getString("title"),
                        language = rs.getString("language"),
                        latestVersionId = rs.getObject("latest_version_id", UUID::class.java),
                        latestVersionCreatedBy = rs.getString("latest_created_by")?.let(VersionAuthor::valueOf),
                        company = rs.getString("company"),
                        roleTitle = rs.getString("role_title"),
                        applicationStatus = ApplicationStatus.valueOf(rs.getString("application_status")),
                        submissionCount = rs.getInt("submission_count"),
                        version = rs.getLong("version"),
                        createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                        updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
                    )
                },
                workspace.workspaceId.value,
                type?.name,
                type?.name,
                q,
                q,
                q,
                q,
                cursor?.date,
                cursor?.date,
                cursor?.date,
                cursor?.id,
                limit + 1,
            )
        val page = rows.take(limit)
        val next =
            if (rows.size > limit) {
                val last = page.last()
                DateIdCursor(last.updatedAt.atZone(ZoneOffset.UTC).toLocalDate(), last.id).encode()
            } else {
                null
            }
        return DocumentPage(page, next)
    }

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

    /** R02: target `{id}` against base `against`; both must be versions of the same document. */
    @Transactional(readOnly = true)
    fun diff(
        workspace: WorkspaceContext,
        targetId: UUID,
        baseId: UUID,
    ): VersionDiffResponse {
        val target = getVersion(workspace, targetId)
        val base = getVersion(workspace, baseId)
        if (base.documentId != target.documentId) throw ResourceNotFoundException("document version", baseId)
        val diff =
            VersionDiff.compare(
                GeneratedOutput(base.blocks.map(DocumentBlockDto::toDomain)),
                GeneratedOutput(target.blocks.map(DocumentBlockDto::toDomain)),
            )
        return VersionDiffResponse(
            baseVersionId = baseId,
            targetVersionId = targetId,
            added = diff.added,
            removed = diff.removed,
            changed = diff.changed,
            unchanged = diff.unchanged,
            entries =
                diff.entries.map { e ->
                    BlockDiffResponse(
                        blockId = e.blockId,
                        kind = e.kind,
                        changedFields = e.changedFields,
                        base = e.base?.let(DocumentBlockDto::from),
                        target = e.target?.let(DocumentBlockDto::from),
                        textDiff = e.textDiff.map { DiffSegmentResponse(it.kind, it.text) },
                    )
                },
        )
    }

    /** Sentence revision: nothing is written; the job result is the proposal (AI feature spec "문장 개선"). */
    @Transactional
    fun startRevision(
        workspace: WorkspaceContext,
        documentId: UUID,
        request: RevisionRequest,
    ): UUID {
        val entity = find(workspace, documentId)
        val versionId = requireNotNull(request.versionId)
        val version =
            versions.find(versionId, workspace.workspaceId.value)?.takeIf { it.documentId == entity.id }
                ?: throw ResourceNotFoundException("document version", versionId)
        val blockId = requireNotNull(request.blockId).trim()
        if (version.blocks.none { it.blockId == blockId }) throw ResourceNotFoundException("block", blockId)
        val mode = requireNotNull(request.mode)
        return jobs.enqueue(
            workspace,
            JobTypes.DOCUMENT_REVISION,
            mapOf(
                "documentId" to entity.id.toString(),
                "versionId" to versionId.toString(),
                "blockId" to blockId,
                "mode" to mode.name,
            ),
            dedupeKey = "$versionId:$blockId:${mode.name}",
        )
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
