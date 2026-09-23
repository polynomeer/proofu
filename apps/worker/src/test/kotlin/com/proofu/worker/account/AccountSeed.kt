package com.proofu.worker.account

import com.proofu.domain.jobs.ContentHash
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** One workspace with a row in every user-owned table, for the purge and export handler tests. */
object AccountSeed {
    data class Seed(
        val userId: UUID,
        val workspaceId: UUID,
        val applicationId: UUID,
        val submissionId: UUID,
        val exportId: UUID,
    )

    fun seed(jdbc: JdbcTemplate): Seed {
        val userId = UUID.randomUUID()
        val ws = UUID.randomUUID()
        jdbc.update(
            "insert into users (id, oidc_subject, oidc_issuer, email, display_name) values (?, ?, 'test', ?, 'T')",
            userId,
            "sub-$userId",
            "$userId@example.com",
        )
        jdbc.update("insert into workspaces (id, owner_user_id, name) values (?, ?, 'W')", ws, userId)
        jdbc.update("insert into workspace_members (workspace_id, user_id, role) values (?, ?, 'OWNER')", ws, userId)
        jdbc.update(
            "insert into user_profiles (user_id, full_name, email) values (?, '홍길동', 'hong@example.com')",
            userId,
        )
        val entry = UUID.randomUUID()
        jdbc.update(
            "insert into career_entries (id, workspace_id, type, title, start_date) values (?, ?, 'EMPLOYMENT', 'PM', '2024-01-01')",
            entry,
            ws,
        )
        val project = UUID.randomUUID()
        jdbc.update(
            "insert into projects (id, workspace_id, career_entry_id, name, role, summary) values (?, ?, ?, 'P', 'r', 's')",
            project,
            ws,
            entry,
        )
        jdbc.update(
            "insert into achievements (id, workspace_id, project_id, action, outcome, confidence) values (?, ?, ?, 'a', 'o', 0.8)",
            UUID.randomUUID(),
            ws,
            project,
        )
        val skill = UUID.randomUUID()
        jdbc.update(
            "insert into skills (id, workspace_id, canonical_name, category) values (?, ?, 'Kotlin', 'PROGRAMMING_LANGUAGE')",
            skill,
            ws,
        )
        jdbc.update("insert into project_skills (project_id, skill_id) values (?, ?)", project, skill)
        val capability = UUID.randomUUID()
        jdbc.update(
            "insert into capabilities (id, workspace_id, name, definition, category) values (?, ?, '설계', '설계한다', 'SYSTEM_DESIGN')",
            capability,
            ws,
        )
        val claim = UUID.randomUUID()
        jdbc.update("insert into claims (id, workspace_id, text, claim_type) values (?, ?, 'c', 'FACT')", claim, ws)
        jdbc.update(
            "insert into claim_sources (claim_id, source_type, source_id, source_revision) values (?, 'PROJECT', ?, 1)",
            claim,
            project,
        )
        val evidence = UUID.randomUUID()
        jdbc.update(
            "insert into evidence (id, workspace_id, type, title, source, uri, captured_at) values (?, ?, 'URL', 'e', 'USER_INPUT', 'https://a', now())",
            evidence,
            ws,
        )
        jdbc.update(
            "insert into claim_evidence (claim_id, evidence_id, relation, confidence) values (?, ?, 'SUPPORTS', 0.9)",
            claim,
            evidence,
        )
        jdbc.update("insert into capability_evidence (capability_id, evidence_id) values (?, ?)", capability, evidence)
        val posting = UUID.randomUUID()
        val snapshot = UUID.randomUUID()
        jdbc.update(
            "insert into job_postings (id, workspace_id, company, role_title) values (?, ?, 'ABC', 'PM')",
            posting,
            ws,
        )
        jdbc.update(
            "insert into job_posting_snapshots (id, posting_id, source, raw_text, content_hash, captured_at) values (?, ?, 'MANUAL_TEXT', 't', ?, now())",
            snapshot,
            posting,
            ContentHash.of("t"),
        )
        val requirement = UUID.randomUUID()
        jdbc.update(
            "insert into requirements (id, snapshot_id, category, text, confidence, origin, status, approved_at) values (?, ?, 'REQUIRED', 'r', 1, 'USER', 'APPROVED', now())",
            requirement,
            snapshot,
        )
        val application = UUID.randomUUID()
        jdbc.update(
            "insert into applications (id, workspace_id, snapshot_id, company, role_title, status) values (?, ?, ?, 'ABC', 'PM', 'SUBMITTED')",
            application,
            ws,
            snapshot,
        )
        jdbc.update(
            "insert into application_status_events (id, application_id, from_status, to_status, occurred_at) values (?, ?, null, 'INTERESTED', now())",
            UUID.randomUUID(),
            application,
        )
        jdbc.update(
            "insert into requirement_matches (id, application_id, requirement_id, claim_id, rank, score, band, features, claim_status_at_scoring, user_decision) values (?, ?, ?, ?, 1, 50, 'MEDIUM', '{}'::jsonb, 'SUPPORTED', 'ACCEPTED')",
            UUID.randomUUID(),
            application,
            requirement,
            claim,
        )
        val document = UUID.randomUUID()
        jdbc.update(
            "insert into documents (id, workspace_id, application_id, type, title) values (?, ?, ?, 'RESUME', 'd')",
            document,
            ws,
            application,
        )
        val version = UUID.randomUUID()
        jdbc.update(
            "insert into document_versions (id, document_id, content_json, template_version, created_by) values (?, ?, '{\"blocks\":[]}'::jsonb, 'ko-v1', 'USER')",
            version,
            document,
        )
        jdbc.update(
            "insert into provenance_links (version_id, block_id, source_type, source_id, source_revision, relation) values (?, 'a-1', 'CLAIM', ?, 1, 'DERIVED_FROM')",
            version,
            claim,
        )
        val submission = UUID.randomUUID()
        jdbc.update(
            "insert into submission_snapshots (id, application_id, document_version_id, posting_snapshot_id, hash, submitted_at) values (?, ?, ?, ?, repeat('a', 64), now())",
            submission,
            application,
            version,
            snapshot,
        )
        jdbc.update(
            "insert into reviews (id, application_id, observed_fact, hypothesis) values (?, ?, 'f', 'h')",
            UUID.randomUUID(),
            application,
        )
        val export = UUID.randomUUID()
        jdbc.update(
            "insert into exports (id, workspace_id, document_version_id, format, template_version, status, object_key) values (?, ?, ?, 'DOCX', 'ko-v1', 'READY', ?)",
            export,
            ws,
            version,
            "pg:$export",
        )
        jdbc.update("insert into export_files (object_key, content) values (?, ?)", "pg:$export", "x".toByteArray())
        jdbc.update(
            "insert into jobs (id, workspace_id, type, status, payload, finished_at) values (?, ?, 'application.match', 'SUCCEEDED', '{}'::jsonb, now())",
            UUID.randomUUID(),
            ws,
        )
        jdbc.update(
            "insert into audit_events (id, workspace_id, actor_id, actor_type, action, target_type, target_id) values (?, ?, ?, 'USER', 'x', 'y', ?)",
            UUID.randomUUID(),
            ws,
            userId,
            ws,
        )
        return Seed(userId, ws, application, submission, export)
    }
}
