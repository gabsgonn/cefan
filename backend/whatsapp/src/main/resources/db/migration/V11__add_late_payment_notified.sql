-- V11__add_late_payment_notified.sql
-- CEFAN - Garante idempotência no webhook do Mercado Pago para pagamentos tardios/conflitantes.

ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS late_payment_notified BOOLEAN NOT NULL DEFAULT false;
