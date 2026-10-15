-- A workspace with enough rows that the read paths cost something, for scripts/perf.sh.
-- Synthetic data only (CLAUDE.md §테스트 데이터). Re-running it starts from an empty workspace.
--
--   psql -v workspace="'…uuid…'" -v rows=2000 -f seed.sql
--
-- The shapes mirror apps/api/.../ListQueryPlanTest, which guards the query plans; this file is
-- about what the whole request costs, not what the planner does.
\set ON_ERROR_STOP on

begin;

-- Start clean so numbers from two runs are comparable. job_posting_snapshots is immutable, and
-- the only way to delete one is the bypass the account purge job uses (V9) — allowed here
-- because every row is synthetic and belongs to this workspace.
set local proofu.purge = 'on';
-- What a previous load run left behind: enqueued jobs and their execution rows.
delete from ai_executions where workspace_id = :workspace;
delete from requirements where snapshot_id in (
  select s.id from job_posting_snapshots s join job_postings p on p.id = s.posting_id
  where p.workspace_id = :workspace);
delete from jobs where workspace_id = :workspace;
delete from audit_events where workspace_id = :workspace;

delete from documents where workspace_id = :workspace;
delete from applications where workspace_id = :workspace;
delete from job_posting_snapshots s using job_postings p
  where p.id = s.posting_id and p.workspace_id = :workspace;
delete from job_postings where workspace_id = :workspace;
delete from capabilities where workspace_id = :workspace;
delete from skills where workspace_id = :workspace;
delete from evidence where workspace_id = :workspace;
delete from projects where workspace_id = :workspace;
delete from career_entries where workspace_id = :workspace;
delete from workspace_settings where workspace_id = :workspace;
delete from workspaces where id = :workspace;
delete from users where oidc_subject = 'perf-load';

insert into users (id, oidc_subject, oidc_issuer, email, display_name)
values (gen_random_uuid(), 'perf-load', 'perf', 'perf@example.test', 'perf');

insert into workspaces (id, owner_user_id, name)
values (:workspace, (select id from users where oidc_subject = 'perf-load'), 'perf');

insert into career_entries (workspace_id, type, title, organization, description, start_date)
select :workspace, 'EMPLOYMENT', '경력 ' || g, '회사 ' || g,
       '설명 ' || repeat('문장 ', 20), date '2000-01-01' + g
from generate_series(1, :rows) g;

-- One project per career entry: hanging them all off one entry would make the foreign key
-- index useless and the measurement would be of the seeding, not of the app.
insert into projects (workspace_id, career_entry_id, name, role, summary, start_date)
select :workspace, ce.id, '프로젝트 ' || ce.ord, '역할', '요약 ' || repeat('문장 ', 10),
       date '2000-01-01' + ce.ord::int
from (select id, row_number() over (order by id) as ord
      from career_entries where workspace_id = :workspace) ce;

insert into evidence (workspace_id, type, title, source, uri, captured_at)
select :workspace, 'URL', 'Evidence ' || g, 'USER_INPUT', 'https://example.test/' || g,
       now() - (g || ' minutes')::interval
from generate_series(1, :rows) g;

insert into skills (workspace_id, canonical_name, category, created_at)
select :workspace, '기술 ' || g, 'TOOL', now() - (g || ' minutes')::interval
from generate_series(1, :rows) g;

insert into capabilities (workspace_id, name, definition, category, created_at)
select :workspace, '역량 ' || g, '정의', 'EXECUTION', now() - (g || ' minutes')::interval
from generate_series(1, :rows) g;

insert into job_postings (workspace_id, company, role_title, updated_at)
select :workspace, '회사 ' || g, '직무 ' || g, now() - (g || ' minutes')::interval
from generate_series(1, :rows) g;

insert into job_posting_snapshots (posting_id, source, raw_text, content_hash, captured_at)
select id, 'MANUAL_TEXT', repeat('공고 원문 ', 100), md5(id::text) || md5(id::text), now()
from job_postings where workspace_id = :workspace;

insert into applications (workspace_id, snapshot_id, company, role_title, status, updated_at)
select :workspace, s.id, p.company, p.role_title, 'INTERESTED',
       now() - (row_number() over () || ' minutes')::interval
from job_posting_snapshots s join job_postings p on p.id = s.posting_id
where p.workspace_id = :workspace;

insert into documents (workspace_id, application_id, type, title, updated_at)
select :workspace, a.id, 'RESUME', '문서 ' || row_number() over (),
       now() - (row_number() over () || ' minutes')::interval
from applications a where a.workspace_id = :workspace;

-- One distinctive term, so the search path has something to find without matching everything.
insert into career_entries (workspace_id, type, title, start_date)
values (:workspace, 'EMPLOYMENT', '코틀린특이값 엔지니어', date '2020-01-01');

commit;

analyze;

select 'career_entries' as table, count(*) from career_entries where workspace_id = :workspace
union all select 'projects', count(*) from projects where workspace_id = :workspace
union all select 'evidence', count(*) from evidence where workspace_id = :workspace
union all select 'skills', count(*) from skills where workspace_id = :workspace
union all select 'capabilities', count(*) from capabilities where workspace_id = :workspace
union all select 'job_postings', count(*) from job_postings where workspace_id = :workspace
union all select 'applications', count(*) from applications where workspace_id = :workspace
union all select 'documents', count(*) from documents where workspace_id = :workspace
order by 1;
