-- ADR-0010 결정 3: when the user last proved a fresh login with an ID token that the API
-- verified itself (POST /me/reauthentications). Sensitive actions accept this or the access
-- token's auth_time, whichever is newer; hosted IdPs put no auth_time in access tokens.
ALTER TABLE users ADD COLUMN reauthenticated_at timestamptz;
