-- V4__chat_messages_and_parameters.sql
-- CEFAN - Mensagens de Chat em Tempo Real, Atribuição de Profissional e Parametrização Completa

-- 1. Tabela para histórico real de mensagens do chat (WhatsApp e Live Chat)
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES conversation_sessions(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    sender_type VARCHAR(20) NOT NULL, -- 'CUSTOMER', 'BOT', 'AGENT'
    sender_id UUID REFERENCES users(id) ON DELETE SET NULL, -- Se AGENT, ID do usuário logado
    sender_name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    media_url TEXT,
    media_type VARCHAR(50),
    is_from_me BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created ON chat_messages(session_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_customer ON chat_messages(customer_id);

-- 2. Atribuição de profissional na sessão de conversa
ALTER TABLE conversation_sessions
ADD COLUMN IF NOT EXISTS assigned_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS assigned_artist_name VARCHAR(100);

-- 3. Parametrização completa de mensagens do bot e regras de estúdio em tenant_configs
ALTER TABLE tenant_configs
ADD COLUMN IF NOT EXISTS signal_percentage NUMERIC(5, 2) NOT NULL DEFAULT 30.00,
ADD COLUMN IF NOT EXISTS min_deposit_amount NUMERIC(10, 2) NOT NULL DEFAULT 50.00,
ADD COLUMN IF NOT EXISTS welcome_message TEXT,
ADD COLUMN IF NOT EXISTS orcamento_intro_message TEXT,
ADD COLUMN IF NOT EXISTS agendamento_intro_message TEXT,
ADD COLUMN IF NOT EXISTS pix_payment_message TEXT,
ADD COLUMN IF NOT EXISTS pix_confirmed_message TEXT,
ADD COLUMN IF NOT EXISTS human_transfer_message TEXT,
ADD COLUMN IF NOT EXISTS bot_reset_message TEXT;

-- 4. Popula mensagens padrão parametrizadas caso estejam nulas
UPDATE tenant_configs
SET
  welcome_message = COALESCE(welcome_message, '👋 Olá! Seja muito bem-vindo(a) ao *Vício Tattoo CWB*!\nEu sou a assistente virtual e estou aqui para agilizar seu atendimento:\n\n1️⃣ *Solicitar Orçamento com IA* (Envie foto da ideia)\n2️⃣ *Agendar Horário / Ver Disponibilidade*\n3️⃣ *Falar com um Tatuador / Dúvidas*\n4️⃣ *Nossos Artistas e Portfólio*\n\nDigite o número da opção desejada:'),
  orcamento_intro_message = COALESCE(orcamento_intro_message, '📸 *Envio de Referência para Orçamento*\n\nPor favor, envie uma *foto ou imagem de referência* da tatuagem que você deseja fazer.\n\nSe preferir, adicione também:\n- Tamanho aproximado em centímetros (ex: 10cm)\n- Local do corpo pretendido (ex: antebraço, ombro)'),
  agendamento_intro_message = COALESCE(agendamento_intro_message, '📅 *Agendamento de Sessão*\n\nNossa equipe conta com 11 artistas especialistas. Para verificar a melhor data:\nQual dia da semana ou período você tem preferência? (ex: próxima sexta à tarde, sábado pela manhã)'),
  pix_payment_message = COALESCE(pix_payment_message, '🗓️ *Pré-Agendamento Criado!*\n\nBloqueamos seu horário na grade. Para confirmar, efetue o pagamento do sinal de 30% via PIX copia e cola abaixo. O horário fica reservado por 2 horas.'),
  pix_confirmed_message = COALESCE(pix_confirmed_message, '🎉 *Agendamento Confirmado com Sucesso!*\n\nSeu pagamento do sinal foi aprovado pelo estúdio. Nos vemos no Vício Tattoo! ⚡'),
  human_transfer_message = COALESCE(human_transfer_message, '💬 *Transbordo para Atendimento Humano*\n\nUm dos nossos tatuadores foi notificado e já vai assumir seu atendimento por aqui em instantes. Aguarde só um momento!'),
  bot_reset_message = COALESCE(bot_reset_message, '🔄 Conversa reiniciada. Como podemos te ajudar agora?');
