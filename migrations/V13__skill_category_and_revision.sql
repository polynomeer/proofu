-- F01 기술: the skills table shipped without a category vocabulary or a revision column
-- (V1). Both are needed before the API exposes skills: the category mirrors SkillCategory and
-- the revision carries optimistic concurrency like every other career record.

ALTER TABLE skills
    ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK (revision >= 1);

ALTER TABLE skills
    ADD CONSTRAINT skills_category_check CHECK (category IN (
        'PROGRAMMING_LANGUAGE', 'FRAMEWORK', 'PLATFORM', 'DATA', 'TOOL',
        'METHOD', 'DOMAIN', 'SPOKEN_LANGUAGE', 'OTHER'));

-- Names differing only in case or spacing are the same skill; the V1 unique index is case-sensitive.
CREATE UNIQUE INDEX skills_workspace_name_idx
    ON skills (workspace_id, lower(regexp_replace(btrim(canonical_name), '\s+', ' ', 'g')))
    WHERE deleted_at IS NULL;
