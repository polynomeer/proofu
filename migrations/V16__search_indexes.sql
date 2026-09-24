-- Global search matches substrings ('Kotlin' inside 'Kotlin/JVM', a company inside a title), so
-- a b-tree cannot serve it and Postgres falls back to reading every workspace's rows. Each index
-- pairs the workspace with one searched column (btree_gin + pg_trgm), which is the shape the
-- planner can use for `workspace_id = ? and lower(col) like '%term%'`.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;

CREATE INDEX career_entries_title_search_idx ON career_entries
    USING gin (workspace_id, lower(title) gin_trgm_ops);
CREATE INDEX career_entries_org_search_idx ON career_entries
    USING gin (workspace_id, lower(coalesce(organization, '')) gin_trgm_ops);

CREATE INDEX projects_name_search_idx ON projects
    USING gin (workspace_id, lower(name) gin_trgm_ops);
CREATE INDEX projects_summary_search_idx ON projects
    USING gin (workspace_id, lower(summary) gin_trgm_ops);

CREATE INDEX skills_name_search_idx ON skills
    USING gin (workspace_id, lower(canonical_name) gin_trgm_ops);

CREATE INDEX evidence_title_search_idx ON evidence
    USING gin (workspace_id, lower(title) gin_trgm_ops);

CREATE INDEX job_postings_company_search_idx ON job_postings
    USING gin (workspace_id, lower(company) gin_trgm_ops);
CREATE INDEX job_postings_role_search_idx ON job_postings
    USING gin (workspace_id, lower(role_title) gin_trgm_ops);
