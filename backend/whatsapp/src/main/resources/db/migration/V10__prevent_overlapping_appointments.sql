-- V10__prevent_overlapping_appointments.sql
-- CEFAN - Garante atomicamente no PostgreSQL que não existam dois agendamentos ativos
-- sobrepostos para o mesmo artista (AGUARDANDO_PAGAMENTO ou CONFIRMADO).

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Expira eventuais pré-agendamentos vencidos antes de ativar a constraint
UPDATE appointments
SET status = 'EXPIRADO'
WHERE status = 'AGUARDANDO_PAGAMENTO'
  AND expires_at IS NOT NULL
  AND expires_at <= NOW();

ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS chk_appointments_valid_range;

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_valid_range
    CHECK (scheduled_end > scheduled_start);

ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS excl_appointments_no_overlap_per_artist;

ALTER TABLE appointments
    ADD CONSTRAINT excl_appointments_no_overlap_per_artist
    EXCLUDE USING gist (
        artist_id WITH =,
        tstzrange(scheduled_start, scheduled_end, '[)') WITH &&
    )
    WHERE (artist_id IS NOT NULL AND status IN ('AGUARDANDO_PAGAMENTO', 'CONFIRMADO'));
