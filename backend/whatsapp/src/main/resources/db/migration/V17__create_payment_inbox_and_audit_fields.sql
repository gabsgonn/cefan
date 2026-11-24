-- V17: Tabela inbox de eventos de pagamento (idempotente por transaction_nsu) e colunas de auditoria em appointments
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS paid_amount_cents BIGINT,
    ADD COLUMN IF NOT EXISTS capture_method VARCHAR(40),
    ADD COLUMN IF NOT EXISTS transaction_nsu VARCHAR(120),
    ADD COLUMN IF NOT EXISTS invoice_slug VARCHAR(120),
    ADD COLUMN IF NOT EXISTS needs_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS manual_confirmed_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS manual_confirmed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS payment_inbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(30) NOT NULL,
    appointment_id UUID,
    order_nsu UUID,
    transaction_nsu VARCHAR(120) NOT NULL,
    invoice_slug VARCHAR(120),
    capture_method VARCHAR(40),
    source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_payment_inbox_provider_tx UNIQUE (provider, transaction_nsu)
);

CREATE INDEX IF NOT EXISTS idx_payment_inbox_pending_next_attempt
    ON payment_inbox_events (status, next_attempt_at);
