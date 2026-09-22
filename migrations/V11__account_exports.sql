-- Full data export (privacy-requirements: 조회·정정·내보내기·삭제). One ZIP per request, built by
-- the account.export job, kept in export_files for a short time and downloadable only after a
-- recent login.

CREATE TABLE account_exports (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid NOT NULL REFERENCES workspaces (id),
    job_id        uuid REFERENCES jobs (id),
    status        varchar(20) NOT NULL DEFAULT 'REQUESTED'
                  CHECK (status IN ('REQUESTED', 'READY', 'FAILED', 'EXPIRED')),
    error_code    varchar(60),
    object_key    varchar(512),
    sha256        char(64),
    size_bytes    bigint,
    tables        jsonb,
    expires_at    timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX account_exports_workspace_idx ON account_exports (workspace_id, created_at DESC);
CREATE TRIGGER account_exports_set_updated_at BEFORE UPDATE ON account_exports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
