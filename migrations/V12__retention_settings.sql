-- S01 retention (docs/data/retention.md §보존 기간 설정): how long the trash keeps soft-deleted
-- source records and how long export files stay downloadable. Ranges mirror RetentionPolicy.

ALTER TABLE workspace_settings
    ADD COLUMN trash_retention_days  integer NOT NULL DEFAULT 30
               CHECK (trash_retention_days BETWEEN 7 AND 365),
    ADD COLUMN export_retention_days integer NOT NULL DEFAULT 7
               CHECK (export_retention_days BETWEEN 1 AND 90);
