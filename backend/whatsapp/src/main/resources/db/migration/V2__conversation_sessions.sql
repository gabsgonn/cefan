-- ==========================================================
-- CEFAN - Migração V2: Tabela de Sessões de Conversa (Máquina de Estados)
-- ==========================================================

CREATE TABLE conversation_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
    current_state VARCHAR(50) NOT NULL DEFAULT 'MENU',
    context_data JSONB,
    last_interaction_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_sessions_customer ON conversation_sessions(customer_id);
CREATE INDEX idx_conversation_sessions_state ON conversation_sessions(current_state);
