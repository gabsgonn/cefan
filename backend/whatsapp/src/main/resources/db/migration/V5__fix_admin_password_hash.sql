-- ==========================================================
-- CEFAN - Migração V5: Correção do Hash BCrypt do Usuário Administrador
-- Senha definida: cefan123
-- Hash BCrypt (custo 10): $2a$10$lkrn.W02WOcSCZ7WEMy5zu1aL./0p0n.xIg29Mr.wFED3NO7FAyLS
-- ==========================================================

UPDATE users
SET password_hash = '$2a$10$lkrn.W02WOcSCZ7WEMy5zu1aL./0p0n.xIg29Mr.wFED3NO7FAyLS',
    is_active = true,
    role = 'OWNER'
WHERE email = 'admin@viciotattoo.com';
