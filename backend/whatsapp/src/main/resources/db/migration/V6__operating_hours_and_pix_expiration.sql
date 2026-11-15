-- V6__operating_hours_and_pix_expiration.sql
-- CEFAN - Parametrização de Tempo de Expiração do PIX, Horários de Funcionamento e Mensagem de Ausência (Seção 3.5 do Plano Final)

ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS pix_expiration_minutes INTEGER NOT NULL DEFAULT 120;
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS opening_time VARCHAR(5) NOT NULL DEFAULT '10:00';
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS closing_time VARCHAR(5) NOT NULL DEFAULT '20:00';
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS working_days VARCHAR(50) NOT NULL DEFAULT 'SEG-SAB';
ALTER TABLE tenant_configs ADD COLUMN IF NOT EXISTS absence_message TEXT;

UPDATE tenant_configs
SET
  pix_expiration_minutes = COALESCE(pix_expiration_minutes, 120),
  opening_time = COALESCE(opening_time, '10:00'),
  closing_time = COALESCE(closing_time, '20:00'),
  working_days = COALESCE(working_days, 'SEG-SAB'),
  absence_message = COALESCE(absence_message, '🌙 *Estúdio Fora do Horário de Expediente*

Nosso horário de funcionamento é de Segunda a Sábado, das 10h às 20h.
Seu atendimento já foi registrado e você pode continuar utilizando nosso assistente virtual para orçamentos e pré-agendamentos normais!');
