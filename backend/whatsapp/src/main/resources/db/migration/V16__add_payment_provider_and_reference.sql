-- V16: Multi-provedor de pagamento (Mercado Pago + InfinitePay) e saneamento de tenant_configs duplicado

-- 1. Saneia eventual linha duplicada em tenant_configs (mantém a linha com updated_at mais recente)
DO $$
DECLARE
    canonical_id UUID;
BEGIN
    IF (SELECT COUNT(*) FROM tenant_configs) > 1 THEN
        SELECT id INTO canonical_id
        FROM tenant_configs
        ORDER BY updated_at DESC, created_at ASC
        LIMIT 1;

        UPDATE users
        SET tenant_id = canonical_id
        WHERE tenant_id IS NOT NULL
          AND tenant_id <> canonical_id;

        DELETE FROM tenant_configs
        WHERE id <> canonical_id;
    END IF;
END $$;

-- 2. Provedor de pagamento e handle InfinitePay por tenant
ALTER TABLE tenant_configs
    ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(30) NOT NULL DEFAULT 'mercadopago',
    ADD COLUMN IF NOT EXISTS infinitepay_handle VARCHAR(100);

ALTER TABLE tenant_configs
    DROP CONSTRAINT IF EXISTS chk_tenant_configs_payment_provider;

ALTER TABLE tenant_configs
    ADD CONSTRAINT chk_tenant_configs_payment_provider
        CHECK (payment_provider IN ('mercadopago', 'infinitepay'));

-- 3. Colunas neutras de pagamento em appointments (preservando colunas mp_* existentes)
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(30),
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(120),
    ADD COLUMN IF NOT EXISTS payment_url TEXT;

UPDATE appointments
SET payment_provider = 'mercadopago',
    payment_reference = mp_payment_id
WHERE mp_payment_id IS NOT NULL
  AND payment_reference IS NULL;

ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS chk_appointments_payment_pair;

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_payment_pair
        CHECK ((payment_provider IS NULL) = (payment_reference IS NULL));

CREATE UNIQUE INDEX IF NOT EXISTS uq_appointments_payment_provider_reference
    ON appointments (payment_provider, payment_reference)
    WHERE payment_reference IS NOT NULL;
