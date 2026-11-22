-- V12__simplify_orcamento_intro_message.sql
-- CEFAN - Remove a escolha de tamanho A-E da frente do fluxo de orçamento (a IA já estima pela foto).
-- Cobre tanto o texto padrão da V7 quanto eventuais edições no painel que ainda mantenham a lista A-E.

UPDATE tenant_configs
SET orcamento_intro_message = '📸 *Envio de Referência para Orçamento*

Por favor, envie uma *foto da tatuagem* (ou referência visual) que você deseja fazer.

Se quiser, pode mandar na legenda o local do corpo ou a medida aproximada em cm (ex: antebraço, 12cm) — nossa IA já estima o tamanho automaticamente pela imagem!'
WHERE orcamento_intro_message IS NULL
   OR orcamento_intro_message LIKE '%Opções de Tamanho da Tatuagem%'
   OR orcamento_intro_message LIKE '%Digite *A*%'
   OR orcamento_intro_message LIKE '%Flash / Mini%'
   OR orcamento_intro_message LIKE '%26 cm+%';
