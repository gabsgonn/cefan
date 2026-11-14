-- ==========================================================
-- CEFAN - Migração V3: Segurança, Autenticação e Dados de Profissionais
-- ==========================================================

-- 1. Novos campos na tabela users para autenticação BCrypt e perfil profissional
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS specialties VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS artist_rate_type VARCHAR(20) DEFAULT 'PERCENTAGE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS artist_rate_value NUMERIC(10, 2) DEFAULT 0.00;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reputation NUMERIC(2, 1) DEFAULT 5.0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS hire_date DATE DEFAULT CURRENT_DATE;

-- Atualiza role se houver registros legados
UPDATE users SET role = 'ARTIST' WHERE role IS NULL;

-- 2. Garante a existência do tenant padrão para o estúdio
INSERT INTO tenant_configs (id, studio_name, absorb_credit_card_fees, default_hourly_rate, fixed_setup_cost)
VALUES ('00000000-0000-0000-0000-000000000001', 'Vício Tattoo', false, 150.00, 50.00)
ON CONFLICT (id) DO NOTHING;

-- 3. Seed inicial do usuário Dono (admin@viciotattoo.com / cefan123 com hash BCrypt custo 10)
-- Hash BCrypt correspondente a "cefan123": $2a$10$tZ27J4u8sJ5BfD5w04p8p.4wP9W6Q2tY2v8W4u0q6p2v8w4u0q6p2 (será garantido/atualizado também via SecurityDataInitializer)
INSERT INTO users (id, tenant_id, name, email, phone, role, password_hash, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'Dono - Vício Tattoo',
    'admin@viciotattoo.com',
    '5511999990000',
    'OWNER',
    '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.tpR4PEFLm3b8v4vIge2C',
    true
)
ON CONFLICT (email) DO UPDATE SET
    role = 'OWNER',
    password_hash = EXCLUDED.password_hash;
