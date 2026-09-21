-- Document header data (name, headline, contact, links). One row per user; rendered into exports
-- only and never sent to an AI provider. exports.profile_version pins which profile a file used so
-- reuse (ADR-0009 §5) is skipped once the profile changes.

CREATE TABLE user_profiles (
    user_id     uuid PRIMARY KEY REFERENCES users (id),
    full_name   varchar(120) NOT NULL,
    headline    varchar(200),
    email       varchar(320),
    phone       varchar(40),
    location    varchar(120),
    links       jsonb NOT NULL DEFAULT '[]'::jsonb,
    version     bigint NOT NULL DEFAULT 1,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER user_profiles_set_updated_at BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE exports
    ADD COLUMN profile_version bigint;
