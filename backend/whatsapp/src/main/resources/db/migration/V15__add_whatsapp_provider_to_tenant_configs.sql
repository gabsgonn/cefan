-- V15: Seleção de provedor WhatsApp por tenant (evolution | meta_cloud)
ALTER TABLE tenant_configs
    ADD COLUMN IF NOT EXISTS whatsapp_provider VARCHAR(30) NOT NULL DEFAULT 'evolution';
