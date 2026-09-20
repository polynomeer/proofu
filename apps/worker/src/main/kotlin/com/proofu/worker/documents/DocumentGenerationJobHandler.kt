package com.proofu.worker.documents

import com.proofu.ai.AiBudgetExceeded
import com.proofu.ai.AiCallFailed
import com.proofu.ai.documents.ClaimForDraft
import com.proofu.ai.documents.DocumentDrafter
import com.proofu.ai.documents.DraftRequest
import com.proofu.ai.documents.EvidenceForDraft
import com.proofu.ai.documents.PostingForDraft
import com.proofu.ai.documents.RequirementForDraft
import com.proofu.ai.model.ModelProviderException
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.WorkspaceId
import com.proofu.domain.documents.DocumentTemplate
import com.proofu.domain.documents.DocumentType
import com.proofu.domain.documents.GeneratedBlock
import com.proofu.domain.documents.ProvenanceRelation
import com.proofu.domain.documents.ProvenanceSourceType
import com.proofu.worker.jobs.JobFailure
import com.proofu.worker.jobs.JobHandler
import com.proofu.worker.jobs.JobRecord
import com.proofu.worker.matching.CandidateRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * F06: draft a new AI version of a document from the requirements the user approved and the
 * match candidates the user accepted. The version is stored unapproved with one provenance link
 * per (block, source) pinned to the source revision the model saw.
 */
@Component
class DocumentGenerationJobHandler(
    private val sources: DraftSourceRepository,
    private val candidates: CandidateRepository,
    private val drafter: DocumentDrafter,
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val mapper: ObjectMapper,
    private val ids: IdGenerator,
) : JobHandler {
    private val log = LoggerFactory.getLogger(DocumentGenerationJobHandler::class.java)

    override val type = TYPE

    override fun handle(job: JobRecord): String {
        val payload = mapper.readTree(job.payload)
        val documentId = UUID.fromString(payload.get("documentId").asString())
        val templateVersion = payload.get("templateVersion")?.asString()?.ifBlank { null } ?: DocumentTemplate.KO_V1
        val onlyClaims = payload.uuidSet("claimIds")
        val onlyRequirements = payload.uuidSet("requirementIds")

        val document =
            sources.document(documentId, job.workspaceId)
                ?: throw JobFailure("DOCUMENT_NOT_FOUND", "document $documentId", retryable = false)
        val template =
            DocumentTemplate.find(templateVersion, DocumentType.valueOf(document.type))
                ?: throw JobFailure(
                    "TEMPLATE_NOT_FOUND",
                    "template $templateVersion for ${document.type}",
                    retryable = false,
                )
        val parentId =
            payload.get("parentVersionId")?.asString()?.let(UUID::fromString) ?: document.latestVersionId

        val requirements =
            candidates
                .approvedRequirements(document.snapshotId)
                .filter { onlyRequirements == null || it.id in onlyRequirements }
        val accepted =
            sources.acceptedClaims(document.applicationId).filterKeys {
                onlyClaims == null ||
                    it in onlyClaims
            }
        if (accepted.isEmpty()) {
            throw JobFailure(
                "NO_ACCEPTED_SOURCES",
                "no accepted match candidates for ${document.applicationId}",
                retryable = false,
            )
        }
        val profiles =
            candidates
                .candidates(
                    job.workspaceId,
                ).filter { it.claimId in accepted }
                .associateBy { it.claimId }
        val evidence = sources.supportingEvidence(accepted.keys)
        val requirementIds = requirements.map { it.id }.toSet()
        val claims =
            accepted.mapNotNull { (claimId, forRequirements) ->
                val c = profiles[claimId] ?: return@mapNotNull null
                ClaimForDraft(
                    id = claimId,
                    text = c.profile.claimText,
                    sourceText = c.profile.sourceText,
                    status = c.profile.claimStatus,
                    evidence = evidence[claimId].orEmpty().map { EvidenceForDraft(it.id, it.title) },
                    requirementIds = forRequirements intersect requirementIds,
                    sensitivity = c.sensitivity,
                )
            }

        val draft =
            try {
                drafter.draft(
                    WorkspaceId(job.workspaceId),
                    DraftRequest(
                        template = template,
                        language = document.language,
                        posting = PostingForDraft(document.roleTitle, document.company),
                        requirements = requirements.map { RequirementForDraft(it.id, it.category, it.text) },
                        claims = claims,
                    ),
                    job.id,
                )
            } catch (e: AiBudgetExceeded) {
                throw JobFailure("AI_BUDGET_EXCEEDED", e.message ?: "budget", retryable = false, cause = e)
            } catch (e: AiCallFailed.Refused) {
                throw JobFailure("AI_REFUSED", e.message ?: "refused", retryable = false, cause = e)
            } catch (e: AiCallFailed.InvalidOutput) {
                throw JobFailure("AI_OUTPUT_INVALID", e.message ?: "invalid output", retryable = true, cause = e)
            } catch (e: ModelProviderException) {
                throw JobFailure("AI_PROVIDER_UNAVAILABLE", e.message ?: "provider", retryable = true, cause = e)
            }
        if (draft.output.blocks.isEmpty()) {
            throw JobFailure("AI_OUTPUT_INVALID", "model produced no usable block", retryable = true)
        }

        val citedClaims =
            draft.output.blocks
                .flatMap { b -> b.claimRefs.map { it.value } }
                .toSet()
        val claimRevisions = sources.claimRevisions(citedClaims)
        val claimSources = sources.claimSources(citedClaims)
        val evidenceRevisions = evidence.values.flatten().associate { it.id to it.revision }
        val requirementVersions = sources.requirementVersions(requirementIds)

        val versionId = ids.next()
        tx.execute {
            jdbc.update(
                """
                insert into document_versions
                  (id, document_id, parent_id, content_json, template_version, prompt_version, model_ref, created_by, source_job_id, execution_id)
                values (?, ?, ?, ?::jsonb, ?, ?, ?, 'AI', ?, ?)
                """.trimIndent(),
                versionId,
                documentId,
                parentId,
                mapper.writeValueAsString(mapOf("blocks" to draft.output.blocks.map { it.toJson() })),
                template.version,
                DocumentDrafter.PROMPT_VERSION,
                draft.model,
                job.id,
                draft.executionId,
            )
            val links = mutableSetOf<List<Any>>()
            draft.output.blocks.forEach { b ->
                b.claimRefs.forEach { claim ->
                    val revision = claimRevisions[claim.value] ?: return@forEach
                    links +=
                        listOf(
                            b.blockId,
                            ProvenanceSourceType.CLAIM,
                            claim.value,
                            revision,
                            ProvenanceRelation.DERIVED_FROM,
                        )
                    claimSources[claim.value].orEmpty().forEach { s ->
                        links +=
                            listOf(
                                b.blockId,
                                ProvenanceSourceType.valueOf(s.sourceType),
                                s.sourceId,
                                s.sourceRevision,
                                ProvenanceRelation.DERIVED_FROM,
                            )
                    }
                }
                b.evidenceRefs.forEach { e ->
                    val revision = evidenceRevisions[e.value] ?: return@forEach
                    links +=
                        listOf(b.blockId, ProvenanceSourceType.EVIDENCE, e.value, revision, ProvenanceRelation.CITES)
                }
                b.requirementRefs.forEach { r ->
                    val version = requirementVersions[r.value] ?: return@forEach
                    links +=
                        listOf(
                            b.blockId,
                            ProvenanceSourceType.REQUIREMENT,
                            r.value,
                            version,
                            ProvenanceRelation.ADDRESSES,
                        )
                }
            }
            links.forEach { (blockId, sourceType, sourceId, revision, relation) ->
                jdbc.update(
                    "insert into provenance_links (version_id, block_id, source_type, source_id, source_revision, relation) values (?, ?, ?, ?, ?, ?)",
                    versionId,
                    blockId,
                    (sourceType as ProvenanceSourceType).name,
                    sourceId,
                    revision,
                    (relation as ProvenanceRelation).name,
                )
            }
        }

        val pending = draft.output.blocksPendingApproval().size
        log.info(
            "document.generation document={} version={} blocks={} pendingApproval={} dropped={} truncated={} cost={}µ$",
            documentId,
            versionId,
            draft.output.blocks.size,
            pending,
            draft.dropped.size,
            draft.truncated,
            draft.costMicros,
        )
        return mapper.writeValueAsString(
            mapOf(
                "documentId" to documentId.toString(),
                "versionId" to versionId.toString(),
                "blocks" to draft.output.blocks.size,
                "pendingApproval" to pending,
                "dropped" to draft.dropped,
                "truncated" to draft.truncated,
                "costMicros" to draft.costMicros,
            ),
        )
    }

    /** Stored shape is the API's DocumentBlock. */
    private fun GeneratedBlock.toJson(): Map<String, Any> =
        mapOf(
            "blockId" to blockId,
            "text" to text,
            "claimRefs" to claimRefs.map { it.value.toString() },
            "evidenceRefs" to evidenceRefs.map { it.value.toString() },
            "requirementRefs" to requirementRefs.map { it.value.toString() },
            "certainty" to certainty.name,
            "warnings" to warnings,
            "approvedByUser" to approvedByUser,
        )

    private fun tools.jackson.databind.JsonNode.uuidSet(field: String): Set<UUID>? {
        val node = get(field) ?: return null
        if (!node.isArray || node.isEmpty) return null
        return buildSet { for (item in node) add(UUID.fromString(item.asString())) }
    }

    companion object {
        /** Must match apps/api JobTypes.DOCUMENT_GENERATION. */
        const val TYPE = "document.generation"
    }
}
