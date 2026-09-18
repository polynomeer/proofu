-- F05 matching results: one row per (application, approved requirement, candidate claim).
-- Scores and features are deterministic (domain MatchFeatureCalculator); reason text comes
-- from the model. user_decision is the user's call and survives re-runs (design §8.3).

CREATE TABLE requirement_matches (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id              uuid NOT NULL REFERENCES applications (id),
    requirement_id              uuid NOT NULL REFERENCES requirements (id),
    claim_id                    uuid NOT NULL REFERENCES claims (id),
    rank                        integer NOT NULL,
    score                       integer NOT NULL CHECK (score BETWEEN 0 AND 100),
    band                        varchar(10) NOT NULL CHECK (band IN ('LOW', 'MEDIUM', 'HIGH')),
    features                    jsonb NOT NULL,
    claim_status_at_scoring     varchar(20) NOT NULL CHECK (claim_status_at_scoring IN ('UNSUPPORTED', 'SUPPORTED', 'CONTESTED')),
    reason                      text,
    matched_requirement_phrase  text,
    matched_evidence_phrase     text,
    reason_execution_id         uuid REFERENCES ai_executions (id),
    user_decision               varchar(10) CHECK (user_decision IS NULL OR user_decision IN ('ACCEPTED', 'REJECTED')),
    run_job_id                  uuid REFERENCES jobs (id),
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (application_id, requirement_id, claim_id)
);
CREATE INDEX requirement_matches_application_idx ON requirement_matches (application_id, requirement_id, rank);
CREATE TRIGGER requirement_matches_set_updated_at BEFORE UPDATE ON requirement_matches FOR EACH ROW EXECUTE FUNCTION set_updated_at();
