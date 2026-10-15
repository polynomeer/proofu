-- claim_sources is keyed by (claim_id, source_type, source_id), which answers "what does this
-- claim cite". Every reader asks the other direction — "what cites this entry/project/
-- achievement" — and had no index for it: the dashboard timeline, the claim list on a career
-- entry or project, and the retention sweeper's "is anything alive still citing this row".
--
-- At 2000 career entries the dashboard spent 17s in that lookup alone.

CREATE INDEX claim_sources_source_idx ON claim_sources (source_type, source_id);
