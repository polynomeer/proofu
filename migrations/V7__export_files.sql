-- ADR-0009: rendered exports live in PostgreSQL until an object store is chosen. exports.object_key
-- is 'pg:<key>' and may be shared by several exports of the same version/format (reuse, §5).

ALTER TABLE exports
    ADD COLUMN job_id uuid REFERENCES jobs (id);

CREATE TABLE export_files (
    object_key  varchar(512) PRIMARY KEY,
    content     bytea NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
