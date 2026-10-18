-- F14 인맥 기록 (docs/domain/career-data-model.md §인맥): the people the user worked with, so a
-- claim can later be confirmed by one of them (F15). There is no reciprocity — no invitation, no
-- acceptance, no profile — and a contact need not use ProofU at all, which is what separates this
-- from workspace_members.
--
-- This is the first third-party personal data the product holds. There is deliberately no
-- sensitivity column: the domain fixes it at RESTRICTED, so no consent setting can put a contact
-- into an AI context (docs/security/privacy-requirements.md). Rows are entered one at a time —
-- no address-book or social import — and are purged with the trash window like any source record.

CREATE TABLE contacts (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid NOT NULL REFERENCES workspaces (id),
    display_name  varchar(120) NOT NULL CHECK (length(btrim(display_name)) > 0),
    relation      varchar(20) NOT NULL
                  CHECK (relation IN ('MANAGER', 'PEER', 'REPORT', 'CLIENT', 'PARTNER', 'MENTOR', 'OTHER')),
    -- Needed only to ask for an attestation; everything else works without it.
    email         varchar(320),
    organization  varchar(120),
    role          varchar(120),
    note          text,
    revision      bigint NOT NULL DEFAULT 1 CHECK (revision >= 1),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    deleted_at    timestamptz
);

-- Where the two of them worked together. Two nullable foreign keys rather than a polymorphic
-- (source_type, source_id): the database enforces these, and backup/restore needs no new non-FK
-- reference check (CLAUDE.md §백업과 복구). Same shape as career_log_links (V19).
CREATE TABLE contact_links (
    contact_id      uuid NOT NULL REFERENCES contacts (id) ON DELETE CASCADE,
    career_entry_id uuid REFERENCES career_entries (id) ON DELETE CASCADE,
    project_id      uuid REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT contact_links_one_target CHECK (num_nonnulls(career_entry_id, project_id) = 1)
);

CREATE UNIQUE INDEX contact_links_entry_idx
    ON contact_links (contact_id, career_entry_id) WHERE career_entry_id IS NOT NULL;

CREATE UNIQUE INDEX contact_links_project_idx
    ON contact_links (contact_id, project_id) WHERE project_id IS NOT NULL;

-- The reverse direction: a career entry's or project's people, and the lookup the cascade uses.
CREATE INDEX contact_links_by_entry_idx
    ON contact_links (career_entry_id) WHERE career_entry_id IS NOT NULL;

CREATE INDEX contact_links_by_project_idx
    ON contact_links (project_id) WHERE project_id IS NOT NULL;

-- The list pages by when the contact was added, newest first (InstantIdCursor), so the index has
-- that exact shape and skips the trash (ListQueryPlanTest).
CREATE INDEX contacts_workspace_page_idx
    ON contacts (workspace_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
