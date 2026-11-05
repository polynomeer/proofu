-- F21 가져오기 적용 (docs/product/personal-archive-import.md): a posting that comes from the
-- user's own archive is neither pasted into ProofU (MANUAL_TEXT) nor collected by us (URL_FETCH,
-- CAREER_OPS), so it gets its own source. The snapshot still holds exactly the text the archive
-- had — the server does not invent posting text — and IMPORTED is what tells a reader that the
-- text is as complete as that archive was, not as complete as the posting was.

ALTER TABLE job_posting_snapshots DROP CONSTRAINT job_posting_snapshots_source_check;

ALTER TABLE job_posting_snapshots
    ADD CONSTRAINT job_posting_snapshots_source_check
    CHECK (source IN ('MANUAL_TEXT', 'URL_FETCH', 'CAREER_OPS', 'IMPORTED'));
