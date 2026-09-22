-- S01 preferences: AI consent for CONFIDENTIAL data (RESTRICTED is never sent) and the default
-- visibility of new career records. One row per workspace, created on first save.

CREATE TABLE workspace_settings (
    workspace_id        uuid PRIMARY KEY REFERENCES workspaces (id),
    ai_consent          varchar(20) NOT NULL DEFAULT 'NONE' CHECK (ai_consent IN ('NONE', 'CONFIDENTIAL')),
    ai_consent_at       timestamptz,
    default_visibility  varchar(20) NOT NULL DEFAULT 'PRIVATE' CHECK (default_visibility IN ('PRIVATE', 'SELECTIVE', 'PUBLIC')),
    version             bigint NOT NULL DEFAULT 1,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT workspace_settings_consent_check CHECK ((ai_consent = 'NONE') = (ai_consent_at IS NULL))
);
CREATE TRIGGER workspace_settings_set_updated_at BEFORE UPDATE ON workspace_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
