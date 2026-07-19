-- Per-user GitHub token store for Helios-side refresh of Keycloak-brokered tokens.
-- One row per GitHub login. Token columns hold AES-GCM ciphertext (see TokenCipher) — this table
-- never stores plaintext. Rows are seeded once from Keycloak's broker retrieve-token endpoint, then
-- refreshed directly against GitHub so deployment approvals keep working past the 8h GitHub
-- user-token lifetime (Keycloak does not refresh brokered tokens).
CREATE TABLE public.github_user_token (
    id                        BIGSERIAL PRIMARY KEY,
    github_login              VARCHAR(255) NOT NULL UNIQUE,
    access_token_enc          TEXT,
    refresh_token_enc         TEXT,
    access_token_expires_at   TIMESTAMPTZ,
    refresh_token_expires_at  TIMESTAMPTZ,
    updated_at                TIMESTAMPTZ NOT NULL
);
