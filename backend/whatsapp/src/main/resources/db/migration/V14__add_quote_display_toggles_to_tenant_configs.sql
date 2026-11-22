-- V14: Toggles de exibição do orçamento IA por tenant
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_greeting BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_style BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_size BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_duration BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_complexity BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_location BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_artists BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_description BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_price BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_materials_note BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS orcamento_show_adjust_hint BOOLEAN NOT NULL DEFAULT true;
