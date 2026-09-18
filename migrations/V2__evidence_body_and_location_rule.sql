-- Evidence of type NOTE carries its content in the row instead of a uri/object key.
-- The location rule becomes type-specific (docs/domain/evidence-model.md):
--   NOTE  -> body required
--   FILE  -> object_key required
--   other -> uri required
-- The domain module enforces the same rule; this constraint is the database defence line.

ALTER TABLE evidence ADD COLUMN body text;

ALTER TABLE evidence DROP CONSTRAINT evidence_location_check;
ALTER TABLE evidence ADD CONSTRAINT evidence_location_check CHECK (
    (type = 'NOTE' AND body IS NOT NULL)
    OR (type = 'FILE' AND object_key IS NOT NULL)
    OR (type NOT IN ('NOTE', 'FILE') AND uri IS NOT NULL)
);
