-- Every list screen pages with a keyset (workspace, timestamp desc, id desc). Without an index
-- in that exact shape Postgres sorts the whole workspace to hand back twenty rows; these
-- indexes let it walk the order instead. Partial on the live rows: the trash is never listed.

CREATE INDEX career_entries_workspace_page_idx
    ON career_entries (workspace_id, start_date DESC, id DESC) WHERE deleted_at IS NULL;

-- Projects without a start date sort first, matching coalesce(...) in the query.
CREATE INDEX projects_workspace_page_idx
    ON projects (workspace_id, (COALESCE(start_date, DATE '9999-12-31')) DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX evidence_workspace_page_idx
    ON evidence (workspace_id, captured_at DESC, id DESC) WHERE deleted_at IS NULL;

CREATE INDEX skills_workspace_page_idx
    ON skills (workspace_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;

CREATE INDEX capabilities_workspace_page_idx
    ON capabilities (workspace_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;

CREATE INDEX job_postings_workspace_page_idx
    ON job_postings (workspace_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;

CREATE INDEX documents_workspace_page_idx
    ON documents (workspace_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;

CREATE INDEX applications_workspace_page_idx
    ON applications (workspace_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
