-- V7__structured_tattoo_size_options.sql
-- CEFAN - Opções estruturadas de tamanho de tatuagem (Flash, Pequena, Média, Grande e Fechamento A Combinar)

UPDATE tenant_configs
SET orcamento_intro_message = '📸 *Envio de Referência para Orçamento*

Por favor, envie uma *foto da tatuagem* (ou referência visual) que você deseja fazer, informe o local do corpo e escolha uma das nossas *opções de tamanho*:

📏 *Opções de Tamanho da Tatuagem:*
• Digite *A* (ou *Flash*) — *Flash / Mini* (até 5 cm)
• Digite *B* (ou *Pequena*) — *Pequena* (6 a 10 cm)
• Digite *C* (ou *Média*) — *Média* (11 a 18 cm)
• Digite *D* (ou *Grande*) — *Grande* (19 a 25 cm)
• Digite *E* (ou *Fechamento*) — *Fechamento* (26 cm+ / Projeto Amplo — _Valor a combinar_)'
WHERE orcamento_intro_message IS NULL
   OR orcamento_intro_message LIKE '%Tamanho aproximado em centímetros%';
