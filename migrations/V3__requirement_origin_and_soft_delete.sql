-- Requirements can be typed by the user (approved as they are written) or extracted by AI
-- (draft until the user approves). Soft delete keeps rows that documents may reference.

ALTER TABLE requirements
    ADD COLUMN origin varchar(10) NOT NULL DEFAULT 'USER' CHECK (origin IN ('USER', 'AI')),
    ADD COLUMN deleted_at timestamptz;

CREATE INDEX requirements_snapshot_live_idx ON requirements (snapshot_id, sort_order) WHERE deleted_at IS NULL;
