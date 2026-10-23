-- F21 개인 자료 가져오기, 1단계 (docs/product/personal-archive-import.md): the ledger behind
-- `import.plan` and `import.apply`. The bundle itself is never stored — it is read, planned
-- against this ledger and deleted, the way F13 keeps no resume text. What stays is the answer to
-- "did I already bring this in, and has it changed since", which is what makes a second import of
-- a repository that keeps moving safe.
--
-- These two tables are a ledger, not source data: they have no soft delete and the retention
-- sweeper does not touch them. They go with the account (export and purge) like everything else
-- keyed by the workspace.

CREATE TABLE import_runs (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid NOT NULL REFERENCES workspaces (id),
    -- sha256 of the bundle, so the same file is recognised when it is sent twice.
    bundle_hash  varchar(64) NOT NULL CHECK (bundle_hash ~ '^[0-9a-f]{64}$'),
    source       varchar(120),
    status       varchar(20) NOT NULL DEFAULT 'PLANNED'
                 CHECK (status IN ('PLANNED', 'APPLIED', 'FAILED')),
    item_count   integer NOT NULL DEFAULT 0 CHECK (item_count >= 0),
    created_at   timestamptz NOT NULL DEFAULT now(),
    applied_at   timestamptz
);

-- One row per item in the bundle. `source_key` is the adapter's own name for the thing and is
-- what makes the import idempotent: the same key arriving with the same content_hash is
-- UNCHANGED, with a different one it is CHANGED, and what ProofU already edited is a CONFLICT the
-- user resolves.
CREATE TABLE import_items (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id       uuid NOT NULL REFERENCES import_runs (id) ON DELETE CASCADE,
    workspace_id uuid NOT NULL REFERENCES workspaces (id),
    source_key   varchar(200) NOT NULL CHECK (length(btrim(source_key)) > 0),
    source_path  varchar(500),
    type         varchar(20) NOT NULL
                 CHECK (type IN ('APPLICATION', 'POSTING', 'DOCUMENT', 'CAREER', 'NOTE')),
    content_hash varchar(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    status       varchar(20) NOT NULL
                 CHECK (status IN ('NEW', 'UNCHANGED', 'CHANGED', 'CONFLICT', 'REJECTED')),
    -- Why it was rejected, in the user's language. Null unless status = REJECTED.
    reason       text,
    -- What it became, once the user picked it. Polymorphic by type, so no foreign key: the target
    -- may be an application, a posting snapshot, a document version or a career entry
    -- (scripts/restore.sh checks references without a foreign key — docs/operations).
    target_type  varchar(30),
    target_id    uuid,
    applied_at   timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT import_items_reason_only_when_rejected CHECK (
        (status = 'REJECTED') = (reason IS NOT NULL)),
    CONSTRAINT import_items_target_pair CHECK (
        (target_type IS NULL) = (target_id IS NULL)),
    CONSTRAINT import_items_applied_has_target CHECK (
        applied_at IS NULL OR target_id IS NOT NULL)
);

-- One verdict per key per run.
CREATE UNIQUE INDEX import_items_run_key_idx ON import_items (run_id, source_key);

-- The lookup every plan makes: what do I already know about this key in this workspace. Only
-- applied rows answer it, and the newest one wins.
CREATE INDEX import_items_applied_key_idx
    ON import_items (workspace_id, source_key, applied_at DESC) WHERE applied_at IS NOT NULL;

-- The list screen pages by when the run was made, newest first (InstantIdCursor).
CREATE INDEX import_runs_workspace_page_idx ON import_runs (workspace_id, created_at DESC, id DESC);
