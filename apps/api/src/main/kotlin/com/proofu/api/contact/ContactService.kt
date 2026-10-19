package com.proofu.api.contact

import com.proofu.api.audit.AuditLog
import com.proofu.api.identity.WorkspaceContext
import com.proofu.api.web.InstantIdCursor
import com.proofu.api.web.ResourceNotFoundException
import com.proofu.api.web.StaleVersionException
import com.proofu.domain.career.ContactLink
import com.proofu.domain.career.ContactLinkType
import com.proofu.domain.career.ContactRelation
import com.proofu.domain.common.ContactId
import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.Revision
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * F14 인맥. Someone the user worked with, kept so a claim can later be confirmed by a person
 * (F15). There is no reciprocity: nothing here notifies the contact, and nothing asks them to
 * accept. This is third-party personal data — it never reaches a model, and the audit log keeps
 * hashes only (docs/security/privacy-requirements.md).
 */
@Service
class ContactService(
    private val repository: ContactRepository,
    private val jdbc: JdbcTemplate,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(
        workspace: WorkspaceContext,
        relation: ContactRelation?,
        cursor: InstantIdCursor?,
        limit: Int,
    ): ContactPage {
        val rows =
            repository.page(
                workspaceId = workspace.workspaceId.value,
                relation = relation?.name,
                cursorAt = cursor?.at,
                cursorId = cursor?.id,
                limit = limit + 1,
            )
        val page = rows.take(limit)
        val links = linksOf(page.map { it.id })
        val next =
            if (rows.size > limit) {
                InstantIdCursor(checkNotNull(page.last().createdAt), page.last().id).encode()
            } else {
                null
            }
        return ContactPage(page.map { ContactResponse.from(it, links[it.id].orEmpty()) }, next)
    }

    @Transactional(readOnly = true)
    fun get(
        workspace: WorkspaceContext,
        id: UUID,
    ): ContactResponse {
        val entity = find(workspace, id)
        return ContactResponse.from(entity, linksOf(listOf(entity.id))[entity.id].orEmpty())
    }

    @Transactional
    fun create(
        workspace: WorkspaceContext,
        request: ContactRequest,
    ): ContactResponse {
        val contact = request.toDomain(ContactId(ids.next()), workspace.workspaceId, Revision.INITIAL)
        requireLinkTargets(workspace, contact.links)
        val saved = repository.saveAndFlush(ContactEntity.from(contact))
        replaceLinks(saved.id, contact.links)
        val response = ContactResponse.from(saved, contact.links)
        audit.record(workspace, "contact.created", TARGET, saved.id, after = response)
        return response
    }

    @Transactional
    fun update(
        workspace: WorkspaceContext,
        id: UUID,
        request: ContactRequest,
        expectedRevision: Long,
    ): ContactResponse {
        val entity = lock(workspace, id)
        if (entity.revision != expectedRevision) throw StaleVersionException(TARGET, expectedRevision, entity.revision)
        val currentLinks = linksOf(listOf(entity.id))[entity.id].orEmpty()
        val before = ContactResponse.from(entity, currentLinks)
        val next =
            entity.toDomain(currentLinks).revisedTo(
                request.toDomain(ContactId(entity.id), workspace.workspaceId, Revision(entity.revision)),
            )
        requireLinkTargets(workspace, next.links)
        entity.apply(next)
        val saved = repository.saveAndFlush(entity)
        replaceLinks(saved.id, next.links)
        val response = ContactResponse.from(saved, next.links)
        audit.record(workspace, "contact.updated", TARGET, saved.id, before = before, after = response)
        return response
    }

    /** Soft delete: the row stays for the trash window (docs/data/retention.md §인맥). */
    @Transactional
    fun delete(
        workspace: WorkspaceContext,
        id: UUID,
    ) {
        val entity = lock(workspace, id)
        val before = ContactResponse.from(entity, linksOf(listOf(entity.id))[entity.id].orEmpty())
        entity.deletedAt = Instant.now(clock)
        entity.revision += 1
        repository.saveAndFlush(entity)
        audit.record(workspace, "contact.deleted", TARGET, entity.id, before = before)
    }

    private fun find(
        workspace: WorkspaceContext,
        id: UUID,
    ): ContactEntity =
        repository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    private fun lock(
        workspace: WorkspaceContext,
        id: UUID,
    ): ContactEntity =
        repository.lockByIdAndWorkspaceId(id, workspace.workspaceId.value)
            ?: throw ResourceNotFoundException(TARGET, id)

    /** Links of several contacts in one query, so a page does not ask per row. */
    private fun linksOf(contactIds: List<UUID>): Map<UUID, List<ContactLink>> {
        if (contactIds.isEmpty()) return emptyMap()
        val placeholders = contactIds.joinToString(",") { "?" }
        return jdbc
            .query(
                """
                select contact_id, career_entry_id, project_id from contact_links
                where contact_id in ($placeholders)
                order by career_entry_id nulls last, project_id nulls last
                """.trimIndent(),
                { rs, _ ->
                    val contactId = rs.getObject("contact_id", UUID::class.java)
                    val entryId = rs.getObject("career_entry_id", UUID::class.java)
                    val projectId = rs.getObject("project_id", UUID::class.java)
                    contactId to
                        if (entryId != null) {
                            ContactLink(ContactLinkType.CAREER_ENTRY, entryId)
                        } else {
                            ContactLink(ContactLinkType.PROJECT, checkNotNull(projectId))
                        }
                },
                *contactIds.toTypedArray(),
            ).groupBy({ it.first }, { it.second })
    }

    /** The set is replaced whole, like every other career link. */
    private fun replaceLinks(
        contactId: UUID,
        links: List<ContactLink>,
    ) {
        jdbc.update("delete from contact_links where contact_id = ?", contactId)
        links.forEach {
            when (it.type) {
                ContactLinkType.CAREER_ENTRY ->
                    jdbc.update(
                        "insert into contact_links (contact_id, career_entry_id) values (?, ?)",
                        contactId,
                        it.id,
                    )

                ContactLinkType.PROJECT ->
                    jdbc.update("insert into contact_links (contact_id, project_id) values (?, ?)", contactId, it.id)
            }
        }
    }

    /** A link to another workspace's record is reported as not found, never 403. */
    private fun requireLinkTargets(
        workspace: WorkspaceContext,
        links: List<ContactLink>,
    ) {
        requireExists(
            "career_entries",
            "career_entry",
            links.filter { it.type == ContactLinkType.CAREER_ENTRY }.map { it.id },
            workspace,
        )
        requireExists(
            "projects",
            "project",
            links.filter { it.type == ContactLinkType.PROJECT }.map { it.id },
            workspace,
        )
    }

    private fun requireExists(
        table: String,
        target: String,
        wanted: List<UUID>,
        workspace: WorkspaceContext,
    ) {
        if (wanted.isEmpty()) return
        val placeholders = wanted.joinToString(",") { "?" }
        val found =
            jdbc
                .query(
                    """
                    select id from $table
                    where workspace_id = ? and deleted_at is null and id in ($placeholders)
                    """.trimIndent(),
                    { rs, _ -> rs.getObject("id", UUID::class.java) },
                    workspace.workspaceId.value,
                    *wanted.toTypedArray(),
                ).toSet()
        val missing = wanted.firstOrNull { it !in found } ?: return
        throw ResourceNotFoundException(target, missing)
    }

    private companion object {
        const val TARGET = "contact"
    }
}
