-- Sprint 2: Add MFA columns to user table
ALTER TABLE IF EXISTS ecos_identity.td_user ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(128);
ALTER TABLE IF EXISTS ecos_identity.td_user ADD COLUMN IF NOT EXISTS mfa_type VARCHAR(16);
ALTER TABLE IF EXISTS ecos_identity.td_user ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN DEFAULT FALSE;
