-- ==========================================================
-- CEFAN - Migração V8: Coluna logo_base64 em tenant_configs
-- ==========================================================

ALTER TABLE tenant_configs
    ADD COLUMN IF NOT EXISTS logo_base64 TEXT;
