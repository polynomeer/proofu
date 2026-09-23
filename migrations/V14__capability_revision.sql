-- F01 역량: capabilities shipped without a revision column (V1); the API needs the same
-- optimistic concurrency as every other career record before it exposes them.

ALTER TABLE capabilities
    ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK (revision >= 1);

CREATE INDEX capabilities_parent_idx ON capabilities (parent_id) WHERE parent_id IS NOT NULL;
