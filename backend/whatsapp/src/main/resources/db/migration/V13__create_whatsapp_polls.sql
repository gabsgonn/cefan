-- V13__create_whatsapp_polls.sql
-- CEFAN - Persistência de enquetes enviadas via Evolution API (sendPoll)
-- para mapear votos por índice -> UserIntent, ignorar enquetes antigas e garantir idempotência.

CREATE TABLE IF NOT EXISTS whatsapp_polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_message_id VARCHAR(150) NOT NULL UNIQUE,
    session_id UUID NOT NULL REFERENCES conversation_sessions(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    expected_state VARCHAR(50) NOT NULL,
    options_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    voted_option_index INTEGER,
    voted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_polls_customer_active ON whatsapp_polls(customer_id, active);
CREATE INDEX IF NOT EXISTS idx_whatsapp_polls_message_id ON whatsapp_polls(poll_message_id);
