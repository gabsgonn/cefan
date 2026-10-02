-- ==========================================================
-- CEFAN - Schema Inicial de Banco de Dados (PostgreSQL)
-- ==========================================================

-- Extensão para UUID se necessário
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Configurações do Estúdio (Tenant)
CREATE TABLE tenant_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    studio_name VARCHAR(255) NOT NULL,
    absorb_credit_card_fees BOOLEAN NOT NULL DEFAULT false,
    default_hourly_rate NUMERIC(10, 2) NOT NULL DEFAULT 150.00,
    fixed_setup_cost NUMERIC(10, 2) NOT NULL DEFAULT 50.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 2. Usuários (Dono, Tatuadores, Atendentes)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenant_configs(id),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    role VARCHAR(50) NOT NULL DEFAULT 'ARTIST',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 3. Clientes (identificados primariamente pelo número do WhatsApp)
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) UNIQUE NOT NULL,
    tax_id VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 4. Agendamentos
CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    artist_id UUID REFERENCES users(id),
    scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AGUARDANDO_PAGAMENTO',
    is_touchup BOOLEAN NOT NULL DEFAULT false,
    total_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    deposit_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    mp_payment_id VARCHAR(255),
    mp_payment_link TEXT,
    mp_pix_copy_paste TEXT,
    mp_pix_qr_code_base64 TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    reminder_24h_sent BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 5. Logs de Auditoria de Valores de Agendamento
CREATE TABLE appointment_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    changed_by_user_id UUID REFERENCES users(id),
    old_value NUMERIC(10, 2) NOT NULL,
    new_value NUMERIC(10, 2) NOT NULL,
    reason TEXT NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 6. Preferências de Notificação do Usuário
CREATE TABLE user_notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notify_new_lead BOOLEAN NOT NULL DEFAULT true,
    notify_payment_received BOOLEAN NOT NULL DEFAULT true,
    notify_human_request BOOLEAN NOT NULL DEFAULT true
);

-- Índices estratégicos
CREATE INDEX idx_appointments_customer ON appointments(customer_id);
CREATE INDEX idx_appointments_status_expires ON appointments(status, expires_at);
CREATE INDEX idx_appointments_start ON appointments(scheduled_start);
CREATE INDEX idx_customers_phone ON customers(phone);
