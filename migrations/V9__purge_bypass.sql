-- Account deletion (privacy-requirements.md, ADR-0006 "dedicated purge"): immutable tables may be
-- DELETEd only inside the purge job's transaction, which sets `proofu.purge = 'on'` with SET LOCAL.
-- UPDATE stays impossible everywhere; ordinary deletes stay rejected.

CREATE OR REPLACE FUNCTION reject_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('proofu.purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION '% rows are immutable (%)', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;
