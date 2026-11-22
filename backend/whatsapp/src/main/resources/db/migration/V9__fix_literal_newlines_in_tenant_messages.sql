-- V9__fix_literal_newlines_in_tenant_messages.sql
-- CEFAN - Corrige sequências literais '\n' gravadas pela migração V4 em tenant_configs
-- No PostgreSQL (standard_conforming_strings = on), strings simples '...' gravam '\n' literalmente.

UPDATE tenant_configs
SET
  welcome_message = REPLACE(welcome_message, '\n', CHR(10)),
  orcamento_intro_message = REPLACE(orcamento_intro_message, '\n', CHR(10)),
  agendamento_intro_message = REPLACE(agendamento_intro_message, '\n', CHR(10)),
  pix_payment_message = REPLACE(pix_payment_message, '\n', CHR(10)),
  pix_confirmed_message = REPLACE(pix_confirmed_message, '\n', CHR(10)),
  human_transfer_message = REPLACE(human_transfer_message, '\n', CHR(10)),
  bot_reset_message = REPLACE(bot_reset_message, '\n', CHR(10)),
  absence_message = REPLACE(absence_message, '\n', CHR(10));
