-- F20 커리어 로그 (docs/product/network-and-social-proof.md §F20): a private work journal the
-- user writes as things happen, so that achievements are not reconstructed from memory at
-- application time. There is no audience, no reaction and no visibility column — a log is read
-- by its owner alone, and it never reaches a shared profile or an export header.
--
-- Sensitivity defaults to CONFIDENTIAL, unlike every other record (INTERNAL): a work journal
-- picks up the employer's information by default, and CONFIDENTIAL keeps it out of an AI context
-- unless the workspace consented (Sensitivity.allowedInAiContext).

CREATE TABLE career_logs (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            uuid NOT NULL REFERENCES workspaces (id),
    occurred_at             date NOT NULL,
    body                    text NOT NULL CHECK (length(btrim(body)) > 0),
    sensitivity             varchar(20) NOT NULL DEFAULT 'CONFIDENTIAL'
                            CHECK (sensitivity IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')),
    -- Promotion is a copy, so the achievement (or evidence) it produced stands on its own and
    -- these columns only record that it happened. ON DELETE SET NULL so a purged achievement
    -- never needs a log's permission to go (docs/data/retention.md §커리어 로그).
    promoted_achievement_id uuid REFERENCES achievements (id) ON DELETE SET NULL,
    promoted_evidence_id    uuid REFERENCES evidence (id) ON DELETE SET NULL,
    promoted_at             timestamptz,
    revision                bigint NOT NULL DEFAULT 1 CHECK (revision >= 1),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    deleted_at              timestamptz,
    -- A log is promoted at most once; promoted_at exists exactly when a target does. Dropping the
    -- target later (ON DELETE SET NULL) leaves promoted_at behind on purpose: it happened.
    CONSTRAINT career_logs_promotion_single CHECK (
        num_nonnulls(promoted_achievement_id, promoted_evidence_id) <= 1),
    CONSTRAINT career_logs_promoted_at_present CHECK (
        promoted_at IS NOT NULL OR num_nonnulls(promoted_achievement_id, promoted_evidence_id) = 0)
);

-- Which career entry or project a log belongs to. Two nullable foreign keys rather than the
-- polymorphic (source_type, source_id) of claim_sources: the database can enforce these, and
-- backup/restore needs no new non-FK reference check (CLAUDE.md §백업과 복구).
CREATE TABLE career_log_links (
    log_id          uuid NOT NULL REFERENCES career_logs (id) ON DELETE CASCADE,
    career_entry_id uuid REFERENCES career_entries (id) ON DELETE CASCADE,
    project_id      uuid REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT career_log_links_one_target CHECK (num_nonnulls(career_entry_id, project_id) = 1)
);

CREATE UNIQUE INDEX career_log_links_entry_idx
    ON career_log_links (log_id, career_entry_id) WHERE career_entry_id IS NOT NULL;

CREATE UNIQUE INDEX career_log_links_project_idx
    ON career_log_links (log_id, project_id) WHERE project_id IS NOT NULL;

-- The reverse direction: a career entry's or project's logs, and the lookup the cascade uses.
CREATE INDEX career_log_links_by_entry_idx
    ON career_log_links (career_entry_id) WHERE career_entry_id IS NOT NULL;

CREATE INDEX career_log_links_by_project_idx
    ON career_log_links (project_id) WHERE project_id IS NOT NULL;

-- The list screen pages by when it happened, newest first (DateIdCursor), so the index has that
-- exact shape and skips the trash (ListQueryPlanTest).
CREATE INDEX career_logs_workspace_page_idx
    ON career_logs (workspace_id, occurred_at DESC, id DESC) WHERE deleted_at IS NULL;

-- Finding the logs still waiting to be promoted is the one filtered list the screen offers.
CREATE INDEX career_logs_unpromoted_idx
    ON career_logs (workspace_id, occurred_at DESC, id DESC)
    WHERE deleted_at IS NULL AND promoted_at IS NULL;
